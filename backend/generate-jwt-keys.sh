#!/bin/bash
# Retired: production must preserve its deployed cryptographic identity.
# Local development already generates ephemeral keys with the dev profile.
set -eu
printf '%s\n' 'Key generation disabled. Preserve the deployed production pair; see docs/production-secrets.md. For local development use the dev profile.' >&2
exit 1
