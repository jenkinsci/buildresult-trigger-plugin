package org.jenkinsci.plugins.buildresulttrigger.model;

import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author Gregory Boissinot
 */
public class BuildResultTriggerInfo {

    private String jobName;

    private CheckedResult[] checkedResults;

    @DataBoundConstructor
    public BuildResultTriggerInfo(String jobName, CheckedResult[] checkedResult) {
        this.jobName = jobName;
        this.checkedResults = checkedResult;
    }

    @SuppressWarnings("unused")
    public String getJobName() {
        return jobName;
    }

    @SuppressWarnings("unused")
    public CheckedResult[] getCheckedResults() {
        return checkedResults;
    }
}
