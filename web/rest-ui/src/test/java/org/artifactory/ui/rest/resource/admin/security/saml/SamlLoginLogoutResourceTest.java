package org.artifactory.ui.rest.resource.admin.security.saml;

import org.apache.http.HttpStatus;
import org.artifactory.request.ArtifactoryRequest;
import org.artifactory.rest.common.resource.BaseResource;
import org.mockito.MockitoAnnotations;
import org.springframework.util.ReflectionUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Response;
import java.lang.reflect.Field;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

/**
 * @author BarakH
 */
public class SamlLoginLogoutResourceTest {
    private static final String BASE_URL = "http://www.jfrog.io";
    private AutoCloseable mocks;

    @BeforeMethod
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    public void testLoginResponseURL() throws NoSuchFieldException {
        SamlLoginLogoutResource samlLoginLogoutResource = new SamlLoginLogoutResource();
        Field field = BaseResource.class.getDeclaredField("servletRequest");
        field.setAccessible(true);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(ArtifactoryRequest.JFROG_OVERRIDE_BASE_URL)).thenReturn(BASE_URL);
        ReflectionUtils.setField(field, samlLoginLogoutResource, request);
        Response response = samlLoginLogoutResource.loginResponse();
        assertEquals(response.getStatus(), HttpStatus.SC_TEMPORARY_REDIRECT);
        assertEquals(response.getLocation().toString(), BASE_URL + SamlLoginLogoutResource.SAML_LOGIN_ENDPOINT);
    }
}