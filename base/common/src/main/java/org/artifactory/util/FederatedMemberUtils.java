package org.artifactory.util;

import jakarta.annotation.Nonnull;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.descriptor.repo.FederatedMember;
import org.artifactory.repo.ArtifactoryFederationServiceException;
import org.jfrog.client.util.PathUtils;

import java.util.ArrayList;
import java.util.List;

import static org.artifactory.repo.federated.FederatedMember.BASE_URL_PLACEHOLDER;

/**
 * @author Tamar Rosen
 */
@UtilityClass
@Slf4j
public class FederatedMemberUtils {

    public static String resolveUrl(FederatedMember member) {
        return resolveUrl(member, ContextHelper.get().getCentralConfig());
    }
    public static String resolveUrl(FederatedMember member, CentralConfigService centralConfigService) {
        return resolveUrl(member.getUrlOrTemplate(), centralConfigService);
    }
    public static String resolveUrl(org.artifactory.repo.federated.FederatedMember member) {
        return resolveUrl(member, ContextHelper.get().getCentralConfig());
    }
    public static String resolveUrl(org.artifactory.repo.federated.FederatedMember member,
            CentralConfigService centralConfigService) {
        return resolveUrl(member.getUrlOrTemplate(), centralConfigService);
    }

    public static String resolveUrl(String url, CentralConfigService configService) {

        if (!url.contains(BASE_URL_PLACEHOLDER)) {
            return url;
        }

        if (configService == null || configService.getDescriptor() == null) {
            log.warn("Cannot resolve the local member's url '{}': Artifactory's config descriptor is not ready.", url);
            return url;
        }
        String baseUrl = configService.getDescriptor().getFederatedRepoUrlBaseOrDefault();
        return resolveUrl(url, baseUrl);
    }

    /**
     * Resolves a URL that may contain {@link org.artifactory.repo.federated.FederatedMember#BASE_URL_PLACEHOLDER}
     * by replacing it with the given base URL. Use this when the base URL is already known (e.g. from a validation
     * model) and config service is not available. The base URL is normalized (trailing slashes trimmed, /artifactory
     * appended if missing) for consistency with {@link #resolveUrl(String, CentralConfigService)}.
     *
     * @param url     the URL or template (may contain placeholder)
     * @param baseUrl the base URL to substitute for the placeholder; may be null or blank
     * @return the URL with placeholder replaced by normalized baseUrl, or the original url if no placeholder or
     *         baseUrl is blank
     */
    public static String resolveUrl(String url, String baseUrl) {
        if (StringUtils.isBlank(url) || !url.contains(BASE_URL_PLACEHOLDER)) {
            return url;
        }
        if (StringUtils.isBlank(baseUrl)) {
            return url;
        }
        String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
        return url.replace(BASE_URL_PLACEHOLDER, normalizedBaseUrl);
    }

    @Nonnull
    public static String normalizeBaseUrl(String baseUrl) {
        String normalizedBaseUrl = PathUtils.trimTrailingSlashes(baseUrl);
        return normalizedBaseUrl.endsWith("/artifactory") ? normalizedBaseUrl : normalizedBaseUrl + "/artifactory";
    }

    /**
     * @return true if the member is the repository's own (self) member, identified by a URL that starts with the
     * {@link org.artifactory.repo.federated.FederatedMember#BASE_URL_PLACEHOLDER} template prefix. This mirrors
     * {@link org.artifactory.model.FederationConfig#isFederated()} and {@link #createSelfMember(String)}, which both
     * treat the placeholder as the leading segment of a self member's URL.
     */
    public static boolean isSelfMember(org.artifactory.repo.federated.FederatedMember member) {
        String url = member.getUrlOrTemplate();
        return url != null && url.startsWith(BASE_URL_PLACEHOLDER);
    }

    /**
     * Builds the repository's own (self) federation member with the base-URL placeholder template.
     */
    public static org.artifactory.repo.federated.FederatedMember createSelfMember(String repoKey) {
        org.artifactory.repo.federated.FederatedMember self = new org.artifactory.repo.federated.FederatedMember();
        self.setUrlOrTemplate(BASE_URL_PLACEHOLDER + "/" + repoKey);
        self.setEnabled(true);
        return self;
    }

    /**
     * Returns a copy of the given members list guaranteed to contain the repository's own (self) member: the self
     * member ({@link #createSelfMember}) is appended only when the list does not already contain one, so an existing
     * self member is never duplicated. The input list is not modified.
     */
    public static List<org.artifactory.repo.federated.FederatedMember> membersWithSelfMember(
            List<org.artifactory.repo.federated.FederatedMember> members, String repoKey) {
        List<org.artifactory.repo.federated.FederatedMember> withSelf = new ArrayList<>(members);
        if (withSelf.stream().noneMatch(FederatedMemberUtils::isSelfMember)) {
            withSelf.add(createSelfMember(repoKey));
        }
        return withSelf;
    }

    public static boolean isLocal(FederatedMember member, String key, String baseUrl) {
        String unresolvedUrl = member.getUrlOrTemplate();
        return isLocal(unresolvedUrl, key, baseUrl);
    }

    public static boolean isLocal(org.artifactory.repo.federated.FederatedMember member, String key, String baseUrl) {
        String unresolvedUrl = member.getUrlOrTemplate();
        return isLocal(unresolvedUrl, key, baseUrl);
    }

    private static boolean isLocal(String url, String key, String baseUrl) {
        String repoKey = PathUtils.getLastPathElement(url);
        return (url.contains(BASE_URL_PLACEHOLDER) || url.contains(baseUrl)) && key.equals(repoKey);
    }

    public static boolean isOldFormatLocal(org.artifactory.repo.federated.FederatedMember member, String repoKey,
            String baseUrl) {
        return isOldFormatLocal(member.getUrlOrTemplate(), repoKey, baseUrl);
    }

    public static boolean isOldFormatLocal(FederatedMember member, String repoKey, String baseUrl) {
        return isOldFormatLocal(member.getUrlOrTemplate(), repoKey, baseUrl);
    }

    private boolean isOldFormatLocal(String url, String key, String baseUrl) {
        if (StringUtils.isBlank(baseUrl)) {
            throw new RuntimeException("Custom base URL must be configured for federated repositories' functionality");
        }
        String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
        String repoKey = PathUtils.getLastPathElement(url);
        return url.contains(normalizedBaseUrl) && key.equals(repoKey);
    }

    public static void convertToNewLocalMember(org.artifactory.repo.federated.FederatedMember federatedMember,
            String baseUrl) {
        String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
        String convertedUrl = federatedMember.getUrlOrTemplate().replace(normalizedBaseUrl, BASE_URL_PLACEHOLDER);
        federatedMember.setUrl(convertedUrl);
        log.debug("Converting local member: '{}' to contain a placeholder url: '{}'...",
                federatedMember.getUrlOrTemplate(), convertedUrl);
    }

    /**
     * Retrieves the local FederatedMember object from the list of FederatedMembers
     * based on the source repository key and the base URL.
     *
     * @param sourceRepoKey       The key of the source repository to identify the local member.
     * @param newFederatedMembers The list of FederatedMember objects to search for the local member.
     * @return The local FederatedMember object associated with the specified source repository key and base URL.
     * @throws ArtifactoryFederationServiceException if the local member cannot be found in the list of
     *                                               FederatedMembers.
     */
    public org.artifactory.repo.federated.FederatedMember getLocalMember(String sourceRepoKey,
            List<org.artifactory.repo.federated.FederatedMember> newFederatedMembers,
            CentralConfigService centralConfigService) throws ArtifactoryFederationServiceException {
        String baseUrl = centralConfigService.getDescriptor().getFederatedRepoUrlBaseOrDefault();
        return newFederatedMembers.stream()
                .filter(federatedMember -> isLocal(federatedMember, sourceRepoKey,
                        baseUrl)).findFirst()
                .orElseThrow(() -> new ArtifactoryFederationServiceException("Failed to identify local member"));
    }
}
