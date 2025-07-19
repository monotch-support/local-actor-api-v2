package main

import (
	"context"
	"crypto/tls"
	"crypto/x509"
	"encoding/json"
	"fmt"
	"log"
	"os"
	"time"

	"github.com/Azure/go-amqp"
)

// Configuration by environment variables
var (
	ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM = getEnv("ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM", "pem_with_x509_certificate_chain_and_private_key")
	CA_CERTIFICATE_PEM                  = getEnv("CA_CERTIFICATE_PEM", "pem_with_x509_certificate")
	MESSAGE_APPLICATION_PROPERTIES_JSON = getEnv("MESSAGE_APPLICATION_PROPERTIES_JSON", "message_application_properties_json")

	// Pre-known endpoint information
	ENDPOINT_HOST   = getEnv("ENDPOINT_HOST", "amqp_endpoint_host")
	ENDPOINT_PORT   = getEnv("ENDPOINT_PORT", "amqp_endpoint_port")
	ENDPOINT_TARGET = getEnv("ENDPOINT_TARGET", "amqp_endpoint_target_address")
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

type Sender struct {
	client       *amqp.Conn
	session      *amqp.Session
	sender       *amqp.Sender
	endpoint     map[string]string
	messageCount int
}

func (s *Sender) connect() error {
	// Step 1: Create TLS configuration
	tlsConfig, err := amqpCreateTLSConfig()
	if err != nil {
		return err
	}

	// Create AMQP URL
	amqpURL := fmt.Sprintf("amqps://%s:%s", s.endpoint["host"], s.endpoint["port"])
	log.Printf("Connecting to %s", amqpURL)

	// Step 2: Connect with TLS
	opts := &amqp.ConnOptions{
		TLSConfig:     tlsConfig,
		SASLType:      amqp.SASLTypeExternal(""),
		IdleTimeout:   0,
		MaxFrameSize:  65536,
		ContainerID:   "",
		HostName:      s.endpoint["host"],
		Properties:    nil,
	}

	ctx := context.Background()
	client, err := amqp.Dial(ctx, amqpURL, opts)
	if err != nil {
		return fmt.Errorf("failed to connect: %v", err)
	}
	s.client = client

	// Create session
	session, err := client.NewSession(ctx, nil)
	if err != nil {
		return fmt.Errorf("failed to create session: %v", err)
	}
	s.session = session

	// Step 3: Create sender link using the target address
	sender, err := session.NewSender(ctx, s.endpoint["target"], nil)
	if err != nil {
		return fmt.Errorf("failed to create sender: %v", err)
	}
	s.sender = sender

	log.Println("Container reactor started")
	return nil
}

func (s *Sender) sendMessage() error {
	// Parse message properties from JSON
	var properties map[string]interface{}
	err := json.Unmarshal([]byte(MESSAGE_APPLICATION_PROPERTIES_JSON), &properties)
	if err != nil {
		return fmt.Errorf("failed to parse message properties: %v", err)
	}

	// Increment message counter
	s.messageCount++
	// Create dynamic message content with counter and timestamp
	bodyText := fmt.Sprintf("Hello World! Message #%d at %s", s.messageCount, time.Now().Format("15:04:05"))

	// Create message
	msg := &amqp.Message{
		Data: [][]byte{[]byte(bodyText)},
		ApplicationProperties: properties,
	}

	ctx := context.Background()
	// Format message for logging with sorted properties
	// Create a map to ensure consistent ordering
	propsMap := make(map[string]interface{})
	for k, v := range msg.ApplicationProperties {
		propsMap[k] = v
	}
	
	// Marshal with sorted keys
	propsJSON, _ := json.Marshal(propsMap)
	log.Printf("Sending message: body='%s', properties=%s", bodyText, string(propsJSON))

	// Send message
	err = s.sender.Send(ctx, msg, nil)
	if err != nil {
		return fmt.Errorf("failed to send message: %v", err)
	}

	return nil
}

func (s *Sender) run() {
	for {
		err := s.sendMessage()
		if err != nil {
			log.Printf("Error sending message: %v", err)
		}
		time.Sleep(1 * time.Second)
	}
}

func (s *Sender) close() {
	if s.sender != nil {
		s.sender.Close(context.Background())
	}
	if s.session != nil {
		s.session.Close(context.Background())
	}
	if s.client != nil {
		s.client.Close()
	}
}

func amqpConnectAndPublish(endpoint map[string]string) error {
	sender := &Sender{
		endpoint: endpoint,
	}

	err := sender.connect()
	if err != nil {
		return err
	}
	defer sender.close()

	// Run sender in continuous loop
	sender.run()
	return nil
}

// ======== DIRECT PUBLISH WITH KNOWN ENDPOINT ========
func directPublish() {
	// Create endpoint from environment variables
	endpoint := map[string]string{
		"host":   ENDPOINT_HOST,
		"port":   ENDPOINT_PORT,
		"target": ENDPOINT_TARGET,
	}

	log.Printf("Using pre-known endpoint %v", endpoint)
	err := amqpConnectAndPublish(endpoint)
	if err != nil {
		log.Printf("An exception occurred while running direct_publish: %v", err)
	}
}

// ======== STARTUP AND RUN LOOP ========
func dumpConfig() {
	log.Printf("ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM: '%s'", ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM)
	log.Printf("CA_CERTIFICATE_PEM: '%s'", CA_CERTIFICATE_PEM)
	log.Printf("MESSAGE_APPLICATION_PROPERTIES_JSON: '%s'", MESSAGE_APPLICATION_PROPERTIES_JSON)
	log.Printf("ENDPOINT_HOST: '%s'", ENDPOINT_HOST)
	log.Printf("ENDPOINT_PORT: '%s'", ENDPOINT_PORT)
	log.Printf("ENDPOINT_TARGET: '%s'", ENDPOINT_TARGET)
}

func configureLogging() {
	log.SetFlags(log.Ldate | log.Ltime | log.Lmicroseconds)
	log.SetPrefix("INFO ")
}

func main() {
	configureLogging()
	log.Println("Starting application")
	dumpConfig()
	directPublish()
}