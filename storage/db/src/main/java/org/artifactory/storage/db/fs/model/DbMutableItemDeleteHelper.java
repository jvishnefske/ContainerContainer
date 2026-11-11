package org.artifactory.storage.db.fs.model;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.MoreExecutors;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.ConstantValues;
import org.artifactory.storage.fs.VfsException;
import org.artifactory.storage.fs.service.FileService;
import org.artifactory.storage.fs.service.NodeMetaInfoService;
import org.artifactory.storage.fs.service.PropertiesService;
import org.artifactory.storage.fs.service.StatsService;
import org.artifactory.storage.fs.service.WatchesService;
import org.jfrog.common.ExecutionUtils;
import org.jfrog.common.RetryException;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.concurrent.ExecutionException;

/**
 * @author Eli Skoran 02/03/2021
 */

@Slf4j
@Component
public class DbMutableItemDeleteHelper {

    /**
     * Deletes an item by the given {@code id}
     *
     * @param id the id of the node to be deleted
     * @throws VfsException if a SQL exception occurs during deletion
     */
    public void deleteItem(long id) {
        try {
            getWatchesService().deleteWatches(id);
            getPropertiesService().deleteProperties(id);
            getNodeMetaInfoService().deleteMetaInfo(id);
            ExecutionUtils.retry(
                    () -> {
                        try (var lockNode = lockNodeForWrite(id)) {
                            getStatsService().deleteStats(id);
                            getFileService().deleteItem(id);
                        } catch (Exception e) {
                            throw new RetryException("Failed to delete item with id '" + id + "': " + e.getMessage(),
                                    e);
                        }
                        return null;
                    },
                    ExecutionUtils.RetryOptions.builder()
                            .numberOfRetries(3)
                            .failedLog("Delete item has failed due to: ${ERROR}. Not trying again")
                            .build(),
                    MoreExecutors.newDirectExecutorService()).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new VfsException("Delete operation interrupted for item id " + id, e);
        } catch (ExecutionException e) {
            log.error("Failed to delete item with id '{}'", id, e);
            throw new VfsException(e.getMessage());
        }
    }

    private AutoCloseable lockNodeForWrite(long id) throws SQLException {
        if (ConstantValues.lockForSelectBeforeDeletingFromNodesEnabled.getBoolean()) {
            // Lock the node for write to prevent foreign key constraint violations
            return getFileService().lockNodeForWrite(id);
        }
        return () -> {};
    }

    @VisibleForTesting
    FileService getFileService() {
        return ContextHelper.get().beanForType(FileService.class);
    }

    @VisibleForTesting
    PropertiesService getPropertiesService() {
        return ContextHelper.get().beanForType(PropertiesService.class);
    }

    @VisibleForTesting
    WatchesService getWatchesService() {
        return ContextHelper.get().beanForType(WatchesService.class);
    }

    @VisibleForTesting
    StatsService getStatsService() {
        return ContextHelper.get().beanForType("statsServiceImpl", StatsService.class);
    }

    @VisibleForTesting
    NodeMetaInfoService getNodeMetaInfoService() {
        return ContextHelper.get().beanForType(NodeMetaInfoService.class);
    }
}
