package org.artifactory.repo.snapshot;

import org.artifactory.fs.ItemInfo;
import org.artifactory.repo.StoringRepo;
import org.artifactory.storage.fs.tree.FolderNode;
import org.artifactory.storage.fs.tree.ItemNode;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Amir Avidan
 */
class ReleaseVersionsRetrieverTest {

    @Test
    void testInternalCollectVersions() {
        ReleaseVersionsRetriever releaseVersionsRetriever = new ReleaseVersionsRetriever(false);
        StoringRepo storingRepo = mock(StoringRepo.class);
        ItemNode rootNode = mock(ItemNode.class);
        ItemNode childNode1 = mock(FolderNode.class);
        ItemNode childNode2 = mock(ItemNode.class);
        List<ItemNode> children = Arrays.asList(childNode1, childNode2);
        when(rootNode.isFolder()).thenReturn(true);
        when(rootNode.isUsedGetChildren()).thenReturn(false);
        when(rootNode.getCachedChildrenNodes()).thenReturn(children);
        when(childNode1.isFolder()).thenReturn(true);
        when(childNode1.isUsedGetChildren()).thenReturn(true);
        when(childNode1.getChildren()).thenReturn(List.of());
        when(childNode2.isFolder()).thenReturn(false);
        ItemInfo itemInfo1 = mock(ItemInfo.class);
        ItemInfo itemInfo2 = mock(ItemInfo.class);
        when(childNode2.getItemInfo()).thenReturn(itemInfo2);
        Calendar cal1 = Calendar.getInstance();
        Calendar cal2 = Calendar.getInstance();
        when(itemInfo1.getCreated()).thenReturn(cal1.getTimeInMillis());
        when(itemInfo2.getCreated()).thenReturn(cal2.getTimeInMillis());
        releaseVersionsRetriever.internalCollectVersionsItems(storingRepo, rootNode);
        verify(rootNode, times(0)).getDescendants();
        verify(rootNode, times(0)).getChildren();
        verify(rootNode, times(1)).getCachedChildrenNodes();
        verify(childNode1, times(1)).getChildren();
    }
}