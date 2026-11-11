package org.artifactory.rest.filter;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.FeatureContext;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.artifactory.aol.AolService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.security.access.AccessTokenAuthentication;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.jfrog.access.common.PermissionScope;
import org.jfrog.access.common.PermissionScopeResourceType;
import org.jfrog.access.common.v2.Action;
import org.jfrog.access.token.JwtAccessToken;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertTrue;
import static org.testng.AssertJUnit.assertEquals;

/**
 * @author Oleksandr Legenkiy
 */
public class AllowedPermissionScopeDynamicFeatureTest extends ArtifactoryHomeBoundTest {

    @Mock
    ArtifactoryContext context;

    @Mock
    AolService aolService;

    AutoCloseable closeable;

    @BeforeMethod
    public void setup() {
        closeable = MockitoAnnotations.openMocks(this);
        when(context.beanForType(AolService.class)).thenReturn(aolService);
        ArtifactoryContextThreadBinder.bind(context);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        closeable.close();
        ArtifactoryContextThreadBinder.unbind();
    }

    @ParameterizedTest
    @CsvSource({"testSingleScope", "testMultipleScopes", "testValueScope"})
    public void testConfigureAtMethodLevel(String methodName) throws NoSuchMethodException {
        AllowedPermissionScopeDynamicFeature feature =
                new AllowedPermissionScopeDynamicFeature(mock(AuthorizationService.class), mock(SecurityService.class),
                        aolService);
        ResourceInfo resourceInfoMock = mock(ResourceInfo.class);
        when(resourceInfoMock.getResourceMethod()).thenReturn(TestResource.class.getMethod(methodName));
        doReturn(TestResource.class).when(resourceInfoMock).getResourceClass();
        FeatureContext contextMock = spy(FeatureContext.class);

        feature.configure(resourceInfoMock, contextMock);
        // Make sure a filter was registered.
        verify(contextMock).register(any(AllowedPermissionScopeDynamicFeature.PermissionScopeFilter.class));
    }

    @Test
    public void testConfigureAtClassLevel() throws NoSuchMethodException {
        AllowedPermissionScopeDynamicFeature feature =
                new AllowedPermissionScopeDynamicFeature(mock(AuthorizationService.class), mock(SecurityService.class),
                        aolService);
        ResourceInfo resourceInfoMock = mock(ResourceInfo.class);
        when(resourceInfoMock.getResourceMethod()).thenReturn(TestResource.class.getMethod("testClassLevelScope"));
        doReturn(TestResource.class).when(resourceInfoMock).getResourceClass();
        FeatureContext contextMock = spy(FeatureContext.class);

        feature.configure(resourceInfoMock, contextMock);
        // Make sure a filter was registered.
        verify(contextMock).register(any(AllowedPermissionScopeDynamicFeature.PermissionScopeFilter.class));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testConfigureWrongScope() throws NoSuchMethodException {
        AllowedPermissionScopeDynamicFeature feature =
                new AllowedPermissionScopeDynamicFeature(mock(AuthorizationService.class), mock(SecurityService.class),
                        aolService);
        ResourceInfo resourceInfoMock = mock(ResourceInfo.class);
        when(resourceInfoMock.getResourceMethod()).thenReturn(TestResource.class.getMethod("testWrongScope"));

        feature.configure(resourceInfoMock, mock(FeatureContext.class));
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "You must specify " +
            "either 'value' or 'scopes' attribute")
    public void testConfigureEmptyScope() throws NoSuchMethodException {
        AllowedPermissionScopeDynamicFeature feature =
                new AllowedPermissionScopeDynamicFeature(mock(AuthorizationService.class), mock(SecurityService.class),
                        aolService);
        ResourceInfo resourceInfoMock = mock(ResourceInfo.class);
        when(resourceInfoMock.getResourceMethod()).thenReturn(TestResource.class.getMethod("testEmptyValueScope"));

        feature.configure(resourceInfoMock, mock(FeatureContext.class));
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "You can use either " +
            "'value' or 'scopes' attribute, not both")
    public void testConfigureValueAndScopes() throws NoSuchMethodException {
        AllowedPermissionScopeDynamicFeature feature =
                new AllowedPermissionScopeDynamicFeature(mock(AuthorizationService.class), mock(SecurityService.class),
                        aolService);
        ResourceInfo resourceInfoMock = mock(ResourceInfo.class);
        when(resourceInfoMock.getResourceMethod()).thenReturn(TestResource.class.getMethod("testValueAndScopes"));

        feature.configure(resourceInfoMock, mock(FeatureContext.class));
    }

    @Test
    public void testFilterWhenAccessAllowed() throws IOException {
        initAuthentication();
        AuthorizationService authServiceMock = mock(AuthorizationService.class);
        when(authServiceMock.isGrantedByAccessToken(any(PermissionScope.class)))
                .thenReturn(Boolean.TRUE);
        SecurityService securityServiceMock = mock(SecurityService.class);
        when(securityServiceMock.currentUsername()).thenReturn("someUsername");

        AllowedPermissionScopeDynamicFeature.PermissionScopeFilter filter =
                new AllowedPermissionScopeDynamicFeature.PermissionScopeFilter(authServiceMock, securityServiceMock,
                        List.of(PermissionScope
                                .create(PermissionScopeResourceType.SYSTEM, "metrics", Action.READ)),
                        true, aolService);

        ContainerRequestContext contextMock = mock(ContainerRequestContext.class);

        doAnswer(invocation -> {
            AllowedPermissionScopeDynamicFeature.AllRolesAuthenticator actualSecContext = invocation.getArgument(0);
            assertTrue(((SecurityContext) actualSecContext).isUserInRole("anyRoleValueCanBeHere"));
            return null;
        }).when(contextMock).setSecurityContext(any(AllowedPermissionScopeDynamicFeature.AllRolesAuthenticator.class));

        // The filter should update security context to ignore any @RolesAllowed annotations.
        filter.filter(contextMock);
        verify(contextMock, times(1))
                .setSecurityContext(any(AllowedPermissionScopeDynamicFeature.AllRolesAuthenticator.class));
        clearAuthentication();
    }

    @Test
    public void testFilterWhenAccessNotAllowed() throws IOException {
        initAuthentication();
        AuthorizationService authServiceMock = mock(AuthorizationService.class);
        when(authServiceMock.isGrantedByAccessToken(any(PermissionScope.class)))
                .thenReturn(Boolean.FALSE);
        when(aolService.isDashboardUser()).thenReturn(false);
        SecurityService securityServiceMock = mock(SecurityService.class);
        when(securityServiceMock.currentUsername()).thenReturn("someUsername");

        AllowedPermissionScopeDynamicFeature.PermissionScopeFilter filter =
                new AllowedPermissionScopeDynamicFeature.PermissionScopeFilter(authServiceMock, securityServiceMock,
                        List.of(PermissionScope
                                .create(PermissionScopeResourceType.SYSTEM, "metrics", Action.READ)),
                        true, aolService);

        ContainerRequestContext contextMock = mock(ContainerRequestContext.class);
        doAnswer(invocation -> {
            Response actualResponse = invocation.getArgument(0);
            assertEquals(actualResponse.getStatus(), Response.Status.FORBIDDEN.getStatusCode());
            return null;
        }).when(contextMock).abortWith(any(Response.class));

        // The filter should abort the request with 403 code.
        filter.filter(contextMock);
        verify(contextMock, times(1)).abortWith(any(Response.class));
        clearAuthentication();
    }

    @Test
    public void testFilterWhenNotTokenAuthAndNoRolesAllowed() throws IOException {
        AuthorizationService authServiceMock = mock(AuthorizationService.class);
        SecurityService securityServiceMock = mock(SecurityService.class);
        when(aolService.isDashboardUser()).thenReturn(false);
        AllowedPermissionScopeDynamicFeature.PermissionScopeFilter filter =
                new AllowedPermissionScopeDynamicFeature.PermissionScopeFilter(authServiceMock, securityServiceMock,
                        List.of(PermissionScope
                                .create(PermissionScopeResourceType.SYSTEM, "metrics", Action.READ)),
                        true, aolService);

        ContainerRequestContext contextMock = mock(ContainerRequestContext.class);
        doAnswer(invocation -> {
            Response actualResponse = invocation.getArgument(0);
            assertEquals(actualResponse.getStatus(), Response.Status.FORBIDDEN.getStatusCode());
            return null;
        }).when(contextMock).abortWith(any(Response.class));

        // The filter should abort the request with 403 code.
        filter.filter(contextMock);
        verify(contextMock, times(1)).abortWith(any(Response.class));
    }

    @Test
    public void testFilterWhenNoAccessButRolesAllowedPresent() throws IOException {
        initAuthentication();
        AuthorizationService authServiceMock = mock(AuthorizationService.class);
        SecurityService securityServiceMock = mock(SecurityService.class);
        when(aolService.isDashboardUser()).thenReturn(false);
        when(authServiceMock.isGrantedByAccessToken(any(PermissionScope.class)))
                .thenReturn(Boolean.FALSE);
        AllowedPermissionScopeDynamicFeature.PermissionScopeFilter filter =
                new AllowedPermissionScopeDynamicFeature.PermissionScopeFilter(authServiceMock, securityServiceMock,
                        List.of(PermissionScope
                                .create(PermissionScopeResourceType.SYSTEM, "livelogs", Action.READ)),
                        false, aolService);

        ContainerRequestContext contextMock = mock(ContainerRequestContext.class);
        filter.filter(contextMock);
        // The filter should not update the security context (i.e. should not ignore @RolesAllowed authentication)
        verifyNoInteractions(contextMock);
        clearAuthentication();
    }

    private void initAuthentication() {
        SecurityContextImpl securityContext = new SecurityContextImpl();
        SecurityContextHolder.setContext(securityContext);

        JwtAccessToken tokenMock = mock(JwtAccessToken.class);
        when(tokenMock.getScope()).thenReturn(List.of("system:metrics:r", "api:*"));
        AccessTokenAuthentication authMock = mock(AccessTokenAuthentication.class);
        when(authMock.getAccessToken()).thenReturn(tokenMock);
        securityContext.setAuthentication(authMock);
    }

    public void clearAuthentication() {
        SecurityContextHolder.setContext(SecurityContextHolder.createEmptyContext());
    }


    @AllowedPermissionScope("system:health:r")
    private static class TestResource {

        @AllowedPermissionScope("system:metrics:r")
        public void testSingleScope() {
            // do nothing
        }

        @AllowedPermissionScope(value = "system:health:r")
        public void testValueScope() {
            // do nothing
        }

        @AllowedPermissionScope(scopes = {"system:metrics:r", "system:health:r"})
        public void testMultipleScopes() {
            // do nothing
        }

        @AllowedPermissionScope("someinvalidscope")
        public void testWrongScope() {
            // do nothing
        }

        @AllowedPermissionScope
        public void testEmptyValueScope() {
            // do nothing
        }

        @AllowedPermissionScope(value = "system:metrics:r", scopes = {"system:metrics:r", "system:health:r"})
        public void testValueAndScopes() {
            // do nothing
        }

        public void testClassLevelScope() {
            // do nothing
        }
    }
}
