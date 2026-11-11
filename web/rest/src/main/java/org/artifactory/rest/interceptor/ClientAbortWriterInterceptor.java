package org.artifactory.rest.interceptor;

import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.artifactory.response.ResponseThreadLocal;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.ext.Provider;
import jakarta.ws.rs.ext.WriterInterceptor;
import jakarta.ws.rs.ext.WriterInterceptorContext;
import java.io.IOException;

/**
 * @author Andrey Tashlitsky
 */
@Slf4j
@Provider
public class ClientAbortWriterInterceptor implements WriterInterceptor {

    @Override
    public void aroundWriteTo(WriterInterceptorContext context) throws IOException, WebApplicationException {
        try {
            context.proceed();
        } catch (ClientAbortException e) {
            log.warn("The request was aborted by the client", e);
            ResponseThreadLocal.setAbortedByClient(true);
        }
    }
}
