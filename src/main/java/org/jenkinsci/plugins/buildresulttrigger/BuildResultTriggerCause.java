package org.jenkinsci.plugins.buildresulttrigger;

import hudson.model.Cause;

/**
 * @author Gregory Boissinot
 */
public class BuildResultTriggerCause extends Cause {

    @Override
    public String getShortDescription() {
        return "Build Result Trigger";
    }
}
