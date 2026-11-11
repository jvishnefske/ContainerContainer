package org.artifactory.rest.common.service;

import jakarta.ws.rs.core.MultivaluedMap;

import java.util.List;

/**
 * Class dedicated to running resources' service executions in async mode. its purpose is to capture request data
 * relevant for the service execution, which would later not be available in an external thread - out of the Http
 * Context scope.
 *
 * @author Dvir Twina
 */
public class ArtifactoryRestRequestForAsyncOp<T> extends ArtifactoryRestRequest<T> {
    private final MultivaluedMap<String, String> pathParameters;
    private final MultivaluedMap<String, String> queryParameters;

    public ArtifactoryRestRequestForAsyncOp(RequestBuilder<T> requestBuilder) {
        super(requestBuilder);
        pathParameters = requestBuilder.uriInfo.getPathParameters();
        queryParameters = requestBuilder.uriInfo.getQueryParameters();
    }

    /**
     * return path param by key
     *
     * @param key - path param key
     * @return path param value
     */
    @Override
    public String getPathParamByKey(String key) {
        return getPathParamByKeyInternal(pathParameters.get(key));
    }

    /**
     * return query param by key
     *
     * @param key - query param key
     * @return query param value
     */
    @Override
    public String getQueryParamByKey(String key) {
        return getQueryParamByKeyInternal(queryParameters.get(key));
    }

    @Override
    public List<String> getQueryParamListByKey(String key) {
        return getQueryParamListByKeyInternal(queryParameters.get(key));
    }
}
