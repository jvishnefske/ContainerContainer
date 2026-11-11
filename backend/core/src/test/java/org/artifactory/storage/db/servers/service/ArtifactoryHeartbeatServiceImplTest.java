package org.artifactory.storage.db.servers.service;

import org.artifactory.addon.ArtifactoryRunningMode;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.spring.ArtifactoryApplicationContext;
import org.artifactory.state.ArtifactoryServerState;
import org.artifactory.state.model.ArtifactoryStateManager;
import org.artifactory.storage.db.servers.model.ArtifactoryServer;
import org.artifactory.storage.db.servers.model.ArtifactoryServerRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * @author Andrey Tashlitsky
 */
class ArtifactoryHeartbeatServiceImplTest {

    private final ArtifactoryHeartbeatServiceImpl.HeartbeatJob heartbeatJob =
            new ArtifactoryHeartbeatServiceImpl.HeartbeatJob();
    @Mock
    private ArtifactoryApplicationContext artifactoryContext;
    @Mock
    private ArtifactoryStateManager artifactoryStateManager;
    private ArtifactoryServer server;
    private List<ArtifactoryServer> servers;
    private AutoCloseable mocks;

    @BeforeEach
    void beforeEach() {
        mocks = MockitoAnnotations.openMocks(this);
        when(artifactoryContext.beanForType(ArtifactoryStateManager.class)).thenReturn(artifactoryStateManager);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
    }

    @AfterEach
    void afterEach() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @Test
    void testRecreateServerEntryIfNeededWithoutRecreate() {
        initSomeServers();
        when(artifactoryContext.getServerId()).thenReturn("cool1");
        ArtifactoryServer artifactoryServer = heartbeatJob.recreateServerEntryIfNeeded(servers, artifactoryContext);
        assertSame(server, artifactoryServer);
        verify(artifactoryContext, times(1)).getServerId();
        verifyNoMoreInteractions(artifactoryContext, artifactoryStateManager);
    }

    @Test
    void testRecreateServerEntryIfNeededWitRecreate() {
        initSomeServers();
        ArtifactoryServer artifactoryServer = new ArtifactoryServer("new-cool", 1_000_000,
                "123.4.5.7", 5701, ArtifactoryServerState.RUNNING, ArtifactoryServerRole.MEMBER,
                System.currentTimeMillis(), "7", 7, 7,
                ArtifactoryRunningMode.HA, "COOL");
        when(artifactoryContext.getServerId()).thenReturn(artifactoryServer.getServerId());
        when(artifactoryStateManager.createOrUpdateArtifactoryServerState(ArtifactoryServerState.RUNNING))
                .thenReturn(artifactoryServer);
        ArtifactoryServer recreatedServer = heartbeatJob.recreateServerEntryIfNeeded(servers, artifactoryContext);
        assertSame(artifactoryServer, recreatedServer);
        verify(artifactoryContext, times(1)).getServerId();
        verify(artifactoryContext, times(1)).beanForType(ArtifactoryStateManager.class);
        verify(artifactoryStateManager, times(1)).createOrUpdateArtifactoryServerState(ArtifactoryServerState.RUNNING);
        verifyNoMoreInteractions(artifactoryContext, artifactoryStateManager);
    }

    private void initSomeServers() {
        server = new ArtifactoryServer("cool1", 1_000_000,
                "123.4.5.6", 5701, ArtifactoryServerState.RUNNING, ArtifactoryServerRole.MEMBER,
                System.currentTimeMillis(), "7", 7, 7,
                ArtifactoryRunningMode.HA, "COOL1");
        servers = List.of(server);
    }
}
