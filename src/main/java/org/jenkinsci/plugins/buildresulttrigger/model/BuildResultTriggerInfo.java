package org.jenkinsci.plugins.buildresulttrigger.model;

import hudson.Extension;
import hudson.Util;
import hudson.model.*;
import hudson.tasks.Messages;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.util.Arrays;
import java.util.StringTokenizer;

/**
 * @author Gregory Boissinot
 */
public class BuildResultTriggerInfo extends AbstractDescribableImpl<BuildResultTriggerInfo> {
    @Deprecated
    private transient String jobName;

    private String jobNames;

    private final CheckedResult[] checkedResults;

    @DataBoundConstructor
    public BuildResultTriggerInfo(String jobNames, CheckedResult[] checkedResults) {
        this.jobNames = jobNames;
        this.checkedResults = Arrays.copyOf(checkedResults, checkedResults.length);
    }

    /**
     * Please use getJobNames() instead
     *
     * @return the job name to trigger
     * @deprecated
     */
    @Deprecated
    public String getJobName() {
        return jobName;
    }

    public String getJobNames() {
        return jobNames;
    }

    public String[] getJobNamesAsArray() {

        if (StringUtils.isBlank(jobNames)) {
            return new String[0];
        }

        final String endJobNames = ", ";
        String jobNames2proceed = jobNames;
        if (jobNames2proceed.endsWith(endJobNames)) {
            jobNames2proceed = jobNames.substring(0, jobNames.length() - endJobNames.length());
        }

        String[] projects = StringUtils.split(jobNames2proceed, ',');
        for (int i = 0; i < projects.length; i++) {
            projects[i] = projects[i].trim();
        }
        return projects;
    }

    public CheckedResult[] getCheckedResults() {
        return Arrays.copyOf(checkedResults, checkedResults.length);
    }


    public boolean onJobRenamed(String fullOldName, String fullNewName) {
        // quick test
        if (!jobNames.contains(fullOldName)) {
            return false;
        }

        boolean changed = false;

        // we need to do this per string, since old Project object is already gone.
        String[] projects = getJobNamesAsArray();
        for (int i = 0; i < projects.length; i++) {
            if (projects[i].equals(fullOldName)) {
                projects[i] = fullNewName;
                changed = true;
            }
        }

        if (changed) {
            StringBuilder b = new StringBuilder();
            for (String p : projects) {
                if (b.length() > 0) {
                    b.append(',');
                }
                b.append(p);
            }
            jobNames = b.toString();
        }

        return changed;
    }

    protected Object readResolve() {
        if (this.jobNames == null) {
            this.jobNames = this.jobName;
        }
        return this;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<BuildResultTriggerInfo> {
        @Override
        public String getDisplayName() {
            return "Job to monitor";
        }

        public AutoCompletionCandidates doAutoCompleteJobNames(@QueryParameter String value, @AncestorInPath Item self, @AncestorInPath ItemGroup container) {
            return AutoCompletionCandidates.ofJobNames(Job.class, value, self, container);
        }

        public FormValidation doCheckJobNames(@AncestorInPath Item project, @QueryParameter String value) {
            // Require CONFIGURE permission on this project
            if (!project.hasPermission(Item.CONFIGURE)) {
                return FormValidation.ok();
            }

            StringTokenizer tokens = new StringTokenizer(Util.fixNull(value), ",");
            boolean hasProjects = false;
            while (tokens.hasMoreTokens()) {
                String projectName = tokens.nextToken().trim();
                if (StringUtils.isNotBlank(projectName)) {
                    Item item = Jenkins.getActiveInstance().getItem(projectName, project, Item.class);
                    if (item == null) {
                        AbstractProject suggestion = AbstractProject.findNearest(projectName, project.getParent());
                        assert suggestion != null;
                        return FormValidation.error(Messages.BuildTrigger_NoSuchProject(projectName,
                                   suggestion.getRelativeNameFrom(project)));
                    }
                    if (!(item instanceof AbstractProject)) {
                        return FormValidation.error(Messages.BuildTrigger_NotBuildable(projectName));
                    }
                    hasProjects = true;
                }
            }
            if (!hasProjects) {
                return FormValidation.error(Messages.BuildTrigger_NoProjectSpecified());
            }

            return FormValidation.ok();
        }

    }

}
