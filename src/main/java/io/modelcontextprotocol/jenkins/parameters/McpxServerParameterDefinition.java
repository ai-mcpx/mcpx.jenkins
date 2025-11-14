package io.modelcontextprotocol.jenkins.parameters;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Job;
import hudson.model.Label;
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
import java.util.logging.Logger;

public class McpxServerParameterDefinition extends SimpleParameterDefinition {
    private static final Logger LOGGER = Logger.getLogger(McpxServerParameterDefinition.class.getName());

    static {
        LOGGER.info("McpxServerParameterDefinition class loaded");
    }

    // Override to ensure we're called instead of parent class
    @Override
    public ParameterValue createValue(StaplerRequest req, JSONObject jo) {
        // Log that we're being called
        LOGGER.severe("=== McpxServerParameterDefinition.createValue(StaplerRequest, JSONObject) CALLED ===");
        LOGGER.info("McpxServerParameterDefinition.createValue(StaplerRequest, JSONObject) called - stack trace: " +
            java.util.Arrays.toString(java.lang.Thread.currentThread().getStackTrace()).substring(0, Math.min(1000,
            java.util.Arrays.toString(java.lang.Thread.currentThread().getStackTrace()).length())));
        LOGGER.info("McpxServerParameterDefinition.createValue(StaplerRequest, JSONObject) called for parameter: " + getName());
        String value = null;

        // Try multiple sources for the parameter value
        // 1. From JSON "value" key
        if (jo != null && jo.has("value")) {
            value = jo.optString("value", null);
            LOGGER.info("Found value from JSON 'value' key: " + value);
        }

        // 2. From JSON using parameter name as key
        if ((value == null || value.isEmpty()) && jo != null && jo.has(getName())) {
            value = jo.optString(getName(), null);
            LOGGER.info("Found value from JSON parameter name key: " + value);
        }

        // 3. From request parameter "value"
        if ((value == null || value.isEmpty()) && req != null) {
            value = req.getParameter("value");
            LOGGER.info("Found value from request parameter 'value': " + value);
        }

        // 4. From request parameter using parameter name
        if ((value == null || value.isEmpty()) && req != null) {
            value = req.getParameter(getName());
            LOGGER.info("Found value from request parameter '" + getName() + "': " + value);
        }

        // Use defaultServer if value is null, empty, or only whitespace
        if (value == null || value.trim().isEmpty()) {
            value = (defaultServer != null && !defaultServer.trim().isEmpty()) ? defaultServer : "";
            LOGGER.info("Using defaultServer or empty string: " + value);
        }

        LOGGER.info("Creating McpxServerParameterValue with name='" + getName() + "', value='" + value + "'");
        McpxServerParameterValue paramValue = new McpxServerParameterValue(getName(), value);
        LOGGER.info("Created parameter value: " + paramValue);
        return paramValue;
    }

    private final String defaultServer;

    @DataBoundConstructor
    public McpxServerParameterDefinition(String name, String description, String defaultServer) {
        super(name, description);
        this.defaultServer = defaultServer;
        LOGGER.info("McpxServerParameterDefinition constructor called: name='" + name + "', description='" + description + "', defaultServer='" + defaultServer + "'");
    }

    public String getDefaultServer() {
        return defaultServer;
    }

    public ListBoxModel doFillDefaultServerItems(@org.kohsuke.stapler.AncestorInPath hudson.model.Job<?, ?> job) {
        if (job != null) {
            return new McpxRegistryClient().fetchServers(job);
        }
        return new McpxRegistryClient().fetchServers();
    }

    @Override
    public ParameterValue getDefaultParameterValue() {
        LOGGER.severe("=== McpxServerParameterDefinition.getDefaultParameterValue CALLED ===");
        String def = defaultServer != null ? defaultServer : "";
        LOGGER.info("McpxServerParameterDefinition.getDefaultParameterValue called: name='" + getName() + "', default='" + def + "'");
        LOGGER.info("getDefaultParameterValue stack trace: " + java.util.Arrays.toString(
            java.lang.Thread.currentThread().getStackTrace()).substring(0, Math.min(1000,
            java.util.Arrays.toString(java.lang.Thread.currentThread().getStackTrace()).length())));
        McpxServerParameterValue paramValue = new McpxServerParameterValue(getName(), def);
        LOGGER.info("getDefaultParameterValue returning: " + paramValue);
        return paramValue;
    }


    @Override
    public ParameterValue createValue(String value) {
        LOGGER.severe("=== McpxServerParameterDefinition.createValue(String) CALLED ===");
        LOGGER.info("McpxServerParameterDefinition.createValue(String) called for parameter: " + getName() + ", input value: " + value);
        // Use defaultServer if value is null, empty, or only whitespace
        // But preserve the original value if it's not empty (don't trim it)
        if (value == null || value.trim().isEmpty()) {
            value = (defaultServer != null && !defaultServer.trim().isEmpty()) ? defaultServer : "";
            LOGGER.info("Using defaultServer or empty string: " + value);
        }
        LOGGER.info("Creating McpxServerParameterValue with name='" + getName() + "', value='" + value + "'");
        McpxServerParameterValue paramValue = new McpxServerParameterValue(getName(), value);
        LOGGER.info("Created parameter value: " + paramValue);
        return paramValue;
    }

    @Extension
    public static class DescriptorImpl extends ParameterDefinition.ParameterDescriptor {
        static {
            java.util.logging.Logger.getLogger(McpxServerParameterDefinition.class.getName())
                .info("McpxServerParameterDefinition.DescriptorImpl class loaded");
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return "MCP Servers from MCPX Registry";
        }

        public ListBoxModel doFillValueItems(@org.kohsuke.stapler.AncestorInPath hudson.model.Job<?, ?> job, @QueryParameter String value) {
            hudson.util.ListBoxModel model = new hudson.util.ListBoxModel();
            try {
                model = (job != null)
                        ? new McpxRegistryClient().fetchServers(job)
                        : new McpxRegistryClient().fetchServers();

                // Ensure we have at least one option
                if (model.isEmpty()) {
                    model.add("(No servers available)", "");
                }

                // Try to preselect the current or default value for clarity on the build page
                String sel = hudson.Util.fixEmptyAndTrim(value);
                if (sel == null || sel.isEmpty()) {
                    // If no current value, prefer the definition's default if present
                    // As a heuristic, select the first non-empty option so the dropdown isn't visually empty.
                    for (hudson.util.ListBoxModel.Option o : model) {
                        if (o != null && o.value != null && !o.value.isEmpty()) { o.selected = true; break; }
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
            } catch (Exception e) {
                // Return error model instead of throwing exception to prevent page rendering failure
                java.util.logging.Logger.getLogger(McpxServerParameterDefinition.class.getName())
                    .log(java.util.logging.Level.WARNING, "Error populating MCP server dropdown", e);
                String errorMsg = e.getMessage();
                if (errorMsg == null || errorMsg.isEmpty()) {
                    errorMsg = e.getClass().getSimpleName();
                }
                // Truncate long error messages
                if (errorMsg.length() > 100) {
                    errorMsg = errorMsg.substring(0, 97) + "...";
                }
                model.add("(Error: " + errorMsg + ")", "");
            }
            return model;
        }

        @POST
        public FormValidation doRefreshServers(@org.kohsuke.stapler.AncestorInPath hudson.model.Job<?, ?> job) {
            try {
                // Trigger a fresh fetch to validate availability; UI will repopulate on reload
                hudson.util.ListBoxModel model = (job != null)
                        ? new McpxRegistryClient().fetchServers(job)
                        : new McpxRegistryClient().fetchServers();
                int count = 0;
                StringBuilder list = new StringBuilder();
                for (hudson.util.ListBoxModel.Option opt : model) {
                    if (opt != null && opt.value != null && !opt.value.isEmpty()) {
                        count++;
                        if (list.length() > 0) list.append("\n");
                        // Show only the full identifier (value), consistent with the preview area
                        list.append(opt.value);
                    }
                }
                String msg = "Refreshed MCP servers from registry (" + count + " items).";
                if (count > 0) msg += "\n\n" + list.toString();
                return FormValidation.ok(msg);
            } catch (Exception e) {
                return FormValidation.error("Failed to refresh servers: " + e.getMessage());
            }
        }

        /**
         * Gets parameter definitions extracted from packages for a given server.
         * This helps users see what parameters are available to add to their job configuration.
         */
        @POST
        public FormValidation doGetPackageParameters(@org.kohsuke.stapler.AncestorInPath hudson.model.Job<?, ?> job, @QueryParameter String serverName) {
            if (serverName == null || serverName.trim().isEmpty()) {
                return FormValidation.warning("No server selected");
            }

            try {
                java.util.List<hudson.model.ParameterDefinition> params = io.modelcontextprotocol.jenkins.McpxPackageParameterExtractor.extractParameters(job, serverName);
                if (params.isEmpty()) {
                    return FormValidation.ok("No parameters found in packages for server: " + serverName);
                }

                StringBuilder sb = new StringBuilder("Found " + params.size() + " parameter(s) in packages:\n\n");
                for (hudson.model.ParameterDefinition param : params) {
                    String name = param.getName();
                    String description = param.getDescription();
                    String defaultValue = "";
                    if (param instanceof hudson.model.StringParameterDefinition) {
                        defaultValue = ((hudson.model.StringParameterDefinition) param).getDefaultValue();
                    }
                    sb.append("• ").append(name);
                    if (description != null && !description.isEmpty()) {
                        sb.append(": ").append(description);
                    }
                    if (defaultValue != null && !defaultValue.isEmpty()) {
                        sb.append(" (default: ").append(defaultValue).append(")");
                    }
                    sb.append("\n");
                }
                sb.append("\nNote: Add these as String parameters in your job configuration to use them in 'Build with Parameters'.");
                sb.append(" Default values will be automatically set when MCP_SERVER is configured.");
                return FormValidation.ok(sb.toString());
            } catch (Exception e) {
                return FormValidation.error("Failed to get package parameters: " + e.getMessage());
            }
        }

        @POST
        public FormValidation doProbeServers(@org.kohsuke.stapler.AncestorInPath hudson.model.Job<?, ?> job) {
            io.modelcontextprotocol.jenkins.McpxGlobalConfiguration cfg = io.modelcontextprotocol.jenkins.McpxGlobalConfiguration.get();
            io.modelcontextprotocol.jenkins.McpxJobProperty jp = job != null ? job.getProperty(io.modelcontextprotocol.jenkins.McpxJobProperty.class) : null;

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
            // Note: Only AbstractProject has getAssignedLabel(), pipeline jobs handle labels differently
            java.util.LinkedHashMap<hudson.model.Node, String> candidates = new java.util.LinkedHashMap<>();
            jenkins.model.Jenkins j = jenkins.model.Jenkins.get();

            if (job != null && job instanceof AbstractProject) {
                AbstractProject<?, ?> project = (AbstractProject<?, ?>) job;
                Label assigned = project.getAssignedLabel();
                if (assigned != null) {
                    for (hudson.model.Node n : assigned.getNodes()) {
                        if (n != null && n.toComputer() != null && n.toComputer().isOnline()) {
                            candidates.put(n, n.getNodeName());
                        }
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
                hudson.model.Job<?, ?> job = null;
                org.kohsuke.stapler.StaplerRequest req = Stapler.getCurrentRequest();
                if (req != null) {
                    job = req.findAncestorObject(hudson.model.Job.class);
                }
                hudson.util.ListBoxModel model = (job != null)
                        ? new McpxRegistryClient().fetchServers(job)
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
