package org.artifactory.rest.compression;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.ext.Provider;
import jakarta.ws.rs.ext.WriterInterceptor;
import jakarta.ws.rs.ext.WriterInterceptorContext;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

/**
 * Intercept and gzip the output of endpoints annotated with @Compress
 *
 * @author shaharz
 */
@Provider
@Compress
public class GZIPWriterInterceptor implements WriterInterceptor {

    @Override
    public void aroundWriteTo(WriterInterceptorContext context)
            throws IOException, WebApplicationException {
        context.getHeaders().add(HttpHeaders.CONTENT_ENCODING, "gzip");
        GZIPOutputStream gzipStream = new GZIPOutputStream(context.getOutputStream());
        context.setOutputStream(gzipStream);
        context.proceed();
    }
}
