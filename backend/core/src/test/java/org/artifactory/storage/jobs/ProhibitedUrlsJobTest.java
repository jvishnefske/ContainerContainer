package org.artifactory.storage.jobs;

import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.service.ProhibitedUrlsService;
import org.artifactory.spring.ArtifactoryApplicationContext;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.quartz.JobExecutionException;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProhibitedUrlsJobTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    ArtifactoryApplicationContext context;
    @Mock
    private ProhibitedUrlsService prohibitedUrlsService;
    private ProhibitedUrlsJob prohibitedUrlsJob;
    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        prohibitedUrlsJob = spy(new ProhibitedUrlsJob());
        ArtifactoryContextThreadBinder.bind(context);
        when(context.beanForType(ProhibitedUrlsService.class)).thenReturn(prohibitedUrlsService);
    }

    @AfterEach
    void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        closeable.close();
    }

    @Test
    void shouldFetchAllowedRemoteUrlsWhenJobExecutes() throws JobExecutionException {
        prohibitedUrlsJob.onExecute(null);
        verify(prohibitedUrlsService).fetchAllowedRemoteUrls();
    }
}
