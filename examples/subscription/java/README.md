# Introduction

The Main.java script is a working example for setting up a "Local Actor v2 Subscription" on an interchange supporting the "Local Actor API v2". The script contains all the functions required for the API and AMQP connectivity. The script is not intended for production use.


# Prerequisites
  
 - Java 11 or higher
 - Maven 3.6 or higher
 - Required Java libraries (managed by Maven):
   - Apache Qpid Proton-J for AMQP 1.0
   - OkHttp for REST API calls
   - Jackson for JSON processing
   - Bouncy Castle for PEM parsing


# Adjust this according your information

 - ACTOR_API_HOST=
 - ACTOR_API_PORT=
 - ACTOR_API_SUBSCRIPTION_SELECTOR= *Such as "messageType = 'denm'"*
 - ACTOR_COMMON_NAME= *your complete actor name*
 - ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM= *your complete crt chain*
 - CA_CERTIFICATE_PEM= *your full chain root.crt*


# Howto run

 1. Set the ENV variables 
 2. Build the project with Maven: `mvn clean package`
 3. Execute the JAR file: `java -jar target/subscription-example-1.0-SNAPSHOT.jar`


# Example 

In the example below the script is executed in a docker container using a multi-stage build with Maven and OpenJDK.

The script uses the following fake configuration values:

- API hostname: `my-interchange`
- API port: `443`
- Subscription selector: `messageType = 'DENM'`
- Actor common name: `actor.my-interchange`
- Actor certificate chain and key file: `chain_and_key.pem`
- Certificate Authority certificate file: `ca.pem`

```dockerfile
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
COPY --from=builder /app/target/subscription-example-1.0-SNAPSHOT.jar .
COPY --from=builder /app/target/lib ./lib

# Add certificates and application
ADD chain_and_key.pem .
ADD ca.pem .
ADD Main.java .

# Execute application
CMD ["java", "-jar", "subscription-example-1.0-SNAPSHOT.jar"]
```


# Common mistakes

## SSL/TLS configuration

- The application uses TLS v1.2 for HTTPS connections and TLS v1.3 for AMQP connections
- Ensure your Java runtime has the necessary security providers for handling SSL/TLS connections
- The Bouncy Castle provider is used for parsing PEM certificates

## Certificate related

 - Not sending a full certificate chain (must include the client certificate, all intermediate certificates and the root certificate)
 - Not configuring the custom truststore (must include the root certificate)
 - Ensure PEM files are properly formatted with correct headers and footers

## Java specific issues

 - Ensure Java 11 or higher is used for compatibility with the TLS versions and security features
 - If running in Docker, ensure the container has sufficient memory allocated
 - The application requires both the JAR file and its dependencies (in the lib directory) to run properly

## Message handling

 - The receiver automatically flows credit to the AMQP sender to receive more messages
 - Messages are automatically accepted and settled after processing
 - Application properties are displayed in sorted JSON format for consistent logging