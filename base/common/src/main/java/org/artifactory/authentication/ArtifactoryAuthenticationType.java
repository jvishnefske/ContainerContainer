package org.artifactory.authentication;

/**
 * Author: einasa
 */
public enum ArtifactoryAuthenticationType {

    ARTIFACTORY_SIGNED_URL("ArtifactorySignedUrlAuthenticationFilter"),
    ACCESS_TOKEN("ArtifactoryAccessTokenAuthenticationFilter"),
    REMEMBER_ME("ArtifactoryRememberMeFilter"),
    OSS_FORCE_AUTH("OssForceAuthenticationFilter"),
    FORCE_AUTH("ForceAuthenticationFilter"),
    PROPS_AUTH("PropsAuthenticationFilter"),
    DOCKER_V2_AUTH("DockerV2AuthenticationFilter"),
    OCI_V2_AUTH("OciV2AuthenticationFilter"),
    HA_REST_AUTH("HaRestAuthenticationFilter"),
    NUGET_AUTH("NuGetAuthenticationFilter"),
    RT_SSO_AUTH("ArtifactoryHttpSsoAuthenticationFilter"),
    ACCESS_SSO_AUTH("AccessHttpSsoAuthenticationFilter"),
    RT_MTLS_AUTH("ArtifactoryMtlsAuthenticationToken"),
    ACCESS_MTLS_AUTH("AccessMtlsAuthenticationToken"),
    CROWD_SSO_AUTH("CrowdSsoAuthenticationFilter"),
    VAGRANT_AUTH("VagrantAuthenticationFilter"),
    PACKAGE_REROUTE_REPOS_AUTH("PackageRerouteRepositoryAuthenticationFilter"),
    PACKAGES_AUTH("PackageAuthenticationFilterImpl"),
    CAS_AUTH("CasAuthenticationFilter"),
    BASIC_AUTH("ArtifactoryBasicAuthenticationFilter");

    private final String artifactoryAuthClassName;

    ArtifactoryAuthenticationType(String artifactoryCAuthClassName) {
        this.artifactoryAuthClassName = artifactoryCAuthClassName;
    }

    public String getKey() {
        return artifactoryAuthClassName;
    }
}
