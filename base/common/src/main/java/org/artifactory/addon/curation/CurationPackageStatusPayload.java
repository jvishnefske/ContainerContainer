package org.artifactory.addon.curation;

import com.google.common.collect.Multimap;
import lombok.Builder;

import java.util.Map;

/**
 * @param packageType      For example, NPM.
 * @param packageUrl       The relative path to this package.
 * @param repoKey          The repository containing this package.
 * @param projectKey       The project associated with this package.
 * @param user             The user that initiated the request.
 * @param email            The email of the user that initiated the request.
 * @param originRepoKey    The repository from which the request was originated.
 * @param originProjectKey The project from which the request was originated.
 * @param originServer     The server from which the request was originated.
 * @param downloadUrl      The remote file download URL.
 * @param responseHeaders  The response headers received from the remote.
 * @param passThrough      True if the request is a pass-through request.
 * @param waiverRequest    The message of Curation's waiver request, if it exists
 * @param fromCachedRepo   The repository is a cached repo for remote repo
 * @param created          The timestamp when the artifact was first created/cached in Artifactory
 * @param lastModified     The timestamp when the artifact was last modified in Artifactory
 * @param requestHeaders   Incoming {@code X-Artifactory-Curation-*} headers. May be empty; never null
 * @author Andrey Tashlitsky
 */
@Builder
public record CurationPackageStatusPayload(String packageType, String packageUrl, String repoKey, String projectKey,
                                           String user, String email, String originRepoKey, String originProjectKey,
                                           String originServer, String downloadUrl,
                                           Multimap<String, String> responseHeaders, boolean passThrough,
                                           String waiverRequest, boolean fromCachedRepo, Long created,
                                           Long lastModified, Map<String, String> requestHeaders) {
}
