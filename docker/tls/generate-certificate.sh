#!/bin/bash

set -e

ca=$1
if [ -z "$ca" ]; then
  echo "\$ca is empty"
  exit 1
fi

applicationName=$2
if [ -z "$applicationName" ]; then
  echo "\$applicationName is empty"
  exit 1
fi

host=$applicationName.localhost # ex. admin.localhost

echo "--------------------------- Generating certificate for '$host'"

# Create application TLS folder if does not exist
mkdir -p "$applicationName"

# Generate ECDSA key
openssl ecparam \
  -name prime256v1 \
  -genkey \
  -out "$applicationName/$host.key"

# Generate CSR from key
# MSYS_NO_PATHCOW=1 needed for Git Bash on Windows users - unable to handle "/"-s in -subj parameter.
MSYS_NO_PATHCONV=1 \
  openssl req \
  -new \
  -sha512 \
  -nodes \
  -key "$applicationName/$host.key" \
  -subj "/CN=$host" \
  -out "$applicationName/$host.csr"

tmpCnfFile=$(mktemp)
trap "rm -f $tmpCnfFile" 0 2 3 15

echo "
authorityKeyIdentifier = keyid,issuer 
basicConstraints       = critical, CA:FALSE 
keyUsage               = digitalSignature, keyEncipherment 
subjectAltName         = DNS:$host
" > $tmpCnfFile

# Generate CA signed certificate
OPENSSL_CONF="$tmpCnfFile" openssl x509 \
  -req \
  -sha512 \
  -in "$applicationName/$host.csr" \
  -CA "$ca/$ca.localhost.crt" \
  -CAkey "$ca/$ca.localhost.key" \
  -CAcreateserial \
  -days 363 \
  -out "$applicationName/$host.crt"

# Generate keystore from application cert and key
openssl pkcs12 \
  -export \
  -name "$host" \
  -in "$applicationName/$host.crt" \
  -inkey "$applicationName/$host.key" \
  -passout pass:changeit \
  -out "$applicationName/$host.keystore.p12"

# Add CA certificate to application keystore
keytool -noprompt \
  -importcert \
  -alias "$ca.localhost" \
  -file "$ca/$ca.localhost.crt" \
  -storepass changeit \
  -keystore "$applicationName/$host.keystore.p12"

# TODO: Find a better solution than making the keys readable by everyone.
# Make the files readable by all users inside containers. Required for 'ory' user in hydra container.
chmod 644 "$applicationName"/*
chmod 644 "$ca"/*
