package org.jenkinsci.plugins.buildresulttrigger;

import antlr.ANTLRException;
import hudson.Extension;
import hudson.Util;
import hudson.model.*;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.SequentialExecutionQueue;
import hudson.util.StreamTaskListener;
import org.jenkinsci.plugins.buildresulttrigger.model.BuildResultTriggerInfo;
import org.jenkinsci.plugins.buildresulttrigger.model.CheckedResult;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Gregory Boissinot
 */
public class BuildResultTrigger extends Trigger<BuildableItem> implements Serializable {

    private static Logger LOGGER = Logger.getLogger(BuildResultTrigger.class.getName());

    private BuildResultTriggerInfo[] jobsInfo = new BuildResultTriggerInfo[0];

    /*
    * Recorded map to know if a build have to be triggered
    */
    private transient Map<String, Result> results = new HashMap<String, Result>();

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

    /**
     * Asynchronous task
     */
    protected class Runner implements Runnable, Serializable {

        private AbstractProject project;

        private BuildResultTriggerLog log;

        private Runner(AbstractProject project, BuildResultTriggerLog log) {
            this.project = project;
            this.log = log;
        }

        public void run() {

            try {
                long start = System.currentTimeMillis();
                log.info("Polling started on " + DateFormat.getDateTimeInstance().format(new Date(start)));
                boolean changed = checkIfModified(log);
                log.info("Polling complete. Took " + Util.getTimeSpanString(System.currentTimeMillis() - start));
                if (changed) {
                    log.info("Checked jobs match polling criteria. Scheduling a build.");
                    project.scheduleBuild(new BuildResultTriggerCause());
                } else {
                    log.info("Checked jobs don't match polling criteria.");
                }
            } catch (BuildResultTriggerException e) {
                log.error("Polling error " + e.getMessage());
            } catch (Throwable e) {
                log.error("SEVERE - Polling error " + e.getMessage());
            }
        }
    }

    @Override
    public void start(BuildableItem project, boolean newInstance) {
        super.start(project, newInstance);
        try {
            resetMapResults();
        } catch (IOException ioe) {
            LOGGER.log(Level.SEVERE, "Error on trigger startup " + ioe.getMessage());
            ioe.printStackTrace();
        }
    }

    private void resetMapResults() throws IOException {
        for (BuildResultTriggerInfo info : jobsInfo) {
            String jobName = info.getJobName();
            TopLevelItem topLevelItem = Hudson.getInstance().getItem(jobName);
            if (topLevelItem != null && topLevelItem instanceof Project) {
                Project job = (Project) topLevelItem;
                Run lastBuild = job.getLastBuild();
                if (lastBuild != null) {
                    results.put(jobName, lastBuild.getResult());
                }
            }
        }
    }

    private boolean checkIfModified(final BuildResultTriggerLog log) throws BuildResultTriggerException, IOException {

        for (BuildResultTriggerInfo info : jobsInfo) {
            String jobName = info.getJobName();
            TopLevelItem topLevelItem = Hudson.getInstance().getItem(jobName);
            if (topLevelItem != null && topLevelItem instanceof Project) {

                Project job = (Project) topLevelItem;
                log.info(String.format("Checking changes for '%s' job.", jobName));

                //Get last build
                Run lastBuild = job.getLastBuild();

                //Get new job result
                Result newResult;
                if (lastBuild == null) {
                    newResult = Result.NOT_BUILT;

                } else {
                    newResult = lastBuild.getResult();
                }

                //Get registered job result if exists
                Result registeredResult = results.get(jobName);
                if (registeredResult == null) {
                    log.info(String.format("The job '%s' didn't exist in the previous polling. Checking a build result change in the next polling.", jobName));
                    resetMapResults();
                    return false;
                }

                //Check expected result only if the build job has changed
                if (newResult.ordinal != registeredResult.ordinal) {
                    log.info(String.format("Last build result for the job '%s' has changed. Checking expected job build results.", jobName));
                    CheckedResult[] expectedResults = info.getCheckedResults();
                    for (CheckedResult checkedResult : expectedResults) {
                        if (checkedResult.getResult().ordinal == newResult.ordinal) {
                            log.info(String.format("Last build result for the job '%s'  matches the expected result '%s'.", jobName, newResult));
                            resetMapResults();
                            return true;
                        }
                    }
                } else {
                    log.info(String.format("The last build result for the job '%s' hasn't changed.", jobName));
                }
            }
        }

        return false;
    }

    @Override
    public void run() {

        if (!Hudson.getInstance().isQuietingDown() && ((AbstractProject) this.job).isBuildable()) {
            BuildResultTriggerDescriptor descriptor = getDescriptor();
            ExecutorService executorService = descriptor.getExecutor();
            StreamTaskListener listener;
            try {
                listener = new StreamTaskListener(getLogFile());
                BuildResultTriggerLog log = new BuildResultTriggerLog(listener);
                if (job instanceof AbstractProject) {
                    Runner runner = new Runner((AbstractProject) job, log);
                    executorService.execute(runner);
                }

            } catch (Throwable t) {
                executorService.shutdown();
                LOGGER.log(Level.SEVERE, "Severe Error during the trigger execution " + t.getMessage());
                t.printStackTrace();
            }
        }
    }


    @Override
    public BuildResultTriggerDescriptor getDescriptor() {
        return (BuildResultTriggerDescriptor) Hudson.getInstance().getDescriptorOrDie(getClass());
    }

    @Extension
    @SuppressWarnings("unused")
    public static class BuildResultTriggerDescriptor extends TriggerDescriptor {

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

        public List<Job> getJobList() {
            List<Job> jobs = new ArrayList<Job>();
            for (Item item : Hudson.getInstance().getAllItems()) {
                jobs.addAll(item.getAllJobs());
            }
            return jobs;
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
