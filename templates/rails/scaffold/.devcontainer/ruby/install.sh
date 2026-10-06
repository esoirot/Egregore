#!/usr/bin/env bash
# Runs as root during the dev container build. VERSION comes from the feature option.
set -euo pipefail

RUBY_BUILD_VERSION=20260924

# tzdata: Rails (tzinfo) needs zoneinfo to boot. libpq-dev: builds the pg gem (--with postgres).
apt-get update
DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
    libssl-dev libyaml-dev zlib1g-dev libffi-dev libgmp-dev libreadline-dev tzdata libpq-dev

curl -fsSL "https://github.com/rbenv/ruby-build/archive/refs/tags/v$RUBY_BUILD_VERSION.tar.gz" | tar -xz -C /tmp
MAKE_OPTS="-j$(nproc)" "/tmp/ruby-build-$RUBY_BUILD_VERSION/bin/ruby-build" "$VERSION" /opt/ruby

# Writable through a group, not chowned to dev: the CLI remaps dev to the host uid
# after this build (only dev's home follows; supplementary groups stay). Not
# world-writable either: Bundler refuses gems under world-writable directories.
getent group devtools > /dev/null || groupadd --system devtools
usermod -aG devtools "$_REMOTE_USER"
chgrp -R devtools /opt/ruby
chmod -R g+rwX /opt/ruby
find /opt/ruby -type d -exec chmod g+s {} +

rm -rf "/tmp/ruby-build-$RUBY_BUILD_VERSION" /var/lib/apt/lists/*
