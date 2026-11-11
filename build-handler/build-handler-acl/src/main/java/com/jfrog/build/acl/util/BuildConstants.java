package com.jfrog.build.acl.util;

import lombok.experimental.UtilityClass;

@UtilityClass
public class BuildConstants {

    public static final String REPO_TYPE_LOCAL = "LOCAL";
    public static final String REPO_TYPE_FEDERATED = "FEDERATED";

    public static final String PACKAGE_TYPE_BUILD_INFO = "buildinfo";

    public static final String BUILDINFO_REPO_KEY = "artifactory-build-info";
    public static final String BUILD_INFO_REPO_KEY_SUFFIX = "-build-info";
    public static final String DEFAULT_RBV2_REPO = "release-bundles-v2";
    public static final String TRASH_REPO_KEY = "auto-trashcan";

    public static final String PACKAGE_TYPE_RB = "releasebundles";

    public static final String PROP_BUILD_NAME = "build.name";
    public static final String PROP_BUILD_NUMBER = "build.number";
    public static final String PROP_BUILD_TIMESTAMP = "build.timestamp";

    public static final String BUILD_RETENTION_JOB_WQ_NAME = "Build Retention Job";

    public static final String MARKER_DELETE_BUILD_ARTIFACT = "delete.build.artifact";
    public static final String BUILDS_EXPORT_DIR = "builds";

    public static final String MT_BUILDS =
            "application/vnd.org.jfrog.build.Builds+json";
    public static final String MT_BUILDS_BY_NAME =
            "application/vnd.org.jfrog.build.BuildsByName+json";
    public static final String MT_BUILD_INFO =
            "application/vnd.org.jfrog.build.BuildInfo+json";
    public static final String MT_BUILD_INFO_MODULE =
            "application/vnd.org.jfrog.build.BuildInfoModule+json";
    public static final String MT_BUILD_PATTERN_ARTIFACTS_REQUEST =
            "application/vnd.org.jfrog.build.BuildPatternArtifactsRequest+json";
    public static final String MT_BUILD_PATTERN_ARTIFACTS_RESULT =
            "application/vnd.org.jfrog.build.BuildPatternArtifactsResult+json";
    public static final String MT_BUILDS_DIFF =
            "application/vnd.org.jfrog.build.BuildsDiff+json";
    public static final String MT_LEGACY_ARTIFACTORY_APP =
            "application/vnd.org.jfrog.artifactory+json";
    public static final String MT_PROMOTION_REQUEST =
            "application/vnd.org.jfrog.artifactory.build.PromotionRequest+json";
    public static final String MT_PROMOTION_RESULT =
            "application/vnd.org.jfrog.artifactory.build.PromotionResult+json";
}
