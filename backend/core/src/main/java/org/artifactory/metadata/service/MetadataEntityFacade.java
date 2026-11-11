package org.artifactory.metadata.service;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.event.EventType;
import org.artifactory.event.priority.service.model.PrioritizedNodeEvent;
import org.artifactory.fs.FileInfo;
import org.artifactory.fs.ItemInfo;
import org.artifactory.fs.StatsInfo;
import org.artifactory.leadartifact.LeadArtifactFacade;
import org.artifactory.md.Properties;
import org.artifactory.metadata.service.provider.AbstractMetadataProvider;
import org.artifactory.model.LocalCacheRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.storage.db.event.service.metadata.mapper.MetadataEntityMapper;
import org.artifactory.storage.db.event.service.metadata.model.MetadataApplicationEntity;
import org.artifactory.storage.db.event.service.metadata.model.MutableMetadataEntityBOM;
import org.artifactory.storage.db.fs.model.MetadataStats;
import org.artifactory.storage.fs.service.FileService;
import org.artifactory.storage.fs.service.PropertiesService;
import org.artifactory.storage.fs.service.StatsService;
import org.jfrog.client.util.PathUtils;
import org.jfrog.metadata.client.model.MetadataEntity;
import org.jfrog.metadata.client.model.MetadataFile;
import org.jfrog.metadata.client.model.MetadataItemStatistics;
import org.jfrog.metadata.client.model.MetadataPackage;
import org.jfrog.metadata.client.model.MetadataStatistics;
import org.jfrog.metadata.client.model.MetadataVersion;
import org.jfrog.metadata.client.model.MetadataVersionRepo;
import org.jfrog.metadata.client.model.event.MetadataEventEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;
import static org.artifactory.util.application.ApplicationServiceConfigurationUtil.APPLICATION_SERVICE_PROPERTIES_NAMES;


/**
 * A facade for creating {@link MetadataEntity}'s
 *
 * @author Uriah Levy
 */
@Slf4j
@Component
public class MetadataEntityFacade {

    private final FileService fileService;
    private final PropertiesService propertiesService;
    private final InternalRepositoryService repositoryService;
    private final MetadataEntityMapper metadataEntityMapper;
    private final Map<RepoType, AbstractMetadataProvider> metadataProvidersMap;
    private final LeadArtifactFacade leadArtifactFacade;
    private MetadataEventService metadataEventService;

    @Autowired
    public MetadataEntityFacade(List<AbstractMetadataProvider> metadataProviders, FileService fileService,
            PropertiesService propertiesService, InternalRepositoryService repositoryService,
            MetadataEntityMapper metadataEntityMapper, LeadArtifactFacade leadArtifactFacade) {
        this.metadataProvidersMap = metadataProviders.stream()
                .collect(Collectors.toMap(AbstractMetadataProvider::getRepoType,
                        Function.identity()));
        this.fileService = fileService;
        this.propertiesService = propertiesService;
        this.repositoryService = repositoryService;
        this.metadataEntityMapper = metadataEntityMapper;
        this.leadArtifactFacade = leadArtifactFacade;
    }

    public void setMetadataService(MetadataEventServiceImpl metadataEventService) {
        this.metadataEventService = metadataEventService;
    }

    Optional<MetadataApplicationEntity> createMetadataEntity(PrioritizedNodeEvent event) {
        Optional<ItemInfo> itemInfo = loadItem(event.getRepoPath());
        if (isDeleteEvent(event.getType())) {
            if (itemInfo.isEmpty()) {
                MetadataApplicationEntity metadataApplicationEntity = new MetadataApplicationEntity(
                        MetadataEventEntity.PATH_DELETED,
                        createPathDeletedEntity(event.getRepoPath()),
                        null);
                return Optional.of(metadataApplicationEntity);
            }
            log.debug("Skipping delete event, item {} exists", event.getRepoPath());
            return Optional.empty();
        }
        return getMetadataEntity(event, itemInfo);
    }

    @Nonnull
    private Optional<MetadataApplicationEntity> getMetadataEntity(PrioritizedNodeEvent event,
            Optional<ItemInfo> itemInfo) {
        if (itemInfo.isEmpty()) {
            log.debug("Skipping create event, item {} not found", event.getRepoPath());
            return Optional.empty();
        }
        ItemInfo item = itemInfo.get();
        Properties properties = propertiesService.getProperties(item);
        Optional<MutableMetadataEntityBOM> mutableMetadataBOM =
                createMutableMetadataEntityBOM(item, properties, getStatsIfNeeded(item));
        return mutableMetadataBOM.flatMap(bom -> createMetadataApplicationEntity(bom, item, properties));
    }

    @Nonnull
    private Optional<MetadataApplicationEntity> createMetadataApplicationEntity(MutableMetadataEntityBOM bom,
            ItemInfo item, Properties properties) {
        Map<String, String> applicationProperties = new HashMap<>();
        extractApplicationProperties(bom, properties, applicationProperties);
        MetadataPackage metadataPackage = metadataBomToMdsPackage(bom, item.getRepoPath());
        if (metadataPackage == null) {
            return Optional.empty();
        }
        return Optional.of(new MetadataApplicationEntity(
                MetadataEventEntity.PACKAGE_CREATED,
                metadataPackage,
                applicationProperties));
    }

    public static void extractApplicationProperties(MutableMetadataEntityBOM bom, Properties properties,
            Map<String, String> applicationProperties) {
        for (String key : APPLICATION_SERVICE_PROPERTIES_NAMES) {
            extractApplicationProperty(bom, properties, applicationProperties, key);
        }
    }

    private static void extractApplicationProperty(MutableMetadataEntityBOM bom, Properties properties,
            Map<String, String> applicationProperties, String propsName) {
        Set<String> propValSet = properties.get(propsName);
        String propVal =
                Optional.ofNullable(propValSet).flatMap(keys -> keys.stream().findFirst())
                        .orElse(null);
        if (propVal != null) {
            bom.getUserProperties().remove(propsName);
            applicationProperties.put(propsName, propVal);
            log.debug("Extracted application property {} from properties succeeded", propsName);
        } else {
            log.debug("Extracted application property {} from properties failed", propsName);
        }
    }

    Optional<MetadataPackage> createMdsPackageEntity(RepoPath repoPath) {
        Optional<ItemInfo> itemInfo = loadItem(repoPath);
        return itemInfo.map(this::createMdsPackage);
    }

    MetadataStatistics getMdsItemStatistics(Map<RepoPath, MetadataStats> pathToDownloadCount) {
        List<MetadataItemStatistics> itemStatistics = pathToDownloadCount.entrySet()
                .stream()
                .filter(e -> leadArtifactFacade.isPackageConsumptionLead(e.getKey()))
                .filter(e -> e.getValue().downloadCount() > 0)
                .map(e -> new MetadataItemStatistics(e.getKey().toPath(), e.getValue().downloadCount(),
                        e.getValue().lastDownloaded(), e.getValue().remoteLastDownloaded(), e.getValue().force()))
                .collect(Collectors.toList());
        return new MetadataStatistics(itemStatistics);
    }

    List<RepoType> getSupportedRepoTypes() {
        return new ArrayList<>(metadataProvidersMap.keySet());
    }

    Optional<LocalRepoDescriptor> getLocalOrCacheRepoDescriptor(String repoKey) {
        LocalRepoDescriptor repoDescriptor = repositoryService.localFederatedOrCachedRepoDescriptorByKey(repoKey);
        if (repoDescriptor == null) {
            log.debug("Unable to associate Metadata event with a local or cache repository.");
            return Optional.empty();
        }
        return Optional.of(repoDescriptor);
    }

    public MetadataVersionRepo createPathDeletedEntity(RepoPath repoPath) {
        String path = repoPath.getPath();

        // if repoPath is a directory, we want to send it with a trailing '/' to metadata
        // if repoPath is only the repoKey, we want to make sure not to send a trailing '/' to metadata
        if (repoPath.isFolder() && !path.isEmpty()) {
            path = PathUtils.addTrailingSlash(path);
        }
        // The version repo is the deleted metadata entity
        return new MetadataVersionRepo(resolveRepoKey(repoPath.getRepoKey()), path);
    }

    /**
     * If the repo is a remote cache, return the remote repo key; otherwise return the original key.
     */
    String resolveRepoKey(String repoKey) {
        return resolveRepoKey(repoKey, repositoryService.getRepoConfigByKey(repoKey));
    }

    /**
     * If the config is a remote cache, return the remote repo key; otherwise return the original key.
     */
    public static String resolveRepoKey(String repoKey, RepoConfig config) {
        if (config instanceof LocalCacheRepoConfig cacheConfig) {
            return cacheConfig.getRemoteRepoConfig().getKey();
        }
        return repoKey;
    }

    private Optional<AbstractMetadataProvider> metadataProviderForItem(String repoKey) {
        Optional<LocalRepoDescriptor> repoDescriptor = getLocalOrCacheRepoDescriptor(repoKey);
        if (repoDescriptor.isPresent()) {
            return Optional.ofNullable(metadataProvidersMap.get(repoDescriptor.get().getType()));
        }
        log.debug("Metadata provider not found for repo '{}'", repoKey);
        return Optional.empty();
    }

    @Nonnull
    private StatsService statsService() {
        return ContextHelper.get().beanForType(StatsService.class);
    }

    private boolean isDeleteEvent(EventType type) {
        return type.equals(EventType.delete);
    }

    private Optional<ItemInfo> loadItem(RepoPath repoPath) {
        try {
            return Optional.of(fileService.loadItem(repoPath));
        } catch (RuntimeException e) {
            log.debug("Unable to find item with path '{}'. Metadata event handling will be skipped.",
                    repoPath.toPath());
            return Optional.empty();
        }
    }

    @Nullable
    private MetadataPackage createMdsPackage(ItemInfo item) {
        Properties properties = propertiesService.getProperties(item);
        Optional<MutableMetadataEntityBOM> mutableMetadataBOM =
                createMutableMetadataEntityBOM(item, properties, getStatsIfNeeded(item));
        return mutableMetadataBOM
                .map(mutableMetadataEntityBOM -> metadataBomToMdsPackage(mutableMetadataEntityBOM, item.getRepoPath()))
                .orElse(null);
    }

    public StatsInfo getStatsIfNeeded(ItemInfo item) {
        if (metadataEventService.isMigrating()) {
            return statsService().getStats((FileInfo) item);
        }
        return null;
    }

    @Nonnull
    public Optional<String> getPackageId(@Nonnull String repoPath) {
        return getMetadataBom(repoPath).map(MutableMetadataEntityBOM::getPkgid);
    }

    @Nonnull
    public Optional<MutableMetadataEntityBOM> getMetadataBom(@Nonnull String repoPathRaw) {
        RepoPath repoPath = RepoPathFactory.create(repoPathRaw);
        try {
            return ofNullable(repositoryService.getLocalRepository(repoPath).getImmutableFsItem(repoPath))
                    .flatMap(item -> createMutableMetadataEntityBOM(item.getInfo(), item.getProperties(), null));
        } catch (Exception e) {
            log.debug("Failed to get metadata for {}: {}", repoPathRaw, e.getMessage());
            return Optional.empty();
        }
    }

    @Nonnull
    public Optional<MutableMetadataEntityBOM> createMutableMetadataEntityBOM(
            ItemInfo itemInfo,
            Properties properties,
            @Nullable StatsInfo stats) {
        Optional<AbstractMetadataProvider> metadataProvider = metadataProviderForItem(itemInfo.getRepoKey());
        if (metadataProvider.isPresent()) {
            MutableMetadataEntityBOM metadataBom = new MutableMetadataEntityBOM();
            metadataProvider.get().supplement(metadataBom, (FileInfo) itemInfo, properties, stats);
            return Optional.of(metadataBom);
        }
        return Optional.empty();
    }

    public MetadataPackage metadataBomToMdsPackage(MutableMetadataEntityBOM mutableMetadataBOM,
            RepoPath repoPath) {
        MetadataPackage metadataPackage = metadataEntityMapper.metadataBomToMdsPackage(mutableMetadataBOM);
        addVersionFromBom(mutableMetadataBOM, metadataPackage);
        addFilesFromBom(mutableMetadataBOM, metadataPackage);
        if (!validPackageEvent(metadataPackage, repoPath)) {
            if (log.isDebugEnabled()) {
                log.debug("Ignoring invalid metadata event for path '{}'", repoPath.toPath());
            }
            return null;
        }
        return metadataPackage;
    }

    private void addFilesFromBom(MutableMetadataEntityBOM mutableMetadataBOM, MetadataPackage metadataPackage) {
        if (metadataPackage.getVersions().getFirst().getFiles() != null) {
            metadataPackage.getVersions().getFirst().getFiles().addAll(mdsFilesFromBomArtifacts(mutableMetadataBOM));
        }
    }

    private void addVersionFromBom(MutableMetadataEntityBOM mutableMetadataBOM, MetadataPackage metadataPackage) {
        List<MetadataVersion> versions = metadataPackage.getVersions();
        if (versions != null) {
            versions.add(metadataEntityMapper.metadataBomToMdsVersion(mutableMetadataBOM));
        }
    }

    private boolean validPackageEvent(MetadataPackage metadataPackage, RepoPath repoPath) {
        if (StringUtils.isBlank(metadataPackage.getPkgid())) {
            if (log.isDebugEnabled()) {
                log.debug("pkgid is empty for {}", repoPath.toPath());
            }
            return false;
        }
        if (metadataPackage.getVersions().isEmpty()) {
            if (log.isDebugEnabled()) {
                log.debug("Versions array is empty for {}", repoPath.toPath());
            }
            return false;
        }
        if (StringUtils.isBlank(metadataPackage.getVersions().getFirst().getName())) {
            if (log.isDebugEnabled()) {
                log.debug("Version name is empty for {}", repoPath.toPath());
            }
            return false;
        }
        return true;
    }

    private List<MetadataFile> mdsFilesFromBomArtifacts(MutableMetadataEntityBOM mutableMetadataBOM) {
        return mutableMetadataBOM.getArtifactMetadata().stream()
                .map(metadataEntityMapper::metadataBomToMdsFile)
                .collect(Collectors.toList());
    }
}
