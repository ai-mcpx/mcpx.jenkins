package io.modelcontextprotocol.jenkins;

import org.junit.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.Assert.*;

public class McpxCliClientTest {
    @Test
    public void testVersionCommandConstruction() throws Exception {
        // Test that the client can be instantiated
        McpxCliClient client = new McpxCliClient("echo");
        assertNotNull(client);
    }

    @Test
    public void testCliPathDefault() throws Exception {
        McpxCliClient client = new McpxCliClient(null);
        // Should not throw, uses default "mcpx-cli"
        assertNotNull(client);
    }
}
