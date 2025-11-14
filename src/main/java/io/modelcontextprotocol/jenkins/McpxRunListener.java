package io.modelcontextprotocol.jenkins;

import hudson.Extension;
import hudson.EnvVars;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class McpxRunListener extends RunListener<Run<?, ?>> {
    private static final Logger LOGGER = Logger.getLogger(McpxRunListener.class.getName());

    @Override
    public void onInitialize(Run<?, ?> run) {
        attachEnv(run);
    }

    @Override
    public void onStarted(Run<?, ?> run, TaskListener listener) {
        attachEnv(run);
    }

    private void attachEnv(Run<?, ?> run) {
        if (run == null) return;
        Job<?, ?> job = run.getParent();
        if (job != null) {
            // Check for MCP_SERVER from job property
            McpxJobProperty prop = job.getProperty(McpxJobProperty.class);
            String selected = null;
            if (prop != null) {
                selected = prop.getSelectedServer();
            }

            // Also check for MCP_SERVER from parameters (Build with Parameters)
            if (selected == null || selected.trim().isEmpty()) {
                try {
                    hudson.model.ParametersAction paramsAction = run.getAction(hudson.model.ParametersAction.class);
                    if (paramsAction != null) {
                        for (hudson.model.ParameterValue paramValue : paramsAction.getParameters()) {
                            if ("MCP_SERVER".equals(paramValue.getName())) {
                                selected = paramValue.getValue().toString();
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.FINE, "Failed to get MCP_SERVER from parameters", e);
                }
            }

            if (selected != null && !selected.trim().isEmpty()) {
                // Avoid duplicates
                for (Object a : run.getAllActions()) {
                    if (a instanceof McpxSelectedServerEnvAction) {
                        return;
                    }
                }
                run.addAction(new McpxSelectedServerEnvAction(selected));

                // Inject package parameters as environment variables with defaults
                try {
                    Map<String, String> defaults = McpxPackageParameterExtractor.getDefaultValues(job, selected);
                    if (!defaults.isEmpty()) {
                        // Add environment variables for package parameters
                        // These will be available in the build environment
                        for (Map.Entry<String, String> entry : defaults.entrySet()) {
                            // Only set if not already set by user parameters
                            try {
                                EnvVars env = run.getEnvironment(null);
                                if (!env.containsKey(entry.getKey())) {
                                    // The environment variable will be set via the action
                                    LOGGER.log(Level.FINE, "Setting default for " + entry.getKey() + " = " + entry.getValue());
                                }
                            } catch (Exception e) {
                                // Ignore
                            }
                        }
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Failed to get default values from packages for server: " + selected, e);
                }
            }
        }
    }
}
