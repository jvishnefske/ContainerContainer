package org.artifactory.addon.onboarding;

import org.artifactory.common.addon.Addon;
import org.artifactory.addon.onboarding.model.OnBoardingRecord;
import org.artifactory.descriptor.repo.RepoType;

import java.util.Map;
import java.util.Set;

import static org.apache.http.HttpStatus.SC_NOT_FOUND;

/**
 * Addon for handling interactive OnBoarding tutorials for multiple users nad package types.
 */
public interface OnBoardingAddon extends Addon {
    /**
     * Determine whether the authenticated user and a repository are qualified for an onBoarding tutorial.
     */
    default void assertRepoIsSupported(String repoKey) throws OnBoardingException {
        throw new OnBoardingException("Unsupported operation", SC_NOT_FOUND);
    }

    /**
     * Determine whether the authenticated user and a set of repositories are qualified for an onBoarding tutorial.
     */
    default Map<String, Boolean> assertRepoIsSupported(Set<String> repoKeys) throws OnBoardingException {
        throw new OnBoardingException("Unsupported operation", SC_NOT_FOUND);
    }

    /**
     * Start a new onBoarding tutorial.
     * The user details will be taken for the authorization and the repoType based on the repoKey.
     */
    default boolean startOnBoardingTutorial(String repoKey, String downloadPackageName, String downloadVersion,
            String uploadPackageName, String uploadVersion) throws OnBoardingException {
        throw new OnBoardingException("Unsupported operation", SC_NOT_FOUND);
    }

    /**
     * Get the current status of an ongoing quickstart session.
     */
    default OnBoardingRecord getStatus(String repoKey) throws OnBoardingException {
        throw new OnBoardingException("Unsupported operation", SC_NOT_FOUND);
    }

    /**
     * Notify the service when a user successfully generated a token.
     */
    default void userGeneratedToken(RepoType repoType, String userName) {
    }

    /**
     * Notify the service when a user successfully downloaded a package.
     */
    default void userDownloadedPackage(RepoType repoType, String repoKey, String packageName,
            String version) {
    }

    /**
     * Notify the service when a user successfully uploaded a package.
     */
    default void userUploadedPackage(RepoType repoType, String repoKey, String packageName,
            String version) {
    }

    /**
     * Update the given {@link OnBoardingRecord} in the onBoarding cache.
     */
    default void updateOnBoardingCacheRecord(OnBoardingRecord onBoardingRecord) {
    }
}
