package org.artifactory.federation;

import org.testng.annotations.Test;

/**
 * @author annakv
 */
public class DefaultFederatedBinaryTasksServiceImplTest {

    @Test(expectedExceptions = UnsupportedOperationException.class)
    void replayTasks() {
        DefaultFederatedBinaryTasksServiceImpl d1 = new DefaultFederatedBinaryTasksServiceImpl();
        d1.replayTasks("repoName", "sha1");
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    void deleteRepoFailedTasksByBinary() {
        DefaultFederatedBinaryTasksServiceImpl d1 = new DefaultFederatedBinaryTasksServiceImpl();
        d1.deleteRepoFailedTasksByBinary("repoName", "sha1");
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    void deleteRepoFailedTasksByBinaryShaNull() {
        DefaultFederatedBinaryTasksServiceImpl d1 = new DefaultFederatedBinaryTasksServiceImpl();
        d1.deleteRepoFailedTasksByBinary("repoName", null);
    }

    @Test(expectedExceptions = NullPointerException.class)
    void deleteRepoFailedTasksByBinaryShaAndRepoNull() {
        DefaultFederatedBinaryTasksServiceImpl d1 = new DefaultFederatedBinaryTasksServiceImpl();
        d1.deleteRepoFailedTasksByBinary(null, null);
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    void deleteAllTasksPerRepo() {
        DefaultFederatedBinaryTasksServiceImpl d1 = new DefaultFederatedBinaryTasksServiceImpl();
        d1.deleteAllTasksPerRepo("repoName");
    }
    @Test(expectedExceptions = NullPointerException.class)
    void deleteAllTasksPerRepoNull() {
        DefaultFederatedBinaryTasksServiceImpl d1 = new DefaultFederatedBinaryTasksServiceImpl();
        d1.deleteAllTasksPerRepo(null);
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    void getFailingTasks() {
        DefaultFederatedBinaryTasksServiceImpl d1 = new DefaultFederatedBinaryTasksServiceImpl();
        d1.getFailingTasks("repoName", null, null);
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    void getBinariesTasksInfo() {
        DefaultFederatedBinaryTasksServiceImpl d1 = new DefaultFederatedBinaryTasksServiceImpl();
        d1.getBinariesTasksInfo("repoName");
    }

}
