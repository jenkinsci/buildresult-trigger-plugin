package org.jenkinsci.plugins.buildresulttrigger;

import antlr.ANTLRException;
import hudson.Extension;
import hudson.matrix.MatrixConfiguration;
import hudson.model.*;
import hudson.model.listeners.RunListener;
import hudson.triggers.Trigger;
import hudson.util.ListBoxModel;
import org.jenkinsci.lib.xtrigger.*;
import org.jenkinsci.plugins.buildresulttrigger.model.BuildResultTriggerInfo;
import org.jenkinsci.plugins.buildresulttrigger.model.CheckedResult;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;

/**
 * @author Gregory Boissinot
 */
public class BuildResultTrigger extends Trigger<BuildableItem> {

    private BuildResultTriggerInfo[] jobsInfo = new BuildResultTriggerInfo[0];

    @DataBoundConstructor
    public BuildResultTrigger(BuildResultTriggerInfo[] jobInfo) throws ANTLRException {
        this.jobsInfo = jobInfo;
    }

    @Override
    public void start(BuildableItem project, boolean newInstance) {
        super.start(project, newInstance);
        instances.put(this, "foo");
    }

    @Override
    public void stop() {
        super.stop();
        instances.remove(this);
    }

    @SuppressWarnings("unused")
    public BuildResultTriggerInfo[] getJobsInfo() {
        return jobsInfo;
    }

    @Extension
    public final static RunListener<Build<?,?>> listener = new RunListener<Build<?,?>>() {
        @Override
        public void onFinalized(Build<?, ?> build) {
            for (BuildResultTrigger trigger : instances.keySet()) {
                trigger.onFinalized(build);
            }
        }
    };

    private void onFinalized(Build<?, ?> build) {
        for (BuildResultTriggerInfo info : jobsInfo) {
            Job job = build.getParent();
            if (info.getJobName().equals(job.getFullName())) {
                CheckedResult[] expectedResults = info.getCheckedResults();

                if (expectedResults == null || expectedResults.length == 0) {
                    return;
                }

                for (CheckedResult checkedResult : expectedResults) {
                    if (checkedResult.getResult().ordinal == build.getResult().ordinal) {
                        this.job.scheduleBuild(0, new BuildResultChangeCause());
                    }
                }
            }
        }
    }

    private static final Map<BuildResultTrigger, String> instances = new ConcurrentHashMap<BuildResultTrigger, String>();

    @Extension
    @SuppressWarnings("unused")
    public static class BuildResultTriggerDescriptor extends XTriggerDescriptor {

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

        public ListBoxModel doFillJobNameItems(@AncestorInPath Job job) {
            ListBoxModel model = new ListBoxModel();
            for (Item item : Hudson.getInstance().getAllItems()) {
                if ((item instanceof Job) && !(item instanceof MatrixConfiguration)) {
                    if (item == job) continue;
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
            return "Monitor build results of other jobs";
        }

        @Override
        public String getHelpFile() {
            return "/plugin/buildresult-trigger/help.html";
        }
    }
}
