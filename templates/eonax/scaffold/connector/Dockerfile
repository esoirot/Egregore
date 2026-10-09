# One image per data space component, built from this project: MODULE=controlplane | dataplane.
# The same image runs anywhere (compose here, Kubernetes later): configuration comes from the environment.
FROM gradle:9.8.0-jdk21@sha256:4debe478645a3ad9208f3403ac021859fdf4d1a819d59970d75309b098f28f76 AS build
ARG MODULE
WORKDIR /src
COPY . .
# One build at a time in the shared Gradle cache (sharing=locked): compose builds the runtimes in
# parallel, and Gradle cannot hand its cache lock to a Gradle in another container (it times out
# after 60 s, e.g. during a cold download on CI).
RUN --mount=type=cache,target=/home/gradle/.gradle/caches,sharing=locked \
    gradle --no-daemon --quiet ":${MODULE}:shadowJar"

FROM eclipse-temurin:21.0.12.1_1-jre@sha256:cff19e6215689161eb6162c11b86b0c60ddf802164f2eaf48d570f8fb79a36c5
ARG MODULE
COPY --from=build /src/${MODULE}/build/libs/runtime.jar /app/runtime.jar
# Non-root, numeric uid: satisfies Kubernetes runAsNonRoot.
USER 10001
ENTRYPOINT ["java", "-jar", "/app/runtime.jar"]
