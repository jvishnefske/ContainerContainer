package org.artifactory.addon.curation;

/**
 * @author Andrey Tashlitsky
 */
public record CurationSupportedRepository(String key, String packageType, String url, String project, boolean curated) {
}
