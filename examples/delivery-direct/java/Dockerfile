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

# Add certificates and application
ADD chain_and_key.pem .
ADD ca.pem .
ADD Main.java .

# Execute application
CMD ["java", "-jar", "delivery-direct-example-1.0-SNAPSHOT.jar"]