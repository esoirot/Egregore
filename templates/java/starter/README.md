# {{PROJECT_NAME}}

Plain Java POC (JDK 27, Gradle). The development environment runs in a container; the host only needs Docker.

## Develop

```bash
dev up        # build and start the dev container; the first run creates the Gradle project
dev shell     # open a shell inside it
```

Inside the container:

```bash
just              # list commands
just install      # download dependencies
just dev          # run the app (./gradlew run)
just test
just lint         # ./gradlew check
just build
```

The code lives in `app/src/main/java/` (package `org.example`, rename as needed). If the app serves HTTP, listen on port 3000: it is published on the host at `APP_PORT` (default 3000).

If `dev up` fails because the port is in use, set another `APP_PORT` in `.env`, then `dev down && dev up`.

Stop the container from the host:

```bash
dev down
```

## Stack

The JDK (Amazon Corretto 27) and Gradle are declared in `.devcontainer/devcontainer.json` as a Dev Container Feature. After the first run, the Gradle wrapper (`gradle/wrapper/gradle-wrapper.properties`) pins the Gradle version and `app/build.gradle.kts` pins the Java toolchain.

## IDE

Any editor works on the files. Dev Containers–aware tools (VS Code, JetBrains, Codespaces) can attach using `.devcontainer/devcontainer.json`.

## Production image

`Dockerfile` is a stub for when the POC graduates:

```bash
docker build -t {{PROJECT_NAME}} .
```
