package org.jenkinsci.plugins.buildresulttrigger;

import hudson.model.TaskListener;

/**
 * @author Gregory Boissinot
 */
public class BuildResultTriggerLog {

    private TaskListener listener;

    public BuildResultTriggerLog(TaskListener listener) {
        this.listener = listener;
    }

    public void info(String message) {
        listener.getLogger().println(message);
    }

    public void error(String message) {
        listener.getLogger().println("[ERROR] - " + message);
    }
}
