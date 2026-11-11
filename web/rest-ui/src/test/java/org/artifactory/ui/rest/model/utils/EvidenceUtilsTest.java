package org.artifactory.ui.rest.model.utils;

import com.jfrog.sysconf.SysConfig;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.repo.RepositoryNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.artifactory.ui.rest.model.utils.evidence.EvidenceUtils.removeEvidenceNodes;
import static org.artifactory.util.evidence.EvidenceSystemYamlConstants.EVIDENCE_NAVIGATION_ENABLED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EvidenceUtilsTest {

    private SysConfig sysConfig;

    @BeforeEach
    public void setUp() {
        ArtifactoryHome artifactoryHome = mock(ArtifactoryHome.class);
        sysConfig = mock(SysConfig.class);
        when(sysConfig.getBoolean(EVIDENCE_NAVIGATION_ENABLED, false)).thenReturn(false);
        when(artifactoryHome.getSysConfig()).thenReturn(sysConfig);
        ArtifactoryHome.bind(artifactoryHome);
    }

    @AfterEach
    public void tearDown() {
        ArtifactoryHome.unbind();
    }

    @Test
    void testRemoveEvidenceNodesSuccess() {
        RepositoryNode node1 = mock(RepositoryNode.class);
        RepositoryNode node2 = mock(RepositoryNode.class);
        RepositoryNode node3 = mock(RepositoryNode.class);

        List<RepositoryNode> nodes = List.of(node1, node2, node3);

        when(node1.getPath()).thenReturn(".evidence/a/b/c");
        when(node2.getPath()).thenReturn(".evidence/a/b/d");
        when(node3.getPath()).thenReturn("a/b/c");

        nodes = removeEvidenceNodes(nodes);
        assertEquals(1, nodes.size());
        assertEquals("a/b/c", nodes.get(0).getPath());
    }

    @Test
    void testRemoveEvidenceNodesFlagDisabledSuccess() {
        when(sysConfig.getBoolean(EVIDENCE_NAVIGATION_ENABLED, false)).thenReturn(true);

        RepositoryNode node1 = mock(RepositoryNode.class);
        RepositoryNode node2 = mock(RepositoryNode.class);
        RepositoryNode node3 = mock(RepositoryNode.class);

        List<RepositoryNode> nodes = List.of(node1, node2, node3);

        when(node1.getPath()).thenReturn(".evidence/a/b/c");
        when(node2.getPath()).thenReturn(".evidence/a/b/d");
        when(node3.getPath()).thenReturn("a/b/c");

        nodes = removeEvidenceNodes(nodes);
        assertEquals(3, nodes.size());
    }
}