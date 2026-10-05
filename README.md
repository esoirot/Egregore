# Development Environment

Standardized, containerized development environments for POC projects.

The team builds POCs on arbitrary stacks. This repository keeps them **consistent where it matters** — how a project is set up, entered and operated — while leaving each project free to pick its stack.

* **One way in:** `dev up`, `dev shell`, `dev down` in every project.
* **One command contract:** `just install | dev | test | lint | build` in every project.
* **Stack as code:** each project declares its stack in `.devcontainer/devcontainer.json` (Dev Container Features), not in a hand-maintained image.
* **Clean host:** the host only needs Git and Docker. Runtimes, package managers and even the Dev Containers CLI run in containers.

## Architecture

```text
Windows 11
└── WSL2
    └── Ubuntu (git + Docker Engine only)
        └── Docker Engine
            ├── devcontainer-cli container   ← runs `dev` commands, then exits
            └── Project containers
                ├── base image (Ubuntu, dev user, git, just)
                └── + project features (Node, Python, ...)
```

Source code stays on the WSL filesystem and is bind-mounted into the project container at `/workspace`.

```text
~/projects/my-project/   ── bind mount ──▶   /workspace
```

---

## Repository Structure

```text
dev-environment/
├── images/
│   ├── base/                  # shared base image for every project
│   │   ├── Dockerfile
│   │   ├── image.env
│   │   └── validate
│   └── devcontainer-cli/      # Dev Containers CLI, used by scripts/dev
│       ├── Dockerfile
│       ├── image.env
│       └── validate
│
├── templates/
│   ├── fastapi/
│   │   └── scaffold/          # starter files copied once into a new project
│   │       ├── .devcontainer/
│   │       │   ├── devcontainer.json
│   │       │   └── uv/            # local feature: uv + Python baked into the image
│   │       ├── app/main.py
│   │       ├── tests/test_main.py
│   │       ├── pyproject.toml
│   │       ├── .python-version
│   │       ├── compose.yaml
│   │       ├── justfile
│   │       ├── Dockerfile
│   │       ├── .dockerignore
│   │       ├── .env.example
│   │       ├── .gitignore
│   │       └── README.md
│   ├── java/
│   │   └── scaffold/
│   │       ├── .devcontainer/devcontainer.json
│   │       ├── compose.yaml
│   │       ├── justfile           # `init` runs `gradle init` on first dev up
│   │       ├── Dockerfile
│   │       ├── .dockerignore
│   │       ├── .env.example
│   │       └── README.md
│   ├── node/
│   │   └── scaffold/
│   │       ├── .devcontainer/devcontainer.json
│   │       ├── src/app.js, src/main.js
│   │       ├── test/app.test.js
│   │       ├── package.json
│   │       ├── biome.json
│   │       ├── compose.yaml
│   │       ├── justfile
│   │       ├── Dockerfile
│   │       ├── .dockerignore
│   │       ├── .env.example
│   │       ├── .gitignore
│   │       └── README.md
│   └── rails/
│       └── scaffold/
│           ├── .devcontainer/
│           │   ├── devcontainer.json
│           │   └── ruby/          # local feature: builds Ruby with ruby-build
│           ├── compose.yaml
│           ├── justfile           # `init` runs `rails new` on first dev up
│           ├── .env.example
│           └── README.md
│
├── scripts/
│   ├── bootstrap              # host setup (Docker Engine, systemd, docker group)
│   ├── install                # PATH + build/validate images
│   ├── new-project            # generate a project from a template
│   └── dev                    # per-project entry point
│
├── .github/workflows/
│   └── images.yml             # CI: shellcheck, build + validate images, smoke-test templates
│
├── .gitignore                 # keeps scaffold .env.example files tracked (!.env.example)
├── CLAUDE.md
└── README.md
```

### `images/`

Every directory with an `image.env` (defines `IMAGE`) and a `Dockerfile` is built by `install` and CI. An optional executable `validate <image>` checks the result.

* `base`: Ubuntu 26.04, non-root `dev` user (uid 1000, passwordless sudo), git, build-essential, `just`. No language runtime.
* `devcontainer-cli`: the [Dev Containers CLI](https://github.com/devcontainers/cli) plus the Docker CLI. `scripts/dev` runs it with the host Docker socket, so the host never needs Node.

### `templates/`

One directory per stack preset. A template is only a `scaffold/`: no image, no script. The stack itself is a list of features in `scaffold/.devcontainer/devcontainer.json`.

| Template | Stack | Notes |
| -------- | ----- | ----- |
| `fastapi` | Python 3.14.8, FastAPI 0.142.2, uv 0.12.23, pytest, ruff | Local `uv` feature installs uv and bakes Python into the image (no compile, about 10 s). Ships a working app (`/`, `/health`) and tests; `postCreateCommand` runs `just install` (`uv sync`, creates `uv.lock`). Python 3.15 is not final yet (rc), so 3.14 is the latest stable. Ships a production `Dockerfile` stub. |
| `java`   | JDK 27.0.0 (Amazon Corretto), Gradle 9.8.0, JUnit 6 (Jupiter) | Official `java` feature (SDKMAN). Plain Java, no framework. On first `dev up`, `just init` runs `gradle init` (Java application, Kotlin DSL, wrapper pinned to 9.8.0, toolchain 27). Corretto because SDKMAN has no Temurin build of 27. Ships a production `Dockerfile` stub. |
| `node`   | Node 26.10.0, pnpm 12.9.1, Biome 2.5.15 | Official `node` feature. Ships a working app on Node built-ins (`node:http`, `/` and `/health`) and tests (`node:test`); Biome for lint and format. `postCreateCommand` runs `just install` (creates `pnpm-lock.yaml`). Ships a production `Dockerfile` stub. |
| `rails`  | Ruby 4.0.7, Rails 8.1.4, SQLite | Local `ruby` feature (the official one does not support Ruby 4), so the first `dev up` compiles Ruby (a few minutes, then cached). On first `dev up`, `just init` runs `rails new` (app named after the project); Rails generates its own production `Dockerfile` and Kamal config. |

### `.github/workflows/images.yml`

On every pull request and push to `main`: `shellcheck` on the scripts and local feature installers, build and validate every image, then generate a project from every template and run `just --list` inside it.

Publishing to GitHub Container Registry (GHCR) is **disabled**. To enable it:

* CI: set the repository variable `PUBLISH_IMAGES` to `true` (Settings → Secrets and variables → Actions → Variables). Pushes to `main` then publish the images.
* Developers: run `PULL_IMAGES=1 ./scripts/install` to pull the published images instead of building them.

---

# Setup

## Requirements

* Windows 11, WSL2, Ubuntu
* Docker Engine inside Ubuntu (Docker Desktop is **not** required)

`scripts/bootstrap` installs what is missing.

## 1. Clone

Anywhere on the WSL filesystem, for example:

```bash
git clone <company-repository-url> ~/dev-environment
cd ~/dev-environment
```

## 2. Bootstrap the host

```bash
./scripts/bootstrap
```

Idempotent. It installs git and Docker Engine with the Compose plugin (official Docker apt repository), enables systemd in `/etc/wsl.conf` so Docker starts with WSL, and adds you to the `docker` group. Follow the "Next" line it prints (`wsl --shutdown` or a new terminal).

## 3. Install

```bash
./scripts/install
```

Idempotent. It:

* checks git, Docker, Compose and daemon access
* adds `scripts/` to `PATH` in `~/.bashrc` (wherever the repository is cloned)
* builds and validates every image in `images/` (with `PULL_IMAGES=1`: pulls from GHCR, building only if the pull fails)

If you pull from GHCR and the package is private, log in once:

```bash
echo <github-token> | docker login ghcr.io -u <github-user> --password-stdin
```

---

# Creating a Project

```bash
new-project <template> <project-name>
new-project node my-poc
```

Run `new-project` without arguments to list templates. Project names are lowercase letters, numbers, `_` and `-` (the name is also the Compose project name).

Projects go to `~/projects/` by default:

```bash
PROJECTS_DIR=~/work new-project node my-poc
```

`new-project` copies the template's scaffold, fills in the placeholders, creates `.env` from `.env.example`, and runs `dev up`. If port 3000 is already used on the host, pick another one up front:

```bash
APP_PORT=3100 new-project node my-poc
```

---

# Daily Workflow

From the project root, on the host:

```bash
dev up        # build the container (base image + features) and start it
dev shell     # bash inside the container
dev exec <cmd>
dev down      # stop and remove the project's containers
```

Inside the container, every project speaks the same commands:

```bash
just          # list commands
just install
just dev
just test
just lint
just build
```

## The `just` Command Contract

Every scaffold ships a `justfile` with at least `install`, `dev`, `test`, `lint`, `build`. What a recipe runs depends on the stack (the Node scaffold delegates to `package.json` scripts); the names do not. Anyone can enter any POC and know how to run it. `just --list` shows each recipe with its description.

Projects may add recipes. They should not rename or remove the five contract recipes.

## Ports

The scaffold's `compose.yaml` publishes the app's port 3000 on the host:

```yaml
ports:
  - "127.0.0.1:${APP_PORT:-3000}:3000"
```

Open `http://localhost:3000` from Windows. To run several POCs at once, set a different `APP_PORT` in each project's `.env` (created by `new-project` from `.env.example`).

If `dev up` fails because the port is already in use ("port is already allocated" or "address already in use"), set another `APP_PORT` in `.env`, then run `dev down` before `dev up`. A plain `dev up` retry starts the half-created container without its port.

---

# Stacks and Versions

A project's stack is declared, not installed by hand:

```json
"features": {
  "ghcr.io/devcontainers/features/node:1": { "version": "26.10.0", "pnpmVersion": "12.9.1" }
}
```

Each project owns its versions. One POC can run Node 26 and another Node 24; a POC that needs Python too adds:

```json
"ghcr.io/devcontainers/features/python:1": { "version": "3.14" }
```

then reruns `dev up`. Available features: <https://containers.dev/features>.

Never install runtimes manually inside a running container. Change `devcontainer.json` and rerun `dev up`, so the change is in the project's git history.

---

# Project Isolation

Each project has its own container, built from the shared base image plus its own features:

```text
~/projects/poc-a   →  base + node 26
~/projects/poc-b   →  base + node 24 + python 3.14
```

Containers are disposable. Deleting and recreating one never touches source code, which lives on the host. If a container becomes inconsistent, `dev down && dev up` instead of repairing it.

Keep source code on the WSL filesystem (`~/projects/...`), not under `/mnt/c/...`.

---

# IDEs

Any editor works on the files. Tools that support the open [Dev Containers spec](https://containers.dev) (VS Code, JetBrains IDEs, GitHub Codespaces) can attach using the same `.devcontainer/devcontainer.json`, which reuses the project's `compose.yaml`. Editors without support ignore it.

---

# When a POC Graduates

Each project gets a production `Dockerfile` (plus `.dockerignore`), separate from the dev container: the `node`, `java` and `fastapi` scaffolds ship a stub, `rails new` generates one for `rails`. Adjust it to the app and build on the host:

```bash
docker build -t my-poc .
```

---

# Team Conventions

1. **Keep the Ubuntu host clean.** Only git and Docker on the host. Everything else belongs in containers.
2. **Declare the stack in `devcontainer.json`.** No manual installs in running containers.
3. **Keep the `just` contract.** `install`, `dev`, `test`, `lint`, `build` in every project.
4. **Use versioned, pinned images.** Tags are versioned (never `latest`); base images are pinned by digest in the Dockerfiles.
5. **Change shared environments here.** Edit `images/` or `templates/` in this repository, not individual projects' copies.
6. **Keep application config in the application repository.**

---

# Updating

```bash
cd ~/dev-environment
git pull
./scripts/install
```

Then in a project:

```bash
dev down
dev up
```

Scaffolds are copied once: changes to a template only reach new projects. Port fixes from a template to existing projects by hand when needed.

To bump a pinned version (base image digest, Dev Containers CLI, or a template's runtime and tools), edit the `FROM` line or version in the relevant `Dockerfile`, `devcontainer.json` or `justfile`, and bump `IMAGE` in `image.env` when an image changes. Some templates pin the same version in several files (for example the dev feature and the production `Dockerfile`); `CLAUDE.md` lists where.

---

# Adding a New Template

A template is a `templates/<name>/scaffold/` directory. No script changes and no image needed. Example: `go`.

```text
templates/go/
└── scaffold/
    ├── .devcontainer/devcontainer.json
    ├── compose.yaml
    ├── justfile
    ├── Dockerfile
    ├── .dockerignore
    ├── .env.example
    ├── .gitignore
    └── README.md
```

Start from `templates/node/scaffold/` and change:

* `devcontainer.json`: the features (e.g. `ghcr.io/devcontainers/features/go:1`). If no published feature supports the version you need, write a local one in `.devcontainer/<name>/` (`devcontainer-feature.json` + `install.sh`; see `templates/rails` and `templates/fastapi`). For a stack whose app is generated by a tool (like `rails new` or `gradle init`), use `postCreateCommand` to run an idempotent `just init` recipe (see `templates/rails` and `templates/java`).
* `justfile`: what the five contract recipes run
* `Dockerfile`, `.gitignore`, `.dockerignore`, `README.md`: stack-specific content
* `compose.yaml`: the app port, if not 3000

Placeholders replaced in every file by `new-project`:

| Placeholder        | Value                                |
| ------------------ | ------------------------------------ |
| `{{PROJECT_NAME}}` | the project name                     |
| `{{IMAGE}}`        | `IMAGE` from `images/base/image.env` |

Keep the contract: service `dev`, `/workspace`, `remoteUser: dev`, the five `just` recipes, and placeholders instead of hardcoded image tags. Open a pull request: CI generates a project from the new template and runs `just --list` in it.

---

# Design Principle

> **Keep the host stable. Make development environments disposable. Standardize the interface, not the stack.**
