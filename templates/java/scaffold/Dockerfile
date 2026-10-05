# Production image stub for when the POC graduates. Adjust to the app.
# Build on the host: docker build -t {{PROJECT_NAME}} .
FROM amazoncorretto:27.0.0@sha256:bda485d0d89dcbc58f60681ed07121e941e512f8713713776a1afeb2aa6159ce AS build
# gradlew needs xargs (findutils), missing from the Amazon Linux base images.
RUN dnf install -y findutils && dnf clean all
WORKDIR /src
COPY . .
RUN ./gradlew installDist --no-daemon

FROM amazoncorretto:27.0.0-al2023-headless@sha256:68d99e3d3ee8a0c4acd519dfdfd4d3e5aa16d1f679d88024e09a6b9a74635c3e
# The generated bin/app start script also needs xargs.
RUN dnf install -y findutils && dnf clean all
WORKDIR /app
COPY --from=build /src/app/build/install/app ./
USER 1000
CMD ["bin/app"]
