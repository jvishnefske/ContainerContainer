package org.artifactory.webapp.servlet.authentication.interceptor.anonymous;


import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import jakarta.servlet.http.HttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnonymousLoginInterceptorTest extends ArtifactoryHomeBoundJUnitTest {

    @Test
    void testAccept() {
        AnonymousLoginInterceptor interceptor = new AnonymousLoginInterceptor();
        assertTrue(interceptor.accept(req("/artifactory/ui/auth/ok")));
        assertTrue(interceptor.accept(req("/artifactory/ui/oauth2/ok")));
        assertTrue(interceptor.accept(req("/artifactory/api/auth/ok")));
        assertTrue(interceptor.accept(req("/artifactory/api/oauth2/ok")));
        assertFalse(interceptor.accept(req("/artifactory/oauth2")));
        assertFalse(interceptor.accept(req("/artifactory/oauth2/nope")));
        assertFalse(interceptor.accept(req("/artifactory/auth/nope")));
        assertFalse(interceptor.accept(req("/bla/ui/auth/ok")));
    }

    @Test
    void testAcceptWithDifferentContext() {
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.overrideContextPath.getPropertyName(), "bla");
        AnonymousLoginInterceptor interceptor = new AnonymousLoginInterceptor();
        assertTrue(interceptor.accept(req("/artifactory/ui/auth/ok")));
        assertTrue(interceptor.accept(req("/bla/ui/auth/ok")));
        assertTrue(interceptor.accept(req("/artifactory/ui/oauth2/ok")));
        assertTrue(interceptor.accept(req("/bla/ui/oauth2/ok")));
        assertTrue(interceptor.accept(req("/artifactory/api/auth/ok")));
        assertTrue(interceptor.accept(req("/bla/api/auth/ok")));
        assertTrue(interceptor.accept(req("/artifactory/api/oauth2/ok")));
        assertTrue(interceptor.accept(req("/bla/api/oauth2/ok")));
        assertFalse(interceptor.accept(req("/artifactory/oauth2")));
        assertFalse(interceptor.accept(req("/artifactory/oauth2/nope")));
        assertFalse(interceptor.accept(req("/artifactory/auth/nope")));
        assertFalse(interceptor.accept(req("/bla/auth/nope")));
    }

    @AfterEach
    public void tearDown() {
        ArtifactoryHome.get().getArtifactoryProperties().removeProperty(ConstantValues.overrideContextPath);
    }

    private HttpServletRequest req(String uri) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn(uri);
        return request;
    }
}