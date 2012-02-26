package org.jenkinsci.plugins.buildresulttrigger;

import org.jenkinsci.lib.xtrigger.XTriggerCause;

/**
 * @author Gregory Boissinot
 */
@Deprecated
public class BuildResultTriggerCause extends XTriggerCause {

    public BuildResultTriggerCause(String causeFrom) {
        super("IvyTrigger", causeFrom, false);
    }
}
