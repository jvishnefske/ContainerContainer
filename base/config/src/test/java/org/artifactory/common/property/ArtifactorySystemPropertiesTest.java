package org.artifactory.common.property;

import org.artifactory.common.ConstantValues;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArtifactorySystemPropertiesTest {

    @Test
    void whenPropertyValueIsNull() {
        ArtifactorySystemProperties properties = new ArtifactorySystemProperties();
        String propertyName = ConstantValues.artifactoryServiceVersion.getPropertyName();
        properties.setProperty(propertyName, null);

        Properties copy = properties.getPropertiesCopy();

        assertEquals("null", copy.getProperty(propertyName));
    }

}