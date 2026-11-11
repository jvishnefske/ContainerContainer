package org.artifactory.addon.curation;

/**
 * @author Andrey Tashlitsky
 */
public record CachedPackageEvent(String packageType, String packageUrl, String repoKey, String projectKey,
                                 String user) {
}
