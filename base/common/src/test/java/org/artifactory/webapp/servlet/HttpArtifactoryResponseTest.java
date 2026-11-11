package org.artifactory.webapp.servlet;

import com.google.common.net.HttpHeaders;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.catalina.connector.ResponseFacade;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.request.RequestThreadLocal;
import org.artifactory.test.ArtifactoryHomeStub;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Amir Avidan
 */
class HttpArtifactoryResponseTest {

    @Mock
    private CapturingHttpServletResponseWrapper response;
    @Mock
    private ResponseFacade responseFacade;

    private HttpArtifactoryResponse httpArtifactoryResponse;
    private AutoCloseable autoCloseable;


    @BeforeEach
    void setUp() {
        autoCloseable = MockitoAnnotations.openMocks(this);
        httpArtifactoryResponse = spy(new HttpArtifactoryResponse(response));
    }

    @AfterEach
    public void afterMethod() throws Exception {
        ArtifactoryHome.unbind();
        autoCloseable.close();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testSendErrorInternalResponseCommitted(boolean isWrapper) throws IOException {
        if (isWrapper) {
            httpArtifactoryResponse = spy(new HttpArtifactoryResponse(response));
            when(response.isCommitted()).thenReturn(true);
        } else {
            httpArtifactoryResponse = spy(new HttpArtifactoryResponse(responseFacade));
            when(responseFacade.isCommitted()).thenReturn(true);
        }
        when(response.getStatusFromCommitedResponse()).thenReturn(400);
        httpArtifactoryResponse.sendErrorInternal(500, "Error");
        verify(httpArtifactoryResponse, never()).flush();
        if (isWrapper) {
            verify(response).updateStatus(400);
        }
    }

    @Test
    void sendAuthorizationRequired_shouldNotAddWwwAuthenticateHeader_whenSkipHeaderFlagIsTrue() throws IOException {
        ArtifactoryHomeStub homeStub = new ArtifactoryHomeStub(getClass().getName() + ".flagTrue");
        homeStub.loadSystemProperties();
        homeStub.setProperty(ConstantValues.skipWWWAuthenticateHeader, "true");
        ArtifactoryHome.bind(homeStub);

        String realm = "TestRealm";
        String message = "Unauthorized";

        HttpServletRequest request = mock(HttpServletRequest.class);
        String browserUserAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/90.0.4430.212 Safari/537.36";
        when(request.getHeader(HttpHeaders.USER_AGENT)).thenReturn(browserUserAgent);

        try (MockedStatic<RequestThreadLocal> rtl = mockStatic(RequestThreadLocal.class)) {
            rtl.when(RequestThreadLocal::getRequest).thenReturn(request);

            httpArtifactoryResponse.sendAuthorizationRequired(message, realm);

            verify(response, never())
                    .addHeader(eq("WWW-Authenticate"), eq("Basic realm=\"" + realm + "\""));
        }
    }

    @Test
    void sendAuthorizationRequired_shouldAddWwwAuthenticateHeader_whenSkipHeaderFlagIsFalse() throws IOException {
        ArtifactoryHomeStub homeStub = new ArtifactoryHomeStub(getClass().getName() + ".flagFalse");
        homeStub.loadSystemProperties();
        homeStub.setProperty(ConstantValues.skipWWWAuthenticateHeader, "false");
        ArtifactoryHome.bind(homeStub);

        String realm = "TestRealm";
        String message = "Unauthorized";

        httpArtifactoryResponse.sendAuthorizationRequired(message, realm);

        verify(response).addHeader("WWW-Authenticate", "Basic realm=\"" + realm + "\"");
    }
}