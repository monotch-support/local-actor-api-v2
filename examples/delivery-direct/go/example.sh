#!/bin/bash

export ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM="/home/user/Downloads/bundle/key_and_certificate.pem"
export CA_CERTIFICATE_PEM="/home/user/Downloads/bundle/ca.pem"
export MESSAGE_APPLICATION_PROPERTIES_JSON='{"messageType": "TEST", "publisherId": "XX99999", "publicationId": "XX99999:TEST", "originatingCountry": "XX", "protocolVersion": "TEST:0.0.0", "quadTree": ",1004,"}'
export ENDPOINT_HOST="example.com"
export ENDPOINT_PORT="5671"
export ENDPOINT_TARGET="LSD-TEST"

# Copy certificate files with expected names
cp "$ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM" chain_and_key.pem
cp "$CA_CERTIFICATE_PEM" ca.pem
cat ca.pem >> chain_and_key.pem

# Build and run Docker container
docker build -t delivery-direct-example-go .
docker run --rm -it  \
  -e ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM="chain_and_key.pem" \
  -e CA_CERTIFICATE_PEM="ca.pem" \
  -e MESSAGE_APPLICATION_PROPERTIES_JSON="$MESSAGE_APPLICATION_PROPERTIES_JSON" \
  -e ENDPOINT_HOST="$ENDPOINT_HOST" \
  -e ENDPOINT_PORT="$ENDPOINT_PORT" \
  -e ENDPOINT_TARGET="$ENDPOINT_TARGET" \
  delivery-direct-example-go

# Clean up
rm -f chain_and_key.pem ca.pem