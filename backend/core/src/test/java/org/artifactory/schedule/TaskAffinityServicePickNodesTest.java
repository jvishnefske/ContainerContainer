/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.schedule;

import org.artifactory.addon.ArtifactoryRunningMode;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.state.ArtifactoryServerState;
import org.artifactory.storage.db.servers.model.ArtifactoryServer;
import org.artifactory.storage.db.servers.model.ArtifactoryServerRole;
import org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService;
import org.artifactory.test.ArtifactoryHomeStub;
import org.jfrog.common.StreamSupportUtils;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * @author Omri Ziv
 */
@Test
public class TaskAffinityServicePickNodesTest {

    @Mock
    private ArtifactoryServersCommonService serversService;
    private TaskAffinityService tester;

    private final String art1 = "art-01";
    private final String art2 = "art-02";
    private AutoCloseable mocks;

    @BeforeMethod
    public void setupMethod() {
        mocks = MockitoAnnotations.openMocks(this);
        ArtifactoryHome.bind(new ArtifactoryHomeStub(getClass().getCanonicalName()));
        tester = new TaskAffinityServiceImpl(serversService);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        ArtifactoryHome.unbind();
        mocks.close();
    }

    public void testPickAffinityMember() {
        List<ArtifactoryServer> servers = new LinkedList<>();
        ArtifactoryServer s1 = new ArtifactoryServer(art1, 100L, "123.4.5.6", 5701, ArtifactoryServerState.RUNNING,
                ArtifactoryServerRole.TASK_AFFINITY, System.currentTimeMillis(), "6.1", 2, 3L,
                ArtifactoryRunningMode.HA, "somehash");
        servers.add(s1);

        mockAlive(servers);

        Optional<ArtifactoryServer> artifactoryServer = tester
                .pickAffinityMemberByNodeNames(Collections.singletonList(art1));
        assertTrue(artifactoryServer.isPresent());
        Assert.assertEquals(art1, artifactoryServer.get().getServerId());

    }

    public void testPickAffinityMemberNoAffinity() {
        List<ArtifactoryServer> servers = new LinkedList<>();
        ArtifactoryServer s1 = new ArtifactoryServer(art1, 100L, "123.4.5.6", 5701, ArtifactoryServerState.RUNNING,
                ArtifactoryServerRole.TASK_AFFINITY, System.currentTimeMillis(), "6.1", 2, 3L,
                ArtifactoryRunningMode.HA, "somehash");
        ArtifactoryServer s2 = new ArtifactoryServer(art2, 100L, "123.4.5.6", 5701, ArtifactoryServerState.RUNNING,
                ArtifactoryServerRole.TASK_AFFINITY, System.currentTimeMillis(), "6.1", 2, 3L,
                ArtifactoryRunningMode.HA, "somehash");
        servers.add(s1);
        servers.add(s2);

        mockAlive(servers);
        Optional<ArtifactoryServer> artifactoryServer = tester.pickAffinityMemberByNodeNames(Collections.emptyList());
        assertFalse(artifactoryServer.isPresent());
    }

    public void testPickRandomAffinityMember() {
        List<ArtifactoryServer> servers = new LinkedList<>();
        ArtifactoryServer s1 = new ArtifactoryServer(art1, 100L, "123.4.5.6", 5701, ArtifactoryServerState.RUNNING,
                ArtifactoryServerRole.TASK_AFFINITY, System.currentTimeMillis(), "6.1", 2, 3L,
                ArtifactoryRunningMode.HA, "somehash");
        ArtifactoryServer s2 = new ArtifactoryServer(art2, 100L, "123.4.5.6", 5701, ArtifactoryServerState.RUNNING,
                ArtifactoryServerRole.TASK_AFFINITY, System.currentTimeMillis(), "6.1", 2, 3L,
                ArtifactoryRunningMode.HA, "somehash");
        servers.add(s1);
        servers.add(s2);

        mockAlive(servers);
        boolean[] picked = new boolean[2];
        int iteration = 0;
        while (!allSet(picked) || iteration > 1000) {
            Optional<ArtifactoryServer> artifactoryServer =
                    tester.pickAffinityMemberByNodeNames(Arrays.asList(art1, art2));
            assertTrue(artifactoryServer.isPresent());
            int index = servers.indexOf(artifactoryServer.get());
            picked[index] = true;
            iteration++;
        }
        assertTrue(allSet(picked));
    }

    public void testPickNonRunningAffinityMember() {
        List<ArtifactoryServer> servers = new LinkedList<>();
        ArtifactoryServer s1 = new ArtifactoryServer(art1, 100L, "123.4.5.6", 5701, ArtifactoryServerState.CONVERTING,
                ArtifactoryServerRole.TASK_AFFINITY, System.currentTimeMillis(), "6.1", 2, 3L,
                ArtifactoryRunningMode.HA, "somehash");
        ArtifactoryServer s2 = new ArtifactoryServer(art2, 100L, "123.4.5.6", 5701, ArtifactoryServerState.OFFLINE,
                ArtifactoryServerRole.TASK_AFFINITY, System.currentTimeMillis(), "6.1", 2, 3L,
                ArtifactoryRunningMode.HA, "somehash");
        servers.add(s1);
        servers.add(s2);
        mockAlive(servers);
        Optional<ArtifactoryServer> artifactoryServer = tester.pickAffinityMemberByNodeNames(Arrays.asList(art1, art2));
        assertFalse(artifactoryServer.isPresent());
    }

    private void mockAlive(List<ArtifactoryServer> servers) {
        doAnswer(i -> {
            List<String> nodeNames = i.getArgument(0);
            return StreamSupportUtils.stream(servers)
                    .filter(server -> server.getServerState() == ArtifactoryServerState.RUNNING &&
                            nodeNames.contains(server.getServerId()))
                    .collect(Collectors.toList());
        }).when(serversService).filterAliveMembersByNodeNames(anyList());
    }

    private boolean allSet(boolean[] arr) {
        for (boolean index : arr) {
            if (!index) {
                return false;
            }
        }
        return true;
    }

}