package org.artifactory.repo.service.flexible;

import lombok.NonNull;
import org.artifactory.aql.AqlService;
import org.artifactory.aql.api.domain.sensitive.AqlApiItem;
import org.artifactory.aql.model.AqlItemTypeEnum;
import org.artifactory.aql.model.AqlPhysicalFieldEnum;
import org.artifactory.aql.result.AqlLazyResult;
import org.artifactory.aql.result.rows.AqlItem;
import org.artifactory.aql.result.rows.ItemInfoRow;
import org.artifactory.checksum.ChecksumInfo;
import org.artifactory.checksum.ChecksumType;
import org.artifactory.checksum.ChecksumsInfo;
import org.artifactory.fs.FileInfo;
import org.artifactory.fs.FolderInfo;
import org.artifactory.fs.ItemInfo;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.properties.PropertiesImpl;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.StoringRepo;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.repo.service.flexible.context.MoveCopyContext;
import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.storage.db.DbService;
import org.artifactory.storage.db.fs.entity.NodePath;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import jakarta.annotation.Nullable;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertTrue;

public class BulkProviderTest {

    @Mock
    private VfsItem<?> currentSourceItem;
    @Mock
    private VfsItem<ItemInfo> originalTarget;
    @Mock
    private RepoPath targetRepoPath;
    @Mock
    private InternalRepositoryService repositoryService;
    @Mock
    private StoringRepo<?, ?> targetStoringRepo;
    @Mock
    private FolderInfo folderInfo;
    @Mock
    private FileInfo fileInfo;
    @Mock
    private AqlService aqlService;
    @Mock
    private AqlApiItem sourceItemsQuery;
    @Mock
    private MoveCopyContext context;
    @Mock
    private AqlLazyResult<AqlItem> lazyResult;

    private BulkProvider bulkProvider;

    private static final String VALID_MD5 = "2f222ca7499ed5bc49fe25a1182c59f7";
    private static final String VALID_SHA1 = "911ca40cdb527969ee47dc6f782425d94a36b510";
    private static final String VALID_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    private AutoCloseable mocks;

    @BeforeMethod
    public void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        when(aqlService.executeQueryLazy(sourceItemsQuery)).thenReturn(lazyResult);
        bulkProvider = new BulkProvider(aqlService, sourceItemsQuery, 1, repositoryService, context);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        mocks.close();
    }

    @DataProvider(name = "targetItemType")
    public Object[][] getType() {
        return new Object[][] { {AqlItemTypeEnum.folder}, {AqlItemTypeEnum.file} };
    }

    @Test(dataProvider = "targetItemType")
    public void testGetTargetItem(AqlItemTypeEnum type) {
        prepareMocksForItem(type);
        ItemInfoRow expectedItemInfoRow = getItemInfoRow(currentSourceItem, originalTarget, targetRepoPath);
        ItemInfoRow actualItemInfoRow = bulkProvider.getItemInfoRow(currentSourceItem, originalTarget, targetRepoPath);
        assertTrue(expectedItemInfoRow.equalsToOther(actualItemInfoRow),
                "ItemInfoRow has different values than expected! expected: " + expectedItemInfoRow +
                        ", actual: " + actualItemInfoRow);
        verifyCalls(AqlItemTypeEnum.file == type ? fileInfo : folderInfo);
    }

    private void verifyCalls(ItemInfo itemInfo) {
        verify(itemInfo, atLeast(1)).getCreated();
        verify(itemInfo, atLeast(1)).getCreatedBy();
        verify(itemInfo, atLeast(1)).getModifiedBy();
        verify(itemInfo, atLeast(1)).getLastUpdated();
        verify(itemInfo, atLeast(1)).getLastModified();
        verify(itemInfo, atLeast(1)).isFolder();
        if (itemInfo instanceof FileInfo) {
            FileInfo fileInfo = (FileInfo) itemInfo;
            verify(fileInfo, atLeast(1)).getSize();
            verify(fileInfo, atLeast(1)).getChecksumsInfo();
        }
    }

    private void prepareMocksForItem(AqlItemTypeEnum type) {
        when(repositoryService.storingRepositoryByKey(any())).thenReturn(targetStoringRepo);
        when(context.getRemoveProps()).thenReturn(a -> null);
        when(context.getAddProps()).thenReturn(a -> null);
        prepareTargetRepoPath(type);
        prepareOriginalTargetItem();
        if (type.equals(AqlItemTypeEnum.file)) {
            prepareFileInfo();
        } else {
            prepareFolderInfo();
        }
        prepareCurrentSourceItem(type);
    }

    private void prepareOriginalTargetItem() {
        ItemInfo itemInfo = Mockito.mock(ItemInfo.class);
        when(originalTarget.getInfo()).thenReturn(itemInfo);
        when(originalTarget.getId()).thenReturn(11L);
    }

    private void prepareTargetRepoPath(AqlItemTypeEnum type) {
        String itemName = type.equals(AqlItemTypeEnum.file) ? "file.txt" : "folder/";
        String repoKey = "targetRepo";
        String path = "target/path/";
        when(targetRepoPath.toPath()).thenReturn(repoKey + ":" + path + itemName);
        when(targetRepoPath.getPath()).thenReturn(path + itemName);
        when(targetRepoPath.getRepoKey()).thenReturn(repoKey);
        when(targetRepoPath.getName()).thenReturn(itemName);
    }

    private void prepareCurrentSourceItem(AqlItemTypeEnum type) {
        long time = System.currentTimeMillis();
        when(currentSourceItem.getId()).thenReturn(12L);
        when(currentSourceItem.getCreated()).thenReturn(time);
        if (type.equals(AqlItemTypeEnum.file)) {
            when(currentSourceItem.getInfo()).thenReturn(fileInfo);
        } else {
            when(currentSourceItem.getInfo()).thenReturn(folderInfo);
        }
        when(currentSourceItem.getProperties()).thenReturn(new PropertiesImpl());
    }

    private void prepareFolderInfo() {
        long time = System.currentTimeMillis();
        when(folderInfo.getCreated()).thenReturn(time);
        when(folderInfo.getCreatedBy()).thenReturn("createdBy");
        when(folderInfo.getModifiedBy()).thenReturn("modifiedBy");
        when(folderInfo.getId()).thenReturn(11L);
        when(folderInfo.getName()).thenReturn("folder");
        when(folderInfo.getLastUpdated()).thenReturn(time + 1);
        when(folderInfo.getLastModified()).thenReturn(time + 2);
        when(folderInfo.getRelPath()).thenReturn("relative/path");
        when(folderInfo.getRepoKey()).thenReturn("repoKey");
        when(folderInfo.isFolder()).thenReturn(true);
        when(folderInfo.getRepoPath()).thenReturn(new RepoPathImpl("repoKey", "repo/path/folder"));
    }

    private void prepareFileInfo() {
        long time = System.currentTimeMillis();
        ChecksumsInfo checksumsInfo = new ChecksumsInfo();
        checksumsInfo.addChecksumInfo(new ChecksumInfo(ChecksumType.md5, "md5_orig", VALID_MD5));
        checksumsInfo.addChecksumInfo(new ChecksumInfo(ChecksumType.sha1, "sha1_orig", VALID_SHA1));
        checksumsInfo.addChecksumInfo(new ChecksumInfo(ChecksumType.sha256, "sha256_orig", VALID_SHA256));
        when(fileInfo.getSize()).thenReturn(10L);
        when(fileInfo.getCreated()).thenReturn(time);
        when(fileInfo.getCreatedBy()).thenReturn("createdBy");
        when(fileInfo.getModifiedBy()).thenReturn("modifiedBy");
        when(fileInfo.getLastUpdated()).thenReturn(time + 1);
        when(fileInfo.getLastModified()).thenReturn(time + 2);
        when(fileInfo.getChecksumsInfo()).thenReturn(checksumsInfo);
    }

    @NonNull
    private ItemInfoRow getItemInfoRow(@NonNull VfsItem<?> currentSourceItem,
            @Nullable VfsItem<ItemInfo> originalTarget,
            @NonNull RepoPath targetRepoPath) {
        ItemInfoRow row = new ItemInfoRow();
        if (originalTarget == null) { //target file doesn't exist
            row.put(AqlPhysicalFieldEnum.itemId, (long) DbService.NO_DB_ID);
        } else {
            row.put(AqlPhysicalFieldEnum.itemId, originalTarget.getId());
        }
        row.put(AqlPhysicalFieldEnum.itemRepo, targetRepoPath.getRepoKey());
        if (targetRepoPath.toPath().equals(".")
                || targetRepoPath.toPath().endsWith("/.")
                || targetRepoPath.getName().equals(targetRepoPath.getPath())) {
            row.put(AqlPhysicalFieldEnum.itemPath, "");
        } else if (!targetRepoPath.getPath().contains("/")) {
            row.put(AqlPhysicalFieldEnum.itemPath, targetRepoPath.getPath());
        } else {
            row.put(AqlPhysicalFieldEnum.itemPath, targetRepoPath.getPath()
                    .substring(0, targetRepoPath.getPath().lastIndexOf('/')));
        }
        row.put(AqlPhysicalFieldEnum.itemName, targetRepoPath.getName().equals(".") ? "" : targetRepoPath.getName());
        row.put(AqlPhysicalFieldEnum.itemCreated, currentSourceItem.getInfo().getCreated());
        row.put(AqlPhysicalFieldEnum.itemCreatedBy, currentSourceItem.getInfo().getCreatedBy());
        row.put(AqlPhysicalFieldEnum.itemModified, currentSourceItem.getInfo().getLastModified());
        row.put(AqlPhysicalFieldEnum.itemModifiedBy, currentSourceItem.getInfo().getModifiedBy());
        row.put(AqlPhysicalFieldEnum.itemUpdated, currentSourceItem.getInfo().getLastUpdated());
        AqlItemTypeEnum type = currentSourceItem.getInfo().isFolder() ? AqlItemTypeEnum.folder : AqlItemTypeEnum.file;
        row.put(AqlPhysicalFieldEnum.itemType, type);
        row.put(AqlPhysicalFieldEnum.itemDepth, NodePath.fromRepoPath(targetRepoPath).getDepth());

        if (AqlItemTypeEnum.file == type) {
            FileInfo fileInfo = (FileInfo) currentSourceItem.getInfo();
            row.put(AqlPhysicalFieldEnum.itemSize, fileInfo.getSize());
            ChecksumInfo md5 = fileInfo.getChecksumsInfo().getChecksumInfo(ChecksumType.md5);
            ChecksumInfo sha1 = fileInfo.getChecksumsInfo().getChecksumInfo(ChecksumType.sha1);
            ChecksumInfo sha256 = fileInfo.getChecksumsInfo().getChecksumInfo(ChecksumType.sha256);
            row.put(AqlPhysicalFieldEnum.itemOriginalMd5, md5.getOriginal());
            row.put(AqlPhysicalFieldEnum.itemActualMd5, md5.getActual());
            row.put(AqlPhysicalFieldEnum.itemOriginalSha1, sha1.getOriginal());
            row.put(AqlPhysicalFieldEnum.itemActualSha1, sha1.getActual());
            row.put(AqlPhysicalFieldEnum.itemSha2, sha256.getActual());
        }
        return row;
    }
}