#!/bin/bash

set -eu

# Recursively remove all directories from current path
cd "$(command dirname -- "${0}")" || exit
rm -rf ./*/

./generate-ca-certificate.sh 'siga-client'
./generate-ca-certificate.sh 'siga'

./generate-certificate.sh 'siga-client-ca' 'siga-demo'
./generate-truststore.sh 'siga-ca' 'siga-demo' 'siga-demo.truststore.p12'
./generate-certificate.sh 'siga-ca' 'siga'
./generate-certificate.sh 'siga-ca' 'siga-admin'
./generate-certificate.sh 'siga-ca' 'siga-01'
./generate-certificate.sh 'siga-ca' 'siga-02'
./generate-certificate.sh 'siga-ca' 'ignite-01'
./generate-certificate.sh 'siga-ca' 'ignite-02'
./generate-certificate.sh 'siga-ca' 'siga-admin-smtp'
./generate-truststore.sh 'siga-ca' 'siga-admin-smtp' 'siga-admin-smtp.truststore.p12'

./fetch-tls-certificate.sh 'sid.demo.sk.ee' 'sid'
./import-to-truststore.sh 'sid' 'sid.demo.sk.ee' 'sid/sid.demo.sk.ee.crt'
./fetch-tls-certificate.sh 'tsp.demo.sk.ee' 'mid'
./import-to-truststore.sh 'mid' 'tsp.demo.sk.ee' 'mid/tsp.demo.sk.ee.crt'
./fetch-tls-certificate.sh 'siva-demo.eesti.ee' 'siva'
./import-to-truststore.sh 'siva' 'siva-demo.eesti.ee' 'siva/siva-demo.eesti.ee.crt'
./fetch-tls-certificate.sh 'esteid.ldap.sk.ee' 'esteid-ldap' 636
./import-to-truststore.sh 'esteid-ldap' 'esteid.ldap.sk.ee' 'esteid-ldap/esteid.ldap.sk.ee.crt'
