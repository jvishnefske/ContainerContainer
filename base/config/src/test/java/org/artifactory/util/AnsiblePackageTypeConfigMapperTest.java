package org.artifactory.util;

import com.google.common.collect.Maps;
import org.artifactory.repo.packagetype.configuration.AnsibleConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.artifactory.util.PackageTypeConfigFields.AUTH_URL;

class AnsiblePackageTypeConfigMapperTest {

    @Test
    void testStandardMapping() {
        Map<String, String> packageTypeConfig = Map.of(
                AUTH_URL.getKey(), "https://sso.example.com/auth");

        AnsibleConfiguration config = AnsiblePackageTypeConfigMapper.INSTANCE.map(packageTypeConfig);
        Assertions.assertEquals("https://sso.example.com/auth", config.getAuthUrl());
    }

    @Test
    void testEmptyMapDefaultsToEmptyString() {
        Map<String, String> packageTypeConfig = Maps.newHashMap();

        AnsibleConfiguration config = AnsiblePackageTypeConfigMapper.INSTANCE.map(packageTypeConfig);
        Assertions.assertNotNull(config);
        Assertions.assertEquals("", config.getAuthUrl());
    }

    @Test
    void testNullValueDefaultsToEmptyString() {
        Map<String, String> packageTypeConfig = Maps.newHashMap();
        packageTypeConfig.put(AUTH_URL.getKey(), null);

        AnsibleConfiguration config = AnsiblePackageTypeConfigMapper.INSTANCE.map(packageTypeConfig);
        Assertions.assertEquals("", config.getAuthUrl());
    }
}
