package org.artifactory.addon.curation;

/**
 * @author Tomer Slutsky
 */
public record CuratedRepoEvent(String repoKey, String repoUrl, String repoType, String projectKey,
                               CuratedRepoEventType eventType) {
}
