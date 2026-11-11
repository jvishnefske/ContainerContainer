package com.jfrog.build.gateway;

import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.metadata.service.MetadataEntityFacade;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.sapi.interceptor.context.DeleteContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuildRepoGatewayTest {

    private InternalRepositoryService repositoryService;
    private BuildRepoGateway buildRepoGateway;

    @BeforeEach
    void setUp() {
        repositoryService = mock(InternalRepositoryService.class);
        buildRepoGateway = new BuildRepoGateway(
                mock(RepositoryConfigService.class),
                repositoryService,
                mock(MetadataEntityFacade.class));
    }

    @Test
    void undeploy_propagatesIdempotentDeleteFlagOntoDeleteContext() {
        when(repositoryService.undeploy(any(RepoPath.class), anyBoolean(), any(DeleteContext.class)))
                .thenReturn(new BasicStatusHolder());
        MutableStatusHolder status = mock(MutableStatusHolder.class);

        buildRepoGateway.undeploy("artifactory-build-info/build/1.json", status, true, false, true, false, true);

        ArgumentCaptor<DeleteContext> ctxCaptor = ArgumentCaptor.forClass(DeleteContext.class);
        verify(repositoryService).undeploy(any(RepoPath.class), eq(false), ctxCaptor.capture());
        DeleteContext ctx = ctxCaptor.getValue();
        assertTrue(ctx.isIdempotentDelete());
        assertTrue(ctx.isTriggerMetadataCalculation());
        assertTrue(ctx.isSkipTrashcan());
        verify(status).merge(any(BasicStatusHolder.class));
    }

    @Test
    void undeploy_strictDeleteKeepsIdempotentDeleteFalse() {
        when(repositoryService.undeploy(any(RepoPath.class), anyBoolean(), any(DeleteContext.class)))
                .thenReturn(new BasicStatusHolder());
        MutableStatusHolder status = mock(MutableStatusHolder.class);

        buildRepoGateway.undeploy("artifactory-build-info/build/1.json", status, false, false, false, true, false);

        ArgumentCaptor<DeleteContext> ctxCaptor = ArgumentCaptor.forClass(DeleteContext.class);
        verify(repositoryService).undeploy(any(RepoPath.class), eq(false), ctxCaptor.capture());
        DeleteContext ctx = ctxCaptor.getValue();
        assertFalse(ctx.isIdempotentDelete());
        assertTrue(ctx.isAvoidBuildDeleteInterceptor());
    }
}
