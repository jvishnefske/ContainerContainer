package org.artifactory.util;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.descriptor.repo.PomCleanupPolicy;
import org.artifactory.descriptor.repo.TerraformType;
import org.artifactory.repo.packagetype.DockerApiVersion;
import org.artifactory.repo.vcs.VcsGitProvider;
import org.artifactory.repo.vcs.VcsType;

/**
 * @author Mor Merhav
 */
@Getter
public enum PackageTypeConfigFields {

    // Maven
    SUPPRESS_POM_CONSISTENCY_CHECKS("suppressPomConsistencyChecks"),
    FETCH_JARS_EAGERLY("fetchJarsEagerly", Boolean.TRUE.toString()),
    FETCH_SOURCES_EAGERLY("fetchSourcesEagerly"),
    REJECT_INVALID_JARS("rejectInvalidJars"),
    POM_REPOSITORY_REFERENCES_CLEANUP_POLICY("pomRepositoryReferencesCleanupPolicy",
            PomCleanupPolicy.discard_active_reference.name()),
    FORCE_MAVEN_AUTHENTICATION("forceMavenAuthentication"),

    // Vcs
    VCS_GIT_PROVIDER("vcsGitProvider", VcsGitProvider.GITHUB.name()),
    VCS_GIT_DOWNLOAD_URL("vcsGitDownloadUrl"),
    VCS_TYPE("vcsType", VcsType.GIT.name()),

    // Go
    GITLAB_RESOLVE_SUBGROUPS("gitLabResolveSubgroups", Boolean.FALSE.toString()),

    // External Dependencies
    EXTERNAL_DEPENDENCIES_ENABLED("externalDependenciesEnabled"),
    EXTERNAL_DEPENDENCIES_REMOTE_REPO_REF("externalDependenciesRemoteRepoRef"),
    EXTERNAL_DEPENDENCIES_PATTERNS("externalDependenciesPatterns"),

    // Yum
    CALCULATE_YUM_METADATA("calculateYumMetadata"),
    YUM_ROOT_DEPTH("yumRootDepth"),
    YUM_GROUP_FILENAMES("yumGroupFileNames"),
    ENABLE_FILE_LISTS_INDEXING("enableFileListsIndexing"),

    // Debian
    DEBIAN_TRIVIAL_LAYOUT("debianTrivialLayout"),
    OPTIONAL_INDEX_COMPRESSION_FORMATS("optionalIndexCompressionFormats", "[\"bz2\"]"),
    DDEB_SUPPORTED("ddebSupported"),
    DEBIAN_DEFAULT_ARCHITECTURES("debianDefaultArchitectures", "i386,amd64"),
    DEBIAN_OPTIONAL_INDEX_COMPRESSION_FORMATS("debianOptionalIndexCompressionFormats"),

    // Docker
    DOCKER_TAG_RETENTION("dockerTagRetention", "1"),
    DOCKER_API_VERSION("dockerApiVersion", DockerApiVersion.V2.name()),
    BLOCK_PUSHING_SCHEMA_1("blockPushingSchema1", Boolean.TRUE.toString()),
    MAX_UNIQUE_TAGS("maxUniqueTags"),
    RESOLVE_DOCKER_TAGS_BY_TIMESTAMP("resolveDockerTagsByTimestamp"),
    CACHING_LOCAL_FOREIGN_LAYERS_ENABLED("cachingLocalForeignLayersEnabled"),
    DOCKER_PROJECT_ID("dockerProjectId", StringUtils.EMPTY),

    // Cargo
    CARGO_ANONYMOUS_ACCESS("cargoAnonymousAccess"),
    CARGO_INTERNAL_INDEX("cargoInternalIndex"),

    // Composer
    ENABLE_COMPOSER_V1_INDEXING("enableComposerV1Indexing"),
    COMPOSER_REGISTRY_URL("composerRegistryUrl", "https://packagist.org"),

    // Terraform
    TERRAFORM_TYPE("terraformType", TerraformType.MODULE.getName()),
    TERRAFORM_REGISTRY_URL("terraformRegistryUrl", "https://registry.terraform.io"),
    TERRAFORM_PROVIDERS_URL("terraformProvidersUrl", "https://releases.hashicorp.com"),

    // Conan
    FORCE_CONAN_AUTHENTICATION("forceConanAuthentication"),

    // Nuget
    FORCE_NUGET_AUTHENTICATION("forceNugetAuthentication"),
    FORCE_P2_AUTHENTICATION("forceP2Authentication"),
    FEED_CONTEXT_PATH("feedContextPath", "api/v2"),
    DOWNLOAD_CONTEXT_PATH("downloadContextPath", "api/v2/package"),
    V3_FEED_URL("v3FeedUrl", "https://api.nuget.org/v3/index.json"),
    SYMBOL_SERVER_URL("symbolServerUrl", "https://symbols.nuget.org/download/symbols"),
    ENABLE_NUGET_NORMALIZED_VERSION("enableNormalizedVersion"),

    // Bower
    BOWER_REGISTRY_URL("bowerRegistryUrl", "https://registry.bower.io"),

    // Pypi
    PYPI_REGISTRY_URL("pyPIRegistryUrl", "https://pypi.org"),
    PYPI_REPOSITORY_SUFFIX("repositorySuffix", "simple"),

    // CocoaPoads
    COCOA_PODS_SPECS_URL("cocoaPodsSpecsRepoUrl", "https://github.com/CocoaPods/Specs"),
    COCOA_PODS_CDN_URL("cocoaPodsCdnUrl", "https://cdn.cocoapods.org"),
    COCOA_PODS_FORCE_MODIFY_INDEX("cocoaPodsForceModifyIndex"),
    SPECS_REPO_DOWNLOAD_URL("specsRepoDownloadUrl"),
    SPECS_REPO_DOWNLOAD_PROVIDER("specsRepoDownloadProvider", VcsGitProvider.GITHUB.name()),

    // Helm
    CHARTS_BASE_URL("chartsBaseUrl"),
    USE_NAMESPACES("useNamespaces"),

    // Git
    GIT_REGISTRY_URL("gitRegistryUrl"),

    // P2
    P2_URLS("urls"),
    P2_ORIGINAL_URL("p2OriginalUrl"),

    // TerraformBackend
    ENCRYPT_STATES("encryptStates","true"),

    //Hex
    HEX_REGISTRY_URL("hexRepositoryUrl", "https://repo.hex.pm"),
    HEX_REGISTRY_PUBLIC_KEY("hexPublicKey", """
            -----BEGIN PUBLIC KEY-----
            MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEApqREcFDt5vV21JVe2QNB
            Edvzk6w36aNFhVGWN5toNJRjRJ6m4hIuG4KaXtDWVLjnvct6MYMfqhC79HAGwyF+
            IqR6Q6a5bbFSsImgBJwz1oadoVKD6ZNetAuCIK84cjMrEFRkELtEIPNHblCzUkkM
            3rS9+DPlnfG8hBvGi6tvQIuZmXGCxF/73hU0/MyGhbmEjIKRtG6b0sJYKelRLTPW
            XgK7s5pESgiwf2YC/2MGDXjAJfpfCd0RpLdvd4eRiXtVlE9qO9bND94E7PgQ/xqZ
            J1i2xWFndWa6nfFnRxZmCStCOZWYYPlaxr+FZceFbpMwzTNs4g3d4tLNUcbKAIH4
            0wIDAQAB
            -----END PUBLIC KEY-----
            """),

    // HuggingFaceML
    HUGGINGFACE_ML_REPO_LAYOUT("isMlRepoLayout", "false"),

    // Ansible
    AUTH_URL("authUrl"),

    // AI-Editor Extensions
    VSCODE_EXTERNAL_DEPENDENCIES_ENABLED("externalDependenciesEnabled", "true"),
    VSCODE_EXTERNAL_DEPENDENCIES_PATTERNS("externalDependenciesPatterns", "[\"**/**vsassets.io/**\"]"),
    ;

    private final String key;
    private String defaultValue;

    PackageTypeConfigFields(String key) {
        this.key = key;
    }

    PackageTypeConfigFields(String key, String defaultValue) {
        this.key = key;
        this.defaultValue = defaultValue;
    }
}