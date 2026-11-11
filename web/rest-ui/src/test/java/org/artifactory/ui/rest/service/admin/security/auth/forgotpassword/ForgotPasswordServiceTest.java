package org.artifactory.ui.rest.service.admin.security.auth.forgotpassword;

import org.artifactory.api.security.UserGroupService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.ui.rest.model.admin.security.login.UserLogin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ForgotPasswordServiceTest {

    @Mock
    private UserGroupService userGroupService;
    @InjectMocks
    private ForgotPasswordService forgotPasswordService;
    private AutoCloseable mocks;

    @BeforeEach
    void beforeEach() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void afterEach() throws Exception {
        mocks.close();
    }

    @Test
    void testForgotPassword() {
        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        RestResponse response = mock(RestResponse.class);
        UserLogin userLogin = mock(UserLogin.class);
        when(userLogin.getUser()).thenReturn("username");
        when(request.getImodel()).thenReturn(userLogin);
        MockHttpServletRequest mockHttpServletRequest = new MockHttpServletRequest();
        mockHttpServletRequest.setRemoteAddr("8.8.8.8");
        when(request.getServletRequest()).thenReturn(mockHttpServletRequest);
        when(userGroupService.resetPassword("username", "8.8.8.8")).thenReturn("OK");

        forgotPasswordService.execute(request, response);

        verify(response).info("OK");
    }
}