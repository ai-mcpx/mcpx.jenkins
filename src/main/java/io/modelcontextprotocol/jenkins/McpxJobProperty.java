package io.modelcontextprotocol.jenkins;

import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.verb.POST;

import javax.annotation.Nonnull;

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

        @Override
        public boolean isApplicable(Class jobType) {
            return AbstractProject.class.isAssignableFrom(jobType);
        }

        @Override
        public JobProperty<?> newInstance(StaplerRequest req, net.sf.json.JSONObject formData) throws FormException {
            if (formData == null || formData.isNullObject()) {
                return null;
            }
            return req.bindJSON(McpxJobProperty.class, formData);
        }

        @POST
        public FormValidation doTestCli(@AncestorInPath AbstractProject<?, ?> project, @QueryParameter String cliPath) {
            try {
                String path = Util.fixEmptyAndTrim(cliPath);
                if (path == null) {
                    // Fall back to global configuration if job-specific path not provided
                    McpxGlobalConfiguration cfg = McpxGlobalConfiguration.get();
                    String globalPath = (cfg != null) ? Util.fixEmptyAndTrim(cfg.getCliPath()) : null;
                    path = (globalPath != null) ? globalPath : "mcpx-cli";
                }

                jenkins.model.Jenkins j = jenkins.model.Jenkins.get();
                hudson.model.Node target = null;

                if (project != null) {
                    hudson.model.Label assigned = project.getAssignedLabel();
                    if (assigned != null) {
                        for (hudson.model.Node n : assigned.getNodes()) {
                            if (n != null && n.toComputer() != null && n.toComputer().isOnline()) {
                                target = n;
                                break;
                            }
                        }
                        if (target == null) {
                            return FormValidation.error("No online agents match the job's label: '" + assigned.getExpression() + "'.");
                        }
                    }
                }

                if (target == null) {
                    target = j; // fallback to controller
                }

                hudson.FilePath root = target.getRootPath();
                if (root == null) {
                    return FormValidation.error("Unable to access workspace on target node.");
                }

                String version = root.act(new CliVersionCallable(path));
                String where = (target == j) ? "controller" : target.getNodeName();
                return FormValidation.ok("mcpx-cli is working on " + where + "! Version: " + version);
            } catch (Exception e) {
                return FormValidation.error("Failed to execute mcpx-cli: " + e.getMessage());
            }
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
