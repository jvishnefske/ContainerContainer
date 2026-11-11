package org.artifactory.repo.cleanup;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.repo.exception.ItemNotFoundRuntimeException;
import org.artifactory.config.CentralConfigKey;
import org.artifactory.cron.CronUtils;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.schedule.Task;
import org.artifactory.schedule.TaskService;
import org.artifactory.schedule.TaskUtils;
import org.artifactory.spring.Reloadable;
import org.artifactory.storage.db.fs.dao.NodesDao;
import org.artifactory.storage.db.fs.entity.Node;
import org.artifactory.storage.fs.service.ConfigsService;
import org.artifactory.system.load.event.SystemLoadState;
import org.artifactory.system.load.monitor.SystemLoadProvider;
import org.jfrog.common.config.diff.DataDiff;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.List;

import org.artifactory.common.ConstantValues;

import static org.artifactory.storage.fs.service.ConfigsServiceKey.ARTIFACTORY_EMPTY_FOLDER_SCAN_START_NODE_ID;

/**
 * Service for systematic empty folder scanning.
 * Proactively discovers and queues orphaned empty folders for pruning.
 *
 * @author annakv
 */
@Slf4j
@Service
@Reloadable(beanClass = EmptyFolderScanService.class,
        initAfter = {TaskService.class, InternalRepositoryService.class},
        listenOn = CentralConfigKey.none)
public class EmptyFolderScanServiceImpl implements EmptyFolderScanService {

    private final NodesDao nodesDao;
    private final FolderPruningService folderPruningService;
    private final SystemLoadProvider systemLoadProvider;
    private final TaskService taskService;
    private final ConfigsService configsService;
    private final MeterRegistry meterRegistry;
    private int maxQueueSize;
    private int batchSize;
    private int limit;

    public EmptyFolderScanServiceImpl(@Autowired NodesDao nodesDao,
                                 @Autowired FolderPruningService folderPruningService,
                                 @Autowired SystemLoadProvider systemLoadProvider,
                                 @Autowired TaskService taskService,
                                 @Autowired ConfigsService configsService,
                                 @Autowired(required = false) MeterRegistry meterRegistry) {
        this.nodesDao = nodesDao;
        this.folderPruningService = folderPruningService;
        this.systemLoadProvider = systemLoadProvider;
        this.taskService = taskService;
        this.configsService = configsService;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void init() {
        maxQueueSize = ConstantValues.folderPruningMaxQueueSize.getInt();
        batchSize = ConstantValues.emptyFolderScanBatchSize.getInt();
        limit = ConstantValues.emptyFolderScanBatchLimit.getInt();
        if (ConstantValues.emptyFolderScanEnabled.getBoolean()) {
            String quartzExpression = EmptyFolderScanJob.buildRandomQuartzExp();
            if (!StringUtils.isBlank(quartzExpression) && CronUtils.isValid(quartzExpression)) {
                Task emptyFolderScanTask = TaskUtils.createCronTask(EmptyFolderScanJob.class,
                        quartzExpression, "Empty Folder Scan Job");
                taskService.startTask(emptyFolderScanTask, false);
                log.info("Scheduling EmptyFolderScanJob to run at '{}'", quartzExpression);
            } else {
                log.info("Cron expression {} is not valid. EmptyFolderScanJob not scheduled to run",
                        quartzExpression);
            }
        } else {
            log.debug("Empty folder scanning is disabled, not registering job");
        }
    }

    @Override
    public void destroy() {
        taskService.cancelTasks(EmptyFolderScanJob.class, true);
    }

    @Override
    public void reload(CentralConfigDescriptor oldDescriptor, List<DataDiff<?>> configDiff) {
        //noop
    }

    @Override
    public void scanEmptyFolders() {
        if (!ConstantValues.emptyFolderScanEnabled.getBoolean()) {
            log.debug("Empty folder scanning is disabled");
            return;
        }
        SystemLoadState systemLoadState = systemLoadProvider.getSystemLoadState();
        if (systemLoadState != SystemLoadState.HEALTHY) {
            log.debug("Skipping empty folder scan - system is not healthy. Current state: {}", systemLoadState);
            return;
        }
        if (isAtMaxCapacity()) {
            recordMaxCapacityMetric();
            return;
        }
        try {
            long currentStartNodeId = loadLastCheckedNodeId();
            long maxNodeId = nodesDao.findLastNodeId();
            long minNodeId = 1;
            log.debug("Starting empty folder scan from node_id: {}, max node_id: {}, batch size: {}",
                    currentStartNodeId, maxNodeId, batchSize);
            List<Node> folderNodes = nodesDao.getTrashFoldersByNodeIdRange(currentStartNodeId, batchSize, limit);
            if (folderNodes.isEmpty()) {
                // No folders found in current interval, advance by interval size
                long nextStartNodeId = currentStartNodeId + batchSize;
                if (nextStartNodeId > maxNodeId) {
                    resetScan(maxNodeId, minNodeId);
                } else {
                    saveNextNodeId(nextStartNodeId);
                    log.debug("No folders found in current range, advancing scan to node_id: {}", nextStartNodeId);
                }
                return;
            }
            processFolders(folderNodes, currentStartNodeId, maxNodeId, minNodeId, batchSize, limit);
        } catch (SQLException e) {
            log.error("Database error during empty folder scan", e);
        } catch (Exception e) {
            log.error("Unexpected error during empty folder scan", e);
        }
    }

    private void processFolders(List<Node> folderNodes, long currentStartNodeId, long maxNodeId, long minNodeId,
            int batchSize, int limit) {
        int processedCount = 0;
        long lastNodeId = currentStartNodeId;
        for (Node folderNode : folderNodes) {
            try {
                RepoPath folderRepoPath = folderNode.getNodePath().toRepoPath();
                folderPruningService.prune(folderRepoPath);
                processedCount++;
            } catch (ItemNotFoundRuntimeException e) {
                log.trace("Folder not found during scan (likely deleted concurrently): {}", folderNode, e);
            } catch (Exception e) {
                log.warn("Error processing folder during empty folder scan: {}", folderNode, e);
            }
            lastNodeId = folderNode.getNodeId();
        }
        if (folderNodes.size() < limit) {
            // in the batch there were less folders than the limit,
            // so the last node id would be last node of the batch
            lastNodeId = currentStartNodeId + batchSize - 1;
        }
        if (lastNodeId >= maxNodeId) {
            resetScan(maxNodeId, minNodeId);
        } else {
            saveNextNodeId(lastNodeId + 1);
        }
        log.debug("Empty folder scan completed. Processed: {}, Last node_id: {}, Max node_id: {}", processedCount,
                lastNodeId, maxNodeId);
        recordProcessedCountMetric(processedCount);
    }

    private void resetScan(long maxNodeId, long minNodeId) {
        log.debug("Reached max node_id ({}), resetting scan to start from beginning", maxNodeId);
        saveNextNodeId(minNodeId);
    }

    private boolean isAtMaxCapacity() {
        // Check queue size before adding work
        int currentQueueSize = folderPruningService.getQueueSize();
        if (currentQueueSize >= maxQueueSize) {
            log.debug("Skipping empty folder scan - pruning queue is at capacity. Current size: {}, Max size: {}",
                    currentQueueSize, maxQueueSize);
            return true;
        }
        return false;
    }

    private long loadLastCheckedNodeId() throws SQLException {
        String persistedNodeId = configsService.getConfig(ARTIFACTORY_EMPTY_FOLDER_SCAN_START_NODE_ID);
        if (StringUtils.isNotBlank(persistedNodeId)) {
            long nodeId = Long.parseLong(persistedNodeId);
            log.debug("Loaded next node id from database: {}", nodeId);
            return nodeId;
        } else {
            long nodeId = 1;
            log.debug("No persisted node id found in database, starting from min {}", nodeId);
            return nodeId;
        }
    }

    private void saveNextNodeId(long nodeId) {
        configsService.addOrUpdateConfig(ARTIFACTORY_EMPTY_FOLDER_SCAN_START_NODE_ID,
                String.valueOf(nodeId), System.currentTimeMillis());
        log.trace("Saved next node id to database: {}", nodeId);
    }

    private void recordProcessedCountMetric(int processedCount) {
        if (meterRegistry != null && processedCount > 0) {
            Counter.builder("empty.folder.scan.processed.count")
                    .description("The number of folders processed during empty folder scan")
                    .register(meterRegistry)
                    .increment(processedCount);
        }
    }

    private void recordMaxCapacityMetric() {
        if (meterRegistry != null) {
            Counter.builder("empty.folder.scan.max.capacity.skipped")
                    .description("The number of times empty folder scan was skipped due to pruning queue being " +
                            "at max capacity")
                    .register(meterRegistry)
                    .increment();
        }
    }

}

