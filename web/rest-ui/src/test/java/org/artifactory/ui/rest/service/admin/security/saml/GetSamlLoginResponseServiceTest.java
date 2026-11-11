package org.artifactory.ui.rest.service.admin.security.saml;

import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.request.ArtifactoryRequest;
import org.artifactory.security.PluggableAuthenticationProviderIfc;
import org.artifactory.security.RealmAwareAuthenticationManager;
import org.artifactory.spring.ArtifactoryApplicationContext;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GetSamlLoginResponseServiceTest {
    @Mock
    private ArtifactoryApplicationContext applicationContext;
    private static final String BASE_URL = "http://www.jfrog.io";
    private AutoCloseable mocks;

    @BeforeMethod
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @Test
    void testAuthenticateAdditives() {
        ArtifactoryContextThreadBinder.bind(applicationContext);
        GetSamlLoginResponseService samlLoginResponseService = new GetSamlLoginResponseService();
        PluggableAuthenticationProviderIfc plugDummyProvider = mock(PluggableAuthenticationProviderIfc.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        RealmAwareAuthenticationManager authenticationManager = mock(RealmAwareAuthenticationManager.class);
        when(request.getHeader(ArtifactoryRequest.JFROG_OVERRIDE_BASE_URL)).thenReturn(BASE_URL);
        when(ContextHelper.get().beanForType(RealmAwareAuthenticationManager.class)).thenReturn(authenticationManager);
        when(authenticationManager.getPluggableAuthenticationProviders()).thenReturn(
                List.of(plugDummyProvider));
        samlLoginResponseService.authenticateAdditives(request);
    }
}