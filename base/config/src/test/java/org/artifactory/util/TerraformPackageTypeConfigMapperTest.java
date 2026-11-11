package org.artifactory.util;

import org.artifactory.descriptor.repo.TerraformType;
import org.artifactory.repo.packagetype.configuration.TerraformConfiguration;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

import static org.testng.Assert.assertEquals;

@Test
public class TerraformPackageTypeConfigMapperTest {
    private final static long DEFAULT_MODEL_VERSION = 0;
    private final static String DEFAULT_TERRAFORM_REGISTRY_URL = "https://registry.terraform.io";
    private final static String DEFAULT_TERRAFORM_PROVIDERS_URL = "https://releases.hashicorp.com";
    private final static TerraformType DEFAULT_TERRAFORM_TYPE = TerraformType.MODULE;

    @Test
    public void testEmptyMap() {
        TerraformConfiguration terraformConfiguration = TerraformPackageTypeConfigMapper.INSTANCE.map(Map.of());
        assertEquals(DEFAULT_TERRAFORM_TYPE, terraformConfiguration.getTerraformType());
        assertEquals(DEFAULT_TERRAFORM_PROVIDERS_URL, terraformConfiguration.getTerraformProvidersUrl());
        assertEquals(DEFAULT_TERRAFORM_REGISTRY_URL, terraformConfiguration.getTerraformRegistryUrl());
        assertEquals(DEFAULT_MODEL_VERSION, terraformConfiguration.getModelVersion());
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = "Package type config can not be null")
    public void testNull() {
        TerraformPackageTypeConfigMapper.INSTANCE.map(null);
    }

    @Test
    public void testMap() {
        String registryUrl = "registryUrl";
        String providersUrl = "providersUrl";
        TerraformType terraformType = TerraformType.PROVIDER;
        long modelVersion = 10;

        Map<String, String> packageConfig = new HashMap<>();
        packageConfig.put("terraformRegistryUrl", registryUrl);
        packageConfig.put("terraformProvidersUrl", providersUrl);
        packageConfig.put("terraformType", terraformType.getName());
        packageConfig.put("modelVersion", Long.toString(modelVersion));
        packageConfig.put(ExternalDependenciesPackageTypeConfigMapper.EXTERNAL_DEPENDENCIES_ENABLED, "true");
        packageConfig.put(ExternalDependenciesPackageTypeConfigMapper.EXTERNAL_DEPENDENCIES_PATTERNS, "[\"1\"]");

        TerraformConfiguration terraformConfiguration = TerraformPackageTypeConfigMapper.INSTANCE.map(packageConfig);
        assertEquals(terraformType, terraformConfiguration.getTerraformType());
        assertEquals(providersUrl, terraformConfiguration.getTerraformProvidersUrl());
        assertEquals(registryUrl, terraformConfiguration.getTerraformRegistryUrl());
        assertEquals(modelVersion, terraformConfiguration.getModelVersion());
        assertEquals("1", terraformConfiguration.getExternalDependencies().getPatterns().get(0));
        assertEquals(true, terraformConfiguration.getExternalDependencies().isEnabled());
    }
}