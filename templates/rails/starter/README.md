# {{PROJECT_NAME}}

Ruby on Rails POC. The development environment runs in a container; the host only needs Docker.

## Develop

```bash
dev up        # build and start the dev container; the first run creates the Rails app
dev shell     # open a shell inside it
```

Inside the container:

```bash
just              # list commands
just install
just dev          # app on http://localhost:3000 (APP_PORT)
just test
just lint
just build
```

If `dev up` fails because the port is in use, set another `APP_PORT` in `.env`, then `dev down && dev up`.

Stop the container from the host:

```bash
dev down
```

## Stack

Ruby is declared in `.devcontainer/devcontainer.json` as a Dev Container Feature. The Rails version is pinned in the `justfile` (`rails_version`); after the first run, the `Gemfile` is the source of truth. Add a feature (for example Node or PostgreSQL client) there and rerun `dev up`.

## IDE

Any editor works on the files. Dev Containers–aware tools (VS Code, JetBrains, Codespaces) can attach using `.devcontainer/devcontainer.json`.

## Production image

`rails new` generates a production `Dockerfile` (and Kamal config) for when the POC graduates:

```bash
docker build -t {{PROJECT_NAME}} .
```
