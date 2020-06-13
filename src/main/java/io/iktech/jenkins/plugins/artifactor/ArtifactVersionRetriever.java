package io.iktech.jenkins.plugins.artifactor;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import io.iktech.jenkins.plugins.artifactor.model.ErrorMessage;
import jenkins.tasks.SimpleBuildStep;
import org.apache.commons.lang.StringUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ArtifactVersionRetriever extends Builder implements SimpleBuildStep {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static Logger logger = LoggerFactory.getLogger(ArtifactVersionRetriever.class);

    private List<Name> names;
    private String stage;
    private String variableName;

    @DataBoundConstructor
    public ArtifactVersionRetriever(List<Name> names,
                                    String stage,
                                    String variableName) {
        logger.info("Creating builder. Stage: " + stage + ", names: " + names.size());
        this.names = names;
        this.stage  = stage;
        this.variableName = variableName;
    }

    public List<Name> getNames() {
        return this.names == null ? new ArrayList<>() : this.names;
    }

    @DataBoundSetter
    public void setNames(List<Name> names) {
        logger.info("Setting names: " + names.size());
        this.names  = names;
    }

    public String getStage() {
        return stage;
    }

    @DataBoundSetter
    public void setStage(String stage) {
        this.stage = stage;
    }

    public String getVariableName() {
        return variableName;
    }

    @DataBoundSetter
    public void setVariableName(String variableName) {
        this.variableName = variableName;
    }

    @Override
    public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath filePath, @Nonnull Launcher launcher, @Nonnull TaskListener taskListener) throws InterruptedException, IOException {
        PrintStream l = taskListener.getLogger();
        l.println("Retrieving versions of the following artifacts at the stage '" + this.stage + "'");
        String param = String.join("&", this.names.stream().map(n -> {
            l.println("  - " + n.getName());
            return "artifact=" + n.getName();
        }).collect(Collectors.toList()));
        StringCredentials token = CredentialsProvider.findCredentialById(Configuration.get().getCredentialsId(), StringCredentials.class, run);

        try {
            String content = RequestHelper.retrieveVersion(token.getSecret().getPlainText(), this.stage, param);
            EnvVars envVars = run.getEnvironment(taskListener);
            envVars.put("_response", content);

            String variableName = !StringUtils.isEmpty(this.variableName) ? this.variableName : "ARTIFACTOR_VERSION_DATA";
            run.addAction(new InjectVariable(variableName, content));
            l.println("Successfully retrieved artifact versions");
        } catch (ExchangeException e) {
            logger.error("Error while retrieving artifact versions", e);
            taskListener.fatalError("Error while retrieving artifact versions: " + e.getMessage());
        }
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        public FormValidation doCheckNames(@QueryParameter List<Name> value)
                throws IOException, ServletException {
            if (value == null || value.size() == 0) {
                return FormValidation.error(Messages.ArtifactVersionRetriever_DescriptorImpl_errors_missingNames());
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckStage(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0) {
                return FormValidation.error(Messages.Artifact_DescriptorImpl_errors_missingStage());
            }
            return FormValidation.ok();
        }
        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return Messages.ArtifactVersionRetriever_DescriptorImpl_DisplayName();
        }
    }
}
