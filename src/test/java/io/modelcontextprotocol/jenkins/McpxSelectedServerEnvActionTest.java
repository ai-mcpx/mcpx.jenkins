package io.modelcontextprotocol.jenkins;

import hudson.EnvVars;
import hudson.model.Job;
import hudson.model.Run;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

/**
 * Tests for McpxSelectedServerEnvAction.
 */
@SuppressWarnings("unchecked")
public class McpxSelectedServerEnvActionTest {

    @Test
    public void testBuildEnvironmentSetsMCP_SERVER() {
        Run<?, ?> run = Mockito.mock(Run.class);
        Job<?, ?> job = Mockito.mock(Job.class);
        when(run.getParent()).thenReturn((Job) job);

        EnvVars env = new EnvVars();
        McpxSelectedServerEnvAction action = new McpxSelectedServerEnvAction("test-server");

        action.buildEnvironment(run, env);

        assertEquals("test-server", env.get("MCP_SERVER"));
    }

    @Test
    public void testBuildEnvironmentRespectsExistingMCP_SERVER() {
        Run<?, ?> run = Mockito.mock(Run.class);
        Job<?, ?> job = Mockito.mock(Job.class);
        when(run.getParent()).thenReturn((Job) job);

        EnvVars env = new EnvVars();
        env.put("MCP_SERVER", "existing-server");

        McpxSelectedServerEnvAction action = new McpxSelectedServerEnvAction("test-server");
        action.buildEnvironment(run, env);

        // Should not override existing value
        assertEquals("existing-server", env.get("MCP_SERVER"));
    }

    @Test
    public void testBuildEnvironmentWithEmptyServerName() {
        Run<?, ?> run = Mockito.mock(Run.class);
        Job<?, ?> job = Mockito.mock(Job.class);
        when(run.getParent()).thenReturn((Job) job);

        EnvVars env = new EnvVars();
        McpxSelectedServerEnvAction action = new McpxSelectedServerEnvAction("");

        action.buildEnvironment(run, env);

        // Should not set MCP_SERVER if server name is empty
        assertNull(env.get("MCP_SERVER"));
    }

    @Test
    public void testBuildEnvironmentWithNullServerName() {
        Run<?, ?> run = Mockito.mock(Run.class);
        Job<?, ?> job = Mockito.mock(Job.class);
        when(run.getParent()).thenReturn((Job) job);

        EnvVars env = new EnvVars();
        McpxSelectedServerEnvAction action = new McpxSelectedServerEnvAction(null);

        action.buildEnvironment(run, env);

        // Should not set MCP_SERVER if server name is null
        assertNull(env.get("MCP_SERVER"));
    }

    @Test
    public void testBuildEnvironmentWithNullJob() {
        Run<?, ?> run = Mockito.mock(Run.class);
        when(run.getParent()).thenReturn(null);

        EnvVars env = new EnvVars();
        McpxSelectedServerEnvAction action = new McpxSelectedServerEnvAction("test-server");

        // Should not throw exception
        action.buildEnvironment(run, env);

        // MCP_SERVER should still be set
        assertEquals("test-server", env.get("MCP_SERVER"));
    }
}
