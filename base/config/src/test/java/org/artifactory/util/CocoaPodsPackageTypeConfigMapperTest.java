package org.artifactory.util;

import com.google.common.collect.Maps;
import org.artifactory.repo.packagetype.configuration.CocoaPodsConfiguration;
import org.artifactory.repo.vcs.VcsGitProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;


/**
 * @author Mor Merhav
 */
class CocoaPodsPackageTypeConfigMapperTest {

    @Test
    void testStandardMapping() {
        Map<String, String> packageTypeConfig =
                Map.of(CocoaPodsPackageTypeConfigMapper.MODEL_VERSION, "1",
                        ExternalDependenciesPackageTypeConfigMapper.EXTERNAL_DEPENDENCIES_ENABLED, "true",
                        ExternalDependenciesPackageTypeConfigMapper.EXTERNAL_DEPENDENCIES_REMOTE_REPO_REF,
                        "remote-repo",
                        ExternalDependenciesPackageTypeConfigMapper.EXTERNAL_DEPENDENCIES_PATTERNS,
                        "[\"1\",\"2\",\"3\"]",
                        CocoaPodsPackageTypeConfigMapper.SPECS_REPO_DOWNLOAD_PROVIDER, "BITBUCKET",
                        CocoaPodsPackageTypeConfigMapper.COCOA_PODS_SPECS_URL, "https://cocoa-pods-specs-url.com/",
                        PackageTypeConfigFields.COCOA_PODS_CDN_URL.getKey(), "https://cocoa-pods-cdn-url.com/",
                        CocoaPodsPackageTypeConfigMapper.SPECS_REPO_DOWNLOAD_URL, "https://specs-repo-download-url.com/"
                );

        CocoaPodsConfiguration cocoaPodsConfiguration =
                CocoaPodsPackageTypeConfigMapper.INSTANCE.map(packageTypeConfig);
        Assertions.assertTrue(cocoaPodsConfiguration.getExternalDependencies().isEnabled());
        Assertions.assertEquals(1, cocoaPodsConfiguration.getModelVersion());
        Assertions.assertEquals("remote-repo", cocoaPodsConfiguration.getExternalDependencies().getRemoteRepoRef());
        Assertions.assertEquals("1", cocoaPodsConfiguration.getExternalDependencies().getPatterns().get(0));
        Assertions.assertEquals("2", cocoaPodsConfiguration.getExternalDependencies().getPatterns().get(1));
        Assertions.assertEquals("3", cocoaPodsConfiguration.getExternalDependencies().getPatterns().get(2));
        Assertions.assertEquals("https://cocoa-pods-specs-url.com/", cocoaPodsConfiguration.getCocoaPodsSpecsRepoUrl());
        Assertions.assertEquals("https://cocoa-pods-cdn-url.com/", cocoaPodsConfiguration.getCocoaPodsCdnUrl());
        Assertions.assertEquals("https://specs-repo-download-url.com/",
                cocoaPodsConfiguration.getSpecRepoProvider().getDownloadUrl());
        Assertions.assertEquals(VcsGitProvider.BITBUCKET, cocoaPodsConfiguration.getSpecRepoProvider().getProvider());
    }

    @Test
    void testAllNullsMapping() {
        Map<String, String> packageTypeConfig = Maps.newHashMap();
        packageTypeConfig.put(CocoaPodsPackageTypeConfigMapper.MODEL_VERSION, null);
        packageTypeConfig.put(ExternalDependenciesPackageTypeConfigMapper.EXTERNAL_DEPENDENCIES_ENABLED, null);
        packageTypeConfig.put(ExternalDependenciesPackageTypeConfigMapper.EXTERNAL_DEPENDENCIES_REMOTE_REPO_REF, null);
        packageTypeConfig.put(ExternalDependenciesPackageTypeConfigMapper.EXTERNAL_DEPENDENCIES_PATTERNS, null);

        CocoaPodsConfiguration cocoaPodsConfiguration =
                CocoaPodsPackageTypeConfigMapper.INSTANCE.map(packageTypeConfig);

        Assertions.assertFalse(cocoaPodsConfiguration.getExternalDependencies().isEnabled());
        Assertions.assertEquals(0L, cocoaPodsConfiguration.getModelVersion());
        Assertions.assertNull(cocoaPodsConfiguration.getExternalDependencies().getRemoteRepoRef());
        Assertions.assertNull(cocoaPodsConfiguration.getExternalDependencies().getPatterns());
        Assertions
                .assertEquals("https://github.com/CocoaPods/Specs", cocoaPodsConfiguration.getCocoaPodsSpecsRepoUrl());
        Assertions.assertEquals("https://cdn.cocoapods.org", cocoaPodsConfiguration.getCocoaPodsCdnUrl());
        Assertions.assertNull(cocoaPodsConfiguration.getSpecRepoProvider().getDownloadUrl());
        Assertions.assertEquals(VcsGitProvider.GITHUB, cocoaPodsConfiguration.getSpecRepoProvider().getProvider());
    }

    @Test
    void testEmptyListMapping() {
        Map<String, String> packageTypeConfig = Maps.newHashMap();
        packageTypeConfig.put(ExternalDependenciesPackageTypeConfigMapper.EXTERNAL_DEPENDENCIES_PATTERNS, "[]");

        CocoaPodsConfiguration bowerConfiguration = CocoaPodsPackageTypeConfigMapper.INSTANCE.map(packageTypeConfig);

        Assertions.assertNotNull(bowerConfiguration.getExternalDependencies().getPatterns());
        Assertions.assertTrue(bowerConfiguration.getExternalDependencies().getPatterns().isEmpty());
    }

    @Test
    void testCdnUrlWithTrailingSpaceIsTrimmed() {
        Map<String, String> packageTypeConfig = Maps.newHashMap();
        packageTypeConfig.put(PackageTypeConfigFields.COCOA_PODS_CDN_URL.getKey(), "https://cdn.cocoapods.org ");

        CocoaPodsConfiguration config = CocoaPodsPackageTypeConfigMapper.INSTANCE.map(packageTypeConfig);

        Assertions.assertEquals("https://cdn.cocoapods.org", config.getCocoaPodsCdnUrl());
    }

    @Test
    void testSpecsRepoUrlWithTrailingSpaceIsTrimmed() {
        Map<String, String> packageTypeConfig = Maps.newHashMap();
        packageTypeConfig.put(CocoaPodsPackageTypeConfigMapper.COCOA_PODS_SPECS_URL,
                "https://github.com/CocoaPods/Specs ");

        CocoaPodsConfiguration config = CocoaPodsPackageTypeConfigMapper.INSTANCE.map(packageTypeConfig);

        Assertions.assertEquals("https://github.com/CocoaPods/Specs", config.getCocoaPodsSpecsRepoUrl());
    }

    @Test
    void testUrlsWithLeadingAndTrailingSpacesAreTrimmed() {
        CocoaPodsConfiguration config = new CocoaPodsConfiguration();
        config.setCocoaPodsCdnUrl("  https://cdn.cocoapods.org  ");
        config.setCocoaPodsSpecsRepoUrl("  https://github.com/CocoaPods/Specs  ");

        Assertions.assertEquals("https://cdn.cocoapods.org", config.getCocoaPodsCdnUrl());
        Assertions.assertEquals("https://github.com/CocoaPods/Specs", config.getCocoaPodsSpecsRepoUrl());
    }

    @Test
    void testSetNullUrlsHandledGracefully() {
        CocoaPodsConfiguration config = new CocoaPodsConfiguration();
        config.setCocoaPodsCdnUrl(null);
        config.setCocoaPodsSpecsRepoUrl(null);

        Assertions.assertNull(config.getCocoaPodsCdnUrl());
        Assertions.assertNull(config.getCocoaPodsSpecsRepoUrl());
    }
}