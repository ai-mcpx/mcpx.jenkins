package io.modelcontextprotocol.jenkins.parameters;

import hudson.Extension;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.SimpleParameterDefinition;
import hudson.util.ListBoxModel;
import io.modelcontextprotocol.jenkins.McpxRegistryClient;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.Nonnull;

public class McpxServerParameterDefinition extends SimpleParameterDefinition {

    @Override
    public ParameterValue createValue(StaplerRequest req, JSONObject jo) {
        String value = jo.optString("value");
        if (value == null || value.isEmpty()) {
            value = defaultServer;
        }
        return new McpxServerParameterValue(getName(), value);
    }

    private final String defaultServer;

    @DataBoundConstructor
    public McpxServerParameterDefinition(String name, String description, String defaultServer) {
        super(name, description);
        this.defaultServer = defaultServer;
    }

    public String getDefaultServer() {
        return defaultServer;
    }

    public ListBoxModel doFillDefaultServerItems() {
        return new McpxRegistryClient().fetchServers();
    }

    @Override
    public ParameterValue getDefaultParameterValue() {
        String def = defaultServer != null ? defaultServer : "";
        return new McpxServerParameterValue(getName(), def);
    }


    @Override
    public ParameterValue createValue(String value) {
        if (value == null || value.isEmpty()) {
            value = defaultServer;
        }
        return new McpxServerParameterValue(getName(), value);
    }

    @Extension
    public static class DescriptorImpl extends ParameterDescriptor {
        @Nonnull
        @Override
        public String getDisplayName() {
            return "MCPX Server (from Registry)";
        }

        public ListBoxModel doFillValueItems(@QueryParameter String value) {
            return new McpxRegistryClient().fetchServers();
        }
    }
}
