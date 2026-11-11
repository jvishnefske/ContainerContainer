package org.artifactory.hqc.cleanup;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.schedule.TaskService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.test.ArtifactoryHomeStub;
import org.jfrog.storage.hqc.service.HeavyQueryCacheService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.Spy;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.util.stream.Stream;

import static org.artifactory.common.ConstantValues.hqcCleanupJobCron;
import static org.artifactory.hqc.cleanup.HeavyQueryCacheCleanupJobService.CLEANUP_JOB_DEFAULT_CRON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * @author Andrey Tashlitsky
 */
class HeavyQueryCacheCleanupJobTest extends ArtifactoryHomeBoundJUnitTest {

    private final ArtifactoryHomeStub artifactoryHomeStub = getOrCreateArtifactoryHomeStub();
    @Mock
    private ArtifactoryContext artifactoryContext;
    @Mock
    private HeavyQueryCacheService heavyQueryCacheService;
    @Mock
    private JobExecutionContext jobExecutionContext;
    @Mock
    private TaskService taskService;
    @Spy
    private HeavyQueryCacheCleanupJobService heavyQueryCacheCleanupJobService;
    private HeavyQueryCacheCleanupJobService.HeavyQueryCacheCleanupJob heavyQueryCacheCleanupJob;
    private AutoCloseable autoCloseable;

    @BeforeEach
    public void beforeEach() {
        heavyQueryCacheCleanupJobService = new HeavyQueryCacheCleanupJobService(taskService);
        heavyQueryCacheCleanupJob = new HeavyQueryCacheCleanupJobService.HeavyQueryCacheCleanupJob();
        autoCloseable = openMocks(this);
        doReturn(heavyQueryCacheService).when(artifactoryContext).beanForType(HeavyQueryCacheService.class);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
    }

    @AfterEach
    public void afterEach() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        autoCloseable.close();
    }

    @Test
    void heavyQueryCacheCleanupJobTest() throws JobExecutionException {
        heavyQueryCacheCleanupJob.onExecute(jobExecutionContext);
        verify(heavyQueryCacheService).runCleanup();
    }

    @ParameterizedTest
    @MethodSource("cronDataProvider")
    void getCronExpressionTest(String providedCronExpression, String expectedCron) {
        artifactoryHomeStub.setProperty(hqcCleanupJobCron.getPropertyName(), providedCronExpression);
        String cronExpression = heavyQueryCacheCleanupJobService.getCronExpression();
        assertEquals(expectedCron, cronExpression);
    }

    private static Stream<Arguments> cronDataProvider() {
        return Stream.of(
                Arguments.of(CLEANUP_JOB_DEFAULT_CRON, CLEANUP_JOB_DEFAULT_CRON),
                Arguments.of("2 30 * * * *", CLEANUP_JOB_DEFAULT_CRON), // negative, invalid cron expression
                Arguments.of("0 0 0/1 1/1 * ? *", CLEANUP_JOB_DEFAULT_CRON), // negative, every hour
                Arguments.of("0 0 12 1 1/1 ? *", CLEANUP_JOB_DEFAULT_CRON), // negative, every month
                Arguments.of("0 0 12 ? * */8 *", CLEANUP_JOB_DEFAULT_CRON), // negative, every 8 days
                Arguments.of("0 0 12 ? * */2 *", "0 0 12 ? * */2 *"), // every 2 days
                Arguments.of(null, CLEANUP_JOB_DEFAULT_CRON)
        );
    }

}
