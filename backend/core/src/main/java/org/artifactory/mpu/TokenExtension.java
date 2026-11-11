package org.artifactory.mpu;

/**
 * Object to hold all the relevant data to load on the scoped token.
 *
 * @param repoKey     Target repository key to deploy the file.
 * @param repoPath    Target path to deploy the file.
 * @param uploadId    Unique ID representing the multipart upload transaction.
 * @param tempPathKey Temporary path to store the parts.
 * @author Andrey Tashlitsky
 */
record TokenExtension(String repoKey, String repoPath, String uploadId, String tempPathKey) {

}
