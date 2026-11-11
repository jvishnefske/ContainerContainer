package org.artifactory.request;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.jfrog.common.JsonParsingException;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.ext.Provider;
import jakarta.ws.rs.ext.ReaderInterceptor;
import jakarta.ws.rs.ext.ReaderInterceptorContext;
import java.io.IOException;

/**
 * @author Oleksandr Legenkiy
 */
@Provider
public class JsonExceptionInterceptor implements ReaderInterceptor {

    @Override
    public Object aroundReadFrom(ReaderInterceptorContext context) throws IOException, WebApplicationException {
        try {
            return context.proceed();
        } catch (JsonProcessingException e) {
            throw new JsonParsingException("An error has occurred while attempting to parse the sent JSON", e);
        }
    }
}
