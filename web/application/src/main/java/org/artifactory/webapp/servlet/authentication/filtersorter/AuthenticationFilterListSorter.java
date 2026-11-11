package org.artifactory.webapp.servlet.authentication.filtersorter;

import com.google.common.collect.Lists;
import org.artifactory.authentication.ArtifactoryAuthenticationContextFilter;
import org.artifactory.authentication.ArtifactoryAuthenticationFilter;
import org.artifactory.authentication.ArtifactoryAuthenticationFilterBase;
import org.artifactory.authentication.ArtifactoryAuthenticationType;


import jakarta.annotation.Nonnull;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

/**
 * author: einasa
 */
public class AuthenticationFilterListSorter {
    /**
     * The matter of this class is to reorder artifactory authentication filters
     * according to the List ORDERED_FILTERS
     */
    private static final List<ArtifactoryAuthenticationType> ORDERED_FILTERS =
            List.of(ArtifactoryAuthenticationType.ARTIFACTORY_SIGNED_URL,
                    ArtifactoryAuthenticationType.ACCESS_TOKEN,
                    ArtifactoryAuthenticationType.REMEMBER_ME,
                    ArtifactoryAuthenticationType.FORCE_AUTH,
                    ArtifactoryAuthenticationType.OSS_FORCE_AUTH,
                    ArtifactoryAuthenticationType.PROPS_AUTH,
                    ArtifactoryAuthenticationType.DOCKER_V2_AUTH,
                    ArtifactoryAuthenticationType.OCI_V2_AUTH,
                    ArtifactoryAuthenticationType.HA_REST_AUTH,
                    ArtifactoryAuthenticationType.NUGET_AUTH,
                    ArtifactoryAuthenticationType.RT_SSO_AUTH,
                    ArtifactoryAuthenticationType.ACCESS_SSO_AUTH,
                    ArtifactoryAuthenticationType.RT_MTLS_AUTH,
                    ArtifactoryAuthenticationType.ACCESS_MTLS_AUTH,
                    ArtifactoryAuthenticationType.CROWD_SSO_AUTH,
                    ArtifactoryAuthenticationType.VAGRANT_AUTH,
                    ArtifactoryAuthenticationType.PACKAGE_REROUTE_REPOS_AUTH,
                    ArtifactoryAuthenticationType.PACKAGES_AUTH,
                    ArtifactoryAuthenticationType.CAS_AUTH,
                    ArtifactoryAuthenticationType.BASIC_AUTH);

    public List<ArtifactoryAuthenticationFilterBase> orderArtifactoryFilters(
            @Nonnull Collection<ArtifactoryAuthenticationFilter> filters,
            @Nonnull Collection<ArtifactoryAuthenticationContextFilter> contextFilters) {
        List<ArtifactoryAuthenticationFilterBase> orderedResult = Lists.newArrayList();
        List<? extends ArtifactoryAuthenticationFilterBase>
                combinedFilters = Stream.concat(
                filters.stream(),
                contextFilters.stream()).toList();

        for (ArtifactoryAuthenticationType classFilter : ORDERED_FILTERS) {
            combinedFilters
                    .stream()
                    .filter(artifactoryAuthentication -> artifactoryAuthentication.getType().equals(classFilter))
                    .findFirst()
                    .ifPresent(orderedResult::add);
        }
        return orderedResult;
    }
}