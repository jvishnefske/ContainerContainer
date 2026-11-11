package org.artifactory.api.request;

import java.util.Set;

public interface ArtifactoryHeaders {
    @Deprecated
    String ORIGIN_ARTIFACTORY = "Origin-Artifactory";

    String ARTIFACTORY_ORIGINATED = "X-Artifactory-Originated";

    String ARTIFACTORY_OVERRIDE_BASE_URL = "X-Artifactory-Override-Base-Url";

    String JFROG_OVERRIDE_BASE_URL = "X-JFrog-Override-Base-Url";

    String ORIGINAL_CLIENT_URI = "X-Orig-Client-Uri";

    String X_FORWARDED_HOST = "X-Forwarded-Host";

    String X_FORWARDED_PROTO = "X-Forwarded-Proto";

    String CHECKSUM_SHA1 = "X-Checksum-Sha1";

    String CHECKSUM_SHA256 = "X-Checksum-Sha256";

    //Should be used for incoming values, not sure there's a lot of sense in sending this out?
    String CHECKSUM = "X-Checksum";

    String CHECKSUM_MD5 = "X-Checksum-Md5";

    String BINARY_SIZE = "X-Binary-Size";

    String ACCEPT_RANGES = "Accept-Ranges";

    String FILE_NAME = "X-Artifactory-Filename";

    /**
     * An header to trigger checksum deploy (when the value is true). Request must also include
     * {@link  org.artifactory.api.request.ArtifactoryHeaders#CHECKSUM_SHA1}.
     */
    String CHECKSUM_DEPLOY = "X-Checksum-Deploy";

    /**
     * if set to true - will retrieve the actual binary from the binary store (if it exists)
     */
    String CHECK_BINARY_EXISTENCE_IN_FILESTORE = "X-Check-Binary-Existence-In-Filestore";

    /**
     * Header to trigger bundle archive deployment (supports zip/tar/tar.gz)
     */
    String EXPLODE_ARCHIVE = "X-Explode-Archive";

    String EXPLODE_ARCHIVE_ATOMIC = "X-Explode-Archive-Atomic";

    String RESULT_DETAIL = "X-Result-Detail";

    String PARAM_SKIP_JAR_INDEXING = "artifactory.skipJarIndexing";

    String PARAM_FORCE_DOWNLOAD_IF_NEWER = "artifactory.forceDownloadIfNewer";

    /**
     * Causes the download service to ignore repository configuration for download redirect
     */
    String PARAM_FORCE_GET_STREAM = "artifactory.disableRedirect";

    String PARAM_SEARCH_FOR_EXISTING_RESOURCE_ON_REMOTE_REQUEST =
            "artifactory.searchForExistingResourceOnRemoteRequest";

    String PARAM_ALTERNATIVE_REMOTE_DOWNLOAD_URL = "artifactory.alternativeRemoteDownloadUrl";

    String PARAM_ALTERNATIVE_REMOTE_SITE_URL = "artifactory.alternativeRemoteSiteUrl";

    String PARAM_REPLICATION_DOWNLOAD_REQUEST = "artifactory.replicationDownloadRequest";

    /**
     * Signifies request seems to have originated in another Artifactory and was directed at a package api
     * replication endpoint
     */
    String PARAM_REPLICATION_ORIGINATED_DOWNLOAD_REQUEST = "artifactory.replicationOriginatedDownloadRequest";

    String PARAM_FOLDER_REDIRECT_ASSERTION = "artifactory.disableFolderRedirectAssertion";

    /**
     * Will replace the HEAD request in RetrieveInfo with a GET request
     */
    String PARAM_REPLACE_HEAD_IN_RETRIEVE_INFO_WITH_GET = "artifactory.replaceHeadInRetrieveInfoWithGet";

    /**
     * Will skip the encoding of the alternative remote download url, assumes it's already encoded
     */
    String PARAM_SKIP_ENCODING = "artifactory.skipEncoding";

    /**
     * The path prefix name for list browsing.
     */
    String LIST_BROWSING_PATH = "list";

    /**
     * The path prefix name for simple browsing.
     */
    String SIMPLE_BROWSING_PATH = "simple";

    String LAST_MODIFIED = "X-Artifactory-Last-Modified";

    String CREATED = "X-Artifactory-Created";

    String MODIFIED_BY = "X-Artifactory-Modified-By";

    String CREATED_BY = "X-Artifactory-Created-By";

    String USER = "X-Artifactory-User";

    String EMAIL = "X-Artifactory-Email";

    String REPO_KEY = "X-Artifactory-Repo-Key";

    String PROJECT_KEY = "X-Artifactory-Project-Key";

    String PACKAGE_TYPE = "X-Artifactory-Package-Type";

    String PACKAGE_URL = "X-Artifactory-Package-Url";

    String SERVER = "X-Artifactory-Server";

    String THIRD_PARTY_ORIGIN = "X-Artifactory-Third-Party-Origin";
    /**
     * When set to true, checking for the existence of a resource will be done using the md5 instead of the sha1
     */
    String IF_NONE_MATCH_MD5 = "X-Artifactory-If-None-Match-MD5";

    String XRAY_ORIGIN = "X-Artifactory-Xray-Origin";

    String CURATION_ORIGIN = "X-Artifactory-Curation-Origin";

    String PASS_THROUGH_KEY = "X-Artifactory-Pass-Through";

    String CURATION_WAIVER_REQUEST_KEY = "X-Artifactory-Curation-Request-Waiver";

    /**
     * Prefix for headers that callers can use to forward extra context to the Curation service and
     * to any outgoing HTTP remote-repo request. Forwarding is case-insensitive on the header name.
     */
    String CURATION_HEADER_PREFIX = "X-Artifactory-Curation-";

    String X_CHECKSUM_DEPLOY_TOKEN = "X-Checksum-Deploy-Token";

    String[] CURATION_HEADERS = new String[] {CURATION_WAIVER_REQUEST_KEY, PASS_THROUGH_KEY};

    String X_JFROG_CONTROLLED_CACHE_REQUEST = "X-JFrog-Cache-Request";
    String X_JFROG_CONTROLLED_CACHE_RESPONSE = "X-JFrog-Cache-Response";

    String ARTIFACTORY_ARCHIVE = "artifactory-archive";

    Set<String> CURATION_HEADERS_FOR_REPO = Set.of(PACKAGE_TYPE, PACKAGE_URL, REPO_KEY,
            PROJECT_KEY, SERVER, CURATION_WAIVER_REQUEST_KEY, PASS_THROUGH_KEY);

    String ORIGIN_REMOTE_PATH = "X-Artifactory-Origin-Remote-Path";

}
