package org.artifactory.repo.service.validation;

import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.util.AolUtils;
import org.artifactory.util.HttpUtils;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Oleksandr Legenkiy
 */
@Slf4j
@Component
public class RepoKeyLengthLimitsHelper {

    private static final int DEFAULT_REPO_KEY_LENGTH_LIMIT = 64; // max repo key length allowed by Artifactory
    private static final int DOCKER_GROUP_SAAS_REPO_KEY_LENGTH_LIMIT = 63; // max length of DNS record subdomain
    private static final int REMOTE_REPO_KEY_LENGTH_LIMIT = 58; // 64 - "-cache".length()
    private static final String SCHEME_DELIMITER = "://";

    /**
     * Provides maximum length of a repo key - a so-called "hard limit" for the repo key length. It's 58 chars for
     * remote repos, 63 chars for Docker-group repos on Saas, 64 chars for the rest of the repos.
     */
    public int hardLimit(@NonNull RepoDetailsType repoType, @NonNull RepoType repoPackageType) {
        if (RepoDetailsType.REMOTE == repoType) {
            return REMOTE_REPO_KEY_LENGTH_LIMIT;
        }
        if (AolUtils.isAol() && repoPackageType.isDockerGroup()) {
            return DOCKER_GROUP_SAAS_REPO_KEY_LENGTH_LIMIT;
        }
        return DEFAULT_REPO_KEY_LENGTH_LIMIT;
    }

    /**
     * If "soft limit for max repo key length" feature is enabled and all conditions satisfied, then a "soft limit" is
     * returned. Otherwise, <code>null</code> - indicating that currently there is no soft limit for this package type.
     * Soft limit is calculated only for Docker-group repos on Saas envs and only if "Docker subdomain method" is used.
     * In this case (Server name length + 1) is subtracted from the "hard limit".
     * <p/>Example for server name "abcd": softLimit = 63 - "-abcd".length() = 58.
     *
     * @param repoPackageType package type
     * @return soft limit for the max repo key length if relevant. Otherwise, <code>null</code>
     */
    @Nullable
    public Integer softLimit(@NonNull RepoType repoPackageType) {
        if (shouldUseSoftLimit(repoPackageType)) {
            String serverName = getServerNameForDnsRecord();
            return calculateSoftLimit(serverName);
        }
        return null;
    }

    private Integer calculateSoftLimit(String serverName) {
        if (StringUtils.isNotBlank(serverName)) {
            // DNS records are not created for remote-cache repos, so no need to handle remote repos specifically
            return DOCKER_GROUP_SAAS_REPO_KEY_LENGTH_LIMIT - serverName.length() - 1;
        }
        return null;
    }

    /**
     * Checks if repoKey length is greater than a "soft limit" for repo key length. If a "soft limit" is not defined for
     * the specified repoPackageType (or Artifactory deployment type), then the result will always be
     * <code>false</code>.
     * See {@link RepoKeyLengthLimitsHelper#softLimit(RepoType)} for more details on "soft limit" concept.
     *
     * @param repoKey         repo key to check. Cannot be null.
     * @param repoPackageType package type. Cannot be null.
     * @return a {@link RepoKeyLengthLimitsHelper.SoftLimitCheckResult} record object containing either
     * <code>true</code> or <code>false</code> result and a <code>details</code> string providing additional
     * description and reasoning. Never <code>null</code>.
     */
    @Nonnull
    public SoftLimitCheckResult repoKeyExceedsSoftLimit(@NonNull String repoKey, @NonNull RepoType repoPackageType) {
        if (shouldUseSoftLimit(repoPackageType)) {
            String serverName = getServerNameForDnsRecord();
            Integer softLimit = calculateSoftLimit(serverName);
            if (softLimit != null && repoKey.length() > softLimit) {
                String dockerDnsRecordDomain = serverName + "-" + repoKey;
                String reason = ("Total length of server name and repository key '%s' exceeds the maximum of " +
                        "%s characters").formatted(dockerDnsRecordDomain, DOCKER_GROUP_SAAS_REPO_KEY_LENGTH_LIMIT);
                return new SoftLimitCheckResult(true, reason);
            }
            return new SoftLimitCheckResult(false, "Soft limit not exceeded");
        }
        return new SoftLimitCheckResult(false, "No soft limit applied");
    }

    /**
     * Returns current server name (not a "technical" name). For example, for a customer that uses the following url
     * "...://abcde.jfrog.io/...", a server name should be "abcde". This server name is supposed to be used when a DNS
     * record is created during a repo creation flow.
     * NOTE: this method is designed be called only if Artifactory runs in Saas env
     */
    @VisibleForTesting
    @Nonnull
    String getServerNameForDnsRecord() {
        if (!AolUtils.isAol()) {
            log.warn("Unexpected call to 'getServerNameForDnsRecord'. This code is supposed to be called only on" +
                    " cloud envs. Skipping.");
            return "";
        }
        try {
            String artifactoryUrl = HttpUtils.getArtifactoryUrlFromRequestIfPresent();
            log.trace("Artifactory URL: '{}'", artifactoryUrl);
            String serverName = StringUtils.substringBetween(artifactoryUrl, SCHEME_DELIMITER,
                    ConstantValues.aolServerUrlTopLevelDomains.getString());
            if (StringUtils.isNotBlank(serverName)) {
                log.trace("DNS record server name was successfully retrieved: '{}'", serverName);
                return serverName;
            }
        } catch (Exception e) {
            log.error("An error occurred when trying to obtain Artifactory server URL from http request", e);
        }
        log.warn("Failed to load server name for DNS record. Repo key length soft limit will not be calculated " +
                "correctly.");
        return "";
    }

    private boolean shouldUseSoftLimit(RepoType repoPackageType) {
        return ConstantValues.enableMaxRepoKeyLengthSoftLimit.getBoolean() &&
                AolUtils.isAol() && repoPackageType.isDockerGroup() && dockerSubdomainMethodEnabledOnSaas();
    }

    private boolean dockerSubdomainMethodEnabledOnSaas() {
        // On Saas, the reverse proxy info is not stored in descriptor, so the only way is to use this property
        return !ConstantValues.aolDockerSkipCreatingDnsRecord.getBoolean();
    }

    public record SoftLimitCheckResult(boolean softLimitExceeded, String details) {
    }

    /**
     * RFC 952 A "name" (Net, Host, Gateway, or Domain name) is a text string up
     * to 24 characters drawn from the alphabet (A-Z), digits (0-9), minus
     * sign (-), and period (.).
     */
    public Set<Character> getForbiddenChars() {
        return ConstantValues.aolForbiddenRepoKeyChars.getString().chars()
                .mapToObj(c -> (char) c)
                .collect(Collectors.toSet());
    }
}
