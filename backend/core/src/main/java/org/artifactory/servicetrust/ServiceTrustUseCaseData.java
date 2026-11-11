package org.artifactory.servicetrust;

/**
 * @author Tomer Slutsky
 */
public record ServiceTrustUseCaseData(String path, ServiceTrust trust, ServiceTrustUseCase trustUseCase) {
}
