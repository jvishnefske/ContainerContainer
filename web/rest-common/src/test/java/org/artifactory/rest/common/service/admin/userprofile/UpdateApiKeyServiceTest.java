package org.artifactory.rest.common.service.admin.userprofile;

import org.apache.http.HttpStatus;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.security.props.auth.model.TokenKeyValue;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class UpdateApiKeyServiceTest extends ApiKeyBaseTest {

    @Test(dataProvider = "provideIdAndUserNameForTest")
    public void updateApiKeyTest(String id, String userName) {
        //setup
        TokenKeyValue tokenKeyValue = mock(TokenKeyValue.class);
        when(tokenKeyValue.getToken()).thenReturn(TOKEN);
        when(apiKeyManager.refreshToken(userName)).thenReturn(tokenKeyValue);
        when(securityService.isAnonymous()).thenReturn(false);
        when(securityService.currentUsername()).thenReturn(userName);
        when(securityService.isApiKeyAuthentication()).thenReturn(false);
        mockSecurityApiKeyBlockCreate(false);
        ArtifactoryRestRequest artifactoryRestRequest = mock(ArtifactoryRestRequest.class);
        RestResponse restResponse = spy(ArtifactoryRestResponse.class);
        when(artifactoryRestRequest.getPathParamByKey("id")).thenReturn(id);
        //when
        getRestService().execute(artifactoryRestRequest, restResponse);

        //then
        verify(securityService, times(1)).isAnonymous();
        verify(securityService, times(1)).currentUsername();
        verify(securityService, times(1)).isApiKeyAuthentication();
        verify(apiKeyManager, times(1)).refreshToken(userName);
        verify(artifactoryRestRequest, times(1)).getPathParamByKey("id");
        assertApiKeyResponse(restResponse);
    }

    @Test
    public void createApiKeyWhenDeprecatedTest() {
        //setup
        mockSecurityApiKeyBlockCreate(true);
        ArtifactoryRestRequest artifactoryRestRequest = mock(ArtifactoryRestRequest.class);
        RestResponse restResponse = spy(ArtifactoryRestResponse.class);

        //when
        getRestService().execute(artifactoryRestRequest, restResponse);

        //then
        Assert.assertEquals(restResponse.getResponseCode(), HttpStatus.SC_GONE);
    }

    @Override
    protected RestService getRestService() {
        UpdateApiKeyService updateApiKeyService = new UpdateApiKeyService();
        updateApiKeyService.securityService = securityService;
        updateApiKeyService.apiKeyManager = apiKeyManager;
        return updateApiKeyService;
    }
}
