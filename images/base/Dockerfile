# Shared base for every project dev container. Stacks are added per project
# through Dev Container Features in .devcontainer/devcontainer.json.
FROM ubuntu:26.04@sha256:f144425ff09be612d6d9ad965196e9cdc23dae1f42110a8a11a3e9a8198759f7

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ca-certificates \
        curl \
        git \
        build-essential \
        sudo \
        just \
    && rm -rf /var/lib/apt/lists/*

# Free uid 1000 (taken by the image's default "ubuntu" user) so dev matches the
# usual WSL host uid and bind-mounted files keep the right owner.
RUN userdel --remove ubuntu \
    && useradd --create-home --shell /bin/bash --uid 1000 dev \
    && echo "dev ALL=(ALL) NOPASSWD:ALL" > /etc/sudoers.d/dev \
    && chmod 0440 /etc/sudoers.d/dev

USER dev
WORKDIR /workspace

CMD ["bash"]
