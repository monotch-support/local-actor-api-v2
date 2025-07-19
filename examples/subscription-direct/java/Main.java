package com.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.qpid.proton.Proton;
import org.apache.qpid.proton.amqp.Binary;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.amqp.messaging.Source;
import org.apache.qpid.proton.amqp.messaging.Target;
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
import java.io.FileInputStream;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.CertificateFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.Date;
import java.text.SimpleDateFormat;

public class Main {
    private static final Logger logger = Logger.getLogger(Main.class.getName());

    // Configuration by environment variables
    private static final String ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM = getEnv("ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM", "pem_with_x509_certificate_chain_and_private_key");
    private static final String CA_CERTIFICATE_PEM = getEnv("CA_CERTIFICATE_PEM", "pem_with_x509_certificate");

    // Pre-known endpoint information
    private static final String ENDPOINT_HOST = getEnv("ENDPOINT_HOST", "amqp_endpoint_host");
    private static final String ENDPOINT_PORT = getEnv("ENDPOINT_PORT", "amqp_endpoint_port");
    private static final String ENDPOINT_SOURCE = getEnv("ENDPOINT_SOURCE", "amqp_endpoint_source_address");

    private static String getEnv(String key, String defaultValue) {
        String value = System.getenv(key);
        return value != null ? value : defaultValue;
    }

    // ======== AMQP 1.0 CLIENT ========
    private static class ReceiverHandler extends BaseHandler {
        private final Map<String, String> endpoint;
        private Receiver receiver;
        private SSLContext sslContext;

        public ReceiverHandler(Map<String, String> endpoint) {
            this.endpoint = endpoint;
        }

        public void setSslContext(SSLContext sslContext) {
            this.sslContext = sslContext;
        }

        @Override
        public void onConnectionInit(Event event) {
            logger.fine("Connection initialized");
            Connection connection = event.getConnection();
            connection.setHostname(endpoint.get("host"));
            connection.setContainer("java-subscription-direct-example");
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
            Source source = new Source();
            String sourceAddress = endpoint.get("source");
            source.setAddress(sourceAddress);
            receiver = session.receiver(sourceAddress);
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
            receiver.flow(100); // Grant credit to receive messages
        }

        @Override
        public void onDelivery(Event event) {
            Delivery delivery = event.getDelivery();
            if (delivery.isReadable() && !delivery.isPartial()) {
                try {
                    // Read message data
                    byte[] messageData = new byte[delivery.available()];
                    receiver.recv(messageData, 0, messageData.length);
                    
                    // Decode message
                    Message message = Message.Factory.create();
                    message.decode(messageData, 0, messageData.length);
                    
                    // Extract body
                    String bodyText = "";
                    if (message.getBody() instanceof Data) {
                        Data bodyData = (Data) message.getBody();
                        if (bodyData.getValue() instanceof Binary) {
                            Binary binary = (Binary) bodyData.getValue();
                            bodyText = new String(binary.getArray(), binary.getArrayOffset(), binary.getLength(), StandardCharsets.UTF_8);
                        } else {
                            logger.warning("Unexpected body type: " + bodyData.getValue().getClass().getName());
                            bodyText = bodyData.getValue().toString(); // Fallback to string representation
                        }
                    } else {
                        logger.warning("Unexpected message body type: " + message.getBody().getClass().getName());
                        bodyText = message.getBody().toString(); // Fallback to string representation
                    }
                    
                    // Extract and format application properties
                    Map<String, Object> appProps = new HashMap<>();
                    if (message.getApplicationProperties() != null && message.getApplicationProperties().getValue() != null) {
                        appProps = (Map<String, Object>) message.getApplicationProperties().getValue();
                    }
                    
                    // Format properties in sorted order for consistent logging
                    ObjectMapper mapper = new ObjectMapper();
                    String sortedProperties = mapper.writeValueAsString(new TreeMap<>(appProps));
                    
                    logger.info(String.format("Message received: body='%s', properties=%s", bodyText, sortedProperties));
                    
                    // Settle the delivery
                    delivery.settle();
                    
                    // Grant more credit for next message
                    receiver.flow(1);
                    
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error processing message", e);
                    delivery.settle();
                    receiver.flow(1);
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

    private static void amqpConnectAndListen(Map<String, String> endpoint) throws Exception {
        // Configure SSL
        SSLContext sslContext = createSSLContext();

        // Create handler
        ReceiverHandler handler = new ReceiverHandler(endpoint);
        handler.setSslContext(sslContext);

        // Create reactor
        Reactor reactor = Proton.reactor(handler);

        // Connect to host with SSL and SASL configuration
        String host = endpoint.get("host");
        int port = Integer.parseInt(endpoint.get("port"));

        // Use reactor's connection method with proper SSL/SASL setup
        reactor.connectionToHost(host, port, handler);

        // Run reactor
        reactor.run();
    }

    // ======== DIRECT SUBSCRIBE WITH KNOWN ENDPOINT ========
    private static void directSubscribe() {
        try {
            // Create endpoint from environment variables
            Map<String, String> endpoint = new HashMap<>();
            endpoint.put("host", ENDPOINT_HOST);
            endpoint.put("port", ENDPOINT_PORT);
            endpoint.put("source", ENDPOINT_SOURCE);

            logger.info("Using pre-known endpoint " + endpoint);
            amqpConnectAndListen(endpoint);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "An exception occurred while running direct_subscribe", e);
        }
    }

    // ======== STARTUP AND RUN LOOP ========
    private static void dumpConfig() {
        logger.info("ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM: '" + ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM + "'");
        logger.info("CA_CERTIFICATE_PEM: '" + CA_CERTIFICATE_PEM + "'");
        logger.info("ENDPOINT_HOST: '" + ENDPOINT_HOST + "'");
        logger.info("ENDPOINT_PORT: '" + ENDPOINT_PORT + "'");
        logger.info("ENDPOINT_SOURCE: '" + ENDPOINT_SOURCE + "'");
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
            directSubscribe();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Application error", e);
            e.printStackTrace();
        }

        logger.info("Application stopped");
    }
}