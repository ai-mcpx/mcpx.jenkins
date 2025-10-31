package io.modelcontextprotocol.jenkins;

import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import hudson.util.Secret;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.Nonnull;

public class McpxJobProperty extends JobProperty<AbstractProject<?, ?>> {
    private final String cliPath;
    private final String registryBaseUrl;
    private final String cliUsername;
    private final Secret cliPassword;

    @DataBoundConstructor
    public McpxJobProperty(String cliPath, String registryBaseUrl, String cliUsername, Secret cliPassword) {
        this.cliPath = Util.fixEmptyAndTrim(cliPath);
        this.registryBaseUrl = Util.fixEmptyAndTrim(registryBaseUrl);
        this.cliUsername = Util.fixEmptyAndTrim(cliUsername);
        this.cliPassword = cliPassword;
    }

    public String getCliPath() {
        return cliPath;
    }

    public String getRegistryBaseUrl() {
        return registryBaseUrl;
    }

    public String getCliUsername() {
        return cliUsername;
    }

    public Secret getCliPassword() {
        return cliPassword;
    }

    @Extension
    public static class DescriptorImpl extends JobPropertyDescriptor {
        @Nonnull
        @Override
        public String getDisplayName() {
            return "MCPX CLI Configuration";
        }

        // Only override the method for AbstractProject, as JobPropertyDescriptor expects
        @Override
        public boolean isApplicable(Class jobType) {
            return AbstractProject.class.isAssignableFrom(jobType);
        }

        @Override
        public JobProperty<?> newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            if (formData == null || formData.isNullObject()) {
                return null;
            }
            return req.bindJSON(McpxJobProperty.class, formData);
        }
    }
}
