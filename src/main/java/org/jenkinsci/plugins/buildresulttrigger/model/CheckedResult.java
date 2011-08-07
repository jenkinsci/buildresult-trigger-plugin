package org.jenkinsci.plugins.buildresulttrigger.model;

import hudson.model.Result;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author Gregory Boissinot
 */
public class CheckedResult {

    private Result result;

    @DataBoundConstructor
    public CheckedResult(String result) {
        this.result = Result.fromString(result);
    }

    @SuppressWarnings("unuser")
    public Result getResult() {
        return result;
    }
}
