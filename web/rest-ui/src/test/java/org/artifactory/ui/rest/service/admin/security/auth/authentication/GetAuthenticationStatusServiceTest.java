package org.artifactory.ui.rest.service.admin.security.auth.authentication;

import org.artifactory.api.security.SecurityService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.security.SimpleUser;
import org.artifactory.session.ReauthenticateService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.stream.Stream;

import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_UNAUTHORIZED;
import static org.artifactory.ui.rest.service.admin.security.auth.authentication.GetAuthenticationStatusService.USERNAME_PARAM;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * @author Andrey Tashlitsky
 */
class GetAuthenticationStatusServiceTest {

    private GetAuthenticationStatusService service;
    @Mock
    private ReauthenticateService reauthenticateService;
    @Mock
    private SecurityService securityService;
    @Mock
    private SimpleUser simpleUser;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = openMocks(this);
        service = new GetAuthenticationStatusService();
        ReflectionTestUtils.setField(service, "reauthenticateService", reauthenticateService);
        ReflectionTestUtils.setField(service, "securityService", securityService);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @ParameterizedTest
    @MethodSource("permissions")
    void testPermissions(boolean admin, String authenticatedUser, String pathParamUser, int expectedCode) {
        doReturn(admin).when(securityService).isAdmin();
        doReturn(authenticatedUser).when(securityService).currentUsername();
        ArtifactoryRestRequest<?> request = mock(ArtifactoryRestRequest.class);
        doReturn(pathParamUser).when(request).getPathParamByKey(USERNAME_PARAM);
        doReturn(simpleUser).when(reauthenticateService).reauthenticateByRealm(any(), any());
        doReturn(true).when(simpleUser).isEnabled();
        ArtifactoryRestResponse<?> response = new ArtifactoryRestResponse<>();
        service.execute(request, response);
        assertEquals(expectedCode, response.getResponseCode());
    }

    private static Stream<Arguments> permissions() {
        return Stream.of(
                Arguments.of(true, "admin", "admin", SC_OK),
                Arguments.of(true, "admin", "not-cool-user", SC_OK),
                Arguments.of(false, "cool-user", "cool-user", SC_OK),
                Arguments.of(false, "cool-user", "not-cool-user", SC_UNAUTHORIZED)
        );
    }
}
