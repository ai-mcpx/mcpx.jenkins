package io.modelcontextprotocol.jenkins.parameters;

import hudson.model.ParameterValue;
import net.sf.json.JSONObject;
import org.junit.Test;
import org.kohsuke.stapler.StaplerRequest;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Tests for McpxServerParameterDefinition to verify parameter definition functionality.
 */
public class McpxServerParameterDefinitionTest {

    @Test
    public void testParameterDefinitionInstantiation() {
        McpxServerParameterDefinition def = new McpxServerParameterDefinition(
            "MCP_SERVER", "Select an MCP server", "default-server"
        );
        assertNotNull(def);
        assertEquals("MCP_SERVER", def.getName());
        assertEquals("Select an MCP server", def.getDescription());
        assertEquals("default-server", def.getDefaultServer());
    }

    @Test
    public void testParameterDefinitionWithEmptyDefaultServer() {
        McpxServerParameterDefinition def = new McpxServerParameterDefinition(
            "MCP_SERVER", "Select an MCP server", ""
        );
        assertNotNull(def);
        assertEquals("", def.getDefaultServer());
    }

    @Test
    public void testParameterDefinitionWithNullDefaultServer() {
        McpxServerParameterDefinition def = new McpxServerParameterDefinition(
            "MCP_SERVER", "Select an MCP server", null
        );
        assertNotNull(def);
        assertNull(def.getDefaultServer());
    }

    @Test
    public void testCreateValueWithString() {
        McpxServerParameterDefinition def = new McpxServerParameterDefinition(
            "MCP_SERVER", "Select an MCP server", "default-server"
        );
        ParameterValue value = def.createValue("test-server");
        assertNotNull(value);
        assertTrue(value instanceof McpxServerParameterValue);
        McpxServerParameterValue mcpValue = (McpxServerParameterValue) value;
        assertEquals("MCP_SERVER", mcpValue.getName());
        assertEquals("test-server", mcpValue.getServerName());
        assertEquals("test-server", mcpValue.getValue());
    }

    @Test
    public void testCreateValueWithEmptyStringUsesDefault() {
        McpxServerParameterDefinition def = new McpxServerParameterDefinition(
            "MCP_SERVER", "Select an MCP server", "default-server"
        );
        ParameterValue value = def.createValue("");
        assertNotNull(value);
        assertTrue(value instanceof McpxServerParameterValue);
        McpxServerParameterValue mcpValue = (McpxServerParameterValue) value;
        assertEquals("default-server", mcpValue.getServerName());
        assertEquals("default-server", mcpValue.getValue());
    }

    @Test
    public void testCreateValueWithNullUsesDefault() {
        McpxServerParameterDefinition def = new McpxServerParameterDefinition(
            "MCP_SERVER", "Select an MCP server", "default-server"
        );
        ParameterValue value = def.createValue((String) null);
        assertNotNull(value);
        assertTrue(value instanceof McpxServerParameterValue);
        McpxServerParameterValue mcpValue = (McpxServerParameterValue) value;
        assertEquals("default-server", mcpValue.getServerName());
        assertEquals("default-server", mcpValue.getValue());
    }

    @Test
    public void testCreateValueWithEmptyDefaultServer() {
        McpxServerParameterDefinition def = new McpxServerParameterDefinition(
            "MCP_SERVER", "Select an MCP server", ""
        );
        ParameterValue value = def.createValue("");
        assertNotNull(value);
        assertTrue(value instanceof McpxServerParameterValue);
        McpxServerParameterValue mcpValue = (McpxServerParameterValue) value;
        assertEquals("", mcpValue.getServerName());
        assertEquals("", mcpValue.getValue());
    }

    @Test
    public void testCreateValueWithStaplerRequestAndJSON() {
        McpxServerParameterDefinition def = new McpxServerParameterDefinition(
            "MCP_SERVER", "Select an MCP server", "default-server"
        );

        StaplerRequest req = mock(StaplerRequest.class);
        JSONObject jo = new JSONObject();
        jo.put("value", "json-server");

        ParameterValue value = def.createValue(req, jo);
        assertNotNull(value);
        assertTrue(value instanceof McpxServerParameterValue);
        McpxServerParameterValue mcpValue = (McpxServerParameterValue) value;
        assertEquals("json-server", mcpValue.getServerName());
        assertEquals("json-server", mcpValue.getValue());
    }

    @Test
    public void testCreateValueWithStaplerRequestAndJSONUsingParameterName() {
        McpxServerParameterDefinition def = new McpxServerParameterDefinition(
            "MCP_SERVER", "Select an MCP server", "default-server"
        );

        StaplerRequest req = mock(StaplerRequest.class);
        JSONObject jo = new JSONObject();
        jo.put("MCP_SERVER", "param-name-server");

        ParameterValue value = def.createValue(req, jo);
        assertNotNull(value);
        assertTrue(value instanceof McpxServerParameterValue);
        McpxServerParameterValue mcpValue = (McpxServerParameterValue) value;
        assertEquals("param-name-server", mcpValue.getServerName());
    }

    @Test
    public void testCreateValueWithStaplerRequestParameter() {
        McpxServerParameterDefinition def = new McpxServerParameterDefinition(
            "MCP_SERVER", "Select an MCP server", "default-server"
        );

        StaplerRequest req = mock(StaplerRequest.class);
        when(req.getParameter("value")).thenReturn("request-value-server");

        ParameterValue value = def.createValue(req, null);
        assertNotNull(value);
        assertTrue(value instanceof McpxServerParameterValue);
        McpxServerParameterValue mcpValue = (McpxServerParameterValue) value;
        assertEquals("request-value-server", mcpValue.getServerName());
    }

    @Test
    public void testCreateValueWithStaplerRequestParameterName() {
        McpxServerParameterDefinition def = new McpxServerParameterDefinition(
            "MCP_SERVER", "Select an MCP server", "default-server"
        );

        StaplerRequest req = mock(StaplerRequest.class);
        when(req.getParameter("MCP_SERVER")).thenReturn("param-name-value");

        ParameterValue value = def.createValue(req, null);
        assertNotNull(value);
        assertTrue(value instanceof McpxServerParameterValue);
        McpxServerParameterValue mcpValue = (McpxServerParameterValue) value;
        assertEquals("param-name-value", mcpValue.getServerName());
    }

    @Test
    public void testGetDefaultParameterValue() {
        McpxServerParameterDefinition def = new McpxServerParameterDefinition(
            "MCP_SERVER", "Select an MCP server", "default-server"
        );
        ParameterValue value = def.getDefaultParameterValue();
        assertNotNull(value);
        assertTrue(value instanceof McpxServerParameterValue);
        McpxServerParameterValue mcpValue = (McpxServerParameterValue) value;
        assertEquals("default-server", mcpValue.getServerName());
        assertEquals("default-server", mcpValue.getValue());
    }

    @Test
    public void testGetDefaultParameterValueWithEmptyDefault() {
        McpxServerParameterDefinition def = new McpxServerParameterDefinition(
            "MCP_SERVER", "Select an MCP server", ""
        );
        ParameterValue value = def.getDefaultParameterValue();
        assertNotNull(value);
        assertTrue(value instanceof McpxServerParameterValue);
        McpxServerParameterValue mcpValue = (McpxServerParameterValue) value;
        assertEquals("", mcpValue.getServerName());
        assertEquals("", mcpValue.getValue());
    }

    @Test
    public void testGetDefaultParameterValueWithNullDefault() {
        McpxServerParameterDefinition def = new McpxServerParameterDefinition(
            "MCP_SERVER", "Select an MCP server", null
        );
        ParameterValue value = def.getDefaultParameterValue();
        assertNotNull(value);
        assertTrue(value instanceof McpxServerParameterValue);
        McpxServerParameterValue mcpValue = (McpxServerParameterValue) value;
        assertEquals("", mcpValue.getServerName());
        assertEquals("", mcpValue.getValue());
    }

    @Test
    public void testDescriptorDisplayName() {
        McpxServerParameterDefinition.DescriptorImpl descriptor =
            new McpxServerParameterDefinition.DescriptorImpl();
        assertEquals("MCP Servers from MCPX Registry", descriptor.getDisplayName());
    }

    @Test
    public void testParameterDefinitionExtendsSimpleParameterDefinition() {
        McpxServerParameterDefinition def = new McpxServerParameterDefinition(
            "MCP_SERVER", "Select an MCP server", "default-server"
        );
        assertTrue(def instanceof hudson.model.SimpleParameterDefinition);
    }
}

