package org.artifactory.request.gzip;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.Provider;
import jakarta.ws.rs.ext.ReaderInterceptor;
import jakarta.ws.rs.ext.ReaderInterceptorContext;
import java.io.IOException;
import java.util.List;
import java.util.zip.GZIPInputStream;

@Provider
public class GZIPReaderInterceptor implements ReaderInterceptor {

    @Override
    public Object aroundReadFrom(ReaderInterceptorContext context) throws IOException, WebApplicationException {
        MultivaluedMap<String, String> headers = context.getHeaders();
        if (headers == null) {
            return context.proceed();
        }
        String encodingKey = headers.keySet().stream()
                .filter(k -> k.equalsIgnoreCase(jakarta.ws.rs.core.HttpHeaders.CONTENT_ENCODING))
                .findFirst().orElse(null);
        if (encodingKey == null) {
            return context.proceed();
        }
        List<String> encoding = headers.get(encodingKey);
        if (encoding != null && encoding.size() == 1 && "gzip".equalsIgnoreCase(encoding.get(0))) {
            headers.remove(encodingKey);
            context.setInputStream(new GZIPInputStream(context.getInputStream()));
        }
        return context.proceed();
    }
}
