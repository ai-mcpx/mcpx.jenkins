FROM ubuntu:24.04

# Set shell
SHELL ["/bin/bash", "-c"]

# Environment
ENV DEBIAN_FRONTEND=noninteractive
ENV PYTHONUNBUFFERED=1
ENV PYTHONDONTWRITEBYTECODE=1

# Install system dependencies
RUN apt-get update && apt-get install -y \
    python3.12 \
    python3.12-venv \
    python3-pip \
    curl \
    git \
    ca-certificates \
    build-essential && \
    rm -rf /var/lib/apt/lists/*

# Python symlink
RUN ln -sf /usr/bin/python3.12 /usr/bin/python3

# Install uv (official installer)
RUN curl -LsSf https://astral.sh/uv/install.sh | sh
ENV PATH="/root/.local/bin:$PATH"

# Clone source instead of using COPY
WORKDIR /app/gerrit-mcp-server
ARG GERRIT_MCP_SERVER_REPO=https://github.com/ai-mcpx/gerrit-mcp-server
ARG GERRIT_MCP_SERVER_REF=master
RUN git clone --depth=1 --branch ${GERRIT_MCP_SERVER_REF} ${GERRIT_MCP_SERVER_REPO} .

# Prepare virtual environment (uv-managed) so uv pip uses it
RUN /root/.local/bin/uv venv /opt/venv
ENV VIRTUAL_ENV=/opt/venv
ENV PATH="/opt/venv/bin:$PATH"

# Install Python build tooling and project dependencies (use uv within venv)
RUN /root/.local/bin/uv pip install -r uv-requirements.txt
RUN /root/.local/bin/uv pip compile pyproject.toml --generate-hashes --output-file requirements.txt --extra-index-url https://pypi.org/simple
RUN /root/.local/bin/uv pip sync requirements.txt
RUN /root/.local/bin/uv pip install -e .
RUN /root/.local/bin/uv pip install build setuptools wheel

# Default command (STDIO mode)
CMD ["python", "gerrit_mcp_server/main.py", "stdio"]
