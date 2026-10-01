FROM ubuntu:26.04

ARG NODE_VERSION=26

ENV NVM_DIR=/home/dev/.nvm
ENV PATH="${NVM_DIR}/versions/node/v26.10.0/bin:${PATH}"

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ca-certificates \
        curl \
        git \
        build-essential \
        sudo \
    && rm -rf /var/lib/apt/lists/*

# Create a non-root development user
RUN useradd --create-home --shell /bin/bash dev \
    && echo "dev ALL=(ALL) NOPASSWD:ALL" > /etc/sudoers.d/dev \
    && chmod 0440 /etc/sudoers.d/dev

USER dev
WORKDIR /workspace

# Install nvm for the dev user
RUN curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.40.3/install.sh | bash

# Install Node through nvm
RUN . "$NVM_DIR/nvm.sh" \
    && nvm install "$NODE_VERSION" \
    && nvm alias default "$NODE_VERSION" \
    && nvm use default

# Install pnpm
RUN . "$NVM_DIR/nvm.sh" \
    && nvm use default \
    && npm install --global pnpm

# Load nvm automatically for interactive shells
RUN echo 'export NVM_DIR="$HOME/.nvm"' >> "$HOME/.bashrc" \
    && echo '[ -s "$NVM_DIR/nvm.sh" ] && . "$NVM_DIR/nvm.sh"' >> "$HOME/.bashrc" \
    && echo '[ -s "$NVM_DIR/bash_completion" ] && . "$NVM_DIR/bash_completion"' >> "$HOME/.bashrc"

CMD ["bash"]
