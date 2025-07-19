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
	ACTOR_API_HOST                   = getEnv("ACTOR_API_HOST", "hostname_of_the_actor_api")
	ACTOR_API_PORT                   = getEnv("ACTOR_API_PORT", "port_of_the_actor_api")
	ACTOR_API_SUBSCRIPTION_SELECTOR  = getEnv("ACTOR_API_SUBSCRIPTION_SELECTOR", "selector_of_the_subscription")
	ACTOR_COMMON_NAME               = getEnv("ACTOR_COMMON_NAME", "cn_of_the_actor_client_certificate")
	ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM = getEnv("ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM", "pem_with_x509_certificate_chain_and_private_key")
	CA_CERTIFICATE_PEM              = getEnv("CA_CERTIFICATE_PEM", "pem_with_x509_certificate")
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

func createHTTPClient() *http.Client {
	cert, err := tls.LoadX509KeyPair(ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM, ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM)
	if err != nil {
		log.Fatalf("Failed to load client certificate: %v", err)
	}

	caCert, err := os.ReadFile(CA_CERTIFICATE_PEM)
	if err != nil {
		log.Fatalf("Failed to read CA certificate: %v", err)
	}

	caCertPool := x509.NewCertPool()
	caCertPool.AppendCertsFromPEM(caCert)

	tlsConfig := &tls.Config{
		Certificates: []tls.Certificate{cert},
		RootCAs:      caCertPool,
		MinVersion:   tls.VersionTLS13,
	}

	return &http.Client{
		Transport: &http.Transport{
			TLSClientConfig: tlsConfig,
		},
	}
}

func apiGet(endpoint string) (*http.Response, error) {
	client := createHTTPClient()
	return client.Get(apiURL(endpoint))
}

func apiPost(endpoint string, jsonData map[string]interface{}) (*http.Response, error) {
	client := createHTTPClient()
	
	data, err := json.Marshal(jsonData)
	if err != nil {
		return nil, err
	}
	
	req, err := http.NewRequest("POST", apiURL(endpoint), strings.NewReader(string(data)))
	if err != nil {
		return nil, err
	}
	
	req.Header.Set("Content-Type", "application/json")
	return client.Do(req)
}

func apiDelete(endpoint string) (*http.Response, error) {
	client := createHTTPClient()
	req, err := http.NewRequest("DELETE", apiURL(endpoint), nil)
	if err != nil {
		return nil, err
	}
	return client.Do(req)
}

func apiGetSubscription(id string) (*http.Response, error) {
	return apiGet(fmt.Sprintf("subscriptions/%s", id))
}

func apiDeleteSubscription(id string) (*http.Response, error) {
	return apiDelete(fmt.Sprintf("subscriptions/%s", id))
}

func apiCreateSubscription() (*http.Response, error) {
	jsonData := map[string]interface{}{
		"selector": ACTOR_API_SUBSCRIPTION_SELECTOR,
	}
	return apiPost("subscriptions", jsonData)
}

// ======== AMQP 1.0 CLIENT ========
func amqpCreateTLSConfig() *tls.Config {
	cert, err := tls.LoadX509KeyPair(ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM, ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM)
	if err != nil {
		log.Fatalf("Failed to load client certificate: %v", err)
	}

	caCert, err := os.ReadFile(CA_CERTIFICATE_PEM)
	if err != nil {
		log.Fatalf("Failed to read CA certificate: %v", err)
	}

	caCertPool := x509.NewCertPool()
	caCertPool.AppendCertsFromPEM(caCert)

	return &tls.Config{
		Certificates: []tls.Certificate{cert},
		RootCAs:      caCertPool,
		MinVersion:   tls.VersionTLS13,
	}
}

func amqpConnectAndListen(endpoint map[string]interface{}) {
	ctx := context.Background()
	
	host := endpoint["host"].(string)
	port := int(endpoint["port"].(float64))
	source := endpoint["source"].(string)
	
	amqpURL := fmt.Sprintf("amqps://%s:%d", host, port)
	
	conn, err := amqp.Dial(ctx, amqpURL, &amqp.ConnOptions{
		TLSConfig: amqpCreateTLSConfig(),
		SASLType:  amqp.SASLTypeExternal(""),
	})
	if err != nil {
		log.Fatalf("Failed to connect to AMQP server: %v", err)
	}
	defer conn.Close()

	session, err := conn.NewSession(ctx, nil)
	if err != nil {
		log.Fatalf("Failed to create AMQP session: %v", err)
	}
	defer session.Close(ctx)

	receiver, err := session.NewReceiver(ctx, source, nil)
	if err != nil {
		log.Fatalf("Failed to create AMQP receiver: %v", err)
	}
	defer receiver.Close(ctx)

	log.Printf("Starting to receive messages from %s. Press Ctrl+C to stop.", source)

	for {
		msg, err := receiver.Receive(ctx, nil)
		if err != nil {
			log.Printf("Error receiving message: %v", err)
			break
		}

		// Decode binary body as UTF-8
		var bodyText string
		if len(msg.Data) > 0 {
			bodyBytes := msg.Data[0]
			bodyText = string(bodyBytes)
		} else {
			bodyText = ""
		}

		// Format application properties in sorted order
		appPropsJSON := "{}"
		if msg.ApplicationProperties != nil {
			appPropsBytes, err := json.Marshal(msg.ApplicationProperties)
			if err == nil {
				appPropsJSON = string(appPropsBytes)
			}
		}

		log.Printf("Message received: body='%s', properties=%s", bodyText, appPropsJSON)

		err = receiver.AcceptMessage(ctx, msg)
		if err != nil {
			log.Printf("Error accepting message: %v", err)
		}
	}
}

// ======== CREATE AND CONSUME A SUBSCRIPTION ========
func logJSON(message string, data interface{}) {
	jsonBytes, err := json.MarshalIndent(data, "", "  ")
	if err != nil {
		log.Printf("%s: error marshaling JSON: %v", message, err)
		return
	}
	log.Printf("%s: %s", message, string(jsonBytes))
}

func subscribeAndReceive() error {
	// Step 1: create a subscription using the actor API
	subscriptionCreateResponse, err := apiCreateSubscription()
	if err != nil {
		return fmt.Errorf("failed to create subscription: %v", err)
	}
	defer subscriptionCreateResponse.Body.Close()

	subscriptionCreateResponseBody, err := io.ReadAll(subscriptionCreateResponse.Body)
	if err != nil {
		return fmt.Errorf("failed to read subscription create response: %v", err)
	}

	var subscriptionCreateResponseJSON map[string]interface{}
	err = json.Unmarshal(subscriptionCreateResponseBody, &subscriptionCreateResponseJSON)
	if err != nil {
		return fmt.Errorf("failed to parse subscription create response: %v", err)
	}

	logJSON("Subscription create response", subscriptionCreateResponseJSON)

	if subscriptionCreateResponse.StatusCode == http.StatusOK || subscriptionCreateResponse.StatusCode == http.StatusCreated {
		// Step 2: get the subscription status
		subscriptionID := subscriptionCreateResponseJSON["id"].(string)
		subscriptionStatusResponse, err := apiGetSubscription(subscriptionID)
		if err != nil {
			return fmt.Errorf("failed to get subscription status: %v", err)
		}
		defer subscriptionStatusResponse.Body.Close()

		subscriptionStatusResponseBody, err := io.ReadAll(subscriptionStatusResponse.Body)
		if err != nil {
			return fmt.Errorf("failed to read subscription status response: %v", err)
		}

		var subscriptionStatusResponseJSON map[string]interface{}
		err = json.Unmarshal(subscriptionStatusResponseBody, &subscriptionStatusResponseJSON)
		if err != nil {
			return fmt.Errorf("failed to parse subscription status response: %v", err)
		}

		logJSON(fmt.Sprintf("Subscription %s status response", subscriptionID), subscriptionStatusResponseJSON)
		subscriptionStatus := subscriptionStatusResponseJSON["status"].(string)

		// Step 3: while the subscription status is "REQUESTED", keep getting the status
		for subscriptionStatus == "REQUESTED" {
			time.Sleep(2 * time.Second)
			subscriptionStatusResponse, err = apiGetSubscription(subscriptionID)
			if err != nil {
				return fmt.Errorf("failed to get subscription status during polling: %v", err)
			}
			defer subscriptionStatusResponse.Body.Close()

			subscriptionStatusResponseBody, err = io.ReadAll(subscriptionStatusResponse.Body)
			if err != nil {
				return fmt.Errorf("failed to read subscription status response during polling: %v", err)
			}

			err = json.Unmarshal(subscriptionStatusResponseBody, &subscriptionStatusResponseJSON)
			if err != nil {
				return fmt.Errorf("failed to parse subscription status response during polling: %v", err)
			}

			subscriptionStatus = subscriptionStatusResponseJSON["status"].(string)
		}

		logJSON(fmt.Sprintf("Subscription %s status response", subscriptionID), subscriptionStatusResponseJSON)

		// Step 4a: if the status is "CREATED", connect to the endpoint and start the AMQP receiver
		if subscriptionStatus == "CREATED" {
			endpoints := subscriptionStatusResponseJSON["endpoints"].([]interface{})
			if len(endpoints) > 0 {
				endpoint := endpoints[0].(map[string]interface{})
				log.Printf("Using endpoint %v", endpoint)
				amqpConnectAndListen(endpoint)
			} else {
				log.Printf("No endpoints available for subscription %s", subscriptionID)
			}
		} else {
			// Step 4b: if the status is not "CREATED" warn log and do nothing
			log.Printf("Unable to use subscription %s", subscriptionID)
		}
	}

	return nil
}

// ======== STARTUP AND RUN LOOP ========
func dumpConfig() {
	log.Printf("ACTOR_API_HOST: '%s'", ACTOR_API_HOST)
	log.Printf("ACTOR_API_PORT: '%s'", ACTOR_API_PORT)
	log.Printf("ACTOR_API_SUBSCRIPTION_SELECTOR: '%s'", ACTOR_API_SUBSCRIPTION_SELECTOR)
	log.Printf("ACTOR_COMMON_NAME: '%s'", ACTOR_COMMON_NAME)
	log.Printf("ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM: '%s'", ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM)
	log.Printf("CA_CERTIFICATE_PEM: '%s'", CA_CERTIFICATE_PEM)
}

func main() {
	log.Println("Starting application")
	dumpConfig()

	err := subscribeAndReceive()
	if err != nil {
		log.Printf("An exception occurred while running subscribe_and_receive: %v", err)
	}

	log.Println("Application stopped")
}