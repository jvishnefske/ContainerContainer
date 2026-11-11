package org.artifactory.repo.interceptor;

import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.status.StatusEntry;
import org.artifactory.pipelines.InternalPipelinesService;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * @author Alexei Vainshtein
 */
public class PipelinesInterceptorTest {

    @Test
    public void testAssertDeleteRepoNotAllowedByNonAdmin() {
        PipelinesInterceptor interceptor = new PipelinesInterceptor();
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        mockSecurityService(context, false);
        mockPipelineService(context, "repo");
        ArtifactoryContextThreadBinder.bind(context);
        interceptor.assertDeleteRepoAllowed("repo", statusHolder);
        assertStatusHolder(statusHolder, "Deletion of Pipe-Info repository 'repo' is restricted only for super admin.");
    }

    @Test
    public void testAssertDeleteRepoAllowedByAdmin() {
        PipelinesInterceptor interceptor = new PipelinesInterceptor();
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        mockSecurityService(context, true);
        mockPipelineService(context, "repo");
        ArtifactoryContextThreadBinder.bind(context);
        interceptor.assertDeleteRepoAllowed("repo", statusHolder);
        assertEquals(statusHolder.getEntries().size(), 0);
        assertFalse(statusHolder.isError());
    }

    @Test
    public void testAssertDeleteRepoNotAllowedDefault() {
        PipelinesInterceptor interceptor = new PipelinesInterceptor();
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        mockSecurityService(context, true);
        mockPipelineService(context, "artifactory-pipe-info");
        ArtifactoryContextThreadBinder.bind(context);
        interceptor.assertDeleteRepoAllowed("artifactory-pipe-info", statusHolder);
        assertStatusHolder(statusHolder, "Deletion of Pipe-Info repository artifactory-pipe-info is not allowed.");
    }

    @AfterMethod
    public void tearDown() {
        ArtifactoryContextThreadBinder.unbind();
    }

    private void assertStatusHolder(BasicStatusHolder statusHolder, String errorMessage) {
        assertTrue(statusHolder.isError());
        assertEquals(statusHolder.getErrors().size(), 1);
        StatusEntry lastError = statusHolder.getLastError();
        assertEquals(lastError.getStatusCode(), 403);
        assertEquals(lastError.getMessage(), errorMessage);
    }

    private void mockPipelineService(ArtifactoryContext context, String repo) {
        InternalPipelinesService pipelinesService = mock(InternalPipelinesService.class);
        when(pipelinesService.isPipeInfoRepo(repo)).thenReturn(true);
        when(context.beanForType(InternalPipelinesService.class)).thenReturn(pipelinesService);
    }

    private void mockSecurityService(ArtifactoryContext context, boolean isAdmin) {
        SecurityService securityService = mock(SecurityService.class);
        when(context.beanForType(SecurityService.class)).thenReturn(securityService);
        when(securityService.isAdmin()).thenReturn(isAdmin);
    }
}