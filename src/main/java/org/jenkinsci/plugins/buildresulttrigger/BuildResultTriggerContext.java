package org.jenkinsci.plugins.buildresulttrigger;

import org.jenkinsci.lib.xtrigger.XTriggerContext;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Gregory Boissinot
 */
public class BuildResultTriggerContext implements XTriggerContext {

    /*
    * Recorded map to know if a build has to be triggered
    */
    private Map<String, Integer> results = new HashMap<String, Integer>();

    public BuildResultTriggerContext(Map<String, Integer> results) {
        this.results = results;
    }

    public Map<String, Integer> getResults() {
        return results;
    }
}
