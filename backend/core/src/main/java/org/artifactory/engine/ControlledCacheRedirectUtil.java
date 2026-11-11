package org.artifactory.engine;

import jakarta.servlet.http.HttpServletRequest;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.request.ArtifactoryHeaders;
import org.artifactory.request.Request;
import org.artifactory.request.RequestResponseHelper;
import org.artifactory.request.RequestThreadLocal;

import static org.artifactory.engine.ControlledCacheConstants.JFROG_CONTROLLED_CACHE_DOWNLOAD_VALUE;
import static org.artifactory.engine.ControlledCacheConstants.JFROG_CONTROLLED_CACHE_VALIDATE_VALUE;

@Slf4j
@UtilityClass
public class ControlledCacheRedirectUtil {

    /**
     * For the requests (redirects) coming from caching proxy we should not perform any download action,
     * like executing before/after download plugins, updating stats, adding traffic loss, etc.
     * this is because the interceptors were already executed on the 1st "validate" request from the caching proxy.
     */
    public static boolean shouldTriggerDownloadInterceptors(@NonNull Request request) {
        return !isCacheControlDownloadRequest(request);
    }

    /**
     * This "validate" means this is the 1st request from caching proxy to validate if the artifact exists in
     * Artifactory
     * currently for validate we might not stream back the artifact, but return a 302 with information on the checksum
     * if the 302 will back to artifactory is will be the "download" request
     */
    public static boolean isCacheControlValidateRequest(@NonNull Request request) {
        return checkCacheRequestHeader(JFROG_CONTROLLED_CACHE_VALIDATE_VALUE, request);
    }

    /**
     * This "download" means this is the 2nd request from caching proxy after the 1st "validate" request
     * it can happen when cache "miss" or when there was an internal error in cache (sha1 mismatch, error retrieving
     * from cache, etc)
     */
    public static boolean isCacheControlDownloadRequest(@NonNull Request request) {
        return checkCacheRequestHeader(JFROG_CONTROLLED_CACHE_DOWNLOAD_VALUE, request);
    }

    private static boolean checkCacheRequestHeader(@NonNull String expectedValue, @NonNull Request request) {
        String controlledCacheHeaderValue =
                RequestResponseHelper.getHeader(request, ArtifactoryHeaders.X_JFROG_CONTROLLED_CACHE_REQUEST);
        if (controlledCacheHeaderValue != null) {
            return controlledCacheHeaderValue.equalsIgnoreCase(expectedValue);
        }
        //todo: currently cannot use request to get the headers, because when package api is using
        //the downloadService it's not passing the original http header, but only "eligible" in some cases
        //this should be resolved with the packages team moving forward, using thread local instead
        HttpServletRequest servletRequest = RequestThreadLocal.getRequest();
        if (servletRequest == null) {
            return false;
        }
        return expectedValue.equalsIgnoreCase(
                servletRequest.getHeader(ArtifactoryHeaders.X_JFROG_CONTROLLED_CACHE_REQUEST));
    }

}