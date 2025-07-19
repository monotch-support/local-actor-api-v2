# Introduction

The main.go Go script is a working example for directly connecting to a known AMQP endpoint for data delivery without using the Actor API to create a delivery. This is useful when you already have the endpoint information from a previous delivery creation or when working with persistent delivery endpoints. The script is not intended for production use.


# Prerequisites
  
 - Go 1.18 or higher
 - Go packages: github.com/Azure/go-amqp 


# Adjust this according your information

 - ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM= *your complete crt chain*
 - CA_CERTIFICATE_PEM= *your full chain root.crt*
 - MESSAGE_APPLICATION_PROPERTIES_JSON= *the AMQP message application properties in JSON*
 - ENDPOINT_HOST= *AMQP endpoint hostname*
 - ENDPOINT_PORT= *AMQP endpoint port*
 - ENDPOINT_TARGET= *AMQP target address for delivery*


# Howto run

 1. Set the ENV variables 
 2. Execute script with go run main.go 


# Example 

In the example below the script is executed in a docker container based on a multi-stage build with Golang and Alpine Linux.

The script uses the following fake configuration values:

- AMQP endpoint hostname: `amqp.my-interchange`
- AMQP endpoint port: `5671`
- AMQP target address: `delivery-target-address`
- Actor certificate chain and key file: `chain_and_key.pem`
- Certificate Authority certificate file: `ca.pem`

The multi-stage build approach ensures a minimal final image size while properly compiling the Go application with all its dependencies.

```
FROM golang:1.18-bullseye AS builder

# Create app directory
WORKDIR /app

# Copy go mod files
COPY go.mod go.sum* ./

# Download dependencies
RUN go mod download

# Copy source code
COPY main.go .

# Build the application
RUN CGO_ENABLED=0 GOOS=linux go build -a -installsuffix cgo -o main .

# Final stage
FROM alpine:latest

# Install ca-certificates for SSL/TLS support
RUN apk --no-cache add ca-certificates

WORKDIR /root/

# Copy the binary from builder
COPY --from=builder /app/main .

# Add certificates
ADD chain_and_key.pem .
ADD ca.pem .

# Execute binary
CMD ["./main"]
```


# Common mistakes

## Certificate related

 - Not sending a full certificate chain (must include the client certificate, all intermediate certificates and the root certificate)
 - Not configuring the custom truststore (must include the root certificate)
 - Certificate files must be accessible within the container (check file permissions and paths)

## Connection issues

 - Ensure the AMQP endpoint supports AMQP 1.0 protocol
 - Verify that port 5671 (AMQPS) is open and accessible
 - Check that the endpoint hostname resolves correctly from within the container