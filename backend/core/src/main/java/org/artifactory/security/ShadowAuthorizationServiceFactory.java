package org.artifactory.security;

import org.artifactory.api.security.AuthorizationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import java.lang.reflect.Proxy;

import static org.artifactory.permissions.PermissionsCacheType.Profiles.PERMISSIONS_CACHE_SHADOW;

/**
 * Factory for creating the beans needed for shadowing mechanism
 *
 * @author Igor Usenko
 */
@Configuration
@Profile(PERMISSIONS_CACHE_SHADOW)
public class ShadowAuthorizationServiceFactory {

    @Bean
    @Primary
    public AuthorizationService shadowAuthorizationService(AuthorizationServiceInvocationHandler handler) {
        return (AuthorizationService) Proxy.newProxyInstance(AuthorizationService.class.getClassLoader(),
                new Class[] {AuthorizationService.class}, handler);
    }

    @Bean("legacyAuthorizationServiceImpl")
    public AuthorizationService legacyAuthorizationServiceImpl() {
        return new LegacyAuthorizationServiceImpl();
    }

    @Bean("authorizationServiceImpl")
    public AuthorizationService authorizationServiceImpl() {
        return new AuthorizationServiceImpl();
    }

}
