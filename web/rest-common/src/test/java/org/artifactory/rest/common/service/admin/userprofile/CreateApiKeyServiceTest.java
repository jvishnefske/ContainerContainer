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

public class CreateApiKeyServiceTest extends ApiKeyBaseTest {

    @Test(dataProvider = "provideIdAndUserNameForTest")
    public void createApiKeyTest(String id, String userName) {
        //setup
        TokenKeyValue tokenKeyValue = mock(TokenKeyValue.class);
        when(tokenKeyValue.getToken()).thenReturn(TOKEN);
        when(apiKeyManager.getToken(userName)).thenReturn(null);
        when(apiKeyManager.createToken(userName)).thenReturn(tokenKeyValue);
        when(securityService.isAnonymous()).thenReturn(false);
        when(securityService.currentUsername()).thenReturn(userName);
        mockSecurityApiKeyBlockCreate(false);
        ArtifactoryRestRequest artifactoryRestRequest = mock(ArtifactoryRestRequest.class);
        when(artifactoryRestRequest.getPathParamByKey("id")).thenReturn(id);
        RestResponse restResponse = spy(ArtifactoryRestResponse.class);

        //when
        getRestService().execute(artifactoryRestRequest, restResponse);

        //then
        verify(securityService, times(1)).currentUsername();
        verify(securityService, times(1)).isAnonymous();
        verify(apiKeyManager, times(1)).createToken(userName);
        verify(apiKeyManager, times(1)).getToken(userName);
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
        CreateApiKeyService createApiKeyService = new CreateApiKeyService();
        createApiKeyService.securityService = securityService;
        createApiKeyService.apiKeyManager = apiKeyManager;
        return createApiKeyService;
    }
}
