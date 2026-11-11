package org.artifactory.ui.rest.service.utils.repositories.repodata;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class RepositoriesDataHandlerFactoryTest {

    @Mock
    private GetRepositoriesDataHandler<Object> handler1;

    @Mock
    private GetRepositoriesDataHandler<Object> handler2;

    @InjectMocks
    private RepositoriesDataHandlerFactory<Object> factory;

    private AutoCloseable closeable;

    @BeforeEach
    public void setUp() {
        closeable = MockitoAnnotations.openMocks(this);

        when(handler1.getHandledQueryParam()).thenReturn("query1");
        when(handler2.getHandledQueryParam()).thenReturn("query2");

        List<GetRepositoriesDataHandler<Object>> handlers = Arrays.asList(handler1, handler2);
        factory = new RepositoriesDataHandlerFactory<>(handlers);
    }

    @AfterEach
    public void teardown() throws Exception {
        closeable.close();
    }

    @Test
    void testGetHandlerWithValidQueryParam() {
        GetRepositoriesDataHandler<Object> handler = factory.getHandler("query1");
        assertNotNull(handler);
        assertEquals(handler1, handler);
    }

    @Test
    void testGetHandlerWithInvalidQueryParam() {
        GetRepositoriesDataHandler<Object> handler = factory.getHandler("invalidQuery");
        assertNull(handler);
    }

    @Test
    void testGetHandlerWithNullQueryParam() {
        GetRepositoriesDataHandler<Object> handler = factory.getHandler(null);
        assertNull(handler);
    }

    @Test
    void testGetHandledQueryParams() {
        Set<String> handledQueryParams = factory.getHandledQueryParams();
        assertEquals(2, handledQueryParams.size());
        assertTrue(handledQueryParams.contains("query1"));
        assertTrue(handledQueryParams.contains("query2"));
    }

    @Test
    void testEmptyFactory() {
        RepositoriesDataHandlerFactory<Object> emptyFactory =
                new RepositoriesDataHandlerFactory<>(Collections.emptyList());

        assertTrue(emptyFactory.getHandledQueryParams().isEmpty());
        assertNull(emptyFactory.getHandler("anyQueryParam"));
    }
}