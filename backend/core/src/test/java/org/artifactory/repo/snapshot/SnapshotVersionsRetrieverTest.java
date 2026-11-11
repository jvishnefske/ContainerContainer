package org.artifactory.repo.snapshot;

import org.artifactory.repo.StoringRepo;
import org.artifactory.storage.fs.tree.FolderNode;
import org.artifactory.storage.fs.tree.ItemNode;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Amir Avidan
 */
class SnapshotVersionsRetrieverTest {

    @Test
    void testInternalCollectVersions() {
        SnapshotVersionsRetriever snapshotVersionsRetriever = new SnapshotVersionsRetriever(false);
        StoringRepo storingRepo = mock(StoringRepo.class);
        ItemNode rootNode = mock(ItemNode.class);
        ItemNode childNode1 = mock(FolderNode.class);
        List<ItemNode> children = Arrays.asList(childNode1);
        when(rootNode.isFolder()).thenReturn(true);
        when(rootNode.isUsedGetChildren()).thenReturn(false);
        when(rootNode.getCachedChildrenNodes()).thenReturn(children);
        when(childNode1.isFolder()).thenReturn(true);
        when(childNode1.isUsedGetChildren()).thenReturn(true);
        when(childNode1.getChildren()).thenReturn(List.of());
        snapshotVersionsRetriever.internalCollectVersionsItems(storingRepo, rootNode);
        verify(rootNode, times(0)).getDescendants();
        verify(rootNode, times(0)).getChildren();
        verify(rootNode, times(1)).getCachedChildrenNodes();
        verify(childNode1, times(1)).getChildren();
    }
}
