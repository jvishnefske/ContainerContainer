package org.artifactory.rest.resource.mfe.server;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefreshMFEVersionJobTest {

    @AfterEach
    void cleanup() {
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void onExecute_shouldRefreshAllMFEVersions() throws JobExecutionException {
        // Arrange
        RefreshMFEVersionJob refreshMFEVersionJob = new RefreshMFEVersionJob();
        ArtifactoryContext artifactoryContext = mock(ArtifactoryContext.class);
        ExternalMFE externalMFE = mock(ExternalMFE.class);
        when(artifactoryContext.beanForType(ExternalMFE.class)).thenReturn(externalMFE);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        
        JobExecutionContext callbackContext = mock(JobExecutionContext.class);

        // Act
        refreshMFEVersionJob.onExecute(callbackContext);

        // Assert
        verify(externalMFE).refreshMFEVersionForProjects();
    }

    @Test
    void onExecute_shouldRefreshAllMFEVersions_whenCallbackContextIsNull() throws JobExecutionException {
        // Arrange
        RefreshMFEVersionJob refreshMFEVersionJob = new RefreshMFEVersionJob();
        ArtifactoryContext artifactoryContext = mock(ArtifactoryContext.class);
        ExternalMFE externalMFE = mock(ExternalMFE.class);
        when(artifactoryContext.beanForType(ExternalMFE.class)).thenReturn(externalMFE);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);

        // Act
        refreshMFEVersionJob.onExecute(null);

        // Assert
        verify(externalMFE).refreshMFEVersionForProjects();
    }
}