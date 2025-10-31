package io.modelcontextprotocol.jenkins;

import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;
import javax.annotation.Nonnull;
import java.io.IOException;

public class McpxJobProperty extends JobProperty<AbstractProject<?, ?>> {
    private final String cliPath;
    private final String registryBaseUrl;
    private final String cliDownloadUrl;

    @DataBoundConstructor
    public McpxJobProperty(String cliPath, String registryBaseUrl, String cliDownloadUrl) {
        this.cliPath = Util.fixEmptyAndTrim(cliPath);
        this.registryBaseUrl = Util.fixEmptyAndTrim(registryBaseUrl);
        this.cliDownloadUrl = Util.fixEmptyAndTrim(cliDownloadUrl);
    }

    public String getCliPath() {
        return cliPath;
    }

    public String getRegistryBaseUrl() {
        return registryBaseUrl;
    }

    public String getCliDownloadUrl() {
        return cliDownloadUrl;
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

        @POST
        public FormValidation doTestCli(@QueryParameter String cliPath, @QueryParameter String testNodeName) {
            try {
                String path = Util.fixEmptyAndTrim(cliPath);
                if (path == null) path = "mcpx-cli";

                // If a node was selected, attempt to run the test on that node
                String nodeName = Util.fixEmptyAndTrim(testNodeName);
                if (nodeName != null) {
                    jenkins.model.Jenkins j = jenkins.model.Jenkins.get();
                    hudson.model.Node node = "(built-in)".equals(nodeName) ? j : j.getNode(nodeName);
                    if (node != null && node.toComputer() != null && node.toComputer().isOnline()) {
                        hudson.FilePath root = node.getRootPath();
                        if (root != null) {
                            String version = root.act(new CliVersionCallable(path));
                            return FormValidation.ok("mcpx-cli is working on node '" + nodeName + "'! Version: " + version);
                        }
                    }
                    // If node not available fall back to controller
                }

                // Fallback: run on controller
                McpxCliClient client = new McpxCliClient(expandTilde(path));
                String version = client.getVersion();
                return FormValidation.ok("mcpx-cli is working on controller! Version: " + version);
            } catch (Exception e) {
                return FormValidation.error("Failed to execute mcpx-cli: " + e.getMessage());
            }
        }

        // Optional: provide a node list for dynamic dropdowns (not required by current jelly)
        public hudson.util.ListBoxModel doFillTestNodeNameItems() {
            hudson.util.ListBoxModel m = new hudson.util.ListBoxModel();
            jenkins.model.Jenkins j = jenkins.model.Jenkins.get();
            m.add("Built-in Node (controller)", "(built-in)");
            for (hudson.model.Node n : j.getNodes()) {
                String name = n.getNodeName();
                String display = name;
                if (n.toComputer() != null && !n.toComputer().isOnline()) {
                    display = name + " (offline)";
                }
                m.add(display, name);
            }
            return m;
        }

        private static String expandTilde(String path) {
            if (path == null) return null;
            if (path.startsWith("~/")) {
                String home = System.getProperty("user.home");
                if (home != null && !home.isEmpty()) {
                    return home + path.substring(1);
                }
            }
            return path;
        }

        // Callable executed on a remote node to get mcpx-cli version
        private static class CliVersionCallable implements hudson.FilePath.FileCallable<String> {
            private final String rawPath;

            CliVersionCallable(String rawPath) {
                this.rawPath = rawPath;
            }

            @Override
            public String invoke(java.io.File f, hudson.remoting.VirtualChannel channel) throws java.io.IOException, InterruptedException {
                String path = rawPath;
                if (path == null || path.isEmpty()) {
                    path = "mcpx-cli";
                }
                // Expand leading ~ on the agent
                if (path.startsWith("~/")) {
                    String home = System.getProperty("user.home");
                    if (home != null && !home.isEmpty()) {
                        path = home + path.substring(1);
                    }
                }

                java.lang.ProcessBuilder pb = new java.lang.ProcessBuilder(path, "--version");
                java.lang.Process proc = pb.start();
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                try (java.io.InputStream is = proc.getInputStream()) {
                    byte[] buf = new byte[4096];
                    int r;
                    while ((r = is.read(buf)) >= 0) {
                        baos.write(buf, 0, r);
                    }
                }
                int code = proc.waitFor();
                if (code != 0) {
                    // Try to capture stderr for better diagnostics
                    java.io.ByteArrayOutputStream err = new java.io.ByteArrayOutputStream();
                    try (java.io.InputStream es = proc.getErrorStream()) {
                        byte[] buf = new byte[4096];
                        int r;
                        while ((r = es.read(buf)) >= 0) {
                            err.write(buf, 0, r);
                        }
                    }
            throw new java.io.IOException("mcpx-cli --version failed with exit code " + code + ": " + err.toString(java.nio.charset.StandardCharsets.UTF_8));
                }
                return baos.toString(java.nio.charset.StandardCharsets.UTF_8).trim();
            }

            @Override
            public void checkRoles(org.jenkinsci.remoting.RoleChecker checker) throws SecurityException {
                // Accept default; no special roles required
            }
        }

        @POST
        public FormValidation doUpdateCli(@QueryParameter String cliPath, @QueryParameter String cliDownloadUrl) {
            try {
                String path = Util.fixEmptyAndTrim(cliPath);
                String url = Util.fixEmptyAndTrim(cliDownloadUrl);
                if (url == null || url.isEmpty()) {
                    return FormValidation.error("CLI Download URL is required to update mcpx-cli.");
                }

                McpxCliInstaller installer = new McpxCliInstaller();
                installer.downloadAndInstall(url, path, null, null);

                return FormValidation.ok("mcpx-cli updated successfully!");
            } catch (Exception e) {
                return FormValidation.error("Failed to update mcpx-cli: " + e.getMessage());
            }
        }
    }
}
