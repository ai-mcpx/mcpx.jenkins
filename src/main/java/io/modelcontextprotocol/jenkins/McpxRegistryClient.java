package io.modelcontextprotocol.jenkins;

import hudson.Util;
import hudson.util.ListBoxModel;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

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
            LOGGER.log(Level.WARNING, "Failed to fetch servers via mcpx-cli", e);
            return errorModel("Failed to fetch via mcpx-cli: " + e.getMessage());
        }
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
                        if (name == null || name.isEmpty()) continue;
                        if (seen.add(name)) {
                            String desc = s.optString("description");
                                        String label = desc != null && !desc.isEmpty() ? name + " — " + desc : name;
                            m.add(label, name);
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
                    if (name == null || name.isEmpty()) continue;
                    if (seen.add(name)) {
                        m.add(name, name);
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
                    if (name != null && !name.isEmpty()) m2.add(name, name);
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
}
