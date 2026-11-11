package org.artifactory.event.migration;

import jakarta.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.HaAddon;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.concurrent.ArtifactoryExecutorFactory;
import org.artifactory.concurrent.ThreadPoolConfig;
import org.artifactory.storage.db.event.dao.eventTableRepoKeyMigration.EventTableRepoKeyMigrationDao;
import org.jfrog.common.ExecutionUtils;
import org.jfrog.common.RetryException;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@Component
public class EventTableRepoKeyMigrationImpl implements EventTableRepoKeyMigration, DisposableBean {
    private EventTableRepoKeyMigrationDao eventTableRepoKeyMigrationDao;
    private final ArtifactoryHome artifactoryHome;
    private final AddonsManager addonsManager;
    private static final Logger log = LoggerFactory.getLogger(EventTableRepoKeyMigrationImpl.class);
    private ExecutorService retryMonitorMigrationThreadPool;
    public static final String MIGRATION_THREAD_NAME = "retry-monitor-migration-job";

    public EventTableRepoKeyMigrationImpl(AddonsManager addonsManager) {
        this.artifactoryHome = ArtifactoryHome.get();
        this.addonsManager = addonsManager;
        getOrCreateExecutor();
    }

    @Autowired
    public void setEventTableRepoKeyMigrationDaoImpl(EventTableRepoKeyMigrationDao eventTableRepoKeyMigrationDao) {
        this.eventTableRepoKeyMigrationDao = eventTableRepoKeyMigrationDao;
    }

    private HaAddon haAddon() {
        return addonsManager.addonByType(HaAddon.class);
    }

    @Override
    public void eventTableRepoKeyMigrationRetryManager() throws JobExecutionException {
        log.debug("Getting eventTableRepoKeyMigrationRetryManager");
        ExecutionUtils.RetryOptions retryOptions = getRetryOptions();
        try {
            ExecutionUtils.retry(this::eventTableRepoKeyMigrationHandle, retryOptions,
                    getOrCreateExecutor()).get();
        } catch (Exception e) {
            throw new JobExecutionException(e);
        } finally {
            log.debug("Getting eventTableRepoKeyMigrationRetryManager has been finished");
        }
    }

    private ExecutionUtils.RetryOptions getRetryOptions() {
        return ExecutionUtils.RetryOptions.builder()
                .numberOfRetries(ConstantValues.eventTableRepoKeyMigrationRetries.getInt())
                .backoffMaxDelay(ConstantValues.eventTableRepoKeyMigrationBackoffMaxDelay.getInt())
                .exponentialBackoffMultiplier(
                        ConstantValues.eventTableRepoKeyMigrationExponentialBackoffMultiplier.getInt())
                .timeout(ConstantValues.eventTableRepoKeyMigrationTimeout.getInt())
                .failedLog("Event Table repo key migration last retry failed: ${ERROR}. Not trying again").build();
    }


    private ExecutorService getOrCreateExecutor() {
        log.debug("Starting getOrCreateExecutor method");
        if (retryMonitorMigrationThreadPool == null) {
            log.debug("retryMonitorMigrationThreadPool is null, creating a new one...");
            retryMonitorMigrationThreadPool = ArtifactoryExecutorFactory.newClassicThreadPool(
                    ThreadPoolConfig.builder().corePoolSize(1).maxThreads(1).keepAliveTimeout(10L)
                            .keepAliveTimeoutTimeUnit(TimeUnit.SECONDS).allowCoreThreadTimeOut(true)
                            .queue(new LinkedBlockingQueue<>()).daemon(true)
                            .threadNameFormat(MIGRATION_THREAD_NAME).build(), ContextHelper.get(), true);
            log.debug("retryMonitorMigrationThreadPool has been created. {}",
                    retryMonitorMigrationThreadPool.hashCode());
            return retryMonitorMigrationThreadPool;
        }
        log.debug("retryMonitorMigrationThreadPool has been created before, using it {}",
                retryMonitorMigrationThreadPool.hashCode());
        return retryMonitorMigrationThreadPool;
    }

    @Override
    public void destroy() {
        try {
            retryMonitorMigrationThreadPool.shutdownNow();
            retryMonitorMigrationThreadPool.awaitTermination(1000, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.error("Error while Event Table repo key migration executor shutdown", e);
        }
    }

    @Nullable
    public Object eventTableRepoKeyMigrationHandle() throws RetryException {
        try {
            log.debug("Starting eventTableRepoKeyMigrationHandle method");
            if (ConstantValues.eventTableRepoKeyMigrationEnable.getBoolean()) {
                log.debug("eventTableRepoKeyMigrationHandle is enabled, getting server id...");
                String serverId = "";
                if (haAddon().isHaEnabled()) {
                    log.debug("Ha is enabled");
                    serverId = artifactoryHome.getHaNodeProperties() != null ?
                            artifactoryHome.getHaNodeProperties().getServerId() : StringUtils.EMPTY;
                    log.debug("Server id is {}", serverId);
                }
                if (!eventTableRepoKeyMigrationDao.checkIfMigrationFinished(serverId)) {
                    log.debug("Migration is not finished, continue it...");
                    doMigration();
                    log.debug("Migration has been finished");
                }
            }
        } catch (Exception e) {
            log.error("Event table repo key migration failed", e);
            throw new RetryException("Monitor migration failed", e);
        }
        log.debug("eventTableRepoKeyMigrationHandle method has been finished");
        return null;
    }

    public void doMigration() throws SQLException {
        // handle add repo column and update dao
        log.debug("Starting doMigration...");
        eventTableRepoKeyMigrationDao.handleAddRepoColumn();
        log.debug("handleAddRepoColumn has been finished");
        // if HA update all nodes in HA
        if (haAddon().isHaEnabled()) {
            log.debug("HA is enabled. Starting propagateAddColumnMonitorMigration...");
            addonsManager.addonByType(HaAddon.class).propagateAddColumnMonitorMigration();
            log.debug("propagateAddColumnMonitorMigration has been finished");
        }
        eventTableRepoKeyMigrationDao.addIndexOnRepoAndEventId();
        log.debug("addIndexOnRepoAndEventId has been finished");
        eventTableRepoKeyMigrationDao.updateRepoInNodeEvent();
        log.debug("updateRepoInNodeEvent has been finished");
        eventTableRepoKeyMigrationDao.finishedMigration();
        log.debug("finishedMigration has been finished");
    }

    public void updateDaoRepoColumnAddedToEventTable() {
        eventTableRepoKeyMigrationDao.updateDaoRepoColumnAddedToEventTable();
    }

    public boolean getIfEventTableRepoKeyMigrationFinished() {
        return eventTableRepoKeyMigrationDao.getIsEventTableRepoKeyMigrationFinished();
    }
}
