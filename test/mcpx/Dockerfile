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
RUN uv venv /opt/venv
ENV VIRTUAL_ENV=/opt/venv
ENV PATH="/opt/venv/bin:$PATH"

# Install Python build tooling and project dependencies (use uv within venv)
RUN uv pip install -r uv-requirements.txt
RUN uv pip compile pyproject.toml --generate-hashes --output-file requirements.txt --extra-index-url https://pypi.org/simple
RUN uv pip sync requirements.txt
RUN uv pip install -e .
RUN uv pip install build setuptools wheel

# Expose port
EXPOSE 6322

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
    CMD curl -f http://localhost:6322/health || exit 1

# Default command
CMD ["uvicorn", "gerrit_mcp_server.main:app", "--host", "0.0.0.0", "--port", "6322"]
