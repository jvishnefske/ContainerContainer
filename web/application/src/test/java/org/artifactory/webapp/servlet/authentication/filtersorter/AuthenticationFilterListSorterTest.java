package org.artifactory.webapp.servlet.authentication.filtersorter;

import org.artifactory.authentication.ArtifactoryAuthenticationContextFilter;
import org.artifactory.authentication.ArtifactoryAuthenticationFilter;
import org.artifactory.authentication.ArtifactoryAuthenticationFilterBase;
import org.artifactory.authentication.ArtifactoryAuthenticationType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.testng.collections.Lists;


import java.util.List;

import static org.mockito.Mockito.when;

/**
 * @author einasa
 */
class AuthenticationFilterListSorterTest {
    private static final List<ArtifactoryAuthenticationType> ARTIFACTORY_CONTEXT_FILTERS =
            List.of(ArtifactoryAuthenticationType.PACKAGES_AUTH);
    private List<ArtifactoryAuthenticationFilter> authList;
    private List<ArtifactoryAuthenticationContextFilter> authContextList;

    @BeforeEach
    void init() {
        authList = Lists.newArrayList();
        authContextList = Lists.newArrayList();
    }

    void mockFilters(List<ArtifactoryAuthenticationType> authenticationFiltersList) {
        if (authenticationFiltersList == null) {
            return;
        }
        authenticationFiltersList.forEach(authFilter -> {
            ArtifactoryAuthenticationFilter artifactoryFilter = Mockito.mock(ArtifactoryAuthenticationFilter.class);
            when(artifactoryFilter.getType()).thenReturn(authFilter);
            authList.add(artifactoryFilter);
        });

        AuthenticationFilterListSorterTest.ARTIFACTORY_CONTEXT_FILTERS.forEach(authContextFilter -> {
            ArtifactoryAuthenticationContextFilter artifactoryFilter =
                    Mockito.mock(ArtifactoryAuthenticationContextFilter.class);
            when(artifactoryFilter.getType()).thenReturn(authContextFilter);
            authContextList.add(artifactoryFilter);
        });
    }

    @Test
    void filterOrderInPro() {
        List<ArtifactoryAuthenticationType> artifactoryFilters =
                List.of(ArtifactoryAuthenticationType.NUGET_AUTH,
                        ArtifactoryAuthenticationType.ACCESS_TOKEN,
                        ArtifactoryAuthenticationType.CROWD_SSO_AUTH,
                        ArtifactoryAuthenticationType.REMEMBER_ME,
                        ArtifactoryAuthenticationType.FORCE_AUTH,
                        ArtifactoryAuthenticationType.PROPS_AUTH,
                        ArtifactoryAuthenticationType.DOCKER_V2_AUTH,
                        ArtifactoryAuthenticationType.ACCESS_SSO_AUTH,
                        ArtifactoryAuthenticationType.RT_SSO_AUTH,
                        ArtifactoryAuthenticationType.RT_MTLS_AUTH,
                        ArtifactoryAuthenticationType.ACCESS_MTLS_AUTH,
                        ArtifactoryAuthenticationType.VAGRANT_AUTH,
                        ArtifactoryAuthenticationType.BASIC_AUTH,
                        ArtifactoryAuthenticationType.ARTIFACTORY_SIGNED_URL);

        List<ArtifactoryAuthenticationType> expectedOrder =
                List.of(ArtifactoryAuthenticationType.ARTIFACTORY_SIGNED_URL,
                        ArtifactoryAuthenticationType.ACCESS_TOKEN,
                        ArtifactoryAuthenticationType.REMEMBER_ME,
                        ArtifactoryAuthenticationType.FORCE_AUTH,
                        ArtifactoryAuthenticationType.PROPS_AUTH,
                        ArtifactoryAuthenticationType.DOCKER_V2_AUTH,
                        ArtifactoryAuthenticationType.NUGET_AUTH,
                        ArtifactoryAuthenticationType.RT_SSO_AUTH,
                        ArtifactoryAuthenticationType.ACCESS_SSO_AUTH,
                        ArtifactoryAuthenticationType.RT_MTLS_AUTH,
                        ArtifactoryAuthenticationType.ACCESS_MTLS_AUTH,
                        ArtifactoryAuthenticationType.CROWD_SSO_AUTH,
                        ArtifactoryAuthenticationType.VAGRANT_AUTH,
                        ArtifactoryAuthenticationType.PACKAGES_AUTH,
                        ArtifactoryAuthenticationType.BASIC_AUTH);

        mockFilters(artifactoryFilters);
        AuthenticationFilterListSorter filterSorter = new AuthenticationFilterListSorter();
        List<ArtifactoryAuthenticationFilterBase> resultsAfterOrdering =
                filterSorter.orderArtifactoryFilters(authList, authContextList);
        Assertions.assertEquals(expectedOrder,
                resultsAfterOrdering.stream().map(ArtifactoryAuthenticationFilterBase::getType).toList());
    }

    @Test
    void filterOrderInJcr() {
        // Those filter is uploaded when starting artifactory JCR
        List<ArtifactoryAuthenticationType> artifactoryFilters =
                List.of(ArtifactoryAuthenticationType.REMEMBER_ME,
                        ArtifactoryAuthenticationType.OSS_FORCE_AUTH,
                        ArtifactoryAuthenticationType.PROPS_AUTH,
                        ArtifactoryAuthenticationType.ACCESS_TOKEN,
                        ArtifactoryAuthenticationType.DOCKER_V2_AUTH,
                        ArtifactoryAuthenticationType.ARTIFACTORY_SIGNED_URL,
                        ArtifactoryAuthenticationType.BASIC_AUTH
                );

        List<ArtifactoryAuthenticationType> expectedOrder =
                List.of(
                        ArtifactoryAuthenticationType.ARTIFACTORY_SIGNED_URL,
                        ArtifactoryAuthenticationType.ACCESS_TOKEN,
                        ArtifactoryAuthenticationType.REMEMBER_ME,
                        ArtifactoryAuthenticationType.OSS_FORCE_AUTH,
                        ArtifactoryAuthenticationType.PROPS_AUTH,
                        ArtifactoryAuthenticationType.DOCKER_V2_AUTH,
                        ArtifactoryAuthenticationType.PACKAGES_AUTH,
                        ArtifactoryAuthenticationType.BASIC_AUTH
                );

        mockFilters(artifactoryFilters);
        AuthenticationFilterListSorter filterSorter = new AuthenticationFilterListSorter();
        List<ArtifactoryAuthenticationFilterBase> resultsAfterOrdering =
                filterSorter.orderArtifactoryFilters(authList, authContextList);
        Assertions.assertEquals(expectedOrder,
                resultsAfterOrdering.stream().map(ArtifactoryAuthenticationFilterBase::getType).toList());
    }

    @Test
    void filterOrderInCPP() {
        // Those filter is uploaded when starting artifactory CPP-CE
        List<ArtifactoryAuthenticationType> artifactoryFilters =
                List.of(ArtifactoryAuthenticationType.REMEMBER_ME,
                        ArtifactoryAuthenticationType.OSS_FORCE_AUTH,
                        ArtifactoryAuthenticationType.PROPS_AUTH,
                        ArtifactoryAuthenticationType.ACCESS_TOKEN,
                        ArtifactoryAuthenticationType.ARTIFACTORY_SIGNED_URL,
                        ArtifactoryAuthenticationType.BASIC_AUTH
                );

        List<ArtifactoryAuthenticationType> expectedOrder =
                List.of(
                        ArtifactoryAuthenticationType.ARTIFACTORY_SIGNED_URL,
                        ArtifactoryAuthenticationType.ACCESS_TOKEN,
                        ArtifactoryAuthenticationType.REMEMBER_ME,
                        ArtifactoryAuthenticationType.OSS_FORCE_AUTH,
                        ArtifactoryAuthenticationType.PROPS_AUTH,
                        ArtifactoryAuthenticationType.PACKAGES_AUTH,
                        ArtifactoryAuthenticationType.BASIC_AUTH
                );

        mockFilters(artifactoryFilters);
        AuthenticationFilterListSorter filterSorter = new AuthenticationFilterListSorter();
        List<ArtifactoryAuthenticationFilterBase> resultsAfterOrdering =
                filterSorter.orderArtifactoryFilters(authList, authContextList);
        Assertions.assertEquals(expectedOrder,
                resultsAfterOrdering.stream().map(ArtifactoryAuthenticationFilterBase::getType).toList());
    }

    @Test
    void filterOrderInHA() {
        // Those filter is uploaded when starting artifactory HA
        List<ArtifactoryAuthenticationType> artifactoryFilters =
                List.of(ArtifactoryAuthenticationType.BASIC_AUTH,
                        ArtifactoryAuthenticationType.ARTIFACTORY_SIGNED_URL,
                        ArtifactoryAuthenticationType.ACCESS_TOKEN,
                        ArtifactoryAuthenticationType.VAGRANT_AUTH,
                        ArtifactoryAuthenticationType.REMEMBER_ME,
                        ArtifactoryAuthenticationType.FORCE_AUTH,
                        ArtifactoryAuthenticationType.PROPS_AUTH,
                        ArtifactoryAuthenticationType.RT_SSO_AUTH,
                        ArtifactoryAuthenticationType.RT_MTLS_AUTH,
                        ArtifactoryAuthenticationType.ACCESS_MTLS_AUTH,
                        ArtifactoryAuthenticationType.CROWD_SSO_AUTH,
                        ArtifactoryAuthenticationType.DOCKER_V2_AUTH,
                        ArtifactoryAuthenticationType.HA_REST_AUTH,
                        ArtifactoryAuthenticationType.NUGET_AUTH,
                        ArtifactoryAuthenticationType.ACCESS_SSO_AUTH);

        List<ArtifactoryAuthenticationType> expectedOrder =
                List.of(ArtifactoryAuthenticationType.ARTIFACTORY_SIGNED_URL,
                        ArtifactoryAuthenticationType.ACCESS_TOKEN,
                        ArtifactoryAuthenticationType.REMEMBER_ME,
                        ArtifactoryAuthenticationType.FORCE_AUTH,
                        ArtifactoryAuthenticationType.PROPS_AUTH,
                        ArtifactoryAuthenticationType.DOCKER_V2_AUTH,
                        ArtifactoryAuthenticationType.HA_REST_AUTH,
                        ArtifactoryAuthenticationType.NUGET_AUTH,
                        ArtifactoryAuthenticationType.RT_SSO_AUTH,
                        ArtifactoryAuthenticationType.ACCESS_SSO_AUTH,
                        ArtifactoryAuthenticationType.RT_MTLS_AUTH,
                        ArtifactoryAuthenticationType.ACCESS_MTLS_AUTH,
                        ArtifactoryAuthenticationType.CROWD_SSO_AUTH,
                        ArtifactoryAuthenticationType.VAGRANT_AUTH,
                        ArtifactoryAuthenticationType.PACKAGES_AUTH,
                        ArtifactoryAuthenticationType.BASIC_AUTH);

        mockFilters(artifactoryFilters);
        AuthenticationFilterListSorter filterSorter = new AuthenticationFilterListSorter();
        List<ArtifactoryAuthenticationFilterBase> resultsAfterOrdering =
                filterSorter.orderArtifactoryFilters(authList, authContextList);
        Assertions.assertEquals(expectedOrder,
                resultsAfterOrdering.stream().map(ArtifactoryAuthenticationFilterBase::getType).toList());
    }

    @Test
    void filterOrderInCloud() {
        // Those filter is uploaded when starting artifactory CLOUD
        List<ArtifactoryAuthenticationType> artifactoryFilters =
                List.of(ArtifactoryAuthenticationType.BASIC_AUTH,
                        ArtifactoryAuthenticationType.ARTIFACTORY_SIGNED_URL,
                        ArtifactoryAuthenticationType.CAS_AUTH,
                        ArtifactoryAuthenticationType.ACCESS_TOKEN,
                        ArtifactoryAuthenticationType.VAGRANT_AUTH,
                        ArtifactoryAuthenticationType.REMEMBER_ME,
                        ArtifactoryAuthenticationType.FORCE_AUTH,
                        ArtifactoryAuthenticationType.PROPS_AUTH,
                        ArtifactoryAuthenticationType.RT_SSO_AUTH,
                        ArtifactoryAuthenticationType.RT_MTLS_AUTH,
                        ArtifactoryAuthenticationType.ACCESS_MTLS_AUTH,
                        ArtifactoryAuthenticationType.CROWD_SSO_AUTH,
                        ArtifactoryAuthenticationType.DOCKER_V2_AUTH,
                        ArtifactoryAuthenticationType.HA_REST_AUTH,
                        ArtifactoryAuthenticationType.NUGET_AUTH,
                        ArtifactoryAuthenticationType.ACCESS_SSO_AUTH);

        List<ArtifactoryAuthenticationType> expectedOrder =
                List.of(ArtifactoryAuthenticationType.ARTIFACTORY_SIGNED_URL,
                        ArtifactoryAuthenticationType.ACCESS_TOKEN,
                        ArtifactoryAuthenticationType.REMEMBER_ME,
                        ArtifactoryAuthenticationType.FORCE_AUTH,
                        ArtifactoryAuthenticationType.PROPS_AUTH,
                        ArtifactoryAuthenticationType.DOCKER_V2_AUTH,
                        ArtifactoryAuthenticationType.HA_REST_AUTH,
                        ArtifactoryAuthenticationType.NUGET_AUTH,
                        ArtifactoryAuthenticationType.RT_SSO_AUTH,
                        ArtifactoryAuthenticationType.ACCESS_SSO_AUTH,
                        ArtifactoryAuthenticationType.RT_MTLS_AUTH,
                        ArtifactoryAuthenticationType.ACCESS_MTLS_AUTH,
                        ArtifactoryAuthenticationType.CROWD_SSO_AUTH,
                        ArtifactoryAuthenticationType.VAGRANT_AUTH,
                        ArtifactoryAuthenticationType.PACKAGES_AUTH,
                        ArtifactoryAuthenticationType.CAS_AUTH,
                        ArtifactoryAuthenticationType.BASIC_AUTH);

        mockFilters(artifactoryFilters);
        AuthenticationFilterListSorter filterSorter = new AuthenticationFilterListSorter();
        List<ArtifactoryAuthenticationFilterBase> resultsAfterOrdering =
                filterSorter.orderArtifactoryFilters(authList, authContextList);
        Assertions.assertEquals(expectedOrder,
                resultsAfterOrdering.stream().map(ArtifactoryAuthenticationFilterBase::getType).toList());
    }

    @Test
    void filterOrderInOss() {
        // Those filter is uploaded when starting artifactory OSS
        List<ArtifactoryAuthenticationType> artifactoryFilters =
                List.of(ArtifactoryAuthenticationType.BASIC_AUTH,
                        ArtifactoryAuthenticationType.ARTIFACTORY_SIGNED_URL,
                        ArtifactoryAuthenticationType.ACCESS_TOKEN,
                        ArtifactoryAuthenticationType.REMEMBER_ME,
                        ArtifactoryAuthenticationType.OSS_FORCE_AUTH,
                        ArtifactoryAuthenticationType.PROPS_AUTH);

        List<ArtifactoryAuthenticationType> expectedOrder =
                List.of(ArtifactoryAuthenticationType.ARTIFACTORY_SIGNED_URL,
                        ArtifactoryAuthenticationType.ACCESS_TOKEN,
                        ArtifactoryAuthenticationType.REMEMBER_ME,
                        ArtifactoryAuthenticationType.OSS_FORCE_AUTH,
                        ArtifactoryAuthenticationType.PROPS_AUTH,
                        ArtifactoryAuthenticationType.PACKAGES_AUTH,
                        ArtifactoryAuthenticationType.BASIC_AUTH);

        mockFilters(artifactoryFilters);
        AuthenticationFilterListSorter filterSorter = new AuthenticationFilterListSorter();
        List<ArtifactoryAuthenticationFilterBase> resultsAfterOrdering =
                filterSorter.orderArtifactoryFilters(authList, authContextList);
        Assertions.assertEquals(expectedOrder,
                resultsAfterOrdering.stream().map(ArtifactoryAuthenticationFilterBase::getType).toList());
    }
}