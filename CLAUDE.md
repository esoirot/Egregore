# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this repo is

Shared infrastructure for containerized team development environments (Windows 11 → WSL2 → Ubuntu → Docker Engine; no Docker Desktop). It is not an application: it holds container image definitions (`templates/`), host-side Bash scripts (`scripts/`), and docs. Application code lives in separate project repos generated under `~/projects/`.

Guiding principle: keep the Ubuntu host clean; project runtimes and dependencies belong in disposable containers, while source code stays on the WSL filesystem and is bind-mounted to `/workspace`.

## Commands

There is no build system, test suite, or linter. The installer is the validation step:

```bash
./scripts/install                     # check host prereqs, add scripts/ to PATH, build + validate every template image
new-project <template> <name>         # generate ~/projects/<name> (override with PROJECTS_DIR) and start its container
new-project                           # usage + list of available templates
docker build -t node-env:26 templates/node   # rebuild one image only
```

`install` is meant to be idempotent. After changing a Dockerfile, rerun it. For each template it checks that `whoami` is `dev`, then runs the template's optional `validate` script.

## How the pieces connect

- A template is any `templates/<name>/` directory with a `template.env`. It contains:
  - `Dockerfile`: the development image (build context is the template directory).
  - `template.env`: sourced by both scripts; must define `IMAGE` (versioned tag) and `SERVICE` (compose service name).
  - `project/`: files copied (dotfiles included) into every new project, with `{{PROJECT_NAME}}`, `{{IMAGE}}` and `{{SERVICE}}` replaced by `sed`.
  - `validate` (optional, executable): called by `install` as `validate <image>`; non-zero exit fails the install.
- `templates/node/` builds `node-env:26`: Ubuntu 26.04, a non-root `dev` user with passwordless sudo, Node installed through nvm, and pnpm installed globally through npm.
- `scripts/install` appends `export PATH="$HOME/dev-environment/scripts:$PATH"` to `~/.bashrc`. That path is hardcoded and does not use `REPO_ROOT`, so the repo has to be cloned at `~/dev-environment`.
- `scripts/new-project` is template-agnostic: it validates the template and project names, checks that `IMAGE` exists locally, copies `project/`, substitutes placeholders, then runs `docker compose up -d`. Generated compose services run `sleep infinity`, so developers work through `docker compose exec <SERVICE> bash`.
- Adding a template needs no script changes; the README section "Adding a New Template" is the user-facing guide.

## Things to keep in sync

- **Image tags** live only in each `template.env` (plus examples in `README.md`). Tags are intentionally versioned; never use `latest`.
- **Node version in the Dockerfile** appears in two places: `ARG NODE_VERSION=26`, which nvm resolves to the newest 26.x, and an exact `PATH` entry `.../versions/node/v26.10.0/bin`. If nvm installs a different patch version, non-interactive `node`/`pnpm` (as used by `install` validation and `docker run`) stop resolving. Update both together.
- New templates must keep the same contract: a non-root `dev` user (enforced by `install`), `/workspace` as the working directory, and placeholders rather than hardcoded tags in `project/`.

## Conventions

- Scripts are Bash with `set -euo pipefail`, are extensionless, and are executable. `install` checks that `new-project` is executable (`-x`).
- Stay IDE-independent: generators must not create `.devcontainer` or editor-specific config.
- Changes to the shared environment go in this repo (the Dockerfile), not in manual edits to running containers.
