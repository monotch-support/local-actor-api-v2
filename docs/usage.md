# Endpoints

## Subscriptions

### Create subscription

`POST /{clientCommonName}/subscriptions`

Create a new subscription.
```json
{
  "selector": "<JMS selector>"
}
```

- Empty selector: subscribe to all data  
- Example: `messageType = 'SPATEM' AND publisherId = 'NL12345'`

**Response:**
```json
{
  "id": "<subscriptionId>",
  "status": "CREATED",
  "endpoints": [
    {
      "host": "amqp.example.net",
      "port": 5671,
      "source": "/some/source",
      "maxBandwidth": 10000,
      "maxMessageRate": 50
    }
  ],
  "lastUpdatedTimestamp": 1713800000000
}
```

### Get all subscriptions

`GET /{clientCommonName}/subscriptions`

List all subscriptions.

### Poll subscription

`GET /{clientCommonName}/subscriptions/{subscriptionId}`

Retrieve details of a specific subscription (for polling).

### Delete subscription

`DELETE /{clientCommonName}/subscriptions/{subscriptionId}`

Delete a subscription. Returns HTTP `204`.

---

## Deliveries

### Create delivery

`POST /{clientCommonName}/deliveries`

Create a new delivery.
```json
{
  "selector": "<JMS selector>"
}
```

**Response:**
```json
{
  "id": "<deliveryId>",
  "status": "CREATED",
  "endpoints": [
    {
      "host": "amqp.example.net",
      "port": 5671,
      "target": "/some/target",
      "maxBandwidth": 5000,
      "maxMessageRate": 20
    }
  ],
  "lastUpdatedTimestamp": 1713800000000
}
```

### Get all deliveries

`GET /{clientCommonName}/deliveries`

List all deliveries.

### Poll delivery

`GET /{clientCommonName}/deliveries/{deliveryId}`

Retrieve details of a specific delivery (for polling).

### Delete delivery

`DELETE /{clientCommonName}/deliveries/{deliveryId}`

Delete a delivery. Returns HTTP `204`.