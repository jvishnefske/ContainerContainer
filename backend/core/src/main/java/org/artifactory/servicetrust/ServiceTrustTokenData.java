package org.artifactory.servicetrust;

import org.jfrog.access.common.ServiceId;

/**
 * @author Tomer Slutsky
 */
public record ServiceTrustTokenData(ServiceId artifactoryServiceId, String subject, String scope) {
}
