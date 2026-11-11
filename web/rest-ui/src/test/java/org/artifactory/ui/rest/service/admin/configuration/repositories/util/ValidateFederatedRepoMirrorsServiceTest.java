package org.artifactory.ui.rest.service.admin.configuration.repositories.util;

import jakarta.annotation.Nonnull;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.federation.FederatedRepoService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.exception.http.ForbiddenException;
import org.artifactory.ui.rest.model.admin.configuration.repository.federation.FederationConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.federation.MirrorMemberConfigModel;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_FORBIDDEN;
import static org.apache.http.HttpStatus.SC_OK;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Alexei Vainshtein
 */
public class ValidateFederatedRepoMirrorsServiceTest {

    private FederatedRepoService federatedRepoService;


    @BeforeMethod
    public void init() {
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        ArtifactoryContextThreadBinder.bind(context);
        federatedRepoService = mock(FederatedRepoService.class);
        when(context.beanForType(FederatedRepoService.class)).thenReturn(federatedRepoService);
        reset(federatedRepoService);
    }

    @AfterMethod
    public void tearDown() {
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    public void testExecuteWithSuccess() {
        ValidateFederatedRepoMirrorsService service = new ValidateFederatedRepoMirrorsService();
        ArtifactoryRestRequest restRequest = prepareRequest();

        ArtifactoryRestResponse restResponse = mock(ArtifactoryRestResponse.class);
        when(restResponse.info(ValidateFederatedRepoMirrorsService.SUCCESS)).thenReturn(restResponse);
        doAnswer(invocationOnMock -> {
            Assert.assertEquals(invocationOnMock.getArgument(0, Integer.class), (Integer) SC_OK);
            return restResponse;
        }).when(restResponse).responseCode(anyInt());
        service.execute(restRequest, restResponse);
        verify(restResponse, times(1)).info(ValidateFederatedRepoMirrorsService.SUCCESS);
    }

    @Test
    public void testExecuteWithError() {
        ValidateFederatedRepoMirrorsService service = new ValidateFederatedRepoMirrorsService();
        ArtifactoryRestRequest restRequest = prepareRequest();
        String error = "error";
        doThrow(new BadRequestException(error)).when(federatedRepoService)
                .validateRemoteServer("http://this.url", "proxy", RepoType.Generic.getType(),
                        null);
        ArtifactoryRestResponse restResponse = mock(ArtifactoryRestResponse.class);
        when(restResponse.error(error)).thenReturn(restResponse);
        doAnswer(invocationOnMock -> {
            Assert.assertEquals(invocationOnMock.getArgument(0, Integer.class), (Integer) SC_BAD_REQUEST);
            return restResponse;
        }).when(restResponse).responseCode(anyInt());
        service.execute(restRequest, restResponse);
        verify(restResponse, times(0)).info(ValidateFederatedRepoMirrorsService.SUCCESS);
        verify(restResponse, times(1)).error(error);
    }

    @Test
    public void testExecuteWithForbidden() {
        ValidateFederatedRepoMirrorsService service = new ValidateFederatedRepoMirrorsService();
        ArtifactoryRestRequest restRequest = prepareRequest();
        String error = "error";
        doThrow(new ForbiddenException(error)).when(federatedRepoService)
                .validateRemoteServer("http://this.url", "proxy", RepoType.Generic.getType(),
                        null);
        ArtifactoryRestResponse restResponse = mock(ArtifactoryRestResponse.class);
        when(restResponse.error(error)).thenReturn(restResponse);
        doAnswer(invocationOnMock -> {
            Assert.assertEquals(invocationOnMock.getArgument(0, Integer.class), (Integer) SC_FORBIDDEN);
            return restResponse;
        }).when(restResponse).responseCode(anyInt());
        service.execute(restRequest, restResponse);
        verify(restResponse, times(0)).info(ValidateFederatedRepoMirrorsService.SUCCESS);
        verify(restResponse, times(1)).error(error);
    }

    @Test
    public void testExecuteNoMirror() {
        ValidateFederatedRepoMirrorsService service = new ValidateFederatedRepoMirrorsService();
        ArtifactoryRestRequest restRequest = mock(ArtifactoryRestRequest.class);
        when(restRequest.getImodel()).thenReturn(new FederationConfigModel());
        ArtifactoryRestResponse restResponse = mock(ArtifactoryRestResponse.class);
        when(restResponse.error("No mirrors were provided"))
                .thenReturn(restResponse);
        service.execute(restRequest, restResponse);
        verify(restResponse, times(0)).info(ValidateFederatedRepoMirrorsService.SUCCESS);
        verify(restResponse, times(1)).error("No mirrors were provided");
    }

    @Nonnull
    private ArtifactoryRestRequest prepareRequest() {
        ArtifactoryRestRequest restRequest = mock(ArtifactoryRestRequest.class);
        FederationConfigModel configModel = prepareConfigModel();
        when(restRequest.getImodel()).thenReturn(configModel);
        return restRequest;
    }

    @Nonnull
    private FederationConfigModel prepareConfigModel() {
        FederationConfigModel configModel = new FederationConfigModel();
        MirrorMemberConfigModel mirrorMemberConfigModel = new MirrorMemberConfigModel();
        mirrorMemberConfigModel.setUrl("http://this.url");
        configModel.setMirrors(List.of(mirrorMemberConfigModel));
        configModel.setProxy("proxy");
        configModel.setSourceRepoType(RepoType.Generic.getType());
        return configModel;
    }
}