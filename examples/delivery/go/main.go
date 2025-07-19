package main

import (
	"context"
	"crypto/tls"
	"crypto/x509"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"strings"
	"time"

	"github.com/Azure/go-amqp"
)

// Configuration by environment variables
var (
	ACTOR_API_HOST                      = getEnv("ACTOR_API_HOST", "hostname_of_the_actor_api")
	ACTOR_API_PORT                      = getEnv("ACTOR_API_PORT", "port_of_the_actor_api")
	ACTOR_API_DELIVERY_SELECTOR         = getEnv("ACTOR_API_DELIVERY_SELECTOR", "selector_of_the_delivery")
	ACTOR_COMMON_NAME                   = getEnv("ACTOR_COMMON_NAME", "cn_of_the_actor_client_certificate")
	ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM = getEnv("ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM", "pem_with_x509_certificate_chain_and_private_key")
	CA_CERTIFICATE_PEM                  = getEnv("CA_CERTIFICATE_PEM", "pem_with_x509_certificate")
	MESSAGE_APPLICATION_PROPERTIES_JSON = getEnv("MESSAGE_APPLICATION_PROPERTIES_JSON", "message_application_properties_json")
)

func getEnv(key, defaultValue string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return defaultValue
}

// ======== ACTOR API FUNCTIONS ========
func apiURL(endpoint string) string {
	return fmt.Sprintf("https://%s:%s/%s/%s", ACTOR_API_HOST, ACTOR_API_PORT, ACTOR_COMMON_NAME, endpoint)
}

func createHTTPClient() (*http.Client, error) {
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
		Certificates: []tls.Certificate{cert},
		RootCAs:      caCertPool,
		MinVersion:   tls.VersionTLS13,
	}

	client := &http.Client{
		Transport: &http.Transport{
			TLSClientConfig: tlsConfig,
		},
	}

	return client, nil
}

func apiGet(endpoint string) (*http.Response, error) {
	client, err := createHTTPClient()
	if err != nil {
		return nil, err
	}

	return client.Get(apiURL(endpoint))
}

func apiPost(endpoint string, jsonData map[string]interface{}) (*http.Response, error) {
	client, err := createHTTPClient()
	if err != nil {
		return nil, err
	}

	jsonBytes, err := json.Marshal(jsonData)
	if err != nil {
		return nil, err
	}

	return client.Post(apiURL(endpoint), "application/json", strings.NewReader(string(jsonBytes)))
}

func apiDelete(endpoint string) (*http.Response, error) {
	client, err := createHTTPClient()
	if err != nil {
		return nil, err
	}

	req, err := http.NewRequest("DELETE", apiURL(endpoint), nil)
	if err != nil {
		return nil, err
	}

	return client.Do(req)
}

func apiGetDelivery(id string) (*http.Response, error) {
	return apiGet(fmt.Sprintf("deliveries/%s", id))
}

func apiDeleteDelivery(id string) (*http.Response, error) {
	return apiDelete(fmt.Sprintf("deliveries/%s", id))
}

func apiCreateDelivery() (*http.Response, error) {
	jsonData := map[string]interface{}{
		"selector": ACTOR_API_DELIVERY_SELECTOR,
	}
	return apiPost("deliveries", jsonData)
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
	endpoint     map[string]interface{}
	sending      bool
	messageCount int
}

func (s *Sender) connect() error {
	// Step 1: Create TLS configuration
	tlsConfig, err := amqpCreateTLSConfig()
	if err != nil {
		return err
	}

	// Create AMQP URL
	host := s.endpoint["host"].(string)
	port := s.endpoint["port"]
	amqpURL := fmt.Sprintf("amqps://%s:%v", host, port)
	log.Printf("Connecting to %s", amqpURL)

	// Step 2: Connect with TLS
	opts := &amqp.ConnOptions{
		TLSConfig:     tlsConfig,
		SASLType:      amqp.SASLTypeExternal(""),
		IdleTimeout:   0,
		MaxFrameSize:  65536,
		ContainerID:   "",
		HostName:      host,
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
	target := s.endpoint["target"].(string)
	sender, err := session.NewSender(ctx, target, nil)
	if err != nil {
		return fmt.Errorf("failed to create sender: %v", err)
	}
	s.sender = sender

	log.Println("Container reactor started")
	s.sending = true
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
	bodyBinary := []byte(bodyText)

	// Create message
	msg := &amqp.Message{
		Data:                  [][]byte{bodyBinary},
		ApplicationProperties: properties,
	}

	ctx := context.Background()
	// Format message for logging with sorted properties
	propsMap := make(map[string]interface{})
	for k, v := range properties {
		propsMap[k] = v
	}
	propsJSON, _ := json.Marshal(propsMap)
	log.Printf("Sending message: body='%s', properties=%s", bodyText, string(propsJSON))

	// Send message
	err = s.sender.Send(ctx, msg, nil)
	if err != nil {
		return fmt.Errorf("failed to send message: %v", err)
	}

	log.Printf("Message settled")
	return nil
}

func (s *Sender) run() {
	for s.sending {
		err := s.sendMessage()
		if err != nil {
			log.Printf("Error sending message: %v", err)
		}
		time.Sleep(1 * time.Second)
	}
}

func (s *Sender) close() {
	s.sending = false
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

func amqpConnectAndPublish(endpoint map[string]interface{}) error {
	sender := &Sender{
		endpoint: endpoint,
	}

	err := sender.connect()
	if err != nil {
		return err
	}
	defer sender.close()

	// Run sender
	sender.run()
	return nil
}

// ======== CREATE AND PUBLISH INTO A DELIVERY ========
func logJSON(message string, jsonData interface{}) {
	jsonBytes, _ := json.MarshalIndent(jsonData, "", "  ")
	log.Printf("%s: %s", message, string(jsonBytes))
}

func parseResponseJSON(resp *http.Response) (map[string]interface{}, error) {
	defer resp.Body.Close()
	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, err
	}

	var result map[string]interface{}
	err = json.Unmarshal(body, &result)
	return result, err
}

func createAndPublish() {
	// Step 1: create a delivery using the actor API
	deliveryCreateResponse, err := apiCreateDelivery()
	if err != nil {
		log.Printf("Error creating delivery: %v", err)
		return
	}

	deliveryCreateResponseJSON, err := parseResponseJSON(deliveryCreateResponse)
	if err != nil {
		log.Printf("Error parsing delivery create response: %v", err)
		return
	}

	logJSON("Delivery create response", deliveryCreateResponseJSON)

	if deliveryCreateResponse.StatusCode >= 200 && deliveryCreateResponse.StatusCode < 300 {
		// Step 2: get the delivery status
		deliveryID := deliveryCreateResponseJSON["id"].(string)
		deliveryStatusResponse, err := apiGetDelivery(deliveryID)
		if err != nil {
			log.Printf("Error getting delivery status: %v", err)
			return
		}

		deliveryStatusResponseJSON, err := parseResponseJSON(deliveryStatusResponse)
		if err != nil {
			log.Printf("Error parsing delivery status response: %v", err)
			return
		}

		logJSON(fmt.Sprintf("Delivery %s status response", deliveryID), deliveryStatusResponseJSON)
		deliveryStatus := deliveryStatusResponseJSON["status"].(string)

		// Step 3: while the delivery status is "REQUESTED", keep getting the status
		for deliveryStatus == "REQUESTED" {
			time.Sleep(2 * time.Second)
			deliveryStatusResponse, err = apiGetDelivery(deliveryID)
			if err != nil {
				log.Printf("Error polling delivery status: %v", err)
				return
			}

			deliveryStatusResponseJSON, err = parseResponseJSON(deliveryStatusResponse)
			if err != nil {
				log.Printf("Error parsing delivery status response: %v", err)
				return
			}
			deliveryStatus = deliveryStatusResponseJSON["status"].(string)
		}

		logJSON(fmt.Sprintf("Delivery %s status response", deliveryID), deliveryStatusResponseJSON)

		// Step 4a: if the status is "CREATED", get the endpoint information from the status response and use the endpoint with the AMQP 1.0 client
		if deliveryStatus == "CREATED" {
			// NOTE to keep things simple, this code assumes that this response contains exactly one endpoint!
			endpoints := deliveryStatusResponseJSON["endpoints"].([]interface{})
			endpoint := endpoints[0].(map[string]interface{})
			log.Printf("Using endpoint %v", endpoint)
			err = amqpConnectAndPublish(endpoint)
			if err != nil {
				log.Printf("Error in AMQP connection: %v", err)
			}
		} else {
			// Step 4b: if the status is not "CREATED" warn log and do nothing
			log.Printf("Unable to use delivery %s", deliveryID)
		}
	}
}

// ======== STARTUP AND RUN LOOP ========
func dumpConfig() {
	log.Printf("ACTOR_API_HOST: '%s'", ACTOR_API_HOST)
	log.Printf("ACTOR_API_PORT: '%s'", ACTOR_API_PORT)
	log.Printf("ACTOR_API_DELIVERY_SELECTOR: '%s'", ACTOR_API_DELIVERY_SELECTOR)
	log.Printf("ACTOR_COMMON_NAME: '%s'", ACTOR_COMMON_NAME)
	log.Printf("ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM: '%s'", ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM)
	log.Printf("CA_CERTIFICATE_PEM: '%s'", CA_CERTIFICATE_PEM)
	log.Printf("MESSAGE_APPLICATION_PROPERTIES_JSON: '%s'", MESSAGE_APPLICATION_PROPERTIES_JSON)
}

func configureLogging() {
	log.SetFlags(log.Ldate | log.Ltime | log.Lmicroseconds)
	log.SetPrefix("INFO ")
}

func main() {
	configureLogging()
	log.Println("Starting application")
	dumpConfig()
	createAndPublish()
}