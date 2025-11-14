package io.modelcontextprotocol.jenkins;

import hudson.Util;
import hudson.model.Job;
import hudson.model.ParameterDefinition;
import hudson.model.StringParameterDefinition;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class to extract parameters from MCP server packages and create Jenkins parameter definitions.
 */
public class McpxPackageParameterExtractor {
    private static final Logger LOGGER = Logger.getLogger(McpxPackageParameterExtractor.class.getName());

    /**
     * Extracts parameters from server packages and creates Jenkins parameter definitions.
     * @param job The job to use for configuration
     * @param serverName The MCP server name
     * @return List of parameter definitions extracted from packages
     */
    public static List<ParameterDefinition> extractParameters(Job<?, ?> job, String serverName) {
        List<ParameterDefinition> parameters = new ArrayList<>();

        if (serverName == null || serverName.trim().isEmpty()) {
            return parameters;
        }

        try {
            McpxRegistryClient client = new McpxRegistryClient();
            String serverDetailsJson = client.fetchServerDetails(job, serverName);
            JSONObject serverDetails = client.parseServerDetails(serverDetailsJson);

            if (!serverDetails.has("packages")) {
                LOGGER.log(Level.FINE, "No packages found in server details for: " + serverName);
                return parameters;
            }

            JSONArray packages = serverDetails.getJSONArray("packages");
            if (packages == null || packages.isEmpty()) {
                return parameters;
            }

            // Process the first package (typically there's one package per server)
            JSONObject packageObj = packages.getJSONObject(0);

            // Extract registryType from package
            if (packageObj.has("registryType")) {
                String registryType = packageObj.optString("registryType", "");
                if (!registryType.isEmpty()) {
                    String description = "Registry type for the MCP server package (e.g., docker, binary, npm, pypi, wheel)";
                    StringParameterDefinition registryTypeParam = new StringParameterDefinition(
                        "MCPX_REGISTRY_TYPE",
                        registryType,
                        description
                    );
                    parameters.add(registryTypeParam);
                }
            }

            // Extract runtimeArguments
            if (packageObj.has("runtimeArguments")) {
                JSONArray runtimeArgs = packageObj.getJSONArray("runtimeArguments");
                for (int i = 0; i < runtimeArgs.size(); i++) {
                    JSONObject arg = runtimeArgs.getJSONObject(i);
                    ParameterDefinition param = createParameterFromRuntimeArgument(arg);
                    if (param != null) {
                        parameters.add(param);
                    }
                }
            }

            // Extract environmentVariables
            if (packageObj.has("environmentVariables")) {
                JSONArray envVars = packageObj.getJSONArray("environmentVariables");
                for (int i = 0; i < envVars.size(); i++) {
                    JSONObject envVar = envVars.getJSONObject(i);
                    ParameterDefinition param = createParameterFromEnvironmentVariable(envVar);
                    if (param != null) {
                        parameters.add(param);
                    }
                }
            }

            LOGGER.log(Level.INFO, "Extracted " + parameters.size() + " parameters from packages for server: " + serverName);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to extract parameters from packages for server: " + serverName, e);
        }

        return parameters;
    }

    /**
     * Creates a Jenkins parameter definition from a runtime argument.
     */
    private static ParameterDefinition createParameterFromRuntimeArgument(JSONObject arg) {
        try {
            String type = arg.optString("type", "positional");
            String name = null;
            String description = arg.optString("description", "");
            String defaultValue = arg.optString("default", "");
            boolean isRequired = arg.optBoolean("isRequired", false);
            String valueHint = arg.optString("valueHint", "");

            if ("named".equals(type)) {
                String argName = arg.optString("name", "");
                if (argName.isEmpty()) {
                    LOGGER.log(Level.WARNING, "Skipping runtime argument with missing name");
                    return null;
                }
                // For named arguments, prefer valueHint if available for more descriptive names
                // (e.g., -p with valueHint "port_mapping" -> MCPX_PORT_MAPPING instead of MCPX_P)
                if (!valueHint.isEmpty()) {
                    name = "MCPX_" + valueHint.replace("-", "_").toUpperCase();
                } else {
                    // Use the argument name (e.g., --port -> MCPX_PORT)
                    name = "MCPX_" + argName.replace("--", "").replace("-", "_").toUpperCase();
                }
            } else if ("positional".equals(type)) {
                // For positional arguments, use valueHint or generate a name
                if (!valueHint.isEmpty()) {
                    name = "MCPX_" + valueHint.replace("-", "_").toUpperCase();
                } else {
                    name = "MCPX_RUNTIME_ARG_" + System.currentTimeMillis();
                }
            } else {
                LOGGER.log(Level.WARNING, "Unknown runtime argument type: " + type);
                return null;
            }

            if (name == null || name.isEmpty()) {
                return null;
            }

            // Create description with original argument info
            if (description.isEmpty()) {
                description = "Runtime argument: " + (arg.has("name") ? arg.getString("name") : valueHint);
            }

            return new StringParameterDefinition(name, defaultValue, description);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to create parameter from runtime argument", e);
            return null;
        }
    }

    /**
     * Creates a Jenkins parameter definition from an environment variable.
     */
    private static ParameterDefinition createParameterFromEnvironmentVariable(JSONObject envVar) {
        try {
            String name = envVar.optString("name", "");
            if (name.isEmpty()) {
                LOGGER.log(Level.WARNING, "Skipping environment variable with missing name");
                return null;
            }

            // Use the environment variable name directly, or prefix with MCPX_ if needed
            String paramName = name;
            if (!paramName.startsWith("MCPX_") && !paramName.startsWith("MCP_")) {
                paramName = "MCPX_" + paramName;
            }

            String description = envVar.optString("description", "");
            if (description.isEmpty()) {
                description = "Environment variable: " + name;
            }

            String defaultValue = envVar.optString("default", "");
            boolean isRequired = envVar.optBoolean("isRequired", false);

            return new StringParameterDefinition(paramName, defaultValue, description);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to create parameter from environment variable", e);
            return null;
        }
    }

    /**
     * Gets default parameter values from packages for a given server.
     * This can be used to set defaults when MCP_SERVER is configured.
     * @param job The job to use for configuration
     * @param serverName The MCP server name
     * @return Map of parameter names to default values
     */
    public static java.util.Map<String, String> getDefaultValues(Job<?, ?> job, String serverName) {
        java.util.Map<String, String> defaults = new java.util.HashMap<>();

        if (serverName == null || serverName.trim().isEmpty()) {
            return defaults;
        }

        try {
            McpxRegistryClient client = new McpxRegistryClient();
            String serverDetailsJson = client.fetchServerDetails(job, serverName);
            JSONObject serverDetails = client.parseServerDetails(serverDetailsJson);

            if (!serverDetails.has("packages")) {
                return defaults;
            }

            JSONArray packages = serverDetails.getJSONArray("packages");
            if (packages == null || packages.isEmpty()) {
                return defaults;
            }

            JSONObject packageObj = packages.getJSONObject(0);

            // Extract registryType from package
            if (packageObj.has("registryType")) {
                String registryType = packageObj.optString("registryType", "");
                if (!registryType.isEmpty()) {
                    defaults.put("MCPX_REGISTRY_TYPE", registryType);
                }
            }

            // Extract runtimeArguments defaults
            if (packageObj.has("runtimeArguments")) {
                JSONArray runtimeArgs = packageObj.getJSONArray("runtimeArguments");
                for (int i = 0; i < runtimeArgs.size(); i++) {
                    JSONObject arg = runtimeArgs.getJSONObject(i);
                    String type = arg.optString("type", "positional");
                    String name = null;
                    String defaultValue = arg.optString("default", "");

                    if ("named".equals(type)) {
                        String argName = arg.optString("name", "");
                        String valueHint = arg.optString("valueHint", "");
                        if (!argName.isEmpty()) {
                            // Prefer valueHint if available for more descriptive names
                            if (!valueHint.isEmpty()) {
                                name = "MCPX_" + valueHint.replace("-", "_").toUpperCase();
                            } else {
                                name = "MCPX_" + argName.replace("--", "").replace("-", "_").toUpperCase();
                            }
                        }
                    } else if ("positional".equals(type)) {
                        String valueHint = arg.optString("valueHint", "");
                        if (!valueHint.isEmpty()) {
                            name = "MCPX_" + valueHint.replace("-", "_").toUpperCase();
                        }
                    }

                    if (name != null && !name.isEmpty() && !defaultValue.isEmpty()) {
                        defaults.put(name, defaultValue);
                    }
                }
            }

            // Extract environmentVariables defaults
            if (packageObj.has("environmentVariables")) {
                JSONArray envVars = packageObj.getJSONArray("environmentVariables");
                for (int i = 0; i < envVars.size(); i++) {
                    JSONObject envVar = envVars.getJSONObject(i);
                    String name = envVar.optString("name", "");
                    if (!name.isEmpty()) {
                        String paramName = name;
                        if (!paramName.startsWith("MCPX_") && !paramName.startsWith("MCP_")) {
                            paramName = "MCPX_" + paramName;
                        }
                        String defaultValue = envVar.optString("default", "");
                        if (!defaultValue.isEmpty()) {
                            defaults.put(paramName, defaultValue);
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to get default values from packages for server: " + serverName, e);
        }

        return defaults;
    }
}
