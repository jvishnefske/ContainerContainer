package org.artifactory.util;

import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.config.ConfigurationException;
import org.artifactory.repo.remote.CustomHttpHeader;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.util.EnumMap;
import java.util.List;

import static org.testng.Assert.assertNotNull;

public class CustomHttpHeaderValidatorTest extends ArtifactoryHomeBoundTest {

    @AfterMethod
    public void clearCustomHeaderCaches() {
        ArtifactorySystemProperties props = ArtifactoryHome.get().getArtifactoryProperties();
        @SuppressWarnings("unchecked")
        EnumMap<ConstantValues, Boolean> boolCache =
                (EnumMap<ConstantValues, Boolean>) ReflectionTestUtils.getField(props, "artifactoryBooleanProperties");
        @SuppressWarnings("unchecked")
        EnumMap<ConstantValues, List<String>> listCache =
                (EnumMap<ConstantValues, List<String>>) ReflectionTestUtils.getField(
                        props, "artifactoryListProperties");
        boolCache.remove(ConstantValues.remoteRepoCustomHttpHeadersEnabled);
        listCache.remove(ConstantValues.remoteRepoCustomHttpHeadersBlockedPrefixes);
        listCache.remove(ConstantValues.remoteRepoCustomHttpHeadersBlockList);
        listCache.remove(ConstantValues.remoteRepoCustomHttpHeadersWhitelist);
    }

    @Test
    public void validate_shouldAllowValidHeaders() {
        List<CustomHttpHeader> headers = List.of(new CustomHttpHeader("X-Custom", "value", false));
        CustomHttpHeaderValidator.validate(headers);
        assertNotNull(headers);
    }

    @Test(expectedExceptions = ConfigurationException.class, expectedExceptionsMessageRegExp = ".*not allowed.*")
    public void validate_shouldRejectBlockedPrefix_whenPrefixesConfigured() {
        setBlockedPrefixesConfig(List.of("proxy-"));
        List<CustomHttpHeader> headers = List.of(new CustomHttpHeader("Proxy-Authorization", "v", false));
        CustomHttpHeaderValidator.validate(headers);
    }

    @Test
    public void validate_shouldAllowBlockedPrefix_whenPrefixesListEmpty() {
        setBlockedPrefixesConfig(List.of());
        List<CustomHttpHeader> headers = List.of(new CustomHttpHeader("Proxy-Authorization", "v", false));
        CustomHttpHeaderValidator.validate(headers);
        assertNotNull(headers);
    }

    @Test
    public void validate_shouldAllowEmpty_whenFeatureDisabled() {
        ArtifactorySystemProperties props = ArtifactoryHome.get().getArtifactoryProperties();
        props.setProperty("artifactory.remoteRepo.customHttpHeaders.enabled", "false");
        @SuppressWarnings("unchecked")
        EnumMap<ConstantValues, Boolean> boolCache =
                (EnumMap<ConstantValues, Boolean>) ReflectionTestUtils.getField(props, "artifactoryBooleanProperties");
        boolCache.put(ConstantValues.remoteRepoCustomHttpHeadersEnabled, false);
        try {
            CustomHttpHeaderValidator.validate(List.of());
            CustomHttpHeaderValidator.validate(null);
        } finally {
            props.setProperty("artifactory.remoteRepo.customHttpHeaders.enabled", "true");
            boolCache.put(ConstantValues.remoteRepoCustomHttpHeadersEnabled, true);
        }
    }

    @Test(expectedExceptions = ConfigurationException.class,
            expectedExceptionsMessageRegExp = ".*Custom HTTP headers are disabled.*")
    public void validate_shouldRejectNonEmpty_whenFeatureDisabled() {
        ArtifactorySystemProperties props = ArtifactoryHome.get().getArtifactoryProperties();
        props.setProperty("artifactory.remoteRepo.customHttpHeaders.enabled", "false");
        @SuppressWarnings("unchecked")
        EnumMap<ConstantValues, Boolean> boolCache =
                (EnumMap<ConstantValues, Boolean>) ReflectionTestUtils.getField(props, "artifactoryBooleanProperties");
        boolCache.put(ConstantValues.remoteRepoCustomHttpHeadersEnabled, false);
        try {
            List<CustomHttpHeader> headers = List.of(new CustomHttpHeader("Proxy-Authorization", "v", false));
            CustomHttpHeaderValidator.validate(headers);
        } finally {
            props.setProperty("artifactory.remoteRepo.customHttpHeaders.enabled", "true");
            boolCache.put(ConstantValues.remoteRepoCustomHttpHeadersEnabled, true);
        }
    }

    private void setBlockedPrefixesConfig(List<String> prefixes) {
        ArtifactorySystemProperties props = ArtifactoryHome.get().getArtifactoryProperties();
        props.setProperty("artifactory.remoteRepo.customHttpHeaders.blockedPrefixes", String.join(",", prefixes));
        @SuppressWarnings("unchecked")
        EnumMap<ConstantValues, List<String>> listCache =
                (EnumMap<ConstantValues, List<String>>) ReflectionTestUtils.getField(
                        props, "artifactoryListProperties");
        listCache.put(ConstantValues.remoteRepoCustomHttpHeadersBlockedPrefixes, prefixes);
    }
}
