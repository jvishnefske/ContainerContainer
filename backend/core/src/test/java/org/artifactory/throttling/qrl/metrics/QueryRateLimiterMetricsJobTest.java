package org.artifactory.throttling.qrl.metrics;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.throtlling.qrl.service.QueryRateLimiterService;
import org.artifactory.throttling.qrl.metrics.QueryRateLimiterMetricsJob;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * @author Andrey Tashlitsky
 */
class QueryRateLimiterMetricsJobTest {

    @Mock
    private ArtifactoryContext artifactoryContext;
    @Mock
    private QueryRateLimiterService qrlService;
    private AutoCloseable mocks;
    private final QueryRateLimiterMetricsJob queryRateLimiterMetricsJob = new QueryRateLimiterMetricsJob();

    @BeforeEach
    void setUp() {
        mocks = openMocks(this);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        when(artifactoryContext.beanForType(QueryRateLimiterService.class)).thenReturn(qrlService);
    }

    @AfterEach
    void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @Test
    void testOnExecute() throws JobExecutionException {
        queryRateLimiterMetricsJob.onExecute(mock(JobExecutionContext.class));
        verify(qrlService, times(1)).collectMetrics();
    }
}
