# Introduction

The main.go Go script is a working example for setting up a "Local Actor v2 Delivery" on an interchange supporting the "Local Actor API v2". The script contains all the functions required for the API and AMQP connectivity. The script is not intended for production use.


# Prerequisites
  
 - Go 1.18 or higher
 - Go packages: github.com/Azure/go-amqp 


# Adjust this according your information

 - ACTOR_API_HOST=
 - ACTOR_API_PORT=
 - ACTOR_API_DELIVERY_SELECTOR= *Such as "messageType = 'denm'"*
 - ACTOR_COMMON_NAME= *your complete actor name*
 - ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM= *your complete crt chain*
 - CA_CERTIFICATE_PEM= *your full chain root.crt*
 - MESSAGE_APPLICATION_PROPERTIES_JSON= *the AMQP message application properties in JSON*


# Howto run

 1. Set the ENV variables 
 2. Execute script with go run main.go 


# Example 

In the example below the script is executed in a docker container based on a multi-stage build with Golang and Alpine Linux.

The script uses the following fake configuration values:

- API hostname: `my-interchange`
- API port: `443`
- Delivery selector: `messageType = 'DENM'`
- Actor common name: `actor.my-interchange`
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

## API related

 - Ensure the Actor API endpoint is accessible and supports HTTPS
 - Verify that the actor common name matches the certificate subject
 - Check that the delivery selector syntax is correct for your interchange
 - Ensure proper JSON formatting for message application properties