# {{PROJECT_NAME}}

FastAPI POC (Python 3.14, uv). The development environment runs in a container; the host only needs Docker.

## Develop

```bash
dev up        # build and start the dev container; installs dependencies
dev shell     # open a shell inside it
```

Inside the container:

```bash
just              # list commands
just install      # uv sync
just dev          # app with reload on http://localhost:3000 (APP_PORT), docs at /docs
just test         # pytest
just lint         # ruff check + format check
just build        # verify uv.lock, byte-compile
```

Code lives in `app/`, tests in `tests/`. Add a dependency with `uv add <package>` (dev-only: `uv add --dev <package>`); commit `uv.lock`.

If `dev up` fails because the port is in use, set another `APP_PORT` in `.env`, then `dev down && dev up`.

Stop the container from the host:

```bash
dev down
```

## Stack

uv and Python are declared in `.devcontainer/devcontainer.json` (local `uv` feature, which bakes Python into the image). `.python-version` pins the Python version for uv; `pyproject.toml` pins direct dependencies and `uv.lock` pins everything.

## IDE

Any editor works on the files. Dev Containers–aware tools (VS Code, JetBrains, Codespaces) can attach using `.devcontainer/devcontainer.json`.

## Production image

`Dockerfile` is a stub for when the POC graduates (no dev dependencies, runs as uid 1000):

```bash
docker build -t {{PROJECT_NAME}} .
```
