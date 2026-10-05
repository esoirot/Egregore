# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this repo is

Shared infrastructure for containerized POC development environments (Windows 11 → WSL2 → Ubuntu → Docker Engine; no Docker Desktop). It is not an application: it holds image definitions (`images/`), project scaffolds (`templates/`), host-side Bash scripts (`scripts/`), and docs. Application code lives in separate project repos generated under `~/projects/`.

Problem it solves: the team builds POCs on arbitrary stacks. Standardize the interface (`dev` entry point, `just` command contract, devcontainer.json), not the stack.

Guiding principle: the Ubuntu host only has git and Docker; everything else (runtimes, the Dev Containers CLI) runs in containers. Source code stays on the WSL filesystem and is bind-mounted to `/workspace`.

## Commands

There is no build system or test suite. CI (`.github/workflows/images.yml`) runs `shellcheck --severity=warning` (SC1091 infos from dynamic `source` paths are expected) on `scripts/*`, `images/*/validate` and local feature `install.sh` files, builds and validates every image, then runs `new-project` for every template and `dev exec just --list` in the result. Locally:

```bash
./scripts/bootstrap                   # host setup: git, Docker Engine + Compose (Docker apt repo), WSL systemd, docker group
./scripts/install                     # check prereqs, add scripts/ to PATH, build + validate every image (PULL_IMAGES=1 to pull from GHCR)
new-project <template> <name>         # generate ~/projects/<name> (override with PROJECTS_DIR), then `dev up`
dev up | shell | exec <cmd> | down    # per-project, from the project root
```

`bootstrap` and `install` are meant to be idempotent. GHCR is wired but **disabled**: `install` pulls only with `PULL_IMAGES=1` (falls back to building), and CI pushes on `main` only when the repository variable `PUBLISH_IMAGES` is `true`.

## How the pieces connect

- An image is any `images/<name>/` with `Dockerfile` + `image.env` (defines `IMAGE`, versioned tag) + optional executable `validate <image>`. `install` and CI build all of them.
  - `base`: Ubuntu 26.04 pinned by digest, non-root `dev` user (uid 1000; the stock `ubuntu` user is removed to free it), passwordless sudo, git, build-essential, `just`. No language runtime.
  - `devcontainer-cli`: `docker:<ver>-cli` + `@devcontainers/cli` (pinned), entrypoint `devcontainer`.
- A template is any `templates/<name>/scaffold/`. No image, no script. It is copied once (dotfiles included) into a new project; `{{PROJECT_NAME}}` and `{{IMAGE}}` (from `images/base/image.env`) are replaced by `sed`. The scaffold contains:
  - `.devcontainer/devcontainer.json`: `dockerComposeFile: ../compose.yaml`, service `dev`, `remoteUser: dev`, and the stack as Dev Container Features (pinned versions). Local features live in `.devcontainer/<name>/` (`devcontainer-feature.json` + `install.sh`, run as root; `_REMOTE_USER` available).
  - `compose.yaml`: top-level `name: {{PROJECT_NAME}}`, service `dev` on the base image, `sleep infinity`, port `127.0.0.1:${APP_PORT:-3000}:3000`.
  - `justfile`: the command contract `install`, `dev`, `test`, `lint`, `build` (plus `init` where the app is generated).
  - `Dockerfile` + `.dockerignore`: production image stub, separate from the dev container (node, java, fastapi; `rails new` generates Rails's own).
  - `.env.example`, `.gitignore`, `README.md`. `java` and `rails` ship no `.gitignore`: `gradle init` / `rails new` generate one. The root `.gitignore` ignores `.env.*` but re-includes `!.env.example`, otherwise scaffold `.env.example` files without their own `.gitignore` are never committed and `new-project` cannot seed `.env`.
- Templates:
  - `fastapi`: local `uv` feature installs uv 0.12.23 and `uv python install 3.14.8` into `/opt/uv-python` owned by `dev`, sets `UV_PYTHON_INSTALL_DIR` and `UV_LINK_MODE=copy`. Ships `app/` + `tests/`; `postCreateCommand: just install` runs `uv sync`. Dev deps include `httpx2` because Starlette's TestClient deprecates `httpx`. `pyproject.toml` has `package = false` and pytest `pythonpath = ["."]`.
  - `java`: official java feature via SDKMAN, Corretto `27.0.0-amzn` (SDKMAN has no Temurin 27), Gradle 9.8.0. `postCreateCommand: just init` runs `gradle init --type java-application --dsl kotlin` with toolchain 27 and `--overwrite` if no `settings.gradle.kts`, then appends `.env` to Gradle's `.gitignore`. Its `Dockerfile` installs `findutils` in both stages because `gradlew` and the generated `bin/app` need `xargs`, missing from Amazon Linux.
  - `node`: official node feature, Node 26.10.0, pnpm 12.9.1. Ships a working app on Node built-ins (`src/app.js` exports `createApp`, `src/main.js` listens on 3000) and `node:test` tests; `postCreateCommand: just install`. `justfile` delegates to `package.json` scripts: `dev` = `node --watch`, `test` = `node --test`, `lint` = `biome check .` (Biome 2.5.15; `biome.json` only sets space indentation, since Biome defaults to tabs), `build` = `node --check` (no bundler).
  - `rails`: local `ruby` feature compiles Ruby 4.0.7 with ruby-build into `/opt/ruby` owned by `dev` (the official rvm-based ruby feature rejects Ruby 4) and installs `tzdata` (base image lacks it; Rails needs it to boot). `postCreateCommand: just init`: if no `Gemfile`, installs Rails 8.1.4 and runs `rails new . --name=<project> --skip` (keeps scaffold files, adds `!/.env.example` to the Rails `.gitignore`), then `bundle install`. `just build` precompiles assets in production mode then clobbers them, because a dev-mode precompile makes `just dev` serve stale assets.
- `scripts/dev` runs the CLI image with `/var/run/docker.sock` mounted and `$PWD` mounted at the same path (the CLI passes host paths to the daemon). `--workspace-folder` must come before the exec command. `down` is plain `docker compose down` (works because compose sets `name:`).
- `scripts/new-project` validates names (project names lowercase: they are compose project names), requires the base image locally, copies the scaffold, substitutes, seeds `.env` from `.env.example` (honoring `APP_PORT=<port> new-project ...`), then runs `dev up`. If the host port is taken, `dev up` fails; a retry without `dev down` starts the half-created container without its port.
- `scripts/install` appends `export PATH="<repo>/scripts:$PATH"` to `~/.bashrc`, using the actual clone location.
- Adding a template needs no script changes; the README section "Adding a New Template" is the user-facing guide.

## Things to keep in sync

- **Image tags** live only in each `images/*/image.env`. Tags are versioned; never `latest`. Bump the tag when the image changes.
- **FastAPI template pins**: uv and Python in `devcontainer.json` (`./uv` feature options), `.python-version`, and the `python:<ver>-slim` + `ghcr.io/astral-sh/uv:<ver>` tags/digests in its `Dockerfile` (keep all equal); direct dependencies `==`-pinned in `pyproject.toml`.
- **Java template pins**: JDK in `devcontainer.json` (SDKMAN id) and the `amazoncorretto` tags/digests in its `Dockerfile`; Java major in the `justfile` (`--java-version`); Gradle in `devcontainer.json` (`gradleVersion`, which `gradle init` copies into the wrapper).
- **Rails template pins**: Ruby in `devcontainer.json` (`./ruby` feature option), `RUBY_BUILD_VERSION` in `.devcontainer/ruby/install.sh` (must know that Ruby version), Rails in the `justfile` (`rails_version`).
- **Image pins**: base image digests in `images/*/Dockerfile`; `@devcontainers/cli` version in its Dockerfile and its `image.env` tag.
- **Node template pins**: Node and pnpm in `devcontainer.json` (features), `packageManager` in `package.json`, and the scaffold `Dockerfile` (keep equal); Biome exact in `package.json`.
- Templates keep the contract: service `dev`, `/workspace`, `remoteUser: dev`, the five `just` recipes, placeholders rather than hardcoded tags.

## Conventions

- Scripts are Bash with `set -euo pipefail`, are extensionless, and are executable. `install` checks that `new-project` and `dev` are executable (`-x`).
- Stacks change through `devcontainer.json` features, never through manual installs in running containers. Shared changes go in this repo.
