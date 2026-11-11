package org.artifactory.entitlement;

public enum EntitlementType {
    LIVE_LOGS,
    BINARY_PROVIDER_DOWNLOAD_REDIRECT,
    CDN,
    ANONYMOUS_CDN,
    CHECKSUM_BASED_STORAGE_REPLICATION,
    EDGE, // identity
    COLD_STORAGE, //we are cold instance - identity
    CURATION,
    RELEASE_BUNDLES_READ,
    RELEASE_BUNDLES_WRITE,
    ACCESS_FEDERATION,
    BINARY_PROVIDER_CUSTOM_ENDPOINT,
    JFXR,
    FEDERATED_REPOSITORY,
    COLD_STORAGE_CREATE,
    RETENTION_WEBHOOKS,
    SIGNED_URL,
    HA,
    JFRT, // artifactory that not edge or ice - identity
    MULTIPUSH_REPLICATION,
    EVENT_BASED_PULL_REPLICATION,
    LDAP,
    SAML,
    CROWD,
    OAUTH,
    BINARY_PROVIDER_RSH,
    MTLS,
    ALIBABA_ARTIFACTORY, // artifactory running on Alibaba Cloud
    CLEANUP_POLICY,

    //those should be loading unless we have an edge license. replacement for @EdgeBlockedAddon
    REPO_REPLICATION,
    SMART_REMOTE_TARGET_FOR_EDGE,

    // this part will be deleted when entitlementService will be stable,
    EDGE_MIXED,
    RELEASE_BUNDLES_WRITE_MIX,
    BINARY_PROVIDER_DOWNLOAD_REDIRECT_MIX,

    // this part is for event hardening
    HARDENED_ARTIFACTORY_EVENTS,
    HARDENED_ARTIFACTORY_USER_PLUGIN_EVENTS,

    //this part is for disable plugin infra
    DISABLE_PLUGIN_INFRA,
    PACKAGES_IDE_EXTENSION,
    AI_CATALOG,
    //Package registry URL reroute (package-reroute).
    PACKAGE_REROUTE
}
