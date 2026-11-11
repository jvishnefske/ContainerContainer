package org.artifactory.rest.resource.admin;

import jakarta.ws.rs.core.Response;
import org.artifactory.aol.AolService;
import org.artifactory.exception.http.ForbiddenException;
import org.artifactory.repo.interceptor.modle.RemoteRepoOfflineTriggersConfig;
import org.artifactory.service.RemoteRepoOfflineGuardService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static jakarta.ws.rs.core.Response.Status.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Hussein Alshqerat
 */
@ExtendWith(MockitoExtension.class)
class RemoteRepoOfflineTriggersResourceTest {

    @Mock
    private AolService aolService;
    @Mock
    private RemoteRepoOfflineGuardService offlineGuardService;

    private RemoteRepoOfflineTriggersResource resource;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        resource = new RemoteRepoOfflineTriggersResource(aolService, offlineGuardService);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    private static RemoteRepoOfflineTriggersConfig triggers() {
        RemoteRepoOfflineTriggersConfig config = new RemoteRepoOfflineTriggersConfig();
        config.setMode(RemoteRepoOfflineTriggersConfig.Mode.PFW3_ALL);
        config.setUrlSubstrings(Set.of("bad-host"));
        config.setHeaderNames(Set.of("X-Bad"));
        return config;
    }

    @Test
    void get_returnsTriggersForDashboardUser() {
        when(aolService.isDashboardUser()).thenReturn(true);
        RemoteRepoOfflineTriggersConfig stored = triggers();
        when(offlineGuardService.getRemoteRepoOfflineGuardTriggersConfig()).thenReturn(stored);

        Response response = resource.get();

        assertEquals(OK.getStatusCode(), response.getStatus());
        assertSame(stored, response.getEntity());
    }

    @Test
    void get_rejectsNonDashboardUser() {
        when(aolService.isDashboardUser()).thenReturn(false);

        assertThrows(ForbiddenException.class, () -> resource.get());

        verify(offlineGuardService, never()).getRemoteRepoOfflineGuardTriggersConfig();
    }

    @Test
    void update_persistsAndReturnsForDashboardUser() {
        when(aolService.isDashboardUser()).thenReturn(true);
        RemoteRepoOfflineTriggersConfig submitted = triggers();
        RemoteRepoOfflineTriggersConfig stored = triggers();
        when(offlineGuardService.getRemoteRepoOfflineGuardTriggersConfig()).thenReturn(stored);

        Response response = resource.update(submitted);

        verify(offlineGuardService).updateTriggers(submitted);
        assertEquals(OK.getStatusCode(), response.getStatus());
        assertSame(stored, response.getEntity());
    }

    @Test
    void update_rejectsNonDashboardUser() {
        when(aolService.isDashboardUser()).thenReturn(false);
        RemoteRepoOfflineTriggersConfig submitted = triggers();

        assertThrows(ForbiddenException.class, () -> resource.update(submitted));

        verify(offlineGuardService, never()).updateTriggers(submitted);
    }
}