#!/usr/bin/env bash
# Runs as root during the dev container build. VERSION and PYTHON come from the feature options.
set -euo pipefail

curl -LsSf "https://astral.sh/uv/$VERSION/install.sh" | env UV_INSTALL_DIR=/usr/local/bin UV_NO_MODIFY_PATH=1 sh

# In the image layer, so it is not downloaded again on every container rebuild.
UV_PYTHON_INSTALL_DIR=/opt/uv-python uv python install "$PYTHON"

# Owned by the dev user so uv can add other Python versions without sudo.
chown -R "$_REMOTE_USER:$_REMOTE_USER" /opt/uv-python
