#!/bin/bash

set -e

caName=$1
if [ -z "$caName" ]; then
  echo "\$caName is empty"
  exit 1
fi

caFullName="$caName-ca"

echo "--------------------------- Generating '$caName' CA certificate"

mkdir -p "$caFullName"

# Generate CA private key
openssl ecparam \
  -genkey \
  -name prime256v1 \
  -out "$caFullName/$caFullName.localhost.key"

# Generate CA certificate
MSYS_NO_PATHCONV=1 \
  openssl req \
  -x509 \
  -new \
  -sha512 \
  -nodes \
  -key "$caFullName/$caFullName.localhost.key" \
  -days 365 \
  -subj "/C=EE/L=Tallinn/O=$caName-local/CN=$caFullName.localhost" \
  -out "$caFullName/$caFullName.localhost.crt"
