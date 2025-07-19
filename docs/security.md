# Security

The API authentication is based on TLS Client Authentication. The API is exposed over HTTPS using TLS v1.3. A client side certificate is required to access the API. The client certificate’s “common name” is used for identification of the client (`actorCommonName`).

The AMQP connections are also secured by TLS v1.3 and require a client side certificate for authentication. The same client certificate must be used for both the API and the AMQP connections. Clients must be configured to use SASL-EXTERNAL as authentication mechanism since the client certificate’s common name is used as client identity.

In short:

- Uses **TLS v1.3** with **Client Certificate Authentication**
- The certificate's Common Name (`CN`) is used as client identity (`actorCommonName`)
- **SASL-EXTERNAL** must be used for AMQP authentication
