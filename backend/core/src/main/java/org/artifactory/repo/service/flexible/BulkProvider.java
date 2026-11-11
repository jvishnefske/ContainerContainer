package org.artifactory.repo.service.flexible;

import com.google.common.collect.Maps;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.utils.Lists;
import org.artifactory.api.repo.exception.FatalCopyMoveException;
import org.artifactory.aql.AqlService;
import org.artifactory.aql.api.domain.sensitive.AqlApiItem;
import org.artifactory.aql.model.AqlItemTypeEnum;
import org.artifactory.aql.model.AqlPhysicalFieldEnum;
import org.artifactory.aql.result.AqlLazyResult;
import org.artifactory.aql.result.rows.AqlItem;
import org.artifactory.aql.result.rows.AqlLazyObjectResultStreamer;
import org.artifactory.aql.result.rows.ItemInfoRow;
import org.artifactory.checksum.ChecksumInfo;
import org.artifactory.checksum.ChecksumType;
import org.artifactory.fs.FileInfo;
import org.artifactory.fs.ItemInfo;
import org.artifactory.fs.MutableItemInfo;
import org.artifactory.md.Properties;
import org.artifactory.md.PropertiesInfo;
import org.artifactory.properties.PropertiesImpl;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.StoringRepo;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.repo.service.flexible.context.MoveCopyContext;
import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.storage.db.DbService;
import org.artifactory.storage.db.fs.entity.NodePath;
import org.artifactory.storage.fs.MutableVfsItem;
import org.jfrog.client.util.PathUtils;

import jakarta.annotation.Nullable;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;

/**
 * BulkProvider provides Bulk's of CopyMoveItem's for the copy/move operation implemented in FlatMoveCopyService.
 * It uses In Memory Vfs items and FixPropertiesVfsItem's to avoid extra calls to DB for achieving artifact data
 * including its properties.
 */

@Slf4j
public class BulkProvider implements AutoCloseable {

    private final AqlLazyResult<AqlItem> sourceListResults;
    private final PushPopStreamer sourceStreamer;
    private final int transactionSize;
    private final InternalRepositoryService repositoryService;
    private final MoveCopyContext context;

    public BulkProvider(@NonNull AqlService aqlService, @NonNull AqlApiItem sourceItemsQuery,
            int transactionSize,
            @NonNull InternalRepositoryService repositoryService,
            @NonNull MoveCopyContext context) {
        this.transactionSize = transactionSize;
        this.repositoryService = repositoryService;
        this.context = context;
        this.sourceListResults = aqlService.executeQueryLazy(sourceItemsQuery);
        this.sourceStreamer =
                new PushPopStreamer(new AqlLazyObjectResultStreamer<>(sourceListResults, ItemInfoRow.class));

    }

    @Override
    public void close() throws Exception {
        sourceListResults.close();
    }

    public boolean hasMore() {
        ItemInfoRow row = sourceStreamer.peek();
        return row != null;
    }

    private boolean sameElement(@NonNull ItemInfoRow row, @Nullable ItemInfoRow prev) {
        if (prev == null) {
            return true;
        }
        return row.getRepoPath().equals(prev.getRepoPath());
    }

    private Bulk nextBulk() {
        Bulk newBulk = new Bulk();
        VfsItem<ItemInfo> sourceItem;
        while (newBulk.size() < transactionSize && (sourceItem = loadItem(sourceStreamer)) != null) {
            if (sourceItem.isFolder() && sourceItem.getPath().equals(".")) {
                continue; //not including the root of repo in the bulk
            }
            MoveCopyItems moveCopyItems = new MoveCopyItems(sourceItem, null, null);
            newBulk.put(moveCopyItems.getSourceItem().getRepoPath(), moveCopyItems);
        }
        fillTargetItemInBulk(newBulk);
        return newBulk;
    }

    private void fillTargetItemInBulk(Bulk newBulk) {
        List<MoveCopyItems> moveCopyItems = newBulk.getMoveCopyItems();
        List<RepoPath> targets = moveCopyItems.stream().map(this::toTarget).toList();
        long start = System.currentTimeMillis();
        Map<String, RepoPath> existing = repositoryService.getExistingItems(targets).stream()
                .collect(Collectors.toMap(key -> PathUtils.trimSlashes(key.toPath()), a -> a));
        log.debug("got {} existing artifacts from getExistingItems {} ms", existing.keySet().size(),
                System.currentTimeMillis() - start);
        for (MoveCopyItems moveCopyItem : moveCopyItems) {
            VfsItem<ItemInfo> sourceItem = moveCopyItem.getSourceItem();
            RepoPath repoPath = sourceItem.getRepoPath();
            RepoPath targetRepoPath = getTargetPath(context, repoPath);
            RepoPath originalTargetPath = existing.get(PathUtils.trimSlashes(targetRepoPath.toPath()));
            VfsItem<ItemInfo> originalItem = null;
            if (originalTargetPath != null) {
                LocalRepo<?, ?> localRepository = repositoryService.getLocalRepository(originalTargetPath);
                //noinspection unchecked
                originalItem = localRepository.getImmutableFsItem(originalTargetPath);
            }
            if (sourceItem.isFolder() && targetRepoPath.isFile()) {
                throw new FatalCopyMoveException(String.format("Cannot copy/move folder %s to file %s",
                        sourceItem.getPath(), targetRepoPath.getPath()));
            }
            moveCopyItem.setTargetItem(getTargetItem(sourceItem, originalItem, targetRepoPath));
            moveCopyItem.setOriginalTargetItem(originalItem);
        }
    }

    private RepoPath toTarget(MoveCopyItems a) {
        return getTargetPath(context, a.getSourceItem().getRepoPath());
    }

    private VfsItem<ItemInfo> loadItem(PushPopStreamer streamer) {
        ItemInfoRow firstRow = streamer.getRow();
        if (firstRow == null) {
            return null;
        }

        VfsItem<ItemInfo> item = createVfsItem(firstRow);
        if (firstRow.getPropKey() != null || firstRow.getPropVal() != null) {
            item.getProperties().put(firstRow.getPropKey(), firstRow.getPropVal());
        }
        ItemInfoRow row;
        //adding to the current item its properties from each row in the streamer
        //assumption 1: each row can contain only one property
        //assumption 2: rows are in-order in the streamer
        while ((row = streamer.getRow()) != null) {
            if (sameElement(firstRow, row)) {
                if (row.getPropKey() != null || row.getPropVal() != null) {
                    item.getProperties().put(row.getPropKey(), row.getPropVal());
                }
            } else {
                streamer.push(row);
                return item;
            }
        }
        return item;
    }

    protected static RepoPath getTargetPath(MoveCopyContext context, RepoPath sourceItemRepoPath) {
        return MoveCopyTargetPathResolver.resolveTargetPath(context, sourceItemRepoPath);
    }

    protected MutableVfsItem<MutableItemInfo> getTargetItem(VfsItem<?> currentSourceItem,
            VfsItem<ItemInfo> originalTarget,
            RepoPath targetRepoPath) {
        VfsItem<ItemInfo> targetItem = createVfsItem(getItemInfoRow(currentSourceItem, originalTarget, targetRepoPath));
        Properties properties = mergeProperties(currentSourceItem.getProperties(), currentSourceItem.getRepoPath());
        targetItem.getProperties().putAll(properties);
        StoringRepo<?, ?> targetStoringRepo = repositoryService.storingRepositoryByKey(targetRepoPath.getRepoKey());
        Properties originalProperties = originalTarget != null ? originalTarget.getProperties() : null;
        ItemInfo originalItemInfo = originalTarget != null ? originalTarget.getInfo() : targetItem.getInfo();
        return targetStoringRepo.getMutableFsItem(targetItem, originalItemInfo, originalProperties);
    }


    protected ItemInfoRow getItemInfoRow(VfsItem<?> currentSourceItem,
            VfsItem<ItemInfo> originalTarget,
            RepoPath targetRepoPath) {
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

    private VfsItem<ItemInfo> createVfsItem(ItemInfoRow row) {
        StoringRepo<?, ?> storingRepo = repositoryService.storingRepositoryByKey(row.getRepoPath().getRepoKey());
        VfsItem<ItemInfo> immutableFsItem = storingRepo.getImmutableFsItem(row.toItemInfo());
        if (row.toItemInfo().isFolder()) {
            VfsItem<?> folder = new FixPropertiesVfsFolder(immutableFsItem, new PropertiesImpl());
            //noinspection unchecked
            return (VfsItem<ItemInfo>) folder;
        } else {
            VfsItem<?> file = new FixPropertiesVfsFile(immutableFsItem, new PropertiesImpl());
            //noinspection unchecked
            return (VfsItem<ItemInfo>) file;
        }
    }

    public List<MoveCopyItems> getBulk() {
        return nextBulk().getMoveCopyItems();
    }

    private Properties mergeProperties(Properties properties, RepoPath repoPath) {
        PropertiesImpl newProperties = new PropertiesImpl();
        if (context.isSkipProperties()) {
            return newProperties;
        }
        properties.keySet().forEach(key -> newProperties.putAll(key, properties.get(key)));
        removeFromMergedProperties(repoPath, newProperties);
        addToMergedProperties(repoPath, newProperties);
        return newProperties;
    }

    private void removeFromMergedProperties(RepoPath repoPath, PropertiesImpl newProperties) {
        List<String> keysToRemove = context.getRemoveProps().apply(repoPath);
        ofNullable(keysToRemove).ifPresent(toRemove -> toRemove.forEach(newProperties::removeAll));
    }

    private void addToMergedProperties(RepoPath repoPath, PropertiesImpl newProperties) {
        Properties keysToAdd = context.getAddProps().apply(repoPath);
        ofNullable(keysToAdd).map(PropertiesInfo::keySet).ifPresent(toAdd ->
                toAdd.forEach(key -> newProperties.putAll(key, keysToAdd.get(key))));
    }

    /**
     * Bulk of MoveCopyItem's
     */
    static class Bulk {
        private final Map<RepoPath, MoveCopyItems> repoPathToMoveCopyItems = Maps.newHashMap();

        public List<MoveCopyItems> getMoveCopyItems() {
            return repoPathToMoveCopyItems.values().stream()
                    .sorted(Comparator.comparing(item -> item.getSourceItem().getRepoPath().toPath()))
                    .toList();
        }

        public void put(RepoPath repoPath, MoveCopyItems moveCopyItems) {
            repoPathToMoveCopyItems.put(repoPath, moveCopyItems);
        }

        public int size() {
            return repoPathToMoveCopyItems.size();
        }

        public MoveCopyItems get(RepoPath repoPath) {
            return repoPathToMoveCopyItems.get(repoPath);
        }
    }

    /**
     * Push Pop Streamer of ItemInfoRow's
     */
    private static class PushPopStreamer {
        private final AqlLazyObjectResultStreamer<ItemInfoRow> itemInfoRowAqlLazyObjectResultStreamer;
        private final List<ItemInfoRow> list = Lists.newArrayList();

        public PushPopStreamer(AqlLazyObjectResultStreamer<ItemInfoRow> itemInfoRowAqlLazyObjectResultStreamer) {
            this.itemInfoRowAqlLazyObjectResultStreamer = itemInfoRowAqlLazyObjectResultStreamer;
        }

        public ItemInfoRow getRow() {
            if (list.isEmpty()) {
                return itemInfoRowAqlLazyObjectResultStreamer.getRow();
            } else {
                return list.remove(list.size() - 1);
            }
        }

        public void push(ItemInfoRow row) {
            list.add(0, row);
        }

        public ItemInfoRow peek() {
            ItemInfoRow itemInfoRow = getRow();
            push(itemInfoRow);
            return itemInfoRow;
        }
    }
}
