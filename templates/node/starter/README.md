# {{PROJECT_NAME}}

Node.js POC (Node 26, pnpm, no framework). The development environment runs in a container; the host only needs Docker.

## Develop

```bash
dev up        # build and start the dev container; installs dependencies
dev shell     # open a shell inside it
```

Inside the container:

```bash
just              # list commands
just install      # pnpm install
just dev          # app with reload (node --watch) on http://localhost:3000 (APP_PORT)
just test         # node --test
just lint         # Biome lint + format check
just build        # syntax check (plain Node, no bundler)
```

Code lives in `src/` (`app.js` builds the server, `main.js` starts it), tests in `test/`. Recipes delegate to the `package.json` scripts. Add a dependency with `pnpm add <package>` (dev-only: `pnpm add --save-dev <package>`); commit `pnpm-lock.yaml`.

If `dev up` fails because the port is in use, set another `APP_PORT` in `.env`, then `dev down && dev up`.

Stop the container from the host:

```bash
dev down
```

## Stack

Node and pnpm are declared in `.devcontainer/devcontainer.json` as a Dev Container Feature; `packageManager` in `package.json` pins pnpm for the project. Add a feature there (for example Python) and rerun `dev up`.

## IDE

Any editor works on the files. Dev Containers–aware tools (VS Code, JetBrains, Codespaces) can attach using `.devcontainer/devcontainer.json`.

## Production image

`Dockerfile` is a stub for when the POC graduates (production dependencies only, runs as the `node` user):

```bash
docker build -t {{PROJECT_NAME}} .
```
