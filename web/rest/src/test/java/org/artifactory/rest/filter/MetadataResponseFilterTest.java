package org.artifactory.rest.filter;

import org.apache.http.HttpStatus;
import org.artifactory.common.ConstantValues;
import org.jfrog.common.config.metadata.MetadataResponse;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.stream.Stream;

import static org.artifactory.api.rest.constant.RepositoriesRestConstants.CACHE_CONTROL_NO_STORE;
import static org.artifactory.api.rest.constant.RepositoriesRestConstants.MAX_AGE;

class MetadataResponseFilterTest extends ArtifactoryHomeBoundJUnitTest{
    @ParameterizedTest
    @MethodSource("filterArgs")
    void testFilterCacheControlHeaderAnnotation (String httpMethod,
            int status,
            Annotation[] entityAnnotation,
            boolean headerShouldAppear,
            int maxAge)
            throws IOException {
        getBound().setProperty(ConstantValues.metadataCacheControlHeaderSeconds, String.valueOf(maxAge));

        ContainerRequestContext requestContext = Mockito.mock(ContainerRequestContext.class);
        ContainerResponseContext responseContext = Mockito.mock(ContainerResponseContext.class);
        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        MetadataResponseFilter metadataResponseFilter = new MetadataResponseFilter();

        Mockito.when(responseContext.getHeaders()).thenReturn(headers);
        Mockito.when(requestContext.getMethod()).thenReturn(httpMethod);
        Mockito.when(responseContext.getEntityAnnotations()).thenReturn(entityAnnotation);
        Mockito.when(responseContext.getStatus()).thenReturn(status);

        metadataResponseFilter.filter(requestContext, responseContext);
        if (headerShouldAppear) {
            Assertions.assertNotNull(headers.get(HttpHeaders.CACHE_CONTROL));
            Assertions.assertFalse(headers.get(HttpHeaders.CACHE_CONTROL).isEmpty());
            if (maxAge == 0) {
                Assertions.assertEquals(CACHE_CONTROL_NO_STORE, headers.get(HttpHeaders.CACHE_CONTROL).get(0));
            } else {
                Assertions.assertEquals(String.format("%s=%d", MAX_AGE, maxAge),
                        headers.get(HttpHeaders.CACHE_CONTROL).get(0));
            }
        } else {
            Assertions.assertNull(headers.get(HttpHeaders.CACHE_CONTROL));
            Assertions.assertTrue(headers.isEmpty());
        }
    }

    private static Stream<Arguments> filterArgs() {
        MetadataResponse metadataResponse = new MetadataResponse() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return MetadataResponse.class;
            }
        };
        //httpMethod, statusCode, entityAnnotation, headerShouldAppear, maxAge
        return Stream.of(
                Arguments.of(HttpMethod.GET, HttpStatus.SC_OK, new Annotation[] {metadataResponse}, true, 0),
                Arguments.of(HttpMethod.GET, HttpStatus.SC_BAD_REQUEST, new Annotation[] {metadataResponse}, false, 0),
                Arguments.of(HttpMethod.GET, HttpStatus.SC_OK, new Annotation[] {}, false, 0),
                Arguments.of(HttpMethod.POST, HttpStatus.SC_OK, new Annotation[] {}, false, 0),
                Arguments.of(HttpMethod.GET, HttpStatus.SC_OK, new Annotation[] {metadataResponse}, true, 1),
                Arguments.of(HttpMethod.GET, HttpStatus.SC_OK, new Annotation[] {metadataResponse}, false, -1));
    }
}