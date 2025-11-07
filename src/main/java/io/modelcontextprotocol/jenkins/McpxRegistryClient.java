package io.modelcontextprotocol.jenkins;

import hudson.FilePath;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.Job;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.labels.LabelAtom;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class McpxRegistryClient {
    private static final Logger LOGGER = Logger.getLogger(McpxRegistryClient.class.getName());

    public ListBoxModel fetchServers() {
        McpxGlobalConfiguration cfg = McpxGlobalConfiguration.get();
        String baseUrl = cfg != null && Util.fixEmptyAndTrim(cfg.getRegistryBaseUrl()) != null
                ? Util.fixEmptyAndTrim(cfg.getRegistryBaseUrl())
                : "https://registry.modelcontextprotocol.io";

        // CLI-only: Use mcpx-cli to fetch servers; do not fallback to HTTP
        try {
            String cliPath = (cfg != null) ? cfg.getCliPath() : null;
            if (Util.fixEmptyAndTrim(cliPath) == null) {
                return errorModel("mcpx-cli path not configured. Configure in Manage Jenkins > System > MCPX CLI.");
            }

            McpxCliClient cliClient = new McpxCliClient(cliPath);
            // Always attempt anonymous login to initialize CLI auth/session
            try {
                cliClient.login(baseUrl, "anonymous");
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "mcpx-cli anonymous login failed; continuing to list servers", e);
            }
            String jsonOutput = cliClient.listServers(baseUrl);
            return parseServersJson(jsonOutput);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Local mcpx-cli fetch failed on controller; attempting agent fallback", e);
            // Try any online agent as a fallback
            try {
                return fetchServersOnAnyAgent();
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Failed to fetch servers via mcpx-cli", ex);
                return errorModel("Failed to fetch via mcpx-cli: " + ex.getMessage());
            }
        }
    }

    public ListBoxModel fetchServers(Job<?, ?> job) {
        // Resolve effective settings: prefer job overrides, then global, then defaults
        String baseUrl;
        String cliPath;
        McpxGlobalConfiguration cfg = McpxGlobalConfiguration.get();
        McpxJobProperty jp = job != null ? job.getProperty(McpxJobProperty.class) : null;

        if (jp != null && Util.fixEmptyAndTrim(jp.getRegistryBaseUrl()) != null) {
            baseUrl = Util.fixEmptyAndTrim(jp.getRegistryBaseUrl());
        } else {
            baseUrl = (cfg != null && Util.fixEmptyAndTrim(cfg.getRegistryBaseUrl()) != null)
                    ? Util.fixEmptyAndTrim(cfg.getRegistryBaseUrl())
                    : "https://registry.modelcontextprotocol.io";
        }

        if (jp != null && Util.fixEmptyAndTrim(jp.getCliPath()) != null) {
            cliPath = Util.fixEmptyAndTrim(jp.getCliPath());
        } else {
            cliPath = (cfg != null) ? cfg.getCliPath() : null;
        }

        if (Util.fixEmptyAndTrim(cliPath) == null) {
            return errorModel("mcpx-cli path not configured. Configure in Manage Jenkins > System > MCPX CLI or job overrides.");
        }

        // First, try an agent matching the job's assigned label (respect 'Restrict where this project can be run')
        // Note: Only AbstractProject has getAssignedLabel(), pipeline jobs handle labels differently
        try {
            Node target = null;
            if (job != null && job instanceof AbstractProject) {
                AbstractProject<?, ?> project = (AbstractProject<?, ?>) job;
                Label assigned = project.getAssignedLabel();
                if (assigned != null) {
                    for (Node n : assigned.getNodes()) {
                        if (n != null && n.toComputer() != null && n.toComputer().isOnline()) {
                            target = n;
                            break;
                        }
                    }
                }
            }
            if (target != null) {
                FilePath root = target.getRootPath();
                if (root != null) {
                    String json = root.act(new RemoteServersCallable(cliPath, baseUrl));
                    return parseServersJson(json);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Labeled agent fetch failed: " + e.getMessage(), e);
        }

        // Next, try any online agent
        try {
            return fetchServersOnAnyAgent();
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Any-agent fetch failed: " + e.getMessage(), e);
        }

        // Finally, try locally on controller as last resort
        try {
            McpxCliClient cliClient = new McpxCliClient(cliPath);
            try { cliClient.login(baseUrl, "anonymous"); } catch (Exception ignore) {}
            String json = cliClient.listServers(baseUrl);
            return parseServersJson(json);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Controller local fetch failed: " + e.getMessage(), e);
            return errorModel("Failed to fetch via mcpx-cli on controller: " + e.getMessage());
        }
    }

    private ListBoxModel fetchServersOnAnyAgent() throws Exception {
        Jenkins j = Jenkins.get();
        McpxGlobalConfiguration cfg = McpxGlobalConfiguration.get();
        String baseUrl = cfg != null && Util.fixEmptyAndTrim(cfg.getRegistryBaseUrl()) != null
                ? Util.fixEmptyAndTrim(cfg.getRegistryBaseUrl())
                : "https://registry.modelcontextprotocol.io";
        String cliPath = (cfg != null) ? cfg.getCliPath() : null;
        if (Util.fixEmptyAndTrim(cliPath) == null) {
            return errorModel("mcpx-cli path not configured. Configure in Manage Jenkins > System > MCPX CLI.");
        }

        for (Node n : j.getNodes()) {
            if (n != null && n.toComputer() != null && n.toComputer().isOnline()) {
                FilePath root = n.getRootPath();
                if (root == null) continue;
                try {
                    String json = root.act(new RemoteServersCallable(cliPath, baseUrl));
                    return parseServersJson(json);
                } catch (Exception ex) {
                    LOGGER.log(Level.FINE, "Agent " + n.getNodeName() + " fetch failed: " + ex.getMessage(), ex);
                }
            }
        }
        throw new IOException("No online agents could fetch servers; ensure mcpx-cli is installed on controller or an agent.");
    }

    ListBoxModel parseServersJson(String jsonText) {
        ListBoxModel m = new ListBoxModel();
        if (jsonText == null || jsonText.trim().isEmpty()) {
            m.add("<no servers>", "");
            return m;
        }
        try {
            Set<String> seen = new HashSet<>();
            // Primary format: { "servers": [ {"name": "...", "description": "..."}, ...] }
            Object rootObj = net.sf.json.JSONSerializer.toJSON(jsonText);
            if (rootObj instanceof JSONObject) {
                JSONObject root = (JSONObject) rootObj;
                if (root.has("servers")) {
                    JSONArray arr = root.getJSONArray("servers");
                    for (int i = 0; i < arr.size(); i++) {
                        JSONObject s = arr.getJSONObject(i);
                        String name = s.optString("name");
                        if ((name == null || name.isEmpty()) && s.has("server")) {
                            JSONObject inner = s.getJSONObject("server");
                            name = inner != null ? inner.optString("name") : name;
                        }
                        if (name == null || name.isEmpty()) continue;
                        if (seen.add(name)) {
                            String shortName = name;
                            int idx = name.lastIndexOf('/');
                            if (idx >= 0 && idx < name.length() - 1) {
                                shortName = name.substring(idx + 1);
                            }
                            m.add(shortName, name);
                        }
                    }
                } else {
                    m.add("<unrecognized registry response>", "");
                }
            } else if (rootObj instanceof JSONArray) {
                // Fallback: [ {"name": "..."} ]
                JSONArray arr = (JSONArray) rootObj;
                for (int i = 0; i < arr.size(); i++) {
                    JSONObject s = arr.getJSONObject(i);
                    String name = s.optString("name");
                    if ((name == null || name.isEmpty()) && s.has("server")) {
                        JSONObject inner = s.getJSONObject("server");
                        name = inner != null ? inner.optString("name") : name;
                    }
                    if (name == null || name.isEmpty()) continue;
                    if (seen.add(name)) {
                        String shortName = name;
                        int idx = name.lastIndexOf('/');
                        if (idx >= 0 && idx < name.length() - 1) {
                            shortName = name.substring(idx + 1);
                        }
                        m.add(shortName, name);
                    }
                }
            } else {
                m.add("<unrecognized registry response>", "");
            }
            if (m.isEmpty()) m.add("<no servers>", "");
            return m;
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Failed to parse servers JSON", ex);
            // Try a very simple fallback: look for 'name' fields
            try {
                JSONArray arr = JSONArray.fromObject(jsonText);
                ListBoxModel m2 = new ListBoxModel();
                for (int i = 0; i < arr.size(); i++) {
                    JSONObject s = arr.getJSONObject(i);
                    String name = s.optString("name");
                    if ((name == null || name.isEmpty()) && s.has("server")) {
                        JSONObject inner = s.getJSONObject("server");
                        name = inner != null ? inner.optString("name") : name;
                    }
                    if (name != null && !name.isEmpty()) {
                        String shortName = name;
                        int idx = name.lastIndexOf('/');
                        if (idx >= 0 && idx < name.length() - 1) {
                            shortName = name.substring(idx + 1);
                        }
                        m2.add(shortName, name);
                    }
                }
                if (m2.isEmpty()) m2.add("<parse error>", "");
                return m2;
            } catch (Exception ignored) {
                ListBoxModel err = new ListBoxModel();
                err.add("<parse error>", "");
                return err;
            }
        }
    }

    private ListBoxModel errorModel(String message) {
        LOGGER.log(Level.WARNING, "Registry fetch error: " + message);
        ListBoxModel m = new ListBoxModel();
        m.add("<" + message + ">", "");
        return m;
    }

    // Remote callable to fetch servers via mcpx-cli on an agent
    private static class RemoteServersCallable implements FilePath.FileCallable<String> {
        private final String rawCliPath;
        private final String baseUrl;

        RemoteServersCallable(String rawCliPath, String baseUrl) {
            this.rawCliPath = rawCliPath;
            this.baseUrl = baseUrl;
        }

        @Override
        public String invoke(java.io.File f, hudson.remoting.VirtualChannel channel) throws IOException, InterruptedException {
            String path = expandHome(rawCliPath);

            // Login anonymous (best-effort)
            ProcessBuilder loginPb = new ProcessBuilder(path, "--base-url=" + baseUrl, "login", "--method", "anonymous");
            Process loginProc = loginPb.start();
            int loginCode = loginProc.waitFor();
            // Ignore non-zero login; we'll still attempt to list servers

            // List servers
            ProcessBuilder pb = new ProcessBuilder(path, "--base-url=" + baseUrl, "servers", "--json");
            Process proc = pb.start();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
                }
            }
            int code = proc.waitFor();
            if (code != 0) {
                // capture stderr for diagnostics
                ByteArrayOutputStream err = new ByteArrayOutputStream();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getErrorStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        err.write((line + "\n").getBytes(StandardCharsets.UTF_8));
                    }
                }
                throw new IOException("mcpx-cli servers failed with exit code " + code + ": " + err.toString(StandardCharsets.UTF_8));
            }
            return out.toString(StandardCharsets.UTF_8);
        }

        @Override
        public void checkRoles(org.jenkinsci.remoting.RoleChecker checker) throws SecurityException {
            // default
        }

        private static String expandHome(String path) {
            if (path != null && path.startsWith("~/")) {
                String home = System.getProperty("user.home");
                if (home != null && !home.isEmpty()) {
                    return home + path.substring(1);
                }
            }
            return path;
        }
    }
}
