package io.jenkins.plugins.casc;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import io.jenkins.plugins.casc.impl.configurators.DataBoundConfigurator;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import io.jenkins.plugins.casc.misc.junit.jupiter.WithJenkinsConfiguredWithCode;
import io.jenkins.plugins.casc.model.CNode;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Collections;
import java.util.List;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.Node;

@WithJenkinsConfiguredWithCode
class Security1446Test {

    private static final String PATH_PATTERN = "path = \\$\\{PATH\\}";
    private static final String JAVA_HOME_PATTERN = "java-home = \\$\\{JAVA_HOME\\}";

    @ConfiguredWithCode("Security1446Test.yml")
    @Test
    @Issue("SECURITY-1446")
    void testImportWithEnvVar(JenkinsConfiguredWithCodeRule j) {
        List<StandardUsernamePasswordCredentials> userPasswCred = CredentialsProvider.lookupCredentials(
                StandardUsernamePasswordCredentials.class, Jenkins.getInstanceOrNull(), null, Collections.emptyList());
        assertThat(userPasswCred.size(), is(1));
        for (StandardUsernamePasswordCredentials cred : userPasswCred) {
            assertTrue(
                    cred.getUsername().matches(JAVA_HOME_PATTERN),
                    "The JAVA_HOME environment variable should not be resolved");
            assertTrue(
                    cred.getDescription().matches(PATH_PATTERN),
                    "The PATH environment variable should not be resolved");
        }

        List<StringCredentials> stringCred = CredentialsProvider.lookupCredentials(
                StringCredentials.class, Jenkins.getInstanceOrNull(), null, Collections.emptyList());
        assertThat(stringCred.size(), is(1));
        for (StringCredentials cred : stringCred) {
            assertTrue(
                    cred.getDescription().matches(PATH_PATTERN),
                    "The PATH environment variable should not be resolved");
        }
    }

    @Test
    @Issue("SECURITY-1446")
    void testExportWithEnvVar(JenkinsConfiguredWithCodeRule j) throws Exception {
        final String message = "Hello, world! PATH=${PATH} JAVA_HOME=^${JAVA_HOME}";
        ConfiguratorRegistry registry = ConfiguratorRegistry.get();
        ConfigurationContext context = new ConfigurationContext(registry);

        DataBoundConfigurator<UsernamePasswordCredentialsImpl> configurator =
                new DataBoundConfigurator<>(UsernamePasswordCredentialsImpl.class);
        UsernamePasswordCredentialsImpl creds =
                new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "test", message, "foo", "bar");
        final CNode config = configurator.describe(creds, context);
        final Node valueNode = ConfigurationAsCode.get().toYaml(config);
        final String exported;
        try (StringWriter writer = new StringWriter()) {
            ConfigurationAsCode.serializeYamlNode(valueNode, writer);
            exported = writer.toString();
        } catch (IOException e) {
            throw new YAMLException(e);
        }

        assertThat("Message was not escaped", exported, not(containsString(message)));
        assertThat("Improper masking for PATH", exported, containsString("^${PATH}"));
        assertThat("Improper masking for JAVA_HOME", exported, containsString("^^${JAVA_HOME}"));
    }
}
