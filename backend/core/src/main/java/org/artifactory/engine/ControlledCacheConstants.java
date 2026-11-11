package org.artifactory.engine;

public interface ControlledCacheConstants {

    String JFROG_CONTROLLED_CACHE_VALIDATE_VALUE = "validate"; // 1st request header value from caching proxy
    String JFROG_CONTROLLED_CACHE_DOWNLOAD_VALUE = "download"; // 2nd request header value from caching proxy
    // This is read only by controlled cache. It is used to identify the artifact being requested by the caching proxy.
    // The value is expected to be the SHA1 of the artifact.
    String SHA1_QUERY_PARAM = "key";

    // Response header (X-JFrog-Cache-Response) values. Hint to the caching proxy about what shape
    // of response to expect on the subsequent download request:
    //   stream   -> download will return 200 with a streamed body (DIRECT/filesystem-backed)
    //   redirect -> download will return another 302 to the cloud signed URL that the caching
    //               proxy is expected to follow
    String JFROG_CONTROLLED_CACHE_RESPONSE_STREAM_VALUE = "stream";
    String JFROG_CONTROLLED_CACHE_RESPONSE_REDIRECT_VALUE = "redirect";
}