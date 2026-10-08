# The deploy profile's setup job and app: the course scripts (dataspace/) and the app, with what they
# run on (bash, curl, jq; a JDK for app/Server.java, launched from source). Context: the project root.
FROM eclipse-temurin:21.0.12.1_1-jdk@sha256:3e3c176ffed168beb42c607be9bc1639b466cf00261a0fb04425562c9d0c5c2b
RUN apt-get update && apt-get install -y --no-install-recommends curl jq && rm -rf /var/lib/apt/lists/*
WORKDIR /srv
COPY dataspace/ dataspace/
COPY app/ app/
COPY deploy/setup deploy/setup
# State of the scripts (ids of agreements, transfers): rebuilt when lost (the app negotiates again).
RUN mkdir .dataspace downloads && chown 10001 .dataspace downloads
USER 10001
