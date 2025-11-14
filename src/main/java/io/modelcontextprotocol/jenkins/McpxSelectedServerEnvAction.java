package io.modelcontextprotocol.jenkins;

import hudson.EnvVars;
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
                Job<?, ?> job = run.getParent();
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
}
