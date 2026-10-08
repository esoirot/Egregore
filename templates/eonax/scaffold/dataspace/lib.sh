# shellcheck shell=bash disable=SC2034  # ASSET_ID, REQUESTS, ...: used by the scripts that source this file
# Shared by the step scripts: where the services are, how to call their APIs, the identities (DIDs),
# and the state each step leaves for the next (.dataspace/).

PROVIDER_MANAGEMENT=http://provider-controlplane:8081/management
CONSUMER_MANAGEMENT=http://consumer-controlplane:8081/management
# Identities: did:web:<host>%3A<port>:<path> resolves to http://<host>:<port>/<path>/did.json
AUTHORITY_DID="did:web:issuerservice%3A10016:issuer"
did_of() { echo "did:web:$1-identityhub%3A7083:$1"; } # did_of provider|consumer

# Identity Hub and Issuer Service APIs: the header x-api-key, with the superuser key of each runtime.
ISSUER_IDENTITY=http://issuerservice:10015/api/identity/v1beta/participants
ISSUER_ADMIN=http://issuerservice:10013/api/admin/v1beta/participants/issuer
ISSUER_KEY="${ISSUER_SUPERUSER_KEY:-c3VwZXItdXNlcg==.c3VwZXItc2VjcmV0LWtleQo=}"
identity_api() { echo "http://$1-identityhub:7081/api/identity/v1beta/participants"; } # identity_api provider|consumer
IDENTITYHUB_KEY="${IDENTITYHUB_SUPERUSER_KEY:-c3VwZXItdXNlcg==.c3VwZXItc2VjcmV0LWtleQo=}"

ASSET_ID=gtfs-demo-transit # the default asset of catalog, negotiate, transfer
STATE=.dataspace
REQUESTS=dataspace/requests

cd "$(dirname "${BASH_SOURCE[0]}")/.." || exit 1
mkdir -p "$STATE"

# management <provider|consumer> <METHOD> <path> [json-body]: call a participant's management API.
# The API key comes from .env (PROVIDER_API_KEY, CONSUMER_API_KEY) or the stack's defaults.
management() {
    local base key
    case "$1" in
        provider) base="$PROVIDER_MANAGEMENT"; key="${PROVIDER_API_KEY:-provider-api-key}" ;;
        consumer) base="$CONSUMER_MANAGEMENT"; key="${CONSUMER_API_KEY:-consumer-api-key}" ;;
    esac
    curl -sS --fail-with-body -X "$2" -H "X-Api-Key: $key" -H "Content-Type: application/json" \
        ${4:+--data "$4"} "$base$3"
}

# create <url> <header> <json-body> <label>: POST that also accepts "already exists" (409),
# so every setup step can run again.
create() {
    local code
    code="$(curl -sS -o "$STATE/last-response.json" -w '%{http_code}' -X POST -H "$2" \
        -H "Content-Type: application/json" --data "$3" "$1")"
    case "$code" in
        200 | 201 | 204) echo "✓ created  $4" ;;
        409) echo "✓ exists   $4" ;;
        *) echo "✗ $4: HTTP $code"; cat "$STATE/last-response.json"; echo; return 1 ;;
    esac
}

# wait_for_state <participant> <path> <wanted-state>: poll a negotiation or transfer process.
wait_for_state() {
    local state=""
    for _ in $(seq 1 180); do
        state="$(management "$1" GET "$2" | jq -r '.state')"
        if [[ "$state" == "$3" ]]; then return 0; fi
        if [[ "$state" == TERMINATED ]]; then
            echo "Terminated: $(management "$1" GET "$2" | jq -r '.errorDetail // "no detail"')" >&2
            return 1
        fi
        sleep 1
    done
    echo "Still $state after 180 s (wanted $3): docker compose logs, from the project folder on the host." >&2
    return 1
}

# need <file> <step>: a later step needs what an earlier one saved.
need() {
    if [[ ! -s "$STATE/$1" ]]; then echo "Run 'just $2' first." >&2; exit 1; fi
}
