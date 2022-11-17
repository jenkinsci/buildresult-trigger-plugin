package org.jenkinsci.plugins.buildresulttrigger.model;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Result;
import hudson.util.ListBoxModel;

import java.io.Serializable;

import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author Gregory Boissinot
 */
public class CheckedResult extends AbstractDescribableImpl<CheckedResult> implements Serializable {

    /**
	 * 
	 */
	private static final long serialVersionUID = -1745559604929300826L;

	private transient Result result;

    private final String checked;

    @DataBoundConstructor
    public CheckedResult(String result) {
        this.result = Result.fromString(result);
        this.checked = result;
    }

    @SuppressWarnings("unused")
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

    @SuppressWarnings("unused")
    private Object readResolve() {
        if (checked != null) {
            this.result = Result.fromString(checked);
        }
        return this;
    }
}
