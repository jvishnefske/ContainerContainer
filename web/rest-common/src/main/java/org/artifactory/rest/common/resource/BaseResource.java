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

package org.artifactory.rest.common.resource;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.http.HttpStatus;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.concurrent.ArtifactoryCallable;
import org.artifactory.concurrent.ArtifactoryExecutor;
import org.artifactory.concurrent.ArtifactoryExecutorFactory;
import org.artifactory.concurrent.ThreadPoolConfig;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestRequestForAsyncOp;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.rest.common.service.ServiceExecutor;
import org.artifactory.security.AuthenticationHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Scope;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

/**
 * @author chen keinan
 */
@Slf4j
@SuperBuilder
@NoArgsConstructor
@Scope(SCOPE_PROTOTYPE)
public abstract class BaseResource {

    private static final long DEFAULT_TIMEOUT = 5000L;

    @Context
    protected HttpServletRequest servletRequest;
    @Context
    protected HttpServletResponse servletResponse;
    @Autowired
    protected ServiceExecutor serviceExecutor;
    protected RestResponse artifactoryResponse;

    @Context
    Request request;
    @Context
    UriInfo uriInfo;
    @Context
    HttpHeaders httpHeaders;

    @Autowired
    @Qualifier("artifactoryUiResponse")
    public void setArtifactoryResponse(RestResponse artifactoryResponse) {
        this.artifactoryResponse = artifactoryResponse;
    }

    /**
     * return ArtifactoryRestRequest instance with http servlet response property
     *
     * @param modelData - rest data type
     * @return instance of ArtifactoryRestRequest
     */
    protected <Y> ArtifactoryRestRequest<Y> getArtifactoryRestRequest(Y modelData,
            HttpServletRequest servletRequest, Request request, UriInfo uriInfo, HttpHeaders httpHeaders) {
        ArtifactoryRestRequest.RequestBuilder builder = new ArtifactoryRestRequest.
                RequestBuilder(servletRequest, request, uriInfo, httpHeaders)
                .model(modelData);
        return new ArtifactoryRestRequest(builder);
    }

    /**
     * return ArtifactoryRestRequest instance with http servlet response property
     *
     * @param modelData - rest data type
     * @return instance of ArtifactoryRestRequest
     */
    protected <Y> ArtifactoryRestRequest<Y> getArtifactoryRestRequest(Y modelData) {
        return getArtifactoryRestRequest(modelData, servletRequest, request, uriInfo, httpHeaders);
    }

    /**
     * return ArtifactoryRestRequest instance with http servlet response property
     *
     * @param modelsData - rest data type List
     * @return instance of ArtifactoryRestRequest
     */
    protected <Y> ArtifactoryRestRequest<Y> getArtifactoryRestRequest(List<Y> modelsData) {
        ArtifactoryRestRequest.RequestBuilder builder = new ArtifactoryRestRequest.
                RequestBuilder(servletRequest, request, uriInfo, httpHeaders)
                .models(modelsData);
        return new ArtifactoryRestRequest(builder);
    }

    /**
     * execute service operation (i.e.: create user , login and etc) with  model
     *
     * @param service - service type (Login Service and etc)
     * @return - rest response
     */
    protected <Y> Response runService(RestService<Y> service, Y model) {
        return runService(service, model, servletRequest, servletResponse, request, uriInfo, httpHeaders);
    }

    /**
     * execute service operation (i.e.: create user , login and etc) with  model
     *
     * @param service - service type (Login Service and etc)
     * @return - rest response
     */
    protected <Y> Response runService(RestService<Y> service, Y model, HttpServletRequest servletRequest,
            HttpServletResponse servletResponse, Request request, UriInfo uriInfo, HttpHeaders httpHeaders) {
        // get encapsulated request data
        ArtifactoryRestRequest artifactoryRequest = getArtifactoryRestRequest(model, servletRequest, request, uriInfo,
                httpHeaders);
        updateServletData(servletRequest, servletResponse);
        // process service request
        return serviceExecutor.process(artifactoryRequest, artifactoryResponse, service);
    }

    /**
     * update http servlet request and response
     */
    private void updateServletData(HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        ((ArtifactoryRestResponse) artifactoryResponse).setServletResponse(servletResponse);
        ((ArtifactoryRestResponse) artifactoryResponse).setServletRequest(servletRequest);
    }

    /**
     * update http servlet request and response
     */
    private void updateServletData() {
        updateServletData(servletRequest, servletResponse);
    }

    /**
     * execute service operation (i.e.: create user , login and etc) with  model
     *
     * @param service - service type (Login Service and etc)
     * @return rest response
     */
    protected <Y> Response runService(RestService<List<Y>> service, List<Y> model) {
        // get encapsulated request data
        ArtifactoryRestRequest artifactoryRequest = getArtifactoryRestRequest(model);
        updateServletData();
        // process service request
        return serviceExecutor.process(artifactoryRequest, artifactoryResponse, service);
    }

    /**
     * execute service operation (i.e.: create user , login and etc) without model
     *
     * @param service - service type (Login Service and etc)
     * @return - rest response
     */
    protected <Y> Response runService(RestService<Y> service) {
        // get encapsulated request data
        ArtifactoryRestRequest artifactoryRequest = getArtifactoryRestRequest();
        updateServletData();
        // process service request
        return serviceExecutor.process(artifactoryRequest, artifactoryResponse, service);
    }

    protected <Y> Response runServiceAndWait(RestService<Y> service, String timeoutMessage) {
        val artifactoryRequest = getArtifactoryRestRequestForAsyncOp();
        updateServletData();
        return executeAndWait(artifactoryRequest, artifactoryResponse, service, timeoutMessage);
    }

    private <Y> Response executeAndWait(ArtifactoryRestRequest<Y> request, RestResponse<String> response,
            RestService<?> service, String timeoutMessage) {
        val executor = createExecutorService();
        try {
            val callable = new ArtifactoryCallable<>(
                    () -> serviceExecutor.process(request, response, service),
                    ContextHelper.get(),
                    AuthenticationHelper.getAuthentication()
            );
            val future = executor.submit(callable);
            return future.get(getTimeout(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            throw new ExecutionWaitException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExecutionWaitException(e);
        } catch (TimeoutException e) {
            response.info(timeoutMessage);
            response.responseCode(HttpStatus.SC_ACCEPTED);
            return response.buildResponse();
        } finally {
            executor.shutdown();
            log.debug("UI async executor shut down");
        }
    }

    private ArtifactoryExecutor createExecutorService() {
        return ArtifactoryExecutorFactory.newClassicThreadPool(ThreadPoolConfig.builder()
                .corePoolSize(1)
                .maxThreads(1)
                .keepAliveTimeout(0L)
                .keepAliveTimeoutTimeUnit(TimeUnit.SECONDS)
                .queue(new LinkedBlockingQueue<>())
                .threadNameFormat("ui-async-executor")
                .build(), ContextHelper.get(), false);
    }

    /**
     * return ArtifactoryRestRequest instance with http servlet response property
     *
     * @return instance of ArtifactoryRestRequest
     */
    protected <Y> ArtifactoryRestRequest<Y> getArtifactoryRestRequest() {
        ArtifactoryRestRequest.RequestBuilder builder = new ArtifactoryRestRequest.
                RequestBuilder(servletRequest, request, uriInfo, httpHeaders);
        return new ArtifactoryRestRequest(builder);
    }

    /**
     * return ArtifactoryRestRequestForAsyncOp instance for running service execution in async mode
     *
     * @return instance of ArtifactoryRestRequestForAsyncOp
     */
    protected <T> ArtifactoryRestRequestForAsyncOp<T> getArtifactoryRestRequestForAsyncOp() {
        ArtifactoryRestRequest.RequestBuilder<T> builder = new ArtifactoryRestRequest.
                RequestBuilder<>(servletRequest, request, uriInfo, httpHeaders);
        return new ArtifactoryRestRequestForAsyncOp<>(builder);
    }

    protected long getTimeout() {
        return DEFAULT_TIMEOUT;
    }
}
