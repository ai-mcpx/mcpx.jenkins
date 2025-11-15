package io.modelcontextprotocol.jenkins;

import hudson.EnvVars;
import hudson.Util;
import hudson.model.EnvironmentContributingAction;
import hudson.model.InvisibleAction;
import hudson.model.Job;
import hudson.model.Run;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class McpxSelectedServerEnvAction extends InvisibleAction implements EnvironmentContributingAction {
    private static final Logger LOGGER = Logger.getLogger(McpxSelectedServerEnvAction.class.getName());
    private final String selectedServer;

    public McpxSelectedServerEnvAction(String selectedServer) {
        this.selectedServer = selectedServer;
    }

    @Override
    public void buildEnvironment(@Nonnull Run<?, ?> run, @Nonnull EnvVars env) {
        Job<?, ?> job = run.getParent();

        // Set MCPX_CLI_PATH environment variable from configuration
        // Priority: user parameter > job-level > global > default
        if (!env.containsKey("MCPX_CLI_PATH") || env.get("MCPX_CLI_PATH").trim().isEmpty()) {
            String cliPath = getCliPath(job);
            if (cliPath != null && !cliPath.trim().isEmpty()) {
                env.put("MCPX_CLI_PATH", cliPath);
                LOGGER.log(Level.FINE, "Set MCPX_CLI_PATH environment variable: " + cliPath);
            }
        }

        // Set MCPX_REGISTRY_BASE_URL environment variable from configuration
        // Priority: user parameter > job-level > global > default
        if (!env.containsKey("MCPX_REGISTRY_BASE_URL") || env.get("MCPX_REGISTRY_BASE_URL").trim().isEmpty()) {
            String baseUrl = getRegistryBaseUrl(job);
            if (baseUrl != null && !baseUrl.trim().isEmpty()) {
                env.put("MCPX_REGISTRY_BASE_URL", baseUrl);
                LOGGER.log(Level.FINE, "Set MCPX_REGISTRY_BASE_URL environment variable: " + baseUrl);
            }
        }

        // Set MCP_SERVER environment variable
        if (selectedServer != null && !selectedServer.trim().isEmpty()) {
            // Only set if not already set by user parameters
            if (!env.containsKey("MCP_SERVER") || env.get("MCP_SERVER").trim().isEmpty()) {
                env.put("MCP_SERVER", selectedServer);
            }
        }

        // Inject package parameters as environment variables with defaults
        if (selectedServer != null && !selectedServer.trim().isEmpty()) {
            try {
                if (job != null) {
                    Map<String, String> defaults = McpxPackageParameterExtractor.getDefaultValues(job, selectedServer);
                    for (Map.Entry<String, String> entry : defaults.entrySet()) {
                        String paramName = entry.getKey();
                        String defaultValue = entry.getValue();

                        // Only set default if not already set by user parameters
                        // User-provided parameters take precedence
                        if (!env.containsKey(paramName) || env.get(paramName).trim().isEmpty()) {
                            env.put(paramName, defaultValue);
                            LOGGER.log(Level.FINE, "Set default environment variable " + paramName + " = " + defaultValue + " from packages");
                        } else {
                            LOGGER.log(Level.FINE, "Skipping default for " + paramName + " (already set to: " + env.get(paramName) + ")");
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to inject package parameters for server: " + selectedServer, e);
            }
        }
    }

    /**
     * Get the CLI path with priority: job-level > global > default
     */
    private String getCliPath(Job<?, ?> job) {
        McpxJobProperty jp = job != null ? job.getProperty(McpxJobProperty.class) : null;

        // Check job-level configuration first
        if (jp != null && Util.fixEmptyAndTrim(jp.getCliPath()) != null) {
            return Util.fixEmptyAndTrim(jp.getCliPath());
        }

        // Try to get global configuration (may fail in unit tests if Jenkins is not available)
        try {
            McpxGlobalConfiguration cfg = McpxGlobalConfiguration.get();
            if (cfg != null && Util.fixEmptyAndTrim(cfg.getCliPath()) != null) {
                return Util.fixEmptyAndTrim(cfg.getCliPath());
            }
        } catch (IllegalStateException e) {
            // Jenkins instance is not available (e.g., in unit tests)
            // Fall through to default
            LOGGER.log(Level.FINE, "Jenkins instance not available, using default CLI path", e);
        } catch (Exception e) {
            // Any other exception, log and fall through to default
            LOGGER.log(Level.FINE, "Failed to get global configuration, using default CLI path", e);
        }

        // Default fallback
        return "mcpx-cli";
    }

    /**
     * Get the registry base URL with priority: job-level > global > default
     */
    private String getRegistryBaseUrl(Job<?, ?> job) {
        McpxJobProperty jp = job != null ? job.getProperty(McpxJobProperty.class) : null;

        // Check job-level configuration first
        if (jp != null && Util.fixEmptyAndTrim(jp.getRegistryBaseUrl()) != null) {
            return Util.fixEmptyAndTrim(jp.getRegistryBaseUrl());
        }

        // Try to get global configuration (may fail in unit tests if Jenkins is not available)
        try {
            McpxGlobalConfiguration cfg = McpxGlobalConfiguration.get();
            if (cfg != null && Util.fixEmptyAndTrim(cfg.getRegistryBaseUrl()) != null) {
                return Util.fixEmptyAndTrim(cfg.getRegistryBaseUrl());
            }
        } catch (IllegalStateException e) {
            // Jenkins instance is not available (e.g., in unit tests)
            // Fall through to default
            LOGGER.log(Level.FINE, "Jenkins instance not available, using default registry base URL", e);
        } catch (Exception e) {
            // Any other exception, log and fall through to default
            LOGGER.log(Level.FINE, "Failed to get global configuration, using default registry base URL", e);
        }

        // Default fallback
        return "https://mcpx.example.com";
    }
}
