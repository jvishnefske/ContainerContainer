package org.artifactory.rest.common;

import org.artifactory.activity.ActivityDetails;
import org.artifactory.activity.CurrentActivity;
import org.artifactory.common.repo.PackageType;
import org.glassfish.jersey.server.ExtendedUriInfo;
import org.glassfish.jersey.server.model.Invocable;
import org.glassfish.jersey.server.model.MethodHandler;
import org.glassfish.jersey.server.model.ResourceMethod;
import org.glassfish.jersey.server.monitoring.ApplicationEvent;
import org.glassfish.jersey.server.monitoring.RequestEvent;
import org.glassfish.jersey.server.monitoring.RequestEventListener;
import org.glassfish.jersey.uri.UriTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import java.net.URI;
import java.util.List;

import static org.artifactory.rest.common.RestRequestActivityListener.getMatchingPattern;
import static org.artifactory.rest.common.RestRequestActivityListener.removeTrailingSlash;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RestRequestActivityListenerTest {
    RestRequestActivityListener listener;
    RequestEvent event;
    ExtendedUriInfo info;

    @BeforeEach
    void beforeEachTest() {
        MDC.clear(); // Just to be sure no other test dirtied MDC before us

        this.listener = new RestRequestActivityListener();
        this.event = mock(RequestEvent.class);
        this.info = mock(ExtendedUriInfo.class);
        when(this.event.getUriInfo()).thenReturn(this.info);
        when(this.info.getBaseUri()).thenReturn(URI.create("/artifactory/api/"));
    }

    @AfterEach
    void afterEachTest() {
        MDC.clear();
    }

    @Test
    void applicationListener_onEvent_shouldNotFail() {
        assertDoesNotThrow(() -> listener.onEvent(mock(ApplicationEvent.class)));
    }

    @Test
    void applicationListener_onRequest_shouldReturnEventListener() {
        RequestEventListener actual = listener.onRequest(event);
        assertNotNull(actual);
    }

    @Test
    void testLocatorMatched() throws NoSuchMethodException {
        Invocable invocable = Invocable.create(
                MethodHandler.create(AlpineResource.class),
                AlpineResource.class.getMethod("navigate"),
                false
        );
        ResourceMethod locator = mock(ResourceMethod.class);
        when(locator.getInvocable()).thenReturn(invocable);
        when(this.event.getType()).thenReturn(RequestEvent.Type.LOCATOR_MATCHED);
        when(this.info.getMatchedResourceLocators()).thenReturn(List.of(locator));

        listener.onEvent(event);
        assertEquals("test", CurrentActivity.KIND.get());
        assertEquals("alpine", CurrentActivity.PACKAGE.get());
        assertNull(CurrentActivity.GROUP.get());
    }

    @Test
    void testResourceMethodMatched() throws NoSuchMethodException {
        Invocable invocable = Invocable.create(
                MethodHandler.create(AlpineResource.class),
                PackageResource.class.getMethod("check"),
                AlpineResource.class.getMethod("check"),
                false
        );
        ResourceMethod resourceMethod = mock(ResourceMethod.class);
        when(resourceMethod.getInvocable()).thenReturn(invocable);
        when(this.event.getType()).thenReturn(RequestEvent.Type.RESOURCE_METHOD_START);
        when(this.info.getMatchedResourceMethod()).thenReturn(resourceMethod);
        when(this.info.getMatchedTemplates()).thenReturn(List.of(new UriTemplate("alpine/{repoKey}")));

        listener.onEvent(event);
        assertEquals("test", CurrentActivity.KIND.get());
        assertEquals("alpine", CurrentActivity.PACKAGE.get());
        assertEquals("health", CurrentActivity.GROUP.get());
        assertEquals("/artifactory/api/alpine/{repoKey}", CurrentActivity.NAME.get());
    }

    @Test
    void resourceMethodMatched_methodNotAnnotatedClassNotAnnotated() throws NoSuchMethodException {
        Invocable invocable = Invocable.create(
                MethodHandler.create(SimpleResource.class),
                SimpleResource.class.getMethod("check")
        );
        ResourceMethod resourceMethod = mock(ResourceMethod.class);
        when(resourceMethod.getInvocable()).thenReturn(invocable);
        when(this.event.getType()).thenReturn(RequestEvent.Type.RESOURCE_METHOD_START);
        when(this.info.getMatchedResourceMethod()).thenReturn(resourceMethod);

        listener.onEvent(event);
        assertNull(CurrentActivity.KIND.get());
        assertNull(CurrentActivity.PACKAGE.get());
        assertNull(CurrentActivity.GROUP.get());
    }

    @Test
    void resourceMethodMatched_methodAnnotatedClassNotAnnotated() throws NoSuchMethodException {
        Invocable invocable = Invocable.create(
                MethodHandler.create(SimpleResource.class),
                SimpleResource.class.getMethod("annotated")
        );
        ResourceMethod resourceMethod = mock(ResourceMethod.class);
        when(resourceMethod.getInvocable()).thenReturn(invocable);
        when(this.event.getType()).thenReturn(RequestEvent.Type.RESOURCE_METHOD_START);
        when(this.info.getMatchedResourceMethod()).thenReturn(resourceMethod);

        listener.onEvent(event);
        assertEquals("test", CurrentActivity.GROUP.get());
        assertNull(CurrentActivity.PACKAGE.get());
        assertNull(CurrentActivity.KIND.get());
    }

    @Test
    void resourceMethodMatched_shouldTakeAnnotationFromDerivedClass() throws NoSuchMethodException {
        Invocable invocable = Invocable.create(
                MethodHandler.create(SimpleDockerResource.class),
                SimpleResource.class.getMethod("annotated")
        );
        ResourceMethod resourceMethod = mock(ResourceMethod.class);
        when(resourceMethod.getInvocable()).thenReturn(invocable);
        when(this.event.getType()).thenReturn(RequestEvent.Type.RESOURCE_METHOD_START);
        when(this.info.getMatchedResourceMethod()).thenReturn(resourceMethod);

        listener.onEvent(event);
        assertEquals("test", CurrentActivity.GROUP.get());
        assertEquals("docker", CurrentActivity.PACKAGE.get());
        assertNull(CurrentActivity.KIND.get());

        // And now check that we did not cache the details for the base class
        Invocable ociInvocable = Invocable.create(
                MethodHandler.create(SimpleOciResource.class),
                SimpleResource.class.getMethod("annotated")
        );
        ResourceMethod ociResourceMethod = mock(ResourceMethod.class);
        when(ociResourceMethod.getInvocable()).thenReturn(ociInvocable);
        when(this.event.getType()).thenReturn(RequestEvent.Type.RESOURCE_METHOD_START);
        when(this.info.getMatchedResourceMethod()).thenReturn(ociResourceMethod);

        listener.onEvent(event);
        assertEquals("test", CurrentActivity.GROUP.get());
        assertEquals("oci", CurrentActivity.PACKAGE.get());
        assertNull(CurrentActivity.KIND.get());
    }

    @Test
    void otherEventTypesSourceNotFail() {
        when(this.event.getType()).thenReturn(RequestEvent.Type.START);
        assertDoesNotThrow(() -> listener.onEvent(event));
    }

    @Test
    void getMatchingPattern_shouldCombineRootPathAndUriPattern() {
        when(this.info.getMatchedTemplates()).thenReturn(List.of(new UriTemplate("system/ping")));
        String actual = getMatchingPattern(this.info);
        assertEquals("/artifactory/api/system/ping", actual);
    }

    @Test
    void getMatchingPattern_shouldCombinePathFromTwoUriTemplates() {
        when(this.info.getMatchedTemplates()).thenReturn(List.of(
                new UriTemplate("/{resource}"),
                new UriTemplate("/nuget/"))
        );
        String actual = getMatchingPattern(this.info);
        assertEquals("/artifactory/api/nuget/{resource}", actual);
    }

    @Test
    void removeTrailingSlash_shouldRemoveSlash_whenExists() {
        StringBuilder builder = new StringBuilder("/artifactory/");
        removeTrailingSlash(builder);
        assertEquals("/artifactory", builder.toString());
    }

    @Test
    void removeTrailingSlash_shouldDoNothing_whenItDoesNotEndWithSlash() {
        StringBuilder builder = new StringBuilder("/api");
        removeTrailingSlash(builder);
        assertEquals("/api", builder.toString());
    }

    //#region ---------- Sample Resource classes ----------
    @ActivityDetails(kind = "test")
    interface PackageResource {
        @ActivityDetails(group = "health")
        String check();
    }

    @ActivityDetails(PackageType.ALPINE)
    static class AlpineResource implements PackageResource {
        @GET
        @Path("/check")
        public String check() {
            return "OK";
        }

        @GET
        @Path("/navigate")
        public Object navigate() {
            return new Object();
        }
    }

    static class SimpleResource {
        @GET
        @Path("/check")
        public String check() {
            return "OK";
        }

        @GET
        @Path("/annotated")
        @ActivityDetails(group = "test")
        public String annotated() {
            return "OK";
        }
    }

    @ActivityDetails(PackageType.DOCKER)
    static class SimpleDockerResource extends SimpleResource {
    }

    @ActivityDetails(PackageType.OCI)
    static class SimpleOciResource extends SimpleResource {
    }
    //#endregion
}