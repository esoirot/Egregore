# One-shot job (a Kubernetes Job later): puts each participant's transfer token keys into Vault.
FROM alpine:3.24.2@sha256:294b683cb724975bec92580e1e685676bd4b50bda910ddb8c51d4cabeaec77e6
RUN apk add --no-cache openssl jq curl
COPY seed.sh /seed.sh
USER 10001
ENTRYPOINT ["/bin/sh", "/seed.sh"]
