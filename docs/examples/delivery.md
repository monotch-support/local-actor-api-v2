# Creating a delivery and sending data

## Configuration

=== "Python"

    ```python
    {% include-markdown "../../examples/delivery/python/main.py" start="# Configuration by environment variables" end="# ======== ACTOR API FUNCTIONS ========"%}
    ```

!!! note "Explanation"
    Configuration by environment variables

## API Functions

=== "Python"

    ```python
    {% include-markdown "../../examples/delivery/python/main.py" dedent=true start="# ======== ACTOR API FUNCTIONS ========" end="# ======== AMQP 1.0 CLIENT ========"%}
    ```

!!! note "Explanation"
    Implementation of the required delivery endpoints (see [REST API Reference](../openapi.md#deliveries))

## AMQP 1.0 Client

=== "Python"

    ```python
    {% include-markdown "../../examples/delivery/python/main.py" dedent=true start="# ======== AMQP 1.0 CLIENT ========" end="# ======== CREATE AND PUBLISH INTO A DELIVERY ========"%}
    ```

## Create a delivery

=== "Python"

    ```python
    {% include-markdown "../../examples/delivery/python/main.py" dedent=true start="# Step 1: create a delivery using the actor API" end="log_json(\"Delivery create response\", delivery_create_response_json)"%}
    ```

## Poll the delivery

=== "Python"

    ```python
    {% include-markdown "../../examples/delivery/python/main.py" dedent=true start="# Step 2: get the delivery status" end='# Step 3: while the delivery status is \"REQUESTED\", keep getting the status'%}
    ```

## Keep polling the delivery while `REQUESTED`

=== "Python"

    ```python
    {% include-markdown "../../examples/delivery/python/main.py" dedent=true start="# Step 3: while the delivery status is \"REQUESTED\", keep getting the status" end="# Step 4a: if the status is \"CREATED\", get the endpoint information from the status response and use the endpoint with the AMQP 1.0 client"%}
    ```

## Use the endpoint information to connect


=== "Python"

    ```python
    {% include-markdown "../../examples/delivery/python/main.py" dedent=true start="# Step 4a: if the status is \"CREATED\", get the endpoint information from the status response and use the endpoint with the AMQP 1.0 client" end="# Step 4b: if the status is not \"CREATED\" warn log and do nothing"%}
    ```

## Full examples

| Language | Location |
| -------- | --------------- |
| Python   | [examples/delivery/python]({{ config.repo_url }}/tree/main/examples/delivery/python) |
