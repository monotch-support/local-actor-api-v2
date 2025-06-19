# Creating a subscription and receiving data

## Configuration

=== "Python"

    ```python
    {% include-markdown "../../examples/subscription/python/main.py" start="# Configuration by environment variables" end="# ======== ACTOR API FUNCTIONS ========"%}
    ```

!!! note "Explanation"
    Configuration by environment variables

## API Functions

=== "Python"

    ```python
    {% include-markdown "../../examples/subscription/python/main.py" dedent=true start="# ======== ACTOR API FUNCTIONS ========" end="# ======== AMQP 1.0 CLIENT ========"%}
    ```

!!! note "Explanation"
    Implementation of the required subscription endpoints (see [REST API Reference](../openapi.md#subscriptions))

## AMQP 1.0 Client

=== "Python"

    ```python
    {% include-markdown "../../examples/subscription/python/main.py" dedent=true start="# ======== AMQP 1.0 CLIENT ========" end="# ======== CREATE AND CONSUME A SUBSCRIPTION ========"%}
    ```

## Create a subscription

=== "Python"

    ```python
    {% include-markdown "../../examples/subscription/python/main.py" dedent=true start="# Step 1: create a subscription using the actor API" end="log_json(\"Subscription create response\", subscription_create_response_json)"%}
    ```

## Poll the subscription

=== "Python"

    ```python
    {% include-markdown "../../examples/subscription/python/main.py" dedent=true start="# Step 2: get the subscription status" end='# Step 3: while the subscription status is \"REQUESTED\", keep getting the status'%}
    ```

## Keep polling the subscription while `REQUESTED`

=== "Python"

    ```python
    {% include-markdown "../../examples/subscription/python/main.py" dedent=true start="# Step 3: while the subscription status is \"REQUESTED\", keep getting the status" end="# Step 4a: if the status is \"CREATED\", connect to the endpoint and start the AMQP receiver"%}
    ```

## Use the endpoint information to connect


=== "Python"

    ```python
    {% include-markdown "../../examples/subscription/python/main.py" dedent=true start="# Step 4a: if the status is \"CREATED\", connect to the endpoint and start the AMQP receiver" end="# Step 4b: if the status is not \"CREATED\" warn log and do nothing"%}
    ```

## Full examples

| Language | Location |
| -------- | --------------- |
| Python   | [examples/subscription/python]({{ config.repo_url }}/tree/main/examples/subscription/python) |
