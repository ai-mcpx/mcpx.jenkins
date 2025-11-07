package io.modelcontextprotocol.jenkins;

import hudson.model.Job;
import hudson.model.AbstractProject;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for McpxJobProperty to verify pipeline job support.
 */
public class McpxJobPropertyTest {

    @Test
    public void testPropertyInstantiation() {
        McpxJobProperty property = new McpxJobProperty("test-path", "test-url", "test-server");
        assertNotNull(property);
        assertEquals("test-path", property.getCliPath());
        assertEquals("test-url", property.getRegistryBaseUrl());
        assertEquals("test-server", property.getSelectedServer());
    }

    @Test
    public void testPropertyWithNullValues() {
        McpxJobProperty property = new McpxJobProperty(null, null, null);
        assertNotNull(property);
        assertNull(property.getCliPath());
        assertNull(property.getRegistryBaseUrl());
        assertEquals("", property.getSelectedServer()); // Should return empty string, not null
    }

    @Test
    public void testPropertyWithEmptyStrings() {
        McpxJobProperty property = new McpxJobProperty("", "", "");
        assertNotNull(property);
        assertNull(property.getCliPath()); // Empty strings should be trimmed to null
        assertNull(property.getRegistryBaseUrl());
        assertEquals("", property.getSelectedServer());
    }

    @Test
    public void testDescriptorIsApplicableForAbstractProject() {
        McpxJobProperty.DescriptorImpl descriptor = new McpxJobProperty.DescriptorImpl();
        // Should return true for AbstractProject (freestyle projects)
        assertTrue(descriptor.isApplicable(AbstractProject.class));
    }

    @Test
    public void testDescriptorIsApplicableForJob() {
        McpxJobProperty.DescriptorImpl descriptor = new McpxJobProperty.DescriptorImpl();
        // Should return true for base Job class (includes pipeline jobs)
        assertTrue(descriptor.isApplicable(Job.class));
    }

    @Test
    public void testDescriptorIsApplicableForAllJobTypes() {
        McpxJobProperty.DescriptorImpl descriptor = new McpxJobProperty.DescriptorImpl();
        // Should return true for any Job subclass
        assertTrue(descriptor.isApplicable(AbstractProject.class));
        assertTrue(descriptor.isApplicable(Job.class));
    }

    @Test
    public void testDescriptorDisplayName() {
        McpxJobProperty.DescriptorImpl descriptor = new McpxJobProperty.DescriptorImpl();
        assertEquals("MCPX CLI Configuration", descriptor.getDisplayName());
    }

    @Test
    public void testPropertyExtendsJobPropertyOfJob() {
        // Verify that McpxJobProperty extends JobProperty<Job<?, ?>>
        // This is important for pipeline job support
        McpxJobProperty property = new McpxJobProperty("path", "url", "server");
        assertTrue(property instanceof hudson.model.JobProperty);
    }
}
