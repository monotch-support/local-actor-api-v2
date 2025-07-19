# Direct delivery connection with known endpoint

This example demonstrates how to connect directly to a known AMQP endpoint for publishing messages without using the Actor API to create a delivery. This is useful when you already have the endpoint information from a previous delivery creation or when working with persistent delivery endpoints.

## Prerequisites

### General Requirements

- Valid client certificate and private key in PEM format
- CA certificate in PEM format
- Known AMQP endpoint information (host, port, target address)

### Language-Specific Requirements

=== "Python"
    - Python 3.x
    - python-qpid-proton library

=== ".NET"
    - .NET 6.0 or later
    - AMQPNetLite NuGet package

=== "Go"
    - Go 1.18 or later
    - github.com/Azure/go-amqp package

=== "Java"
    - Java 11 or later
    - Apache Qpid Proton-J library

## Environment Variables

| Variable | Description | Example |
| -------- | ----------- | ------- |
| `ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM` | Path to client certificate and private key in PEM format | `/path/to/client-cert-and-key.pem` |
| `CA_CERTIFICATE_PEM` | Path to CA certificate in PEM format | `/path/to/ca-cert.pem` |
| `MESSAGE_APPLICATION_PROPERTIES_JSON` | Message properties in JSON format | `{"messageType": "TEST", "publisherId": "XX99999", "publicationId": "XX99999:TEST", "originatingCountry": "XX", "protocolVersion": "TEST:0.0.0", "quadTree": ",1004,"}` |
| `ENDPOINT_HOST` | AMQP endpoint hostname | `amqp.example.com` |
| `ENDPOINT_PORT` | AMQP endpoint port | `5671` |
| `ENDPOINT_TARGET` | AMQP target address for delivery | `delivery-target-address` |

## Configuration

=== "Python"

    ```python
    {% include-markdown "../../examples/delivery-direct/python/main.py" start="# Configuration by environment variables" end="# ======== AMQP 1.0 CLIENT ========"%}
    ```

=== ".NET"

    ```csharp
    {% include-markdown "../../examples/delivery-direct/dotnet/Program.cs" start="// Configuration by environment variables" end="// ======== LOGGING ========"%}
    ```

=== "Go"

    ```go
    {% include-markdown "../../examples/delivery-direct/go/main.go" start="// Configuration by environment variables" end="// ======== AMQP 1.0 CLIENT ========"%}
    ```

=== "Java"

    ```java
    {% include-markdown "../../examples/delivery-direct/java/Main.java" start="// Configuration by environment variables" end="// ======== AMQP 1.0 CLIENT ========"%}
    ```

!!! note "Explanation"
    Configuration by environment variables for certificates and pre-known endpoint information

## AMQP 1.0 Client

=== "Python"

    ```python
    {% include-markdown "../../examples/delivery-direct/python/main.py" dedent=true start="# ======== AMQP 1.0 CLIENT ========" end="# ======== DIRECT PUBLISH WITH KNOWN ENDPOINT ========"%}
    ```

=== ".NET"

    ```csharp
    {% include-markdown "../../examples/delivery-direct/dotnet/Program.cs" dedent=true start="// ======== AMQP 1.0 CLIENT ========" end="// ======== DIRECT PUBLISH WITH KNOWN ENDPOINT ========"%}
    ```

=== "Go"

    ```go
    {% include-markdown "../../examples/delivery-direct/go/main.go" dedent=true start="// ======== AMQP 1.0 CLIENT ========" end="// ======== DIRECT PUBLISH WITH KNOWN ENDPOINT ========"%}
    ```

=== "Java"

    ```java
    {% include-markdown "../../examples/delivery-direct/java/Main.java" dedent=true start="// ======== AMQP 1.0 CLIENT ========" end="// ======== DIRECT PUBLISH WITH KNOWN ENDPOINT ========"%}
    ```

!!! note "Explanation"
    AMQP 1.0 client implementation with SSL/TLS configuration using:
    
    - **Python**: python-qpid-proton library
    - **.NET**: AMQPNetLite library  
    - **Go**: Azure/go-amqp library
    - **Java**: Apache Qpid Proton-J library

## Direct connection and publishing

=== "Python"

    ```python
    {% include-markdown "../../examples/delivery-direct/python/main.py" dedent=true start="# ======== DIRECT PUBLISH WITH KNOWN ENDPOINT ========" end="# ======== STARTUP AND RUN LOOP ========"%}
    ```

=== ".NET"

    ```csharp
    {% include-markdown "../../examples/delivery-direct/dotnet/Program.cs" dedent=true start="// ======== DIRECT PUBLISH WITH KNOWN ENDPOINT ========" end="// ======== STARTUP AND RUN LOOP ========"%}
    ```

=== "Go"

    ```go
    {% include-markdown "../../examples/delivery-direct/go/main.go" dedent=true start="// ======== DIRECT PUBLISH WITH KNOWN ENDPOINT ========" end="// ======== STARTUP AND RUN LOOP ========"%}
    ```

=== "Java"

    ```java
    {% include-markdown "../../examples/delivery-direct/java/Main.java" dedent=true start="// ======== DIRECT PUBLISH WITH KNOWN ENDPOINT ========" end="// ======== STARTUP AND RUN LOOP ========"%}
    ```

!!! note "Explanation"
    Direct connection to AMQP endpoint using pre-configured host, port, and target address

## Full examples

| Language | Location | Description |
| -------- | -------- | ----------- |
| Python   | [examples/delivery-direct/python]({{ config.repo_url }}/tree/main/examples/delivery-direct/python) | Direct AMQP connection with known endpoint |
| .NET     | [examples/delivery-direct/dotnet]({{ config.repo_url }}/tree/main/examples/delivery-direct/dotnet) | Direct AMQP connection with known endpoint |
| Go       | [examples/delivery-direct/go]({{ config.repo_url }}/tree/main/examples/delivery-direct/go) | Direct AMQP connection with known endpoint |
| Java     | [examples/delivery-direct/java]({{ config.repo_url }}/tree/main/examples/delivery-direct/java) | Direct AMQP connection with known endpoint |