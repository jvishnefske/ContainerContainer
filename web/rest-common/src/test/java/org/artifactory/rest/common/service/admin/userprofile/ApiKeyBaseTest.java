package org.artifactory.rest.common.service.admin.userprofile;

import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.rest.common.model.userprofile.UserProfileModel;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.security.props.auth.ApiKeyManager;
import org.jfrog.common.JsonUtils;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.core.Response;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

public abstract class ApiKeyBaseTest {

    private static final String ADMIN = "admin";
    private static final String TEST_USER = "test-user";
    protected static final String TOKEN = "token";
    private AutoCloseable mocks;

    protected abstract RestService getRestService();

    @Mock
    protected SecurityService securityService;

    @Mock
    protected ApiKeyManager apiKeyManager;

    @BeforeMethod
    protected void setup() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterMethod
    private void noMoreCalls() throws Exception {
        Mockito.verifyNoMoreInteractions(apiKeyManager, securityService);
        ArtifactoryHome.unbind();
        mocks.close();
    }

    @DataProvider
    protected Object[][] provideIdAndUserNameForTest() {
        return new Object[][] {
                {ADMIN, ADMIN},
                {TEST_USER, TEST_USER},
                {"", ADMIN}
        };
    }

    @DataProvider
    protected Object[][] provideIdAndUserNameForTestApiKeyServiceWithDifferentUsersFailed() {
        return new Object[][] {
                {ADMIN, TEST_USER},
                {TEST_USER, ADMIN}
        };
    }

    @Test(dataProvider = "provideIdAndUserNameForTestApiKeyServiceWithDifferentUsersFailed")
    public void testApiKeyServiceWithDifferentUsersShouldFailed(String id, String userName) {
        //setup
        when(securityService.isAnonymous()).thenReturn(false);
        when(securityService.currentUsername()).thenReturn(userName);
        mockSecurityApiKeyBlockCreate(false);
        //when
        ArtifactoryRestRequest artifactoryRestRequest = mock(ArtifactoryRestRequest.class);
        when(artifactoryRestRequest.getPathParamByKey("id")).thenReturn(id);
        RestResponse restResponse = spy(ArtifactoryRestResponse.class);

        //then
        getRestService().execute(artifactoryRestRequest, restResponse);
        assertEquals(restResponse.getResponseCode(), HttpServletResponse.SC_BAD_REQUEST);
    }

    protected void mockSecurityApiKeyBlockCreate(boolean value) {
        ArtifactoryHome artifactoryHome = mock(ArtifactoryHome.class);
        ArtifactorySystemProperties artifactProperties = mock(ArtifactorySystemProperties.class);
        when(artifactProperties.getBooleanProperty(ConstantValues.securityApiKeyBlockCreate))
                .thenReturn(value);
        when(artifactoryHome.getArtifactoryProperties()).thenReturn(artifactProperties);
        ArtifactoryHome.bind(artifactoryHome);
    }

    protected void assertApiKeyResponse(RestResponse artifactoryRestResponse) {
        assertApiKeyResponse(artifactoryRestResponse, false);
    }

    protected void assertApiKeyResponse(RestResponse artifactoryRestResponse, boolean expectedIsBlockCreateApiKey) {
        Response response = artifactoryRestResponse.buildResponse();
        UserProfileModel userProfileModel = JsonUtils.getInstance()
                .readValue(response.getEntity().toString(), UserProfileModel.class);
        assertEquals(TOKEN, userProfileModel.getApiKey());
        assertEquals(userProfileModel.isBlockCreateApiKey(), expectedIsBlockCreateApiKey);
    }
}
