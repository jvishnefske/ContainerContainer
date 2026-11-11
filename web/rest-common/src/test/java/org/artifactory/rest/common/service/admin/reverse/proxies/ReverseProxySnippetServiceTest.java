package org.artifactory.rest.common.service.admin.reverse.proxies;

import org.artifactory.aol.AolService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.security.SecurityService;
import org.artifactory.rest.exception.AuthorizationRestException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReverseProxySnippetServiceTest {
    @InjectMocks
    ReverseProxySnippetService reverseProxySnippetService;
    @Mock
    SecurityService securityService;
    @Mock
    ArtifactoryContext context;
    @Mock
    AolService aolService;

    AutoCloseable closeable;

    @BeforeEach
    void setup() {
        closeable = MockitoAnnotations.openMocks(this);
        when(context.beanForType(AolService.class)).thenReturn(aolService);
        ArtifactoryContextThreadBinder.bind(context);
    }

    @AfterEach
    void cleanup() throws Exception {
        closeable.close();
        ArtifactoryContextThreadBinder.unbind();
        verify(aolService, times(1)).isAol();
    }

    @Test
    void testExecuteAllowForAdmin() {
        Assertions.assertThrows(AuthorizationRestException.class,
                () -> reverseProxySnippetService.execute(null, null));
        verify(securityService, times(1)).isAdmin();
    }
}