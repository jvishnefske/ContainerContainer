package org.artifactory.metadata.service.indexer;

import com.google.common.annotations.VisibleForTesting;
import com.jfrog.artifactory.packageindexed.DeletePackageIndexEventOuterClass;
import com.jfrog.artifactory.packageindexed.PackageIndexEventOuterClass;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.repo.exception.ItemNotFoundRuntimeException;
import org.artifactory.common.ConstantValues;
import org.artifactory.concurrent.ArtifactoryExecutorFactory;
import org.artifactory.concurrent.ThreadPoolConfig;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.fs.ItemInfo;
import org.artifactory.jfbus.JfbusEventingService.JfbusOperationMode;
import org.artifactory.leadartifact.LeadArtifactFacade;
import org.artifactory.md.Properties;
import org.artifactory.metadata.service.MetadataEntityFacade;
import org.artifactory.metadata.service.MetadataEventService;
import org.artifactory.metadata.service.PackagesEventService;
import org.artifactory.model.RepoConfig;
import org.artifactory.properties.PropertiesImpl;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.storage.db.event.service.metadata.model.MutableMetadataEntityBOM;
import org.jfrog.metadata.client.model.MetadataPackage;
import org.jfrog.metadata.client.model.MetadataVersionRepo;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.artifactory.jfbus.JfbusConstants.HEADER_TYPE;
import static org.artifactory.jfbus.JfbusConstants.PACKAGE_UPDATED_EVENT_TYPE;
import static org.jfrog.common.ExceptionLogUtils.debugOrError;
import static org.jfrog.common.ExceptionLogUtils.debugOrWarn;

/**
 * @author BarakH
 */
@Component
@Slf4j
public class MetadataServiceIndexHandlerImpl
        implements MetadataServiceIndexHandler, InternalMetadataServiceIndexHandler, DisposableBean {

    /**
     * Supported package types by this indexer.
     * The rest of the package types, will get indexed by the old event-based mechanism.
     */
    private static final Set<RepoType> REALTIME_INDEXING_PACKAGES =
            EnumSet.of(RepoType.Docker, RepoType.Npm, RepoType.Gradle, RepoType.Maven, RepoType.SBT, RepoType.Ivy,
                    RepoType.P2, RepoType.Pypi, RepoType.NuGet, RepoType.Go, RepoType.OCI, RepoType.Debian,
                    RepoType.Opkg, RepoType.HelmOCI, RepoType.HuggingFaceML, RepoType.Conda, RepoType.MachineLearning,
                    RepoType.Terraform, RepoType.Pub, RepoType.Ansible, RepoType.Bower, RepoType.CRAN, RepoType.Alpine,
                    RepoType.BazelModules, RepoType.JetBrainsPlugins, RepoType.NimModel, RepoType.Gems,
                    RepoType.Puppet, RepoType.Swift, RepoType.Hex, RepoType.Conan, RepoType.Chef, RepoType.Cargo,
                    RepoType.YUM, RepoType.CocoaPods, RepoType.Vagrant, RepoType.Skills, RepoType.AgentPackages,
                    RepoType.Composer, RepoType.LuaRocks);
    private static final String REPO_PACKAGE_TYPE = "repo-package-type";

    private MetadataEntityFacade metadataEntityFacade;
    private LeadArtifactFacade leadArtifactFacade;
    private MetadataEventService metadataEventService;
    private RepositoryService repositoryService;
    private ExecutorService executorService;
    private boolean isRealTimeIndexingEnabled;
    private final BlockingQueue<Runnable> tasksBlockingQueue = new LinkedBlockingQueue<>();
    private final AtomicInteger notifyCounter = new AtomicInteger(0);
    private final PackagesEventService packagesEventService;


    public MetadataServiceIndexHandlerImpl(PackagesEventService packagesEventService) {
        this.packagesEventService = packagesEventService;
    }

    @PostConstruct
    void init() {
        isRealTimeIndexingEnabled = ConstantValues.metadataRealTimeIndexingEnable.getBoolean();
        if (isRealTimeIndexingEnabled) {
            this.executorService = initExecutor();
        }
    }

    @Override
    public void destroy() throws Exception {
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }

    @Override
    public boolean shouldUseRealTimeIndex(String repoPath) {
        if (!isRealTimeIndexingEnabled || StringUtils.isEmpty(repoPath)) {
            return false;
        }
        String repoKey = RepoPathFactory.create(repoPath).getRepoKey();
        RepoConfig repoConfig = repositoryService.getRepoConfigByKey(repoKey);
        if (repoConfig == null) {
            // Repo might have been deleted since the event created, so we return false.
            return false;
        }
        return REALTIME_INDEXING_PACKAGES.contains(repoConfig.getPackageType());
    }

    @VisibleForTesting
    boolean isRealTimeIdxOrJfbusEnabled(RepoPath repoPath) {
        return (shouldUseRealTimeIndex(repoPath.toPath()) ||
                !JfbusOperationMode.OFF.equals(jfbusOperationMode())) // Jfbus or RTI enabled
                && leadArtifactFacade.isLeadArtifact(repoPath); // Only notify on lead artifacts
    }

    @VisibleForTesting
    JfbusOperationMode jfbusOperationMode() {
        return JfbusOperationMode.valueOf(ConstantValues.jfbusOperationMode.getString());
    }

    @Override
    public void deletePackageAsync(ItemInfo itemInfo, @Nullable Map<String, Collection<String>> props) {
        if (!isRealTimeIdxOrJfbusEnabled(itemInfo.getRepoPath())) {
            return;
        }
        ContextHelper.get().beanForType(InternalMetadataServiceIndexHandler.class)
                .submitPackageDeletionAfterCommit(itemInfo, props);
    }

    @Override
    public void submitPackageDeletionAfterCommit(ItemInfo itemInfo, @Nullable Map<String, Collection<String>> props) {
        RepoPath repoPath = itemInfo.getRepoPath();
        log.debug("Submitting async delete package task to Metadata service at {}", repoPath);
        notifyJfbusOnDelete(itemInfo, props);
        if (isRealTimeIndexingEnabled && !JfbusOperationMode.ACTIVE.equals(jfbusOperationMode())) {
            executorService.submit(new MetadataDeletePackageTask(metadataEventService, repoPath));
            warnIfQueueIsFull();
        }
    }

    private void notifyJfbusOnDelete(ItemInfo itemInfo, Map<String, Collection<String>> propsMap) {
        RepoPath repoPath = itemInfo.getRepoPath();
        if (JfbusOperationMode.OFF.equals(jfbusOperationMode())) {
            return;
        }
        try {
            MetadataVersionRepo pathDeletedEntity = metadataEntityFacade.createPathDeletedEntity(repoPath);

            PackageIndexEventOuterClass.PackageIndexEvent.Builder envelope =
                    PackageIndexEventOuterClass.PackageIndexEvent.newBuilder();
            DeletePackageIndexEventOuterClass.DeletePackageIndexEvent.Builder deletePackageIndexEvent =
                    DeletePackageIndexEventOuterClass.DeletePackageIndexEvent.newBuilder();

            envelope.setRepoName(pathDeletedEntity.getName());
            envelope.setLeadFilePath(pathDeletedEntity.getLeadFilePath());
            if (pathDeletedEntity.getType() != null) {
                envelope.setRepoPackageType(pathDeletedEntity.getType());
            }
            if (pathDeletedEntity.getDownloadCount() != null) {
                deletePackageIndexEvent.setDownloadCount(pathDeletedEntity.getDownloadCount());
            }
            PackageIndexEventConverter.setUserProperties(deletePackageIndexEvent, propsMap);
            envelope.setDeletePackageIndexEvent(deletePackageIndexEvent.build());
            PackageIndexEventOuterClass.PackageIndexEvent event = envelope.build();

            Properties properties = convertMapToProperties(propsMap);
            Optional<MutableMetadataEntityBOM> bom = metadataEntityFacade.createMutableMetadataEntityBOM(
                    itemInfo, properties, metadataEntityFacade.getStatsIfNeeded(itemInfo));

            String pkgName;
            Map<String, String> headers = Map.of();
            if (bom.isPresent()) {
                pkgName = bom.get().getName();
                // Couldn't get package name for deleted item, using repo name instead.
                pkgName = StringUtils.isNotBlank(pkgName) ? pkgName : repoPath.getRepoKey();
                headers = Map.of(REPO_PACKAGE_TYPE, bom.get().getPackageType());
            } else {
                log.warn("Couldn't get Package name for deleted item at {}, using repo name instead.", repoPath);
                pkgName = repoPath.getRepoKey();
            }
            packagesEventService.sendPackageIndexEvent(pkgName, event, headers);
        } catch (Exception e) {
            debugOrWarn(log, "Failed to send package delete event for path: " +
                    repoPath.toPath(), e);
        }
    }

    private static @NonNull Properties convertMapToProperties(Map<String, Collection<String>> props) {
        Properties properties = new PropertiesImpl();
        if (props != null && !props.isEmpty()) {
            for (Map.Entry<String, Collection<String>> entry : props.entrySet()) {
                properties.putAll(entry.getKey(), entry.getValue());
            }
        }
        return properties;
    }

    @Override
    public void indexPackageAsync(ItemInfo itemInfo, Properties properties) {
        RepoPath repoPath = itemInfo.getRepoPath();
        if (!isRealTimeIdxOrJfbusEnabled(repoPath)) {
            return;
        }

        Optional<MutableMetadataEntityBOM> bom = metadataEntityFacade
                .createMutableMetadataEntityBOM(itemInfo, properties, metadataEntityFacade.getStatsIfNeeded(itemInfo));
        if (bom.isEmpty()) {
            log.warn("Failed to create metadata entity BOM for an item at {}", repoPath);
            return;
        }

        MetadataPackage metadataPackage = bom.map(mutableMetadataEntityBOM ->
                        metadataEntityFacade.metadataBomToMdsPackage(mutableMetadataEntityBOM, itemInfo.getRepoPath()))
                .orElse(null);
        if (metadataPackage == null) {
            log.debug("Failed to create metadata package for an item at {}", repoPath);
            return;
        }
        Map<String, String> applicationProperties = new HashMap<>();
        MetadataEntityFacade.extractApplicationProperties(bom.get(), properties, applicationProperties);

        notifyMetadataIfNeeded(repoPath, metadataPackage);
        notifyJfbusIfNeeded(repoPath, metadataPackage, applicationProperties, null);
    }

    private void sendPackagePropertyUpdateEvent(ItemInfo itemInfo, Properties properties) {
        RepoPath repoPath = itemInfo.getRepoPath();
        if (!isRealTimeIdxOrJfbusEnabled(repoPath)) {
            return;
        }

        Optional<MutableMetadataEntityBOM> bom = metadataEntityFacade
                .createMutableMetadataEntityBOM(itemInfo, properties, metadataEntityFacade.getStatsIfNeeded(itemInfo));
        if (bom.isEmpty()) {
            log.warn("Failed to create metadata entity BOM for property update at {}", repoPath);
            return;
        }

        MetadataPackage metadataPackage = bom.map(mutableMetadataEntityBOM ->
                        metadataEntityFacade.metadataBomToMdsPackage(mutableMetadataEntityBOM, itemInfo.getRepoPath()))
                .orElse(null);
        if (metadataPackage == null) {
            log.debug("Failed to create metadata package for property update at {}", repoPath);
            return;
        }
        Map<String, String> applicationProperties = new HashMap<>();
        MetadataEntityFacade.extractApplicationProperties(bom.get(), properties, applicationProperties);

        notifyJfbusIfNeeded(repoPath, metadataPackage, applicationProperties, PACKAGE_UPDATED_EVENT_TYPE);
    }

    @Override
    public void notifyPropertyChange(RepoPath repoPath, @Nullable Properties properties) {
        if (properties == null) {
            return;
        }
        try {
            ItemInfo itemInfo;
            try {
                itemInfo = repositoryService.getItemInfo(repoPath);
            } catch (ItemNotFoundRuntimeException e) {
                log.debug("Item not found for property change notification: {}", repoPath);
                return;
            }
            if (!leadArtifactFacade.isLeadArtifact(repoPath)) {
                return;
            }
            sendPackagePropertyUpdateEvent(itemInfo, properties);
        } catch (Exception e) {
            debugOrError(log, "Failed to notify property change for path: " + repoPath, e);
        }
    }

    private void notifyMetadataIfNeeded(RepoPath repoPath, MetadataPackage metadataPackage) {
        if (isRealTimeIndexingEnabled && !JfbusOperationMode.ACTIVE.equals(jfbusOperationMode())) {
            log.debug("Submitting async create/update package task to Metadata service at {}", repoPath);
            executorService.submit(new MetadataCreatePackageTask(metadataEventService, metadataPackage, repoPath));
            warnIfQueueIsFull();
        }
    }

    private void notifyJfbusIfNeeded(RepoPath repoPath, MetadataPackage metadataPackage,
            Map<String, String> applicationProperties, String eventTypeOverride) {
        if (!JfbusOperationMode.OFF.equals(jfbusOperationMode())) {
            PackageIndexEventOuterClass.PackageIndexEvent event =
                    PackageIndexEventConverter.convert(metadataPackage, applicationProperties);
            try {
                log.debug("Sending package index event for package: {}, PackageType: {}",
                        metadataPackage.getName(), metadataPackage.getPackageType());
                var headers = new HashMap<>(Map.of(REPO_PACKAGE_TYPE, metadataPackage.getPackageType()));
                if (eventTypeOverride != null) {
                    headers.put(HEADER_TYPE, eventTypeOverride);
                }
                packagesEventService.sendPackageIndexEvent(metadataPackage.getName(), event, headers);

                log.debug("Successfully sent package index event for package: {}", metadataPackage.getName());
            } catch (Exception e) {
                debugOrWarn(log, "Failed to send package index event for package: " +
                        metadataPackage.getName(), e);
            }
        }
    }

    @Nonnull
    private ExecutorService initExecutor() {
        int workersCount = ConstantValues.metadataRealTimeIndexingWorkers.getInt();
        return ArtifactoryExecutorFactory.newClassicThreadPool(ThreadPoolConfig.builder()
                .corePoolSize(workersCount)
                .maxThreads(workersCount)
                .keepAliveTimeout(5L)
                .keepAliveTimeoutTimeUnit(TimeUnit.MINUTES)
                .daemon(true)
                .queue(tasksBlockingQueue)
                .threadNameFormat("mds-index-exec-%s")
                .build(), ContextHelper.get(), true, false);
    }

    private void warnIfQueueIsFull() {
        if (notifyCounter.incrementAndGet() % 100 != 0) {
            // Check it only once in 100 tasks
            return;
        }
        notifyCounter.set(0);
        if (tasksBlockingQueue.size() > 100) {
            log.warn("Metadata service indexing Queue is full - consider adding more workers.");
        }
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
    public void setRepositoryService(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    @Override
    public void setMetadataEventService(MetadataEventService metadataEventService) {
        this.metadataEventService = metadataEventService;
    }
}
