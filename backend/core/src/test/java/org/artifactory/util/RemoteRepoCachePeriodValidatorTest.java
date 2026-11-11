package org.artifactory.util;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.artifactory.repo.config.RepoConfigDefaultValues.MIN_METADATA_CACHE_PERIOD_SECS;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link org.artifactory.util.RemoteRepoCachePeriodValidator }
 *
 * @author Ron Malka
 */
class RemoteRepoCachePeriodValidatorTest extends ArtifactoryHomeBoundJUnitTest {

    private static final String CHOCOLATEY_URL = "https://chocolatey.org/api/v2";
    // Matches the default value baked into ConstantValues.remoteRepoCustomUpstreamMinCachePeriodRules
    private static final long CHOCOLATEY_MIN_PERIOD_SECS = 14400;

    @BeforeEach
    void setUp() {
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        ArtifactoryContextThreadBinder.bind(context);
        // Reset to default state — feature flag enabled so validation runs; individual tests opt out by setting false
        getBound().setProperty(ConstantValues.remoteRepoEnforceMinimumMetadataCachePeriod, "true");
        getBound().setProperty(ConstantValues.remoteRepoMinMetadataCachePeriodWhitelistUrls, "");
        getBound().setProperty(ConstantValues.remoteRepoCustomUpstreamMinCachePeriodRules,
                "chocolatey.org|NuGet|" + CHOCOLATEY_MIN_PERIOD_SECS);
    }

    @AfterEach
    void tearDown() {
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void validate_shouldPass_whenFeatureFlagIsDisabled() {
        getBound().setProperty(ConstantValues.remoteRepoEnforceMinimumMetadataCachePeriod, "false");

        assertDoesNotThrow(
                () -> RemoteRepoCachePeriodValidator.assertValidValue("https://repo1.maven.org/maven2",
                        10L, false,
                        RepoType.Maven));
    }


    @Test
    void validate_shouldPass_whenCachePeriodMeetsMinimum() {
        getBound().setProperty(ConstantValues.remoteRepoEnforceMinimumMetadataCachePeriod, "true");

        assertDoesNotThrow(() -> RemoteRepoCachePeriodValidator.assertValidValue("https://repo1.maven.org/maven2",
                MIN_METADATA_CACHE_PERIOD_SECS, false, RepoType.Maven));
    }

    @Test
    void validate_shouldPass_whenCachePeriodExceedsMinimum() {
        getBound().setProperty(ConstantValues.remoteRepoEnforceMinimumMetadataCachePeriod, "true");

        assertDoesNotThrow(
                () -> RemoteRepoCachePeriodValidator.assertValidValue("https://repo1.maven.org/maven2",
                        3600L, false,
                        RepoType.Maven));
    }

    @Test
    void validate_shouldPass_whenSmartRepository() {
        getBound().setProperty(ConstantValues.remoteRepoEnforceMinimumMetadataCachePeriod, "true");

        assertDoesNotThrow(
                () -> RemoteRepoCachePeriodValidator.assertValidValue("https://acme.jfrog.io/artifactory/maven-remote",
                        10L, true, RepoType.Maven));
    }

    @Test
    void validate_shouldPass_whenSmartRepository_withNonJFrogUrl() {
        getBound().setProperty(ConstantValues.remoteRepoEnforceMinimumMetadataCachePeriod, "true");

        assertDoesNotThrow(
                () -> RemoteRepoCachePeriodValidator.assertValidValue("https://repo1.maven.org/maven2",
                        10L, true,
                        RepoType.Maven));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // Root whitelisted URL - tests exact prefix match
            "https://internal.company.com/repo",
            // Nested path under whitelisted URL
            "https://internal.company.com/repo/maven2",
            // Another whitelisted domain - tests multiple whitelist entries
            "https://legacy.system.com/artifacts/libs"})
    void validate_shouldPass_whenUrlIsWhitelisted(String url) {
        getBound().setProperty(ConstantValues.remoteRepoEnforceMinimumMetadataCachePeriod, "true");
        getBound().setProperty(ConstantValues.remoteRepoMinMetadataCachePeriodWhitelistUrls,
                "https://internal.company.com,https://legacy.system.com");

        assertDoesNotThrow(() -> RemoteRepoCachePeriodValidator.assertValidValue(url, 10L,
                false, RepoType.Maven));
    }

    @Test
    void validate_shouldFail_whenCachePeriodBelowMinimum() {
        getBound().setProperty(ConstantValues.remoteRepoEnforceMinimumMetadataCachePeriod, "true");
        String url = "https://repo1.maven.org/maven2";
        long cachePeriod = 5;
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> RemoteRepoCachePeriodValidator.assertValidValue(url, cachePeriod, false, RepoType.Maven));

        assertTrue(exception.getMessage().contains("must be at least 30 seconds"));
        assertTrue(exception.getMessage().contains("Current value: " + cachePeriod));
    }

    @Test
    void validate_shouldFail_whenCachePeriodBelowMinimumWithCorrectErrorMessage() {
        getBound().setProperty(ConstantValues.remoteRepoEnforceMinimumMetadataCachePeriod, "true");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> RemoteRepoCachePeriodValidator.assertValidValue("https://repo1.maven.org/maven2",
                        15L, false,
                        RepoType.Maven));

        String expectedMessage = String.format(
                "Remote repository metadata retrieval cache period must be at least %d seconds. Current value: 15 " +
                        "seconds.", MIN_METADATA_CACHE_PERIOD_SECS);
        assertEquals(expectedMessage, exception.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://repo1.maven.org/maven2", "https://notjfrog.io/repo",
            "https://acme.jfrog" + ".io/artifactory/maven-remote", ""})
    void validate_shouldFail_whenSmartRepositoryDisabledWithLowCachePeriod(String url) {
        getBound().setProperty(ConstantValues.remoteRepoEnforceMinimumMetadataCachePeriod, "true");

        assertThrows(IllegalArgumentException.class,
                () -> RemoteRepoCachePeriodValidator.assertValidValue(url, 10L,
                        false, RepoType.Maven));
    }

    @Test
    void validate_shouldFail_whenUrlNotWhitelistedWithLowCachePeriod() {
        String url = "https://external.maven.org/repo";
        getBound().setProperty(ConstantValues.remoteRepoEnforceMinimumMetadataCachePeriod, "true");
        getBound().setProperty(ConstantValues.remoteRepoMinMetadataCachePeriodWhitelistUrls,
                "https://internal.company.com");

        assertThrows(IllegalArgumentException.class,
                () -> RemoteRepoCachePeriodValidator.assertValidValue(url, 10L,
                        false, RepoType.Maven));
    }

    @Test
    void validate_shouldPass_whenUrlPrefixWhitelistedWithLowCachePeriod() {
        String url = "https://internal.company.com.fake/repo";
        getBound().setProperty(ConstantValues.remoteRepoEnforceMinimumMetadataCachePeriod, "true");
        getBound().setProperty(ConstantValues.remoteRepoMinMetadataCachePeriodWhitelistUrls,
                "https://internal.company.com");

        assertDoesNotThrow(() -> RemoteRepoCachePeriodValidator.assertValidValue(url, 10L,
                false, RepoType.Maven));
    }

    @Test
    void validate_shouldPass_whenUrlIsNullAndCachePeriodIsValid() {
        getBound().setProperty(ConstantValues.remoteRepoEnforceMinimumMetadataCachePeriod, "true");

        assertDoesNotThrow(
                () -> RemoteRepoCachePeriodValidator.assertValidValue(null, MIN_METADATA_CACHE_PERIOD_SECS,
                        false,
                        RepoType.Maven));
    }

    @Test
    void validate_shouldFail_whenUrlIsNullAndCachePeriodIsInvalid() {
        getBound().setProperty(ConstantValues.remoteRepoEnforceMinimumMetadataCachePeriod, "true");

        assertThrows(IllegalArgumentException.class,
                () -> RemoteRepoCachePeriodValidator.assertValidValue(null, 10L,
                        false, RepoType.Maven));
    }

    @Test
    void validate_shouldPass_whenWhitelistIsEmptyAndSmartRepositoryEnabled() {
        getBound().setProperty(ConstantValues.remoteRepoEnforceMinimumMetadataCachePeriod, "true");
        getBound().setProperty(ConstantValues.remoteRepoMinMetadataCachePeriodWhitelistUrls, "");

        assertDoesNotThrow(
                () -> RemoteRepoCachePeriodValidator.assertValidValue("https://acme.jfrog.io/artifactory/maven-remote/",
                        10L, true, RepoType.Maven));
    }

    @Test
    void validate_shouldHandleWhitelistWithSpaces() {
        getBound().setProperty(ConstantValues.remoteRepoEnforceMinimumMetadataCachePeriod, "true");
        getBound().setProperty(ConstantValues.remoteRepoMinMetadataCachePeriodWhitelistUrls,
                "  https://internal.company.com  ,  https://legacy.system.com  ");

        assertDoesNotThrow(
                () -> RemoteRepoCachePeriodValidator.assertValidValue("https://internal.company.com/repo",
                        10L, false,
                        RepoType.Maven));
    }

    @Test
    void validate_shouldPass_whenUrlIsNullSmartRepositoryAndLowCachePeriod() {
        getBound().setProperty(ConstantValues.remoteRepoEnforceMinimumMetadataCachePeriod, "true");

        assertDoesNotThrow(() -> RemoteRepoCachePeriodValidator.assertValidValue(null, 10L,
                true, RepoType.Maven));
    }

    @Test
    void validate_shouldPass_whenGenericPackageTypeWithSubMinimumCachePeriod() {
        getBound().setProperty(ConstantValues.remoteRepoEnforceMinimumMetadataCachePeriod, "true");

        assertDoesNotThrow(
                () -> RemoteRepoCachePeriodValidator.assertValidValue("https://repo1.maven.org/maven2",
                        5L, false,
                        RepoType.Generic));
    }

    @Test
    void validate_shouldFail_whenDockerPackageTypeWithSubMinimumCachePeriod() {
        getBound().setProperty(ConstantValues.remoteRepoEnforceMinimumMetadataCachePeriod, "true");

        assertThrows(IllegalArgumentException.class,
                () -> RemoteRepoCachePeriodValidator.assertValidValue("https://registry-1.docker.io/",
                        10L, false,
                        RepoType.Docker));
    }

    // --- Custom upstream minimum cache period (urlPattern|packageType|minSeconds rules) ---

    @Test
    void validateShouldFailWhenCustomUpstreamRuleMatchesAndPeriodBelowRuleMinimum() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> RemoteRepoCachePeriodValidator.assertValidValue(CHOCOLATEY_URL, MIN_METADATA_CACHE_PERIOD_SECS,
                        false, RepoType.NuGet));
        String message = exception.getMessage();
        assertTrue(message.contains(CHOCOLATEY_URL));
        assertTrue(message.contains(
                "metadata cache retrieval period of at least " + CHOCOLATEY_MIN_PERIOD_SECS));
        assertTrue(message.contains("current value : " + MIN_METADATA_CACHE_PERIOD_SECS));
    }

    @Test
    void validateShouldFailWhenCustomUpstreamRuleMatchesEvenWhenFeatureFlagIsDisabled() {
        // Custom upstream rules are enforced unconditionally — the feature flag does not bypass them
        getBound().setProperty(ConstantValues.remoteRepoEnforceMinimumMetadataCachePeriod, "false");

        assertThrows(IllegalArgumentException.class,
                () -> RemoteRepoCachePeriodValidator.assertValidValue(CHOCOLATEY_URL, MIN_METADATA_CACHE_PERIOD_SECS,
                        false, RepoType.NuGet));
    }

    @Test
    void validateShouldFailWhenCustomUpstreamRuleMatchesAndSmartRepository() {
        // Smart-repo does NOT bypass a custom upstream rule
        assertThrows(IllegalArgumentException.class,
                () -> RemoteRepoCachePeriodValidator.assertValidValue(CHOCOLATEY_URL, 60L, true, RepoType.NuGet));
    }

    @Test
    void validateShouldFailWhenCustomUpstreamRuleMatchesAndUrlIsWhitelisted() {
        getBound().setProperty(ConstantValues.remoteRepoMinMetadataCachePeriodWhitelistUrls, "https://chocolatey.org");

        // Whitelist does NOT bypass a custom upstream rule
        assertThrows(IllegalArgumentException.class,
                () -> RemoteRepoCachePeriodValidator.assertValidValue(CHOCOLATEY_URL, 60L, false, RepoType.NuGet));
    }

    @Test
    void validateShouldPassWhenCustomUpstreamRuleMatchesAndPeriodMeetsRuleMinimum() {
        assertDoesNotThrow(() -> RemoteRepoCachePeriodValidator.assertValidValue(CHOCOLATEY_URL,
                CHOCOLATEY_MIN_PERIOD_SECS, false, RepoType.NuGet));
    }

    @Test
    void validateShouldUseStandardMinimumWhenChocolateyUrlButNonNuGetType() {
        // Custom upstream rule does not apply for non-NuGet type; standard minimum (30s) is sufficient
        assertDoesNotThrow(
                () -> RemoteRepoCachePeriodValidator.assertValidValue(CHOCOLATEY_URL, MIN_METADATA_CACHE_PERIOD_SECS,
                        false, RepoType.Maven));
    }

    @Test
    void validateShouldUseStandardMinimumWhenNuGetTypeButNonChocolateyUrl() {
        // Custom upstream rule does not apply for non-chocolatey URL; standard minimum (30s) is sufficient
        assertDoesNotThrow(() -> RemoteRepoCachePeriodValidator.assertValidValue("https://api.nuget.org/v3/index.json",
                MIN_METADATA_CACHE_PERIOD_SECS, false, RepoType.NuGet));
    }

    @Test
    void validateShouldApplyCustomUpstreamRuleWhenConfiguredForDifferentUpstream() {
        getBound().setProperty(ConstantValues.remoteRepoCustomUpstreamMinCachePeriodRules,
                "registry.example.com|Docker|300");

        assertThrows(IllegalArgumentException.class,
                () -> RemoteRepoCachePeriodValidator.assertValidValue("https://registry.example.com/v2",
                        60L, false, RepoType.Docker));
    }

    @Test
    void validateShouldSupportMultipleCustomUpstreamRules() {
        getBound().setProperty(ConstantValues.remoteRepoCustomUpstreamMinCachePeriodRules,
                "chocolatey.org|NuGet|14400,registry.example.com|Docker|2400");

        assertThrows(IllegalArgumentException.class,
                () -> RemoteRepoCachePeriodValidator.assertValidValue(CHOCOLATEY_URL, 60L, false, RepoType.NuGet));
        assertThrows(IllegalArgumentException.class,
                () -> RemoteRepoCachePeriodValidator.assertValidValue("https://registry.example.com/v2",
                        60L, false, RepoType.Docker));
    }

    @Test
    void validateShouldPassWhenCustomUpstreamRulesEmpty() {
        getBound().setProperty(ConstantValues.remoteRepoCustomUpstreamMinCachePeriodRules, "");

        // Falls back to standard minimum (30s)
        assertDoesNotThrow(
                () -> RemoteRepoCachePeriodValidator.assertValidValue(CHOCOLATEY_URL, MIN_METADATA_CACHE_PERIOD_SECS,
                        false, RepoType.NuGet));
    }

    @Test
    void validateShouldIgnoreRuleWhenSegmentCountIsWrong() {
        // Two segments instead of three — rule is malformed and should be ignored with a warning
        getBound().setProperty(ConstantValues.remoteRepoCustomUpstreamMinCachePeriodRules, "chocolatey.org|NuGet");

        // Falls back to standard 30s minimum
        assertDoesNotThrow(
                () -> RemoteRepoCachePeriodValidator.assertValidValue(CHOCOLATEY_URL, MIN_METADATA_CACHE_PERIOD_SECS,
                        false, RepoType.NuGet));
        // And still fails when below the standard minimum
        assertThrows(IllegalArgumentException.class,
                () -> RemoteRepoCachePeriodValidator.assertValidValue(CHOCOLATEY_URL, 10L, false, RepoType.NuGet));
    }

    @Test
    void validateShouldIgnoreRuleWhenMinSecondsIsUnparseable() {
        getBound().setProperty(ConstantValues.remoteRepoCustomUpstreamMinCachePeriodRules,
                "chocolatey.org|NuGet|notANumber");

        // Falls back to standard 30s minimum
        assertDoesNotThrow(
                () -> RemoteRepoCachePeriodValidator.assertValidValue(CHOCOLATEY_URL, MIN_METADATA_CACHE_PERIOD_SECS,
                        false, RepoType.NuGet));
    }

    @Test
    void validateShouldIgnoreRuleWhenMinSecondsIsNonPositive() {
        getBound().setProperty(ConstantValues.remoteRepoCustomUpstreamMinCachePeriodRules,
                "chocolatey.org|NuGet|-1");

        // Negative minSeconds must not silently disable enforcement; falls back to standard 30s minimum
        assertThrows(IllegalArgumentException.class,
                () -> RemoteRepoCachePeriodValidator.assertValidValue(CHOCOLATEY_URL, 10L, false, RepoType.NuGet));
        assertDoesNotThrow(
                () -> RemoteRepoCachePeriodValidator.assertValidValue(CHOCOLATEY_URL, MIN_METADATA_CACHE_PERIOD_SECS,
                        false, RepoType.NuGet));
    }

    @Test
    void validateShouldIgnoreRuleWhenMinSecondsIsZero() {
        getBound().setProperty(ConstantValues.remoteRepoCustomUpstreamMinCachePeriodRules,
                "chocolatey.org|NuGet|0");

        // Zero minSeconds is treated as invalid; falls back to standard 30s minimum
        assertThrows(IllegalArgumentException.class,
                () -> RemoteRepoCachePeriodValidator.assertValidValue(CHOCOLATEY_URL, 10L, false, RepoType.NuGet));
    }

    @Test
    void validateShouldUseFirstMatchingRuleWhenMultipleRulesMatchSameUpstream() {
        // Both rules match chocolatey.org+NuGet; first rule (14400s) should win
        getBound().setProperty(ConstantValues.remoteRepoCustomUpstreamMinCachePeriodRules,
                "chocolatey.org|NuGet|14400,chocolatey|NuGet|60");

        // 14400 (first rule) is enforced — value of 100s fails the first rule's 14400 minimum
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> RemoteRepoCachePeriodValidator.assertValidValue(CHOCOLATEY_URL, 100L, false, RepoType.NuGet));
        String message = exception.getMessage();
        assertTrue(message.contains(CHOCOLATEY_URL));
        assertTrue(message.contains(
                "metadata cache retrieval period of at least " + CHOCOLATEY_MIN_PERIOD_SECS));
        assertTrue(message.contains("current value : 100"));
    }

    @Test
    void validateShouldSkipMalformedRulesAndApplyValidOne() {
        // First rule malformed (wrong part count), second rule unparseable, third rule valid
        getBound().setProperty(ConstantValues.remoteRepoCustomUpstreamMinCachePeriodRules,
                "broken|rule,chocolatey.org|NuGet|notANumber,chocolatey.org|NuGet|14400");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> RemoteRepoCachePeriodValidator.assertValidValue(CHOCOLATEY_URL, 100L, false, RepoType.NuGet));
        String message = exception.getMessage();
        assertTrue(message.contains(CHOCOLATEY_URL));
        assertTrue(message.contains(
                "metadata cache retrieval period of at least " + CHOCOLATEY_MIN_PERIOD_SECS));
        assertTrue(message.contains("current value : 100"));
    }
}
