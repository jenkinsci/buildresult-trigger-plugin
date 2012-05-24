package org.jenkinsci.plugins.buildresulttrigger;

import org.jenkinsci.lib.xtrigger.XTriggerCause;

/**
 * @author: <a hef="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class BuildResultChangeCause extends XTriggerCause {

    public BuildResultChangeCause() {
        super("BuildResultTrigger", "A change to build result", true);
    }
}
