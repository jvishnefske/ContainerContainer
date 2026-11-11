/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.rest.common;


import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.concurrent.ArtifactoryExecutor;
import org.artifactory.concurrent.ArtifactoryExecutorFactory;
import org.artifactory.concurrent.ThreadPoolConfig;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RepoConfig;
import org.artifactory.shadow.ShadowRequestContext;
import org.artifactory.shadow.ShadowRequestService;
import org.artifactory.shadow.ShadowResponseContext;
import org.artifactory.shadow.ShadowResponseType;
import org.artifactory.util.PackageTypeUtil;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.internal.process.Endpoint;
import org.glassfish.jersey.server.internal.routing.UriRoutingContext;
import org.jfrog.common.ExceptionLogUtils;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.Nullable;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

import static org.artifactory.opentracing.ArtifactoryTracer.UBER_TRACE_ID;
import static org.artifactory.shadow.ShadowRequestService.API_URL;
import static org.artifactory.shadow.ShadowRequestService.SHADOW;


/**
 * This filter is responsible for shadowing legacy API requests to the new API.
 *
 * @author Michael Akushsky
 */
@Slf4j
@Component
public class ShadowResponseFilter implements ContainerResponseFilter {

    private final long backoffLimit;
    @Autowired
    private ShadowRequestService shadowRequestService;
    private RepositoryService repositoryService;

    private final ArtifactoryExecutor singleThreadExecutor;

    private static final Set<String> SUPPORTED_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private LoadingCache<ShadowBackoffMapKey, LongAdder> blockedApisCache;

    private static final AtomicInteger probabilityCounter = new AtomicInteger(0);

    public ShadowResponseFilter() {
        backoffLimit = ConstantValues.shadowingBackoffLimit.getLong();
        singleThreadExecutor = ArtifactoryExecutorFactory.newClassicThreadPool(ThreadPoolConfig.builder()
                .corePoolSize(0)
                .maxThreads(1)
                .keepAliveTimeout(0L)
                .keepAliveTimeoutTimeUnit(TimeUnit.MILLISECONDS)
                .queue(new LinkedBlockingQueue<>())
                .threadNameFormat("art-shadow-%s")
                // We want to completely reject shadowing requests, that were rejected by the executor
                .rejectedExecutionHandler((r, executor) -> {
                    log.warn("Shadowing request was rejected");
                    throw new RejectedExecutionException("Shadowing request was rejected");
                })
                .build(), ContextHelper.get(), true);
        initCacheLoader();
    }

    void initCacheLoader() {
        blockedApisCache = CacheBuilder.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(3, TimeUnit.HOURS)
                .build(CacheLoader.from(metric -> new LongAdder()));
    }

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {

        ShadowRequestContext shadowRequestContext = createShadowRequestContext(request);
        if (shadowRequestContext == null) {
            return;
        }

        // Capture response data synchronously to avoid accessing recycled request object from async thread.
        // Tomcat recycles request/response objects after the request completes, so we must copy the data now.
        ShadowResponseContext shadowResponseContext = response != null
                ? ShadowResponseContext.of(response.getStatus(), response.getHeaders())
                : null;

        getExecutorService().submit(() -> executeShadowRequest(shadowRequestContext, shadowResponseContext));
    }

    private void executeShadowRequest(ShadowRequestContext shadowRequestContext, ShadowResponseContext response) {
        try {
            //The order is important - the property deleted when executing executeShadow.
            String repoType = String.valueOf(shadowRequestContext.repoType());
            ShadowResponseType shadowResponseType =
                    getShadowRequestService().executeShadow(shadowRequestContext, response);
            if (ShadowResponseType.SUCCEEDED != shadowResponseType) {
                String endpointName = shadowRequestContext.endpoint();
                LongAdder counter = blockedApisCache.getUnchecked(new ShadowBackoffMapKey(repoType, endpointName));
                if (ShadowResponseType.NOT_IMPLEMENTED == shadowResponseType) {
                    counter.add(backoffLimit);
                } else {
                    counter.increment();
                }
            }
        } catch (Exception e) {
            ExceptionLogUtils.debugOrWarn(log, "Failed to fetch shadow", e);
        }
    }

    @Nullable
    private ShadowRequestContext createShadowRequestContext(ContainerRequestContext request) {
        if (!SUPPORTED_METHODS.contains(request.getMethod())) {
            log.trace("Shadow filter: method {} is not supported, skipping", request.getMethod());
            return null;
        }

        String requestUrl = ((ContainerRequest) request).getRequestUri().toString();

        // Extract package type from request URL
        String packageType = PackageTypeUtil.extractPackageType(requestUrl);

        // Do shadowing only for legacy API requests
        if (SHADOW.equals(packageType) || packageType == null) {
            log.trace("Shadow filter: requestUrl {} is not a legacy API url, skipping", requestUrl);
            return null;
        }

        // Shadowing is disabled by default for all package types
        if (!isShadowEnableForPackageType(packageType)) {
            log.trace("Shadowing is disabled for package type {}", packageType);
            return null;
        }
        String requestEndpoint = getEndpointName(request);
        if (requestEndpoint == null) {
            log.trace("Endpoint is not exists.Skipping shadow");
            return null;
        }

        String repoType = getRepoTypeFromRequest(requestUrl, packageType);
        if (shouldSkipShadowForApi(repoType, requestEndpoint)) {
            log.debug("Shadowing is blocked for endpoint {} and repoType {}", requestEndpoint, repoType);
            return null;
        }
        if (!isTimeToShadow()) {
            log.trace("Skip shadowing for endpoint {} due to probability calculation", requestEndpoint);
            return null;
        }

        // Use factory method to deep-copy headers, avoiding reference to recycled request object
        return ShadowRequestContext.of(repoType, MDC.get(UBER_TRACE_ID), requestUrl, requestEndpoint,
                request.getMethod(), request.getHeaders());
    }

    private String getRepoTypeFromRequest(String requestUrl, String packageType) {
        // Normalize "terraformbackend" to "terraform" for URL parsing
        if (packageType.equals(RepoType.TerraformBackend.getType())) {
            packageType = RepoType.Terraform.getType();
        }

        RepoConfig repoConfig = null;
        try {
            repoConfig = repositoryService
                    .getRepoConfigByKey(
                            StringUtils.substringBetween(requestUrl, String.join("/", API_URL, packageType + "/"),
                                    "/"));
        } catch (RuntimeException ignore) {
            //ignore. the config service throw error in several cases, for example in cache repository
        }
        return repoConfig == null ? "null" : repoConfig.getRepoType().typeNameLowercase();
    }

    private boolean shouldSkipShadowForApi(String repoType, String endpoint) {
        LongAdder counter = blockedApisCache.getIfPresent(new ShadowBackoffMapKey(repoType, endpoint));
        return counter != null && counter.sum() >= backoffLimit;
    }

    private boolean isShadowEnableForPackageType(String packageType) {
        if (packageType == null) {
            return false;
        }
        return ArtifactoryHome.get().getArtifactoryProperties()
                .getBooleanValue("shadowing." + packageType.toLowerCase(), false);
    }

    /**
     * Shadowing probability is controlled by the following property:
     * artifactory.shadowing.probability.factor
     * The default value is 100, which means that each 100th request will be shadowed (1%).
     *
     * @return true if the request should be shadowed
     */
    boolean isTimeToShadow() {
        int rotateValue = ConstantValues.shadowingProbabilityFactor.getInt();
        return probabilityCounter.updateAndGet(i -> ++i == rotateValue ? 0 : i) == 0;
    }

    @Nullable
    private String getEndpointName(ContainerRequestContext requestContext) {
        Endpoint endpoint = ((UriRoutingContext) requestContext.getUriInfo()).getEndpoint();
        return endpoint == null ? null : endpoint.toString();
    }

    ExecutorService getExecutorService() {
        return singleThreadExecutor;
    }

    ShadowRequestService getShadowRequestService() {
        return shadowRequestService;
    }

    @Autowired
    public void setRepositoryService(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    private record ShadowBackoffMapKey(String repoType, String endpoint) {
    }
}
