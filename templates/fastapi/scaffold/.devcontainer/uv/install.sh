#!/usr/bin/env bash
# Runs as root during the dev container build. VERSION and PYTHON come from the feature options.
set -euo pipefail

curl -LsSf "https://astral.sh/uv/$VERSION/install.sh" | env UV_INSTALL_DIR=/usr/local/bin UV_NO_MODIFY_PATH=1 sh

# In the image layer, so it is not downloaded again on every container rebuild.
UV_PYTHON_INSTALL_DIR=/opt/uv-python uv python install "$PYTHON"

# Writable through a group, not chowned to dev: the CLI remaps dev to the host uid
# after this build (only dev's home follows; supplementary groups stay).
getent group devtools > /dev/null || groupadd --system devtools
usermod -aG devtools "$_REMOTE_USER"
chgrp -R devtools /opt/uv-python
chmod -R g+rwX /opt/uv-python
find /opt/uv-python -type d -exec chmod g+s {} +
