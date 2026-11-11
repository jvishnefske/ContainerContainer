package org.artifactory.rest.filter;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.ser.FilterProvider;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.fasterxml.jackson.jakarta.rs.cfg.EndpointConfigBase;
import com.fasterxml.jackson.jakarta.rs.cfg.ObjectWriterInjector;
import com.fasterxml.jackson.jakarta.rs.cfg.ObjectWriterModifier;
import org.artifactory.api.jackson.util.TypeSpecificFilter;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;

import static org.artifactory.api.jackson.util.TypeSpecificFilter.TYPE_SPECIFIC_FILTER;

@Provider
public class JsonMappingExceptionOnCollectionResponseFilter implements ContainerResponseFilter {
    @Override
    public void filter(ContainerRequestContext requestCtx, ContainerResponseContext responseCtx) throws IOException {
        ObjectWriterInjector.set(new ObjectWriterModifier() {

            @Override
            public ObjectWriter modify(EndpointConfigBase<?> endpoint, MultivaluedMap<String, Object> responseHeaders,
                    Object valueToWrite, ObjectWriter w, JsonGenerator g) {
                FilterProvider filterProvider =
                        new SimpleFilterProvider().addFilter(TYPE_SPECIFIC_FILTER, new TypeSpecificFilter());
                return w.with(filterProvider);
            }
        });
    }
}
