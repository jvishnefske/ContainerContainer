package org.artifactory.storage;

import org.artifactory.repo.RepoPath;

/**
 * @author dudim
 */
public record GCCandidate(RepoPath repoPath, String sha1, String sha2, String md5, long length) {
}
