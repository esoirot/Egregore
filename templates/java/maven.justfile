# Team command contract: every project exposes install, dev, test, lint, build.
# Run inside the container (dev shell). Maven build, written by templates/java/adopt.

set dotenv-load

# List recipes
default:
    @just --list

# Nothing to generate: the Maven build already exists (run by dev up)
init:
    @true

# Download dependencies
install:
    @MVN@ dependency:resolve

# Run the app
dev:
    @DEV@

# Run tests
test *args:
    @MVN@ test {{args}}

# Lint the code (compile, tests and checks)
lint:
    @MVN@ verify

# Build the app
build:
    @MVN@ package
