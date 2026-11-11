package org.artifactory.ui.rest.service.admin.configuration.repositories.util;

import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.federation.FederatedRepoService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_GONE;
import static org.apache.http.HttpStatus.SC_OK;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Alexei Vainshtein
 */
public class MigrateLocalToFederatedRepositoryTest {

    @AfterMethod
    public void cleanup() {
        ArtifactoryHome.unbind();
    }

    private void mockFederationAttributeEnabled(boolean value) {
        ArtifactoryHome artifactoryHome = mock(ArtifactoryHome.class);
        ArtifactorySystemProperties artifactProperties = mock(ArtifactorySystemProperties.class);
        when(artifactProperties.getBooleanProperty(ConstantValues.repositoriesFederationsAttributeEnabled))
                .thenReturn(value);
        when(artifactoryHome.getArtifactoryProperties()).thenReturn(artifactProperties);
        ArtifactoryHome.bind(artifactoryHome);
    }

    @Test
    public void testExecute() {
        mockFederationAttributeEnabled(false);
        FederatedRepoService repoService = mock(FederatedRepoService.class);
        doNothing().when(repoService).migrate("repo");
        MigrateLocalToFederatedRepository migrate = new MigrateLocalToFederatedRepository(repoService);
        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        when(request.getImodel()).thenReturn("repo");
        ArtifactoryRestResponse restResponse = mock(ArtifactoryRestResponse.class);
        doAnswer(invocationOnMock -> restResponse).when(restResponse).info(migrate.SUCCESSFULLY);
        doAnswer(invocationOnMock -> restResponse).when(restResponse).responseCode(SC_OK);
        migrate.execute(request, restResponse);
        verify(restResponse, times(1)).responseCode(SC_OK);
        verify(restResponse, times(1)).info(migrate.SUCCESSFULLY);
    }

    @Test
    public void testExecuteWithException() {
        mockFederationAttributeEnabled(false);
        FederatedRepoService repoService = mock(FederatedRepoService.class);
        BadRequestException exception = new BadRequestException("failed migration");
        doThrow(exception).when(repoService).migrate("repo");
        MigrateLocalToFederatedRepository migrate = new MigrateLocalToFederatedRepository(repoService);
        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        when(request.getImodel()).thenReturn("repo");
        ArtifactoryRestResponse restResponse = mock(ArtifactoryRestResponse.class);
        doAnswer(invocationOnMock -> restResponse).when(restResponse).error(exception.getMessage());
        doAnswer(invocationOnMock -> restResponse).when(restResponse).responseCode(SC_BAD_REQUEST);
        migrate.execute(request, restResponse);
        verify(restResponse, times(1)).responseCode(SC_BAD_REQUEST);
        verify(restResponse, times(1)).error(exception.getMessage());
    }

    @Test
    public void testExecuteWhenFederationAttributeEnabled_returnsGone() {
        mockFederationAttributeEnabled(true);
        FederatedRepoService repoService = mock(FederatedRepoService.class);
        MigrateLocalToFederatedRepository migrate = new MigrateLocalToFederatedRepository(repoService);
        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        ArtifactoryRestResponse restResponse = spy(ArtifactoryRestResponse.class);

        migrate.execute(request, restResponse);

        Assert.assertEquals(restResponse.getResponseCode(), SC_GONE);
        verify(repoService, never()).migrate(any());
    }
}