package io.modelcontextprotocol.jenkins;

import hudson.util.ListBoxModel;
import org.junit.Test;

import static org.junit.Assert.*;

public class McpxRegistryClientTest {
    @Test
    public void parsesServersInObjectEnvelope() {
        String json = "{\n  \"servers\": [\n    {\"name\": \"io.modelcontextprotocol/filesystem\", \"description\": \"FS server\"},\n    {\"name\": \"io.modelcontextprotocol/git\"}\n  ]\n}";
        McpxRegistryClient c = new McpxRegistryClient();
        ListBoxModel m = c.parseServersJson(json);
        assertEquals(2, m.size());
        assertEquals("io.modelcontextprotocol/filesystem — FS server", m.get(0).name);
        assertEquals("io.modelcontextprotocol/filesystem", m.get(0).value);
    }

    @Test
    public void parsesArrayFallback() {
        String json = "[ {\"name\": \"a/b\"}, {\"name\": \"c/d\"} ]";
        McpxRegistryClient c = new McpxRegistryClient();
        ListBoxModel m = c.parseServersJson(json);
        assertEquals(2, m.size());
        assertEquals("a/b", m.get(0).name);
        assertEquals("a/b", m.get(0).value);
    }

    @Test
    public void parsesCLIJsonOutput() {
        // Test mcpx-cli JSON format (same as API but validates compatibility)
        String json = "{\"servers\":[{\"name\":\"io.modelcontextprotocol/test\",\"description\":\"Test server\"}],\"metadata\":{\"count\":1}}";
        McpxRegistryClient c = new McpxRegistryClient();
        ListBoxModel m = c.parseServersJson(json);
        assertEquals(1, m.size());
        assertEquals("io.modelcontextprotocol/test — Test server", m.get(0).name);
    }
}
