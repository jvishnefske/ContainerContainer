package org.artifactory.rest.filter;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.FeatureContext;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.artifactory.common.ConstantValues;
import org.artifactory.rest.common.DisabledApi;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Igor Usenko
 */
class DisabledApiFilterTest extends ArtifactoryHomeBoundJUnitTest {

    private static final String TEST_MESSAGE = "This API is deprecated. Use the new API instead.";

    @Test
    void filterPassesThroughWhenFlagIsDisabled() {
        getBound().setProperty(ConstantValues.deprecatedPermissionsApiDisabled, "false");
        DisabledApiFilter filter = new DisabledApiFilter(ConstantValues.deprecatedPermissionsApiDisabled,
                TEST_MESSAGE);
        ContainerRequestContext ctx = mockRequestContext();

        filter.filter(ctx);

        verify(ctx, never()).abortWith(any());
    }

    @Test
    void filterAbortsWithGoneWhenFlagIsEnabled() {
        getBound().setProperty(ConstantValues.deprecatedPermissionsApiDisabled, "true");
        DisabledApiFilter filter = new DisabledApiFilter(ConstantValues.deprecatedPermissionsApiDisabled,
                TEST_MESSAGE);
        ContainerRequestContext ctx = mockRequestContext();

        filter.filter(ctx);

        verify(ctx).abortWith(any(Response.class));
    }

    @Test
    void filterResponseIsGoneStatusWhenFlagIsEnabled() {
        getBound().setProperty(ConstantValues.deprecatedPermissionsApiDisabled, "true");
        DisabledApiFilter filter = new DisabledApiFilter(ConstantValues.deprecatedPermissionsApiDisabled,
                TEST_MESSAGE);
        ContainerRequestContext ctx = mockRequestContext();

        filter.filter(ctx);

        verify(ctx).abortWith(argThat(response ->
                response.getStatus() == Response.Status.GONE.getStatusCode()));
    }

    @Test
    void filterResponseBodyContainsConfiguredMessage() {
        getBound().setProperty(ConstantValues.deprecatedPermissionsApiDisabled, "true");
        DisabledApiFilter filter = new DisabledApiFilter(ConstantValues.deprecatedPermissionsApiDisabled,
                TEST_MESSAGE);
        ContainerRequestContext ctx = mockRequestContext();

        filter.filter(ctx);

        verify(ctx).abortWith(argThat(response -> TEST_MESSAGE.equals(response.getEntity())));
    }

    @Test
    void dynamicFeatureRegistersFilterForAnnotatedMethod() throws NoSuchMethodException {
        DisabledApiDynamicFeature feature = new DisabledApiDynamicFeature();
        ResourceInfo resourceInfo = mock(ResourceInfo.class);
        when(resourceInfo.getResourceMethod())
                .thenReturn(TestResource.class.getDeclaredMethod("annotatedMethod"));
        FeatureContext featureContext = mock(FeatureContext.class);

        feature.configure(resourceInfo, featureContext);

        verify(featureContext).register(any(DisabledApiFilter.class));
    }

    @Test
    void dynamicFeatureSkipsFilterForUnannotatedMethod() throws NoSuchMethodException {
        DisabledApiDynamicFeature feature = new DisabledApiDynamicFeature();
        ResourceInfo resourceInfo = mock(ResourceInfo.class);
        when(resourceInfo.getResourceMethod())
                .thenReturn(TestResource.class.getDeclaredMethod("unannotatedMethod"));
        FeatureContext featureContext = mock(FeatureContext.class);

        feature.configure(resourceInfo, featureContext);

        verify(featureContext, never()).register(any());
    }

    private ContainerRequestContext mockRequestContext() {
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getPath()).thenReturn("/api/v2/security/permissions/test-permission");
        ContainerRequestContext ctx = mock(ContainerRequestContext.class);
        when(ctx.getMethod()).thenReturn("PUT");
        when(ctx.getUriInfo()).thenReturn(uriInfo);
        return ctx;
    }

    private static class TestResource {

        @GET
        @DisabledApi(
                disabledWhen = ConstantValues.deprecatedPermissionsApiDisabled,
                responseMessage = TEST_MESSAGE)
        void annotatedMethod() {}

        @GET
        void unannotatedMethod() {}
    }
}
