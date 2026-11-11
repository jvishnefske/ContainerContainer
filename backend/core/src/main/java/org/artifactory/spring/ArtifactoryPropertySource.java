package org.artifactory.spring;

import jakarta.annotation.Nonnull;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.springframework.core.env.PropertySource;

import java.util.HashMap;
import java.util.Map;

/**
 * This class introduces Artifactory system configuration to Spring as a property source.  This bridge
 * allows using Artifactory-specific configuration logic in Spring annotations. For example, it allows
 * {@link ConditionalOnProperty} annotation to react on properties, defined in the system.yaml file or
 * in some JF_SHARED_... environment variable.
 * <p>
 * Note: in addition to the standard inheritance logic from the {@link com.jfrog.sysconf.SysConfig},
 * the {@link ArtifactoryPropertySource} class also supports default values for properties, defined in
 * the {@link ConstantValues} enumeration. If a property is not found in the system configuration, the default
 * value will be used.
 * </p>
 *
 * @author Sergey Steinvil
 */
public class ArtifactoryPropertySource extends PropertySource<ArtifactoryHome> {
    // The default values for properties, defined in the ConstantValues enum.
    private static final Map<String, Object> DEFAULTS;

    static {
        DEFAULTS = new HashMap<>(ConstantValues.values().length);
        for (ConstantValues constant : ConstantValues.values()) {
            DEFAULTS.put(constant.getPropertyName(), constant.getDefValue());
        }
    }

    public ArtifactoryPropertySource(String name, ArtifactoryHome source) {
        super(name, source);
    }

    @Override
    public Object getProperty(@Nonnull String name) {
        // First, we should try the system configuration that inherits values from multiple sources: system.yaml,
        // system properties, environment variables, shared configuration, etc.
        Object value = this.source.getSysConfig().get(name, null);
        if (value != null) {
            return value;
        }
        value = this.source.getArtifactoryProperties().getPropertiesCopy().getProperty(name);
        if (value != null && !value.equals("null")) {
            return value;
        }

        // If the property is not found in the system configuration, we should give a chance to defaults,
        // coming from the {@link ConstantValues} class.
        return DEFAULTS.get(name);
    }
}
