package io.modelcontextprotocol.jenkins.parameters;

import hudson.EnvVars;
import hudson.model.ParameterValue;
import hudson.model.Run;
import hudson.util.VariableResolver;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.Nonnull;
import java.util.logging.Logger;

public class McpxServerParameterValue extends ParameterValue {
    private static final Logger LOGGER = Logger.getLogger(McpxServerParameterValue.class.getName());
    private String serverName;

    static {
        LOGGER.severe("=== McpxServerParameterValue class loaded ===");
        LOGGER.info("McpxServerParameterValue class loaded");
    }

    // No-argument constructor for JSON deserialization
    public McpxServerParameterValue() {
        super("");
        this.serverName = "";
        LOGGER.severe("=== McpxServerParameterValue no-arg constructor CALLED ===");
        LOGGER.info("McpxServerParameterValue no-arg constructor called");
    }

    @DataBoundConstructor
    public McpxServerParameterValue(String name, String serverName) {
        super(name);
        this.serverName = serverName;
        LOGGER.severe("=== McpxServerParameterValue constructor(String, String) CALLED ===");
        LOGGER.info("McpxServerParameterValue constructor(String, String) called: name='" + name + "', serverName='" + serverName + "'");
        LOGGER.info("McpxServerParameterValue stack trace: " + java.util.Arrays.toString(
            java.lang.Thread.currentThread().getStackTrace()).substring(0, Math.min(500,
            java.util.Arrays.toString(java.lang.Thread.currentThread().getStackTrace()).length())));
    }

    // Constructor for JSON deserialization - Jenkins may use this when loading builds
    public McpxServerParameterValue(StaplerRequest req, JSONObject jo) {
        super(jo.getString("name"));
        String value = jo.optString("value", "");
        if (value == null || value.isEmpty()) {
            value = jo.optString("serverName", "");
        }
        this.serverName = value;
        LOGGER.severe("=== McpxServerParameterValue constructor(StaplerRequest, JSONObject) CALLED ===");
        String paramName = jo.getString("name");
        LOGGER.info("McpxServerParameterValue(StaplerRequest, JSONObject) constructor called: name='" + paramName + "', serverName='" + serverName + "'");
        LOGGER.info("JSONObject: " + jo.toString());
    }

    public String getServerName() {
        return serverName;
    }

    @Override
    public String getValue() {
        // Return the server name as the parameter value
        // This is used by Jenkins to set the environment variable
        return serverName != null ? serverName : "";
    }

    @Override
    public void buildEnvironment(@Nonnull Run<?, ?> build, @Nonnull EnvVars env) {
        // Always set the environment variable
        // Use getValue() to ensure we get the correct value even if serverName is null
        String value = getValue();
        LOGGER.severe("=== McpxServerParameterValue.buildEnvironment CALLED ===");
        LOGGER.info("McpxServerParameterValue.buildEnvironment called: name='" + name + "', value='" + value + "', build=" + build);
        env.put(name, value);
        LOGGER.info("Set environment variable '" + name + "' to '" + value + "' in build " + build.getNumber());
    }

    public VariableResolver<String> createVariableResolver(@Nonnull Run<?, ?> build) {
        return name -> McpxServerParameterValue.this.name.equals(name) ? getValue() : null;
    }

    @Override
    public String toString() {
        return "McpxServerParameterValue{name='" + name + "', serverName='" + serverName + "'}";
    }

    // Method to ensure proper deserialization from JSON
    // This might be called when Jenkins loads builds from disk
    private Object readResolve() {
        LOGGER.info("McpxServerParameterValue.readResolve called: name='" + name + "', serverName='" + serverName + "'");
        return this;
    }
}
