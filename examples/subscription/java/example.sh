#!/bin/bash

export ACTOR_API_HOST="example.com"
export ACTOR_API_PORT="5443"
export ACTOR_API_SUBSCRIPTION_SELECTOR="messageType = 'TEST'"
export ACTOR_COMMON_NAME="example.actor"
export ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM="/home/user/Downloads/bundle/key_and_certificate.pem"
export CA_CERTIFICATE_PEM="/home/user/Downloads/bundle/ca.pem"

# Copy certificate files with expected names
cp "$ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM" chain_and_key.pem
cp "$CA_CERTIFICATE_PEM" ca.pem
cat ca.pem >> chain_and_key.pem

# Build and run Docker container
docker build -t subscription-example-java .
docker run --rm -it  \
  -e ACTOR_API_HOST="$ACTOR_API_HOST" \
  -e ACTOR_API_PORT="$ACTOR_API_PORT" \
  -e ACTOR_API_SUBSCRIPTION_SELECTOR="$ACTOR_API_SUBSCRIPTION_SELECTOR" \
  -e ACTOR_COMMON_NAME="$ACTOR_COMMON_NAME" \
  -e ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM="chain_and_key.pem" \
  -e CA_CERTIFICATE_PEM="ca.pem" \
  subscription-example-java

# Clean up
rm -f chain_and_key.pem ca.pem