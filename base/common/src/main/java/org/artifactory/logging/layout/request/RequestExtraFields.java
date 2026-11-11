package org.artifactory.logging.layout.request;

import lombok.experimental.UtilityClass;

@UtilityClass
public class RequestExtraFields {
    /// The package type, relevant for the current request (if any)
    public static final String PACKAGE_TYPE = "package";
    /// The API group the current request belongs to (see {@link org.artifactory.api.rest.constant.RestApiGroup})
    public static final String API_GROUP = "group";
    /// The repository key involved in the current request (if any)
    public static final String REPO = "repo";
    /// The repository type involved in the current request (if any)
    public static final String REPO_TYPE = "repo_type";
    /// The contents of the <c>X-Request-ID</c> header if any (see RTDEV-43137)
    public static final String EXTERNAL_ID = "xid";
    /// The time (msec) elapsed between the beginning of the request’s processing until the moment
    /// Artifactory sent the HTTP status code
    public static final String TIME_TO_FIRST_BYTE = "ttfb";
    /// The full size of the requested artifact (in bytes) - present when Artifactory returned only a part of
    /// the requested artifact. It can happen for multiple reasons: redirect to S3, client originally requested
    /// a portion of data (byte range request), request could fail in the middle (networking issues / client aborted).
    public static final String ARTIFACT_SIZE = "size";
    /// The time spent on downloading the binary from the remote repository to the local cache.
    /// Relevant only for download requests.
    public static final String REMOTE_DOWNLOAD_TIME = "dtime";
    /// CDN, S3, Azure, GCP
    public static final String REDIRECT_TARGET = "redirect_target";
    // A dictionary, specifying how much time (msec) was spent waiting for external services, like Xray, Curation, etc.
    public static final String WAITED_TIME = "waited";

    //#region ---------- Byte range requests (see RTDEV-58322) ----------
    /// The number of byte ranges requested. Relevant only for requests having <c>Ranges</c> header.
    public static final String RANGES_COUNT = "ranges";
    /// The smallest of the byte range offsets. Relevant only for requests having <c>Ranges</c> header.
    public static final String RANGES_OFFSET = "offset";
    /// The size of the requests byte range(s). Relevant only for requests having <c>Ranges</c> header.
    public static final String RANGES_SIZE = "rsize";
    //#endregion

    /// Checksum of the normalized AQL query template. Present on every <c>POST /api/search/aql</c>
    /// line (including 429s) when the AQL statistics collection feature is enabled.
    public static final String AQL_QUERY_CHECKSUM = "aql_checksum";
}
