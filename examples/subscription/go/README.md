# Go Subscription Example

This example demonstrates how to create a subscription and receive messages using the Local Actor API v2 with Go.

## Prerequisites

- Go 1.18 or later
- Valid client certificate and private key in PEM format
- CA certificate in PEM format
- Access to a Local Actor API v2 instance

## Dependencies

This example uses the `github.com/Azure/go-amqp` library for AMQP 1.0 connectivity.

## Environment Variables

Set the following environment variables before running the example:

- `ACTOR_API_HOST`: Hostname of the Actor API instance
- `ACTOR_API_PORT`: Port of the Actor API instance  
- `ACTOR_API_SUBSCRIPTION_SELECTOR`: Selector for the subscription to create
- `ACTOR_COMMON_NAME`: Common name from the actor client certificate
- `ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM`: Path to client certificate and private key in PEM format
- `CA_CERTIFICATE_PEM`: Path to CA certificate in PEM format

## Building and Running

### Option 1: Using the shell script

```bash
./example.sh
```

### Option 2: Manual build and run

```bash
go build -o subscription main.go
./subscription
```

### Option 3: Using Docker

```bash
docker build -t subscription-example .
docker run -e ACTOR_API_HOST=... -e ACTOR_API_PORT=... subscription-example
```

## How it works

1. **CREATE**: Create a subscription via the Actor API
2. **POLL**: Poll the subscription status until it's ready
3. **CONNECT**: Connect to the provided AMQP endpoint
4. **USE**: Receive and process messages

The application will continue receiving messages until stopped with Ctrl+C.