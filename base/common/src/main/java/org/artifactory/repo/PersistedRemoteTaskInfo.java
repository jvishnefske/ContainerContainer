package org.artifactory.repo;

import org.jfrog.rtfs.module.binariesTasks.Task;

/**
 * @author annakv
 */
public record PersistedRemoteTaskInfo(String sha1, String repoKey, long creationTime, int failuresCount) {

    public static PersistedRemoteTaskInfo fromBinaryTask(Task binaryTask) {
        return new PersistedRemoteTaskInfo(binaryTask.getSha1(),
                binaryTask.getRepoKey(),
                binaryTask.getInsertTime(),
                binaryTask.getFailureCount());
    }
}
