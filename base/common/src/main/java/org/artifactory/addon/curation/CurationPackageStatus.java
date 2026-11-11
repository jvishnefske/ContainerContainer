package org.artifactory.addon.curation;

/**
 * @author Andrey Tashlitsky
 */
public record CurationPackageStatus(boolean permitted, String message, SecurityDecision securityDecision) {
}
