#!/bin/bash

# Script to generate EC P-256 keys for JWT signing
# Run this script to generate production keys

set -e

echo "Generating EC P-256 key pair for JWT signing..."

# Private key
openssl ecparam -name prime256v1 -genkey -noout -out jwt-private-key.pem

# Public key
openssl ec -in jwt-private-key.pem -pubout -out jwt-public-key.pem

# Convert to PKCS#8 format
openssl pkcs8 -topk8 -nocrypt -in jwt-private-key.pem -out jwt-private-key-pkcs8.pem
mv jwt-private-key-pkcs8.pem jwt-private-key.pem

echo "Generated files:"
echo "  - jwt-private-key.pem"
echo "  - jwt-public-key.pem"
echo ""
echo "For local development, keys are auto-generated when using 'dev' profile"
