package io.modelcontextprotocol.jenkins;

import hudson.Extension;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;

@Extension
public class McpxRunListener extends RunListener<Run<?, ?>> {
    @Override
    public void onInitialize(Run<?, ?> run) {
        attachEnv(run);
    }

    @Override
    public void onStarted(Run<?, ?> run, TaskListener listener) {
        attachEnv(run);
    }

    private void attachEnv(Run<?, ?> run) {
        if (run == null) return;
        Job<?, ?> job = run.getParent();
        if (job != null) {
            McpxJobProperty prop = job.getProperty(McpxJobProperty.class);
            if (prop != null) {
                String selected = prop.getSelectedServer();
                if (selected != null && !selected.trim().isEmpty()) {
                    // Avoid duplicates
                    for (Object a : run.getAllActions()) {
                        if (a instanceof McpxSelectedServerEnvAction) {
                            return;
                        }
                    }
                    run.addAction(new McpxSelectedServerEnvAction(selected));
                }
            }
        }
    }
}
