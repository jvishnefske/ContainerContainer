package org.artifactory.rest.resource.security;

import jakarta.ws.rs.core.Response;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.rest.ErrorResponse;
import org.artifactory.security.SingleSignOnService;
import org.artifactory.security.access.AccessService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.util.stream.Stream;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Dvir Twina
 */
class AuthDelegationHandlerTest {
    @InjectMocks
    @Spy
    private AuthDelegationHandler authDelegationHandler;
    @Mock
    private ArtifactoryContext context;
    @Mock
    AccessService accessService;
    @Mock
    private SingleSignOnService singleSignOnService;
    private AutoCloseable mocks;

    public static Stream<Arguments> handleRequestProvider() {
        return Stream.of(
                Arguments.of(new UserAuthDetails("username", "password", "token"), false),
                Arguments.of(new UserAuthDetails(null, "password", "token"), false),
                Arguments.of(new UserAuthDetails(" ", "password", "token"), false),
                Arguments.of(new UserAuthDetails("username", null, "token"), false),
                Arguments.of(new UserAuthDetails("username", " ", "token"), false),

                Arguments.of(new UserAuthDetails("username", "password", null), true),
                Arguments.of(new UserAuthDetails("username", "password", ""), true),
                Arguments.of(new UserAuthDetails("username", " ", null), true)
        );
    }

    public static Stream<Arguments> handleRequestBadRequestProvider() {
        return Stream.of(
                Arguments.of(new UserAuthDetails("", "password", null)),
                Arguments.of(new UserAuthDetails(" ", "password", null)),
                Arguments.of(new UserAuthDetails(null, "password", null)),
                Arguments.of(new UserAuthDetails("username", "", null)),
                Arguments.of(new UserAuthDetails("username", null, null))
        );
    }

    @BeforeEach
    void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        when(context.beanForType(AccessService.class)).thenReturn(accessService);
        ArtifactoryContextThreadBinder.bind(context);
    }

    @AfterEach
    void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @ParameterizedTest
    @MethodSource("handleRequestProvider")
    void testHandleRequestSuccessful(UserAuthDetails userAuthDetails, boolean isBasicExpected) {
        doReturn(null).when(authDelegationHandler).handleBasicAuth(any(), any());
        doReturn(null).when(authDelegationHandler).handleToken(any(), any(), any());

        authDelegationHandler.handleRequest(singleSignOnService, userAuthDetails);

        verify(authDelegationHandler, isBasicExpected ? times(1) : never())
                .handleBasicAuth(singleSignOnService, userAuthDetails);
        verify(authDelegationHandler, isBasicExpected ? never() : times(1))
                .handleToken(singleSignOnService, userAuthDetails.getToken(), accessService);
    }

    @ParameterizedTest
    @MethodSource("handleRequestBadRequestProvider")
    void testHandleRequestBadRequest(UserAuthDetails userAuthDetails) {
        Response response = authDelegationHandler.handleRequest(singleSignOnService, userAuthDetails);

        assertEquals(SC_BAD_REQUEST, response.getStatus());
        assertEquals("Invalid username or password",
                ((ErrorResponse) response.getEntity()).getErrors().getFirst().getMessage());
    }
}