package org.artifactory.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.repo.RepoType;

import java.util.Arrays;
import java.util.List;
import java.util.OptionalLong;

import static org.artifactory.repo.config.RepoConfigDefaultValues.MIN_METADATA_CACHE_PERIOD_SECS;

/**
 * Utility class for validating remote repository metadata cache period requirements.
 * Enforces minimum cache period of 30 seconds with support for exclusions (smart repos, whitelisted URLs).
 * Smart-repo exclusion is driven by the {@code smartRepository} flag (typically content synchronisation enabled).
 * {@link RepoType#Generic} remotes are excluded: the limitation does not apply to generic package type.
 * Custom upstream rules (ConstantValues.remoteRepoCustomUpstreamMinCachePeriodRules) are evaluated first and
 * override every other exclusion, including the {@code remoteRepoEnforceMinimumMetadataCachePeriod} feature
 * flag, smart-repo, and whitelist. Each rule is a {@code urlPattern|packageType|minSeconds} triplet (e.g.
 * "chocolatey.org|NuGet|14400"); URL matching is a substring check (keep patterns specific to avoid
 * unintended matches), package-type matching is case-insensitive. Rules with non-positive
 * {@code minSeconds} or wrong segment count are ignored with a warning. The first matching rule wins.
 *
 * @author Ron Malka
 */
@Slf4j
@UtilityClass
public class RemoteRepoCachePeriodValidator {

    /**
     * Validates that the metadata retrieval cache period meets the minimum requirement of 30 seconds.
     * Generic package type ({@link RepoType#Generic}) is excluded — the limitation does not apply.
     * Custom upstream rules (ConstantValues.remoteRepoCustomUpstreamMinCachePeriodRules) are evaluated next
     * and enforced unconditionally — they override every exclusion below when a rule matches.
     * Exclusions for the standard 30-second minimum (only when no custom rule matches):
     * - Feature flag is disabled (ConstantValues.remoteRepoEnforceMinimumMetadataCachePeriod)
     * - Smart repositories ({@code smartRepository} true — e.g. content synchronisation enabled)
     * - URLs matching the whitelist prefix (ConstantValues.remoteRepoMinMetadataCachePeriodWhitelistUrls)
     *
     * @param repoUrl                  - The remote repository URL
     * @param retrievalCachePeriodSecs - The retrieval cache period in seconds
     * @param smartRepository          - Whether this remote is treated as a smart repository for this check
     * @param repoType                 - Repository / package type; limitation does not apply to
     *                                   {@link RepoType#Generic}
     * @throws IllegalArgumentException if validation fails
     */
    public static void assertValidValue(String repoUrl, long retrievalCachePeriodSecs, boolean smartRepository,
            RepoType repoType) throws IllegalArgumentException {
        if (repoType == RepoType.Generic) {
            log.debug("Skipping minimum cache period validation for Generic package type; URL: {}", repoUrl);
            return;
        }

        // Custom upstream rules take precedence unconditionally — feature flag, smart-repo, and whitelist do not bypass
        OptionalLong customMinPeriod = findCustomUpstreamMinPeriod(repoUrl, repoType);
        if (customMinPeriod.isPresent()) {
            long minPeriod = customMinPeriod.getAsLong();
            if (retrievalCachePeriodSecs < minPeriod) {
                String errorMessage = String.format(
                        "%s registry requires a metadata cache retrieval period of at least %d, current value : " +
                                "%d seconds", repoUrl, minPeriod, retrievalCachePeriodSecs);
                throwValidationError(errorMessage);
            }
            return;
        }

        if (!ConstantValues.remoteRepoEnforceMinimumMetadataCachePeriod.getBoolean()) {
            log.debug("Minimum metadata cache period validation is disabled by feature flag");
            return;
        }

        if (retrievalCachePeriodSecs >= MIN_METADATA_CACHE_PERIOD_SECS) {
            return;
        }

        if (smartRepository) {
            log.debug("Skipping minimum cache period validation for smart repository; URL: {}", repoUrl);
            return;
        }

        if (isWhitelistedUrl(repoUrl)) {
            log.debug("Skipping minimum cache period validation for whitelisted URL: {}", repoUrl);
            return;
        }

        throwValidationError(MIN_METADATA_CACHE_PERIOD_SECS, retrievalCachePeriodSecs);
    }

    /**
     * Returns the minimum period from the first rule in
     * {@link ConstantValues#remoteRepoCustomUpstreamMinCachePeriodRules} that matches the given URL and
     * package type. Each rule is a {@code urlPattern|packageType|minSeconds} triplet; URL matching is a
     * substring check, package-type matching is case-insensitive.
     */
    private static OptionalLong findCustomUpstreamMinPeriod(String repoUrl, RepoType repoType) {
        List<String> rules = ConstantValues.remoteRepoCustomUpstreamMinCachePeriodRules.getList();
        if (rules.isEmpty() || repoUrl == null || repoType == null) {
            return OptionalLong.empty();
        }

        return rules.stream().map(String::trim)
                .filter(StringUtils::isNotBlank)
                .map(rule -> parseCustomUpstreamRule(rule, repoUrl, repoType))
                .filter(OptionalLong::isPresent)
                .mapToLong(OptionalLong::getAsLong)
                .findFirst();
    }

    /**
     * Parses a single {@code urlPattern|packageType|minSeconds} rule and returns the minimum period when
     * the URL and package type match, or empty otherwise.
     */
    private static OptionalLong parseCustomUpstreamRule(String rule, String repoUrl, RepoType repoType) {
        String[] parts = StringUtils.split(rule, "|");
        if (parts.length != 3) {
            log.warn("Invalid custom upstream cache period rule (expected 'urlPattern|packageType|minSeconds'): {}",
                    rule);
            return OptionalLong.empty();
        }
        String urlPattern = parts[0].trim();
        String packageType = parts[1].trim();
        String minSecondsStr = parts[2].trim();

        if (!repoUrl.contains(urlPattern) || !repoType.name().equalsIgnoreCase(packageType)) {
            return OptionalLong.empty();
        }
        long minSeconds;
        try {
            minSeconds = Long.parseLong(minSecondsStr);
        } catch (NumberFormatException e) {
            log.warn("Invalid minSeconds in custom upstream cache period rule '{}': {}", rule, minSecondsStr);
            return OptionalLong.empty();
        }
        if (minSeconds <= 0) {
            log.warn("Non-positive minSeconds in custom upstream cache period rule '{}': {}", rule, minSeconds);
            return OptionalLong.empty();
        }
        log.debug("Custom upstream cache period rule matched: URL={}, packageType={}, minSeconds={}", repoUrl,
                repoType, minSeconds);
        return OptionalLong.of(minSeconds);
    }

    /**
     * Checks if the URL matches any of the whitelisted prefixes
     *
     * @param url - The URL to check
     * @return true if the URL starts with any whitelisted prefix, false otherwise
     */
    private static boolean isWhitelistedUrl(String url) {
        String whitelistedPrefixes = ConstantValues.remoteRepoMinMetadataCachePeriodWhitelistUrls.getString();
        if (StringUtils.isBlank(whitelistedPrefixes) || url == null) {
            return false;
        }

        return Arrays.stream(StringUtils.split(whitelistedPrefixes, ","))
                .map(String::trim)
                .anyMatch(url::startsWith);
    }

    private static void throwValidationError(long minPeriod, long actualPeriod) {
        String errorMessage = String.format(
                "Remote repository metadata retrieval cache period must be at least %d seconds. Current value: %d " +
                        "seconds.",
                minPeriod,
                actualPeriod
        );
        throwValidationError(errorMessage);
    }

    private static void throwValidationError(String errorMessage) {
        log.warn(errorMessage);
        throw new IllegalArgumentException(errorMessage);
    }
}
