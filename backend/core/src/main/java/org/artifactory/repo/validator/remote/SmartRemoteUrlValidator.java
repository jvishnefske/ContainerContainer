package org.artifactory.repo.validator.remote;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.ConstantValues;
import org.artifactory.config.ConfigurationException;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RepoConfig;
import org.artifactory.ph.PackageSpecification;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.interceptor.RepoConfigChangeValidator;
import org.artifactory.repo.interceptor.RepoConfigChanges;
import org.artifactory.util.SmartRepoUrlUtils;
import org.springframework.stereotype.Component;

import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_CREATE;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_UPDATE;
import static org.artifactory.repo.RepoDetailsType.REMOTE;

/**
 * Validates that remote repository URLs for smart remote repositories contain the required package-specific URL prefix
 * The validator performs a soft comparison, meaning it ignores trailing slashes and query params and is
 * case-insensitive.
 *
 * @see SmartRepoUrlUtils#isSmartRepoUrlRequired
 * @author Mor Merhav
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SmartRemoteUrlValidator implements RepoConfigChangeValidator {

    private static final String SMART_REPO_URL_CONFIG_DOCS_LINK =
            "https://www.jfrog.com/confluence/display/JFROG/Configure+a+Smart+Remote+Repository";
    private final PackageSpecification packageSpecification;

    @Override
    public void validate(RepoConfigChanges repoConfigChanges) {
        if (!ConstantValues.autoBlockInvalidSmartRepoUrl.getBoolean()) {
            log.debug("Skipping smart remote URL validation as '{}' is set to false",
                    ConstantValues.autoBlockInvalidSmartRepoUrl.getPropertyName());
            return;
        }

        for (RepoConfigChange change : repoConfigChanges.filterBy(REMOTE, REPO_CREATE, REPO_UPDATE)) {
            RepoConfig newRepoConfig = change.getNewRepoConfig();
            if (newRepoConfig != null) {
                String url = newRepoConfig.getUrl();
                RepoType packageType = newRepoConfig.getPackageType();
                String prefixForSmartUrl = packageSpecification.getPrefixForSmartUrl(packageType);
                if (isPackageSpecificUrlRequiredButNotSet(url, prefixForSmartUrl)) {
                    throw new ConfigurationException("""
                            Smart remote repository URL: '%s' for package type: '%s'
                            must contain the package-specific URL prefix.
                            For more information, refer to the documentation: %s
                            """
                            .formatted(url, packageType.getDisplayName(), SMART_REPO_URL_CONFIG_DOCS_LINK));
                }
            }
        }
    }

    /**
     * Checks whether the given URL is an Artifactory URL that requires a package-specific URL prefix, but does not
     * have it set.
     * <p>
     * Validation logic:
     * - If URL matches smart format → skip validation (return false)
     * - If URL matches standard format → continue validation (check if it should be smart)
     * - If URL doesn't match either format → skip validation (return false)
     *
     * @param url               the URL to check
     * @param prefixForSmartUrl the package-specific URL prefix
     * @return true if the URL is an Artifactory URL that requires a package-specific URL prefix but does not have it
     * set, false otherwise
     */
    private boolean isPackageSpecificUrlRequiredButNotSet(String url, String prefixForSmartUrl) {
        if (url == null || prefixForSmartUrl == null) {
            return false;
        }
        // If URL already matches smart format, skip validation
        if (SmartRepoUrlUtils.matchesSmartUrlFormat(url, prefixForSmartUrl)) {
            return false;
        }
        // If URL matches standard format, check if it should be smart
        if (SmartRepoUrlUtils.matchesStandardUrlFormat(url)) {
            // Check if this is an Artifactory URL that requires smart format
            return SmartRepoUrlUtils.isSmartRepoUrlRequired(url, prefixForSmartUrl);
        }
        // If URL doesn't match either format, skip validation (even if it contains /artifactory/)
        return false;
    }
}