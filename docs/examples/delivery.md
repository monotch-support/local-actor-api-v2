# Creating a delivery and sending data

This example demonstrates the complete workflow for creating a delivery and sending data through the Local Actor API v2. The workflow follows the pattern: CREATE → POLL → CONNECT → USE, where you first create a delivery via the REST API, poll for its status until it's ready, then connect to the provided AMQP endpoint to publish messages.

## Prerequisites

### General Requirements

- Valid client certificate and private key in PEM format
- CA certificate in PEM format
- Access to a Local Actor API v2 instance

### Language-Specific Requirements

=== "Python"
    - Python 3.x
    - python-qpid-proton library
    - requests library

=== ".NET"
    - .NET 6.0 or later
    - AMQPNetLite NuGet package

=== "Go"
    - Go 1.18 or later
    - github.com/Azure/go-amqp package

=== "Java"
    - Java 11 or later
    - Maven 3.6 or later
    - Apache Qpid Proton-J library
    - OkHttp library
    - Jackson library
    - Bouncy Castle library

## Environment Variables

| Variable | Description | Example |
| -------- | ----------- | ------- |
| `ACTOR_API_HOST` | Hostname of the Actor API instance | `api.example.com` |
| `ACTOR_API_PORT` | Port of the Actor API instance | `443` |
| `ACTOR_API_DELIVERY_SELECTOR` | Selector for the delivery to create | `messageType = 'TEST'` |
| `ACTOR_COMMON_NAME` | Common name from the actor client certificate | `actor.example.com` |
| `ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM` | Path to client certificate and private key in PEM format | `/path/to/client-cert-and-key.pem` |
| `CA_CERTIFICATE_PEM` | Path to CA certificate in PEM format | `/path/to/ca-cert.pem` |
| `MESSAGE_APPLICATION_PROPERTIES_JSON` | Message properties in JSON format | `{"messageType": "TEST", "publisherId": "XX99999", "publicationId": "XX99999:TEST", "originatingCountry": "XX", "protocolVersion": "TEST:0.0.0", "quadTree": ",1004,"}` |

## Configuration

=== "Python"

    ```python
    {% include-markdown "../../examples/delivery/python/main.py" start="# Configuration by environment variables" end="# ======== ACTOR API FUNCTIONS ========"%}
    ```

=== ".NET"

    ```csharp
    {% include-markdown "../../examples/delivery/dotnet/Program.cs" start="// Configuration by environment variables" end="// ======== LOGGING ========"%}
    ```

=== "Go"

    ```go
    {% include-markdown "../../examples/delivery/go/main.go" start="// Configuration by environment variables" end="// ======== ACTOR API FUNCTIONS ========"%}
    ```

=== "Java"

    ```java
    {% include-markdown "../../examples/delivery/java/Main.java" start="// Configuration by environment variables" end="// ======== ACTOR API FUNCTIONS ========"%}
    ```

!!! note "Explanation"
    Configuration by environment variables

## API Functions

=== "Python"

    ```python
    {% include-markdown "../../examples/delivery/python/main.py" dedent=true start="# ======== ACTOR API FUNCTIONS ========" end="# ======== AMQP 1.0 CLIENT ========"%}
    ```

=== ".NET"

    ```csharp
    {% include-markdown "../../examples/delivery/dotnet/Program.cs" dedent=true start="// ======== ACTOR API FUNCTIONS ========" end="// ======== AMQP 1.0 CLIENT ========"%}
    ```

=== "Go"

    ```go
    {% include-markdown "../../examples/delivery/go/main.go" dedent=true start="// ======== ACTOR API FUNCTIONS ========" end="// ======== AMQP 1.0 CLIENT ========"%}
    ```

=== "Java"

    ```java
    {% include-markdown "../../examples/delivery/java/Main.java" dedent=true start="// ======== ACTOR API FUNCTIONS ========" end="// ======== SSL Configuration for HTTP Client ========"%}
    ```

!!! note "Explanation"
    Implementation of the required delivery endpoints (see [REST API Reference](../openapi.md#deliveries))

## AMQP 1.0 Client

=== "Python"

    ```python
    {% include-markdown "../../examples/delivery/python/main.py" dedent=true start="# ======== AMQP 1.0 CLIENT ========" end="# ======== CREATE AND PUBLISH INTO A DELIVERY ========"%}
    ```

=== ".NET"

    ```csharp
    {% include-markdown "../../examples/delivery/dotnet/Program.cs" dedent=true start="// ======== AMQP 1.0 CLIENT ========" end="// ======== CREATE AND PUBLISH INTO A DELIVERY ========"%}
    ```

=== "Go"

    ```go
    {% include-markdown "../../examples/delivery/go/main.go" dedent=true start="// ======== AMQP 1.0 CLIENT ========" end="// ======== CREATE AND PUBLISH INTO A DELIVERY ========"%}
    ```

=== "Java"

    ```java
    {% include-markdown "../../examples/delivery/java/Main.java" dedent=true start="// ======== AMQP 1.0 CLIENT ========" end="// ======== CREATE AND PUBLISH INTO A DELIVERY ========"%}
    ```

## Create a delivery

=== "Python"

    ```python
    {% include-markdown "../../examples/delivery/python/main.py" dedent=true start="# Step 1: create a delivery using the actor API" end="log_json(\"Delivery create response\", delivery_create_response_json)"%}
    ```

=== ".NET"

    ```csharp
    {% include-markdown "../../examples/delivery/dotnet/Program.cs" dedent=true start="// Step 1: create a delivery using the actor API" end="LogJson(\"Delivery create response\", deliveryCreateResponseJson);"%}
    ```

=== "Go"

    ```go
    {% include-markdown "../../examples/delivery/go/main.go" dedent=true start="// Step 1: create a delivery using the actor API" end="logJSON(\"Delivery create response\", deliveryCreateResponseJSON)"%}
    ```

=== "Java"

    ```java
    {% include-markdown "../../examples/delivery/java/Main.java" dedent=true start="// Step 1: create a delivery using the actor API" end="logJson(\"Delivery create response\", deliveryCreateResponseJson);"%}
    ```

## Poll the delivery

=== "Python"

    ```python
    {% include-markdown "../../examples/delivery/python/main.py" dedent=true start="# Step 2: get the delivery status" end='# Step 3: while the delivery status is \"REQUESTED\", keep getting the status'%}
    ```

=== ".NET"

    ```csharp
    {% include-markdown "../../examples/delivery/dotnet/Program.cs" dedent=true start="// Step 2: get the delivery status" end='// Step 3: while the delivery status is \"REQUESTED\", keep getting the status'%}
    ```

=== "Go"

    ```go
    {% include-markdown "../../examples/delivery/go/main.go" dedent=true start="// Step 2: get the delivery status" end='// Step 3: while the delivery status is \"REQUESTED\", keep getting the status'%}
    ```

=== "Java"

    ```java
    {% include-markdown "../../examples/delivery/java/Main.java" dedent=true start="// Step 2: get the delivery status" end='// Step 3: while the delivery status is \"REQUESTED\", keep getting the status'%}
    ```

## Keep polling the delivery while `REQUESTED`

=== "Python"

    ```python
    {% include-markdown "../../examples/delivery/python/main.py" dedent=true start="# Step 3: while the delivery status is \"REQUESTED\", keep getting the status" end="# Step 4a: if the status is \"CREATED\", get the endpoint information from the status response and use the endpoint with the AMQP 1.0 client"%}
    ```

=== ".NET"

    ```csharp
    {% include-markdown "../../examples/delivery/dotnet/Program.cs" dedent=true start="// Step 3: while the delivery status is \"REQUESTED\", keep getting the status" end="// Step 4a: if the status is \"CREATED\", get the endpoint information from the status response and use the endpoint with the AMQP 1.0 client"%}
    ```

=== "Go"

    ```go
    {% include-markdown "../../examples/delivery/go/main.go" dedent=true start="// Step 3: while the delivery status is \"REQUESTED\", keep getting the status" end="// Step 4a: if the status is \"CREATED\", get the endpoint information from the status response and use the endpoint with the AMQP 1.0 client"%}
    ```

=== "Java"

    ```java
    {% include-markdown "../../examples/delivery/java/Main.java" dedent=true start="// Step 3: while the delivery status is \"REQUESTED\", keep getting the status" end="// Step 4a: if the status is \"CREATED\", get the endpoint information from the status response and use the endpoint with the AMQP 1.0 client"%}
    ```

## Use the endpoint information to connect


=== "Python"

    ```python
    {% include-markdown "../../examples/delivery/python/main.py" dedent=true start="# Step 4a: if the status is \"CREATED\", get the endpoint information from the status response and use the endpoint with the AMQP 1.0 client" end="# Step 4b: if the status is not \"CREATED\" warn log and do nothing"%}
    ```

=== ".NET"

    ```csharp
    {% include-markdown "../../examples/delivery/dotnet/Program.cs" dedent=true start="// Step 4a: if the status is \"CREATED\", get the endpoint information from the status response and use the endpoint with the AMQP 1.0 client" end="// Step 4b: if the status is not \"CREATED\" warn log and do nothing"%}
    ```

=== "Go"

    ```go
    {% include-markdown "../../examples/delivery/go/main.go" dedent=true start="// Step 4a: if the status is \"CREATED\", get the endpoint information from the status response and use the endpoint with the AMQP 1.0 client" end="// Step 4b: if the status is not \"CREATED\" warn log and do nothing"%}
    ```

=== "Java"

    ```java
    {% include-markdown "../../examples/delivery/java/Main.java" dedent=true start="// Step 4a: if the status is \"CREATED\", get the endpoint information from the status response and use the endpoint with the AMQP 1.0 client" end="// Step 4b: if the status is not \"CREATED\" warn log and do nothing"%}
    ```

## Full examples

| Language | Location | Description |
| -------- | -------- | ----------- |
| Python   | [examples/delivery/python]({{ config.repo_url }}/tree/main/examples/delivery/python) | Complete workflow: CREATE → POLL → CONNECT → USE |
| .NET     | [examples/delivery/dotnet]({{ config.repo_url }}/tree/main/examples/delivery/dotnet) | Complete workflow: CREATE → POLL → CONNECT → USE |
| Go       | [examples/delivery/go]({{ config.repo_url }}/tree/main/examples/delivery/go) | Complete workflow: CREATE → POLL → CONNECT → USE |
| Java     | [examples/delivery/java]({{ config.repo_url }}/tree/main/examples/delivery/java) | Complete workflow: CREATE → POLL → CONNECT → USE |
