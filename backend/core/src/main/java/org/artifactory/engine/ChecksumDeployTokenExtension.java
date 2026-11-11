package org.artifactory.engine;

/**
 * Object to hold all the relevant data to load on the scoped token.
 *
 * @param repoKey  Target repository key to deploy the file.
 * @param repoPath Target path to deploy the file.
 * @param sha1     The SHA1 checksum of the complete file.
 * @param sha2     The SHA256 checksum of the complete file.
 */
public record ChecksumDeployTokenExtension(String repoKey, String repoPath, String sha1, String sha2) {
}
