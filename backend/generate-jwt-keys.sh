#!/bin/bash
# Retired: production must preserve its deployed cryptographic identity.
# Local development already generates ephemeral keys with the dev profile.
set -eu
printf '%s\n' 'Key generation disabled. Preserve the deployed production pair; operational runbooks are maintained outside the repository. For local development use the dev profile.' >&2
exit 1
