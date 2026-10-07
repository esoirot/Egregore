#!/usr/bin/env bash
# Runs as root during the dev container build.
set -euo pipefail

apt-get update
apt-get install -y --no-install-recommends jq
rm -rf /var/lib/apt/lists/*
