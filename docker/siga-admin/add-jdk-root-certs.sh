#!/bin/bash

set -e

application=$1
if [ -z "$application" ]; then
  echo "\$application is empty"
  exit 1
fi

filename=$2
if [ -z "$filename" ]; then
  filename="$application.localhost.truststore.p12"
fi

truststore="$application/$filename"
if [ ! -f "$truststore" ]; then
  echo "$truststore does not exist, run generate-truststore.sh first"
  exit 1
fi

cacerts="${JAVA_HOME:?JAVA_HOME is not set}/lib/security/cacerts"

echo "--------------------------- Adding public CA roots to '$truststore' so the OIDC provider is trusted"

keytool -noprompt \
  -importkeystore \
  -srckeystore "$cacerts" \
  -srcstorepass changeit \
  -destkeystore "$truststore" \
  -deststorepass changeit \
  -deststoretype pkcs12
