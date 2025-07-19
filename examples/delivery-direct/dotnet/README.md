# Introduction

The Program.cs C# script is a working example for directly connecting to a known AMQP endpoint for data delivery without using the Actor API to create a delivery. This is useful when you already have the endpoint information from a previous delivery creation or when working with persistent delivery endpoints. The script is not intended for production use.


# Prerequisites
  
 - .NET 6.0 or later
 - NuGet packages: AMQPNetLite, System.Text.Json


# Adjust this according your information

 - ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM= *your complete crt chain*
 - CA_CERTIFICATE_PEM= *your full chain root.crt*
 - MESSAGE_APPLICATION_PROPERTIES_JSON= *the AMQP message application properties in JSON*
 - ENDPOINT_HOST= *AMQP endpoint hostname*
 - ENDPOINT_PORT= *AMQP endpoint port*
 - ENDPOINT_TARGET= *AMQP target address for delivery*


# Howto run

 1. Set the ENV variables 
 2. Execute script with dotnet run


# Example 

In the example below the script is executed in a docker container based on the official .NET 6.0 runtime image.

The script uses the following fake configuration values:

- AMQP endpoint hostname: `amqp.my-interchange`
- AMQP endpoint port: `5671`
- AMQP target address: `delivery-target-address`
- Actor certificate chain and key file: `chain_and_key.pem`
- Certificate Authority certificate file: `ca.pem`

```
FROM mcr.microsoft.com/dotnet/sdk:6.0 AS build

WORKDIR /app

# Copy project file and restore dependencies
COPY *.csproj ./
RUN dotnet restore

# Copy source code and build
COPY . ./
RUN dotnet publish -c Release -o out

# Runtime stage
FROM mcr.microsoft.com/dotnet/runtime:6.0

# install prerequisites
RUN apt update
RUN apt install -y ca-certificates

# add certificates and application
ADD chain_and_key.pem .
ADD ca.pem .
COPY --from=build /app/out .

# execute application
CMD ["dotnet", "DeliveryDirectExample.dll"]
```


# Common mistakes

## Certificate format issues

- The .NET example expects certificates in PEM format that can be loaded by X509Certificate2
- Ensure the certificate chain includes the client certificate, all intermediate certificates and the root certificate
- The CA certificate file should contain the root certificate for server validation

## TLS/SSL configuration

- The example uses custom server certificate validation to trust the specific CA certificate
- Make sure the server certificate chain is properly configured on the AMQP endpoint