package io.modelcontextprotocol.jenkins;

import hudson.Extension;
import hudson.Util;
import hudson.util.FormValidation;
import hudson.util.Secret;
import jenkins.model.GlobalConfiguration;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.verb.POST;

@Extension
public class McpxGlobalConfiguration extends GlobalConfiguration {
    private String registryBaseUrl = "";

    // mcpx-cli configuration
    private String cliPath = "~/.local/bin/mcpx-cli";

    public McpxGlobalConfiguration() {
        load();
    }

    public static McpxGlobalConfiguration get() {
        return GlobalConfiguration.all().get(McpxGlobalConfiguration.class);
    }

    public String getRegistryBaseUrl() {
        return registryBaseUrl;
    }

    public void setRegistryBaseUrl(String registryBaseUrl) {
        this.registryBaseUrl = Util.fixEmptyAndTrim(registryBaseUrl);
    }



    public String getCliPath() {
        return cliPath;
    }

    public void setCliPath(String cliPath) {
        this.cliPath = Util.fixEmptyAndTrim(cliPath);
    }

    @POST
    public FormValidation doCheckRequired(@QueryParameter String registryBaseUrl) {
        String url = Util.fixEmptyAndTrim(registryBaseUrl);
        if (url == null || url.isEmpty()) {
            return FormValidation.error("Registry Base URL is required");
        }
        return FormValidation.ok();
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        req.bindJSON(this, json);
        save();
        return true;
    }
}
