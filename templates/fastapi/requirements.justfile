# Team command contract: every project exposes install, dev, test, lint, build.
# Run inside the container (dev shell). requirements.txt project, written by templates/fastapi/adopt.

set dotenv-load

# List recipes
default:
    @just --list

# Install dependencies into .venv (run by dev up)
install:
    uv venv --allow-existing
    uv pip install -r requirements.txt
@DEV_REQUIREMENTS@
# Start the app on port 3000 with reload (reachable on the host at APP_PORT).
# FastAPI finds the app in main.py, app.py, api.py or app/{main,app,api}.py.
dev:
    uv run fastapi dev --host 0.0.0.0 --port 3000

# Run tests
test *args:
    uv run pytest {{args}}

# Lint and check formatting (ruff runs from uvx, it does not need to be a dependency)
lint:
    uvx ruff@0.16.10 check .
    uvx ruff@0.16.10 format --check .

# Byte-compile the project
build:
    uv run python -m compileall -q -x '\.venv' .
