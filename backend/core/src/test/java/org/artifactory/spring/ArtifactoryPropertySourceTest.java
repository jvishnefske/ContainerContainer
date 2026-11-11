package org.artifactory.spring;

import org.apache.commons.lang3.reflect.MethodUtils;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.junit.jupiter.api.Test;

import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;


class ArtifactoryPropertySourceTest {
    // Note: we cannot simply create the ArtifactoryPropertySource in the @BeforeEach handler, because the SysConfig
    // under the hood pre-fills the configuration from environment variables and system properties, and will not
    // re-read them later, making it hard for us to affect it in the test.
    static ArtifactoryPropertySource createPropertySource() {
        URL homePath = ArtifactoryPropertySourceTest.class.getResource("/home");
        if (homePath == null) {
            throw new IllegalStateException("Cannot find the test resources directory");
        }

        ArtifactoryHome artifactoryHome = new ArtifactoryHome(homePath.getPath());
        try {
            MethodUtils.invokeMethod(artifactoryHome, true, "initArtifactorySystemPropertiesInternal");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return new ArtifactoryPropertySource(
                "artifactory",
                artifactoryHome
        );
    }

    @Test
    void getProperty_shouldReturnNull_whenPropertyIsNotFoundAndHasNoDefault() {
        ArtifactoryPropertySource source = createPropertySource();
        assertNull(source.getProperty("nonExistingProperty"));
    }

    @Test
    void getProperty_shouldReturnDefaultValueFromConstantValues() {
        ArtifactoryPropertySource source = createPropertySource();
        assertEquals(
                ConstantValues.overrideContextPath.getDefValue(),
                source.getProperty(ConstantValues.overrideContextPath.getPropertyName())
        );
    }

    @Test
    void getProperty_shouldReturnValueFromJFrogSystemProperty() {
        try {
            // SysConfig will automatically resolve properties that have the "jf." prefix (it will remove it)
            System.setProperty("jf.artifactory.context.path", "/custom");
            ArtifactoryPropertySource source = createPropertySource();
            assertEquals("/custom", source.getProperty("artifactory.context.path"));
        } finally {
            System.clearProperty("jf.artifactory.context.path");
        }
    }

    @Test
    void getProperty_shouldReturnValueFromJFrogSharedSystemProperty() {
        try {
            // SysConfig will automatically resolve properties from the shared configuration (those having
            // the "jf.shared" prefix)
            System.setProperty("jf.shared.context.path", "/custom");
            ArtifactoryPropertySource source = createPropertySource();
            assertEquals("/custom", source.getProperty("artifactory.context.path"));
        } finally {
            System.clearProperty("jf.artifactory.context.path");
        }
    }

    @Test
    void getProperty_shouldReturnArtifactorySpecificValueFromSystemYaml() {
        // SysConfig will automatically resolve properties from the system.yaml
        ArtifactoryPropertySource source = createPropertySource();
        assertEquals("artifactory-test", source.getProperty("artifactory.custom.name"));
    }

    @Test
    void getProperty_shouldReturnSharedConfigValueFromSystemYaml() {
        // SysConfig will automatically resolve properties from the shared section of the system.yaml
        ArtifactoryPropertySource source = createPropertySource();
        assertEquals("XXX-YYY", source.getProperty("artifactory.custom.id"));
    }
}