package com.example;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.apache.qpid.proton.Proton;
import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.amqp.messaging.Source;
import org.apache.qpid.proton.amqp.messaging.Target;
import org.apache.qpid.proton.amqp.transport.DeliveryState;
import org.apache.qpid.proton.engine.BaseHandler;
import org.apache.qpid.proton.engine.Connection;
import org.apache.qpid.proton.engine.Delivery;
import org.apache.qpid.proton.engine.Event;
import org.apache.qpid.proton.engine.Link;
import org.apache.qpid.proton.engine.Receiver;
import org.apache.qpid.proton.engine.Sasl;
import org.apache.qpid.proton.engine.Session;
import org.apache.qpid.proton.engine.SslDomain;
import org.apache.qpid.proton.engine.Transport;
import org.apache.qpid.proton.message.Message;
import org.apache.qpid.proton.reactor.Reactor;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.CertificateFactory;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class Main {
    private static final Logger logger = Logger.getLogger(Main.class.getName());

    // Configuration by environment variables
    private static final String ACTOR_API_HOST = getEnv("ACTOR_API_HOST", "hostname_of_the_actor_api");
    private static final String ACTOR_API_PORT = getEnv("ACTOR_API_PORT", "port_of_the_actor_api");
    private static final String ACTOR_API_SUBSCRIPTION_SELECTOR = getEnv("ACTOR_API_SUBSCRIPTION_SELECTOR", "selector_of_the_subscription");
    private static final String ACTOR_COMMON_NAME = getEnv("ACTOR_COMMON_NAME", "cn_of_the_actor_client_certificate");
    private static final String ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM = getEnv("ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM", "pem_with_x509_certificate_chain_and_private_key");
    private static final String CA_CERTIFICATE_PEM = getEnv("CA_CERTIFICATE_PEM", "pem_with_x509_certificate");

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static OkHttpClient httpClient;

    private static String getEnv(String key, String defaultValue) {
        String value = System.getenv(key);
        return value != null ? value : defaultValue;
    }

    // ======== ACTOR API FUNCTIONS ========
    private static String apiUrl(String endpoint) {
        return String.format("https://%s:%s/%s/%s", ACTOR_API_HOST, ACTOR_API_PORT, ACTOR_COMMON_NAME, endpoint);
    }

    private static Response apiGet(String endpoint) throws IOException {
        Request request = new Request.Builder()
                .url(apiUrl(endpoint))
                .build();
        return httpClient.newCall(request).execute();
    }

    private static Response apiPost(String endpoint, String jsonData) throws IOException {
        RequestBody body = RequestBody.create(jsonData, MediaType.parse("application/json"));
        Request request = new Request.Builder()
                .url(apiUrl(endpoint))
                .post(body)
                .build();
        return httpClient.newCall(request).execute();
    }

    private static Response apiDelete(String endpoint) throws IOException {
        Request request = new Request.Builder()
                .url(apiUrl(endpoint))
                .delete()
                .build();
        return httpClient.newCall(request).execute();
    }

    private static Response apiGetSubscription(String id) throws IOException {
        return apiGet("subscriptions/" + id);
    }

    private static Response apiDeleteSubscription(String id) throws IOException {
        return apiDelete("subscriptions/" + id);
    }

    private static Response apiCreateSubscription() throws IOException {
        Map<String, Object> jsonData = new HashMap<>();
        jsonData.put("selector", ACTOR_API_SUBSCRIPTION_SELECTOR);
        String json = objectMapper.writeValueAsString(jsonData);
        return apiPost("subscriptions", json);
    }

    // ======== SSL Configuration for HTTP Client ========
    private static void initializeHttpClient() throws Exception {
        // Add BouncyCastle provider
        Security.addProvider(new BouncyCastleProvider());

        // Create SSL context
        SSLContext sslContext = SSLContext.getInstance("TLSv1.2");

        // Load client certificate and key
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null);

        // Parse certificates and key using BouncyCastle
        List<java.security.cert.Certificate> certChain = new ArrayList<>();
        PrivateKey privateKey = null;

        try (PEMParser pemParser = new PEMParser(new FileReader(ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM))) {
            Object object;
            JcaPEMKeyConverter keyConverter = new JcaPEMKeyConverter().setProvider("BC");
            JcaX509CertificateConverter certConverter = new JcaX509CertificateConverter().setProvider("BC");

            while ((object = pemParser.readObject()) != null) {
                if (object instanceof X509CertificateHolder) {
                    certChain.add(certConverter.getCertificate((X509CertificateHolder) object));
                } else if (object instanceof PEMKeyPair) {
                    privateKey = keyConverter.getPrivateKey(((PEMKeyPair) object).getPrivateKeyInfo());
                }
            }
        }

        // Add certificate chain and key to keystore
        if (privateKey != null && !certChain.isEmpty()) {
            keyStore.setKeyEntry("client", privateKey, new char[0], certChain.toArray(new java.security.cert.Certificate[0]));
        } else {
            throw new IllegalStateException("Failed to load client certificate and key");
        }

        // Initialize key manager
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, new char[0]);

        // Load CA certificate
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null);

        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        try (FileInputStream fis = new FileInputStream(CA_CERTIFICATE_PEM)) {
            java.security.cert.Certificate caCert = cf.generateCertificate(fis);
            trustStore.setCertificateEntry("ca", caCert);
        }

        // Initialize trust manager
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);

        // Initialize SSL context
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());

        // Create OkHttpClient with SSL configuration
        httpClient = new OkHttpClient.Builder()
                .sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) tmf.getTrustManagers()[0])
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    // ======== AMQP 1.0 CLIENT ========
    private static class ReceiverHandler extends BaseHandler {
        private final Map<String, Object> endpoint;
        private SSLContext sslContext;

        public ReceiverHandler(Map<String, Object> endpoint) {
            this.endpoint = endpoint;
        }

        public void setSslContext(SSLContext sslContext) {
            this.sslContext = sslContext;
        }

        @Override
        public void onConnectionInit(Event event) {
            logger.fine("Connection initialized");
            Connection connection = event.getConnection();
            connection.setHostname((String) endpoint.get("host"));
            connection.setContainer("java-subscription-example");
            connection.open();
        }

        @Override
        public void onConnectionBound(Event event) {
            logger.fine("Connection bound, configuring transport");
            Transport transport = event.getTransport();

            if (sslContext != null) {
                // Configure SASL EXTERNAL
                Sasl sasl = transport.sasl();
                sasl.setMechanisms("EXTERNAL");

                // Configure SSL
                SslDomain sslDomain = Proton.sslDomain();
                sslDomain.init(SslDomain.Mode.CLIENT);
                sslDomain.setPeerAuthentication(SslDomain.VerifyMode.VERIFY_PEER);
                sslDomain.setSslContext(sslContext);

                transport.ssl(sslDomain);
            }
        }

        @Override
        public void onConnectionRemoteOpen(Event event) {
            logger.fine("Connection opened");
            Connection connection = event.getConnection();
            Session session = connection.session();
            session.open();
        }

        @Override
        public void onSessionRemoteOpen(Event event) {
            logger.fine("Session opened");
            Session session = event.getSession();
            String sourceAddress = (String) endpoint.get("source");
            Receiver receiver = session.receiver(sourceAddress);
            Source source = new Source();
            source.setAddress(sourceAddress);
            receiver.setSource(source);
            Target target = new Target();
            receiver.setTarget(target);
            receiver.open();
        }

        @Override
        public void onLinkRemoteClose(Event event) {
            Link link = event.getLink();
            logger.severe("Link remote close - State: " + link.getRemoteState());
            if (link.getRemoteCondition() != null) {
                logger.severe("Condition: " + link.getRemoteCondition().getCondition());
                logger.severe("Description: " + link.getRemoteCondition().getDescription());
            }
        }

        @Override
        public void onLinkRemoteOpen(Event event) {
            logger.fine("Receiver link opened, ready to receive messages");
            if (event.getLink() instanceof Receiver) {
                Receiver receiver = (Receiver) event.getLink();
                receiver.flow(10); // Initial credit
            }
        }

        @Override
        public void onDelivery(Event event) {
            Delivery delivery = event.getDelivery();
            if (delivery.isReadable() && !delivery.isPartial()) {
                Receiver receiver = (Receiver) delivery.getLink();
                
                // Read the message
                int size = delivery.pending();
                byte[] buffer = new byte[size];
                int read = receiver.recv(buffer, 0, buffer.length);
                receiver.advance();
                
                // Decode the message
                Message message = Proton.message();
                message.decode(buffer, 0, read);
                
                // Extract body
                String bodyText = "";
                if (message.getBody() != null) {
                    Object body = message.getBody();
                    if (body instanceof org.apache.qpid.proton.amqp.messaging.Data) {
                        org.apache.qpid.proton.amqp.messaging.Data data = (org.apache.qpid.proton.amqp.messaging.Data) body;
                        if (data.getValue() != null) {
                            bodyText = new String(data.getValue().getArray(), StandardCharsets.UTF_8);
                        }
                    } else {
                        bodyText = body.toString();
                    }
                }
                
                // Extract and format application properties
                Map<String, Object> appProps = new HashMap<>();
                if (message.getApplicationProperties() != null && message.getApplicationProperties().getValue() != null) {
                    appProps = message.getApplicationProperties().getValue();
                }
                
                try {
                    // Format properties in sorted order
                    String sortedPropsJson = objectMapper.writeValueAsString(new TreeMap<>(appProps));
                    logger.info(String.format("Message received: body='%s', properties=%s", bodyText, sortedPropsJson));
                } catch (Exception e) {
                    logger.warning("Error formatting properties: " + e.getMessage());
                }
                
                // Accept the message
                delivery.disposition(Accepted.getInstance());
                delivery.settle();
                
                // Flow more credit if needed
                if (receiver.getCredit() < 5) {
                    receiver.flow(10);
                }
            }
        }

        @Override
        public void onTransportError(Event event) {
            logger.log(Level.SEVERE, "Transport error: " + event.getTransport().getCondition());
        }
    }

    private static SSLContext createSSLContext() throws Exception {
        // Add BouncyCastle provider
        Security.addProvider(new BouncyCastleProvider());

        // Create SSL context
        SSLContext sslContext = SSLContext.getInstance("TLSv1.3");

        // Load client certificate and key
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null);

        // Parse certificates and key using BouncyCastle
        List<java.security.cert.Certificate> certChain = new ArrayList<>();
        PrivateKey privateKey = null;

        try (PEMParser pemParser = new PEMParser(new FileReader(ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM))) {
            Object object;
            JcaPEMKeyConverter keyConverter = new JcaPEMKeyConverter().setProvider("BC");
            JcaX509CertificateConverter certConverter = new JcaX509CertificateConverter().setProvider("BC");

            while ((object = pemParser.readObject()) != null) {
                if (object instanceof X509CertificateHolder) {
                    certChain.add(certConverter.getCertificate((X509CertificateHolder) object));
                } else if (object instanceof PEMKeyPair) {
                    privateKey = keyConverter.getPrivateKey(((PEMKeyPair) object).getPrivateKeyInfo());
                }
            }
        }

        // Add certificate chain and key to keystore
        if (privateKey != null && !certChain.isEmpty()) {
            keyStore.setKeyEntry("client", privateKey, new char[0], certChain.toArray(new java.security.cert.Certificate[0]));
        } else {
            throw new IllegalStateException("Failed to load client certificate and key");
        }

        // Initialize key manager
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, new char[0]);

        // Load CA certificate
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null);

        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        try (FileInputStream fis = new FileInputStream(CA_CERTIFICATE_PEM)) {
            java.security.cert.Certificate caCert = cf.generateCertificate(fis);
            trustStore.setCertificateEntry("ca", caCert);
        }

        // Initialize trust manager
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);

        // Initialize SSL context
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());

        return sslContext;
    }

    private static void amqpConnectAndListen(Map<String, Object> endpoint) throws Exception {
        // Configure SSL
        SSLContext sslContext = createSSLContext();

        // Create handler
        ReceiverHandler handler = new ReceiverHandler(endpoint);
        handler.setSslContext(sslContext);

        // Create reactor
        Reactor reactor = Proton.reactor(handler);

        // Connect to host with SSL and SASL configuration
        String host = (String) endpoint.get("host");
        int port = ((Number) endpoint.get("port")).intValue();

        // Use reactor's connection method with proper SSL/SASL setup
        reactor.connectionToHost(host, port, handler);

        // Run reactor
        reactor.run();
    }

    // ======== CREATE AND CONSUME A SUBSCRIPTION ========
    private static void logJson(String message, Map<String, Object> jsonDict) {
        try {
            String jsonString = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonDict);
            logger.info(message + ": " + jsonString);
        } catch (Exception e) {
            logger.warning("Error formatting JSON: " + e.getMessage());
        }
    }

    private static void subscribeAndReceive() {
        try {
            // Step 1: create a subscription using the actor API
            try (Response subscriptionCreateResponse = apiCreateSubscription()) {
                String responseBody = subscriptionCreateResponse.body().string();
                Map<String, Object> subscriptionCreateResponseJson = objectMapper.readValue(responseBody, 
                    new TypeReference<Map<String, Object>>() {});
                logJson("Subscription create response", subscriptionCreateResponseJson);

                if (subscriptionCreateResponse.isSuccessful()) {
                    // Step 2: get the subscription status
                    String subscriptionId = (String) subscriptionCreateResponseJson.get("id");
                    Map<String, Object> subscriptionStatusResponseJson;
                    String subscriptionStatus;
                    
                    try (Response subscriptionStatusResponse = apiGetSubscription(subscriptionId)) {
                        subscriptionStatusResponseJson = objectMapper.readValue(subscriptionStatusResponse.body().string(),
                            new TypeReference<Map<String, Object>>() {});
                        logJson("Subscription " + subscriptionId + " status response", subscriptionStatusResponseJson);
                        subscriptionStatus = (String) subscriptionStatusResponseJson.get("status");
                    }

                    // Step 3: while the subscription status is "REQUESTED", keep getting the status
                    while ("REQUESTED".equals(subscriptionStatus)) {
                        Thread.sleep(2000);
                        try (Response subscriptionStatusResponse = apiGetSubscription(subscriptionId)) {
                            subscriptionStatusResponseJson = objectMapper.readValue(subscriptionStatusResponse.body().string(),
                                new TypeReference<Map<String, Object>>() {});
                            subscriptionStatus = (String) subscriptionStatusResponseJson.get("status");
                        }
                    }

                    logJson("Subscription " + subscriptionId + " status response", subscriptionStatusResponseJson);

                    // Step 4a: if the status is "CREATED", connect to the endpoint and start the AMQP receiver
                    if ("CREATED".equals(subscriptionStatus)) {
                        // NOTE to keep things simple, this code assumes that this response contains exactly one endpoint!
                        List<Map<String, Object>> endpoints = (List<Map<String, Object>>) subscriptionStatusResponseJson.get("endpoints");
                        Map<String, Object> endpoint = endpoints.get(0);
                        logger.info("Using endpoint " + endpoint);
                        amqpConnectAndListen(endpoint);
                    }
                    // Step 4b: if the status is not "CREATED" warn log and do nothing
                    else {
                        logger.warning("Unable to use subscription " + subscriptionId);
                    }
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "An exception occurred while running subscribeAndReceive", e);
            e.printStackTrace();
        }
    }

    // ======== STARTUP AND RUN LOOP ========
    private static void dumpConfig() {
        logger.info("ACTOR_API_HOST: '" + ACTOR_API_HOST + "'");
        logger.info("ACTOR_API_PORT: '" + ACTOR_API_PORT + "'");
        logger.info("ACTOR_API_SUBSCRIPTION_SELECTOR: '" + ACTOR_API_SUBSCRIPTION_SELECTOR + "'");
        logger.info("ACTOR_COMMON_NAME: '" + ACTOR_COMMON_NAME + "'");
        logger.info("ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM: '" + ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM + "'");
        logger.info("CA_CERTIFICATE_PEM: '" + CA_CERTIFICATE_PEM + "'");
    }

    private static void configureLogging() {
        // Configure console handler
        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setLevel(Level.ALL);
        consoleHandler.setFormatter(new SimpleFormatter() {
            private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

            @Override
            public synchronized String format(LogRecord record) {
                return String.format("%s %s %s%n",
                        dateFormat.format(new Date(record.getMillis())),
                        record.getLevel().getName(),
                        record.getMessage());
            }
        });

        // Configure logger
        logger.setLevel(Level.ALL);
        logger.setUseParentHandlers(false);
        logger.addHandler(consoleHandler);

        // Set Proton library logging to INFO
        Logger protonLogger = Logger.getLogger("org.apache.qpid.proton");
        protonLogger.setLevel(Level.INFO);
    }

    public static void main(String[] args) {
        configureLogging();
        logger.info("Starting application");
        dumpConfig();

        try {
            initializeHttpClient();
            subscribeAndReceive();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Application error", e);
            e.printStackTrace();
        }

        logger.info("Application stopped");
    }
}