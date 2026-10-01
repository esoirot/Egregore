# Development Environment

Standardized, containerized development environments for the development team.

The goal of this repository is to provide a **consistent, reproducible, and disposable development environment** across developer machines, while keeping the host system clean.

## Architecture

```text
Windows 11
└── WSL2
    └── Ubuntu
        └── Docker Engine
            └── Project containers
                ├── Runtime
                ├── Dependencies
                ├── CLI tools
                └── Build tools
```

Source code remains on the developer's Linux filesystem and is mounted into the project container.

```text
~/projects/my-project/
    ├── source code
    ├── compose.yaml
    └── ...

        ↓ bind mount

/container/workspace
```

The Ubuntu host is intended to provide the infrastructure, not project-specific runtimes.

---

## Repository Structure

```text
dev-environment/
├── templates/
│   └── node/
│       ├── Dockerfile
│       ├── template.env
│       ├── validate
│       └── project/
│           ├── compose.yaml
│           ├── .gitignore
│           └── README.md
│
├── scripts/
│   ├── install
│   └── new-project
│
├── docs/
│
├── .gitignore
└── README.md
```

### `templates/`

One directory per supported development environment. Each template contains the container image definition and the files copied into new projects. See [Adding a New Template](#adding-a-new-template).

### `scripts/`

Automation used to install the development environment and create new projects.

### `docs/`

Additional development-environment documentation and conventions.

---

# Requirements

The standard environment currently assumes:

* Windows 11 Pro
* WSL2
* Ubuntu
* Docker Engine running inside Ubuntu
* Docker Compose
* Git

Docker Desktop is **not required**.

The intended architecture is:

```text
Windows → WSL2 → Ubuntu → Docker Engine
```

---

# Initial Setup

Clone this repository into your WSL home directory:

```bash
git clone <company-repository-url> ~/dev-environment
```

Enter the repository:

```bash
cd ~/dev-environment
```

Run the installer:

```bash
./scripts/install
```

The installer:

* verifies the required host dependencies
* configures the development scripts in `PATH`
* validates the project generator
* builds the development image of every template in `templates/`
* validates that each image runs as the non-root `dev` user
* runs each template's own checks (for Node: Node, pnpm and nvm)

The installer is designed to be safe to run more than once.

---

# Creating a Project

Once the environment is installed, create a new project with:

```bash
new-project <template> <project-name>
```

For example, a Node project:

```bash
new-project node my-project
```

Run `new-project` without arguments to list the available templates.

Projects are created in `~/projects/` by default. Set `PROJECTS_DIR` to use another location:

```bash
PROJECTS_DIR=~/work new-project node my-project
```

This creates:

```text
~/projects/my-project/
├── compose.yaml
├── .gitignore
└── README.md
```

Enter the project:

```bash
cd ~/projects/my-project
```

Start the development container:

```bash
docker compose up -d
```

Open a shell inside the container:

```bash
docker compose exec node bash
```

You are now working inside the standardized Node development environment.

---

# Node Development Environment

The current Node environment is based on:

```text
Ubuntu 26.04
Node 26
nvm
pnpm
Git
build-essential
```

The container runs as the non-root user:

```text
dev
```

## Node Version Management

`nvm` is the standard Node version manager inside the development container.

Node installations should be managed through `nvm`.

For example:

```bash
nvm --version
```

```bash
node --version
```

```bash
nvm ls
```

The development image currently uses Node 26.

The development image is versioned as:

```text
node-env:26
```

Avoid using an unversioned `latest` tag for the team environment. Versioned tags make changes to the development environment explicit and reproducible.

---

# pnpm

The standard package manager for Node projects is:

```text
pnpm
```

Check the installed version:

```bash
pnpm --version
```

Project dependencies should be installed inside the development container.

For example:

```bash
pnpm install
```

---

# Project Isolation

Each project gets its own container environment.

For example:

```text
~/projects/project-a
    └── container A

~/projects/project-b
    └── container B
```

This prevents dependencies and system tools from one project from interfering with another.

A project can therefore use its own:

* Node version
* npm/pnpm dependencies
* system libraries
* CLI tools
* build tools
* environment configuration

without modifying the Ubuntu host.

---

# Source Code and Containers

The source code lives on the WSL Linux filesystem:

```text
~/projects/
```

It is mounted into the container at:

```text
/workspace
```

The container is therefore disposable.

The important distinction is:

```text
Host
└── Source code
        ↓
Container
└── Runtime + dependencies + tools
```

Deleting and recreating a container should not delete the project source code.

---

# Container Lifecycle

Start a project:

```bash
docker compose up -d
```

Enter the container:

```bash
docker compose exec node bash
```

Stop the project:

```bash
docker compose down
```

Recreate the environment:

```bash
docker compose down
docker compose up -d
```

The project environment should be considered disposable.

If something becomes inconsistent inside the container, prefer rebuilding or recreating the environment rather than manually repairing it.

---

# Development Workflow

A typical workflow is:

```bash
cd ~/projects/my-project

docker compose up -d

docker compose exec node bash
```

Inside the container:

```bash
pnpm install
pnpm run dev
```

When finished:

```bash
exit
docker compose down
```

The exact project commands depend on the application.

---

# IDEs

The development environment is **IDE-independent**.

The team standard is the container/runtime environment, not a specific editor.

Developers may use:

* VS Code
* JetBrains IDEs
* Neovim
* Vim
* Emacs
* another editor or IDE

VS Code Dev Containers can be used as a personal workflow, but VS Code configuration is not required in application repositories.

The project generator therefore does not create `.devcontainer` configuration by default.

---

# Team Conventions

The following principles should be followed.

### 1. Keep the Ubuntu host clean

Do not install project-specific Node, Python, databases, or other development dependencies directly into Ubuntu unless they are part of the infrastructure itself.

### 2. Use containers for project environments

Project runtimes and dependencies belong in the project's container environment.

### 3. Keep source code on the WSL filesystem

Prefer:

```text
~/projects/my-project
```

over:

```text
/mnt/c/...
```

for Linux-based development workflows.

### 4. Use versioned development images

Use:

```text
node-env:26
```

rather than:

```text
node-env:latest
```

This makes environment changes deliberate and easier to reproduce.

### 5. Don't manually customize shared images

Changes to the standard development environment should be made in this repository.

For example:

```text
templates/node/Dockerfile
```

Then the image can be rebuilt and validated for the team.

### 6. Keep project configuration in the project repository

Application-specific dependencies and configuration belong in the application repository.

The `dev-environment` repository contains the shared development infrastructure.

---

# Updating the Development Environment

When the shared environment changes:

```bash
cd ~/dev-environment
git pull
```

Then rebuild the image:

```bash
./scripts/install
```

The installer will rebuild and validate the standard development image.

Existing project containers can then be recreated when appropriate:

```bash
cd ~/projects/my-project

docker compose down
docker compose up -d
```

---

# Repository Responsibilities

## `dev-environment`

Contains:

* development container definitions
* development image configuration
* project generators
* installation scripts
* team development conventions
* shared development tooling

## Application Repositories

Contain:

* application source code
* application dependencies
* application configuration
* application tests
* application-specific Docker/Compose configuration

This separation keeps the shared development infrastructure independent from individual applications.

---

# Adding a New Template

Every directory in `templates/` that contains a `template.env` file is a template. `scripts/install` builds all of them, and `new-project` can generate projects from any of them. No new script is needed.

The example below adds a `python` template.

## 1. Create the template directory

```text
templates/python/
├── Dockerfile        # development image
├── template.env      # image tag and compose service name
├── validate          # optional: template-specific checks
└── project/          # files copied into every new project
    ├── compose.yaml
    ├── .gitignore
    └── README.md
```

## 2. Write the `Dockerfile`

Follow the same rules as the Node image:

* create a non-root user named `dev` and switch to it (`install` fails otherwise)
* use `/workspace` as the working directory
* pin versions explicitly

## 3. Write `template.env`

```bash
# Image tag built by scripts/install and used by generated projects.
IMAGE=python-env:3.14

# Compose service name in generated projects (docker compose exec <SERVICE> bash).
SERVICE=python
```

Both values are required. Always use a versioned tag, never `latest`.

## 4. Add the project files

Everything in `project/`, including dotfiles, is copied into the new project. The following placeholders are replaced in every file:

| Placeholder        | Value                         |
| ------------------ | ----------------------------- |
| `{{PROJECT_NAME}}` | the project name              |
| `{{IMAGE}}`        | `IMAGE` from `template.env`   |
| `{{SERVICE}}`      | `SERVICE` from `template.env` |

A minimal `project/compose.yaml`:

```yaml
services:
  {{SERVICE}}:
    image: {{IMAGE}}
    working_dir: /workspace
    volumes:
      - .:/workspace
    command: sleep infinity
    init: true
```

`command: sleep infinity` keeps the container running so developers can enter it with `docker compose exec`.

## 5. Add checks (optional)

If `validate` exists and is executable, `install` runs it with the image tag as its first argument after the build. A non-zero exit fails the installation.

```bash
#!/usr/bin/env bash
set -euo pipefail

IMAGE="$1"

echo "✓ Python: $(docker run --rm "$IMAGE" python3 --version)"
```

```bash
chmod +x templates/python/validate
```

## 6. Build and use the template

Build and validate the image:

```bash
./scripts/install
```

Create a project from the new template:

```bash
new-project python my-api
```

Then work in it like any other project:

```bash
cd ~/projects/my-api
docker compose exec python bash
```

Each template should follow the same principles:

* reproducible
* versioned
* containerized
* non-root development user
* disposable
* documented
* IDE-independent

---

# Design Principle

The main principle behind this repository is:

> **Keep the host stable. Make development environments disposable.**

The developer's machine provides the infrastructure.

The container provides the project environment.

The source code remains persistent on the host.

This gives developers a consistent environment without turning the Ubuntu installation into a collection of project-specific dependencies.
