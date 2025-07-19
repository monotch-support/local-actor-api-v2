package com.example;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.qpid.proton.Proton;
import org.apache.qpid.proton.amqp.Binary;
import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.amqp.messaging.Source;
import org.apache.qpid.proton.amqp.messaging.Target;
import org.apache.qpid.proton.engine.BaseHandler;
import org.apache.qpid.proton.engine.Connection;
import org.apache.qpid.proton.engine.Delivery;
import org.apache.qpid.proton.engine.Event;
import org.apache.qpid.proton.engine.Link;
import org.apache.qpid.proton.engine.Sasl;
import org.apache.qpid.proton.engine.Sender;
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class Main {
    private static final Logger logger = Logger.getLogger(Main.class.getName());

    // Configuration by environment variables
    private static final String ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM = getEnv("ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM", "pem_with_x509_certificate_chain_and_private_key");
    private static final String CA_CERTIFICATE_PEM = getEnv("CA_CERTIFICATE_PEM", "pem_with_x509_certificate");
    private static final String MESSAGE_APPLICATION_PROPERTIES_JSON = getEnv("MESSAGE_APPLICATION_PROPERTIES_JSON", "message_application_properties_json");

    // Pre-known endpoint information
    private static final String ENDPOINT_HOST = getEnv("ENDPOINT_HOST", "amqp_endpoint_host");
    private static final String ENDPOINT_PORT = getEnv("ENDPOINT_PORT", "amqp_endpoint_port");
    private static final String ENDPOINT_TARGET = getEnv("ENDPOINT_TARGET", "amqp_endpoint_target_address");

    private static String getEnv(String key, String defaultValue) {
        String value = System.getenv(key);
        return value != null ? value : defaultValue;
    }

    // ======== AMQP 1.0 CLIENT ========
    private static class SenderHandler extends BaseHandler {
        private final Map<String, String> endpoint;
        private final AtomicInteger messageCount = new AtomicInteger(0);
        private Sender sender;
        private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
        private SSLContext sslContext;

        public SenderHandler(Map<String, String> endpoint) {
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
            connection.setContainer("java-delivery-direct-example");
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
            Target target = new Target();
            String targetAddress = endpoint.get("target");
            target.setAddress(targetAddress);
            sender = session.sender(targetAddress);
            sender.setTarget(target);
            Source source = new Source();
            sender.setSource(source);
            sender.open();
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
            logger.fine("Sender link opened, ready to send messages");
            if (event.getLink() instanceof Sender && event.getSender().getCredit() > 0) {
                sendMessage();
            }
        }

        @Override
        public void onDelivery(Event event) {
            Delivery delivery = event.getDelivery();
            if (delivery.getRemoteState() != null) {
                delivery.settle();
                // Schedule next message after 1 second
                event.getReactor().schedule(1000, this);
            }
        }

        @Override
        public void onTimerTask(Event event) {
            if (sender != null && sender.getCredit() > 0) {
                sendMessage();
            }
        }

        private void sendMessage() {
            try {
                // Increment message counter
                int count = messageCount.incrementAndGet();
                // Create dynamic message content with counter and timestamp
                String bodyText = String.format("Hello World! Message #%d at %s", count, timeFormat.format(new Date()));

                // Create message
                Message message = Message.Factory.create();
                message.setBody(new Data(new Binary(bodyText.getBytes(StandardCharsets.UTF_8))));

                // Parse and set application properties
                ObjectMapper mapper = new ObjectMapper();

                Map<String, Object> properties = mapper.readValue(MESSAGE_APPLICATION_PROPERTIES_JSON,
                        new TypeReference<Map<String, Object>>() {
                        });
                message.setApplicationProperties(new ApplicationProperties(properties));

                // Format properties for logging
                String sortedProperties = mapper.writeValueAsString(new TreeMap<>(properties));
                logger.info(String.format("Sending message: body='%s', properties=%s", bodyText, sortedProperties));

                // Send message
                byte[] encodedMessage = new byte[1024];
                int encodedSize = message.encode(encodedMessage, 0, encodedMessage.length);
                Delivery delivery = sender.delivery(new byte[0]);
                sender.send(encodedMessage, 0, encodedSize);
                sender.advance();
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error sending message", e);
                e.printStackTrace();
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


    private static void amqpConnectAndPublish(Map<String, String> endpoint) throws Exception {
        // Configure SSL
        SSLContext sslContext = createSSLContext();

        // Create handler
        SenderHandler handler = new SenderHandler(endpoint);
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

    // ======== DIRECT PUBLISH WITH KNOWN ENDPOINT ========
    private static void directPublish() {
        try {
            // Create endpoint from environment variables
            Map<String, String> endpoint = new HashMap<>();
            endpoint.put("host", ENDPOINT_HOST);
            endpoint.put("port", ENDPOINT_PORT);
            endpoint.put("target", ENDPOINT_TARGET);

            logger.info("Using pre-known endpoint " + endpoint);
            amqpConnectAndPublish(endpoint);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "An exception occurred while running direct_publish", e);
        }
    }

    // ======== STARTUP AND RUN LOOP ========
    private static void dumpConfig() {
        logger.info("ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM: '" + ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM + "'");
        logger.info("CA_CERTIFICATE_PEM: '" + CA_CERTIFICATE_PEM + "'");
        logger.info("MESSAGE_APPLICATION_PROPERTIES_JSON: '" + MESSAGE_APPLICATION_PROPERTIES_JSON + "'");
        logger.info("ENDPOINT_HOST: '" + ENDPOINT_HOST + "'");
        logger.info("ENDPOINT_PORT: '" + ENDPOINT_PORT + "'");
        logger.info("ENDPOINT_TARGET: '" + ENDPOINT_TARGET + "'");
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
            directPublish();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Application error", e);
            e.printStackTrace();
        }

        logger.info("Application stopped");
    }
}