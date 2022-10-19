#!/bin/bash

set -e

ca=$1
if [ -z "$ca" ]; then
  echo "\$ca is empty"
  exit 1
fi

application=$2
if [ -z "$application" ]; then
  echo "\$application is empty"
  exit 1
fi

filename=$3
if [ -z "$filename" ]; then
  filename="$application.localhost.truststore.p12"
fi

echo "--------------------------- Generating truststore for '$application'"

# Remove application existing truststore
rm -f "$application/$filename"

# Generate truststore with CA certificate for application
keytool -noprompt \
  -importcert \
  -alias "$ca.localhost" \
  -file "$ca/$ca.localhost.crt" \
  -storepass changeit \
  -storetype pkcs12 \
  -keystore "$application/$filename"
