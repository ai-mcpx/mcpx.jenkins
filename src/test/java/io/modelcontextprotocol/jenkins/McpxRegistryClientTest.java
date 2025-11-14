package io.modelcontextprotocol.jenkins;

import hudson.model.Job;
import hudson.util.ListBoxModel;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for McpxRegistryClient.
 * Note: fetchServers() now accepts Job instead of AbstractProject to support pipeline jobs.
 */
public class McpxRegistryClientTest {
    @Test
    public void parsesServersInObjectEnvelope() {
        String json = "{\n  \"servers\": [\n    {\"name\": \"io.modelcontextprotocol/filesystem\", \"description\": \"FS server\"},\n    {\"name\": \"io.modelcontextprotocol/git\"}\n  ]\n}";
        McpxRegistryClient c = new McpxRegistryClient();
        ListBoxModel m = c.parseServersJson(json);
        assertEquals(2, m.size());
        assertEquals("filesystem", m.get(0).name);
        assertEquals("io.modelcontextprotocol/filesystem", m.get(0).value);
    }

    @Test
    public void parsesArrayFallback() {
        String json = "[ {\"name\": \"a/b\"}, {\"name\": \"c/d\"} ]";
        McpxRegistryClient c = new McpxRegistryClient();
        ListBoxModel m = c.parseServersJson(json);
        assertEquals(2, m.size());
        assertEquals("b", m.get(0).name);
        assertEquals("a/b", m.get(0).value);
    }

    @Test
    public void parsesCLIJsonOutput() {
        // Test mcpx-cli JSON format (same as API but validates compatibility)
        String json = "{\"servers\":[{\"name\":\"io.modelcontextprotocol/test\",\"description\":\"Test server\"}],\"metadata\":{\"count\":1}}";
        McpxRegistryClient c = new McpxRegistryClient();
        ListBoxModel m = c.parseServersJson(json);
        assertEquals(1, m.size());
        assertEquals("test", m.get(0).name);
    }

    @Test
    public void testParseServerDetailsWithPackages() {
        String json = "{\n" +
            "  \"name\": \"io.modelcontextprotocol/test-server\",\n" +
            "  \"packages\": [\n" +
            "    {\n" +
            "      \"runtimeArguments\": [\n" +
            "        {\"type\": \"named\", \"name\": \"--port\", \"default\": \"8005\"}\n" +
            "      ],\n" +
            "      \"environmentVariables\": [\n" +
            "        {\"name\": \"MCP_LOG_LEVEL\", \"default\": \"INFO\"}\n" +
            "      ]\n" +
            "    }\n" +
            "  ]\n" +
            "}";
        McpxRegistryClient c = new McpxRegistryClient();
        net.sf.json.JSONObject result = c.parseServerDetails(json);
        assertNotNull(result);
        assertTrue("Should have packages", result.has("packages"));
        net.sf.json.JSONArray packages = result.getJSONArray("packages");
        assertEquals(1, packages.size());

        // Verify package structure
        net.sf.json.JSONObject packageObj = packages.getJSONObject(0);
        assertTrue("Package should have runtimeArguments", packageObj.has("runtimeArguments"));
        assertTrue("Package should have environmentVariables", packageObj.has("environmentVariables"));
    }

    @Test
    public void testParseServerDetailsWithNamedArgumentValueHint() {
        // Test parsing of named arguments with valueHint (like -p with valueHint "port_mapping")
        String json = "{\n" +
            "  \"packages\": [\n" +
            "    {\n" +
            "      \"runtimeArguments\": [\n" +
            "        {\n" +
            "          \"type\": \"named\",\n" +
            "          \"name\": \"-p\",\n" +
            "          \"valueHint\": \"port_mapping\",\n" +
            "          \"default\": \"8004:8000\"\n" +
            "        }\n" +
            "      ]\n" +
            "    }\n" +
            "  ]\n" +
            "}";
        McpxRegistryClient c = new McpxRegistryClient();
        net.sf.json.JSONObject result = c.parseServerDetails(json);
        assertNotNull(result);
        assertTrue("Should have packages", result.has("packages"));
        net.sf.json.JSONArray packages = result.getJSONArray("packages");
        net.sf.json.JSONObject packageObj = packages.getJSONObject(0);
        net.sf.json.JSONArray runtimeArgs = packageObj.getJSONArray("runtimeArguments");
        net.sf.json.JSONObject arg = runtimeArgs.getJSONObject(0);
        assertEquals("named", arg.getString("type"));
        assertEquals("-p", arg.getString("name"));
        assertEquals("port_mapping", arg.getString("valueHint"));
        assertEquals("8004:8000", arg.getString("default"));
    }

    @Test
    public void testParseServerDetailsWithArray() {
        String json = "[{\"name\": \"test\", \"packages\": []}]";
        McpxRegistryClient c = new McpxRegistryClient();
        net.sf.json.JSONObject result = c.parseServerDetails(json);
        assertNotNull(result);
        assertTrue("Should parse array format", result.has("name") || result.has("packages"));
    }

    @Test
    public void testParseServerDetailsWithEmptyString() {
        McpxRegistryClient c = new McpxRegistryClient();
        net.sf.json.JSONObject result = c.parseServerDetails("");
        assertNotNull(result);
        assertTrue("Should return empty JSONObject", result.isEmpty());
    }

    @Test
    public void testParseServerDetailsWithNull() {
        McpxRegistryClient c = new McpxRegistryClient();
        net.sf.json.JSONObject result = c.parseServerDetails(null);
        assertNotNull(result);
        assertTrue("Should return empty JSONObject", result.isEmpty());
    }
}
