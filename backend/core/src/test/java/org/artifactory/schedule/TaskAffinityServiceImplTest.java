package org.artifactory.schedule;

import org.artifactory.addon.ArtifactoryRunningMode;
import org.artifactory.state.ArtifactoryServerState;
import org.artifactory.storage.db.servers.model.ArtifactoryServer;
import org.artifactory.storage.db.servers.model.ArtifactoryServerRole;
import org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * @author Omri Ziv
 */
public class TaskAffinityServiceImplTest {

    @Mock
    private ArtifactoryServersCommonService artifactoryServersCommonService;

    private AutoCloseable autoCloseable;

    @BeforeMethod
    public void setUp() {
        autoCloseable = MockitoAnnotations.openMocks(this);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        autoCloseable.close();
    }

    @Test
    public void testAffinityAnyWithJobName() {
        TaskAffinityService taskAffinityService = new TaskAffinityServiceImpl(artifactoryServersCommonService);
        ArtifactoryServer server1 =
                new ArtifactoryServer("art1", System.currentTimeMillis(), "", 333, ArtifactoryServerState.RUNNING,
                        ArtifactoryServerRole.TASK_AFFINITY,
                        System.currentTimeMillis(), "7.6.0", 1234, 1234, ArtifactoryRunningMode.HA, "aaaa");

        ArtifactoryServer server2 =
                new ArtifactoryServer("art2", System.currentTimeMillis(), "", 333, ArtifactoryServerState.RUNNING,
                        ArtifactoryServerRole.MEMBER,
                        System.currentTimeMillis(), "7.6.0", 1234, 1234, ArtifactoryRunningMode.HA, "aaaa");

        when(artifactoryServersCommonService.getAliveAffinityNodes()).thenReturn(List.of(server1, server2));

        List<String> nodes = taskAffinityService.getAffinityForTask(JobName.CALL_HOME);
        assertEquals(nodes.size(), 1);
        assertEquals(nodes.get(0), "art1");
    }


    @Test
    public void testAffinityAnyWithJobNameMultipleNodes() {
        TaskAffinityService taskAffinityService = new TaskAffinityServiceImpl(artifactoryServersCommonService);
        ArtifactoryServer server1 =
                new ArtifactoryServer("art1", System.currentTimeMillis(), "", 333, ArtifactoryServerState.RUNNING,
                        ArtifactoryServerRole.TASK_AFFINITY,
                        System.currentTimeMillis(), "7.6.0", 1234, 1234, ArtifactoryRunningMode.HA, "aaaa");

        ArtifactoryServer server2 =
                new ArtifactoryServer("art2", System.currentTimeMillis(), "", 333, ArtifactoryServerState.RUNNING,
                        ArtifactoryServerRole.TASK_AFFINITY,
                        System.currentTimeMillis(), "7.6.0", 1234, 1234, ArtifactoryRunningMode.HA, "aaaa");

        ArtifactoryServer server3 =
                new ArtifactoryServer("art3", System.currentTimeMillis(), "", 333, ArtifactoryServerState.RUNNING,
                        ArtifactoryServerRole.TASK_AFFINITY,
                        System.currentTimeMillis(), "7.6.0", 1234, 1234, ArtifactoryRunningMode.HA, "aaaa");

        when(artifactoryServersCommonService.getAliveAffinityNodes()).thenReturn(List.of(server1, server2, server3));

        List<String> nodes = taskAffinityService.getAffinityForTask(JobName.CALL_HOME);
        assertEquals(nodes.size(), 3);
        assertTrue(nodes.containsAll(List.of(server1.getServerId(), server2.getServerId(), server3.getServerId())));
    }

    @Test
    public void testAffinityAnyAndNoneWithJobNameMultipleNodes() {
        TaskAffinityService taskAffinityService = new TaskAffinityServiceImpl(artifactoryServersCommonService);
        ArtifactoryServer server1 =
                new ArtifactoryServer("art1", System.currentTimeMillis(), "", 333, ArtifactoryServerState.RUNNING,
                        ArtifactoryServerRole.TASK_AFFINITY,
                        System.currentTimeMillis(), "7.6.0", 1234, 1234, ArtifactoryRunningMode.HA, "aaaa");

        ArtifactoryServer server2 =
                new ArtifactoryServer("art2", System.currentTimeMillis(), "", 333, ArtifactoryServerState.RUNNING,
                        ArtifactoryServerRole.TASK_AFFINITY,
                        System.currentTimeMillis(), "7.6.0", 1234, 1234, ArtifactoryRunningMode.HA, "aaaa");

        ArtifactoryServer server3 =
                new ArtifactoryServer("art3", System.currentTimeMillis(), "", 333, ArtifactoryServerState.RUNNING,
                        ArtifactoryServerRole.MEMBER,
                        System.currentTimeMillis(), "7.6.0", 1234, 1234, ArtifactoryRunningMode.HA, "aaaa");

        ArtifactoryServer server4 =
                new ArtifactoryServer("art4", System.currentTimeMillis(), "", 333, ArtifactoryServerState.RUNNING,
                        ArtifactoryServerRole.MEMBER,
                        System.currentTimeMillis(), "7.6.0", 1234, 1234, ArtifactoryRunningMode.HA, "aaaa");

        when(artifactoryServersCommonService.getAliveAffinityNodes())
                .thenReturn(List.of(server1, server2, server3, server4));

        List<String> nodes = taskAffinityService.getAffinityForTask(JobName.CALL_HOME);
        assertEquals(nodes.size(), 2);
        assertTrue(nodes.containsAll(List.of(server1.getServerId(), server2.getServerId())));
    }

    @Test
    public void testAffinityNoneWithJobNameMultipleNodes() {
        TaskAffinityService taskAffinityService = new TaskAffinityServiceImpl(artifactoryServersCommonService);
        ArtifactoryServer server1 =
                new ArtifactoryServer("art1", System.currentTimeMillis(), "", 333, ArtifactoryServerState.RUNNING,
                        ArtifactoryServerRole.MEMBER,
                        System.currentTimeMillis(), "7.6.0", 1234, 1234, ArtifactoryRunningMode.HA, "aaaa");

        ArtifactoryServer server2 =
                new ArtifactoryServer("art2", System.currentTimeMillis(), "", 333, ArtifactoryServerState.RUNNING,
                        ArtifactoryServerRole.MEMBER,
                        System.currentTimeMillis(), "7.6.0", 1234, 1234, ArtifactoryRunningMode.HA, "aaaa");

        when(artifactoryServersCommonService.getAliveAffinityNodes()).thenReturn(List.of(server1, server2));

        List<String> nodes = taskAffinityService.getAffinityForTask(JobName.CALL_HOME);
        assertEquals(nodes.size(), 0);
    }
}
