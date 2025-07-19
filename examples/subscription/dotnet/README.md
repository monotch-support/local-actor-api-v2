# Introduction

The Program.cs C# script is a working example for setting up a "Local Actor v2 Subscription" on an interchange supporting the "Local Actor API v2". The script contains all the functions required for the API and AMQP connectivity. The script is not intended for production use.


# Prerequisites
  
 - .NET 6.0 or later
 - NuGet packages: AMQPNetLite, System.Text.Json


# Adjust this according your information

 - ACTOR_API_HOST=
 - ACTOR_API_PORT=
 - ACTOR_API_SUBSCRIPTION_SELECTOR= *Such as "messageType = 'denm'"*
 - ACTOR_COMMON_NAME= *your complete actor name*
 - ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM= *your complete crt chain*
 - CA_CERTIFICATE_PEM= *your full chain root.crt*


# Howto run

 1. Set the ENV variables 
 2. Execute script with dotnet run


# Example 

In the example below the script is executed in a docker container based on the official .NET 6.0 runtime image.

The script uses the following fake configuration values:

- API hostname: `my-interchange`
- API port: `443`
- Subscription selector: `messageType = 'DENM'`
- Actor common name: `actor.my-interchange`
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
CMD ["dotnet", "SubscriptionExample.dll"]
```


# Common mistakes

## Certificate format issues

- The .NET example expects certificates in PEM format that can be loaded by X509Certificate2
- Ensure the certificate chain includes the client certificate, all intermediate certificates and the root certificate
- The CA certificate file should contain the root certificate for server validation

## TLS/SSL configuration

- The example uses custom server certificate validation to trust the specific CA certificate
- Make sure the server certificate chain is properly configured on the AMQP endpoint