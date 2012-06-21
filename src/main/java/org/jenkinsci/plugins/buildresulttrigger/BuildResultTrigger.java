package org.jenkinsci.plugins.buildresulttrigger;

import antlr.ANTLRException;
import hudson.Extension;
import hudson.matrix.MatrixConfiguration;
import hudson.model.*;
import hudson.security.ACL;
import hudson.security.NotSerilizableSecurityContext;
import hudson.util.ListBoxModel;
import hudson.util.SequentialExecutionQueue;
import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;
import org.jenkinsci.lib.xtrigger.AbstractTriggerByFullContext;
import org.jenkinsci.lib.xtrigger.XTriggerDescriptor;
import org.jenkinsci.lib.xtrigger.XTriggerException;
import org.jenkinsci.lib.xtrigger.XTriggerLog;
import org.jenkinsci.plugins.buildresulttrigger.model.BuildResultTriggerInfo;
import org.jenkinsci.plugins.buildresulttrigger.model.CheckedResult;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.File;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author Gregory Boissinot
 */
public class BuildResultTrigger extends AbstractTriggerByFullContext<BuildResultTriggerContext> {

    private BuildResultTriggerInfo[] jobsInfo = new BuildResultTriggerInfo[0];

    @DataBoundConstructor
    public BuildResultTrigger(String cronTabSpec, BuildResultTriggerInfo[] jobInfo) throws ANTLRException {
        super(cronTabSpec);
        this.jobsInfo = jobInfo;
    }

    @SuppressWarnings("unused")
    public BuildResultTriggerInfo[] getJobsInfo() {
        return jobsInfo;
    }

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
    protected BuildResultTriggerContext getContext(Node node, XTriggerLog log) throws XTriggerException {
        Map<String, Integer> contextResults = new HashMap<String, Integer>();
        SecurityContext securityContext = ACL.impersonate(ACL.SYSTEM);
        try {
            for (BuildResultTriggerInfo info : jobsInfo) {
                String jobName = info.getJobName();
                AbstractProject job = Hudson.getInstance().getItemByFullName(jobName, AbstractProject.class);
                if (isValidBuildResultProject(job)) {
                    Run lastBuild = job.getLastBuild();
                    if (lastBuild != null) {
                        contextResults.put(jobName, lastBuild.getNumber());
                    }
                }
            }
        }
        finally {
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
    protected boolean checkIfModified(BuildResultTriggerContext oldContext, BuildResultTriggerContext newContext1, XTriggerLog log) throws XTriggerException {

        Map<String, Integer> oldContextResults = oldContext.getResults();
        SecurityContext securityContext = ACL.impersonate(ACL.SYSTEM);
        try {
            for (BuildResultTriggerInfo info : jobsInfo) {
                String jobName = info.getJobName();
                AbstractProject job = Hudson.getInstance().getItemByFullName(jobName, AbstractProject.class);
                if (isValidBuildResultProject(job)) {

                    log.info(String.format("Checking changes for job %s.", jobName));

                    //Get last build
                    Run lastBuild = job.getLastBuild();

                    //Get new job result
                    Result newResult;
                    int newBuildNumber = 0;
                    if (lastBuild == null) {
                        newResult = Result.NOT_BUILT;
                    } else {
                        newResult = lastBuild.getResult();
                        newBuildNumber = lastBuild.getNumber();
                    }


                    //Get registered job result if exists
                    Integer lastBuildNumber = oldContextResults.get(jobName);
                    if (lastBuildNumber == null || lastBuildNumber == 0) {
                        log.info(String.format("The job %s didn't exist in the previous poll. Checking a build result change in the next poll.", jobName));
                        return false;
                    }

                    //Process if there is a new build between now and previous polling
                    if (newBuildNumber == 0 || newBuildNumber != lastBuildNumber) {

                        log.info(String.format("There is at least one new build for the job %s. Checking expected job build results.", jobName));
                        CheckedResult[] expectedResults = info.getCheckedResults();

                        if (expectedResults == null || expectedResults.length == 0) {
                            log.info("No results to check. You have to specify at least one expected build result in the build-result trigger configuration.");
                            return false;
                        }

                        for (CheckedResult checkedResult : expectedResults) {
                            log.info(String.format("Checking %s", checkedResult.getResult().toString()));
                            if (checkedResult.getResult().ordinal == newResult.ordinal) {
                                log.info(String.format("Last build result for the job %s matches the expected result %s.", jobName, newResult));
                                return true;
                            }
                        }

                        return false;

                    }

                    log.info(String.format("There is no new build for the job %s.", jobName));
                    return false;

                }
            }
        }
        finally {
            SecurityContextHolder.setContext(securityContext);
        }

        return false;
    }

    @Extension
    @SuppressWarnings("unused")
    public static class BuildResultTriggerDescriptor extends XTriggerDescriptor {

        private transient final SequentialExecutionQueue queue = new SequentialExecutionQueue(Executors.newSingleThreadExecutor());

        public ExecutorService getExecutor() {
            return queue.getExecutors();
        }

        public List<Result> getResultList() {
            try {
                Field allField = Result.class.getDeclaredField("all");
                allField.setAccessible(true);
                Result[] results = (Result[]) allField.get(null);
                return Arrays.asList(results);
            } catch (NoSuchFieldException nse) {
                throw new RuntimeException(nse);
            } catch (IllegalAccessException iae) {
                throw new RuntimeException(iae);
            }
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
}
