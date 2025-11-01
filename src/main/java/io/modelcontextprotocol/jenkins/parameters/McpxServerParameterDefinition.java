package io.modelcontextprotocol.jenkins.parameters;

import hudson.Extension;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.SimpleParameterDefinition;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import io.modelcontextprotocol.jenkins.McpxRegistryClient;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.verb.POST;

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

    public ListBoxModel doFillDefaultServerItems(@org.kohsuke.stapler.AncestorInPath hudson.model.AbstractProject<?, ?> project) {
        if (project != null) {
            return new McpxRegistryClient().fetchServers(project);
        }
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
            return "MCP Servers from MCPX Registry";
        }

        public ListBoxModel doFillValueItems(@org.kohsuke.stapler.AncestorInPath hudson.model.AbstractProject<?, ?> project, @QueryParameter String value) {
            hudson.util.ListBoxModel model = (project != null)
                    ? new McpxRegistryClient().fetchServers(project)
                    : new McpxRegistryClient().fetchServers();
            // Try to preselect the current or default value for clarity on the build page
            String sel = hudson.Util.fixEmptyAndTrim(value);
            if (sel == null || sel.isEmpty()) {
                // If no current value, prefer the definition's default if present
                if (project != null) {
                    // 'it' (definition) is not available here; we can't access defaultServer directly.
                    // As a heuristic, select the first non-empty option so the dropdown isn't visually empty.
                    for (hudson.util.ListBoxModel.Option o : model) {
                        if (o != null && o.value != null && !o.value.isEmpty()) { o.selected = true; break; }
                    }
                } else {
                    for (hudson.util.ListBoxModel.Option o : model) {
                        if (o != null && o.value != null && !o.value.isEmpty()) { o.selected = true; break; }
                    }
                }
            } else {
                // Select the option matching the current value
                boolean matched = false;
                for (hudson.util.ListBoxModel.Option o : model) {
                    if (o != null && sel.equals(o.value)) { o.selected = true; matched = true; break; }
                }
                // If no exact match found, fall back to first non-empty option
                if (!matched) {
                    for (hudson.util.ListBoxModel.Option o : model) {
                        if (o != null && o.value != null && !o.value.isEmpty()) { o.selected = true; break; }
                    }
                }
            }
            return model;
        }

        @POST
        public FormValidation doRefreshServers(@org.kohsuke.stapler.AncestorInPath hudson.model.AbstractProject<?, ?> project) {
            try {
                // Trigger a fresh fetch to validate availability; UI will repopulate on reload
                hudson.util.ListBoxModel model = (project != null)
                        ? new McpxRegistryClient().fetchServers(project)
                        : new McpxRegistryClient().fetchServers();
                int count = 0;
                StringBuilder list = new StringBuilder();
                for (hudson.util.ListBoxModel.Option opt : model) {
                    if (opt != null && opt.value != null && !opt.value.isEmpty()) {
                        count++;
                        if (list.length() > 0) list.append("\n");
                        list.append(opt.name).append(" -> ").append(opt.value);
                    }
                }
                String msg = "Refreshed MCP servers from registry (" + count + " items).";
                if (count > 0) msg += "\n\n" + list.toString();
                return FormValidation.ok(msg);
            } catch (Exception e) {
                return FormValidation.error("Failed to refresh servers: " + e.getMessage());
            }
        }

        @POST
        public FormValidation doProbeServers(@org.kohsuke.stapler.AncestorInPath hudson.model.AbstractProject<?, ?> project) {
            io.modelcontextprotocol.jenkins.McpxGlobalConfiguration cfg = io.modelcontextprotocol.jenkins.McpxGlobalConfiguration.get();
            io.modelcontextprotocol.jenkins.McpxJobProperty jp = project != null ? project.getProperty(io.modelcontextprotocol.jenkins.McpxJobProperty.class) : null;

            String baseUrl = (jp != null && hudson.Util.fixEmptyAndTrim(jp.getRegistryBaseUrl()) != null)
                    ? hudson.Util.fixEmptyAndTrim(jp.getRegistryBaseUrl())
                    : (cfg != null && hudson.Util.fixEmptyAndTrim(cfg.getRegistryBaseUrl()) != null)
                        ? hudson.Util.fixEmptyAndTrim(cfg.getRegistryBaseUrl())
                        : "https://registry.modelcontextprotocol.io";

            String cliPath = (jp != null && hudson.Util.fixEmptyAndTrim(jp.getCliPath()) != null)
                    ? hudson.Util.fixEmptyAndTrim(jp.getCliPath())
                    : (cfg != null) ? cfg.getCliPath() : null;
            if (hudson.Util.fixEmptyAndTrim(cliPath) == null) {
                return FormValidation.error("mcpx-cli path not configured (global or job override).");
            }

            // Build ordered candidate nodes: job's labeled nodes -> any online agents -> controller (last resort)
            java.util.LinkedHashMap<hudson.model.Node, String> candidates = new java.util.LinkedHashMap<>();
            jenkins.model.Jenkins j = jenkins.model.Jenkins.get();

            if (project != null && project.getAssignedLabel() != null) {
                for (hudson.model.Node n : project.getAssignedLabel().getNodes()) {
                    if (n != null && n.toComputer() != null && n.toComputer().isOnline()) {
                        candidates.put(n, n.getNodeName());
                    }
                }
            }

            for (hudson.model.Node n : j.getNodes()) {
                if (n != null && n.toComputer() != null && n.toComputer().isOnline() && !candidates.containsKey(n)) {
                    candidates.put(n, n.getNodeName());
                }
            }

            candidates.put(j, "controller");

            java.util.List<String> errors = new java.util.ArrayList<>();
            for (java.util.Map.Entry<hudson.model.Node, String> entry : candidates.entrySet()) {
                hudson.model.Node node = entry.getKey();
                String where = entry.getValue();
                try {
                    hudson.FilePath root = node.getRootPath();
                    if (root == null) { errors.add(where + ": no root path"); continue; }
                    String json = root.act(new ProbeCallable(cliPath, baseUrl));
                    String snippet = (json != null) ? json : "<null>";
                    if (snippet.length() > 400) snippet = snippet.substring(0, 400) + "...";
                    return FormValidation.ok("Probe OK on " + where + " | baseUrl=" + baseUrl + " | cliPath=" + cliPath + " | json: " + snippet);
                } catch (Exception ex) {
                    String msg = ex.getMessage();
                    if (msg == null) msg = ex.toString();
                    if (msg.length() > 300) msg = msg.substring(0, 300) + "...";
                    errors.add(where + ": " + msg);
                }
            }

            StringBuilder sb = new StringBuilder("Probe failed on all candidates. Errors: ");
            for (int i = 0; i < Math.min(errors.size(), 3); i++) {
                if (i > 0) sb.append(" | ");
                sb.append(errors.get(i));
            }
            if (errors.size() > 3) sb.append(" | +").append(errors.size() - 3).append(" more");
            return FormValidation.error(sb.toString());
        }

        // Build a multi-line preview of available servers to display in the UI
        public String getServersPreview() {
            try {
                hudson.model.AbstractProject<?, ?> project = null;
                org.kohsuke.stapler.StaplerRequest req = Stapler.getCurrentRequest();
                if (req != null) {
                    project = req.findAncestorObject(hudson.model.AbstractProject.class);
                }
                hudson.util.ListBoxModel model = (project != null)
                        ? new McpxRegistryClient().fetchServers(project)
                        : new McpxRegistryClient().fetchServers();
                StringBuilder sb = new StringBuilder();
                for (hudson.util.ListBoxModel.Option o : model) {
                    if (o != null && o.value != null && !o.value.isEmpty()) {
                        if (sb.length() > 0) sb.append('\n');
                        // Show only the full server identifier (value), without the display name
                        sb.append(o.value);
                    }
                }
                if (sb.length() == 0) {
                    return "<no servers>";
                }
                return sb.toString();
            } catch (Exception e) {
                return "<error: " + e.getMessage() + ">";
            }
        }

        // Minimal callable to fetch raw JSON for diagnostics
        private static class ProbeCallable implements hudson.FilePath.FileCallable<String> {
            private final String rawCliPath;
            private final String baseUrl;

            ProbeCallable(String cliPath, String baseUrl) {
                this.rawCliPath = cliPath; // expand on remote to use remote user.home
                this.baseUrl = baseUrl;
            }

            @Override
            public String invoke(java.io.File f, hudson.remoting.VirtualChannel channel) throws java.io.IOException, InterruptedException {
                String cliPath = expandHome(rawCliPath);
                // Best-effort login
                new ProcessBuilder(cliPath, "--base-url=" + baseUrl, "login", "--method", "anonymous").start().waitFor();
                // List servers JSON
                Process p = new ProcessBuilder(cliPath, "--base-url=" + baseUrl, "servers", "--json").start();
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                try (java.io.InputStream is = p.getInputStream()) {
                    byte[] buf = new byte[4096];
                    int r; while ((r = is.read(buf)) >= 0) { out.write(buf, 0, r); }
                }
                int code = p.waitFor();
                if (code != 0) {
                    java.io.ByteArrayOutputStream err = new java.io.ByteArrayOutputStream();
                    try (java.io.InputStream es = p.getErrorStream()) {
                        byte[] buf = new byte[4096]; int r; while ((r = es.read(buf)) >= 0) { err.write(buf, 0, r); }
                    }
                    throw new java.io.IOException("mcpx-cli servers exit=" + code + ": " + err.toString(java.nio.charset.StandardCharsets.UTF_8));
                }
                return out.toString(java.nio.charset.StandardCharsets.UTF_8);
            }

            @Override
            public void checkRoles(org.jenkinsci.remoting.RoleChecker checker) throws SecurityException { }

            private static String expandHome(String path) {
                if (path != null && path.startsWith("~/")) {
                    String home = System.getProperty("user.home");
                    if (home != null && !home.isEmpty()) return home + path.substring(1);
                }
                return path;
            }
        }
    }
}
