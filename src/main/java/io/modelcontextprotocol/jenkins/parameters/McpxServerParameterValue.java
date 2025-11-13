package io.modelcontextprotocol.jenkins.parameters;

import hudson.EnvVars;
import hudson.model.ParameterValue;
import hudson.model.Run;
import hudson.util.VariableResolver;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;

public class McpxServerParameterValue extends ParameterValue {
    private final String serverName;

    @DataBoundConstructor
    public McpxServerParameterValue(String name, String serverName) {
        super(name);
        this.serverName = serverName;
    }

    public String getServerName() {
        return serverName;
    }

    public void buildEnvironment(@Nonnull Run<?, ?> build, @Nonnull EnvVars env) {
        if (serverName != null && !serverName.trim().isEmpty()) {
            env.put(name, serverName);
        }
    }

    public VariableResolver<String> createVariableResolver(@Nonnull Run<?, ?> build) {
        return name -> McpxServerParameterValue.this.name.equals(name) ? serverName : null;
    }
}
