#!/bin/bash

export ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM="/home/user/Downloads/bundle/key_and_certificate.pem"
export CA_CERTIFICATE_PEM="/home/user/Downloads/bundle/ca.pem"
export ENDPOINT_HOST="example.com"
export ENDPOINT_PORT="5671"
export ENDPOINT_SOURCE="LSS-TEST"

# Copy certificate files with expected names
cp "$ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM" chain_and_key.pem
cp "$CA_CERTIFICATE_PEM" ca.pem

# Build and run Docker container
docker build -t subscription-direct-example-dotnet .
docker run --rm  \
  -e ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM="chain_and_key.pem" \
  -e CA_CERTIFICATE_PEM="ca.pem" \
  -e ENDPOINT_HOST="$ENDPOINT_HOST" \
  -e ENDPOINT_PORT="$ENDPOINT_PORT" \
  -e ENDPOINT_SOURCE="$ENDPOINT_SOURCE" \
  subscription-direct-example-dotnet

# Clean up
rm -f chain_and_key.pem ca.pem