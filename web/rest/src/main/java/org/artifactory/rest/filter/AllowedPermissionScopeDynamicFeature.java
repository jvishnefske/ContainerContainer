package org.artifactory.rest.filter;

import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.aol.AolService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.security.AuthenticationHelper;
import org.artifactory.security.access.AccessTokenAuthentication;
import org.glassfish.jersey.server.model.AnnotatedMethod;
import org.jfrog.access.common.PermissionScope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.Priority;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.DynamicFeature;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.FeatureContext;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.jfrog.common.ArgUtils.requireNonNull;

/**
 * @author Oleksandr Legenkiy
 */
@Component
@Provider
@Slf4j
public class AllowedPermissionScopeDynamicFeature implements DynamicFeature {

    private static final Class<AllowedPermissionScope> ANNOTATION_CLASS = AllowedPermissionScope.class;
    private final AuthorizationService authorizationService;
    private final SecurityService securityService;
    private final AolService aolService;

    @Autowired
    public AllowedPermissionScopeDynamicFeature(AuthorizationService authorizationService,
            SecurityService securityService, AolService aolService) {
        this.authorizationService = authorizationService;
        this.securityService = securityService;
        this.aolService = aolService;
    }

    @Override
    public void configure(ResourceInfo resourceInfo, FeatureContext context) {
        AnnotatedMethod am = new AnnotatedMethod(resourceInfo.getResourceMethod());
        AllowedPermissionScope allowedPermissionScope = null;
        if (am.isAnnotationPresent(ANNOTATION_CLASS)) {
            allowedPermissionScope = am.getAnnotation(ANNOTATION_CLASS);
        } else if (resourceInfo.getResourceClass().isAnnotationPresent(ANNOTATION_CLASS)) {
            allowedPermissionScope = resourceInfo.getResourceClass().getAnnotation(ANNOTATION_CLASS);
        }
        if (allowedPermissionScope != null) {
            List<PermissionScope> permissionScopes = extractPermissionScopes(allowedPermissionScope);
            context.register(new PermissionScopeFilter(authorizationService, securityService, permissionScopes,
                    !am.isAnnotationPresent(RolesAllowed.class) &&
                            !resourceInfo.getResourceClass().isAnnotationPresent(RolesAllowed.class), aolService));
        }

    }

    private List<PermissionScope> extractPermissionScopes(
            @Nonnull AllowedPermissionScope allowedPermissionScope) {
        String scope = allowedPermissionScope.value();
        String[] allowedScopes = allowedPermissionScope.scopes();
        validateScopeAttributes(scope, allowedScopes);
        List<String> permissionScopes = new ArrayList<>();
        if (!scope.isBlank()) {
            permissionScopes.add(scope);
        } else {
            permissionScopes.addAll(Arrays.asList(allowedScopes));
        }
        List<PermissionScope> scopes = new ArrayList<>();
        for (String permissionScopeString : permissionScopes) {
            try {
                scopes.add(PermissionScope.parse(permissionScopeString));
            } catch (Exception e) {
                log.error("Failed to parse permission scope: '{}'. {}", permissionScopeString, e.getMessage());
                throw e;
            }
        }
        return scopes;
    }

    private void validateScopeAttributes(String scope, String[] allowedScopes) {
        if (!scope.isBlank() && allowedScopes.length > 0) {
            throw new IllegalArgumentException("You can use either 'value' or 'scopes' attribute, not both");
        } else if (scope.isBlank() && allowedScopes.length == 0) {
            throw new IllegalArgumentException("You must specify either 'value' or 'scopes' attribute");
        }
    }

    // Should be called before the authorization filters because this filter can update the security context
    @Priority(Priorities.AUTHORIZATION - 100)
    @VisibleForTesting
    static class PermissionScopeFilter implements ContainerRequestFilter {

        private final AuthorizationService authorizationService;
        private final SecurityService securityService;
        private final List<PermissionScope> permissionScopes;
        private final boolean shouldAbortNotAuthorized;
        private final AolService aolService;

        @VisibleForTesting
        PermissionScopeFilter(AuthorizationService authorizationService, SecurityService securityService,
                List<PermissionScope> permissionScopes, boolean shouldAbortNotAuthorized, AolService aolService) {
            this.authorizationService =
                    requireNonNull(authorizationService, "authorizationService is required for the filter");
            this.securityService = requireNonNull(securityService, "securityService is required for the filter");
            this.permissionScopes = requireNonNull(permissionScopes, "permission scope is required for the filter");
            this.shouldAbortNotAuthorized = shouldAbortNotAuthorized;
            this.aolService = aolService;
        }

        @Override
        public void filter(ContainerRequestContext requestContext) throws IOException {
            if (isPermissionGranted() || isDashboardUser()) {
                // Make it ignore any @RolesAllowed value specified for the method
                requestContext.setSecurityContext(new AllRolesAuthenticator(securityService.currentUsername()));
            } else if (shouldAbortNotAuthorized) {
                requestContext.abortWith(Response.status(Response.Status.FORBIDDEN).build());
            }
        }

        private boolean isDashboardUser() {
            return aolService.isAol() && aolService.isDashboardUser();
        }

        private boolean isPermissionGranted() {
            if (!(AuthenticationHelper.getAuthentication() instanceof AccessTokenAuthentication)) {
                return false;
            }
            for (PermissionScope permissionScope : permissionScopes) {
                if (authorizationService.isGrantedByAccessToken(permissionScope)) {
                    return true;
                }
            }
            return false;
        }
    }


    @VisibleForTesting
    static class AllRolesAuthenticator implements SecurityContext {

        private final Principal principal;

        @VisibleForTesting
        AllRolesAuthenticator(String name) {
            this.principal = () -> name;
        }

        @Override
        public Principal getUserPrincipal() {
            return principal;
        }

        @Override
        public boolean isUserInRole(String role) {
            return true;
        }

        @Override
        public boolean isSecure() {
            return false;
        }

        @Override
        public String getAuthenticationScheme() {
            return SecurityContext.BASIC_AUTH;
        }
    }
}