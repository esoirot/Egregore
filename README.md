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
            └── Project containers (one compose project each)
                ├── dev: base image (Ubuntu, dev user, git, just)
                │        + project features (Node, Python, ...)
                └── optional services (postgres, redis)
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
│       ├── entrypoint         # registers the host user, then runs the CLI
│       ├── image.env
│       └── validate
│
├── services/                  # optional services for new-project --with
│   ├── postgres/              # compose.yaml (pinned image, volume, healthcheck) + env
│   └── redis/
│
├── templates/                # per template: scaffold/ (dev environment) + starter/ (sample project)
│   ├── _shared/               # compose.yaml, .env.example: copied into every template's scaffold
│   ├── fastapi/
│   │   ├── adopt              # --from: pip recipes for requirements.txt-only repos
│   │   ├── test               # adopt scenarios, run by tests/new-project
│   │   ├── requirements.justfile
│   │   ├── vscode-extensions  # --ide=vscode: one extension id per line
│   │   ├── scaffold/          # always copied
│   │   │   ├── .devcontainer/
│   │   │   │   ├── devcontainer.json
│   │   │   │   └── uv/        # local feature: uv + Python baked into the image
│   │   │   ├── justfile
│   │   │   └── .gitignore
│   │   └── starter/           # new projects only, never with --from
│   │       ├── app/main.py, tests/test_main.py
│   │       ├── pyproject.toml, .python-version
│   │       ├── Dockerfile, .dockerignore
│   │       └── README.md
│   ├── java/
│   │   ├── adopt              # --from: .sdkmanrc versions, Maven recipes for pom.xml
│   │   ├── test
│   │   ├── maven.justfile     # recipes written by adopt for Maven builds
│   │   ├── vscode-extensions
│   │   ├── scaffold/
│   │   │   ├── .devcontainer/devcontainer.json
│   │   │   └── justfile       # `init` runs `gradle init` on first dev up
│   │   └── starter/           # Dockerfile, .dockerignore, README.md
│   ├── node/
│   │   ├── adopt              # --from: .nvmrc / .node-version
│   │   ├── test
│   │   ├── vscode-extensions
│   │   ├── scaffold/
│   │   │   ├── .devcontainer/devcontainer.json
│   │   │   ├── justfile
│   │   │   └── .gitignore
│   │   └── starter/
│   │       ├── src/app.js, src/main.js, test/app.test.js
│   │       ├── package.json, biome.json
│   │       ├── Dockerfile, .dockerignore
│   │       └── README.md
│   └── rails/
│       ├── adopt              # --from: .ruby-version
│       ├── test
│       ├── env-skip           # service variables not written for Rails (DATABASE_URL)
│       ├── vscode-extensions
│       ├── scaffold/
│       │   ├── .devcontainer/
│       │   │   ├── devcontainer.json
│       │   │   └── ruby/      # local feature: builds Ruby with ruby-build
│       │   └── justfile       # `init` runs `rails new` on first dev up
│       └── starter/           # README.md (rails new generates the rest)
│
├── tests/
│   ├── renovate               # every templates/ renovate: comment yields its version (no Docker)
│   ├── new-project            # behavior tests for new-project and dev update (stubbed dev, fixture repos),
│   │                          # template contract, and each templates/<name>/test
│   ├── check-image-tags       # behavior tests for scripts/check-image-tags (no Docker)
│   └── dev                    # dev up cleanup, services, host uid ≠ 1000 (real containers)
│
├── scripts/
│   ├── bootstrap              # host setup (Docker Engine, systemd, docker group)
│   ├── install                # PATH + build/validate images
│   ├── new-project            # create a project, adopt a repo (--from), add services (--with), IDE (--ide)
│   ├── dev                    # per-project entry point (up, shell, exec, down, update)
│   └── check-image-tags       # CI: an image that changed must get a new tag
│
├── .github/workflows/
│   └── images.yml             # CI: shellcheck, build + validate images, smoke-test templates
│
├── renovate.json              # Renovate: weekly PRs bumping every pinned version
├── .gitignore                 # keeps scaffold .env.example files tracked (!.env.example)
├── CLAUDE.md
└── README.md
```

### `images/`

Every directory with an `image.env` (defines `IMAGE`) and a `Dockerfile` is built by `install` and CI. An optional executable `validate <image>` checks the result.

* `base`: Ubuntu 26.04, non-root `dev` user (uid 1000, passwordless sudo), git, build-essential, `just`. No language runtime.
* `devcontainer-cli`: the [Dev Containers CLI](https://github.com/devcontainers/cli) plus the Docker CLI. `scripts/dev` runs it with the host Docker socket, so the host never needs Node. It runs **as your host user**, so the CLI remaps the container's `dev` user to your uid: files in `/workspace` stay yours and writable whatever your uid is (1000 on most WSL setups, 1001 on GitHub's runners).

### `templates/`

One directory per stack preset, with no image of its own:

* `scaffold/`: the dev environment (`.devcontainer/`, `justfile`, …), copied into every project, on top of `templates/_shared/` (`compose.yaml`, `.env.example`, the same for every stack; a template's own file of the same name wins). The stack itself is a list of features in `scaffold/.devcontainer/devcontainer.json`.
* `starter/`: a sample project (app, tests, production `Dockerfile` stub, README), copied only into **new** projects, never into a repository adopted with `--from`.
* `adopt` (optional, executable): adapts the copied scaffold to an adopted repository (runtime version, build tool).

| Template | Stack | Notes |
| -------- | ----- | ----- |
| `eonax` | Eclipse Dataspace Components 0.18.1 (connectors, Identity Hub, Issuer Service) on JDK 21.0.12 (Corretto in the dev container, Temurin in the images), Gradle 9.8.0, Postgres 18.6, Vault 2.1.1 | A training data space shaped like EONA-X (mobility, transport, tourism), in 7 chapters (its project README): connectors and the Dataspace Protocol, DCP identity and membership credentials, ODRL usage policies (purpose, time window), a federated catalog, Gaia-X compliance (a GXDCH mock unless every `GAIAX_*` is set), and a consumer app (`just dev`). `just test` runs it all end to end. Kubernetes-ready by design (one image per component, configuration from the environment). First `dev up` builds every image (several minutes); needs about 6 GB of RAM. |
| `fastapi` | Python 3.14.8, FastAPI 0.142.2, uv 0.12.23, pytest, ruff | Local `uv` feature installs uv and bakes Python into the image (no compile, about 10 s). Ships a working app (`/`, `/health`) and tests; `postCreateCommand` runs `just install` (`uv sync`, creates `uv.lock`). Python 3.15 is not final yet (rc), so 3.14 is the latest stable. Ships a production `Dockerfile` stub. |
| `java`   | JDK 27.0.0 (Amazon Corretto), Gradle 9.8.0, JUnit 6 (Jupiter) | Official `java` feature (SDKMAN). Plain Java, no framework. On first `dev up`, `just init` runs `gradle init` (Java application, Kotlin DSL, wrapper pinned to 9.8.0, toolchain 27). Corretto because SDKMAN has no Temurin build of 27. Ships a production `Dockerfile` stub. |
| `node`   | Node 26.10.0, pnpm 12.9.1, Biome 2.5.15 | Official `node` feature. Ships a working app on Node built-ins (`node:http`, `/` and `/health`) and tests (`node:test`); Biome for lint and format. `postCreateCommand` runs `just install` (creates `pnpm-lock.yaml`). Ships a production `Dockerfile` stub. |
| `rails`  | Ruby 4.0.7, Rails 8.1.4, SQLite (PostgreSQL with `--with postgres`) | Local `ruby` feature (the official one does not support Ruby 4), so the first `dev up` compiles Ruby (a few minutes, then cached). On first `dev up`, `just init` runs `rails new` (app named after the project); Rails generates its own production `Dockerfile` and Kamal config. |

### `.github/workflows/images.yml`

On every pull request and push to `main`: `shellcheck` on the scripts, tests, adopt hooks and local feature installers; build and validate every image; run `tests/new-project` and `tests/dev`; then generate a project from every template and run `just --list` inside it.

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

`new-project` copies the template's `scaffold/` and `starter/`, fills in the placeholders, creates `.env` from `.env.example`, and runs `dev up`. The host port (`APP_PORT` in `.env`) is the first free one from 3000: a port something listens on, or that another project in `~/projects` claims in its `.env` (even stopped), is skipped, and `new-project` prints the one it picked. To choose it yourself (used as given):

```bash
APP_PORT=3100 new-project node my-poc
```

Options (any order, also combined with `--from`):

```bash
new-project rails shop --with postgres,redis    # add services
new-project fastapi api --ide=vscode            # VS Code extensions + open the project
```

## Services (`--with`)

`--with` adds services next to the dev container, from `services/`:

| Service    | Image                         | Variables in `.env`                                       |
| ---------- | ----------------------------- | --------------------------------------------------------- |
| `postgres` | Postgres 18.6 (user, password and database `dev`) | `DATABASE_URL`, `PGHOST`, `PGUSER`, `PGPASSWORD` |
| `redis`    | Redis 8.10.2                  | `REDIS_URL`                                               |

Each service becomes a `compose.<service>.yaml` in the project, included from `compose.yaml`, with a named volume (data survives `dev down`) and a healthcheck. Services are not published on the host: the app reaches them by name (`postgres:5432`, `redis:6379`). For a GUI client, run it in a container on the same network, or open a shell with `docker compose exec postgres psql -U dev`.

Rails projects get the `PG*` variables but no `DATABASE_URL`: Rails would apply it to every environment, so tests would run against the dev database. With `PGHOST`, Rails keeps separate `<app>_development` and `<app>_test` databases from `database.yml`, and `rails new` generates a PostgreSQL app.

## Adopting an Existing Repository

To work on an existing repository in this environment, clone it through `new-project` with `--from`:

```bash
new-project rails my-app --from git@github.com:org/my-app.git
APP_PORT=3100 new-project rails my-app --from ~/src/my-app    # a local path works too
```

It clones the repository into `~/projects/my-app`, then adds the template's dev environment around the code:

* **Only the dev environment is added.** The template's `scaffold/` is copied; its `starter/` (sample app, tests, production `Dockerfile` stub, README) is not.
* **Repository files always win.** Scaffold files are copied only where the repository has none (its `.gitignore`, `justfile`, `.devcontainer/` are kept).
* **Placeholders are filled only in the copied files**, never in the repository's code.
* **Runtime version follows the repository**: `.ruby-version` (rails), `.nvmrc` or `.node-version` (node), `.sdkmanrc` `java=` / `gradle=` (java). For fastapi, uv reads `.python-version` directly. Without a version file, the template's pin is kept.
* **Generators never run over existing code**: `rails new` is skipped when a `Gemfile` exists, `gradle init` when any Gradle or Maven build exists.
* **Build tool follows the repository**: for a `pom.xml` build, the `java` recipes switch to Maven (`./mvnw` if the repository has the wrapper, otherwise `mvn`, installed through the java feature). `just dev` runs `spring-boot:run` for Spring Boot; for other Maven apps it explains how to set the main class.
* **FastAPI app location is discovered**: `just dev` finds the app in `main.py`, `app.py`, `api.py` or `app/{main,app,api}.py`, and `just build` byte-compiles the whole project. A repository with only `requirements.txt` (no `pyproject.toml`) gets pip-style recipes: `uv venv` + `uv pip install -r requirements.txt` (and `requirements-dev.txt`), ruff run through `uvx`.
* **`.env`** is created from `.env.example` if missing, and `APP_PORT` is set: the repository's own `.env` value if it has one, else the first free port from its example's value (or 3000).
* The copied files are listed at the end. They stay **untracked**: commit them if the team adopts this setup, or list them in `.git/info/exclude` to keep them local.

Still manual: other app layouts (for example a FastAPI app outside the discovered paths, or a non-Spring Maven app: edit the `dev` recipe). A repository that ships its own `compose.yaml` keeps it, so `--with` cannot wire services into it.

If the repository has its own `.devcontainer/devcontainer.json`, it is kept and `new-project` prints a note: merge it with the template's by hand.

---

# Daily Workflow

From the project root, on the host:

```bash
dev up        # build the container (base image + features) and start it
dev shell     # bash inside the container
dev exec <cmd>
dev down      # stop and remove the project's containers (service data volumes are kept)
dev update    # bring template fixes into this project
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

Every template ships a `justfile` with at least `install`, `dev`, `test`, `lint`, `build`. What a recipe runs depends on the stack (the Node scaffold delegates to `package.json` scripts); the names do not. Anyone can enter any POC and know how to run it. `just --list` shows each recipe with its description.

Projects may add recipes. They should not rename or remove the five contract recipes.

## Ports

The shared `compose.yaml` (`templates/_shared/`) publishes the app's container port (3000, or the template's `port` file) on the host:

```yaml
ports:
  - "127.0.0.1:${APP_PORT:-3000}:{{PORT}}"
```

`.env` is loaded into the dev container (`env_file`), so every process sees its variables, not only `just` recipes. After editing `.env`, run `dev down && dev up`.

Open `http://localhost:<APP_PORT>` from Windows (3000 for the first project). Each project gets its own free `APP_PORT` at creation, so several POCs run at once; change it in the project's `.env`, then `dev down && dev up`.

If `dev up` fails (for example "port is already allocated"), it removes the half-created container and says so. Set another `APP_PORT` in `.env` and run `dev up` again.

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

## VS Code (`--ide=vscode`)

```bash
new-project fastapi api --ide=vscode
```

adds the template's VS Code extensions to `devcontainer.json` (`customizations.vscode.extensions`: Ruby LSP for rails; Python + Ruff for fastapi; Biome for node; Java pack + Gradle for java) and runs `code .` at the end, if the `code` CLI is on the `PATH` (VS Code's WSL integration). VS Code then offers **Reopen in Container**. Other editors ignore the `customizations` block.

One-time setup on Windows:

1. Install the **WSL** and **Dev Containers** extensions.
2. In the Dev Containers settings, enable **Execute In WSL**, so VS Code uses Docker Engine inside WSL (no Docker Desktop).

Every template's configuration, with and without `--ide=vscode`, resolves with the official Dev Containers CLI (`devcontainer read-configuration`), the engine VS Code uses. The GUI attach itself has not been tested yet: after **Reopen in Container**, expect the window in `/workspace` as `dev`, with a terminal where `just --list` works.

---

# When a POC Graduates

Each new project gets a production `Dockerfile` (plus `.dockerignore`), separate from the dev container: the `node`, `java` and `fastapi` starters ship a stub, `rails new` generates one for `rails`. Adopted repositories keep their own. Adjust it to the app and build on the host:

```bash
docker build -t my-poc .
```

---

# Team Conventions

1. **Keep the Ubuntu host clean.** Only git and Docker on the host. Everything else belongs in containers.
2. **Declare the stack in `devcontainer.json`.** No manual installs in running containers.
3. **Keep the `just` contract.** `install`, `dev`, `test`, `lint`, `build` in every project.
4. **Use versioned, pinned images.** Tags are versioned (never `latest`); base images are pinned by digest in the Dockerfiles. Renovate proposes the bumps; nothing updates silently.
5. **Change shared environments here.** Edit `images/`, `templates/` or `services/` in this repository, not individual projects' copies; projects pick changes up with `dev update`.
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

## Updating a project from its template

Template fixes (and a new base image tag) reach an existing project with:

```bash
dev update        # in the project root
dev down && dev up
```

`new-project` records what it installed in `.devcontainer/template.lock` (template, project name, services, IDE, a hash per dev-environment file). `dev update` re-renders the template's current `scaffold/` (and `templates/_shared/`) with the same options, then, file by file:

* **unchanged since install** → replaced by the new version
* **edited by you** → kept; the new version is written next to it as `<file>.template-new` and listed as a conflict (if the template did not change that file, nothing happens)
* **new in the template** → added
* **deleted by you** → stays deleted

The `adopt` hook runs again, so versions and build tools taken from the repository (`.ruby-version`, `pom.xml`, …) are kept. `starter/` files (the sample app) are yours after creation and never updated. Projects created before `template.lock` existed must be updated by hand.

## Version updates (Renovate)

Every version in this repository is pinned, and [Renovate](https://docs.renovatebot.com) keeps the pins current: once a week (Monday, before 6am) it opens a pull request per update, with release notes, labeled `dependencies`. Each PR runs CI, so an update that breaks a template fails before it is merged. Review and merge, or close to skip that version.

**Status:** `renovate.json` is ready and checked (official validator, plus a local dry run that detects every pin below). Renovate reads its config from the default branch, so it starts once `renovate.json` is on `main` **and** the **Renovate GitHub App** is installed on the repository (`github.com/apps/renovate` → Install → select this repository). With the config already on `main` there is no onboarding PR: the first run directly opens the pending updates. Installed earlier, it would open an onboarding PR with default settings, missing the custom managers.

What it tracks:

| Pins | Where |
| ---- | ----- |
| Base images and digests | `FROM` lines in `images/*/Dockerfile` and the starter `Dockerfile`s (including `COPY --from=` uv) |
| Service images and digests | `services/*/compose.yaml` |
| Dev Container Features and Node | `devcontainer.json` (`features/*:<major>`, node `version`) |
| pnpm, Gradle, JDK | `devcontainer.json` feature options |
| Ruby, ruby-build, Rails | rails `devcontainer.json` + feature default, `install.sh` (`RUBY_BUILD_VERSION`), `justfile` (`rails_version`) |
| uv, Python | fastapi `devcontainer.json` + feature defaults, `.python-version` |
| npm and Python packages | starter `package.json` (Biome, pnpm), `pyproject.toml` (FastAPI, pytest, ruff, httpx2), `requirements.justfile` (ruff) |
| Dev Containers CLI | `images/devcontainer-cli/Dockerfile` and its `image.env` tag |
| GitHub Actions | `.github/workflows/images.yml` |

Template pins outside built-in managers carry a `# renovate: datasource=… depName=…` comment (`//` in JSON files) read by one generic manager (see "Adding a New Template").

Pins that must stay equal go in one PR: all of a template's pins (runtime, starter images, tools) share one PR named `<template> template` (major updates in a separate `major-` one), so CI tests the template as a whole and a new template gets its group with no config change. Outside `templates/`, every file pinning the same dependency shares a PR (Renovate's default). Not tracked, on purpose: the `dev-base` image tag (bump it by hand when the base image changes), the Java major in the java `justfile` (`--java-version`), and apt packages (they follow the pinned base image).

To bump a pin by hand, edit the `FROM` line or version in the relevant file, and bump `IMAGE` in `image.env` when an image changes (CI fails otherwise: `scripts/check-image-tags origin/main` runs the same check locally); `CLAUDE.md` lists where each version lives.

---

# Adding a New Template

A template is a `templates/<name>/` directory with a `scaffold/` and, usually, a `starter/`. No script changes and no image needed. Example: `go`.

```text
templates/go/
├── scaffold/                 # dev environment, always copied (on top of templates/_shared/)
│   ├── .devcontainer/devcontainer.json
│   ├── compose.template.yaml # optional, the template's own services (included by compose.yaml)
│   ├── justfile
│   └── .gitignore
├── starter/                  # sample project, new projects only
│   ├── main.go, main_test.go, go.mod
│   ├── Dockerfile, .dockerignore
│   └── README.md
├── adopt                     # optional, for new-project --from and dev update
├── test                      # optional, scenarios for adopt (run by tests/new-project)
├── vscode-extensions         # for --ide=vscode
├── env                       # optional, the template's own variables (appended to .env.example)
├── env-skip                  # optional
└── port                      # optional, the app's container port (default 3000)
```

Start from `templates/node/` and change:

* `scaffold/.devcontainer/devcontainer.json`: the features (e.g. `ghcr.io/devcontainers/features/go:1`). If no published feature supports the version you need, write a local one in `.devcontainer/<name>/` (`devcontainer-feature.json` + `install.sh`; see `templates/rails` and `templates/fastapi`). For a stack whose app is generated by a tool (like `rails new` or `gradle init`), use `postCreateCommand` to run an idempotent `just init` recipe that never runs over existing code (see `templates/rails` and `templates/java`).
* `scaffold/justfile`: what the five contract recipes run. Keep them generic enough for adopted repositories.
* `compose.yaml` and `.env.example` come from `templates/_shared/`. The app listens on port 3000 in the container; if the stack needs another port, write it in an optional `port` file (one number, e.g. `8000`) and use `{{PORT}}` in the `dev` recipe. The host side stays `APP_PORT` (default 3000).
* `starter/`: a minimal working app with a test, the production `Dockerfile` stub, the README. Files that only fit the sample app belong here, not in `scaffold/`.
* optional `adopt` (executable): called as `adopt <project-dir>` by `new-project` (only when it copied `devcontainer.json`) and by `dev update`, to match the feature version to the repository's version file (see `templates/rails/adopt`) or its build tool (see `templates/java/adopt`). It must be idempotent.
* optional `scaffold/compose.template.yaml`: services the stack needs besides `dev` (e.g. the components of a multi-container system). `new-project` adds it to the project's `include:` (before `--with` services); placeholders work in it, and `dev update` tracks it. Keep `compose.yaml` itself shared.
* optional `test`: given/when/then scenarios for `adopt` (and `env-skip`), sourced by `tests/new-project`; see `templates/java/test`.
* `vscode-extensions`: one VS Code extension id per line, for `--ide=vscode`.
* optional `env`: the template's own variables (`NAME=value` lines and comments), appended to the project's `.env.example` after the shared ones, so they reach `.env` too; `dev update` tracks them.
* optional `env-skip`: service variables (from `services/*/env`) not to write into this template's projects (see `templates/rails/env-skip`).

Pin every version, and let Renovate find each pin no built-in manager knows (feature options, versions in `install.sh` or the `justfile`) with a comment on the line just above it; the version is the first number on the next line not glued to a letter (`"python3": "3.14"` reads `3.14`; `v1.2` is not read). `tests/renovate` checks every such comment in seconds. No change to `renovate.json`:

```just
# renovate: datasource=golang-version depName=go
go_version := "1.27.1"
```

In `devcontainer.json` and `devcontainer-feature.json`, use `// renovate: …`. Optional fields after `depName`, in this order: `versioning=<v>` and `extractVersion=<regex>` (see `templates/rails/scaffold/.devcontainer/ruby/install.sh`). Check with the local dry run in `CLAUDE.md`.

Placeholders replaced by `new-project` in the template files it copies (never in a repository's own files):

| Placeholder        | Value                                |
| ------------------ | ------------------------------------ |
| `{{PROJECT_NAME}}` | the project name                     |
| `{{IMAGE}}`        | `IMAGE` from `images/base/image.env` |
| `{{PORT}}`         | the template's `port` file, else 3000 |

Keep the contract: service `dev`, `/workspace`, `remoteUser: dev`, `env_file: .env` in `compose.yaml`, the five `just` recipes, a `vscode-extensions` file, and placeholders instead of hardcoded image tags. `tests/new-project` checks this contract for every template it finds (no test to add): run it before opening a pull request. CI also generates a project from the new template and runs `just --list` in it.

# Adding a Service

A service is a `services/<name>/` directory, available to `new-project --with <name>` with no script changes:

```text
services/mysql/
├── compose.yaml   # one service named like the directory, image pinned by tag + digest,
│                  # named volume, healthcheck, no published ports
└── env            # KEY=value lines added to the project's .env and .env.example
```

Start from `services/postgres/`. Use the service name as the host in the variables (`mysql:3306`), since the app reaches it over the compose network. If a variable misleads a framework, list it in that template's `env-skip` (see `templates/rails/env-skip`). Add a `tests/new-project` scenario for the generated files and, for a cheap image, a reachability check in `tests/dev`.

---

# Design Principle

> **Keep the host stable. Make development environments disposable. Standardize the interface, not the stack.**
