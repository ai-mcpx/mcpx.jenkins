package io.modelcontextprotocol.jenkins.parameters;

import hudson.EnvVars;
import hudson.model.ParameterValue;
import hudson.model.Run;
import net.sf.json.JSONObject;
import org.junit.Test;
import org.kohsuke.stapler.StaplerRequest;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Tests for McpxServerParameterValue to verify parameter value functionality.
 */
public class McpxServerParameterValueTest {

    @Test
    public void testParameterValueInstantiation() {
        McpxServerParameterValue value = new McpxServerParameterValue("MCP_SERVER", "test-server");
        assertNotNull(value);
        assertEquals("MCP_SERVER", value.getName());
        assertEquals("test-server", value.getServerName());
        assertEquals("test-server", value.getValue());
    }

    @Test
    public void testParameterValueWithEmptyServerName() {
        McpxServerParameterValue value = new McpxServerParameterValue("MCP_SERVER", "");
        assertNotNull(value);
        assertEquals("", value.getServerName());
        assertEquals("", value.getValue());
    }

    @Test
    public void testParameterValueWithNullServerName() {
        McpxServerParameterValue value = new McpxServerParameterValue("MCP_SERVER", null);
        assertNotNull(value);
        assertNull(value.getServerName());
        assertEquals("", value.getValue()); // getValue() should return empty string for null
    }

    @Test
    public void testNoArgumentConstructor() {
        McpxServerParameterValue value = new McpxServerParameterValue();
        assertNotNull(value);
        assertEquals("", value.getName());
        assertEquals("", value.getServerName());
        assertEquals("", value.getValue());
    }

    @Test
    public void testParameterValueFromJSON() {
        StaplerRequest req = mock(StaplerRequest.class);
        JSONObject jo = new JSONObject();
        jo.put("name", "MCP_SERVER");
        jo.put("value", "json-server");

        McpxServerParameterValue value = new McpxServerParameterValue(req, jo);
        assertNotNull(value);
        assertEquals("MCP_SERVER", value.getName());
        assertEquals("json-server", value.getServerName());
        assertEquals("json-server", value.getValue());
    }

    @Test
    public void testParameterValueFromJSONWithServerName() {
        StaplerRequest req = mock(StaplerRequest.class);
        JSONObject jo = new JSONObject();
        jo.put("name", "MCP_SERVER");
        jo.put("serverName", "server-name-value");

        McpxServerParameterValue value = new McpxServerParameterValue(req, jo);
        assertNotNull(value);
        assertEquals("MCP_SERVER", value.getName());
        assertEquals("server-name-value", value.getServerName());
    }

    @Test
    public void testParameterValueFromJSONWithEmptyValue() {
        StaplerRequest req = mock(StaplerRequest.class);
        JSONObject jo = new JSONObject();
        jo.put("name", "MCP_SERVER");
        jo.put("value", "");

        McpxServerParameterValue value = new McpxServerParameterValue(req, jo);
        assertNotNull(value);
        assertEquals("MCP_SERVER", value.getName());
        assertEquals("", value.getServerName());
        assertEquals("", value.getValue());
    }

    @Test
    public void testBuildEnvironment() {
        McpxServerParameterValue value = new McpxServerParameterValue("MCP_SERVER", "test-server");
        Run<?, ?> build = mock(Run.class);
        when(build.getNumber()).thenReturn(1);
        EnvVars env = new EnvVars();

        value.buildEnvironment(build, env);

        assertEquals("test-server", env.get("MCP_SERVER"));
    }

    @Test
    public void testBuildEnvironmentWithEmptyValue() {
        McpxServerParameterValue value = new McpxServerParameterValue("MCP_SERVER", "");
        Run<?, ?> build = mock(Run.class);
        when(build.getNumber()).thenReturn(1);
        EnvVars env = new EnvVars();

        value.buildEnvironment(build, env);

        assertEquals("", env.get("MCP_SERVER"));
    }

    @Test
    public void testBuildEnvironmentWithNullValue() {
        McpxServerParameterValue value = new McpxServerParameterValue("MCP_SERVER", null);
        Run<?, ?> build = mock(Run.class);
        when(build.getNumber()).thenReturn(1);
        EnvVars env = new EnvVars();

        value.buildEnvironment(build, env);

        assertEquals("", env.get("MCP_SERVER")); // Should set empty string, not null
    }

    @Test
    public void testCreateVariableResolver() {
        McpxServerParameterValue value = new McpxServerParameterValue("MCP_SERVER", "test-server");
        Run<?, ?> build = mock(Run.class);

        hudson.util.VariableResolver<String> resolver = value.createVariableResolver(build);
        assertNotNull(resolver);
        assertEquals("test-server", resolver.resolve("MCP_SERVER"));
        assertNull(resolver.resolve("OTHER_VAR"));
    }

    @Test
    public void testToString() {
        McpxServerParameterValue value = new McpxServerParameterValue("MCP_SERVER", "test-server");
        String str = value.toString();
        assertNotNull(str);
        assertTrue(str.contains("MCP_SERVER"));
        assertTrue(str.contains("test-server"));
    }

    @Test
    public void testParameterValueExtendsParameterValue() {
        McpxServerParameterValue value = new McpxServerParameterValue("MCP_SERVER", "test-server");
        assertTrue(value instanceof ParameterValue);
    }

    @Test
    public void testGetValueReturnsEmptyStringForNull() {
        McpxServerParameterValue value = new McpxServerParameterValue("MCP_SERVER", null);
        assertEquals("", value.getValue());
    }

    @Test
    public void testGetValueReturnsServerName() {
        McpxServerParameterValue value = new McpxServerParameterValue("MCP_SERVER", "my-server");
        assertEquals("my-server", value.getValue());
    }
}

