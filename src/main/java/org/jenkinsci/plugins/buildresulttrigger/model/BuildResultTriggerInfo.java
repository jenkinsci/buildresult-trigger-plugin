package org.jenkinsci.plugins.buildresulttrigger.model;

import hudson.Extension;
import hudson.matrix.MatrixConfiguration;
import hudson.model.*;
import hudson.util.ListBoxModel;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author Gregory Boissinot
 */
public class BuildResultTriggerInfo extends AbstractDescribableImpl<BuildResultTriggerInfo> {

    private final String jobName;

    private final CheckedResult[] checkedResults;

    @DataBoundConstructor
    public BuildResultTriggerInfo(String jobName, CheckedResult[] checkedResults) {
        this.jobName = jobName;
        this.checkedResults = checkedResults;
    }

    @SuppressWarnings("unused")
    public String getJobName() {
        return jobName;
    }

    @SuppressWarnings("unused")
    public CheckedResult[] getCheckedResults() {
        return checkedResults;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<BuildResultTriggerInfo> {
        public String getDisplayName() {
            return "Job to monitor";
        }

        public ListBoxModel doFillJobNameItems() {
            ListBoxModel model = new ListBoxModel();
            for (Item item : Hudson.getInstance().getAllItems()) {
                if ((item instanceof Job) && !(item instanceof MatrixConfiguration)) {
                    model.add(item.getFullName());
                }
            }
            return model;
        }
    }

}
