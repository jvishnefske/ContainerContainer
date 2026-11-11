package org.artifactory.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.artifactory.descriptor.repo.ProxyDescriptor;
import org.artifactory.exception.http.BadRequestException;

import static org.artifactory.descriptor.security.ProxyServices.VALID_PROXY_SERVICES;

/**
 * @author Yuval Reches
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public abstract class ProxyUtils {

    public static void validateProxyServices(ProxyDescriptor proxy) {
        proxy.getServicesList().forEach(service -> {
            if (!VALID_PROXY_SERVICES.contains(service)) {
                throw new BadRequestException(
                        "Invalid service type '" + service + "'. Valid types are: " + VALID_PROXY_SERVICES);
            }
        });
    }
}
