package org.artifactory.rest.resource.replication;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.replication.ReplicationAddon;
import org.artifactory.addon.replication.event.ReplicationEventQueueData;
import org.artifactory.api.security.SecurityService;
import org.jfrog.common.JsonUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Response;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Tomer Slutsky
 */
class ReplicationsResourceTest {

    private ReplicationsResource replicationsResource;
    private HttpServletRequest http;
    private AddonsManager addonsManager;
    private ReplicationAddon replicationAddon;
    private SecurityService securityService;

    @BeforeEach
    void setUp() {
        replicationsResource = new ReplicationsResource();
        http = mock(HttpServletRequest.class);
        addonsManager = mock(AddonsManager.class);
        replicationAddon = mock(ReplicationAddon.class);
        securityService = mock(SecurityService.class);
        ReflectionTestUtils.setField(replicationsResource, "addonsManager", addonsManager);
        ReflectionTestUtils.setField(replicationsResource, "securityService", securityService);
        replicationsResource.httpRequest = http;
        when(addonsManager.addonByType(ReplicationAddon.class)).thenReturn(replicationAddon);
    }

    @Test
    void handlePullReplicationEvents() throws IOException {
        when(securityService.isAnonymous()).thenReturn(false);
        ReplicationEventQueueData replicationEventQueueData = new ReplicationEventQueueData("key", List.of());
        when(http.getInputStream()).thenReturn(jsonInputStream(replicationEventQueueData));
        when(http.getHeader("X-Artifactory-HA-Originated-ServerId")).thenReturn("header");

        replicationsResource.handlePullReplicationEvents();

        verify(replicationAddon).handlePropagatedRemoteReplicationEvents(any(), any());
    }

    @Test
    void establishChannelRejectsAnonymous() {
        when(securityService.isAnonymous()).thenReturn(true);

        Response response = replicationsResource.establishChanel();

        assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
        verify(replicationAddon, never())
                .establishReplicationChannel(any(), any(), any(), any());
    }

    @Test
    void removeChannelRejectsAnonymous() {
        when(securityService.isAnonymous()).thenReturn(true);

        Response response = replicationsResource.removeListeners();

        assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
        verify(replicationAddon, never()).removeReplicationChannel(any());
    }

    @Test
    void propagateEventsRejectsAnonymous() {
        when(securityService.isAnonymous()).thenReturn(true);

        Response response = replicationsResource.handlePullReplicationEvents();

        assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
        verify(replicationAddon, never()).handlePropagatedRemoteReplicationEvents(any(), any());
    }

    private static ServletInputStream jsonInputStream(Object payload) {
        ByteArrayInputStream byteArrayInputStream =
                new ByteArrayInputStream(JsonUtils.getInstance().writeValueAsString(payload)
                        .getBytes(StandardCharsets.UTF_8));
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return false;
            }

            @Override
            public boolean isReady() {
                return false;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
            }

            @Override
            public int read() throws IOException {
                return byteArrayInputStream.read();
            }
        };
    }
}
