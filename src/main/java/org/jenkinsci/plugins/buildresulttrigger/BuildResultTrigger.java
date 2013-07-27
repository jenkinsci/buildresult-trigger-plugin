package org.jenkinsci.plugins.buildresulttrigger;

import hudson.Extension;
import hudson.matrix.MatrixConfiguration;
import hudson.model.Action;
import hudson.model.Item;
import hudson.model.Result;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.model.Node;
import hudson.model.Project;
import hudson.model.Run;
import hudson.model.listeners.ItemListener;
import hudson.security.ACL;
import hudson.util.SequentialExecutionQueue;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

import jenkins.model.Jenkins;

import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.lib.xtrigger.AbstractTriggerByFullContext;
import org.jenkinsci.lib.xtrigger.XTriggerDescriptor;
import org.jenkinsci.lib.xtrigger.XTriggerException;
import org.jenkinsci.lib.xtrigger.XTriggerLog;
import org.jenkinsci.plugins.buildresulttrigger.model.BuildResultTriggerInfo;
import org.jenkinsci.plugins.buildresulttrigger.model.CheckedResult;
import org.kohsuke.stapler.DataBoundConstructor;

import antlr.ANTLRException;

/**
 * @author Gregory Boissinot
 */
public class BuildResultTrigger extends AbstractTriggerByFullContext<BuildResultTriggerContext> {

    private BuildResultTriggerInfo[] jobsInfo = new BuildResultTriggerInfo[0];

    @DataBoundConstructor
    public BuildResultTrigger(String cronTabSpec, BuildResultTriggerInfo[] jobsInfo) throws ANTLRException {
        super(cronTabSpec);
        this.jobsInfo = jobsInfo;
    }

    public BuildResultTriggerInfo[] getJobsInfo() {
        return jobsInfo;
    }

    @Override
    public File getLogFile() {
        return new File(job.getRootDir(), "buildResultTrigger-polling.log");
    }

    @Override
    public Collection<? extends Action> getProjectActions() {
        BuildResultTriggerAction action = new BuildResultTriggerAction((AbstractProject) job, getLogFile(), getDescriptor().getDisplayName());
        return Collections.singleton(action);
    }

    @Override
    protected boolean requiresWorkspaceForPolling() {
        return false;
    }

    @Override
    protected String getName() {
        return "BuildResultTrigger";
    }

    @Override
    protected Action[] getScheduledActions(Node node, XTriggerLog log) {
        return new Action[0];
    }

    @Override
    protected String getCause() {
        return "A change to build result";
    }

    @Override
    public boolean isContextOnStartupFetched() {
        return true;
    }

    @Override
    protected BuildResultTriggerContext getContext(Node node, XTriggerLog log) throws XTriggerException {
        Map<String, Integer> contextResults = new HashMap<String, Integer>();
        SecurityContext securityContext = ACL.impersonate(ACL.SYSTEM);
        try {
            for (BuildResultTriggerInfo info : jobsInfo) {
                for (String jobName : info.getJobNamesAsArray()) {
                    AbstractProject job = Hudson.getInstance().getItemByFullName(jobName, AbstractProject.class);
                    if (isValidBuildResultProject(job)) {
                        Run lastBuild = job.getLastCompletedBuild();
                        if (lastBuild != null) {
                            int buildNumber = lastBuild.getNumber();
                            if (buildNumber != 0) {
                                contextResults.put(jobName, buildNumber);
                            }
                        }
                    }
                    else {
                        log.info(String.format("Job %s is not a valid job - ignoring it.", jobName));
                    }
                }
            }
        } finally {
            SecurityContextHolder.setContext(securityContext);
        }
        return new BuildResultTriggerContext(contextResults);
    }

    private boolean isValidBuildResultProject(AbstractProject item) {
        if (item == null) {
            return false;
        }

        if (item instanceof MatrixConfiguration) {
            return false;
        }

        return true;
    }

    @Override
    protected boolean checkIfModified(BuildResultTriggerContext oldContext,
                                      BuildResultTriggerContext newContext,
                                      XTriggerLog log)
            throws XTriggerException {
        SecurityContext securityContext = ACL.impersonate(ACL.SYSTEM);
        try {
            for (BuildResultTriggerInfo info : jobsInfo) {
                CheckedResult[] expectedResults = info.getCheckedResults();
                for (String jobName : info.getJobNamesAsArray()) {
                    boolean atLeastOneModification = checkIfModifiedJob(jobName, expectedResults, oldContext, newContext, log);
                    if (atLeastOneModification) {
                        log.info(String.format("Job %s is modified. Triggering a new build.", jobName));
                        return true;
                    }
                }
            }
        } finally {
            SecurityContextHolder.setContext(securityContext);
        }

        return false;
    }

    private boolean checkIfModifiedJob(String jobName, CheckedResult[] expectedResults, BuildResultTriggerContext oldContext, BuildResultTriggerContext newContext,
            XTriggerLog log) {
        log.info(String.format("Checking changes for job %s.", jobName));

        final Map<String, Integer> oldContextResults = oldContext.getResults();
        final Map<String, Integer> newContextResults = newContext.getResults();

        if (newContextResults == null || newContextResults.size() == 0) {
            log.info(String.format("No new builds to check for the job %s", jobName));
            return false;
        }

        if (newContextResults.size() != oldContextResults.size()) {
            return isMatchingExpectedResults(jobName, expectedResults, log, newContextResults.get(jobName));
        }

        Integer newLastBuildNumber = newContextResults.get(jobName);
        if (newLastBuildNumber == null || newLastBuildNumber.intValue() == 0) {
            log.info(String.format("The job %s doesn't have any new builds.", jobName));
            return false;
        }


        Integer oldLastBuildNumber = oldContextResults.get(jobName);
        if (oldLastBuildNumber == null || oldLastBuildNumber.intValue() == 0) {
            return isMatchingExpectedResults(jobName, expectedResults, log, newContextResults.get(jobName));
        }

        //Process if there is a new build between now and previous polling
        if (newLastBuildNumber.intValue() == 0 || newLastBuildNumber.intValue() != oldLastBuildNumber.intValue()) {
            return isMatchingExpectedResults(jobName, expectedResults, log, newContextResults.get(jobName));
        }

        log.info(String.format("There are no new builds for the job %s.", jobName));
        return false;
    }


    private boolean isMatchingExpectedResults(String jobName, CheckedResult[] expectedResults, XTriggerLog log, Integer buildId) {
        log.info(String.format("There is at least one new build for the job %s. Checking expected job build results.", jobName));

        if (expectedResults == null || expectedResults.length == 0) {
            log.info("No results to check. You have to specify at least one expected build result in the build-result trigger configuration.");
            return false;
        }
        if (buildId == null) {
          // no complete build was found so can't trigger here.
        }
        AbstractProject jobObj = Hudson.getInstance().getItemByFullName(jobName, AbstractProject.class);
        Run jobObjLastBuild = jobObj.getBuildByNumber(buildId.intValue());
        Result jobObjectLastResult = jobObjLastBuild.getResult();

        for (CheckedResult checkedResult : expectedResults) {
            log.info(String.format("Checking %s", checkedResult.getResult().toString()));
            if (checkedResult.getResult().ordinal == jobObjectLastResult.ordinal) {
                log.info(String.format("Last build result for the job %s matches the expected result %s.", jobName, jobObjectLastResult));
                return true;
            }
        }

        return false;
    }
    
    public boolean onJobRenamed(String fullOldName, String fullNewName) {
        boolean result = true;
        for (BuildResultTriggerInfo b : jobsInfo) {
            result &= b.onJobRenamed(fullOldName, fullNewName);
        }
        return result;
    }

    @Extension
    @SuppressWarnings("unused")
    public static class BuildResultTriggerDescriptor extends XTriggerDescriptor {

        private transient final SequentialExecutionQueue queue = new SequentialExecutionQueue(Executors.newSingleThreadExecutor());

        @Override
        public ExecutorService getExecutor() {
            return queue.getExecutors();
        }

        @Override
        public boolean isApplicable(Item item) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "[BuildResultTrigger] - Monitor build results of other jobs";
        }

        @Override
        public String getHelpFile() {
            return "/plugin/buildresult-trigger/help.html";
        }
    }
    
    @Extension
    public static class ItemListenerImpl extends ItemListener {
        @Override
        public void onRenamed(Item item, String oldName, String newName) {
            String fullNewName = item.getFullName();
            String fullOldName = StringUtils.removeEnd(fullNewName, newName) + oldName;
            for( Project<?,?> p : Jenkins.getInstance().getAllItems(Project.class) ) {
                BuildResultTrigger t = p.getTrigger(BuildResultTrigger.class);
                if(t!=null) {
                    if(t.onJobRenamed(fullOldName,fullNewName)) {
                        try {
                            p.save();
                        } catch (IOException e) {
                            LOGGER.log(Level.WARNING, "Failed to persist project setting during rename from "+oldName+" to "+newName,e);
                        }
                    }
                }
            }
        }
    }
}
