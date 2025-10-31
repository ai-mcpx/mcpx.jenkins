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
    private String cliDownloadUrl = "";
    private String cliUsername;
    private Secret cliPassword;
    private boolean autoUpdateCli = false;

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

    public String getCliDownloadUrl() {
        return cliDownloadUrl;
    }

    public void setCliDownloadUrl(String cliDownloadUrl) {
        this.cliDownloadUrl = Util.fixEmptyAndTrim(cliDownloadUrl);
    }

    public String getCliUsername() {
        return cliUsername;
    }

    public void setCliUsername(String cliUsername) {
        this.cliUsername = Util.fixEmptyAndTrim(cliUsername);
    }

    public Secret getCliPassword() {
        return cliPassword;
    }

    public void setCliPassword(Secret cliPassword) {
        this.cliPassword = cliPassword;
    }

    public boolean isAutoUpdateCli() {
        return autoUpdateCli;
    }

    public void setAutoUpdateCli(boolean autoUpdateCli) {
        this.autoUpdateCli = autoUpdateCli;
    }

    @POST
    public FormValidation doCheckRequired(@QueryParameter String registryBaseUrl) {
        String url = Util.fixEmptyAndTrim(registryBaseUrl);
        if (url == null || url.isEmpty()) {
            return FormValidation.error("Registry Base URL is required");
        }
        return FormValidation.ok();
    }

    @POST
    public FormValidation doTestCli(@QueryParameter String cliPath) {
        try {
            String path = Util.fixEmptyAndTrim(cliPath);
            if (path == null) path = "mcpx-cli";

            McpxCliClient client = new McpxCliClient(path);
            String version = client.getVersion();
            return FormValidation.ok("mcpx-cli is working! Version: " + version);
        } catch (Exception e) {
            return FormValidation.error("Failed to execute mcpx-cli: " + e.getMessage());
        }
    }

    @POST
    public FormValidation doUpdateCli(@QueryParameter String cliPath, @QueryParameter String cliDownloadUrl) {
        try {
            String path = Util.fixEmptyAndTrim(cliPath);
            String url = Util.fixEmptyAndTrim(cliDownloadUrl);
            String user = Util.fixEmptyAndTrim(getCliUsername());
            Secret pass = getCliPassword();

            McpxCliInstaller installer = new McpxCliInstaller();
            installer.downloadAndInstall(url, path, user, pass);

            return FormValidation.ok("mcpx-cli updated successfully!");
        } catch (Exception e) {
            return FormValidation.error("Failed to update mcpx-cli: " + e.getMessage());
        }
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        req.bindJSON(this, json);
        save();
        return true;
    }
}
