# MCPX Registry Jenkins Plugin

A Jenkins plugin that adds a build parameter to list MCP servers from an MCPX Registry and select one for your job.

## Table of Contents

- [Features](#features)
- [Quick Start](#quick-start)
- [MCPX CLI Integration](#mcpx-cli-integration)
  - [Login behavior](#login-behavior)
  - [Job-level overrides](#job-level-overrides)
  - [Why CLI instead of HTTP?](#why-cli-instead-of-http)
- [Jenkinsfile example](#jenkinsfile-example)
- [Development](#development)
- [License](#license)

## Features

- Global configuration for registry base URL
- Parameterized job input to select an MCP server from the registry
- Exposes the selected value as environment variables: `$MCPX_SERVER_NAME` and `$<PARAM_NAME>`
- mcpx-cli integration: configure CLI path
- Job-level overrides: per-job CLI settings (path, registry URL)

## Quick Start

1) Build the plugin

```bash
mvn -U -e -ntp -DskipTests package
```

The resulting `.hpi` will be under `target/`.

2) Install in Jenkins
- Manage Jenkins → Plugins → Advanced → Upload Plugin → select the built `.hpi`.

3) Configure the registry
- Manage Jenkins → System → MCPX Registry: set the Registry Base URL

4) Configure mcpx-cli
- Manage Jenkins → System → MCPX CLI:
  - CLI Path: path to mcpx-cli (default: `~/.local/bin/mcpx-cli`)

5) Add a parameter to a job
- Configure job → This build is parameterized → Add parameter → “MCP Servers from MCPX Registry”
- In the parameter configuration, use the “MCP Servers” dropdown to choose a server (list is fetched via mcpx-cli)
- Click “Refresh” to fetch the latest servers from the registry; if the list doesn’t update immediately, save and reopen or reload the page
- The dropdown displays the short server name (final path segment), but the stored value is the full name (e.g., shows `gerrit-mcp-server`, stores `io.modelcontextprotocol.anonymous/gerrit-mcp-server`)

6) Use it in a build step

## MCPX CLI Integration

The plugin uses mcpx-cli to fetch server lists.

### Login behavior

Before listing servers, the plugin initializes the CLI session with an anonymous login, then lists servers:

```bash
mcpx-cli --base-url=<your-registry> login --method anonymous
mcpx-cli --base-url=<your-registry> servers --json
```

### Job-level overrides

Jobs can override global CLI settings:
1. Configure job → check "MCPX Registry Plugin Configuration"
2. Set any of:
  - CLI Path (e.g., a different version)
  - Registry Base URL (to use a different registry for this job)
  - CLI Download URL (optional, required if using Update CLI)
  - Use the Test CLI button to verify the CLI works at the configured path
    - You can choose a Node (agent) to run the test on; this is useful when mcpx-cli is installed on an agent rather than the controller. If no node is chosen, the test runs on the controller.
  - Use the Update CLI button to download/install the CLI to the configured path (requires a download URL)

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
