#!/bin/sh
# Brings Vault up for the runtimes: initializes it the first time (one unseal key, kept in the
# vault-init volume: a dev shortcut), unseals it, creates the runtimes' token (VAULT_TOKEN) and the
# KV store "secret". Then the secrets every runtime needs, in that store under <folder>/<alias>,
# field "content" (where EDC's HashiCorp Vault extension reads them, EDC_VAULT_HASHICORP_FOLDER):
#   FOLDERS       aes-key-alias: the key a runtime encrypts its stored secrets with
#   PARTICIPANTS  transfer-proxy-private-key / -public-key: EC P-256 pair its data plane signs and
#                 verifies access tokens (EDR) with
# Keys, DIDs and credentials of the identities are not here: the Identity Hubs and the Issuer
# Service create those themselves (just onboard). Keeps existing keys: running again changes nothing.
set -eu

: "${VAULT_ADDR:?}" "${VAULT_TOKEN:?}" "${FOLDERS:?}" "${PARTICIPANTS:?}"

vault_api() { # vault_api <METHOD> <path> [json-body]: call Vault with the runtimes' token
    curl -fsS -X "$1" -H "X-Vault-Token: $VAULT_TOKEN" ${3:+-H "Content-Type: application/json" --data "$3"} "$VAULT_ADDR/v1/$2"
}

seal_status="$(curl -fsS "$VAULT_ADDR/v1/sys/seal-status")"
if [ "$(echo "$seal_status" | jq -r .initialized)" = false ]; then
    curl -fsS -X PUT --data '{"secret_shares": 1, "secret_threshold": 1}' "$VAULT_ADDR/v1/sys/init" > /vault-init/init.json
    echo "vault: initialized (unseal key and root token in the vault-init volume)"
fi
if [ ! -s /vault-init/init.json ]; then
    echo "vault: initialized, but its unseal key is gone (vault-init volume). Start the data space over:" >&2
    echo "  dev down, docker volume rm <project>_vault-data <project>_vault-init <project>_edc-postgres-data, dev up" >&2
    exit 1
fi
if [ "$(curl -fsS "$VAULT_ADDR/v1/sys/seal-status" | jq -r .sealed)" = true ]; then
    jq '{key: .keys_base64[0]}' /vault-init/init.json | curl -fsS -o /dev/null -X PUT --data @- "$VAULT_ADDR/v1/sys/unseal"
    echo "vault: unsealed"
fi
if ! vault_api GET auth/token/lookup-self > /dev/null 2>&1; then
    jq -n --arg id "$VAULT_TOKEN" '{id: $id, policies: ["root"], no_parent: true}' \
        | curl -fsS -o /dev/null -X POST -H "X-Vault-Token: $(jq -r .root_token /vault-init/init.json)" \
            --data @- "$VAULT_ADDR/v1/auth/token/create"
    echo "vault: runtimes' token created"
fi
if ! vault_api GET sys/mounts/secret > /dev/null 2>&1; then
    vault_api POST sys/mounts/secret '{"type": "kv", "options": {"version": "2"}}'
    echo "vault: KV store 'secret' enabled"
fi

put() { # put <path> <value>
    jq -n --arg v "$2" '{data: {content: $v}}' \
        | curl -fsS -o /dev/null -H "X-Vault-Token: $VAULT_TOKEN" -H "Content-Type: application/json" \
            -X POST --data @- "$VAULT_ADDR/v1/secret/data/$1"
}

exists() { # exists <path>
    curl -fsS -o /dev/null -H "X-Vault-Token: $VAULT_TOKEN" "$VAULT_ADDR/v1/secret/data/$1" 2> /dev/null
}

cd /tmp
for f in $FOLDERS; do
    if exists "$f/aes-key-alias"; then
        echo "$f: encryption key already in Vault"
    else
        put "$f/aes-key-alias" "$(openssl rand -base64 32)"
        echo "$f: encryption key stored in Vault"
    fi
done

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
