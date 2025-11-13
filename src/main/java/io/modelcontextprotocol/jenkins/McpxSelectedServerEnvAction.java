package io.modelcontextprotocol.jenkins;

import hudson.EnvVars;
import hudson.model.EnvironmentContributingAction;
import hudson.model.InvisibleAction;
import hudson.model.Run;

import javax.annotation.Nonnull;

public class McpxSelectedServerEnvAction extends InvisibleAction implements EnvironmentContributingAction {
    private final String selectedServer;

    public McpxSelectedServerEnvAction(String selectedServer) {
        this.selectedServer = selectedServer;
    }

    @Override
    public void buildEnvironment(@Nonnull Run<?, ?> run, @Nonnull EnvVars env) {
    }
}
