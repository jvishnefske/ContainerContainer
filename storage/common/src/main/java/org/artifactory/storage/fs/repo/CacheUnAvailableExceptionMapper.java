package org.artifactory.storage.fs.repo;

import org.artifactory.storage.CacheUnAvailableException;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * @author Inbar Tal
 */
@Provider
public class CacheUnAvailableExceptionMapper implements ExceptionMapper<CacheUnAvailableException> {

    @Override
    public Response toResponse(CacheUnAvailableException e) {
        return Response.status(Response.Status.SERVICE_UNAVAILABLE).type(MediaType.APPLICATION_JSON)
                .entity(e.getMessage()).build();
    }
}
