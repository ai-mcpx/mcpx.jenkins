# MCPX Registry Jenkins Plugin

A Jenkins plugin that adds a build parameter to list MCP servers from an MCPX Registry and select one for your job.

## Table of Contents

- [Features](#features)
- [Quick Start](#quick-start)
- [MCPX CLI Integration](#mcpx-cli-integration)
  - [Login behavior](#login-behavior)
  - [Installation and updates](#installation-and-updates)
  - [Job-level overrides](#job-level-overrides)
  - [Testing the CLI](#testing-the-cli)
  - [Why CLI instead of HTTP?](#why-cli-instead-of-http)
- [Jenkinsfile example](#jenkinsfile-example)
- [Development](#development)
- [License](#license)

## Features

- Global configuration for registry base URL (required, no default)
- Parameterized job input to select an MCP server from the registry
- Exposes the selected value as environment variables: `$MCPX_SERVER_NAME` and `$<PARAM_NAME>`
- mcpx-cli integration: configure CLI path, download/update CLI, and test CLI
- Job-level overrides: per-job CLI settings (path, registry URL, credentials)
- Auth for CLI download: optional username/password for protected download URLs (Basic Auth)

## Quick Start

1) Build the plugin

```bash
mvn -U -e -ntp -DskipTests package
```

The resulting `.hpi` will be under `target/`.

2) Install in Jenkins
- Manage Jenkins → Plugins → Advanced → Upload Plugin → select the built `.hpi`.

3) Configure the registry
- Manage Jenkins → System → MCPX Registry: set the Registry Base URL (required, no default).

4) Configure mcpx-cli (required)
- Manage Jenkins → System → MCPX CLI:
  - CLI Path: path to mcpx-cli (default: `~/.local/bin/mcpx-cli`)
  - CLI Download URL: (optional, leave empty unless you want to override download location)
  - Test CLI: verify the CLI is working
  - Update CLI: download/update from the given URL
  - Auto-update CLI: keep CLI up to date automatically
  - Advanced: optional username/password for downloading the CLI (HTTP Basic Auth)

5) Add a parameter to a job
- Configure job → This build is parameterized → Add parameter → “MCP Servers from MCPX Registry”
- Choose a name (e.g., `MCP_SERVER`) and optionally set a default. The dropdown shows only the final segment of each server name (e.g., `gerrit-mcp-server` for `io.modelcontextprotocol.anonymous/gerrit-mcp-server`), while the stored value is the full name.

6) Use it in a build step

## MCPX CLI Integration

The plugin uses mcpx-cli to fetch server lists.

### Login behavior

Before listing servers, the plugin initializes the CLI session with an anonymous login, then lists servers:

```bash
mcpx-cli --base-url=<your-registry> login --method anonymous
mcpx-cli --base-url=<your-registry> servers --json
```

### Installation and updates

System-level (Global):
1. Manage Jenkins → System → MCPX CLI
2. Set CLI Path (e.g., `~/.local/bin/mcpx-cli` or another location)
3. (Optional) Set Download URL for your platform if you want to override the default download location
4. Click Test CLI to verify installation
5. Click Update CLI to download/install the CLI

Auto-update: enable “Auto-update CLI” to download the latest CLI before each use.

### Job-level overrides

Jobs can override global CLI settings:
1. Configure job → check “Override MCPX CLI Configuration”
2. Set any of:
  - CLI Path (e.g., a different version)
  - Registry Base URL (required)
  - CLI Download URL (optional, leave empty unless needed)
  - Username/Password for CLI download if needed (not for login)

### Testing the CLI

Use the Test CLI button to verify:
- CLI executable is accessible
- CLI runs successfully
- CLI version information

Example output:

```
mcpx-cli is working! Version: mcpx-cli version 0.1.0
```

### Why CLI instead of HTTP?

- Avoids CORS: no browser restrictions
- Better auth handling: CLI manages tokens/config
- Consistent tooling: same as developer workflows
- Reliable behind proxies/firewalls

HTTP is intentionally not used to avoid CORS and environment-specific constraints. Install the CLI on Jenkins controller/agents.

## Jenkinsfile example

```groovy
properties([
  parameters([
    [$class: 'io.modelcontextprotocol.jenkins.parameters.McpxServerParameterDefinition', name: 'MCP_SERVER', description: 'Select an MCP server', defaultServer: '']
  ])
])

pipeline {
  agent any
  stages {
    stage('Show selection') {
      steps {
        echo "Selected: ${env.MCP_SERVER}"
      }
    }
  }
}
```

## Development

- Java 11+
- Jenkins 2.414.3+ baseline

Run tests:

```bash
mvn -ntp -Dspotbugs.skip package
```

## License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.
