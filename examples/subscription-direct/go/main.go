package main

import (
	"context"
	"crypto/tls"
	"crypto/x509"
	"encoding/json"
	"fmt"
	"log"
	"os"

	"github.com/Azure/go-amqp"
)

// Configuration by environment variables
var (
	ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM = getEnv("ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM", "pem_with_x509_certificate_chain_and_private_key")
	CA_CERTIFICATE_PEM                  = getEnv("CA_CERTIFICATE_PEM", "pem_with_x509_certificate")

	// Pre-known endpoint information
	ENDPOINT_HOST   = getEnv("ENDPOINT_HOST", "amqp_endpoint_host")
	ENDPOINT_PORT   = getEnv("ENDPOINT_PORT", "amqp_endpoint_port")
	ENDPOINT_SOURCE = getEnv("ENDPOINT_SOURCE", "amqp_endpoint_source_address")
)

func getEnv(key, defaultValue string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return defaultValue
}

// ======== AMQP 1.0 CLIENT ========
func amqpCreateTLSConfig() (*tls.Config, error) {
	// Load client certificate and key
	cert, err := tls.LoadX509KeyPair(ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM, ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM)
	if err != nil {
		return nil, fmt.Errorf("failed to load client certificate: %v", err)
	}

	// Load CA certificate
	caCert, err := os.ReadFile(CA_CERTIFICATE_PEM)
	if err != nil {
		return nil, fmt.Errorf("failed to read CA certificate: %v", err)
	}

	// Create certificate pool with CA
	caCertPool := x509.NewCertPool()
	caCertPool.AppendCertsFromPEM(caCert)

	tlsConfig := &tls.Config{
		Certificates:       []tls.Certificate{cert},
		RootCAs:            caCertPool,
		InsecureSkipVerify: false,
		MinVersion:         tls.VersionTLS13,
	}

	return tlsConfig, nil
}

type Receiver struct {
	client   *amqp.Conn
	session  *amqp.Session
	receiver *amqp.Receiver
	endpoint map[string]string
}

func (r *Receiver) connect() error {
	// Step 1: Create TLS configuration
	tlsConfig, err := amqpCreateTLSConfig()
	if err != nil {
		return err
	}

	// Create AMQP URL
	amqpURL := fmt.Sprintf("amqps://%s:%s", r.endpoint["host"], r.endpoint["port"])
	log.Printf("Connecting to %s", amqpURL)

	// Step 2: Connect with TLS
	opts := &amqp.ConnOptions{
		TLSConfig:     tlsConfig,
		SASLType:      amqp.SASLTypeExternal(""),
		IdleTimeout:   0,
		MaxFrameSize:  65536,
		ContainerID:   "",
		HostName:      r.endpoint["host"],
		Properties:    nil,
	}

	ctx := context.Background()
	client, err := amqp.Dial(ctx, amqpURL, opts)
	if err != nil {
		return fmt.Errorf("failed to connect: %v", err)
	}
	r.client = client

	// Create session
	session, err := client.NewSession(ctx, nil)
	if err != nil {
		return fmt.Errorf("failed to create session: %v", err)
	}
	r.session = session

	// Step 3: Create receiver link using the source address
	receiver, err := session.NewReceiver(ctx, r.endpoint["source"], nil)
	if err != nil {
		return fmt.Errorf("failed to create receiver: %v", err)
	}
	r.receiver = receiver

	log.Println("Container reactor started")
	return nil
}

func (r *Receiver) receiveMessages() {
	ctx := context.Background()
	
	for {
		// Receive message with timeout
		msg, err := r.receiver.Receive(ctx, nil)
		if err != nil {
			log.Printf("Error receiving message: %v", err)
			continue
		}

		// Decode binary body as UTF-8
		var bodyText string
		if len(msg.Data) > 0 && len(msg.Data[0]) > 0 {
			// Decode binary data as UTF-8 string - this handles the binary payload
			bodyText = string(msg.Data[0])
		} else if msg.Value != nil {
			// Handle other message value types (string, bytes, etc.)
			switch v := msg.Value.(type) {
			case []byte:
				bodyText = string(v)
			case string:
				bodyText = v
			default:
				bodyText = fmt.Sprintf("%v", v)
			}
		} else {
			bodyText = ""
		}

		// Format application properties in sorted order
		var propsMap map[string]interface{}
		if msg.ApplicationProperties != nil {
			propsMap = make(map[string]interface{})
			for k, v := range msg.ApplicationProperties {
				propsMap[k] = v
			}
		} else {
			propsMap = make(map[string]interface{})
		}

		// Marshal with sorted keys
		propsJSON, _ := json.Marshal(propsMap)
		log.Printf("Message received: body='%s', properties=%s", bodyText, string(propsJSON))

		// Accept the message
		err = r.receiver.AcceptMessage(ctx, msg)
		if err != nil {
			log.Printf("Error accepting message: %v", err)
		}
	}
}

func (r *Receiver) close() {
	if r.receiver != nil {
		r.receiver.Close(context.Background())
	}
	if r.session != nil {
		r.session.Close(context.Background())
	}
	if r.client != nil {
		r.client.Close()
	}
}

func amqpConnectAndListen(endpoint map[string]string) error {
	receiver := &Receiver{
		endpoint: endpoint,
	}

	err := receiver.connect()
	if err != nil {
		return err
	}
	defer receiver.close()

	// Start receiving messages
	receiver.receiveMessages()
	return nil
}

// ======== DIRECT SUBSCRIBE WITH KNOWN ENDPOINT ========
func directSubscribe() {
	// Create endpoint from environment variables
	endpoint := map[string]string{
		"host":   ENDPOINT_HOST,
		"port":   ENDPOINT_PORT,
		"source": ENDPOINT_SOURCE,
	}

	log.Printf("Using pre-known endpoint %v", endpoint)
	err := amqpConnectAndListen(endpoint)
	if err != nil {
		log.Printf("An exception occurred while running direct_subscribe: %v", err)
	}
}

// ======== STARTUP AND RUN LOOP ========
func dumpConfig() {
	log.Printf("ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM: '%s'", ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM)
	log.Printf("CA_CERTIFICATE_PEM: '%s'", CA_CERTIFICATE_PEM)
	log.Printf("ENDPOINT_HOST: '%s'", ENDPOINT_HOST)
	log.Printf("ENDPOINT_PORT: '%s'", ENDPOINT_PORT)
	log.Printf("ENDPOINT_SOURCE: '%s'", ENDPOINT_SOURCE)
}

func configureLogging() {
	log.SetFlags(log.Ldate | log.Ltime | log.Lmicroseconds)
	log.SetPrefix("INFO ")
}

func main() {
	configureLogging()
	log.Println("Starting application")
	dumpConfig()
	directSubscribe()
}