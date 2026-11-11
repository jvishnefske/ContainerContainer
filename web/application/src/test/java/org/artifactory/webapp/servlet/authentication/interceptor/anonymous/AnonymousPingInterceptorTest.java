package org.artifactory.webapp.servlet.authentication.interceptor.anonymous;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.HttpHeaders;
import java.util.Base64;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.testng.Assert.assertEquals;

public class AnonymousPingInterceptorTest {
    private static final String ANONYMOUS_BASIC_AUTH =
            "Basic " + Base64.getEncoder().encodeToString("anonymous:".getBytes());

    private static final String NOT_ANONYMOUS_BASIC_AUTH =
            "Basic " + Base64.getEncoder().encodeToString("foo-bar:".getBytes());

    @Test(dataProvider = "provideAcceptArguments")
    void testAcceptReqForSystemResource(String method, String path, String authHeader, boolean expected) {
        AnonymousPingInterceptor interceptor = new AnonymousPingInterceptor();
        HttpServletRequest request = createMock(HttpServletRequest.class);
        expect(request.getMethod()).andReturn(method).anyTimes();
        expect(request.getContextPath()).andReturn("");
        expect(request.getRequestURI()).andReturn(path);
        expect(request.getHeader(HttpHeaders.AUTHORIZATION)).andReturn(authHeader).anyTimes();
        replay(request);
        assertEquals(interceptor.accept(request), expected);
    }

    @DataProvider(name = "provideAcceptArguments")
    public Object[][] provideAcceptArguments() {
        return new Object[][] {
                {"GET", "/api/system/ping", ANONYMOUS_BASIC_AUTH, true},
                {"GET", "/api/system/ping", null, true},
                {"GET", "/api/system/ping", NOT_ANONYMOUS_BASIC_AUTH, false},
                {"GET", "/api/system/service_id", ANONYMOUS_BASIC_AUTH, true},
                {"GET", "/api/system/random/service_id/", ANONYMOUS_BASIC_AUTH, false},
                {"GET", "/api/v1/system/preStop", ANONYMOUS_BASIC_AUTH, true},
                {"GET", "/api/v1/system/edge/status", ANONYMOUS_BASIC_AUTH, false},
        };
    }
}
