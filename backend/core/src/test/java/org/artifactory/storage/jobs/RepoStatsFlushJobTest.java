package org.artifactory.storage.jobs;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.storage.fs.service.RepoStatsService;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RepoStatsFlushJobTest {

    @Test
    void onExecuteFlushesRepoStatsSuccessfully() throws JobExecutionException {
        try (MockedStatic<ContextHelper> contextHelper = Mockito.mockStatic(ContextHelper.class)) {
            RepoStatsService repoStatsService = Mockito.mock(RepoStatsService.class);

            ArtifactoryContext artifactoryContext = Mockito.mock(ArtifactoryContext.class);

            contextHelper.when(ContextHelper::get).thenReturn(artifactoryContext);
            when(artifactoryContext.beanForType(RepoStatsService.class)).thenReturn(repoStatsService);

            RepoStatsFlushJob job = new RepoStatsFlushJob();

            job.onExecute(Mockito.mock(JobExecutionContext.class));

            verify(repoStatsService, times(1)).flush();
            ArtifactoryContextThreadBinder.bind(artifactoryContext);
        }
    }
}
