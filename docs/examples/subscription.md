# Creating a subscription and receiving data

This example demonstrates the complete workflow for creating a subscription and receiving data through the Local Actor API v2. The workflow follows the pattern: CREATE → POLL → CONNECT → USE, where you first create a subscription via the REST API, poll for its status until it's ready, then connect to the provided AMQP endpoint to receive messages.

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
| `ACTOR_API_SUBSCRIPTION_SELECTOR` | Selector for the subscription to create | `messageType = 'TEST'` |
| `ACTOR_COMMON_NAME` | Common name from the actor client certificate | `actor.example.com` |
| `ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM` | Path to client certificate and private key in PEM format | `/path/to/client-cert-and-key.pem` |
| `CA_CERTIFICATE_PEM` | Path to CA certificate in PEM format | `/path/to/ca-cert.pem` |

## Configuration

=== "Python"

    ```python
    {% include-markdown "../../examples/subscription/python/main.py" start="# Configuration by environment variables" end="# ======== ACTOR API FUNCTIONS ========"%}
    ```

=== ".NET"

    ```csharp
    {% include-markdown "../../examples/subscription/dotnet/Program.cs" start="// Configuration by environment variables" end="// ======== LOGGING ========"%}
    ```

=== "Go"

    ```go
    {% include-markdown "../../examples/subscription/go/main.go" start="// Configuration by environment variables" end="// ======== ACTOR API FUNCTIONS ========"%}
    ```

=== "Java"

    ```java
    {% include-markdown "../../examples/subscription/java/Main.java" start="// Configuration by environment variables" end="// ======== ACTOR API FUNCTIONS ========"%}
    ```

!!! note "Explanation"
    Configuration by environment variables

## API Functions

=== "Python"

    ```python
    {% include-markdown "../../examples/subscription/python/main.py" dedent=true start="# ======== ACTOR API FUNCTIONS ========" end="# ======== AMQP 1.0 CLIENT ========"%}
    ```

=== ".NET"

    ```csharp
    {% include-markdown "../../examples/subscription/dotnet/Program.cs" dedent=true start="// ======== ACTOR API FUNCTIONS ========" end="// ======== AMQP 1.0 CLIENT ========"%}
    ```

=== "Go"

    ```go
    {% include-markdown "../../examples/subscription/go/main.go" dedent=true start="// ======== ACTOR API FUNCTIONS ========" end="// ======== AMQP 1.0 CLIENT ========"%}
    ```

=== "Java"

    ```java
    {% include-markdown "../../examples/subscription/java/Main.java" dedent=true start="// ======== ACTOR API FUNCTIONS ========" end="// ======== SSL Configuration for HTTP Client ========"%}
    ```

!!! note "Explanation"
    Implementation of the required subscription endpoints (see [REST API Reference](../openapi.md#subscriptions))

## AMQP 1.0 Client

=== "Python"

    ```python
    {% include-markdown "../../examples/subscription/python/main.py" dedent=true start="# ======== AMQP 1.0 CLIENT ========" end="# ======== CREATE AND CONSUME A SUBSCRIPTION ========"%}
    ```

=== ".NET"

    ```csharp
    {% include-markdown "../../examples/subscription/dotnet/Program.cs" dedent=true start="// ======== AMQP 1.0 CLIENT ========" end="// ======== CREATE AND CONSUME A SUBSCRIPTION ========"%}
    ```

=== "Go"

    ```go
    {% include-markdown "../../examples/subscription/go/main.go" dedent=true start="// ======== AMQP 1.0 CLIENT ========" end="// ======== CREATE AND CONSUME A SUBSCRIPTION ========"%}
    ```

=== "Java"

    ```java
    {% include-markdown "../../examples/subscription/java/Main.java" dedent=true start="// ======== AMQP 1.0 CLIENT ========" end="// ======== CREATE AND CONSUME A SUBSCRIPTION ========"%}
    ```

## Create a subscription

=== "Python"

    ```python
    {% include-markdown "../../examples/subscription/python/main.py" dedent=true start="# Step 1: create a subscription using the actor API" end="log_json(\"Subscription create response\", subscription_create_response_json)"%}
    ```

=== ".NET"

    ```csharp
    {% include-markdown "../../examples/subscription/dotnet/Program.cs" dedent=true start="// Step 1: create a subscription using the actor API" end="LogJson(\"Subscription create response\", subscriptionCreateResponseJson);"%}
    ```

=== "Go"

    ```go
    {% include-markdown "../../examples/subscription/go/main.go" dedent=true start="// Step 1: create a subscription using the actor API" end="logJSON(\"Subscription create response\", subscriptionCreateResponseJSON)"%}
    ```

=== "Java"

    ```java
    {% include-markdown "../../examples/subscription/java/Main.java" dedent=true start="// Step 1: create a subscription using the actor API" end="logJson(\"Subscription create response\", subscriptionCreateResponseJson);"%}
    ```

## Poll the subscription

=== "Python"

    ```python
    {% include-markdown "../../examples/subscription/python/main.py" dedent=true start="# Step 2: get the subscription status" end='# Step 3: while the subscription status is \"REQUESTED\", keep getting the status'%}
    ```

=== ".NET"

    ```csharp
    {% include-markdown "../../examples/subscription/dotnet/Program.cs" dedent=true start="// Step 2: get the subscription status" end='// Step 3: while the subscription status is \"REQUESTED\", keep getting the status'%}
    ```

=== "Go"

    ```go
    {% include-markdown "../../examples/subscription/go/main.go" dedent=true start="// Step 2: get the subscription status" end='// Step 3: while the subscription status is \"REQUESTED\", keep getting the status'%}
    ```

=== "Java"

    ```java
    {% include-markdown "../../examples/subscription/java/Main.java" dedent=true start="// Step 2: get the subscription status" end='// Step 3: while the subscription status is \"REQUESTED\", keep getting the status'%}
    ```

## Keep polling the subscription while `REQUESTED`

=== "Python"

    ```python
    {% include-markdown "../../examples/subscription/python/main.py" dedent=true start="# Step 3: while the subscription status is \"REQUESTED\", keep getting the status" end="# Step 4a: if the status is \"CREATED\", connect to the endpoint and start the AMQP receiver"%}
    ```

=== ".NET"

    ```csharp
    {% include-markdown "../../examples/subscription/dotnet/Program.cs" dedent=true start="// Step 3: while the subscription status is \"REQUESTED\", keep getting the status" end="// Step 4a: if the status is \"CREATED\", connect to the endpoint and start the AMQP receiver"%}
    ```

=== "Go"

    ```go
    {% include-markdown "../../examples/subscription/go/main.go" dedent=true start="// Step 3: while the subscription status is \"REQUESTED\", keep getting the status" end="// Step 4a: if the status is \"CREATED\", connect to the endpoint and start the AMQP receiver"%}
    ```

=== "Java"

    ```java
    {% include-markdown "../../examples/subscription/java/Main.java" dedent=true start="// Step 3: while the subscription status is \"REQUESTED\", keep getting the status" end="// Step 4a: if the status is \"CREATED\", connect to the endpoint and start the AMQP receiver"%}
    ```

## Use the endpoint information to connect


=== "Python"

    ```python
    {% include-markdown "../../examples/subscription/python/main.py" dedent=true start="# Step 4a: if the status is \"CREATED\", connect to the endpoint and start the AMQP receiver" end="# Step 4b: if the status is not \"CREATED\" warn log and do nothing"%}
    ```

=== ".NET"

    ```csharp
    {% include-markdown "../../examples/subscription/dotnet/Program.cs" dedent=true start="// Step 4a: if the status is \"CREATED\", connect to the endpoint and start the AMQP receiver" end="// Step 4b: if the status is not \"CREATED\" warn log and do nothing"%}
    ```

=== "Go"

    ```go
    {% include-markdown "../../examples/subscription/go/main.go" dedent=true start="// Step 4a: if the status is \"CREATED\", connect to the endpoint and start the AMQP receiver" end="// Step 4b: if the status is not \"CREATED\" warn log and do nothing"%}
    ```

=== "Java"

    ```java
    {% include-markdown "../../examples/subscription/java/Main.java" dedent=true start="// Step 4a: if the status is \"CREATED\", connect to the endpoint and start the AMQP receiver" end="// Step 4b: if the status is not \"CREATED\" warn log and do nothing"%}
    ```

## Full examples

| Language | Location | Description |
| -------- | -------- | ----------- |
| Python   | [examples/subscription/python]({{ config.repo_url }}/tree/main/examples/subscription/python) | Complete workflow: CREATE → POLL → CONNECT → USE |
| .NET     | [examples/subscription/dotnet]({{ config.repo_url }}/tree/main/examples/subscription/dotnet) | Complete workflow: CREATE → POLL → CONNECT → USE |
| Go       | [examples/subscription/go]({{ config.repo_url }}/tree/main/examples/subscription/go) | Complete workflow: CREATE → POLL → CONNECT → USE |
| Java     | [examples/subscription/java]({{ config.repo_url }}/tree/main/examples/subscription/java) | Complete workflow: CREATE → POLL → CONNECT → USE |
