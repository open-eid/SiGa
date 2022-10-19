#!/bin/bash

host=$1
if [ -z "$host" ]; then
  echo "\$host is empty"
  exit 1
fi

targetDirectory=$2
if [ -z "$targetDirectory" ]; then
  echo "\$targetDirectory is empty"
  exit 1
fi

port=$3
if [ -z "$port" ]; then
  port='443'
fi

echo "--------------------------- Fetching TLS certificate from '$host'"

# Create the target directory if does not exist
mkdir -p "$targetDirectory"

# Fetch the TLS certificate from the given host:port
# and save it into targetDirectory/host.crt
echo \
| \
openssl s_client \
  -showcerts \
  -servername "$host" \
  -connect "${host}:${port}" \
  2>/dev/null \
| \
openssl x509 \
  -inform pem \
  -text \
  -out "${targetDirectory}/${host}.crt"
