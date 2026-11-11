package org.artifactory.rest.common.util;

import org.artifactory.projects.ProjectsService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * @author Yevdo Abramov
 * Created on 16/03/2021
 */
class RequestUtilsTest {

    @Test
    void testGetNormalizedProjectKey() {
        ArtifactoryRestRequest<?> request = mock(ArtifactoryRestRequest.class);
        String normalizedProjectKey = RequestUtils.getNormalizedProjectKey(request);

        // when projectKey is empty
        assertEquals(ProjectsService.DEFAULT_PROJECT, normalizedProjectKey);
        verify(request).getQueryParamByKey(ProjectsService.PROJECT_KEY);
        verify(request).getQueryParamByKey(ProjectsService.PROJECT);
        verifyNoMoreInteractions(request);

        // when projectKey is not empty
        request = mock(ArtifactoryRestRequest.class);
        when(request.getQueryParamByKey(ProjectsService.PROJECT_KEY)).thenReturn("myprj");
        normalizedProjectKey = RequestUtils.getNormalizedProjectKey(request);

        assertEquals("myprj", normalizedProjectKey);
        verify(request).getQueryParamByKey(ProjectsService.PROJECT_KEY);
        verifyNoMoreInteractions(request);

        // when projectKey is empty and project is not empty
        request = mock(ArtifactoryRestRequest.class);
        when(request.getQueryParamByKey(ProjectsService.PROJECT)).thenReturn("myprj");
        normalizedProjectKey = RequestUtils.getNormalizedProjectKey(request);

        assertEquals("myprj", normalizedProjectKey);
        verify(request).getQueryParamByKey(ProjectsService.PROJECT_KEY);
        verify(request).getQueryParamByKey(ProjectsService.PROJECT);
        verifyNoMoreInteractions(request);
    }
}