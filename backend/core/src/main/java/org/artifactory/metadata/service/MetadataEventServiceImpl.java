package org.artifactory.metadata.service;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.MoreExecutors;
import jakarta.annotation.Nonnull;
import jakarta.ws.rs.core.Response;
import lombok.Setter;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.PropertiesAddon;
import org.artifactory.addon.retention.ArtifactRetentionAddon;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.events.EventLogHintManager;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.application.exception.ApplicationServiceDisableException;
import org.artifactory.application.exception.ApplicationServiceException;
import org.artifactory.application.exception.NotifyExistsPackageException;
import org.artifactory.application.exception.NotifyPackageCreatedException;
import org.artifactory.application.model.ApplicationServiceSBOM;
import org.artifactory.application.service.ApplicationService;
import org.artifactory.aql.AqlService;
import org.artifactory.aql.api.domain.sensitive.AqlApiItem;
import org.artifactory.aql.result.AqlLazyResult;
import org.artifactory.aql.result.rows.AqlItem;
import org.artifactory.aql.result.rows.AqlLazyObjectResultStreamer;
import org.artifactory.aql.result.rows.FileInfoItemRow;
import org.artifactory.common.ConstantValues;
import org.artifactory.concurrent.ArtifactoryExecutorFactory;
import org.artifactory.concurrent.ThreadPoolConfig;
import org.artifactory.config.InternalCentralConfigService;
import org.artifactory.event.EventHintType;
import org.artifactory.event.EventOperatorId;
import org.artifactory.event.EventType;
import org.artifactory.event.priority.service.model.PrioritizedNodeEvent;
import org.artifactory.event.work.NodeEventOperator;
import org.artifactory.event.work.NodeEventTaskManager;
import org.artifactory.event.work.NodeEventsErrorService;
import org.artifactory.event.work.NodeEventsErrorsBackOffStrategy;
import org.artifactory.event.work.StatusProbe;
import org.artifactory.event.work.StatusProbeImpl;
import org.artifactory.fs.StatsInfo;
import org.artifactory.jfbus.JfbusEventingService;
import org.artifactory.leadartifact.LeadArtifactFacade;
import org.artifactory.metadata.MetadataBackOffStrategy;
import org.artifactory.metadata.service.indexer.InternalMetadataServiceIndexHandler;
import org.artifactory.metadata.service.store.ArtifactoryMetadataClientConfigStore;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.sapi.common.ExportSettings;
import org.artifactory.sapi.common.ImportSettings;
import org.artifactory.security.access.AccessService;
import org.artifactory.spring.ContextCreationListener;
import org.artifactory.spring.Reloadable;
import org.artifactory.storage.db.DbService;
import org.artifactory.storage.db.event.entity.enums.NodeEventCursorType;
import org.artifactory.storage.db.event.model.NodeEventCursor;
import org.artifactory.storage.db.event.service.NodeEventCursorService;
import org.artifactory.storage.db.event.service.metadata.model.MetadataEvent;
import org.artifactory.storage.db.fs.dao.NodesDao;
import org.artifactory.storage.db.fs.model.MetadataStats;
import org.artifactory.storage.db.fs.service.AbstractStatsService;
import org.artifactory.storage.db.fs.service.StatsPersistingServiceImpl;
import org.artifactory.storage.db.fs.service.UpdateArtifactStatsEvent;
import org.artifactory.storage.db.init.migration.service.MigrationStatusStorageService;
import org.artifactory.storage.event.EventsService;
import org.artifactory.version.CompoundVersionDetails;
import org.jfrog.client.util.ServicelistLoader;
import org.jfrog.common.ClockUtils;
import org.jfrog.common.ExceptionLogUtils;
import org.jfrog.metadata.client.MetadataClient;
import org.jfrog.metadata.client.confstore.MetadataClientConfigStore;
import org.jfrog.metadata.client.model.MetadataEntity;
import org.jfrog.metadata.client.model.MetadataPackage;
import org.jfrog.metadata.client.model.MetadataStatistics;
import org.jfrog.metadata.client.model.MetadataVersionRepo;
import org.jfrog.metadata.client.model.event.MetadataEventEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.AndClause;
import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.OrClause;
import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.createWithEmptyResults;
import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.name;
import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.or;
import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.path;
import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.repo;
import static org.artifactory.aql.api.internal.AqlBase.and;
import static org.artifactory.repo.RepoPathFactory.create;
import static org.artifactory.util.application.ApplicationServiceConfigurationUtil.APPLICATION_SERVICE_ID_PROPERTY_NAME;
import static org.artifactory.util.application.ApplicationServiceConfigurationUtil.isApplicationServiceFeatureEnabled;

/**
 * A business service that handles Metadata Events. Responsible for starting the event pipeline and initializing
 * the {@link MetadataClient}. Additionally, this service implements the {@link NodeEventOperator} which provides
 * the logic for handling Metadata events.
 *
 * @author Uriah Levy
 */
@Reloadable(beanClass = MetadataEventService.class, initAfter = {DbService.class, InternalCentralConfigService.class,
        InternalRepositoryService.class})
public class MetadataEventServiceImpl implements MetadataEventService, ContextCreationListener,
        ApplicationListener<UpdateArtifactStatsEvent> {

    private static final Logger log = LoggerFactory.getLogger(MetadataEventServiceImpl.class);

    private StatsPersistingServiceImpl statsPersistingService;
    private RepositoryService repositoryService;
    private NodeEventCursorService nodeEventCursorService;
    private AccessService accessService;
    private NodeEventTaskManager nodeEventTaskManager;
    private StatusProbe statusProbe;
    private MetadataClient metadataClient;
    private MetadataClientConfigStore configStore;
    private boolean operatorEnabled = false;
    private ExecutorService asyncProbeExecutor;
    private MigrationStatusStorageService migrationStatusStorageService;
    @Setter(onMethod_ = {@Autowired})
    private ApplicationService applicationService;
    private NodesDao nodesDao;
    private AqlService aqlService;
    private EventsService eventsService;
    private MetadataEntityFacade metadataEntityFacade;
    private LeadArtifactFacade leadArtifactFacade;
    private CentralConfigService configService;
    private MetadataMigrationHelper metadataMigrationHelper;
    private NodeEventsErrorService nodeEventsErrorService;
    private ServicelistLoader servicelistLoader;

    @Setter(onMethod_ = {@Autowired})
    private AddonsManager addonsManager;
    @Setter(onMethod_ = {@Autowired})
    private StatsEventService statsEventService;
    private InternalMetadataServiceIndexHandler internalMetadataServiceIndexHandler;
    private static final List<Pair<EventType, EventType>> RESPECTIVE_MDS_SQUASHABLE_TYPES =
            Lists.newArrayList(new ImmutablePair<>(EventType.create, EventType.props),
                    new ImmutablePair<>(EventType.props, EventType.create));

    @Autowired
    public void setNodeEventsErrorService(NodeEventsErrorService nodeEventsErrorService) {
        this.nodeEventsErrorService = nodeEventsErrorService;
    }

    @Autowired
    public void setMetadataEntityFacade(MetadataEntityFacade metadataEntityFacade) {
        this.metadataEntityFacade = metadataEntityFacade;
    }

    @Autowired
    public void setLeadArtifactFacade(LeadArtifactFacade leadArtifactFacade) {
        this.leadArtifactFacade = leadArtifactFacade;
    }

    @Autowired
    public void setInternalMetadataServiceIndexHandler(
            InternalMetadataServiceIndexHandler internalMetadataServiceIndexHandler) {
        this.internalMetadataServiceIndexHandler = internalMetadataServiceIndexHandler;
    }

    @Autowired
    public void setConfigService(CentralConfigService configService) {
        this.configService = configService;
    }

    @Autowired
    public void setStatsPersistingService(StatsPersistingServiceImpl statsPersistingService) {
        this.statsPersistingService = statsPersistingService;
    }

    @Override
    public void init() {
        metadataEntityFacade.setMetadataService(this);
        internalMetadataServiceIndexHandler.setMetadataEventService(this);
        if (metadataMigrationHelper == null) {
            metadataMigrationHelper = new MetadataMigrationHelper(migrationStatusStorageService,
                    nodeEventCursorService, nodesDao, aqlService, eventsService, repositoryService,
                    metadataEntityFacade,
                    this, nodeEventsErrorService);
        }
        if (statusProbe == null) {
            this.statusProbe = new StatusProbeImpl(TimeUnit.MINUTES.toMillis(2), 11_000, getOperatorId());
        }
        if (isFeatureEnable()) {
            initOperatorCursor();
            if (configStore == null) {
                initMetadataConfigStore();
            }
            getOrCreateAsyncProbeExecutor();
            if (metadataClient == null) {
                initMetadataClient();
            }
        }
    }

    private boolean isFeatureEnable() {
        ArtifactRetentionAddon artifactRetentionAddon = addonsManager.addonByType(ArtifactRetentionAddon.class);

        return ConstantValues.metadataServerEventsEnabled.getBoolean() &&
                !artifactRetentionAddon.isColdInstanceV1orV2();
    }

    @Override
    public void convert(CompoundVersionDetails source, CompoundVersionDetails target) {
        init();
        MetadataConverterVersion.convert(source.getVersion());
    }

    @Override
    public void onContextCreated() {
        if (isFeatureEnable()) {
            metadataMigrationHelper.migrateOrStartEventPipe(this, nodeEventTaskManager);
        }
    }

    @Override
    public boolean isOperatorEnabled() {
        return this.operatorEnabled;
    }

    @Override
    public MetadataClient getMetadataClient() {
        return metadataClient;
    }

    void setMetadataClient(MetadataClient metadataClient) {
        this.metadataClient = metadataClient;
    }

    @Override
    public void initOperatorCursor() {
        String eventPipelineCursorId = getEventPipelineCursorId();
        NodeEventCursor eventCursor = nodeEventCursorService.cursorForOperator(eventPipelineCursorId);
        if (eventCursor == null) {
            nodeEventCursorService.insertCursor(eventPipelineCursorId, 0, NodeEventCursorType.METADATA_EVENTS);
        }
    }

    @Override
    public void reindexAsync(List<String> reindexPaths, boolean deleteFirst) {
        if (!operatorEnabled) {
            log.error("Unable to start Metadata Re-indexing since " +
                    "the Metadata Events Operator is disabled. Please review the logs to see why");
            return;
        }
        try {
            reindexPaths.forEach(path -> doReindex(path, deleteFirst));
        } catch (Exception e) {
            log.error("Caught exception while attempting to reindex paths {}", reindexPaths, e);
        }
    }

    @Override
    public Response reindexSync(List<String> reindexPaths, boolean deleteFirst) {
        if (!operatorEnabled) {
            log.error(
                    "Unable to start Metadata Re-indexing since the Metadata Events Operator is disabled. " +
                            "Please review the logs to see why");
            return Response.serverError()
                    .entity("Unable to start Metadata Re-indexing since the Metadata Events Operator is disabled. " +
                            "Please review the logs to see why")
                    .build();
        }
        try {
            reindexPaths.forEach(path -> doReindex(path, deleteFirst));
        } catch (Exception e) {
            String message = String.format("Caught exception while attempting to reindex paths %s", reindexPaths);
            log.error(message, e);
            return Response.serverError().entity(message).build();
        }
        return Response.ok().build();
    }

    @Override
    public Response statsRecreateSync(List<String> statsCalculationPaths) {
        if (!operatorEnabled) {
            log.error(
                    "Unable to start Metadata Stats Re-creation since the Metadata Events Operator is disabled. " +
                            "Please review the logs to see why");
            return Response.serverError()
                    .entity("Unable to start Metadata Stats Re-creation since the Metadata Events Operator is " +
                            "disabled. Please review the logs to see why")
                    .build();
        }
        try {
            statsCalculationPaths.forEach(this::doStatsRecreate);
        } catch (Exception e) {
            String message =
                    String.format("Caught exception while attempting to reindex paths %s", statsCalculationPaths);
            log.error(message, e);
            return Response.serverError().entity(message).build();
        }
        return Response.ok().build();
    }

    @Override
    public void statsRecreateAsync(List<String> statsCalculationPaths) {
        if (!operatorEnabled) {
            log.error("Unable to start Metadata Stats Re-creation since " +
                    "the Metadata Events Operator is disabled. Please review the logs to see why");
            return;
        }
        try {
            statsCalculationPaths.forEach(this::doStatsRecreate);
        } catch (Exception e) {
            log.error("Caught exception while attempting to stats recreation paths {}", statsCalculationPaths, e);
        }
    }

    @Override
    public void deletePathFromMetadataServerUsingMetadataClient(RepoPath repoPath) {
        if (!operatorEnabled) {
            log.debug("Metadata Service Is not enable. Delete operation.");
            return;
        }
        if (repoPath.isRoot()) {
            log.info("Received delete instruction on root for repository '{}'. Deleting paths from Metadata Server",
                     repoPath.getRepoKey());
        } else {
            log.debug("Deleting Metadata Entity with path '{}'", repoPath);
        }
        MetadataVersionRepo pathDeletedEntity = metadataEntityFacade.createPathDeletedEntity(repoPath);
        try {
            metadataClient.paths().deletePath(pathDeletedEntity);
        } catch (Exception e) {
            log.error("Unable to delete path '{}' on Metadata Server", repoPath.toPath(), e);
        }
    }

    /**
     * Invoked by {@link org.artifactory.event.work.NodeEventSweeper} while holding a per-path
     * {@code node-event-task-manager} cluster lock via {@link org.artifactory.event.work.EventOperatorDispatcher}.
     */
    @Override
    public void operateOnEvent(PrioritizedNodeEvent nodeEvent) {
        MetadataEvent metadataEvent = prioritizedEventToMetadataEvent(nodeEvent);
        if (metadataEvent != null) {
            handleMetadataEvent(metadataEvent);
        }
    }

    public boolean meetsExecutionCriteria(PrioritizedNodeEvent nodeEvent) {
        return leadArtifactFacade.isLeadArtifact(nodeEvent.getRepoPath());
    }

    @Override
    public boolean isSquashEventsByPathAndTypeCombination() {
        return true;
    }

    @Override
    public List<Pair<EventType, EventType>> getSquashableEventTypes() {
        return RESPECTIVE_MDS_SQUASHABLE_TYPES;
    }

    @Override
    public long getDispatcherIntervalSecs() {
        return ConstantValues.metadataEventOperatorDispatcherIntervalSecs.getLong();
    }

    @Override
    public String getOperatorId() {
        if (metadataMigrationHelper.isMigrating()) {
            return getMigrationCursorId();
        }
        return getEventPipelineCursorId();
    }

    @Override
    public String getEventCategory() {
        return getOperatorId();
    }

    @Override
    public NodeEventsErrorsBackOffStrategy getBackOffStrategy() {
        return new MetadataBackOffStrategy();
    }

    @Override
    public int getNumberOfEventThreads() {
        if (isMigrating()) {
            return ConstantValues.metadataMigrationEventOperatorThreads.getInt();
        } else {
            return ConstantValues.metadataEventOperatorThreads.getInt();
        }
    }

    @Override
    public void onErrorThresholdReached() {
        disableServiceAndProbeSync();
    }

    @Override
    public Predicate<PrioritizedNodeEvent> getExecutionPredicate() {
        return this::meetsExecutionCriteria;
    }

    @Override
    public void destroy() {
        try {
            nodeEventTaskManager.stopDispatcherThread(getOperatorId());
            if (statusProbe != null) {
                statusProbe.destroy();
            }
            if (isFeatureEnable()) {
                asyncProbeExecutor.shutdown();
                asyncProbeExecutor.awaitTermination(20, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            log.error("Unable to stop Metadata Probe Executor: {}", e.getMessage());
            log.debug("Unable to stop Metadata Probe Executor", e);
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            log.error("Unable to stop Metadata tasks dispatcher, caught exception: {}", e.getMessage(), e);
        }
    }

    @Override
    public void onApplicationEvent(@Nonnull UpdateArtifactStatsEvent event) {
        try {
            if (isJfbusStatsActive()) {
                // jfbus path is decoupled from MDS reachability: events ride the bus, so an MDS
                // outage must not block producer-side publish. Rollback is via jfbus.working.mode=OFF.
                statsEventService.publishStats(event.getPathToStats());
            } else if (isOperatorEnabled()) {
                updateVersionStatistics(event.getPathToStats());
            }
        } catch (Exception e) {
            log.error("Unable to send statistics event to Metadata Server. Caught exception: {}",
                    e.getMessage(), e);
        }
    }

    /**
     * Hard switch between REST and jfbus publish for stats events. Both flags must be true:
     * the per-event-type {@code jfbus.stats.events.enabled} AND the global
     * {@code jfbus.working.mode == ACTIVE}. No SHADOW phase — rollback flips
     * {@code jfbus.working.mode} back to OFF and REST publish resumes without redeploy.
     */
    private boolean isJfbusStatsActive() {
        return ConstantValues.jfbusStatsEventsEnabled.getBoolean()
                && JfbusEventingService.JfbusOperationMode.ACTIVE.equals(getJfbusOperationMode());
    }

    @Override
    public boolean isMigrating() {
        return metadataMigrationHelper.isMigrating();
    }

    @Override
    public String getMigrationCursorId() {
        return EventOperatorId.METADATA_OPERATOR_ID.getId() + "-migration";
    }

    @Override
    public String getEventPipelineCursorId() {
        return EventOperatorId.METADATA_OPERATOR_ID.getId() + "-events";
    }

    void setConfigStore(MetadataClientConfigStore configStore) {
        this.configStore = configStore;
    }

    void initMetadataConfigStore() {
        configStore = new ArtifactoryMetadataClientConfigStore(accessService,
                configService.getSystemConfiguration(), servicelistLoader);
    }

    void initMetadataClient() {
        closeCurrentClient();
        try {
            metadataClient = configStore.authenticatedClientBuilder().create();
            operatorEnabled = metadataClient.system().ping();
        } catch (Exception e) {
            log.warn("Unable to init the Metadata client. The Metadata Event pipeline will be disabled: {}",
                    e.getMessage());
            log.debug("", e);
            if (!statusProbe.isProbing()) { // If we're already probing, avoid cyclic re-init
                log.warn("Metadata Client not initialized. Starting Metadata Server Status Probe");
                disableServiceAndProbeAsync();
            }
        }
    }

    private void doReindex(String path, boolean deleteFirst) {
        if (StringUtils.isBlank(path)) {
            log.warn("Empty path found while re-indexing Metadata Server, skipping current path.");
            return;
        }
        RepoPath repoPath = RepoPathFactory.create(path);
        if (deleteFirst) {
            deletePathFromMetadataServerUsingMetadataClient(repoPath);
        }
        Set<String> supportedExtensions = leadArtifactFacade.getLeadArtifactExtensions(repoPath);
        if (supportedExtensions.isEmpty()) {
            log.warn("Unsupported for repository {}", repoPath);
        } else {
            proceedPathOrItem(repoPath, supportedExtensions, this::reindexItem);
        }
    }

    private void doStatsRecreate(String path) {
        if (StringUtils.isBlank(path)) {
            log.warn("Empty path found while stats re-creation Metadata Server, skipping current path.");
            return;
        }
        RepoPath repoPath = RepoPathFactory.create(path);
        Set<String> supportedExtensions = leadArtifactFacade.getLeadArtifactExtensions(repoPath);
        if (supportedExtensions.isEmpty()) {
            log.warn("Unsupported for repository {}", repoPath);
        } else {
            proceedPathOrItem(repoPath, supportedExtensions, this::recreateStats);
        }
    }

    private void proceedPathOrItem(RepoPath reindexPath, Set<String> extension,
            Consumer<RepoPath> dataCalculationFunction) {
        if (reindexPath.isFolder()) {
            // Index recursively
            try (AqlLazyResult<AqlItem> lazyResult = aqlService
                    .executeQueryLazy(getAllArtifactsUnderMeQuery(reindexPath, extension))) {
                AqlLazyObjectResultStreamer<FileInfoItemRow> resultStreamer =
                        new AqlLazyObjectResultStreamer<>(lazyResult, FileInfoItemRow.class);
                FileInfoItemRow item;
                while ((item = resultStreamer.getRow()) != null) {
                    dataCalculationFunction.accept(item.getRepoPath());
                }
            } catch (Exception e) {
                log.error("Unable to re-index '{}' recursively on the Metadata Server:", reindexPath, e);
            }
        } else {
            dataCalculationFunction.accept(reindexPath);
        }
    }

    private void recreateStats(RepoPath repoPath) {
        StatsInfo statsInfo = statsPersistingService.getStats(repoPath);
        if (statsInfo != null) {
            log.debug("Recreating stats for '{}'", repoPath);
            AbstractStatsService.StatsEvent statsEvent =
                    new AbstractStatsService.StatsEvent(repoPath, statsInfo.getDownloadCount(),
                            statsInfo.getRemoteDownloadCount(), statsInfo.getLastDownloaded(),
                            statsInfo.getRemoteLastDownloaded());

            statsPersistingService.getStatsEvents().put(repoPath, statsEvent);
        }
    }

    private void reindexItem(RepoPath itemPath) {
        Optional<MetadataPackage> mdsPackage = metadataEntityFacade.createMdsPackageEntity(itemPath);
        if (mdsPackage.isPresent()) {
            log.debug("Reindexing {} on the Metadata Server", itemPath);
            MetadataEvent metadataEvent = MetadataEvent.builder()
                    .eventTime(ClockUtils.epochMillis())
                    .eventType(EventType.update)
                    .metadataEventEntity(MetadataEventEntity.PACKAGE_UPDATED)
                    .metadataEntity(mdsPackage.get())
                    .path(resolveEventPath(itemPath))
                    .build();
            createOrUpdatePackage(metadataEvent);
        }
    }

    private AqlApiItem getAllArtifactsUnderMeQuery(RepoPath reindexPath, Set<String> supportedLeadFileExtension) {
        AndClause<AqlApiItem> aqlFilter = and(repo().equal(reindexPath.getRepoKey()),
                getExtensionCriteria(supportedLeadFileExtension));
        if (!reindexPath.isRoot()) {
            aqlFilter.append(
                    or(
                            AqlApiItem.path().matches(reindexPath.getPath() + "/*"), // deep child
                            AqlApiItem.path().equal(reindexPath.getPath())) // direct child
            );
        }
        return createWithEmptyResults()
                .filter(aqlFilter)
                .include(repo(), path(), name());
    }

    private OrClause<AqlApiItem> getExtensionCriteria(Set<String> supportedLeadFileExtension) {
        OrClause<AqlApiItem> extensionCriteria = or();
        supportedLeadFileExtension.forEach(extension -> extensionCriteria.append(name().matches("*" + extension)));
        return extensionCriteria;
    }

    private void handleMetadataEvent(MetadataEvent metadataEvent) {
        if (isPackageCreateOrUpdateEvent(metadataEvent)) {
            createOrUpdatePackage(metadataEvent);
        } else if (isPathDeletedEvent(metadataEvent) &&
                           !JfbusEventingService.JfbusOperationMode.ACTIVE.equals(getJfbusOperationMode())) {
            deletePathFromMetadataServerUsingMetadataClient(RepoPathFactory.create(metadataEvent.getPath()));
        }

    }

    private void updateVersionStatistics(Map<RepoPath, MetadataStats> pathToMetadataStats) {
        MetadataStatistics metadataStatistics = metadataEntityFacade.getMdsItemStatistics(pathToMetadataStats);
        if (!metadataStatistics.getItems().isEmpty()) {
            if (log.isDebugEnabled()) {
                log.debug("Updating package statistics for '{}' items", metadataStatistics.getItems().size());
            }
            metadataClient.versions().updateVersionStatistics(metadataStatistics);
        }
    }

    @VisibleForTesting
    void createOrUpdatePackage(MetadataEvent metadataEvent) {
        MetadataPackage metadataPackage = (MetadataPackage) metadataEvent.getMetadataEntity();
        if (metadataPackage == null) {
            return;
        }
        log.debug("Creating/updating Package Metadata for '{}' ({})", metadataPackage.getPkgid(),
                metadataEvent.getPath());

        if (!JfbusEventingService.JfbusOperationMode.ACTIVE.equals(getJfbusOperationMode())) {
            metadataClient.packages().createOrUpdate(metadataPackage);
        }
        notifyApplicationServicePackageCreated(metadataEvent, metadataPackage);
    }

    private JfbusEventingService.JfbusOperationMode getJfbusOperationMode() {
        return JfbusEventingService.JfbusOperationMode.valueOf(ConstantValues.jfbusOperationMode.getString());
    }

    /**
     * Notify the application service on new package creation
     */
    private void notifyApplicationServicePackageCreated(MetadataEvent metadataEvent, MetadataPackage metadataPackage) {
        if (shouldNotifyApplicationServiceOnNewPackageCreation(metadataEvent)) {
            log.debug("Start process of notify application service on new package: {} to bind application",
                    metadataPackage.getPkgid());
            applicationService.createApplicationServiceSBOM(metadataEvent, metadataPackage)
                    .ifPresent(bom -> handlePackageCreationNotification(metadataEvent, metadataPackage, bom));
            log.debug("The package {} is now bound to the application {}",
                    metadataPackage.getPkgid(), metadataEvent.getApplicationKey());
        }
    }

    private void handlePackageCreationNotification(MetadataEvent metadataEvent, MetadataPackage metadataPackage,
            ApplicationServiceSBOM applicationServiceSBOM) {
        try {
            applicationService.notifyPackageCreated(applicationServiceSBOM);
            log.info("Notified application service on new package: {} to bind application {}",
                    metadataPackage.getPkgid(), metadataEvent.getApplicationKey());
            deleteApplicationProperties(metadataEvent);
        } catch (ApplicationServiceDisableException e) {
            throw new ApplicationServiceException("Application service is disabled", e);
        } catch (NotifyExistsPackageException e) {
            log.debug("Application already exists for package: {}. Delete application.id property from artifact"
                    , metadataPackage.getPkgid());
            deleteApplicationProperties(metadataEvent);
        } catch (NotifyPackageCreatedException e) {
            ExceptionLogUtils.debugOrWarn(log, String.format(
                    "Failed to notify application service on new package: %s to bind application %s",
                    metadataPackage.getPkgid(), metadataEvent.getApplicationKey()), e);
            throw e;
        }
    }

    private void deleteApplicationProperties(MetadataEvent metadataEvent) {
        try {
            EventLogHintManager.put(EventHintType.ApplicationPropertyCleanup, APPLICATION_SERVICE_ID_PROPERTY_NAME);
            PropertiesAddon propertiesAddon = addonsManager.addonByType(PropertiesAddon.class);
            for (String prop : metadataEvent.getApplicationProperties().keySet()) {
                propertiesAddon.deleteProperty(create(metadataEvent.getPath()), prop);
                log.debug("Deleted application property {} with id {} for path: {}", prop,
                        metadataEvent.getApplicationKey(),
                        metadataEvent.getPath());
            }
            log.info("Deleted application properties with id {} for path: {}", metadataEvent.getApplicationKey(),
                    metadataEvent.getPath());
        } finally {
            EventLogHintManager.remove(EventHintType.ApplicationPropertyCleanup);
        }
    }

    private boolean shouldNotifyApplicationServiceOnNewPackageCreation(MetadataEvent metadataEvent) {
        return isApplicationServiceFeatureEnabled() && StringUtils.isNotEmpty(metadataEvent.getApplicationKey());
    }

    private boolean isPathDeletedEvent(MetadataEvent metadataEvent) {
        return metadataEvent.getMetadataEventEntity().equals(MetadataEventEntity.PATH_DELETED);
    }

    private boolean isPackageCreateOrUpdateEvent(MetadataEvent metadataEvent) {
        return metadataEvent.getMetadataEventEntity().equals(MetadataEventEntity.PACKAGE_CREATED) ||
                metadataEvent.getMetadataEventEntity().equals(MetadataEventEntity.PACKAGE_UPDATED);
    }

    private void closeCurrentClient() {
        if (metadataClient != null) {
            log.info("Closing old Metadata Client {}", metadataClient.hashCode());
            IOUtils.closeQuietly(metadataClient);
        }
    }

    private ExecutorService getOrCreateAsyncProbeExecutor() {
        if (asyncProbeExecutor == null) {
            asyncProbeExecutor = ArtifactoryExecutorFactory.newClassicThreadPool(ThreadPoolConfig.builder()
                    .corePoolSize(1)
                    .maxThreads(1)
                    .keepAliveTimeout(0L)
                    .keepAliveTimeoutTimeUnit(TimeUnit.SECONDS)
                    .daemon(true)
                    .queue(new LinkedBlockingDeque<>(1))
                    .threadNameFormat(getOperatorId() + "-status-probe")
                    .build(), ContextHelper.get(), false);
            return asyncProbeExecutor;
        }
        return asyncProbeExecutor;
    }

    private void disableServiceAndProbeSync() {
        if (operatorEnabled) {
            this.operatorEnabled = false;
            statusProbe.startProbing(MoreExecutors.newDirectExecutorService(), this::probeMetadataServer,
                    this::onSuccessfulStatusProbe);
        }
    }

    private void disableServiceAndProbeAsync() {
        this.operatorEnabled = false;
        statusProbe
                .startProbing(getOrCreateAsyncProbeExecutor(), this::probeMetadataServer,
                        this::onSuccessfulStatusProbe);
    }

    private void onSuccessfulStatusProbe() {
        initMetadataClient();
        log.info("Metadata Service Probing succeeded. The event pipeline was re-enabled.");
    }

    private boolean probeMetadataServer() {
        try (MetadataClient noAuthMetadataClient = configStore.noAuthClientBuilder().create()) {
            return noAuthMetadataClient.system().ping();
        } catch (IOException e) {
            log.error("Unable to create the status probe HTTP client", e);
        }
        return false;
    }

    ExecutorService getAsyncProbeExecutor() {
        return getOrCreateAsyncProbeExecutor();
    }

    MetadataEvent prioritizedEventToMetadataEvent(PrioritizedNodeEvent prioritizedNodeEvent) {
        return metadataEntityFacade.createMetadataEntity(prioritizedNodeEvent)
                .map(entityToEventEntity -> createMetadataEvent(prioritizedNodeEvent,
                        entityToEventEntity.getMetadataEntity(),
                        entityToEventEntity.getMetadataEventEntity(),
                        entityToEventEntity.getApplicationProperties()))
                .orElse(null);
    }

    private MetadataEvent createMetadataEvent(PrioritizedNodeEvent nodeEvent, MetadataEntity metadataEntity,
            MetadataEventEntity eventEntity, Map<String, String> applicationProperties) {
        return MetadataEvent.builder()
                .eventType(nodeEvent.getType())
                .metadataEventEntity(eventEntity)
                .eventTime(nodeEvent.getTimestamp())
                .metadataEntity(metadataEntity)
                .path(resolveEventPath(nodeEvent.getRepoPath()))
                .applicationProperties(applicationProperties)
                .build();
    }

    private String resolveEventPath(RepoPath repoPath) {
        String resolvedRepoKey = metadataEntityFacade.resolveRepoKey(repoPath.getRepoKey());
        return repoPath.getPath().isEmpty()
                ? resolvedRepoKey
                : resolvedRepoKey + "/" + repoPath.getPath();
    }

    void setStatusProbe(StatusProbe statusProbe) {
        this.statusProbe = statusProbe;
    }

    @Autowired
    void setRepositoryService(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    @Autowired
    void setAccessService(AccessService accessService) {
        this.accessService = accessService;
    }

    @Autowired
    void setNodeEventCursorService(NodeEventCursorService nodeEventCursorService) {
        this.nodeEventCursorService = nodeEventCursorService;
    }

    @Autowired
    void setNodeEventTaskManager(NodeEventTaskManager nodeEventTaskManager) {
        this.nodeEventTaskManager = nodeEventTaskManager;
    }

    @Autowired
    void setMigrationStatusStorageService(
            MigrationStatusStorageService migrationStatusStorageService) {
        this.migrationStatusStorageService = migrationStatusStorageService;
    }

    @Autowired
    void setNodesDao(NodesDao nodesDao) {
        this.nodesDao = nodesDao;
    }

    @Autowired
    void setAqlService(AqlService aqlService) {
        this.aqlService = aqlService;
    }

    @Autowired
    void setEventsService(EventsService eventsService) {
        this.eventsService = eventsService;
    }

    void setMetadataMigrationHelper(MetadataMigrationHelper metadataMigrationHelper) {
        this.metadataMigrationHelper = metadataMigrationHelper;
    }

    @Override
    public void exportTo(ExportSettings settings) {
        // noop
    }

    @Override
    public void importFrom(ImportSettings settings) {
        // Re-init the metadata client which holds a service token required to communicate with the MDS, which may be
        // invalid post-access-import
        initMetadataClient();
    }

    /**
     * Refresh the metadata client and config store with the new service list loader
     *
     * @param servicelistLoader The new service list loader
     */
    public void refreshMetadataClient(ServicelistLoader servicelistLoader) {
        this.servicelistLoader = servicelistLoader;
        initMetadataConfigStore();
        initMetadataClient();
    }
}
