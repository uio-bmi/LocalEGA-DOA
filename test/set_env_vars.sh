#!/bin/bash

export SSL_ENABLED=false
export BROKER_HOST=localhost
export BROKER_VALIDATE=false
export DB_INSTANCE=localhost
export ROOT_CERT_PATH=/test/client_certs/ca.crt
export CERT_PATH=/test/client_certs/client.crt
export CERT_KEY=/test/client_certs/client.der
export POSTGRES_PASSWORD=rootpasswd
export OPENID_CONFIGURATION_URL=http://localhost:8000/openid-configuration
export USERINFO_ENDPOINT_URL=http://localhost:8000/userinfo
export CRYPT4GH_PRIVATE_KEY_PATH=test/crypt4gh/crypt4gh.sec.pem
export CRYPT4GH_PRIVATE_KEY_PASSWORD_PATH=test/crypt4gh/crypt4gh.pass
export BROKER_USERNAME=guest
export OUTBOX_LOCATION=$PWD/%s/files/
export OUTBOX_TYPE=S3

echo "Environment variables set successfully."
