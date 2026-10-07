# shellcheck shell=bash disable=SC2034  # ASSET_ID, REQUESTS: used by the scripts that source this file
# Shared by the step scripts (publish, catalog, negotiate, transfer): where the connectors are,
# how to call their management API, and the state each step leaves for the next (.dataspace/).

PROVIDER_MANAGEMENT=http://provider-controlplane:8081/management
CONSUMER_MANAGEMENT=http://consumer-controlplane:8081/management
ASSET_ID=gtfs-demo-transit
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

# wait_for_state <participant> <path> <wanted-state>: poll a negotiation or transfer process.
wait_for_state() {
    local state=""
    for _ in $(seq 1 60); do
        state="$(management "$1" GET "$2" | jq -r '.state')"
        if [[ "$state" == "$3" ]]; then return 0; fi
        if [[ "$state" == TERMINATED ]]; then
            echo "Terminated: $(management "$1" GET "$2" | jq -r '.errorDetail // "no detail"')" >&2
            return 1
        fi
        sleep 1
    done
    echo "Still $state after 60 s (wanted $3): docker compose logs, from the project folder on the host." >&2
    return 1
}

# need <file> <step>: a later step needs what an earlier one saved.
need() {
    if [[ ! -s "$STATE/$1" ]]; then echo "Run 'just $2' first." >&2; exit 1; fi
}
