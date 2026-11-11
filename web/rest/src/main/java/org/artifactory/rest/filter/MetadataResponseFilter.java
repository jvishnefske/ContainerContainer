package org.artifactory.rest.filter;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.common.ConstantValues;
import org.artifactory.util.BasicHttpUtils;
import org.jfrog.common.config.metadata.MetadataResponse;

import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;

import static org.artifactory.api.rest.constant.RepositoriesRestConstants.CACHE_CONTROL_NO_STORE;
import static org.artifactory.api.rest.constant.RepositoriesRestConstants.MAX_AGE;

/**
 * A Response Filter that adds a "Cache-Control" header to metadata endpoints response
 * that filter by @MetadataResponse, See {@link org.jfrog.common.config.metadata.MetadataResponse}
 * <p>
 *  metadataCacheControlHeaderSeconds < 0 -> no header at all
 *  metadataCacheControlHeaderSeconds = 0 -> no-store header
 *  metadataCacheControlHeaderSeconds > 0 -> max-age=metadataCacheControlHeaderSecondsValue header
 * </p>
 *
 */

@Provider
@Slf4j
public class MetadataResponseFilter implements ContainerResponseFilter {

    private final String cacheControlHeader;

    public MetadataResponseFilter() {
        cacheControlHeader = getCacheControlHeader();
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
            throws IOException {
        if (!shouldLookForMetadataResponseAnnotation(requestContext, responseContext, cacheControlHeader)) {
            return;
        }
        log.debug("metadata response include the cache-control header {}", cacheControlHeader);
        List<Annotation> annotations = Arrays.stream(responseContext.getEntityAnnotations()).toList();
        if (annotations.stream()
                    .map(Annotation::annotationType)
                    .anyMatch(annotation -> annotation.isAssignableFrom(MetadataResponse.class))) {
            responseContext.getHeaders().add(HttpHeaders.CACHE_CONTROL, cacheControlHeader);
        }
    }

    private String getCacheControlHeader() {
        int maxAge = ConstantValues.metadataCacheControlHeaderSeconds.getInt();
        if (maxAge == 0) {
            return CACHE_CONTROL_NO_STORE;
        }
        if (maxAge > 0) {
            return String.format("%s=%d", MAX_AGE, maxAge);
        }
        return "";
    }

    private boolean shouldLookForMetadataResponseAnnotation(ContainerRequestContext requestContext,
                    ContainerResponseContext responseContext, String cacheControlHeader) {
        return StringUtils.isNotBlank(cacheControlHeader)
                && requestContext.getMethod().equals(HttpMethod.GET)
                && BasicHttpUtils.isInSuccessOrRedirectResponseCodeRange(responseContext.getStatus());
    }
}
