package org.artifactory.repo;

import org.jfrog.rtfs.module.binariesTasks.Task;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PersistedRemoteTaskInfoTest {

    @Test
    void testFromBinaryTask() {
        Task task = Task.newBuilder()
                .setSha1("dummySha1")
                .setRepoKey("dummyRepoKey")
                .setInsertTime(123456789L)
                .setFailureCount(3)
                .build();

        PersistedRemoteTaskInfo info = PersistedRemoteTaskInfo.fromBinaryTask(task);

        assertEquals("dummySha1", info.sha1());
        assertEquals("dummyRepoKey", info.repoKey());
        assertEquals(123456789L, info.creationTime());
        assertEquals(3, info.failuresCount());
    }
}