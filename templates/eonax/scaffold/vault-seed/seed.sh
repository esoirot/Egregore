#!/bin/sh
# For each participant, an EC P-256 key pair its data plane uses to sign and verify the access
# tokens it hands out (EDR). Stored in Vault's KV store under <participant>/<alias>, field
# "content": where EDC's HashiCorp Vault extension reads secrets (EDC_VAULT_HASHICORP_FOLDER).
# Keeps existing keys: running it again changes nothing.
set -eu

: "${VAULT_ADDR:?}" "${VAULT_TOKEN:?}" "${PARTICIPANTS:?}"

put() { # put <path> <value>
    jq -n --arg v "$2" '{data: {content: $v}}' \
        | curl -fsS -o /dev/null -H "X-Vault-Token: $VAULT_TOKEN" -H "Content-Type: application/json" \
            -X POST --data @- "$VAULT_ADDR/v1/secret/data/$1"
}

exists() { # exists <path>
    curl -fsS -o /dev/null -H "X-Vault-Token: $VAULT_TOKEN" "$VAULT_ADDR/v1/secret/data/$1" 2> /dev/null
}

cd /tmp
for p in $PARTICIPANTS; do
    if exists "$p/transfer-proxy-private-key"; then
        echo "$p: transfer keys already in Vault"
        continue
    fi
    openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out key.pem 2> /dev/null
    openssl pkey -in key.pem -pubout -out pub.pem
    put "$p/transfer-proxy-private-key" "$(cat key.pem)"
    put "$p/transfer-proxy-public-key" "$(cat pub.pem)"
    rm -f key.pem pub.pem
    echo "$p: transfer keys stored in Vault"
done
