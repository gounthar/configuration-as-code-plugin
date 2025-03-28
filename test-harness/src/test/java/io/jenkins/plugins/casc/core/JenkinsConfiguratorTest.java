package io.jenkins.plugins.casc.core;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.model.TaskListener;
import hudson.model.labels.LabelAtom;
import hudson.model.labels.LabelAtomProperty;
import hudson.model.labels.LabelAtomPropertyDescriptor;
import hudson.security.FullControlOnceLoggedInAuthorizationStrategy;
import hudson.security.HudsonPrivateSecurityRealm;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import hudson.util.DescribableList;
import io.jenkins.plugins.casc.ConfigurationAsCode;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import io.jenkins.plugins.casc.misc.junit.jupiter.WithJenkinsConfiguredWithCode;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Objects;
import jenkins.model.Jenkins;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.Symbol;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
@WithJenkinsConfiguredWithCode
class JenkinsConfiguratorTest {

    @Test
    @ConfiguredWithCode("Primitives.yml")
    void jenkins_primitive_attributes(JenkinsConfiguredWithCodeRule j) {
        final Jenkins jenkins = Jenkins.get();
        assertEquals(6666, jenkins.getSlaveAgentPort());
    }

    @Test
    @ConfiguredWithCode("HeteroDescribable.yml")
    void jenkins_abstract_describable_attributes(JenkinsConfiguredWithCodeRule j) {
        final Jenkins jenkins = Jenkins.get();
        assertInstanceOf(HudsonPrivateSecurityRealm.class, jenkins.getSecurityRealm());
        assertInstanceOf(FullControlOnceLoggedInAuthorizationStrategy.class, jenkins.getAuthorizationStrategy());
        assertFalse(((FullControlOnceLoggedInAuthorizationStrategy) jenkins.getAuthorizationStrategy())
                .isAllowAnonymousRead());
    }

    @Test
    @Issue("Issue #173")
    @ConfiguredWithCode("SetEnvironmentVariable.yml")
    void shouldSetEnvironmentVariable(JenkinsConfiguredWithCodeRule j) throws Exception {
        final DescribableList<NodeProperty<?>, NodePropertyDescriptor> properties =
                Jenkins.get().getNodeProperties();
        EnvVars env = new EnvVars();
        for (NodeProperty<?> property : properties) {
            property.buildEnvVars(env, TaskListener.NULL);
        }
        assertEquals("BAR", env.get("FOO"));
    }

    @Test
    @ConfiguredWithCode("ConfigureLabels.yml")
    void shouldExportLabelAtoms(JenkinsConfiguredWithCodeRule j) throws Exception {
        Objects.requireNonNull(Jenkins.get().getLabelAtom("label1"))
                .getProperties()
                .add(new TestProperty(1));

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        ConfigurationAsCode.get().export(out);
        final String exported = out.toString();

        String content = FileUtils.readFileToString(
                new File(getClass()
                        .getResource("ExpectedLabelsConfiguration.yml")
                        .toURI()),
                "UTF-8");
        assertThat(exported, containsString(content));
    }

    @Test
    @ConfiguredWithCode("ConfigureLabels.yml")
    void shouldImportLabelAtoms(JenkinsConfiguredWithCodeRule j) {
        LabelAtom label1 = Jenkins.get().getLabelAtom("label1");
        assertNotNull(label1);
        assertThat(label1.getProperties(), hasSize(2));
        assertEquals(2, label1.getProperties().get(TestProperty.class).value);
        assertEquals(4, label1.getProperties().get(AnotherTestProperty.class).otherProperty);

        LabelAtom label2 = Jenkins.get().getLabelAtom("label2");
        assertNotNull(label2);
        assertThat(label2.getProperties(), hasSize(1));
        assertEquals(3, label2.getProperties().get(TestProperty.class).value);
    }

    public static class TestProperty extends LabelAtomProperty {

        public final int value;

        @DataBoundConstructor
        public TestProperty(int value) {
            this.value = value;
        }

        @TestExtension
        @Symbol("myProperty")
        public static class DescriptorImpl extends LabelAtomPropertyDescriptor {
            @NonNull
            @Override
            public String getDisplayName() {
                return "A simple value";
            }
        }
    }

    public static class AnotherTestProperty extends LabelAtomProperty {

        public final int otherProperty;

        @DataBoundConstructor
        public AnotherTestProperty(int otherProperty) {
            this.otherProperty = otherProperty;
        }

        @TestExtension
        public static class DescriptorImpl extends LabelAtomPropertyDescriptor {
            @NonNull
            @Override
            public String getDisplayName() {
                return "Another simple value";
            }
        }
    }
}
