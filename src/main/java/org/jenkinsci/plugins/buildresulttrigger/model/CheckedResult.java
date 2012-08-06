package org.jenkinsci.plugins.buildresulttrigger.model;

import hudson.Extension;
import hudson.model.*;
import hudson.util.ListBoxModel;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author Gregory Boissinot
 */
public class CheckedResult extends AbstractDescribableImpl<CheckedResult> {

    private Result result;

    @DataBoundConstructor
    public CheckedResult(String result) {
        this.result = Result.fromString(result);
    }

    @SuppressWarnings("unuser")
    public Result getResult() {
        return result;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<CheckedResult> {
        public String getDisplayName() {
            return "Job Build Results to check}";
        }

        public ListBoxModel doFillResultItems() {
            ListBoxModel model = new ListBoxModel();
            model.add(Result.SUCCESS.toString());
            model.add(Result.UNSTABLE.toString());
            model.add(Result.FAILURE.toString());
            model.add(Result.NOT_BUILT.toString());
            model.add(Result.ABORTED.toString());
            return model;
        }

    }
}
