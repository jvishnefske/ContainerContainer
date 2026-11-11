package org.artifactory.repo.service.flexible;

import org.artifactory.fs.ItemInfo;
import org.artifactory.fs.MutableItemInfo;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.storage.fs.MutableVfsItem;
import org.testng.annotations.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

/**
 * @author Yevdo Abramov
 * Created on 19/10/2022
 */
public class BulkProviderBulkTest {

    @Test
    void testGetMoveCopyItemsIsSorted() {
        BulkProvider.Bulk bulk = new BulkProvider.Bulk();

        addToTheBulk(bulk, "_intransit:rb-Rvx0/ver-0/example-repo-local/my_dir", "example-repo-local/my_dir");
        addToTheBulk(bulk, "_intransit:rb-Rvx0/ver-0/example-repo-local/my_dir/rb-Rvx0/ver-0",
                "example-repo-local/my_dir/rb-Rvx0/ver-0");
        addToTheBulk(bulk, "_intransit:rb-Rvx0/ver-0/example-repo-local/my_dir/rb-Rvx0",
                "example-repo-local/my_dir/rb-Rvx0");
        addToTheBulk(bulk, "_intransit:rb-Rvx0/ver-0/example-repo-local/my_dir/rb-Rvx0/ver-0/rb-Rvx0_ver-0_0.bin",
                "example-repo-local/my_dir/rb-Rvx0/ver-0/rb-Rvx0_ver-0_0.bin");
        addToTheBulk(bulk, "_intransit:rb-Rvx0/ver-0/example-repo-local", "example-repo-local");

        List<MoveCopyItems> result = bulk.getMoveCopyItems();
        List<String> expectedResult = List.of(
                "example-repo-local/",
                "example-repo-local/my_dir",
                "example-repo-local/my_dir/rb-Rvx0",
                "example-repo-local/my_dir/rb-Rvx0/ver-0",
                "example-repo-local/my_dir/rb-Rvx0/ver-0/rb-Rvx0_ver-0_0.bin");

        assertEquals(result.size(), 5);
        for (int i = 0; i < result.size(); i++) {
            assertEquals(result.get(i).getTargetItem().getRepoPath().toPath(), expectedResult.get(i));
        }
    }

    private void addToTheBulk(BulkProvider.Bulk bulk, String source, String target) {
        VfsItem<ItemInfo> sourceItem = createMockVfsItem(source);
        MutableVfsItem<MutableItemInfo> targetItem = createMockMutableVfsItem(target);

        MoveCopyItems moveCopyItems = new MoveCopyItems(sourceItem, null, targetItem);
        bulk.put(RepoPathFactory.create(source), moveCopyItems);
    }

    private VfsItem<ItemInfo> createMockVfsItem(String repoPath) {
        VfsItem<ItemInfo> item = mock(VfsItem.class);
        when(item.getRepoPath()).thenReturn(RepoPathFactory.create(repoPath));
        return item;
    }

    private MutableVfsItem<MutableItemInfo> createMockMutableVfsItem(String repoPath) {
        MutableVfsItem<MutableItemInfo> item = mock(MutableVfsItem.class);
        when(item.getRepoPath()).thenReturn(RepoPathFactory.create(repoPath));
        return item;
    }
}