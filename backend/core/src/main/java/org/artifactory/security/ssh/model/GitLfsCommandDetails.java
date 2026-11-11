package org.artifactory.security.ssh.model;

/**
 * @author Noam Y. Tenne
 */
public record GitLfsCommandDetails(String repoKey, String operation, String blobDigest) {
}
