package org.artifactory.repo.cleanup;

import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.schedule.TaskService;
import org.artifactory.storage.db.fs.service.JobsService;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.test.ArtifactoryHomeStub;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.artifactory.common.ConstantValues.jobsTableTimeToLiveMillis;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * @author Yoaz Menda
 */
public class JobsTableCleanupServiceImplTest extends ArtifactoryHomeBoundTest {

    private JobsTableCleanupService jobsTableCleanupService;
    @Mock
    private JobsService jobsService;
    @Mock
    private TaskService taskService;
    private AutoCloseable mocks;

    @BeforeMethod
    public void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        jobsTableCleanupService = new JobsTableCleanupServiceImpl(jobsService, taskService);
        homeStub.setProperty(jobsTableTimeToLiveMillis, "3000");
    }

    @AfterMethod
    public void tearDown() throws Exception {
        ArtifactoryHome.unbind();
        mocks.close();
    }

    public ArtifactoryHomeStub getOrCreateArtifactoryHomeStub() {
        if (homeStub == null) {
            homeStub = new ArtifactoryHomeStub("testhome");
            homeStub.loadSystemProperties();
        }
        return homeStub;
    }

    @Test
    public void testClean() {
        when(jobsService.deleteJobsStartedBefore(anyLong())).thenReturn(1);
        jobsTableCleanupService.clean();
    }

    @Test
    public void testExceptionDuringClean() {
        when(jobsService.deleteJobsStartedBefore(anyLong())).thenThrow(new RuntimeException("STOP"));
        jobsTableCleanupService.clean();
    }
}