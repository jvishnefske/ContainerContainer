package org.artifactory.util;

import org.artifactory.descriptor.repo.RepoType;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Priyanshu Gupta
 */
class LocalDefaultPackageConfigSetterTest {

    @Test
    void testSetDefaultValuesForMavenGroup() {
        Map<String, String> packageTypeConfig = new HashMap<>();
        LocalDefaultPackageConfigSetter.setDefaultValues(RepoType.Maven, packageTypeConfig);

        assertEquals("true", packageTypeConfig.get(PackageTypeConfigFields.SUPPRESS_POM_CONSISTENCY_CHECKS.getKey()));
    }

    @Test
    void testSetDefaultValuesForDocker() {
        Map<String, String> packageTypeConfig = new HashMap<>();
        LocalDefaultPackageConfigSetter.setDefaultValues(RepoType.Docker, packageTypeConfig);

        assertEquals(PackageTypeConfigFields.DOCKER_API_VERSION.getDefaultValue(),
                packageTypeConfig.get(PackageTypeConfigFields.DOCKER_API_VERSION.getKey()));
        assertEquals(PackageTypeConfigFields.DOCKER_TAG_RETENTION.getDefaultValue(),
                packageTypeConfig.get(PackageTypeConfigFields.DOCKER_TAG_RETENTION.getKey()));
        assertEquals(PackageTypeConfigFields.BLOCK_PUSHING_SCHEMA_1.getDefaultValue(),
                packageTypeConfig.get(PackageTypeConfigFields.BLOCK_PUSHING_SCHEMA_1.getKey()));
    }

    @Test
    void testSetDefaultValuesForTerraform() {
        Map<String, String> packageTypeConfig = new HashMap<>();
        LocalDefaultPackageConfigSetter.setDefaultValues(RepoType.Terraform, packageTypeConfig);

        assertEquals(PackageTypeConfigFields.TERRAFORM_TYPE.getDefaultValue(),
                packageTypeConfig.get(PackageTypeConfigFields.TERRAFORM_TYPE.getKey()));
    }

    @Test
    void testSetDefaultValuesForTerraformBackend() {
        Map<String, String> packageTypeConfig = new HashMap<>();
        LocalDefaultPackageConfigSetter.setDefaultValues(RepoType.TerraformBackend, packageTypeConfig);

        assertEquals(PackageTypeConfigFields.ENCRYPT_STATES.getDefaultValue(),
                packageTypeConfig.get(PackageTypeConfigFields.ENCRYPT_STATES.getKey()));
    }

    @Test
    void testSetDefaultValuesForDebian() {
        Map<String, String> packageTypeConfig = new HashMap<>();
        LocalDefaultPackageConfigSetter.setDefaultValues(RepoType.Debian, packageTypeConfig);

        assertEquals(PackageTypeConfigFields.OPTIONAL_INDEX_COMPRESSION_FORMATS.getDefaultValue(),
                packageTypeConfig.get(PackageTypeConfigFields.OPTIONAL_INDEX_COMPRESSION_FORMATS.getKey()));
    }
}