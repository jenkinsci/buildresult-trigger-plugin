package org.jenkinsci.plugins.buildresulttrigger;

/**
 * @author Gregory Boissinot
 */
public class BuildResultTriggerException extends Exception {

    public BuildResultTriggerException() {
        super();
    }

    public BuildResultTriggerException(String s) {
        super(s);
    }

    public BuildResultTriggerException(String s, Throwable throwable) {
        super(s, throwable);
    }

    public BuildResultTriggerException(Throwable throwable) {
        super(throwable);
    }
}
