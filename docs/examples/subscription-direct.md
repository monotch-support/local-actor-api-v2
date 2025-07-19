# Direct subscription connection with known endpoint

This example demonstrates how to connect directly to a known AMQP endpoint for receiving messages without using the Actor API to create a subscription. This is useful when you already have the endpoint information from a previous subscription creation or when working with persistent subscription endpoints.

## Prerequisites

### General Requirements

- Valid client certificate and private key in PEM format
- CA certificate in PEM format
- Known AMQP endpoint information (host, port, source address)

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
| `ENDPOINT_HOST` | AMQP endpoint hostname | `amqp.example.com` |
| `ENDPOINT_PORT` | AMQP endpoint port | `5671` |
| `ENDPOINT_SOURCE` | AMQP source address for subscription | `subscription-source-address` |

## Configuration

=== "Python"

    ```python
    {% include-markdown "../../examples/subscription-direct/python/main.py" start="# Configuration by environment variables" end="# ======== AMQP 1.0 CLIENT ========"%}
    ```

=== ".NET"

    ```csharp
    {% include-markdown "../../examples/subscription-direct/dotnet/Program.cs" start="// Configuration by environment variables" end="// ======== LOGGING ========"%}
    ```

=== "Go"

    ```go
    {% include-markdown "../../examples/subscription-direct/go/main.go" start="// Configuration by environment variables" end="// ======== AMQP 1.0 CLIENT ========"%}
    ```

=== "Java"

    ```java
    {% include-markdown "../../examples/subscription-direct/java/Main.java" start="// Configuration by environment variables" end="// ======== AMQP 1.0 CLIENT ========"%}
    ```

!!! note "Explanation"
    Configuration by environment variables for certificates and pre-known endpoint information

## AMQP 1.0 Client

=== "Python"

    ```python
    {% include-markdown "../../examples/subscription-direct/python/main.py" dedent=true start="# ======== AMQP 1.0 CLIENT ========" end="# ======== DIRECT SUBSCRIBE WITH KNOWN ENDPOINT ========"%}
    ```

=== ".NET"

    ```csharp
    {% include-markdown "../../examples/subscription-direct/dotnet/Program.cs" dedent=true start="// ======== AMQP 1.0 CLIENT ========" end="// ======== DIRECT SUBSCRIBE WITH KNOWN ENDPOINT ========"%}
    ```

=== "Go"

    ```go
    {% include-markdown "../../examples/subscription-direct/go/main.go" dedent=true start="// ======== AMQP 1.0 CLIENT ========" end="// ======== DIRECT SUBSCRIBE WITH KNOWN ENDPOINT ========"%}
    ```

=== "Java"

    ```java
    {% include-markdown "../../examples/subscription-direct/java/Main.java" dedent=true start="// ======== AMQP 1.0 CLIENT ========" end="// ======== DIRECT SUBSCRIBE WITH KNOWN ENDPOINT ========"%}
    ```

!!! note "Explanation"
    AMQP 1.0 client implementation with SSL/TLS configuration using:
    
    - **Python**: python-qpid-proton library
    - **.NET**: AMQPNetLite library  
    - **Go**: Azure/go-amqp library
    - **Java**: Apache Qpid Proton-J library

## Direct connection and subscription

=== "Python"

    ```python
    {% include-markdown "../../examples/subscription-direct/python/main.py" dedent=true start="# ======== DIRECT SUBSCRIBE WITH KNOWN ENDPOINT ========" end="# ======== STARTUP AND RUN LOOP ========"%}
    ```

=== ".NET"

    ```csharp
    {% include-markdown "../../examples/subscription-direct/dotnet/Program.cs" dedent=true start="// ======== DIRECT SUBSCRIBE WITH KNOWN ENDPOINT ========" end="// ======== STARTUP AND RUN LOOP ========"%}
    ```

=== "Go"

    ```go
    {% include-markdown "../../examples/subscription-direct/go/main.go" dedent=true start="// ======== DIRECT SUBSCRIBE WITH KNOWN ENDPOINT ========" end="// ======== STARTUP AND RUN LOOP ========"%}
    ```

=== "Java"

    ```java
    {% include-markdown "../../examples/subscription-direct/java/Main.java" dedent=true start="// ======== DIRECT SUBSCRIBE WITH KNOWN ENDPOINT ========" end="// ======== STARTUP AND RUN LOOP ========"%}
    ```

!!! note "Explanation"
    Direct connection to AMQP endpoint using pre-configured host, port, and source address

## Full examples

| Language | Location | Description |
| -------- | -------- | ----------- |
| Python   | [examples/subscription-direct/python]({{ config.repo_url }}/tree/main/examples/subscription-direct/python) | Direct AMQP connection with known endpoint |
| .NET     | [examples/subscription-direct/dotnet]({{ config.repo_url }}/tree/main/examples/subscription-direct/dotnet) | Direct AMQP connection with known endpoint |
| Go       | [examples/subscription-direct/go]({{ config.repo_url }}/tree/main/examples/subscription-direct/go) | Direct AMQP connection with known endpoint |
| Java     | [examples/subscription-direct/java]({{ config.repo_url }}/tree/main/examples/subscription-direct/java) | Direct AMQP connection with known endpoint |