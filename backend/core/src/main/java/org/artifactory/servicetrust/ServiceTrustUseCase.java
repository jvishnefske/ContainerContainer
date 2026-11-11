package org.artifactory.servicetrust;

import lombok.Data;

import java.util.List;
import java.util.regex.Pattern;

import static org.artifactory.servicetrust.distribution.DistributionTokenSpecService.DISTRIBUTE_TOKEN_PATTERN;
import static org.artifactory.servicetrust.distribution.DistributionTokenSpecService.DISTRIBUTION_GENERIC_TOKEN_PATTERN;
import static org.artifactory.servicetrust.distribution.DistributionTokenSpecService.DISTRIBUTION_SPECIFIC_SCOPE_TOKEN_PATTERN;

/**
 * @author igoru
 */
@Data
public class ServiceTrustUseCase {

    public static final ServiceTrustUseCase ARTIFACTORY_COLD =
            new ServiceTrustUseCase("artifactory-cold",
                    List.of("^artifactory-cold$"),
                    ServiceTrust.ONE_WAY);

    public static final ServiceTrustUseCase ARTIFACTORY_COLD_WITH_NAMESPACE =
            new ServiceTrustUseCase("artifactory-cold",
                    List.of("^artifactory-cold/.+$"),
                    ServiceTrust.ONE_WAY);

    public static final ServiceTrustUseCase DISTRIBUTION =
            new ServiceTrustUseCase("distribution",
                    List.of(DISTRIBUTION_GENERIC_TOKEN_PATTERN,
                            DISTRIBUTE_TOKEN_PATTERN,
                            DISTRIBUTION_SPECIFIC_SCOPE_TOKEN_PATTERN),
                    ServiceTrust.ONE_WAY);

    public static final ServiceTrustUseCase FEDERATED_REPO =
            new ServiceTrustUseCase("federated-repo",
                    List.of("^federated-repo/.*:[xw]$", "^federated-repo/\\*$", "artifact:\\*:r"),
                    ServiceTrust.ONE_WAY);

    public static final ServiceTrustUseCase FEDERATED_REPO_READ =
            new ServiceTrustUseCase("federated-repo",
                    List.of("^federated-repo/r$", "^federated-repo/.*:r$"),
                    ServiceTrust.ONE_WAY);

    private final String name;
    private final List<Pattern> patterns;
    private final ServiceTrust trust;

    ServiceTrustUseCase(String name, List<String> patterns, ServiceTrust trust) {
        this.name = name;
        this.patterns = patterns
                .stream()
                .map(Pattern::compile).toList();
        this.trust = trust;
    }

    public boolean matches(String candidate) {
        final String p = candidate.replace(ServiceTrustService.INTERNAL_SCOPE_PREFIX, "");
        return patterns.stream()
                .anyMatch(pattern -> pattern.matcher(p).matches());
    }

}
