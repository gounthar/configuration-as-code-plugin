package io.jenkins.plugins.casc.impl.configurators;

import static io.jenkins.plugins.casc.Attribute.Setter.NOP;
import static io.jenkins.plugins.casc.ConfigurationAsCode.printThrowable;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Descriptor;
import io.jenkins.plugins.casc.Attribute;
import io.jenkins.plugins.casc.BaseConfigurator;
import io.jenkins.plugins.casc.ConfigurationContext;
import io.jenkins.plugins.casc.Configurator;
import io.jenkins.plugins.casc.RootElementConfigurator;
import io.jenkins.plugins.casc.model.CNode;
import io.jenkins.plugins.casc.model.Mapping;
import io.jenkins.plugins.casc.model.Scalar;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import jenkins.model.GlobalConfigurationCategory;
import jenkins.model.Jenkins;
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
@Restricted(NoExternalUse.class)
public class GlobalConfigurationCategoryConfigurator extends BaseConfigurator<GlobalConfigurationCategory>
        implements RootElementConfigurator<GlobalConfigurationCategory> {

    private static final Logger LOGGER = Logger.getLogger(GlobalConfigurationCategoryConfigurator.class.getName());

    private final GlobalConfigurationCategory category;

    public GlobalConfigurationCategoryConfigurator(GlobalConfigurationCategory category) {
        this.category = category;
    }

    @NonNull
    @Override
    public String getName() {
        return getNames().get(0);
    }

    @NonNull
    @Override
    public List<String> getNames() {
        final Class c = category.getClass();
        final Symbol symbol = (Symbol) c.getAnnotation(Symbol.class);
        if (symbol != null) {
            return asList(symbol.value());
        }

        String name = c.getSimpleName();
        name = StringUtils.remove(name, "Global");
        name = StringUtils.remove(name, "Configuration");
        name = StringUtils.remove(name, "Category");
        return singletonList(name.toLowerCase());
    }

    @Override
    public Class getTarget() {
        return category.getClass();
    }

    @Override
    public GlobalConfigurationCategory getTargetComponent(ConfigurationContext context) {
        return category;
    }

    @Override
    protected GlobalConfigurationCategory instance(Mapping mapping, ConfigurationContext context) {
        return category;
    }

    @SuppressWarnings("RedundantCast") // TODO remove once we are on JDK 11
    @NonNull
    @Override
    public Set describe() {
        return (Set) Jenkins.get().getExtensionList(Descriptor.class).stream()
                .filter(d -> d.getCategory() == category)
                .filter(d -> d.getGlobalConfigPage() != null)
                .map(DescriptorConfigurator::new)
                .filter(GlobalConfigurationCategoryConfigurator::reportDescriptorWithoutSetters)
                .map(c -> new Attribute<GlobalConfigurationCategory, Object>(c.getNames(), c.getTarget()).setter(NOP))
                .collect(Collectors.toSet());
    }

    public static boolean reportDescriptorWithoutSetters(Configurator c) {
        if (c.describe().isEmpty()) {
            LOGGER.fine(
                    c.getTarget().getName()
                            + " has a global view but CasC didn't detect any configurable attribute; see: https://jenkins.io/redirect/casc-requirements/");
        }
        return true;
    }

    @CheckForNull
    @Override
    public CNode describe(GlobalConfigurationCategory instance, ConfigurationContext context) {

        final Mapping mapping = new Mapping();
        Jenkins.get().getExtensionList(Descriptor.class).stream()
                .filter(this::filterDescriptors)
                .forEach(d -> describe(d, mapping, context));
        return mapping;
    }

    private void describe(Descriptor d, Mapping mapping, ConfigurationContext context) {
        final DescriptorConfigurator c = new DescriptorConfigurator(d);
        try {
            final CNode node = c.describe(d, context);
            if (node != null) {
                mapping.put(c.getName(), node);
            }
        } catch (Exception e) {
            final Scalar scalar = new Scalar("FAILED TO EXPORT\n" + d.getClass().getName() + " : " + printThrowable(e));
            mapping.put(c.getName(), scalar);
        }
    }

    private boolean filterDescriptors(Descriptor d) {
        return d.getCategory() == category && d.getGlobalConfigPage() != null;
    }
}
