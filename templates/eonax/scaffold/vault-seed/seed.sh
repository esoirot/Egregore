#!/bin/sh
# Brings Vault up for the runtimes: initializes it the first time (one unseal key and the root token,
# in /vault-init: the vault-init volume in dev, deploy/secrets/ in the deploy profile), unseals it,
# then makes sure of, in order:
#   - the KV store "secret"
#   - one policy per folder (<folder>-runtime): read and write secret/<folder>/* only
#   - one token per runtime (RUNTIMES: "<token>:<folder> ..."), with its folder's policy plus Vault's
#     default one (look up and renew itself), periodic (EDC renews it): a runtime reaches its
#     participant's folder, never another's, never admin
#   - the secrets every runtime needs, under secret/<folder>/<alias>, field "content" (where EDC's
#     HashiCorp Vault extension reads them, EDC_VAULT_HASHICORP_FOLDER):
#       aes-key-alias                                   per folder: encrypts its stored secrets
#       transfer-proxy-private-key / -public-key        per participant in PARTICIPANTS: EC P-256 pair
#                                                       its data plane signs access tokens (EDR) with
# The root token is only needed for the first run (init, store, policies, tokens). REVOKE_ROOT=true
# (deploy) revokes it then and keeps only the unseal key. Keys, DIDs and credentials of identities are
# not here: the Identity Hubs and the Issuer Service create them. Running again changes nothing.
set -eu

: "${VAULT_ADDR:?}" "${RUNTIMES:?}" "${PARTICIPANTS:?}"
INIT=/vault-init/init.json

api() { # api <token> <METHOD> <path> [json-body]
    curl -fsS -X "$2" -H "X-Vault-Token: $1" ${4:+-H "Content-Type: application/json" --data "$4"} "$VAULT_ADDR/v1/$3"
}
root() { # root: the root token, or a hint when it was revoked
    token="$(jq -r '.root_token // empty' "$INIT")"
    if [ -z "$token" ]; then
        echo "vault: setup needed but the root token was revoked: create one from the unseal key" >&2
        echo "  (vault operator generate-root), put it in $INIT as root_token, start again" >&2
        exit 1
    fi
    echo "$token"
}

if [ "$(curl -fsS "$VAULT_ADDR/v1/sys/seal-status" | jq -r .initialized)" = false ]; then
    (umask 077 && curl -fsS -X PUT --data '{"secret_shares": 1, "secret_threshold": 1}' "$VAULT_ADDR/v1/sys/init" > "$INIT")
    echo "vault: initialized (unseal key and root token in $INIT)"
fi
if [ ! -s "$INIT" ]; then
    echo "vault: initialized, but its unseal key is gone ($INIT). Start the data space over:" >&2
    echo "  remove the volumes vault-data, vault-init (dev) and edc-postgres-data together" >&2
    exit 1
fi
if [ "$(curl -fsS "$VAULT_ADDR/v1/sys/seal-status" | jq -r .sealed)" = true ]; then
    jq '{key: .keys_base64[0]}' "$INIT" | curl -fsS -o /dev/null -X PUT --data @- "$VAULT_ADDR/v1/sys/unseal"
    echo "vault: unsealed"
fi

# Setup that needs the root token: only what is missing.
missing=""
for r in $RUNTIMES; do
    if ! api "${r%%:*}" GET auth/token/lookup-self > /dev/null 2>&1; then missing="$missing $r"; fi
done
if [ -n "$missing" ]; then
    admin="$(root)"
    if ! api "$admin" GET sys/mounts/secret > /dev/null 2>&1; then
        api "$admin" POST sys/mounts/secret '{"type": "kv", "options": {"version": "2"}}'
        echo "vault: KV store 'secret' enabled"
    fi
    for r in $missing; do
        token="${r%%:*}" folder="${r#*:}"
        policy="path \"secret/data/$folder/*\" { capabilities = [\"create\", \"read\", \"update\", \"delete\"] }
path \"secret/metadata/$folder/*\" { capabilities = [\"read\", \"list\", \"delete\"] }"
        api "$admin" PUT "sys/policies/acl/$folder-runtime" "$(jq -n --arg p "$policy" '{policy: $p}')"
        jq -n --arg id "$token" --arg policy "$folder-runtime" \
            '{id: $id, policies: [$policy], no_parent: true, period: "1h", renewable: true}' \
            | curl -fsS -o /dev/null -X POST -H "X-Vault-Token: $admin" --data @- "$VAULT_ADDR/v1/auth/token/create"
        echo "vault: token for a $folder runtime created (policy $folder-runtime)"
    done
fi
if [ "${REVOKE_ROOT:-false}" = true ] && [ -n "$(jq -r '.root_token // empty' "$INIT")" ]; then
    api "$(root)" POST auth/token/revoke-self > /dev/null
    jq 'del(.root_token)' "$INIT" > "$INIT.new" && mv "$INIT.new" "$INIT"
    echo "vault: root token revoked (only the unseal key is kept)"
fi

# The runtimes' secrets, written with each folder's own runtime token.
token_of() { # token_of <folder>
    for r in $RUNTIMES; do if [ "${r#*:}" = "$1" ]; then echo "${r%%:*}"; return; fi; done
}
put() { # put <folder> <alias> <value>
    api "$(token_of "$1")" POST "secret/data/$1/$2" "$(jq -n --arg v "$3" '{data: {content: $v}}')" > /dev/null
}
exists() { # exists <folder> <alias>
    api "$(token_of "$1")" GET "secret/data/$1/$2" > /dev/null 2>&1
}

cd /tmp
for f in $(for r in $RUNTIMES; do echo "${r#*:}"; done | sort -u); do
    if exists "$f" aes-key-alias; then
        echo "$f: encryption key already in Vault"
    else
        put "$f" aes-key-alias "$(openssl rand -base64 32)"
        echo "$f: encryption key stored in Vault"
    fi
done

for p in $PARTICIPANTS; do
    if exists "$p" transfer-proxy-private-key; then
        echo "$p: transfer keys already in Vault"
        continue
    fi
    openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out key.pem 2> /dev/null
    openssl pkey -in key.pem -pubout -out pub.pem
    put "$p" transfer-proxy-private-key "$(cat key.pem)"
    put "$p" transfer-proxy-public-key "$(cat pub.pem)"
    rm -f key.pem pub.pem
    echo "$p: transfer keys stored in Vault"
done
