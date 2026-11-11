package org.artifactory.response;

import org.junit.jupiter.api.Test;

import jakarta.servlet.http.HttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * @author Andrey Tashlitsky
 */
class ResponseThreadLocalTest {

    @Test
    void testResponse() {
        HttpServletResponse httpServletResponse = mock(HttpServletResponse.class);
        ResponseWrapper responseWrapper = new ResponseWrapper(httpServletResponse);
        ResponseThreadLocal.bind(responseWrapper);
        assertEquals(ResponseThreadLocal.getResponse(), httpServletResponse);
        ResponseThreadLocal.unbind();
        assertNull(ResponseThreadLocal.getResponse());
    }

    @Test
    void testAbortedByClient() {
        ResponseWrapper responseWrapper = mock(ResponseWrapper.class);
        ResponseThreadLocal.bind(responseWrapper);
        ResponseThreadLocal.setAbortedByClient(true);
        assertTrue(ResponseThreadLocal.isAbortedByClient());
        ResponseThreadLocal.setAbortedByClient(false);
        assertFalse(ResponseThreadLocal.isAbortedByClient());
        ResponseThreadLocal.unbind();
        ResponseThreadLocal.setAbortedByClient(true);
        assertFalse(ResponseThreadLocal.isAbortedByClient());
        ResponseThreadLocal.setAbortedByClient(false);
        assertFalse(ResponseThreadLocal.isAbortedByClient());
    }

    @Test
    void testStatusCode() {
        ResponseWrapper responseWrapper = mock(ResponseWrapper.class);
        ResponseThreadLocal.bind(responseWrapper);
        ResponseThreadLocal.setStatusCode(200);
        assertEquals(200, ResponseThreadLocal.getStatusCode());
        ResponseThreadLocal.unbind();
        assertNull(ResponseThreadLocal.getStatusCode());
        ResponseThreadLocal.setStatusCode(200);
        assertNull(ResponseThreadLocal.getStatusCode());
    }
}
