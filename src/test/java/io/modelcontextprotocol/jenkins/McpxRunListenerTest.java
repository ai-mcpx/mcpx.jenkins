package io.modelcontextprotocol.jenkins;

import hudson.model.Job;
import hudson.model.ParametersAction;
import hudson.model.Run;
import hudson.model.StringParameterValue;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Tests for McpxRunListener.
 */
@SuppressWarnings("unchecked")
public class McpxRunListenerTest {

    @Test
    public void testOnInitializeWithJobProperty() {
        McpxRunListener listener = new McpxRunListener();
        Run<?, ?> run = Mockito.mock(Run.class);
        Job<?, ?> job = Mockito.mock(Job.class);
        McpxJobProperty property = new McpxJobProperty(null, null, "test-server");

        when(run.getParent()).thenReturn((Job) job);
        when(job.getProperty(McpxJobProperty.class)).thenReturn(property);
        when(run.getAllActions()).thenReturn(java.util.Collections.emptyList());

        listener.onInitialize(run);

        // Verify that an action was added
        verify(run, atLeastOnce()).addAction(any(McpxSelectedServerEnvAction.class));
    }

    @Test
    public void testOnInitializeWithParameterizedBuild() {
        McpxRunListener listener = new McpxRunListener();
        // Create a mock Run that has ParametersAction
        Run<?, ?> run = Mockito.mock(Run.class);
        Job<?, ?> job = Mockito.mock(Job.class);
        ParametersAction paramsAction = Mockito.mock(ParametersAction.class);
        StringParameterValue mcpServerParam = new StringParameterValue("MCP_SERVER", "param-server");

        when(run.getParent()).thenReturn((Job) job);
        when(job.getProperty(McpxJobProperty.class)).thenReturn(null);
        when(run.getAction(ParametersAction.class)).thenReturn(paramsAction);
        when(paramsAction.getParameters()).thenReturn(java.util.Collections.singletonList(mcpServerParam));
        when(run.getAllActions()).thenReturn(java.util.Collections.emptyList());

        listener.onInitialize(run);

        // Verify that an action was added
        verify(run, atLeastOnce()).addAction(any(McpxSelectedServerEnvAction.class));
    }

    @Test
    public void testOnInitializeWithNoServer() {
        McpxRunListener listener = new McpxRunListener();
        Run<?, ?> run = Mockito.mock(Run.class);
        Job<?, ?> job = Mockito.mock(Job.class);

        when(run.getParent()).thenReturn((Job) job);
        when(job.getProperty(McpxJobProperty.class)).thenReturn(null);

        listener.onInitialize(run);

        // Should not add action if no server is configured
        verify(run, never()).addAction(any(McpxSelectedServerEnvAction.class));
    }

    @Test
    public void testOnStartedWithJobProperty() {
        McpxRunListener listener = new McpxRunListener();
        Run<?, ?> run = Mockito.mock(Run.class);
        Job<?, ?> job = Mockito.mock(Job.class);
        McpxJobProperty property = new McpxJobProperty(null, null, "test-server");

        when(run.getParent()).thenReturn((Job) job);
        when(job.getProperty(McpxJobProperty.class)).thenReturn(property);
        when(run.getAllActions()).thenReturn(java.util.Collections.emptyList());

        listener.onStarted(run, null);

        // Verify that an action was added
        verify(run, atLeastOnce()).addAction(any(McpxSelectedServerEnvAction.class));
    }

    @Test
    public void testOnInitializePreventsDuplicates() {
        McpxRunListener listener = new McpxRunListener();
        Run<?, ?> run = Mockito.mock(Run.class);
        Job<?, ?> job = Mockito.mock(Job.class);
        McpxJobProperty property = new McpxJobProperty(null, null, "test-server");
        McpxSelectedServerEnvAction existingAction = new McpxSelectedServerEnvAction("test-server");

        when(run.getParent()).thenReturn((Job) job);
        when(job.getProperty(McpxJobProperty.class)).thenReturn(property);
        when(run.getAllActions()).thenReturn((java.util.List) java.util.Collections.singletonList(existingAction));

        listener.onInitialize(run);

        // Should not add duplicate action
        verify(run, never()).addAction(any(McpxSelectedServerEnvAction.class));
    }

    @Test
    public void testOnInitializeWithNullRun() {
        McpxRunListener listener = new McpxRunListener();

        // Should not throw exception
        listener.onInitialize(null);
    }
}
