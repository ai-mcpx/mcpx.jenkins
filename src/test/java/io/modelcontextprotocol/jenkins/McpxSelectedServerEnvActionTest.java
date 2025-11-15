package io.modelcontextprotocol.jenkins;

import hudson.EnvVars;
import hudson.model.Job;
import hudson.model.Run;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

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

    @Test
    public void testBuildEnvironmentSetsMCPX_REGISTRY_BASE_URLWithDefault() {
        Run<?, ?> run = Mockito.mock(Run.class);
        Job<?, ?> job = Mockito.mock(Job.class);
        when(run.getParent()).thenReturn((Job) job);
        when(job.getProperty(McpxJobProperty.class)).thenReturn(null);

        EnvVars env = new EnvVars();
        McpxSelectedServerEnvAction action = new McpxSelectedServerEnvAction("test-server");

        action.buildEnvironment(run, env);

        // Should set default when no job-level or global configuration is available
        assertNotNull(env.get("MCPX_REGISTRY_BASE_URL"));
        assertEquals("https://mcpx.example.com", env.get("MCPX_REGISTRY_BASE_URL"));
    }

    @Test
    public void testBuildEnvironmentSetsMCPX_REGISTRY_BASE_URLFromJobConfig() {
        Run<?, ?> run = Mockito.mock(Run.class);
        Job<?, ?> job = Mockito.mock(Job.class);
        when(run.getParent()).thenReturn((Job) job);

        // Mock job-level configuration
        McpxJobProperty jobProperty = Mockito.mock(McpxJobProperty.class);
        when(jobProperty.getRegistryBaseUrl()).thenReturn("https://job-registry.example.com");
        when(job.getProperty(McpxJobProperty.class)).thenReturn(jobProperty);

        EnvVars env = new EnvVars();
        McpxSelectedServerEnvAction action = new McpxSelectedServerEnvAction("test-server");

        action.buildEnvironment(run, env);

        // Should use job-level configuration
        assertEquals("https://job-registry.example.com", env.get("MCPX_REGISTRY_BASE_URL"));
    }

    @Test
    public void testBuildEnvironmentRespectsExistingMCPX_REGISTRY_BASE_URL() {
        Run<?, ?> run = Mockito.mock(Run.class);
        Job<?, ?> job = Mockito.mock(Job.class);
        when(run.getParent()).thenReturn((Job) job);

        // Mock job-level configuration
        McpxJobProperty jobProperty = Mockito.mock(McpxJobProperty.class);
        when(jobProperty.getRegistryBaseUrl()).thenReturn("https://job-registry.example.com");
        when(job.getProperty(McpxJobProperty.class)).thenReturn(jobProperty);

        EnvVars env = new EnvVars();
        env.put("MCPX_REGISTRY_BASE_URL", "https://user-override.example.com");

        McpxSelectedServerEnvAction action = new McpxSelectedServerEnvAction("test-server");
        action.buildEnvironment(run, env);

        // Should not override existing value
        assertEquals("https://user-override.example.com", env.get("MCPX_REGISTRY_BASE_URL"));
    }

    @Test
    public void testBuildEnvironmentUsesDefaultWhenNoConfig() {
        Run<?, ?> run = Mockito.mock(Run.class);
        Job<?, ?> job = Mockito.mock(Job.class);
        when(run.getParent()).thenReturn((Job) job);
        when(job.getProperty(McpxJobProperty.class)).thenReturn(null);

        EnvVars env = new EnvVars();
        McpxSelectedServerEnvAction action = new McpxSelectedServerEnvAction("test-server");

        action.buildEnvironment(run, env);

        // Should use default when no configuration is set
        assertEquals("https://mcpx.example.com", env.get("MCPX_REGISTRY_BASE_URL"));
    }

    @Test
    public void testBuildEnvironmentJobLevelOverridesGlobal() {
        Run<?, ?> run = Mockito.mock(Run.class);
        Job<?, ?> job = Mockito.mock(Job.class);
        when(run.getParent()).thenReturn((Job) job);

        // Mock job-level configuration (should take precedence)
        McpxJobProperty jobProperty = Mockito.mock(McpxJobProperty.class);
        when(jobProperty.getRegistryBaseUrl()).thenReturn("https://job-level.example.com");
        when(job.getProperty(McpxJobProperty.class)).thenReturn(jobProperty);

        EnvVars env = new EnvVars();
        McpxSelectedServerEnvAction action = new McpxSelectedServerEnvAction("test-server");

        action.buildEnvironment(run, env);

        // Job-level should override global
        assertEquals("https://job-level.example.com", env.get("MCPX_REGISTRY_BASE_URL"));
    }

    @Test
    public void testBuildEnvironmentSetsMCPX_CLI_PATHWithDefault() {
        Run<?, ?> run = Mockito.mock(Run.class);
        Job<?, ?> job = Mockito.mock(Job.class);
        when(run.getParent()).thenReturn((Job) job);
        when(job.getProperty(McpxJobProperty.class)).thenReturn(null);

        EnvVars env = new EnvVars();
        McpxSelectedServerEnvAction action = new McpxSelectedServerEnvAction("test-server");

        action.buildEnvironment(run, env);

        // Should set default when no job-level or global configuration is available
        assertNotNull(env.get("MCPX_CLI_PATH"));
        assertEquals("mcpx-cli", env.get("MCPX_CLI_PATH"));
    }

    @Test
    public void testBuildEnvironmentSetsMCPX_CLI_PATHFromJobConfig() {
        Run<?, ?> run = Mockito.mock(Run.class);
        Job<?, ?> job = Mockito.mock(Job.class);
        when(run.getParent()).thenReturn((Job) job);

        // Mock job-level configuration
        McpxJobProperty jobProperty = Mockito.mock(McpxJobProperty.class);
        when(jobProperty.getCliPath()).thenReturn("/usr/local/bin/mcpx-cli");
        when(job.getProperty(McpxJobProperty.class)).thenReturn(jobProperty);

        EnvVars env = new EnvVars();
        McpxSelectedServerEnvAction action = new McpxSelectedServerEnvAction("test-server");

        action.buildEnvironment(run, env);

        // Should use job-level configuration
        assertEquals("/usr/local/bin/mcpx-cli", env.get("MCPX_CLI_PATH"));
    }

    @Test
    public void testBuildEnvironmentSetsMCPX_CLI_PATHWithTilde() {
        Run<?, ?> run = Mockito.mock(Run.class);
        Job<?, ?> job = Mockito.mock(Job.class);
        when(run.getParent()).thenReturn((Job) job);

        // Mock job-level configuration with tilde
        McpxJobProperty jobProperty = Mockito.mock(McpxJobProperty.class);
        when(jobProperty.getCliPath()).thenReturn("~/.local/bin/mcpx-cli");
        when(job.getProperty(McpxJobProperty.class)).thenReturn(jobProperty);

        EnvVars env = new EnvVars();
        McpxSelectedServerEnvAction action = new McpxSelectedServerEnvAction("test-server");

        action.buildEnvironment(run, env);

        // Should use job-level configuration (tilde expansion happens in bash script)
        assertEquals("~/.local/bin/mcpx-cli", env.get("MCPX_CLI_PATH"));
    }

    @Test
    public void testBuildEnvironmentRespectsExistingMCPX_CLI_PATH() {
        Run<?, ?> run = Mockito.mock(Run.class);
        Job<?, ?> job = Mockito.mock(Job.class);
        when(run.getParent()).thenReturn((Job) job);

        // Mock job-level configuration
        McpxJobProperty jobProperty = Mockito.mock(McpxJobProperty.class);
        when(jobProperty.getCliPath()).thenReturn("/usr/local/bin/mcpx-cli");
        when(job.getProperty(McpxJobProperty.class)).thenReturn(jobProperty);

        EnvVars env = new EnvVars();
        env.put("MCPX_CLI_PATH", "/custom/path/mcpx-cli");

        McpxSelectedServerEnvAction action = new McpxSelectedServerEnvAction("test-server");
        action.buildEnvironment(run, env);

        // Should not override existing value
        assertEquals("/custom/path/mcpx-cli", env.get("MCPX_CLI_PATH"));
    }

    @Test
    public void testBuildEnvironmentCliPathJobLevelOverridesGlobal() {
        Run<?, ?> run = Mockito.mock(Run.class);
        Job<?, ?> job = Mockito.mock(Job.class);
        when(run.getParent()).thenReturn((Job) job);

        // Mock job-level configuration (should take precedence)
        McpxJobProperty jobProperty = Mockito.mock(McpxJobProperty.class);
        when(jobProperty.getCliPath()).thenReturn("/job/path/mcpx-cli");
        when(job.getProperty(McpxJobProperty.class)).thenReturn(jobProperty);

        EnvVars env = new EnvVars();
        McpxSelectedServerEnvAction action = new McpxSelectedServerEnvAction("test-server");

        action.buildEnvironment(run, env);

        // Job-level should override global
        assertEquals("/job/path/mcpx-cli", env.get("MCPX_CLI_PATH"));
    }
}
