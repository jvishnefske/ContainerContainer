package org.artifactory.api.rest.constant;

import lombok.experimental.UtilityClass;

/**
 * Defines constants for categorizing REST APIs. These categories will be then used in metrics.
 * We MUST keep this list as short as possible, because of the metrics cardinality problem.
 *
 * @author Sergey Steinvil
 */
@UtilityClass
public class RestApiGroup {
    /**
     * Use this group to mark REST APIs that are responsible for checking the liveness and readiness of the system
     */
    public final String PROBES_API = "probes";

    /**
     * Use this group to mark REST APIs, providing information about the system -
     */
    public final String SYSTEM_INFO_API = "system/info";

    /**
     * Use this group to mark REST APIs that help users to manage (configure, administer) the system
     */
    public final String MANAGEMENT_API = "manage";

    /**
     * Should be common for operations on artifacts: download, copy, move, etc.
     */
    public final String ARTIFACT_API = "artifact";

    /**
     * Should be common for all search APIs, except search with AQL (see the next constant)
     */
    public final String SEARCH_API = "search";

    /**
     * Should be applied on REST APIs, performing search with AQL (our own query language).
     * This kind of searches is less predictable, so we would like to monitor it separately
     */
    public final String SEARCH_AQL_API = "search/aql";

    /**
     * Use this group to mark REST APIs that are related to authentication
     */
    public final String AUTH_API = "auth";

    /**
     * Use this group to mark REST APIs that are related to the tree browser (tree nodes)
     */
    public final String BROWSE_API = "browse";

    /**
     * Use this group to mark REST APIs that are related to loading the repositories of the root page in the tree
     * browser
     */
    public final String BROWSE_REPOS_API = "browse/repo";

    /**
     * Use this group to mark REST APIs that are related to navigating repositories in the tree browser, for a specific
     * artifact in the tree (Show in Tree)
     */
    public final String BROWSE_SHOW_IN_TREE_API = "browse/showInTree";

    // Use this group to mark REST APIs that downloads a package (or one of its artifacts).
    public static final String DOWNLOAD = "download";

    // Use this group to mark REST APIs that provide a metadata about the repository (but do not list packages)
    public static final String REPO_INFO = "repo_info";
    // Use this group to mark REST APIs that list packages in a repository
    public static final String REPO_LIST_PACKAGES = "repo_index";
    // Use this group to mark REST APIs that return information about a package (usually listing all its versions).
    public static final String PACKAGE_INFO = "pkg_info";
    // Use this group to mark REST APIs that return information about a specific package version.
    public static final String PACKAGE_VER_INFO = "pkg_ver_info";
    // Use this group to mark REST APIs that upload package artifacts
    public static final String UPLOAD = "upload";
}
