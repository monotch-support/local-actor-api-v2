# Introduction

The Main.java Java application is a working example for directly connecting to a known AMQP endpoint for data delivery without using the Actor API to create a delivery. This is useful when you already have the endpoint information from a previous delivery creation or when working with persistent delivery endpoints. The application is not intended for production use.


# Prerequisites
  
 - Java 11 or later
 - Maven packages: qpid-proton-j, jackson-databind, bouncy castle


# Adjust this according your information

 - ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM= *your complete crt chain*
 - CA_CERTIFICATE_PEM= *your full chain root.crt*
 - MESSAGE_APPLICATION_PROPERTIES_JSON= *the AMQP message application properties in JSON*
 - ENDPOINT_HOST= *AMQP endpoint hostname*
 - ENDPOINT_PORT= *AMQP endpoint port*
 - ENDPOINT_TARGET= *AMQP target address for delivery*


# Howto run

 1. Set the ENV variables 
 2. Execute application with Java 


# Example 

In the example below the application is executed in a docker container based on a multi-stage build with Maven and OpenJDK.

The application uses the following fake configuration values:

- AMQP endpoint hostname: `amqp.my-interchange`
- AMQP endpoint port: `5671`
- AMQP target address: `delivery-target-address`
- Actor certificate chain and key file: `chain_and_key.pem`
- Certificate Authority certificate file: `ca.pem`
- Message properties: `{"messageType": "TEST", "publisherId": "XX99999"}`

```
FROM maven:3.8-openjdk-11 AS builder

# Create app directory
WORKDIR /app

# Copy pom.xml
COPY pom.xml .

# Download dependencies
RUN mvn dependency:go-offline

# Copy source code
RUN mkdir -p src/main/java/com/example
COPY Main.java src/main/java/com/example/

# Build the application
RUN mvn clean package

# Final stage
FROM openjdk:11-jre-slim

# Install ca-certificates for SSL/TLS support
RUN apt-get update && apt-get install -y ca-certificates && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy the jar and dependencies from builder
COPY --from=builder /app/target/delivery-direct-example-1.0-SNAPSHOT.jar .
COPY --from=builder /app/target/lib ./lib

# Add certificates
ADD chain_and_key.pem .
ADD ca.pem .

# Execute application
CMD ["java", "-jar", "delivery-direct-example-1.0-SNAPSHOT.jar"]
```


# Common mistakes

## Certificate related

 - Not sending a full certificate chain (must include the client certificate, all intermediate certificates and the root certificate)
 - Not configuring the custom truststore (must include the root certificate)