# Dev Containers CLI, run by scripts/dev so the host only needs Docker.
FROM docker:29.8.2-cli@sha256:b1805116a6a86cc591b5d5f60a910a0715cdcc9d18d866ad68b1457ead25c35c

RUN apk add --no-cache nodejs npm \
    && npm install --global @devcontainers/cli@0.89.0 \
    && chmod 0666 /etc/passwd /etc/group

COPY entrypoint /usr/local/bin/devcontainer-entrypoint
ENTRYPOINT ["devcontainer-entrypoint"]
