/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.repo.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.jfrog.base.util.Closeables;
import com.jfrog.build.acl.bridge.BuildFacade;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.experimental.Delegate;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.HaAddon;
import org.artifactory.addon.WebstartAddon;
import org.artifactory.addon.curation.packages.PackagesCurationCompliantVersionService;
import org.artifactory.addon.federated.FederatedRepoAddon;
import org.artifactory.addon.replication.ReplicationAddon;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.common.MoveMultiStatusHolder;
import org.artifactory.api.common.RepoBulkRemovalReport;
import org.artifactory.api.common.RepoRemovalStatusHolder;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.config.ExportSettingsImpl;
import org.artifactory.api.config.ImportSettingsImpl;
import org.artifactory.api.config.InternalRepositoryConfigService;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.config.RepositoryImportSettingsImpl;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.events.EventLogHintManager;
import org.artifactory.api.jackson.JacksonReader;
import org.artifactory.api.maven.MavenMetadataService;
import org.artifactory.api.maven.MavenMetadataWorkItem;
import org.artifactory.api.module.ModuleInfo;
import org.artifactory.api.module.VersionUnit;
import org.artifactory.api.repo.ArchiveFileContent;
import org.artifactory.api.repo.AsyncDeleteWorkItem;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.repo.ResearchService;
import org.artifactory.api.repo.exception.FileExpectedException;
import org.artifactory.api.repo.exception.FolderExpectedException;
import org.artifactory.api.repo.exception.ItemNotFoundRuntimeException;
import org.artifactory.api.repo.exception.RepoRejectException;
import org.artifactory.api.repo.storage.FolderSummaryInfo;
import org.artifactory.api.request.InternalArtifactoryRequest;
import org.artifactory.api.request.UploadService;
import org.artifactory.api.rest.constant.RepositoriesRestConstants;
import org.artifactory.api.rest.constant.RestConstants;
import org.artifactory.api.search.SavedSearchResults;
import org.artifactory.api.search.VersionSearchResults;
import org.artifactory.api.search.deployable.VersionUnitSearchControls;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.api.storage.StorageQuotaInfo;
import org.artifactory.aql.result.AqlJsonMultiStreamer;
import org.artifactory.aql.result.AqlJsonRemoteStreamer;
import org.artifactory.async.Async;
import org.artifactory.binary.headers.BinaryHeaders;
import org.artifactory.checksum.ChecksumInfo;
import org.artifactory.checksum.ChecksumType;
import org.artifactory.checksum.ChecksumsInfo;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.common.status.StatusEntry;
import org.artifactory.common.status.StatusHolder;
import org.artifactory.config.CentralConfigKey;
import org.artifactory.config.InternalCentralConfigService;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.property.PropertySet;
import org.artifactory.descriptor.repo.FederatedRepoDescriptor;
import org.artifactory.descriptor.repo.HttpRepoDescriptor;
import org.artifactory.descriptor.repo.KeyPairDescriptor;
import org.artifactory.descriptor.repo.LocalCacheRepoDescriptor;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.ProxyDescriptor;
import org.artifactory.descriptor.repo.RemoteRepoDescriptor;
import org.artifactory.descriptor.repo.RepoBaseDescriptor;
import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.descriptor.repo.RepoLayout;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.descriptor.repo.SupportBundleRepoDescriptor;
import org.artifactory.descriptor.repo.TrashRepoDescriptor;
import org.artifactory.descriptor.repo.VirtualRepoDescriptor;
import org.artifactory.descriptor.repo.VirtualRepoResolver;
import org.artifactory.descriptor.repo.releasebundle.ReleaseBundlesRepoDescriptor;
import org.artifactory.descriptor.security.sshserver.SshServerSettings;
import org.artifactory.entitlement.EntitlementType;
import org.artifactory.exception.CancelException;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.exception.http.ForbiddenException;
import org.artifactory.exception.http.NotFoundException;
import org.artifactory.factory.InfoFactoryHolder;
import org.artifactory.federation.exceptions.FederatedRemoteMembersUpdateException;
import org.artifactory.fs.FileInfo;
import org.artifactory.fs.FolderInfo;
import org.artifactory.fs.ItemInfo;
import org.artifactory.fs.MutableFileInfo;
import org.artifactory.fs.RepoResource;
import org.artifactory.fs.StatsInfo;
import org.artifactory.fs.ZipEntryInfo;
import org.artifactory.importexport.ImportIsDisabledException;
import org.artifactory.info.InfoWriter;
import org.artifactory.io.StringResourceStreamHandle;
import org.artifactory.mbean.MBeanRegistrationServiceImpl;
import org.artifactory.md.Properties;
import org.artifactory.metadata.service.MetadataEventService;
import org.artifactory.mime.NamingUtils;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.LocalCacheRepoConfig;
import org.artifactory.model.LocalConfig;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RealRepoConfig;
import org.artifactory.model.ReleaseBundleRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.RepoConfigCopyFactory;
import org.artifactory.model.SpecialAction;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.opentracing.ArtifactoryTracer;
import org.artifactory.ph.PackageSpecification;
import org.artifactory.projects.ProjectsService;
import org.artifactory.projects.ProjectsUtils;
import org.artifactory.repo.HttpRepo;
import org.artifactory.repo.HttpRepoFactory;
import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.repo.LocalCacheRepo;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.RealRepo;
import org.artifactory.repo.ReferencedConfigChange;
import org.artifactory.repo.ReleaseBundlesRepo;
import org.artifactory.repo.RemoteRepo;
import org.artifactory.repo.Repo;
import org.artifactory.repo.RepoBase;
import org.artifactory.repo.RepoConfigChangeReload;
import org.artifactory.repo.RepoConfigChangesReload;
import org.artifactory.repo.RepoDetails;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.RepoRepoPath;
import org.artifactory.repo.SaveResourceContext;
import org.artifactory.repo.StoringRepo;
import org.artifactory.repo.cleanup.FolderPruningService;
import org.artifactory.repo.db.DbCacheRepo;
import org.artifactory.repo.db.DbLocalRepo;
import org.artifactory.repo.db.importexport.DbRepoExportSearchHandler;
import org.artifactory.repo.federated.FederatedRepo;
import org.artifactory.repo.interceptor.StorageInterceptors;
import org.artifactory.repo.local.PathDeletionContext;
import org.artifactory.repo.local.ValidDeployPathContext;
import org.artifactory.repo.mbean.ManagedRepository;
import org.artifactory.repo.service.deploy.ArtifactoryDeployRequest;
import org.artifactory.repo.service.deploy.ArtifactoryDeployRequestBuilder;
import org.artifactory.repo.service.flexible.context.MoveCopyContext;
import org.artifactory.repo.service.flexible.interfaces.FlatMoveCopyService;
import org.artifactory.repo.service.mover.CopyRepoPathService;
import org.artifactory.repo.service.mover.MoveRepoPathService;
import org.artifactory.repo.service.mover.MoverConfig;
import org.artifactory.repo.service.mover.MoverConfigBuilder;
import org.artifactory.repo.service.mover.RepoPathMover;
import org.artifactory.repo.service.versioning.RepositoriesCache;
import org.artifactory.repo.system.SystemFolders;
import org.artifactory.repo.system.SystemRepoConfigFactory;
import org.artifactory.repo.system.SystemRepos;
import org.artifactory.repo.system.suppliers.SupportBundleRepo;
import org.artifactory.repo.system.suppliers.TrashRepo;
import org.artifactory.repo.virtual.VirtualRepo;
import org.artifactory.request.InternalArtifactoryResponse;
import org.artifactory.request.InternalRequestContext;
import org.artifactory.request.NullRequestContext;
import org.artifactory.request.RepoRequests;
import org.artifactory.resource.FileResource;
import org.artifactory.resource.ResolvedResource;
import org.artifactory.resource.ResourceStreamHandle;
import org.artifactory.resource.UnfoundRepoResource;
import org.artifactory.sapi.common.BaseSettings;
import org.artifactory.sapi.common.ExportSettings;
import org.artifactory.sapi.common.ImportSettings;
import org.artifactory.sapi.common.RepositoryRuntimeException;
import org.artifactory.sapi.fs.VfsFile;
import org.artifactory.sapi.fs.VfsFolder;
import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.sapi.interceptor.context.DeleteContext;
import org.artifactory.schedule.Task;
import org.artifactory.schedule.TaskCallback;
import org.artifactory.schedule.TaskService;
import org.artifactory.schedule.TaskUtils;
import org.artifactory.schedule.monitor.OnlineMonitorService;
import org.artifactory.search.InternalSearchService;
import org.artifactory.security.AccessLogger;
import org.artifactory.security.Acl;
import org.artifactory.security.ArtifactoryPermission;
import org.artifactory.security.AuthenticationHelper;
import org.artifactory.security.MutableAcl;
import org.artifactory.security.MutableReleaseBundlePermissionTarget;
import org.artifactory.security.MutableRepoPermissionTarget;
import org.artifactory.security.PermissionTarget;
import org.artifactory.security.ReleaseBundleAcl;
import org.artifactory.security.ReleaseBundlePermissionTarget;
import org.artifactory.security.RepoAcl;
import org.artifactory.security.RepoPermissionTarget;
import org.artifactory.security.access.AccessService;
import org.artifactory.service.SmartRepoService;
import org.artifactory.sh.acl.ArtifactoryBinaryService;
import org.artifactory.sh.acl.BinaryHeadersUtil;
import org.artifactory.spring.EntitlementsEventListener;
import org.artifactory.spring.InternalContextHelper;
import org.artifactory.spring.Reloadable;
import org.artifactory.storage.DBEntityNotFoundException;
import org.artifactory.storage.StorageService;
import org.artifactory.storage.db.fs.service.RepositoryPersistingService;
import org.artifactory.storage.db.locks.service.DbLocksService;
import org.artifactory.storage.db.security.service.VersioningCacheImpl;
import org.artifactory.storage.fs.MutableVfsFile;
import org.artifactory.storage.fs.MutableVfsFolder;
import org.artifactory.storage.fs.MutableVfsItem;
import org.artifactory.storage.fs.Sha256ConvertableMutableVfsFile;
import org.artifactory.storage.fs.VfsException;
import org.artifactory.storage.fs.VfsItemFactory;
import org.artifactory.storage.fs.service.FileService;
import org.artifactory.storage.fs.service.ItemMetaInfo;
import org.artifactory.storage.fs.service.NodeMetaInfoService;
import org.artifactory.storage.fs.service.PropertiesService;
import org.artifactory.storage.fs.tree.ItemNode;
import org.artifactory.storage.fs.tree.ItemTree;
import org.artifactory.storage.fs.tree.TreeBrowsingCriteria;
import org.artifactory.storage.fs.tree.TreeBrowsingCriteriaBuilder;
import org.artifactory.storage.jobs.RepoStatsFlushJob;
import org.artifactory.storage.jobs.RepositoryConfigPersistingJob;
import org.artifactory.storage.jobs.StatsDelegatingServiceFlushJob;
import org.artifactory.storage.jobs.StatsPersistingServiceFlushJob;
import org.artifactory.storage.jobs.migration.sha256.ChecksumCalculationWorkItem;
import org.artifactory.storage.jobs.migration.sha256.Sha256CalculationFatalException;
import org.artifactory.storage.jobs.migration.sha256.Sha256MigrationJob;
import org.artifactory.storage.jobs.migration.sha256.Sha256MigrationJobDelegate;
import org.artifactory.storage.service.StatsServiceImpl;
import org.artifactory.threadlocal.cache.ReposItemsThreadLocalCacheService;
import org.artifactory.throtlling.generic.rrl.service.IRequestRateLimiterService;
import org.artifactory.util.AolUtils;
import org.artifactory.util.DoesNotExistException;
import org.artifactory.util.HttpClientConfigurator;
import org.artifactory.util.HttpUtils;
import org.artifactory.util.RepoLayoutUtils;
import org.artifactory.util.RepoPathUtils;
import org.artifactory.util.Tree;
import org.artifactory.util.ZipUtils;
import org.artifactory.version.CompoundVersionDetails;
import org.jfrog.access.common.ResourceType;
import org.jfrog.client.util.PathUtils;
import org.jfrog.common.StreamSupportUtils;
import org.jfrog.common.config.diff.DataDiff;
import org.jfrog.security.util.Pair;
import org.jfrog.storage.binstore.exceptions.BinaryNotFoundException;
import org.jfrog.storage.binstore.exceptions.BinaryRejectedException;
import org.jfrog.storage.binstore.exceptions.BinaryStorageException;
import org.jfrog.storage.binstore.ifc.BinaryServiceInputStream;
import org.jfrog.storage.binstore.ifc.model.BinaryElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StopWatch;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static ch.qos.logback.classic.Level.WARN;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Stream.concat;
import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_FAILED_DEPENDENCY;
import static org.apache.http.HttpStatus.SC_FORBIDDEN;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_REQUEST_TOO_LONG;
import static org.artifactory.addon.AddonsManager.LICENSE_READ_ONLY_MESSAGE_SUFFIX;
import static org.artifactory.api.request.ArtifactoryResponse.ARTIFACTORY_ID;
import static org.artifactory.api.security.SecurityService.USER_SYSTEM;
import static org.artifactory.checksum.ChecksumInfo.TRUSTED_FILE_MARKER;
import static org.artifactory.common.ConstantValues.lockResourceBeforeOpenTransaction;
import static org.artifactory.common.ConstantValues.locksTimeoutSecs;
import static org.artifactory.common.ConstantValues.moveCopyAggregatePathsEnabled;
import static org.artifactory.common.ConstantValues.nodeLastUpdatedThresholdMillis;
import static org.artifactory.common.ConstantValues.qrlLowPriorityUndeploy;
import static org.artifactory.descriptor.repo.RepoType.Ansible;
import static org.artifactory.descriptor.repo.RepoType.BuildInfo;
import static org.artifactory.descriptor.repo.RepoType.Docker;
import static org.artifactory.event.EventHintType.RemoveRepo;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_DELETE;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_UPDATE;
import static org.artifactory.repo.RepoDetailsType.FEDERATED;
import static org.artifactory.repo.RepoDetailsType.LOCAL;
import static org.artifactory.repo.RepoDetailsType.REMOTE;
import static org.artifactory.repo.RepoDetailsType.VIRTUAL;
import static org.artifactory.repo.db.importexport.SafePathUtils.convertToSafeName;
import static org.artifactory.repo.service.ArtifactDeleteFailStatus.ARTIFACT_NOT_FOUND;
import static org.artifactory.repo.service.ArtifactDeleteFailStatus.CANCELED_BY_PLUGIN;
import static org.artifactory.repo.service.ArtifactDeleteFailStatus.ITEM_NOT_FOUND;
import static org.artifactory.repo.service.ArtifactDeleteFailStatus.REPOSITORY_NOT_FOUND;
import static org.artifactory.repo.service.ArtifactDeleteFailStatus.REQUIRES_DELETE_PERMISSION;
import static org.artifactory.repo.service.RepositoryConfigServiceHelper.isHiddenRepo;
import static org.artifactory.repo.service.RepositoryConfigServiceHelper.isInfoRepo;
import static org.artifactory.repo.system.SystemFolders.isUnderSystemFolder;
import static org.artifactory.repo.system.SystemRepos.EDGE_UPLOADS_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.FULL_SYNC_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.IN_TRANSIT_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.SUPPORT_BUNDLE_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.SystemRepoConfig.EDGE_UPLOADS;
import static org.artifactory.repo.system.SystemRepos.TRASH_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.USAGE_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.isSupportBundle;
import static org.artifactory.throtlling.qrl.enums.QueryRateLimiterType.LOW_PRIORITY;
import static org.artifactory.throtlling.qrl.enums.QueryRateLimiterType.SYSTEM;
import static org.artifactory.throttling.qrl.service.QueryRateLimiterServiceImpl.withQueryRateLimiter;
import static org.artifactory.util.RepoPathUtils.isTrash;
import static org.artifactory.util.RepoPathUtils.removeDashCache;
import static org.artifactory.util.StopWatchUtil.measureAndLogTime;
import static org.artifactory.util.StopWatchUtil.measureTime;

@Service
@Reloadable(beanClass = InternalRepositoryService.class,
        initAfter = {StorageInterceptors.class, InternalCentralConfigService.class, TaskService.class,
                RepositoryPersistingService.class},
        listenOn = {
                CentralConfigKey.propertySets,
                CentralConfigKey.offlineMode,
                CentralConfigKey.xrayConfig,
                CentralConfigKey.keyPairs
        })
public class RepositoryServiceImpl implements InternalRepositoryService, EntitlementsEventListener {
    public static final RepoPath IN_TRANSIT_REPO_PATH =
            RepoPathFactory.create(IN_TRANSIT_REPO_KEY, "");
    private static final String DELETION_NOT_ALLOWED_MSG = "Deletion not allowed for path {}, skipping it...";
    private static final Logger log = LoggerFactory.getLogger(RepositoryServiceImpl.class);
    private static final String REPOSITORIES_MBEAN_TYPE = "Repositories";
    private static final String USER = "User ";
    private final Set<String> externalSystemRepos = new HashSet<>();
    VersioningCacheImpl<RepositoriesCache> cache;
    private final ReentrantLock reposByFirstCharMapLock = new ReentrantLock();
    private final AtomicReference<Map<Character, List<String>>> cachedReposByFirstCharMap = new AtomicReference<>();
    LoadingCache<String, Boolean> hostsCache;
    @Autowired
    private AuthorizationService authService;
    @Autowired
    private CentralConfigService centralConfigService;
    private TaskService taskService;

    @Autowired
    private MavenMetadataService mavenMetadataService;

    @Autowired
    private InternalSearchService searchService;

    @Autowired
    private AddonsManager addonsManager;

    @Autowired
    private IRequestRateLimiterService rateLimiterService;

    private OnlineMonitorService onlineMonitorService;

    @Autowired
    private UploadService uploadService;

    @Autowired
    private DbLocksService dbLocksService;

    @Autowired
    private StorageService storageService;

    @Autowired
    private StatsServiceImpl statsService;

    @Autowired
    private FileService fileService;
    @Autowired
    private ArtifactoryBinaryService binaryService;
    @Autowired
    private FolderPruningService pruneService;
    @Autowired
    private FlatMoveCopyService flatMoveCopyService;
    @Autowired
    private SecurityService securityService;
    @Autowired
    private BuildFacade buildFacade;
    @Autowired
    private ResearchService researchService;
    @Autowired
    private ProjectsService projectsService;
    @Autowired
    private SmartRepoService smartRepoService;
    @Autowired
    private ArtifactoryTracer tracer;
    @Autowired
    private RepositoryServiceHelper repoServiceHelper;
    @Autowired
    private PropertiesService propertiesService;
    @Autowired
    private StorageInterceptors storageInterceptors;
    @Autowired
    private RemoveRepositoryService removeRepositoryService;
    @Autowired
    private ZapService zapService;
    private MetadataEventService metadataEventService;
    //Holds providers for specialized remote repos
    private Map<RepoType, HttpRepoFactory> remoteRepoProviders;
    private CloseableHttpClient client;
    @Setter(onMethod_ = {@Autowired})
    private PackageSpecification packageSpecification;
    @Delegate(excludes = ExcludeRepoConfigMethods.class)
    @Autowired
    private RepositoryConfigService repositoryConfigService;
    @Autowired
    private AccessService accessService;

    @Setter(onMethod_ = {@Autowired})
    private PackagesCurationCompliantVersionService curationCompliantVersionService;

    @SuppressWarnings("unused")
    private interface ExcludeRepoConfigMethods {
        RepoConfig getRepoConfigByKey(String repoKey);
        boolean existsByType(RepoDetailsType repoType, String repoKey);
    }

    @Override
    public void removeDanglingChildRefFromVirtual(String virtualRepoKey, String childRepoKey,
            @Nullable RepoConfig removedRepo) {
        repositoryConfigService.removeDanglingChildRefFromVirtual(virtualRepoKey, childRepoKey, removedRepo);
    }

    private static InternalRepositoryService getTransactionalMe() {
        return InternalContextHelper.get().beanForType(InternalRepositoryService.class);
    }

    void initClient() {
        ProxyDescriptor proxy = InternalContextHelper.get().getCentralConfig().getDescriptor().getDefaultProxy();
        HttpClientConfigurator configurator = new HttpClientConfigurator();
        configurator.socketTimeout(15000).connectionTimeout(15000).retry(0, false);
        client = configurator.proxy(proxy).build();
    }

    @Override
    public Properties getRepoProperties(String repoKey) {
        RepoPath repoPath = RepoPathUtils.repoRootPath(repoKey);
        return propertiesService.getProperties(repoPath);
    }

    @Override
    public int countFiles(String repoKey, String fileName) {
        int result = -1;
        log.debug("Fetching number of images in repoKey:{} ", repoKey);
        long startTime = System.currentTimeMillis();
        try {
            result = fileService.getFileCount(repoKey, fileName);
        } catch (Exception e) {
            log.error("Error during fetching number of images of repository:{} due to:{}", repoKey, e.getMessage());
            log.error("", e);
        }
        log.debug("Finished fetching number of images from:{}, time took:{} millis", repoKey,
                System.currentTimeMillis() - startTime);
        return result;
    }

    @Override
    public void init() {
        remoteRepoProviders = InternalContextHelper.get().beansForType(HttpRepoFactory.class).values()
                .stream()
                .collect(Collectors.toMap(HttpRepoFactory::getRepoType, Function.identity()));
        if (cache == null) {
            long timeout = ConstantValues.repositoriesDirtyReadsTimeoutMillis.getLong();
            cache = new VersioningCacheImpl<>(timeout, new RepositoriesCacheLoader(), "repositoriesCache");
        }
        HttpUtils.resetArtifactoryUserAgent();

        try {
            //Dump info to the log
            new InfoWriter(AolUtils.isAol()).writeInfo();
        } catch (Exception e) {
            log.warn("Failed dumping system info", e);
        }

        // register internal statistics flushing job
        Task localStatsFlushTask = TaskUtils.createRepeatingTask(StatsPersistingServiceFlushJob.class,
                SECONDS.toMillis(ConstantValues.statsFlushIntervalSecs.getLong()),
                SECONDS.toMillis(ConstantValues.statsFlushIntervalSecs.getLong()));

        // register remote statistics flushing job
        Task remoteFlushTask = TaskUtils.createRepeatingTask(StatsDelegatingServiceFlushJob.class,
                SECONDS.toMillis(ConstantValues.statsRemoteFlushIntervalSecs.getLong()),
                SECONDS.toMillis(ConstantValues.statsRemoteFlushIntervalSecs.getLong()));

        // register repository configurations persistence job
        long persistingIntervalMillis = getRepoConfigBackupIntervalMillis();
        Task repoConfigPersistingTask =
                TaskUtils.createRepeatingTask(RepositoryConfigPersistingJob.class, persistingIntervalMillis, 0,
                        "repository configurations persistence job", true);

        taskService.startTask(localStatsFlushTask, false);
        taskService.startTask(remoteFlushTask, false);
        taskService.startTask(repoConfigPersistingTask, false);

        if (ConstantValues.repoStatsEnabled.getBoolean()) {
            // register repository statistics flushing job
            Task reposStatsFlushTask = TaskUtils.createRepeatingTask(RepoStatsFlushJob.class,
                    SECONDS.toMillis(ConstantValues.repoStatsFlushIntervalSecs.getLong()),
                    SECONDS.toMillis(ConstantValues.repoStatsFlushIntervalSecs.getLong()));
            taskService.startTask(reposStatsFlushTask, false);
        }

        if (!ConstantValues.disableGlobalRepoAccess.getBoolean()) {
            log.warn("The global virtual repository 'repo' has been deprecated, requests to '/repo' will be treated " +
                    "normally.");
        }
        initClient();
        initExternalSystemRepos();
    }

    private static long getRepoConfigBackupIntervalMillis() {
        int backupIntervalMinutes = ConstantValues.repoConfigBackupIntervalMinutes.getInt();
        if (backupIntervalMinutes > 0) {
            return TimeUnit.MINUTES.toMillis(backupIntervalMinutes);
        }
        return ConstantValues.repoConfigBackupInternalMillis.getInt();
    }

    @VisibleForTesting
    void initExternalSystemRepos() {
        externalSystemRepos.addAll(ConstantValues.externalSystemRepositoriesList.getList());
    }

    private void initHostsCache() {
        hostsCache = CacheBuilder.newBuilder()
                .initialCapacity(1000)
                .maximumSize(10000)
                .expireAfterWrite(ConstantValues.httpRepoHostsCacheEvictionTime.getLong(), TimeUnit.MINUTES)
                .build(new CacheLoader<>() {
                    @Override
                    public Boolean load(@Nonnull String key) {
                        return isInetAddressLoopback(key);
                    }
                });
    }

    private boolean isInetAddressLoopback(String host) {
        try {
            InetAddress inetAddress = InetAddress.getByName(host);
            return inetAddress != null && inetAddress.isLoopbackAddress();
        } catch (Exception e) {
            log.error("Unable to find Inet address of host {} because of the following: {}", host, e.getMessage(), e);
            return false;
        }
    }

    @Override
    public void onRepoConfigReload(RepoConfigChangesReload repoConfigChangesReload) {
        HttpUtils.resetArtifactoryUserAgent();
        List<RepoConfigChangeReload> allChanges = repoConfigChangesReload.getAll();
        deleteOrphanRepos(allChanges);
        checkAndCleanChangedVirtualPomCleanupPolicy(allChanges);
        onPropagatedRepoConfigReload(repoConfigChangesReload);
    }

    /**
     * Deletes all system folders in the given repositories.
     * Temporary method to be moved to: {@link DeleteRepositoryContentService} once
     * {@link ConstantValues#removeRepositoryFlowV2Enabled} feature flag is removed
     */
    private void deleteSystemFolders(String repoKey, UndeployContext undeployContext) {
        UndeployContext deleteSysFoldersUndeployContext = new UndeployContext(undeployContext.getStatusHolder(),
                undeployContext.isShouldProtectPathDeletion(),
                System.currentTimeMillis(), false, undeployContext.isFullRepoContentDeletion());
        Set<SystemFolders.SystemFolder> systemFolders = SystemFolders.ALL_SYSTEM_FOLDERS;
        for (SystemFolders.SystemFolder systemFolder : systemFolders) {
            deleteSystemFolder(repoKey, systemFolder, deleteSysFoldersUndeployContext);
        }
    }

    private void deleteSystemFolder(String repoKey, SystemFolders.SystemFolder systemFolder,
            UndeployContext undeployContext) {
        RepoPath systemFolderPath = RepoPathFactory.create(repoKey, systemFolder.getFolderName());
        boolean childrenDeleted = deleteChildren(undeployContext, systemFolderPath);
        if (!childrenDeleted) {
            log.warn("Failed to delete children of system folder: '{}' in repository: '{}'.",
                    systemFolder.getFolderName(), repoKey);
        }
        boolean systemFolderDeleted = deleteSystemFolderItem(undeployContext, systemFolderPath);
        if (!systemFolderDeleted) {
            log.warn("Failed to delete system folder: '{}' in repository: '{}'.", systemFolder.getFolderName(),
                    repoKey);
        }
    }

    @Override
    public void onPropagatedRepoConfigReload(RepoConfigChangesReload configChangesReload) {
        List<String> remoteRepoKeysToUpdateOrDelete = extractRemoteRepoKeysToUpdateOrDelete(configChangesReload);
        stopRemoteReposMonitoringAndPromoteCache(remoteRepoKeysToUpdateOrDelete);
    }

    @Override
    public void reloadOnReferencedConfigChange(List<ReferencedConfigChange> affectedRepos) {
        // don't need to clean up resources since no repos have been deleted
        cache.promoteVersion();
    }

    @Override
    public void reload(CentralConfigDescriptor oldDescriptor, List<DataDiff<?>> configDiff) {
        HttpUtils.resetArtifactoryUserAgent();
    }

    @Override
    @Async(authenticateAsSystem = true)
    public void onContextReady() {
        registerRepositoriesMBeans();
    }

    @Override
    public void onContextCreated() {
        startSha2CalculationJob();
    }

    private void stopRemoteReposMonitoringAndPromoteCache(List<String> remoteRepoKeysToDelete) {
        remoteRepoKeysToDelete.forEach(onlineMonitorService::stopMonitorForRepo);
        cache.promoteVersion();
    }

    void checkAndCleanChangedVirtualPomCleanupPolicy(List<RepoConfigChangeReload> repoConfigReloads) {
        repoConfigReloads.stream()
                .filter(repoConfigReload -> repoConfigReload.getSpecialAction() ==
                        SpecialAction.PERFORM_POM_CLEANUP_POLICY)
                .forEach(repoConfigReload -> {
                    VirtualRepo virtualRepo = virtualRepositoryByKey(repoConfigReload.getRepoKey());
                    if (virtualRepo != null) {
                        log.info("Pom Repository Reference Cleanup Policy changed in '{}', cleaning repository cache. ",
                                repoConfigReload.getRepoKey());
                        RepoPath rootPath = InternalRepoPathFactory.repoRootPath(repoConfigReload.getRepoKey());
                        virtualRepo.undeploy(new DeleteContext(rootPath));
                    } else {
                        log.warn("Unable to cleanup poms from the non-existing virtual repository '{}'",
                                repoConfigReload.getRepoKey());
                    }
                });
    }

    /**
     * Temporary method to be moved to: {@link RemoveRepositoryService} once
     * {@link ConstantValues#removeRepositoryFlowV2Enabled} feature flag is removed
     * @param repoConfigReloads
     */
    void deleteOrphanRepos(List<RepoConfigChangeReload> repoConfigReloads) {
        repoConfigReloads.stream()
                .filter(repoConfigReload -> repoConfigReload.getConfigAction()
                        .equals(REPO_DELETE))
                .forEach(repoConfigReload -> {
                    log.debug("Removing the no-longer-referenced repository {}", repoConfigReload.getRepoKey());
                    unregisterMBeans(repoConfigReload);
                    StatusHolder statusHolder = deleteOrphanRepo(repoConfigReload.getRepoKey());
                    if (statusHolder.isError()) {
                        log.warn("Error occurred during repo '{}' removal: {}", repoConfigReload.getRepoKey(),
                                statusHolder.getStatusMsg());
                    }
                });
    }

    private void unregisterMBeans(RepoConfigChangeReload repoConfigReload) {
        if (repoConfigReload.getRepoType() == REMOTE) {
            log.debug("Removing the no-longer-referenced connection pool of repository {}",
                    repoConfigReload.getRepoKey());
            MBeanRegistrationServiceImpl
                    .unregister(MBeanRegistrationServiceImpl.createObjectName("HTTPConnectionPool",
                            repoConfigReload.getRepoKey()));
            if (repoConfigReload.getPackageType() == Docker || repoConfigReload.getPackageType() == Ansible) {
                MBeanRegistrationServiceImpl
                        .unregister(MBeanRegistrationServiceImpl.createObjectName(
                                "HTTPConnectionPool", repoConfigReload.getRepoKey()
                                        + "-token-client"));
            }
        }
    }

    //TORE: [by YS] delete from the db directly - there's no need for permissions checks, events etc.
    private StatusHolder deleteOrphanRepo(String repoKey) {
        deleteRepoFromAllRelatedRepoAcls(repoKey);
        BasicStatusHolder status = new BasicStatusHolder();
        StoringRepo<?, ?> storingRepo = storingRepositoryByKey(repoKey);
        if (storingRepo == null) {
            status.status("Repo not found for deletion: " + repoKey, log);
            return status;
        } else if (storingRepo instanceof ReleaseBundlesRepo) {
            deleteRepoFromAllRelatedReleaseBundleAcls(repoKey);
        }
        RepoPath rootPath = storingRepo.getRepoPath("");
        MutableVfsFolder rootFolder = storingRepo.getMutableFolder(rootPath);
        if (rootFolder == null) {
            status.warn("Root folder not found for deletion: " + repoKey, log);
            return status;
        }
        rootFolder.deleteIncludingRoot();
        metadataEventService.deletePathFromMetadataServerUsingMetadataClient(rootPath);
        return status;
    }

    @VisibleForTesting
    void deleteRepoFromAllRelatedRepoAcls(String repoKey) {
        //Delete all acl references to the repository being deleted
        if (ConstantValues.enableAccessClientDeleteRepoFromPermission.getBoolean()) {
            accessService.getAccessClient().notifications().deleteResource(ResourceType.REPO, repoKey);
            return;
        }

        List<RepoAcl> acls = authService.getAllRepoAcls();
        for (RepoAcl repoAcl : acls) {
            MutableRepoPermissionTarget permissionTarget = InfoFactoryHolder.get().copyRepoPermissionTarget
                    (repoAcl.getPermissionTarget());
            String cachedRepoKey = repoKey.concat(LocalCacheRepoDescriptor.PATH_SUFFIX); //for remote repos
            Set<String> repoKeys = permissionTarget.getRepoKeys();
            if (repoKeys.remove(repoKey) || repoKeys.remove(cachedRepoKey)) {
                MutableAcl<RepoPermissionTarget> mutableAclInfo = InfoFactoryHolder.get().copyRepoAcl(repoAcl);
                permissionTarget.setRepoKeys(repoKeys);
                mutableAclInfo.setPermissionTarget(permissionTarget);
                updateAcl(mutableAclInfo);
            }
        }
    }

    private void deleteRepoFromAllRelatedReleaseBundleAcls(String repoKey) {
        List<ReleaseBundleAcl> releaseBundleAcls = authService.getAllReleaseBundleAcls(false);
        for (ReleaseBundleAcl releaseBundleAcl : releaseBundleAcls) {
            MutableReleaseBundlePermissionTarget permissionTarget = InfoFactoryHolder.get()
                    .copyReleaseBundlePermissionTarget(releaseBundleAcl.getPermissionTarget());
            Set<String> repoKeys = permissionTarget.getRepoKeys();
            if (repoKeys.remove(repoKey)) {
                MutableAcl<ReleaseBundlePermissionTarget> mutableAclInfo = InfoFactoryHolder.get()
                        .copyReleaseBundleAcl(releaseBundleAcl);
                permissionTarget.setRepoKeys(repoKeys);
                mutableAclInfo.setPermissionTarget(permissionTarget);
                updateAcl(mutableAclInfo);
            }
        }
    }

    /**
     * Call this method only with a verified admin or a verified projectAdmin as it using the effectiveAdmin=true flag.
     */
    private void updateAcl(Acl<? extends RepoPermissionTarget> acl) {
        //In case that the DB returns null (e.g. permission target not found - already deleted by another thread),
        //the repository deletion process will not be aborted
        try {
            authService.updateAcl(acl, true);
        } catch (DBEntityNotFoundException e) {
            log.warn(e.getMessage());
            log.debug("Permission target or ACL was not found in the database", e);
        }
    }

    @Override
    public void destroy() {
        List<Repo> repos = Lists.newArrayList();
        repos.addAll(getVirtualRepositories());
        repos.addAll(getLocalFederatedAndRemoteRepositories());
        for (Repo repo : repos) {
            try {
                repo.destroy();
            } catch (Exception e) {
                log.error("Error while destroying the repository '{}'.", repo, e);
            }
        }
    }

    @Override
    public void convert(CompoundVersionDetails source, CompoundVersionDetails target) {
    }

    @Override
    public List<ItemInfo> getChildrenDeeply(RepoPath path) {
        return internalGetChildrenDeeply(path, false, Integer.MAX_VALUE, new ArrayList<>());
    }

    @Override
    public List<ItemInfo> getChildrenDeeply(RepoPath path, boolean excludeDirs, int maxResults) {
        return internalGetChildrenDeeply(path, excludeDirs, maxResults, new ArrayList<>());
    }

    private List<ItemInfo> internalGetChildrenDeeply(RepoPath path, boolean excludeDirs, int maxResults,
            List<ItemInfo> results) {
        if (path == null || !hasChildren(path)) {
            return Collections.emptyList();
        }
        List<ItemInfo> children = getChildren(path);
        for (ItemInfo child : children) {
            if (results.size() >= maxResults) {
                return results;
            }
            if (!excludeDirs || !child.isFolder()) {
                results.add(child);
            }
            internalGetChildrenDeeply(child.getRepoPath(), excludeDirs, maxResults, results);
        }
        return results;
    }

    @Override
    public ModuleInfo getItemModuleInfo(RepoPath repoPath) {
        Repo repo = assertRepoKey(repoPath);
        return repo.getItemModuleInfo(repoPath.getPath());
    }

    private Repo assertRepoKey(RepoPath repoPath) {
        String repoKey = repoPath.getRepoKey();
        Repo repo = repositoryByKey(repoKey);
        if (repo == null) {
            throw new IllegalArgumentException("Repository '" + repoKey + "' not found!");
        }
        return repo;
    }

    @Override
    public boolean mkdirs(RepoPath folderRepoPath) {
        StoringRepo storingRepo = storingRepositoryByKey(folderRepoPath.getRepoKey());
        if (!storingRepo.itemExists(folderRepoPath.getPath())) {
            MutableVfsFolder folder = storingRepo.createOrGetFolder(folderRepoPath);
            return folder.isNew();
        }
        return false;
    }

    @Override
    public boolean virtualItemExists(RepoPath repoPath) {
        VirtualRepo virtualRepo = virtualRepositoryByKey(repoPath.getRepoKey());
        if (virtualRepo == null) {
            throw new RepositoryRuntimeException("Repository " + repoPath.getRepoKey() + " does not exists!");
        }
        return virtualRepo.virtualItemExists(repoPath.getPath());
    }

    @Override
    @Nonnull
    public MutableVfsItem getMutableItem(RepoPath repoPath) {
        //TORE: [by YS] should be storing repo once interfaces refactoring is done
        LocalRepo localRepo = localFederatedOrCachedRepositoryByKey(repoPath.getRepoKey());
        if (localRepo != null) {
            RepoPath resolvedRepoPath = ensureStoringRepoPathResolved(localRepo, repoPath);
            log.debug("LocalRepo has found {}. Finding mutableFsItem.", localRepo);
            MutableVfsItem<?> mutableFsItem = localRepo.getMutableFsItem(resolvedRepoPath);
            if (mutableFsItem != null) {
                return mutableFsItem;
            }
        } else {
            log.debug("Failed to get localRepo for repo path: {}, repoKey: {}", repoPath, repoPath.getRepoKey());
        }
        log.debug("Failed to get mutableFsItem for repo path: {}", repoPath);
        throw new ItemNotFoundRuntimeException(repoPath);
    }

    private MutableVfsFile getMutableFile(RepoPath repoPath) {
        MutableVfsItem mutableItem = getMutableItem(repoPath);
        if (!(mutableItem instanceof MutableVfsFile)) {
            throw new FileExpectedException(repoPath);
        }
        return (MutableVfsFile) mutableItem;
    }

    @VisibleForTesting
    VfsFile<?> getImmutableFile(RepoPath repoPath) {
        StoringRepo<?, ?> storingRepo = storingRepositoryByKey(repoPath.getRepoKey());
        VfsFile<?> immutableFile = storingRepo.getImmutableFile(repoPath);
        if (immutableFile == null) {
            throw new FileExpectedException(repoPath);
        }
        return immutableFile;
    }

    @Override
    @Nullable
    public StatsInfo getStatsInfo(FileInfo fileInfo) {
        if (forbiddenRead(fileInfo.getRepoPath())) {
            return null;
        }
        return statsService.getStats(fileInfo);
    }

    @Override
    @Nullable
    public StatsInfo getStatsInfo(RepoPath repoPath) {
        if (forbiddenRead(repoPath)) {
            return null;
        }
        return statsService.getStats(repoPath);
    }

    private boolean forbiddenRead(RepoPath repoPath) {
        if (!authService.canRead(repoPath)) {
            AccessLogger.downloadDenied(repoPath);
            return true;
        }
        return false;
    }

    @Override
    public boolean isRepoContainsFiles(String repoKey) {
        return fileService.isRepoContainsFiles(repoKey);
    }

    @Override
    public long getArtifactCount(RepoPath repoPath) {
        return fileService.getFilesCount(repoPath);
    }

    @Override
    public FolderSummaryInfo getArtifactCountAndSize(@Nonnull RepoPath repoPath) {
        return fileService.getFilesCountAndSize(repoPath);
    }

    @Override
    public long getNodesCount(RepoPath repoPath) {
        return fileService.getNodesCount(repoPath);
    }

    @Override
    public List<FileInfo> searchFilesWithBadChecksum(ChecksumType type, Set<String> repos) {
        return fileService.searchFilesWithBadChecksum(type, repos);
    }

    @Override
    @Nonnull
    public List<ItemInfo> getChildren(RepoPath repoPath) {
        return repoServiceHelper.getChildren(repoPath);
    }

    @Nonnull
    @Override
    public Stream<ItemInfo> getChildrenStream(RepoPath repoPath) {
        return fileService.getChildrenStream(repoPath);
    }

    @Nonnull
    @Override
    public Stream<ItemInfo> getNodesStreamIgnoringRepo(RepoPath repoPath) {
        return fileService.getNodesStreamIgnoringRepo(repoPath);
    }

    @Override
    public List<String> getChildrenNames(RepoPath repoPath) {
        List<ItemInfo> childrenInfo = getChildren(repoPath);
        List<String> childrenNames = Lists.newArrayListWithCapacity(childrenInfo.size());
        for (ItemInfo itemInfo : childrenInfo) {
            childrenNames.add(itemInfo.getName());
        }
        return childrenNames;
    }

    @Override
    public boolean hasChildren(RepoPath repoPath) {
        return fileService.hasChildren(repoPath);
    }

    @Override
    public void saveFileInternal(RepoPath fileRepoPath, InputStream is)
            throws RepoRejectException, IOException {
        saveFileInternal(fileRepoPath, is, -1);
    }

    @Override
    public void saveFileInternal(RepoPath fileRepoPath, InputStream is, long contentLength)
            throws RepoRejectException, IOException {
        try {
            MutableFileInfo fileInfo = InfoFactoryHolder.get().createFileInfo(fileRepoPath);
            fileInfo.createTrustedChecksums();
            SaveResourceContext.Builder builder = new SaveResourceContext.Builder(new FileResource(fileInfo), is);
            if (contentLength > 0) {
                builder.contentLength(String.valueOf(contentLength));
            }
            SaveResourceContext saveContext = builder.build();
            StoringRepo storingRepo = storingRepositoryByKey(fileRepoPath.getRepoKey());
            if (storingRepo == null) {
                throw new IllegalArgumentException("Storing repo for '" + fileRepoPath + "' not found");
            }
            saveResource(storingRepo, saveContext);
        } finally {
            Closeables.closeQuietly(is);
        }
    }

    @Override
    public void saveFilesInternal(@Nonnull List<Pair<RepoPath, File>> files) {
        files.forEach(pair -> {
            try {
                saveFileInternal(pair.getFirst(), new FileInputStream(pair.getSecond())); // No leakage here
            } catch (Exception e) {
                // Log error and continue to the next file
                log.error("Error during saving file {}: {}", pair.getFirst().toPath(), e.getMessage());
            }
        });
    }

    @Override
    public List<VirtualRepo> getVirtualRepositories() {
        return new ArrayList<>(cache.get().virtualRepositoriesMap.values());
    }

    @Override
    public List<LocalRepo> getLocalFederatedAndCachedRepositories() {
        RepositoriesCache repositoriesCache = cache.get();
        Collection<LocalRepo> localRepos = repositoriesCache.localRepositoriesMap.values();
        Collection<LocalCacheRepo> cacheRepos = repositoriesCache.localCacheRepositoriesMap.values();
        Collection<FederatedRepo> federatedRepos = repositoriesCache.federatedRepositoriesMap.values();
        List<LocalRepo> repos = new ArrayList<>(localRepos);
        repos.addAll(cacheRepos);
        repos.addAll(federatedRepos);
        return repos;
    }

    @Override
    public RepoConfig getRepositoryConfigByKey(String key) {
        return getRepoConfigByKey(key);
    }

    @Override
    public List<RealRepo> getLocalFederatedAndRemoteRepositories() {
        List<RealRepo> repos = new ArrayList<>();
        RepositoriesCache repositoriesCache = cache.get();
        repos.addAll(repositoriesCache.localRepositoriesMap.values());
        repos.addAll(repositoriesCache.remoteRepositoriesMap.values());
        repos.addAll(repositoriesCache.federatedRepositoriesMap.values());
        return repos;
    }

    @Override
    public List<LocalConfig> getLocalFederatedAndCachedRepoConfigs() {
        return getLocalFederatedAndCachedRepositories().stream()
                .map(LocalRepo::getRepoConfig)
                .map(LocalConfig.class::cast)
                .collect(Collectors.toList());
    }

    @Override
    public List<LocalConfig> getLocalFederatedAndCachedRepoConfigsByProject(@Nullable String projectKey) {
        if (ProjectsUtils.isDefaultProject(projectKey)) {
            return getLocalFederatedAndCachedRepoConfigs();
        }
        Set<String> projectRepoKeys = projectsService.getProjectRepositoriesKeys(projectKey, false);
        return getLocalFederatedAndCachedRepositories()
                .stream()
                .filter(repo -> projectRepoKeys.contains(
                        repo.isCache() ? removeDashCache(repo.getKey()) : repo.getKey()))
                .map(Repo::getRepoConfig)
                .map(LocalConfig.class::cast)
                .collect(Collectors.toList());
    }

    @Override
    public VirtualRepoDescriptor virtualRepoDescriptorByKey(String repoKey) {
        if (StringUtils.isBlank(repoKey)) {
            return null;
        }
        VirtualRepo virtualRepo = cache.get().virtualRepositoriesMap.get(repoKey);
        return virtualRepo != null ? virtualRepo.getDescriptor() : null;
    }

    @Override
    public VirtualRepoConfig virtualRepoConfigByKey(String repoKey) {
        if (StringUtils.isBlank(repoKey)) {
            return null;
        }
        return repositoryConfigService.getVirtualRepoConfigByKey(repoKey);
    }

    @Nullable
    public RepoConfig getRepoConfigByKey(String repoKey) {
        if (getInternalRepoConfigService().isManaged(repoKey)) {
            return repositoryConfigService.getRepoConfigByKey(repoKey);
        }
        Repo<?, ?> repo = repositoryByKey(repoKey);
        return repo != null ? repo.getRepoConfig() : null;
    }

    @Override
    public boolean existsByType(RepoDetailsType repoType, String repoKey) {
        if (getInternalRepoConfigService().isManaged(repoKey)) {
            return repositoryConfigService.existsByType(repoType, repoKey);
        }
        RepositoriesCache repositoriesCache = cache.get();
        return switch (repoType) {
            case LOCAL -> repositoriesCache.localRepositoriesMap.containsKey(repoKey);
            case REMOTE -> repositoriesCache.remoteRepositoriesMap.containsKey(repoKey);
            case VIRTUAL -> repositoriesCache.virtualRepositoriesMap.containsKey(repoKey);
            case FEDERATED -> repositoriesCache.federatedRepositoriesMap.containsKey(repoKey);
            case RELEASE_BUNDLE -> repositoriesCache.releaseBundlesRepoMap.containsKey(repoKey);
        };
    }

    @Override
    public List<RepoConfig> getRepoConfigsByPackageType(RepoType repoType) {
        if (repoType == null) {
            throw new ItemNotFoundRuntimeException("Can't get repo config of null package type");

        }
        Collection<Repo> repos = cache.get().repoTypeRepositoriesMap.get(repoType);
        if (repos == null) {
            return List.of();
        }
        return repos.stream()
                .map(Repo::getRepoConfig)
                .toList();
    }

    /**
     * Returns list of all repos, including cache repos
     */
    @Override
    public List<RepoConfig> getRepoConfigsByPackageTypeAndProject(RepoType repoType, @Nullable String projectKey) {
        Function<String, Boolean> projectFilter = createProjectReposFilter(projectKey);
        return getRepoConfigsByPackageType(repoType)
                .stream()
                .filter(repo -> projectFilter.apply(repo.getKey()))
                .toList();
    }

    @Override
    public Map<RepoType, Integer> getRepoDescriptorByPackageTypeCountAndProject(
            @Nullable String projectKey) {
        Map<RepoType, Integer> ans = new TreeMap<>(new RepoType.RepoNameComparator());
        Function<String, Boolean> projectFilter = createProjectReposFilter(projectKey);
        StreamSupportUtils.mapEntriesStream(cache.get().repoTypeRepositoriesMap)
                .forEach(entry -> ans.putIfAbsent(entry.getKey(),
                        (int) entry.getValue()
                                .stream()
                                .filter(repo -> projectFilter.apply(repo.getKey()))
                                .count()));
        return ans;
    }

    @Override
    public boolean isVirtualRepoExist(String repoKey) {
        return virtualRepoDescriptorByKey(repoKey) != null;
    }

    @Override
    public boolean isRemoteRepoExist(String repoKey) {
        return remoteRepositoryByKey(repoKey) != null;
    }

    @Override
    public boolean isPipeInfoRepository(String key) {
        Repo<?, ?> repo = repositoryByKey(key);
        if (repo == null) {
            return false;
        }
        return repo.getDescriptor().getType().isPipeInfo();
    }

    @Override
    @Nonnull
    public String getStringContent(FileInfo fileInfo) {
        return getStringContent(fileInfo.getRepoPath());
    }

    @Override
    @Nonnull
    public String getStringContent(RepoPath repoPath) {
        LocalRepo repo = localFederatedOrCachedRepositoryByKey(repoPath.getRepoKey());
        if (repo == null) {
            throw new IllegalArgumentException("Local repository for '" + repoPath + "' doesn't exist");
        }
        return repo.getTextFileContent(repoPath);
    }

    @Override
    public ResourceStreamHandle getResourceStreamHandle(RepoPath repoPath) {
        LocalRepo repo = localFederatedOrCachedRepositoryByKey(repoPath.getRepoKey());
        if (repo == null) {
            throw new IllegalArgumentException("Local repository for '" + repoPath + "' doesn't exist");
        }
        // Recreate the repo path for remote stream handle request
        if (repo.isCache() && !repo.getKey().equals(repoPath.getRepoKey())) {
            repoPath = InternalRepoPathFactory.cacheRepoPath(repoPath);
        }
        try {
            return repo.getFileContent(repoPath);
        } catch (BinaryNotFoundException exception) {
            throw new NotFoundException(exception.getMessage(), exception);
        }
    }

    @Override
    @Deprecated(since = "7.134.0")
    public ArchiveFileContent getArchiveFileContent(RepoPath archivePath, String sourceEntryPath) throws IOException {
        LocalRepo repo = localFederatedOrCachedRepositoryByKey(archivePath.getRepoKey());
        if (repo == null) {
            return ArchiveFileContent.contentNotFound("Repository '" + archivePath.getRepoKey() + "' not found");
        }
        return new ArchiveContentRetriever().getArchiveFileContent(repo, archivePath, sourceEntryPath);
    }

    @Override
    public ArchiveFileContent getGenericArchiveFileContent(RepoPath archivePath, String sourceEntryPath)
            throws IOException {
        LocalRepo repo = localFederatedOrCachedRepositoryByKey(archivePath.getRepoKey());
        if (repo == null) {
            return ArchiveFileContent.contentNotFound("Repository '" + archivePath.getRepoKey() + "' not found");
        }
        return new ArchiveContentRetriever().getGenericArchiveFileContent(repo, archivePath, sourceEntryPath);
    }

    /**
     * Import all the repositories under the passed folder which matches local or cached repository declared in the
     * configuration. Having empty directory for each repository is allowed and not an error. Nothing will be imported
     * for those.
     */
    @Override
    public void importAll(ImportSettingsImpl settings) {
        RepositoryImportSettingsImpl repositoriesImportSettings =
                new RepositoryImportSettingsImpl(settings.getBaseDir(), settings);
        List<String> localRepoTypeKeys = getAllLocalRepoTypeKeys();
        List<String> reposAllowedForImport = repositoryConfigService.filterReposAllowedForImport(localRepoTypeKeys);
        repositoriesImportSettings.setRepositories(reposAllowedForImport);
        repositoriesImportSettings.setRepositoriesToDelete(Collections.emptyList());
        repositoriesImportSettings.setFailIfEmpty(false);
        importRepositoriesFromSettings(repositoriesImportSettings);
    }

    /**
     * Import the artifacts under the folder passed directly in the repository named "repoKey". If no repository with
     * this repo key exists or if the folder passed is empty, the status will be set to error.
     */
    @Override
    @SuppressWarnings({"ThrowableInstanceNeverThrown"})
    public void importRepo(String repoKey, ImportSettingsImpl settings) {
        RepositoryImportSettingsImpl singleRepoImportSettings =
                new RepositoryImportSettingsImpl(settings.getBaseDir(), settings);
        singleRepoImportSettings.setRepositories(Lists.newArrayList(repoKey));
        singleRepoImportSettings.setSingleRepoImport(true);
        importRepositoriesFromSettings(singleRepoImportSettings);
    }

    /**
     * This method will delete and import all the local and cached repositories listed in the (newly loaded) config
     * file. This action is resource intensive and is done in multiple transactions to avoid out of memory exceptions.
     */
    @Override
    public void importFrom(ImportSettings settings) {
        MutableStatusHolder status = settings.getStatusHolder();
        status.status("Importing Repositories Content...", log);
        File repoRootPath = getRepositoriesExportDir(settings.getBaseDir());
        if (!repoRootPath.exists() || !repoRootPath.isDirectory()) {
            if (settings.isFailIfEmpty()) {
                throw new IllegalArgumentException(
                        "Could not find content to restore. Verify your export includes content or check the 'exclude" +
                                " content' checkbox");
            } else {
                status.status("No repositories root to import at " + repoRootPath, log);
                return;
            }
        }
        List<String> repositoryKeysForDeletion = getAllLocalRepoTypeKeys();
        List<String> localRepoKeysToImport = getLocalReposToImport(settings, repositoryKeysForDeletion);
        if (settings.isExcludeBuildInfoRepo()) {
            filterOutBuildRepos(localRepoKeysToImport);
            filterOutBuildRepos(repositoryKeysForDeletion);
        }
        RepositoryImportSettingsImpl repositoriesImportSettings =
                new RepositoryImportSettingsImpl(repoRootPath, settings);
        repositoriesImportSettings.setRepositories(localRepoKeysToImport);
        repositoriesImportSettings.setRepositoriesToDelete(repositoryKeysForDeletion);
        repositoriesImportSettings.setFailIfEmpty(false);
        importRepositoriesFromSettings(repositoriesImportSettings);
    }

    @VisibleForTesting
     List<String> getLocalReposToImport(ImportSettings settings, List<String> repositoryKeysForDeletion) {
        List<String> localRepoKeysForImport = settings.getRepositories();
        if (localRepoKeysForImport.isEmpty()) {
            return new ArrayList<>(repositoryKeysForDeletion);
        }
        return repositoryConfigService.filterReposAllowedForImport(localRepoKeysForImport);
    }

    private void filterOutBuildRepos(List<String> repoKeys) {
        repoKeys.removeIf(key -> buildFacade.isBuildInfoRepo(key));
    }

    @VisibleForTesting
    void importRepositoriesFromSettings(ImportSettingsImpl settings) {
        if (!ConstantValues.repositoryImportEnabled.getBoolean()) {
            throw ImportIsDisabledException.buildRepositoriesException();
        }

        if (addonsManager.isReadOnlyLicense()) {
            settings.getStatusHolder()
                    .error("Failed to import repositories due to an expired license, see logs for more details.",
                            SC_BAD_REQUEST, log);
            throw new ImportIsDisabledException("Repositories Import failed " + LICENSE_READ_ONLY_MESSAGE_SUFFIX);
        }

        String jobToken = "No Job";
        boolean taskCompletion = false;
        MutableStatusHolder status = settings.getStatusHolder();
        try {
            jobToken = createAndStartImportJob(settings);
            taskCompletion = taskService.waitForTaskCompletion(jobToken);
        } finally {
            if (!taskCompletion && !status.isError()) {
                // Add error of no completion
                status.error("The task " + jobToken + " did not complete correctly.", log);
            }
        }
    }

    private String createAndStartImportJob(ImportSettingsImpl settings) {
        Task task = TaskUtils.createManualTask(ImportJob.class, 0L);
        task.addAttribute(RepositoryImportSettingsImpl.class.getName(), settings);
        String remoteClientIp = HttpUtils.getRemoteClientAddress();
        task.addAttribute(ImportJob.REMOTE_CLIENT_IP, remoteClientIp);
        return taskService.startTask(task, true);
    }

    @Override
    public void exportTo(ExportSettings settings) {
        MutableStatusHolder status = settings.getStatusHolder();
        status.status("Exporting repositories...", log);
        if (TaskCallback.currentTaskId() == null) {
            exportAsync(BaseSettings.FULL_SYSTEM, settings);
        } else {
            List<String> repoKeys = settings.getRepositories();
            for (String repoKey : repoKeys) {
                boolean stop = taskService.pauseOrBreak();
                if (stop) {
                    status.error("Export was stopped", log);
                    return;
                }
                exportRepo(repoKey, settings);
                if (status.isError() && settings.isFailFast()) {
                    return;
                }
            }

            if (settings.isIncremental()) {
                File repositoriesDir = getRepositoriesExportDir(settings.getBaseDir());
                cleanupIncrementalBackupDirectory(repositoriesDir, repoKeys);
            }
        }
    }

    @Override
    public void exportRepo(String repoKey, ExportSettings settings) {
        MutableStatusHolder status = settings.getStatusHolder();
        if (TaskCallback.currentTaskId() == null) {
            exportAsync(repoKey, settings);
        } else {
            //Check if we need to break/pause
            boolean stop = taskService.pauseOrBreak();
            if (stop) {
                status.error("Export was stopped on " + repoKey, log);
                return;
            }
            LocalRepo sourceRepo = localFederatedOrCachedRepositoryByKey(repoKey);
            if (sourceRepo == null) {
                status.error("Export cannot be done on non existing repository " + repoKey, log);
                return;
            }
            if (shouldExcludeBuildInfo(settings, sourceRepo.getDescriptor())) {
                status.debug(repoKey + " is excluded from the export since user opted to exclude builds.", log);
                return;
            }
            boolean safeExport = settings.isSafeExport();
            log.info("OS is {}, Safe Export is {}", SystemUtils.OS_NAME, safeExport ? "ON" : "OFF");
            File targetDir = getRepoExportDir(settings.getBaseDir(),
                    safeExport ? convertToSafeName(repoKey, Collections.emptySet(), true) : repoKey);
            ExportSettingsImpl repoSettings = new ExportSettingsImpl(targetDir, settings);
            sourceRepo.exportTo(repoSettings);
        }
    }

    @Override
    public boolean virtualItemExistsIncludingNested(RepoPath repoPath) {
        VirtualRepo virtualRepo = virtualRepositoryByKey(repoPath.getRepoKey());
        if (virtualRepo == null) {
            throw new RepositoryRuntimeException("Repository " + repoPath.getRepoKey() + " does not exists!");
        }
        return virtualRepo.virtualItemExistsIncludingNested(repoPath.getPath());
    }

    /**
     * If a user sends the 'excludeBuilds' option in the {@param settings},
     * (because it is not possible to exclude specific repositories in the system export)
     * we exclude them here like this. See {@code ExportArtifcatResource}.
     */
    @VisibleForTesting
    boolean shouldExcludeBuildInfo(ExportSettings settings, RepoDescriptor descriptor) {
        return settings.isExcludeBuilds() && BuildInfo.equals(descriptor.getType());
    }

    private File getRepoExportDir(File exportDir, String repoKey) {
        return new File(getRepositoriesExportDir(exportDir), repoKey);
    }

    private File getRepositoriesExportDir(File exportDir) {
        // the directory under the base export dir that contains the exported repositories
        return new File(exportDir, "repositories");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MutableStatusHolder exportSearchResults(SavedSearchResults searchResults, ExportSettingsImpl baseSettings) {
        return new DbRepoExportSearchHandler(searchResults, baseSettings).export();
    }

    @Override
    @Nonnull
    public ItemInfo getItemInfo(RepoPath repoPath) {
        LocalRepo localRepo = getLocalRepository(repoPath);
        RepoPath resolvedRepoPath = ensureStoringRepoPathResolved(localRepo, repoPath);
        VfsItem item = localRepo.getImmutableFsItem(resolvedRepoPath);
        if (item != null) {
            return item.getInfo();
        }
        throw new ItemNotFoundRuntimeException("Item " + repoPath + " does not exist");
    }

    @Override
    @Nonnull
    public FileInfo getFileInfo(RepoPath repoPath) {
        ItemInfo itemInfo = getItemInfo(repoPath);
        if (itemInfo instanceof FileInfo) {
            return (FileInfo) itemInfo;
        } else {
            throw new FileExpectedException(repoPath);
        }
    }

    @Override
    @Nonnull
    public FolderInfo getFolderInfo(RepoPath repoPath) {
        ItemInfo itemInfo = getItemInfo(repoPath);
        if (itemInfo instanceof FolderInfo) {
            return (FolderInfo) itemInfo;
        } else {
            throw new FolderExpectedException(repoPath);
        }
    }

    @Override
    public boolean exists(RepoPath repoPath) {
        String repoKey = repoPath.getRepoKey();
        LocalRepo localRepo = localFederatedOrCachedRepositoryByKey(repoKey);
        return localRepo != null && localRepo.itemExists(repoPath.getPath());
    }

    @Override
    @Nonnull
    public List<RepoPath> getExistingRepoPaths(@Nonnull List<RepoPath> repoPaths) {
        if (repoPaths.isEmpty()) {
            return new ArrayList<>();
        }
        Map<String, List<RepoPath>> repoKeyRepoPaths = new HashMap<>();
        for (RepoPath repoPath : repoPaths) {
            if (!repoKeyRepoPaths.containsKey(repoPath.getRepoKey())) {
                repoKeyRepoPaths.put(repoPath.getRepoKey(), new ArrayList<>());
            }
            repoKeyRepoPaths.get(repoPath.getRepoKey()).add(repoPath);
        }

        List<RepoPath> existingRepoPaths = new ArrayList<>();
        repoKeyRepoPaths.forEach((repoKey, repoPathsList) -> {
            LocalRepo localRepo = localFederatedOrCachedRepositoryByKey(repoKey);
            if (localRepo != null) {
                existingRepoPaths.addAll(localRepo.getExistingItems(repoPathsList));
            }
        });
        return existingRepoPaths;
    }

    @Override
    public boolean existsBySha1(String sha1) {
        return fileService.existsBySha1(sha1);
    }

    @Override
    public ItemMetaInfo getItemMetaInfo(ItemInfo itemInfo) {
        return ContextHelper.get().beanForType(NodeMetaInfoService.class).getNodeMetaInfo(itemInfo);
    }

    @Override
    public MoveMultiStatusHolder moveWithoutMavenMetadata(RepoPath from, RepoPath to, boolean dryRun,
            boolean suppressLayouts, boolean failFast) {
        MoverConfigBuilder configBuilder = new MoverConfigBuilder(from, to).copy(false).dryRun(dryRun)
                .atomic(true)
                .suppressLayouts(suppressLayouts).failFast(failFast);
        return moveOrCopy(configBuilder.build());
    }

    @Override
    public ChecksumInfo setClientChecksum(LocalRepo repo, ChecksumType checksumType, RepoPath targetFileRepoPath,
            String checksum) {
        MutableVfsFile vfsFile = getMutableFile(targetFileRepoPath);
        if (!checksumType.isValid(checksum)) {
            log.warn("Uploading non valid original checksum for {}", vfsFile.getRepoPath());
        }
        vfsFile.setClientChecksum(checksumType, checksum);
        // fire replication event for the client checksum
        RepoPath checksumRepoPath = InternalRepoPathFactory.create(targetFileRepoPath.getRepoKey(),
                targetFileRepoPath.getPath() + checksumType.ext());
        addonsManager.addonByType(ReplicationAddon.class)
                .offerLocalReplicationDeploymentEvent(checksumRepoPath, vfsFile.isFile());

        return vfsFile.getInfo().getChecksumsInfo().getChecksumInfo(checksumType);
    }

    @Override
    public MoveMultiStatusHolder delete(Set<RepoPath> pathsToMove) {
        return delete(pathsToMove, false, false);
    }

    @Override
    public MoveMultiStatusHolder delete(
            Set<RepoPath> pathsToMove,
            boolean skipTrashcan,
            boolean pruneEmptyFolders) {
        Set<RepoPath> pathsToMoveIncludingParents = aggregatePathsToMove(pathsToMove, "", false);
        log.debug("The following paths will be removed: {}", pathsToMoveIncludingParents);
        MoveMultiStatusHolder status = new MoveMultiStatusHolder();
        for (RepoPath pathToDelete : pathsToMoveIncludingParents) {
            status.merge(undeploy(pathToDelete, true, pruneEmptyFolders, skipTrashcan));
        }
        return status;
    }

    @Override
    public void deletePathAsync(@Nonnull AsyncDeleteWorkItem asyncDeleteWorkItem) {
        RepositoryService txMe = ContextHelper.get().beanForType(RepositoryService.class);
        txMe.undeploy(asyncDeleteWorkItem.getDeleteContext().getRepoPath(),
                asyncDeleteWorkItem.isPruneEmptyFolders(),
                asyncDeleteWorkItem.getDeleteContext());
    }

    @Override
    public MoveMultiStatusHolder move(RepoPath from, RepoPath to, boolean dryRun, boolean suppressLayouts,
            boolean failFast) {
        return move(from, to, dryRun, suppressLayouts, failFast, true);
    }

    @Override
    public MoveMultiStatusHolder move(RepoPath from, RepoPath to, boolean dryRun, boolean suppressLayouts,
            boolean failFast, boolean unixStyleBehaviour) {
        MoverConfigBuilder configBuilder = new MoverConfigBuilder(from, to).copy(false).dryRun(dryRun)
                .unixStyleBehavior(unixStyleBehaviour).
                suppressLayouts(suppressLayouts).failFast(failFast).atomic(true);
        return moveOrCopy(configBuilder.build());
    }

    @Override
    public MoveMultiStatusHolder move(Set<RepoPath> pathsToMove, String targetLocalRepoKey, Properties properties,
            boolean dryRun, boolean failFast) {
        Set<RepoPath> pathsToMoveIncludingParents = aggregatePathsToMove(pathsToMove, targetLocalRepoKey, false);
        log.debug("The following paths will be moved: {}", pathsToMoveIncludingParents);
        // start moving each path separately, marking each folder or file's parent folder for metadata recalculation
        MoveMultiStatusHolder status = new MoveMultiStatusHolder();
        RepoPathMover mover = getMoveRepoPathService();
        for (RepoPath pathToMove : pathsToMoveIncludingParents) {
            RepoPath targetRepoPath = InternalRepoPathFactory
                    .create(targetLocalRepoKey, pathToMove.getPath(), pathToMove.isFolder());
            log.debug("Moving path: {} to {}", pathToMove, targetRepoPath);
            mover.executeOperation(status, new MoverConfigBuilder(pathToMove, targetRepoPath).copy(false).dryRun(dryRun)
                    .pruneEmptyFolders(true).properties(properties)
                    .unixStyleBehavior(false).failFast(failFast).atomic(true).build());
        }
        return status;
    }

    @Override
    public MoveMultiStatusHolder move(Set<RepoPath> pathsToMove, RepoPath targetParent, Properties properties,
            boolean dryRun, boolean failFast) {
        log.debug("The following paths will be moved under path {}: {}", targetParent, pathsToMove);
        String targetRepo = targetParent.getRepoKey();
        String targetPath = targetParent.getPath();
        MoveMultiStatusHolder status = new MoveMultiStatusHolder();
        RepoPathMover mover = getMoveRepoPathService();
        for (RepoPath source : pathsToMove) {
            RepoPath target = InternalRepoPathFactory.create(targetRepo, targetPath + "/" + source.getName());
            log.debug("Moving path: {} to {}", source, target);
            mover.executeOperation(status, new MoverConfigBuilder(source, target).copy(false).dryRun(dryRun)
                    .pruneEmptyFolders(true).properties(properties)
                    .unixStyleBehavior(false).failFast(failFast).atomic(true).build());
        }
        return status;
    }

    @Override
    public MoveMultiStatusHolder moveFlat(RepoPath repoPath, String targetKey, String targetPath,
            Properties propsAppliedToEachMovedItem, List<String> removeProps, boolean dryRun, boolean failFast,
            int transactionSize) {
        return moveFlat(repoPath,
                targetKey,
                targetPath,
                dryRun,
                failFast,
                transactionSize,
                a -> propsAppliedToEachMovedItem,
                a -> removeProps);
    }

    @Override
    public MoveMultiStatusHolder moveFlat(RepoPath repoPath, String targetKey, String targetPath,
            boolean dryRun, boolean failFast, int transactionSize,
            @Nonnull Function<RepoPath, Properties> propsAppliedToEachMovedItem,
            @Nonnull Function<RepoPath, List<String>> removeProps) {
        MoveMultiStatusHolder assertStatus = new MoveMultiStatusHolder();
        RepoPath targetRepoPath = RepoPathFactory.create(targetKey, targetPath);
        fireAssertDeletePathAllowedEvent(repoPath, assertStatus); //shall not delete file that's related to RB
        fireAssertOverwriteAllowedEvent(targetRepoPath, assertStatus); //shall not overwrite file related to RB
        if (assertStatus.hasErrors()) {
            return assertStatus;
        }
        // Create move context
        MoveCopyContext context = new MoveCopyContext(repoPath, targetKey, targetPath);
        context.setTransactionSize(transactionSize).
                setDryRun(dryRun).
                setFailFast(failFast).
                setAddProperties(propsAppliedToEachMovedItem).
                setRemoveProperties(removeProps).
                setCopy(false).
                setSuppressLayouts(true);
        // Execute move
        return flatMoveCopyService.moveCopy(context);
    }

    @Override
    public MoveMultiStatusHolder copyFlat(RepoPath repoPath, String targetKey, String targetPath, Properties addProps,
            List<String> removeProps, boolean dryRun, boolean failFast, int transactionSize) {
        return copyFlat(repoPath,
                targetKey,
                targetPath,
                dryRun,
                failFast,
                transactionSize,
                a -> addProps,
                a -> removeProps);
    }

    @Override
    public MoveMultiStatusHolder copyFlat(RepoPath repoPath, String targetKey, String targetPath,
            boolean dryRun, boolean failFast, int transactionSize, @Nonnull Function<RepoPath, Properties> addProps,
            @Nonnull Function<RepoPath, List<String>> removeProps) {
        MoveMultiStatusHolder assertStatus = new MoveMultiStatusHolder();
        fireAssertOverwriteAllowedEvent(RepoPathFactory.create(targetKey, targetPath), assertStatus);
        if (assertStatus.hasErrors()) {
            return assertStatus;
        }
        // Create move context
        MoveCopyContext context = new MoveCopyContext(repoPath, targetKey, targetPath);
        context.setTransactionSize(transactionSize).
                setDryRun(dryRun).
                setFailFast(failFast).
                setAddProperties(addProps).
                setRemoveProperties(removeProps).
                setCopy(true).
                setSuppressLayouts(true);
        // Execute move/copy
        return flatMoveCopyService.moveCopy(context);
    }

    @Override
    public MoveMultiStatusHolder moveMultiTx(RepoPath fromRepoPath, RepoPath targetRepoPath, boolean dryRun,
            boolean suppressLayouts,
            boolean failFast) {
        return move(fromRepoPath, targetRepoPath, dryRun, suppressLayouts, failFast, false, null, false);
    }

    @Override
    public MoveMultiStatusHolder move(RepoPath fromRepoPath, RepoPath targetRepoPath, boolean dryRun,
            boolean suppressLayouts,
            boolean failFast, boolean atomic, Properties properties, boolean overrideProperties) {
        return move(fromRepoPath, targetRepoPath, dryRun, suppressLayouts, failFast, atomic, false,
                properties, overrideProperties);
    }

    @Override
    public MoveMultiStatusHolder move(RepoPath fromRepoPath, RepoPath targetRepoPath, boolean dryRun,
            boolean suppressLayouts, boolean failFast, boolean atomic, boolean toCacheAllowed,
            Properties properties, boolean overrideProperties) {
        MoverConfigBuilder configBuilder =
                new MoverConfigBuilder(fromRepoPath, targetRepoPath).copy(false).dryRun(dryRun)
                        .suppressLayouts(suppressLayouts).failFast(failFast)
                        .atomic(atomic).toCache(toCacheAllowed)
                        .properties(properties).overrideProperties(overrideProperties);
        MoveMultiStatusHolder assertStatus = new MoveMultiStatusHolder();
        fireAssertDeletePathAllowedEvent(fromRepoPath, assertStatus);
        if (assertStatus.hasErrors()) {
            return assertStatus;
        }
        return moveOrCopy(configBuilder.build());
    }

    @Override
    public MoveMultiStatusHolder copyMultiTx(RepoPath fromRepoPath, RepoPath targetRepoPath, boolean dryRun,
            boolean suppressLayouts, boolean failFast) {
        return copy(fromRepoPath, targetRepoPath, dryRun, suppressLayouts, failFast, false, null, false);
    }

    @Override
    public MoveMultiStatusHolder copy(RepoPath fromRepoPath, RepoPath targetRepoPath, boolean dryRun,
            boolean suppressLayouts, boolean failFast) {
        return copy(fromRepoPath, targetRepoPath, dryRun, suppressLayouts, failFast, true, null, false);
    }

    @Override
    public MoveMultiStatusHolder copy(RepoPath fromRepoPath, RepoPath targetRepoPath, boolean dryRun,
            boolean suppressLayouts, boolean failFast, boolean atomic, Properties properties,
            boolean overrideProperties) {
        return copy(fromRepoPath, targetRepoPath, dryRun, suppressLayouts, failFast, atomic, properties,
                overrideProperties, false);
    }

    @Override
    public MoveMultiStatusHolder copyToCache(RepoPath fromRepoPath, RepoPath targetRepoPath, boolean dryRun,
            boolean suppressLayouts, boolean failFast, boolean atomic, Properties properties,
            boolean overrideProperties) {
        return copy(fromRepoPath, targetRepoPath, dryRun, suppressLayouts, failFast, atomic, properties,
                overrideProperties, true);
    }

    private MoveMultiStatusHolder copy(RepoPath fromRepoPath, RepoPath targetRepoPath, boolean dryRun,
            boolean suppressLayouts,
            boolean failFast, boolean atomic, Properties properties, boolean overrideProperties, boolean toCache) {
        MoverConfigBuilder configBuilder = new MoverConfigBuilder(fromRepoPath, targetRepoPath).copy(true)
                .dryRun(dryRun).suppressLayouts(suppressLayouts)
                .failFast(failFast)
                .atomic(atomic).properties(properties).overrideProperties(overrideProperties).toCache(toCache);
        MoveMultiStatusHolder status = moveOrCopy(configBuilder.build());
        for (RepoPath folderPath : status.getCandidatesForMavenMetadataCalculation()) {
            mavenMetadataService.calculateMavenMetadataAsync(new MavenMetadataWorkItem(folderPath, false));
        }
        return status;
    }

    @Override
    public MoveMultiStatusHolder copy(Set<RepoPath> pathsToCopy, String targetLocalRepoKey,
            Properties properties, boolean dryRun, boolean failFast) {
        Set<RepoPath> pathsToCopyIncludingParents = aggregatePathsToMove(pathsToCopy, targetLocalRepoKey, true);

        log.debug("The following paths will be copied: {}", pathsToCopyIncludingParents);
        //Start copying each path separately, marking each folder or file's parent folder for metadata recalculation
        MoveMultiStatusHolder status = new MoveMultiStatusHolder();
        RepoPathMover mover = getCopyRepoPathService();
        for (RepoPath pathToCopy : pathsToCopyIncludingParents) {
            RepoPath targetRepoPath = InternalRepoPathFactory
                    .create(targetLocalRepoKey, pathToCopy.getPath(), pathToCopy.isFolder());
            mover.executeOperation(status, new MoverConfigBuilder(pathToCopy, targetRepoPath).copy(true).dryRun(dryRun)
                    .pruneEmptyFolders(false).properties(properties)
                    .unixStyleBehavior(false).failFast(failFast).atomic(true).build());
        }

        return status;
    }

    @Override
    public MoveMultiStatusHolder copy(RepoPath fromRepoPath, RepoPath targetRepoPath) {
        MoverConfigBuilder configBuilder = new MoverConfigBuilder(fromRepoPath, targetRepoPath).copy(true)
                .dryRun(false).suppressLayouts(true).failFast(true)
                .atomic(false).properties(null).overrideProperties(false).toCache(false).unixStyleBehavior(false);
        return moveOrCopy(configBuilder.build());
    }

    private MoveMultiStatusHolder moveOrCopy(MoverConfig config) {
        MoveMultiStatusHolder status = new MoveMultiStatusHolder();
        // copy or move service
        if (config.isCopy()) {
            getCopyRepoPathService().executeOperation(status, config);
        } else {
            getMoveRepoPathService().executeOperation(status, config);
        }
        return status;
    }

    /**
     * Returns an instance of the Repo Path Mover
     *
     * @return RepoPathMover
     */
    private RepoPathMover getMoveRepoPathService() {
        return ContextHelper.get().beanForType(MoveRepoPathService.class);
    }

    /**
     * Returns an instance of the Repo Path Mover
     *
     * @return RepoPathMover
     */
    private RepoPathMover getCopyRepoPathService() {
        return ContextHelper.get().beanForType(CopyRepoPathService.class);
    }

    @Override
    public StatusHolder deploy(RepoPath repoPath, InputStream inputStream) {
        ArtifactoryDeployRequest request = new ArtifactoryDeployRequestBuilder(repoPath)
                .inputStream(inputStream).build();
        return deploy(request);
    }

    @Override
    public StatusHolder deploy(InternalArtifactoryRequest request) {
        try {
            InternalArtifactoryResponse response = new InternalArtifactoryResponse();
            uploadService.upload(request, response);
            return response.getStatusHolder();
        } catch (Exception e) {
            String msg = String.format("Cannot deploy to '{%s}'.", request.getRepoPath());
            log.error(msg + e.getMessage(), e);
            throw new RepositoryRuntimeException(msg, e);
        }
    }

    @Override
    public FileInfo getVirtualFileInfo(RepoPath virtualRepoPath) {
        VirtualRepo virtualRepo = virtualRepositoryByKey(virtualRepoPath.getRepoKey());
        FileInfo fileInfo = applyFunctionToLocalFederatedWithCaching(
                virtualRepo, virtualRepoPath, this::getFileInfo);
        if (fileInfo != null) {
            return fileInfo;
        }
        throw new ItemNotFoundRuntimeException("Item " + virtualRepoPath + " does not exists");
    }

    @Override
    public ItemInfo getVirtualItemInfo(RepoPath virtualRepoPath) {
        VirtualRepo virtualRepo = virtualRepositoryByKey(virtualRepoPath.getRepoKey());
        ItemInfo itemInfo = applyFunctionToLocalFederatedWithCaching(
                virtualRepo, virtualRepoPath, this::getItemInfo);
        if (itemInfo != null) {
            return itemInfo;
        }
        throw new ItemNotFoundRuntimeException("Item " + virtualRepoPath + " does not exists");
    }

    @Override
    public FolderInfo getVirtualFolderInfo(RepoPath virtualRepoPath) {
        VirtualRepo virtualRepo = virtualRepositoryByKey(virtualRepoPath.getRepoKey());
        FolderInfo folderInfo = applyFunctionToLocalFederatedWithCaching(
                virtualRepo, virtualRepoPath, this::getFolderInfo);
        if (folderInfo != null) {
            return folderInfo;
        }
        throw new ItemNotFoundRuntimeException("Folder " + virtualRepoPath + " does not exists");
    }

    @Nullable
    private <T> T applyFunctionToLocalFederatedWithCaching(VirtualRepo virtualRepo,
            RepoPath virtualRepoPath, Function<RepoPath, T> infoFunction) {
        if (virtualRepo == null) {
            throw new IllegalArgumentException(virtualRepoPath.getRepoKey() + " is not a virtual repository.");
        }
        Set<LocalRepo> resolvedLocalRepos = virtualRepo.getResolvedLocalFederatedAndCachedRepos();
        return ReposItemsThreadLocalCacheService.wrap(virtualRepoPath.getRepoKey(), () -> {
            for (LocalRepo<?, ?> resolvedLocalRepo : resolvedLocalRepos) {
                if (resolvedLocalRepo.itemExists(virtualRepoPath.getPath())) {
                    RepoPath path = resolvedLocalRepo.getRepoPath(virtualRepoPath.getPath());
                    return infoFunction.apply(path);
                }
            }
            return null;
        }, ConstantValues.fscRepoServiceEnabled::getBoolean);
    }


    @Override
    public List<RepoDescriptor> getVirtualResolvedLocalFederatedAndCacheDescriptors(String virtualRepoKey) {
        VirtualRepo virtualRepo = virtualRepositoryByKey(virtualRepoKey);
        if (virtualRepo != null) {
            return virtualRepo.getResolvedLocalFederatedAndCachedRepos().stream()
                    .map(Repo::getDescriptor).collect(Collectors.toList());
        }
        return Lists.newArrayList();
    }

    /**
     * This response is going to be closed in the Aql process after the results were streamed
     *
     * @see AqlJsonRemoteStreamer
     * @see AqlJsonMultiStreamer
     */
    @Override
    public CloseableHttpResponse executeAqlRemotely(String aqlQuery, String repoKey) {
        CloseableHttpResponse getResponse;
        HttpRepo remoteRepo = (HttpRepo) this.remoteRepositoryByKey(repoKey);
        log.trace("Executing remote aql request for repo: {}", repoKey);
        String url = this.getRemoteUrlFromRepo(remoteRepo.getUrl());
        HttpPost httpPost = new HttpPost(url + "api/search/aql");
        httpPost.setEntity(new StringEntity(aqlQuery, ContentType.TEXT_PLAIN));
        try {
            getResponse = remoteRepo.executeMethod(httpPost);
        } catch (IOException e) {
            log.error("Exception occurred in remoteAql {}", e.getMessage(), e);
            return null;
        }

        return getResponse;
    }

    private String getRemoteUrlFromRepo(String repoUrl) {
        String repo = StringUtils.substringAfterLast(PathUtils.trimTrailingSlashes(repoUrl), "/");
        String url = StringUtils.substringBeforeLast(repoUrl, repo);
        return PathUtils.addTrailingSlash(url.split("/api")[0]);
    }

    CloseableHttpResponse executeRemotePing(String repoKey) throws IOException {
        HttpRepo remoteRepo = (HttpRepo) remoteRepositoryByKey(repoKey);
        HttpGet httpGet = new HttpGet(HttpUtils.encodeUrl(remoteRepo.getUrl()));
        return remoteRepo.executeMethod(httpGet);
    }

    boolean isSmartRepo(String repoKey) {
        return this.smartRepoService.isSmartRepo(repoKey);
    }

    boolean isArtifactory(String repoKey) {
        boolean isArtifactory = false;
        log.debug("Attempting to ping a remote repo: {}", repoKey);
        try (CloseableHttpResponse response = this.executeRemotePing(repoKey)) {
            log.debug("Received ping response, checking headers for artifactory_id");
            isArtifactory = ArrayUtils.isNotEmpty(response.getHeaders(ARTIFACTORY_ID));
            log.debug("Checked headers, is it an artifactory: {}", isArtifactory);
        } catch (Exception e) {
            log.warn("Failed to verify remote repo {}", e.getMessage());
        }

        return isArtifactory;
    }

    @Override
    public boolean isRepoTransitiveValid(String repoKey) {
        return this.isSmartRepo(repoKey) || this.isArtifactory(repoKey);
    }

    @Override
    public BasicStatusHolder undeploy(RepoPath repoPath, boolean triggerMetadataCalculation) {
        return undeploy(repoPath, triggerMetadataCalculation, false, false);
    }

    @Override
    public BasicStatusHolder undeploy(
            RepoPath repoPath,
            boolean triggerMetadataCalculation,
            boolean pruneEmptyFolders,
            boolean skipTrashcan) {
        return undeploy(repoPath, pruneEmptyFolders,
                new DeleteContext(repoPath).triggerMetadataCalculation(triggerMetadataCalculation)
                        .skipTrashcan(skipTrashcan));
    }

    @Override
    public BasicStatusHolder undeploy(
            RepoPath repoPath,
            boolean triggerMetadataCalculation,
            boolean pruneEmptyFolders) {
        return undeploy(repoPath, pruneEmptyFolders,
                new DeleteContext(repoPath).triggerMetadataCalculation(triggerMetadataCalculation));
    }

    @Override
    public BasicStatusHolder gcUndeploy(RepoPath repoPath, boolean triggerMetadataCalculation,
            boolean pruneEmptyFolders) {
        return undeploy(repoPath, pruneEmptyFolders,
                new DeleteContext(repoPath).triggerMetadataCalculation(triggerMetadataCalculation).triggeredByGc(true));
    }

    @Override
    public BasicStatusHolder undeploy(RepoPath repoPath, boolean pruneEmptyFolders, DeleteContext deleteContext) {
        return undeployWithQueryRateLimiterIfNeeded(() -> undeployInternal(repoPath, pruneEmptyFolders, deleteContext));
    }

    private BasicStatusHolder undeployInternal(RepoPath repoPath, boolean pruneEmptyFolders,
            DeleteContext deleteContext) {
        String repoKey = repoPath.getRepoKey();
        StoringRepo<?, ?> storingRepo = storingRepositoryByKey(repoKey);
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        if (storingRepo == null) {
            statusHolder.status(REPOSITORY_NOT_FOUND.getReason(repoKey), REPOSITORY_NOT_FOUND.getStatusCode(), log);
            return statusHolder;
        }
        PathDeletionContext pathDeletionContext = new PathDeletionContext.Builder(storingRepo, repoPath.getPath(),
                statusHolder).assertOverwrite(false).idempotentDelete(deleteContext.isIdempotentDelete()).build();
        assertDeleteIncludingRoot(pathDeletionContext);
        if (!statusHolder.isError()) {
            try {
                storingRepo.undeploy(deleteContext);
            } catch (CancelException e) {
                statusHolder.errorWithDifferentLogLevel(CANCELED_BY_PLUGIN.getReason(), e.getErrorCode(), e, log, WARN);
            }
        }

        if (pruneEmptyFolders && !repoPath.isRoot()) {
            pruneService.prune(repoPath.getParent());
        }

        return statusHolder;
    }

    @Override
    public BasicStatusHolder undeployMultiTransaction(RepoPath repoPath) {
        return undeployMultiTransaction(repoPath, new BasicStatusHolder());
    }

    @Override
    public BasicStatusHolder undeployMultiTransaction(RepoPath repoPath, BasicStatusHolder statusHolder) {
        return undeployWithQueryRateLimiterIfNeeded(() -> undeployMultiTransactionInternal(repoPath, statusHolder));
    }

    /**
     * Undeploy the repo content, but not the repository root itself.
     */
    private void undeployRepoContentMultiTransaction(StoringRepo<?, ?> storingRepo, BasicStatusHolder statusHolder,
            boolean fullRepoContentDeletion) {
        RepoPath repoPath = RepoPathFactory.create(storingRepo.getKey());
        PathDeletionContext pathDeletionContext = new PathDeletionContext.Builder(storingRepo, repoPath.getPath(),
                statusHolder).assertOverwrite(false).build();
        assertNonRootPathDelete(pathDeletionContext, storingRepo, pathDeletionContext.getPath(), statusHolder);
        if (!statusHolder.isError()) {
            undeploySingleItem(repoPath, statusHolder, pathDeletionContext, fullRepoContentDeletion);
        }
    }

    private BasicStatusHolder undeploySingleItem(RepoPath repoPath, BasicStatusHolder statusHolder,
            PathDeletionContext pathDeletionContext) {
        return undeploySingleItem(repoPath, statusHolder, pathDeletionContext, repoPath.isRoot());
    }

    private BasicStatusHolder undeploySingleItem(RepoPath repoPath, BasicStatusHolder statusHolder,
            PathDeletionContext pathDeletionContext, boolean fullRepoContentDeletion) {
        ItemInfo itemInfo;
        try {
            itemInfo = getItemInfo(repoPath);
        } catch (ItemNotFoundRuntimeException e) {
            statusHolder.status(ITEM_NOT_FOUND.getReason(repoPath.toPrettyPath()), ITEM_NOT_FOUND.getStatusCode(), log);
            return statusHolder;
        }
        boolean shouldProtectPathDeletion =
                pathDeletionContext.getRepo().shouldProtectPathDeletion(pathDeletionContext);
        UndeployContext undeployContext = new UndeployContext(statusHolder, shouldProtectPathDeletion,
                System.currentTimeMillis(), repoPath.isRoot(), fullRepoContentDeletion);
        boolean deleted = undeploySingleItemTransactions(undeployContext, itemInfo.getRepoPath(), true);
        if (!deleted && !statusHolder.isError()) {
            // We won't respond 404 even if hideUnauthorizedResources flag is on, because the user does have
            //permission for repoPath itself
            addErrorStatusLogAsInfo(statusHolder, REQUIRES_DELETE_PERMISSION, repoPath.toPrettyPath(),
                    securityService.currentUsername());
        }
        if (fullRepoContentDeletion && deleted && !statusHolder.isError()) {
            deleteSystemFolders(repoPath.getRepoKey(), undeployContext);
        }
        return statusHolder;
    }

    private BasicStatusHolder undeployMultiTransactionInternal(RepoPath repoPath, BasicStatusHolder statusHolder) {
        String repoKey = repoPath.getRepoKey();
        StoringRepo<?, ?> storingRepo = storingRepositoryByKey(repoKey);
        if (storingRepo == null) {
            statusHolder.status(REPOSITORY_NOT_FOUND.getReason(repoKey), REPOSITORY_NOT_FOUND.getStatusCode(), log);
            return statusHolder;
        }
        PathDeletionContext pathDeletionContext = new PathDeletionContext.Builder(storingRepo, repoPath.getPath(),
                statusHolder).assertOverwrite(false).build();
        assertDeleteIncludingRoot(pathDeletionContext);
        if (!statusHolder.isError()) {
            return undeploySingleItem(repoPath, statusHolder, pathDeletionContext);
        }
        return statusHolder;
    }

    private BasicStatusHolder undeployWithQueryRateLimiterIfNeeded(Supplier<BasicStatusHolder> supplier) {
        if (qrlLowPriorityUndeploy.getBoolean()) {
            return withQueryRateLimiter(LOW_PRIORITY, supplier);
        }
        return supplier.get();
    }

    /**
     * Deletes a file or recursively deletes a folder. Uses a single transaction for each leaf item.
     * Skips deletion of entire system folders (See: {@link SystemFolders}), which are deleted subsequently on:
     * {@link #deleteSystemFolders} (See: RTDEV-57265). However, deleting single items from system
     * folders is permissible, unless blocked by specific validations.
     *
     * @param undeployContext             - undeploy context
     * @param repoPath                   - repo path
     * @param triggerMetadataCalculation - if true calculate meta data
     * @return true if the given repoPath was successfully deleted
     */
    @VisibleForTesting
    boolean undeploySingleItemTransactions(UndeployContext undeployContext, RepoPath repoPath,
            boolean triggerMetadataCalculation) {
        if (undeployContext.isSkipSystemFolder() && isUnderSystemFolder(repoPath)) {
            log.info("Skipping deletion of system folder: '{}'", repoPath.toPrettyPath());
            return true;
        }
        if (deleteForbidden(repoPath, undeployContext)) {
            return false;
        }
        boolean allChildrenDeleted = deleteChildren(undeployContext, repoPath);
        if (isDeletablePath(repoPath, allChildrenDeleted, undeployContext)) {
            return deletePath(undeployContext, repoPath, triggerMetadataCalculation);
        }
        return allChildrenDeleted;
    }

    private void incrementDeletedCount(BasicStatusHolder statusHolder) {
        if (isRemoveRepositoryFlowV2Enabled() &&
                statusHolder instanceof RepoRemovalStatusHolder repoRemovalStatusHolder) {
            repoRemovalStatusHolder.incrementDeletedCount();
        }
    }

    private boolean isDeletablePath(RepoPath repoPath, boolean allChildrenDeleted, UndeployContext undeployContext) {
        return !repoPath.isRoot() && allChildrenDeleted && !undeployContext.getPathsToProtect().contains(repoPath);
    }

    private boolean deleteForbidden(RepoPath repoPath, UndeployContext undeployContext) {
        // Use a temporary statusHolder for canDelete checks to isolate errors from other artifacts
        // that may be deleted concurrently with the shared statusHolder. This prevents false positives
        // where errors from one artifact incorrectly block deletion of another artifact.
        BasicStatusHolder tempStatusHolder = new BasicStatusHolder();
        DeleteContext deleteContext = new DeleteContext(repoPath)
                .fullRepoContentDeletion(undeployContext.isFullRepoContentDeletion());
        boolean canDelete = undeployContext.isFullRepoContentDeletion() ?
                storageInterceptors.canDelete(repoPath, tempStatusHolder, deleteContext) :
                storageInterceptors.canDelete(repoPath, tempStatusHolder);
        if (!canDelete) {
            mergeCanDeleteStatus(tempStatusHolder, undeployContext.getStatusHolder());
            // Unfortunately - this flow performs the interceptors calls only on the way back from the recursion,
            // (overriding the normal recursion flow of MutableVfsItem.delete(...)), so we need to go through
            // a check with the interceptors - right here (BEFORE going down the recursion)
            log.debug(DELETION_NOT_ALLOWED_MSG, repoPath);
            return true;
        }
        return false;
    }

    private void mergeCanDeleteStatus(BasicStatusHolder source, BasicStatusHolder target) {
        if (!source.hasErrorsOrWarnings()) {
            return;
        }
        if (source.isError()) {
            StatusEntry lastError = source.getLastError();
            if (lastError != null) {
                target.error(lastError.getMessage(), lastError.getStatusCode(), lastError.getException(), log);
            }
            return;
        }
        if (source.isWarning()) {
            StatusEntry lastWarning = source.getLastWarning();
            if (lastWarning != null) {
                target.warn(lastWarning.getMessage(), lastWarning.getStatusCode(), lastWarning.getException(), log);
            }
        }
    }

    private boolean deleteChildren(UndeployContext undeployContext, RepoPath repoPath) {
        if (repoPath.isFolder()) {
            List<Boolean> results = fileService.loadChildren(repoPath).stream()
                    .filter(child -> isChildDeletable(undeployContext, child))
                    .map(child -> undeploySingleItemTransactions(undeployContext, child.getRepoPath(),
                            false))
                    .toList();
            return results.stream().allMatch(Boolean::booleanValue);
        }
        return true;
    }

    /**
     * Determines whether a child item should be skipped during the deletion process.
     * This method ensures that artifacts moved to the trash after the deletion process
     * has started are not deleted.
     *
     * @param undeployContext The context of the undeployment process, containing details such as
     *                        the undeployment start time and status holder.
     * @param child           The child item being evaluated for deletion.
     * @return {@code false} if the child item should be skipped (not deleted), {@code true} otherwise.
     */
    private boolean isChildDeletable(UndeployContext undeployContext, ItemInfo child) {
        if (ConstantValues.undeploySingleItemWithTimeCheckEnabled.getBoolean()) {
            String trashTime = propertiesService.getProperties(child).getFirst("trash.time");
            if (trashTime != null && Long.parseLong(trashTime) > undeployContext.getUndeployStartTime()) {
                markParentPathsAsProtectedFromDeletion(undeployContext, child);
                return false;
            }
            return true;
        }
        return true;
    }

    private void markParentPathsAsProtectedFromDeletion(UndeployContext undeployContext, ItemInfo child) {
        Stream.iterate(child.getRepoPath().getParent(), parent -> parent != null && !parent.getPath().isEmpty(),
                RepoPath::getParent).forEach(undeployContext::addPath);
    }

    private boolean deleteSystemFolderItem(UndeployContext parentContext, RepoPath repoPath) {
        UndeployContext undeployContext = new UndeployContext(new BasicStatusHolder(), false,
                System.currentTimeMillis(), false, parentContext.isFullRepoContentDeletion());
        return deletePath(undeployContext, repoPath, true);
    }

    private boolean deletePath(UndeployContext undeployContext,
            RepoPath repoPath, boolean triggerMetadataCalculation) {
        boolean deleted = deleteSingleLeaf(repoPath, triggerMetadataCalculation, undeployContext);
        if (deleted && !undeployContext.getPathsToProtect().contains(repoPath.getParent())) {
            pruneService.prune(repoPath.getParent());
        }
        return deleted;
    }

    /**
     * Checks that leaf deletion is allowed and if so, deletes in a single transaction
     *
     * @param repoPath - file or empty folder (leaf) repo path
     * @return true if the given repoPath was successfully deleted
     */
    private boolean deleteSingleLeaf(@Nonnull RepoPath repoPath, boolean triggerMetadataCalculation,
            @Nonnull UndeployContext undeployContext) {
        if (undeployContext.isShouldProtectPathDeletion() && !authService.canDelete(repoPath)) {
            AccessLogger.deleteDenied(repoPath);
            return false;
        }
        boolean deleted = undeployContext.isFullRepoContentDeletion() ?
                getTransactionalMe().undeployInTx(repoPath, triggerMetadataCalculation,
                        undeployContext.getStatusHolder(), true) :
                getTransactionalMe().undeployInTx(repoPath, triggerMetadataCalculation,
                        undeployContext.getStatusHolder());
        if (deleted) {
            incrementDeletedCount(undeployContext.getStatusHolder());
        }
        return deleted;
    }

    @Override
    public boolean undeployInTx(@Nonnull RepoPath repoPath, boolean triggerMetadataCalculation,
            @Nonnull BasicStatusHolder statusHolder) {
        return undeployInTx(repoPath, triggerMetadataCalculation, statusHolder, false);
    }

    @Override
    public boolean undeployInTx(@Nonnull RepoPath repoPath, boolean triggerMetadataCalculation,
            @Nonnull BasicStatusHolder statusHolder, boolean fullRepoContentDeletion) {
        StoringRepo<?, ?> storingRepo = storingRepositoryByKey(repoPath.getRepoKey());
        if (storingRepo == null) {
            log.debug("Repository '{}' not found, undeploy skipped", repoPath.getRepoKey());
            statusHolder.status(REPOSITORY_NOT_FOUND.getReason(repoPath.getRepoKey()),
                    REPOSITORY_NOT_FOUND.getStatusCode(), log);
            return true;
        }
        try {
            DeleteContext deleteContext = new DeleteContext(repoPath)
                    .triggerMetadataCalculation(triggerMetadataCalculation)
                    .fullRepoContentDeletion(fullRepoContentDeletion);
            if (repoPath.getRepoKey().equals(USAGE_REPO_KEY) || repoPath.getRepoKey().equals(FULL_SYNC_REPO_KEY)) {
                storingRepo.undeploy(deleteContext.skipTrashcan(true));
            } else {
                storingRepo.undeploy(deleteContext, statusHolder);
            }
            return true;
        } catch (CancelException e) {
            statusHolder.errorWithDifferentLogLevel(
                    "Undeploy of path: '%s' was canceled: %s".formatted(repoPath.toPrettyPath(), e.getMessage()),
                    e.getErrorCode(), e, log, WARN);
            return false;
        }
    }

    @SneakyThrows
    @Override
    public RepositoriesCache rebuildRepositoriesInTransaction() {
        RepositoriesCacheBuilder repositoriesCacheBuilder = new RepositoriesCacheBuilder();
        StopWatch stopWatch = new StopWatch();
        RepositoriesCache repositoriesCache = measureTime(stopWatch,
                repositoriesCacheBuilder::rebuildRepositories);
        log.info("Rebuilt repositories cache in {} ms", stopWatch.getTotalTimeMillis());
        return repositoriesCache;
    }

    @Override
    public BasicStatusHolder undeploy(RepoPath repoPath) {
        return undeploy(repoPath, true);
    }

    @Override
    public StatusHolder undeployVersionUnits(Set<VersionUnit> versionUnits) {
        BasicStatusHolder status = new BasicStatusHolder();
        InternalRepositoryService transactionalMe = getTransactionalMe();

        Set<RepoPath> pathsForMavenMetadataCalculation = Sets.newHashSet();

        for (VersionUnit versionUnit : versionUnits) {
            Set<RepoPath> repoPaths = versionUnit.getRepoPaths();
            if (repoPaths.stream().filter(authService::canDelete).count() != repoPaths.size()) {
                status.warn(USER + securityService.currentUsername() + " doesn't have permission to delete one or more "
                        + "the paths associated with module '" + versionUnit.getModuleInfo().getPrettyModuleId()
                        + "', it will not be removed.", log);
                continue;
            }
            for (RepoPath repoPath : repoPaths) {
                BasicStatusHolder holder = transactionalMe.undeploy(repoPath, false, true);
                status.merge(holder);
                if (NamingUtils.isPom(repoPath.getPath())) {
                    // We need to re-calculate the artifact id folder (which is the grandparent of the pom file)
                    RepoPath grandparentFolder = RepoPathUtils.getAncestor(repoPath, 2);
                    if (grandparentFolder != null) {
                        pathsForMavenMetadataCalculation.add(grandparentFolder);
                    }
                }
            }
        }
        // Check to make sure of existence, might have been removed through the iterations of the version units

        if (!ConstantValues.mvnMetadataCalculationSkipDeleteEvent.getBoolean()) {
            pathsForMavenMetadataCalculation.stream()
                    .filter(this::exists)
                    .forEach(path -> mavenMetadataService
                            .calculateMavenMetadataAsync(new MavenMetadataWorkItem(path, false)));
        }
        return status;
    }

    @Override
    public int zap(RepoPath repoPath) {
        if (!canZap(repoPath)) {
            String errMessage =
                    String.format("Forbidden REST call from user: '%s'", securityService.currentUsername());
            log.error(errMessage);
            throw new ForbiddenException(errMessage);
        }
        int zappedItems;
        LocalRepo<?,?> localRepo = getLocalRepository(repoPath);
        if (localRepo.isCache()) {
            LocalCacheRepo cache = (LocalCacheRepo) localRepo;
            zappedItems = zapService.zap(repoPath, cache, System::currentTimeMillis);
        } else {
            String msg = String.format("Got a zap request on a non-local-cache node '%s'.", repoPath);
            throw new BadRequestException(msg);
        }
        return zappedItems;
    }

    @Override
    public void zapAsSystem(RepoPath repoPath) {
        securityService.doAsSystem(() -> getTransactionalMe().zap(repoPath));
    }

    @Override
    public RepoResource zapFileFromVirtual(RepoPath repoPath) {
        String repoKey = repoPath.getRepoKey();
        VirtualRepo virtualRepo = virtualRepositoryByKey(repoKey);
        if (virtualRepo == null) {
            throw new IllegalArgumentException("Repository '" + repoKey + "' is not a virtual repository");
        }
        return setUpdatedTimeIfFileExists(virtualRepo, repoPath.getPath(), 0);
    }

    @Override
    public Set<String> getAllRepoKeys() {
        return cache.get().allRepoKeysCache;
    }

    @Override
    public Set<String> getAllProjectSupportedRepoKeys() {
        Set<String> allRepoKeys = getLocalFederatedAndRemoteRepoConfigs()
                .stream()
                .filter(repo -> repo.getPackageType() != RepoType.ReleaseBundles)
                .filter(repo -> repo.getPackageType() != BuildInfo)
                .filter(repo -> repo.getPackageType() != RepoType.Support)
                .filter(repo -> repo.getPackageType() != RepoType.Distribution)
                .map(RepoConfig::getKey)
                .filter(key -> !key.equals(IN_TRANSIT_REPO_KEY))
                .collect(Collectors.toSet());

        Set<String> allVirtualRepos = getVirtualRepoDescriptors()
                .stream()
                .map(RepoBaseDescriptor::getKey)
                .collect(Collectors.toSet());
        allRepoKeys.addAll(allVirtualRepos);
        return allRepoKeys;
    }

    @Override
    public Map<Character, List<String>> getAllRepoKeysByFirstCharMapAndProject(@Nullable String projectKey) {
        RepositoriesCache c = cache.get();
        Map<Character, List<String>> map = getAllRepoKeysByFirstCharMap(c);
        if (ProjectsUtils.isDefaultProject(projectKey)) {
            return map;
        }
        Function<String, Boolean> projectFilter = createProjectReposFilter(projectKey);
        return map.entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        repoList -> repoList.getValue().stream().filter(projectFilter::apply)
                                //.sorted(String::compareToIgnoreCase) Pre-sorted on getAllRepoKeysByFirstCharMap
                                .toList()));
    }

    private Map<Character, List<String>> getAllRepoKeysByFirstCharMap(RepositoriesCache c) {
        Map<Character, List<String>> map = cachedReposByFirstCharMap.get();
        if (map == null) {
            reposByFirstCharMapLock.lock();
            try {
                map = cachedReposByFirstCharMap.get();
                if (map == null) {
                    map = computeReposByFirstCharMap(c.allRepoKeysCache, c.supportBundlesRepo.getKey());
                    cachedReposByFirstCharMap.set(map);
                }
            } finally {
                reposByFirstCharMapLock.unlock();
            }
        }
        return map;
    }

    private Map<Character, List<String>> computeReposByFirstCharMap(Set<String> allRepoKeys,
            String supportBundlesKey) {
        Map<Character, List<String>> byFirstChar = HashMap.newHashMap(26);
        for (String repoKey : allRepoKeys) {
            if (shouldExcludeFromReposByFirstCharMap(repoKey, supportBundlesKey)) {
                continue;
            }
            byFirstChar
                    .computeIfAbsent(Character.toLowerCase(repoKey.charAt(0)), k -> new ArrayList<>())
                    .add(repoKey);
        }
        for (List<String> list : byFirstChar.values()) {
            list.sort(String::compareToIgnoreCase);
        }
        return byFirstChar;
    }

    private static boolean shouldExcludeFromReposByFirstCharMap(String repoKey, String supportBundlesKey) {
        return SystemRepos.SystemRepoConfig.isInvisibleSystemRepo(repoKey) || supportBundlesKey.equals(repoKey);
    }

    @Override
    public boolean repoExists(String repoKey) {
        return repositoryByKey(repoKey) != null;
    }

    @Override
    public List<RealRepoConfig> getLocalFederatedAndRemoteRepoConfigs() {
        return repositoryConfigService.getRepoConfigsByTypes(Set.of(LOCAL, REMOTE, FEDERATED))
                .stream()
                .map(RealRepoConfig.class::cast)
                .collect(Collectors.toList());
    }

    @Override
    public List<RealRepoConfig> getLocalFederatedAndRemoteRepoConfigsByProject(@Nullable String projectKey) {
        Stream<RepoConfig> repoConfigs =
                repositoryConfigService.getRepoConfigsByTypes(Set.of(LOCAL, REMOTE, FEDERATED)).stream();
        repoConfigs = filterByProject(repoConfigs, projectKey);
        return repoConfigs.map(RealRepoConfig.class::cast).toList();
    }

    @Override
    public boolean isAnonAccessEnabled() {
        return securityService.isAnonAccessEnabled();
    }

    @Override
    public Repo repositoryByKey(String key) {
        Repo repo = null;
        RepositoriesCache repositoriesCache = cache.get();
        if (repositoriesCache.localRepositoriesMap.containsKey(key)) {
            repo = (repositoriesCache.localRepositoriesMap.get(key));
        } else if (repositoriesCache.localCacheRepositoriesMap.containsKey(key)) {
            repo = repositoriesCache.localCacheRepositoriesMap.get(key);
        } else if (repositoriesCache.remoteRepositoriesMap.containsKey(key)) {
            repo = repositoriesCache.remoteRepositoriesMap.get(key);
        } else if (repositoriesCache.virtualRepositoriesMap.containsKey(key)) {
            repo = repositoriesCache.virtualRepositoriesMap.get(key);
        } else if (repositoriesCache.federatedRepositoriesMap.containsKey(key)) {
            repo = repositoriesCache.federatedRepositoriesMap.get(key);
        } else if (repositoriesCache.releaseBundlesRepoMap.containsKey(key)) {
            repo = repositoriesCache.releaseBundlesRepoMap.get(key);
        } else if (repositoriesCache.trashcan != null && repositoriesCache.trashcan.getKey().equals(key)) {
            repo = repositoriesCache.trashcan;
        } else if (repositoriesCache.supportBundlesRepo != null &&
                repositoriesCache.supportBundlesRepo.getKey().equals(key)) {
            repo = repositoriesCache.supportBundlesRepo;
        }
        return repo;
    }

    @Override
    public LocalRepo<?, ?> localOrFederatedRepositoryByKey(String key) {
        RepositoriesCache repositoriesCache = cache.get();
        if (RepoPathUtils.isTrash(key)) {
            return repositoriesCache.trashcan;
        }
        if (SUPPORT_BUNDLE_REPO_KEY.equals(key)) {
            return repositoriesCache.supportBundlesRepo;
        }
        LocalRepo<?, ?> localRepo = repositoriesCache.localRepositoriesMap.get(key);
        if (localRepo == null) {
            localRepo = repositoriesCache.federatedRepositoriesMap.get(key);
        }
        return localRepo;
    }

    @Override
    public RemoteRepo remoteRepositoryByKey(String key) {
        return cache.get().remoteRepositoriesMap.get(key);
    }

    @Override
    public VirtualRepo virtualRepositoryByKey(String key) {
        return cache.get().virtualRepositoriesMap.get(key);
    }

    @Override
    public ReleaseBundlesRepo releaseBundleRepositoryByKey(String key) {
        return cache.get().releaseBundlesRepoMap.get(key);
    }

    @Override
    @Nullable
    public LocalRepo localFederatedOrCachedRepositoryByKey(String key) {
        LocalRepo localRepo = localOrFederatedRepositoryByKey(key);
        if (localRepo == null) {
            RemoteRepo remoteRepo = remoteRepositoryByRemoteOrCacheKey(key);
            if (remoteRepo != null && remoteRepo.isStoreArtifactsLocally()) {
                localRepo = remoteRepo.getLocalCacheRepo();
            }
        }
        if (localRepo == null) {
            localRepo = releaseBundleRepositoryByKey(key);
        }
        if (localRepo == null) {
            localRepo = federatedRepositoryByKey(key);
        }
        return localRepo;
    }

    private RemoteRepo remoteRepositoryByRemoteOrCacheKey(String key) {
        RemoteRepo remoteRepo = remoteRepositoryByKey(key);
        if (remoteRepo == null) {
            //Try to get cached repositories
            int idx = key.lastIndexOf(RepoPath.REMOTE_CACHE_SUFFIX);
            //Get the cache either by <remote-repo-name> or by <remote-repo-name>-cache
            if (idx >= 1 && idx + RepoPath.REMOTE_CACHE_SUFFIX.length() == key.length()) {
                remoteRepo = remoteRepositoryByKey(key.substring(0, idx));
            }
        }

        return remoteRepo;
    }

    @Override
    @SuppressWarnings({"unchecked"})
    public <R extends Repo> RepoRepoPath<R> getRepoRepoPath(RepoPath repoPath) {
        String repoKey = repoPath.getRepoKey();
        if (RepoPathUtils.isTrash(repoKey)) {
            return new RepoRepoPath<>((R) cache.get().trashcan, repoPath);
        }
        R repo = (R) repositoryByKey(repoKey);
        if (repo == null) {
            throw new IllegalArgumentException("Repository '" + repoKey + "' not found!");
        }
        return new RepoRepoPath<>(repo, repoPath);
    }

    @Override
    public StoringRepo storingRepositoryByKey(String key) {
        LocalRepo localRepo = localFederatedOrCachedRepositoryByKey(key);
        if (localRepo != null) {
            return localRepo;
        }
        VirtualRepo virtualRepo = virtualRepositoryByKey(key);
        if (virtualRepo != null) {
            return virtualRepo;
        }
        FederatedRepo federatedRepo = federatedRepositoryByKey(key);
        if (federatedRepo != null) {
            return federatedRepo;
        }
        return releaseBundleRepositoryByKey(key);
    }

    @Override
    public FederatedRepo federatedRepositoryByKey(String key) {
        return cache.get().federatedRepositoriesMap.get(key);
    }

    @Override
    public boolean isWriteLocked(RepoPath repoPath) {
        StoringRepo storingRepo = storingRepositoryByKey(repoPath.getRepoKey());
        return storingRepo != null && storingRepo.isWriteLocked(repoPath);
    }

    @Override
    public List<ItemInfo> getOrphanItems(RepoPath repoPath) {
        return fileService.getOrphanItems(repoPath);
    }

    @Override
    public void reloadConfigurationLazy() {
        cache.promoteVersion();
    }

    @Override
    public List<LocalCacheRepoConfig> getCachedRepoConfigs() {
        return repositoryConfigService.getRepoConfigsByType(REMOTE).stream()
                .map(RemoteRepoConfig.class::cast)
                .filter(remoteRepoConfig -> remoteRepoConfig.getRepoTypeConfig().isStoreArtifactsLocally())
                .map(LocalCacheRepoConfig::create)
                .toList();
    }

    @Override
    public List<LocalCacheRepoDescriptor> getCachedRepoDescriptorsByProject(@Nullable String projectKey) {
        Function<String, Boolean> projectFilter = createProjectReposFilter(projectKey);
        return cache.get().localCacheRepositoriesMap.values().stream()
                .filter(repo -> projectFilter.apply(RepoPathUtils.removeDashCache(repo.getKey())))
                .map(Repo::getDescriptor)
                .collect(Collectors.toList());
    }

    @Override
    public RepoDescriptor repoDescriptorByKey(String key) {
        Repo repo = repositoryByKey(key);
        return repo != null ? repo.getDescriptor() : null;
    }

    @Override
    public RepoConfig repoConfigByKey(String key) {
        return getRepoConfigByKey(key);
    }

    @Override
    public LocalRepoDescriptor localOrFederatedRepoDescriptorByKey(String key) {
        LocalRepo localRepo = localOrFederatedRepositoryByKey(key);
        return localRepo != null ? (LocalRepoDescriptor) localRepo.getDescriptor() : null;
    }

    @Override
    public LocalRepoDescriptor localFederatedOrCachedRepoDescriptorByKey(String key) {
        LocalRepo localRepo = localFederatedOrCachedRepositoryByKey(key);
        return localRepo != null ? (LocalRepoDescriptor) localRepo.getDescriptor() : null;
    }

    @Override
    public LocalRepoDescriptor localFederatedCachedRepoDescriptorByKey(String repoKey) {
        return localFederatedOrCachedRepoDescriptorByKey(repoKey);
    }

    @Override
    public LocalConfig localFederatedOrCachedRepoConfigByKey(String key) {
        if (StringUtils.isBlank(key)) {
            return null;
        }
        return Optional.ofNullable(repositoryConfigService.getLocalOrFederatedRepoConfigByKey(key))
                .orElseGet(() -> Optional.ofNullable(localFederatedOrCachedRepositoryByKey(key))
                        .map(LocalRepo::getRepoConfig)
                        .map(LocalConfig.class::cast)
                        .orElse(null));
    }

    @Override
    public LocalConfig localOrFederatedRepoConfigByKey(String key) {
        if (StringUtils.isBlank(key)) {
            return null;
        }
        return Optional.ofNullable(repositoryConfigService.getLocalOrFederatedRepoConfigByKey(key))
                .orElseGet(() -> {
                    if (isTrash(key)) {
                        return Optional.ofNullable(cache.get().trashcan)
                                .map(trashcanRepo -> (LocalConfig) trashcanRepo.getRepoConfig())
                                .orElse(null);
                    }
                    if (isSupportBundle(key)) {
                        return Optional.ofNullable(cache.get().supportBundlesRepo)
                                .map(supportBundlesRepo -> (LocalConfig) supportBundlesRepo.getRepoConfig())
                                .orElse(null);
                    }
                    return null;
                });
    }

    @Override
    public RemoteRepoDescriptor remoteRepoDescriptorByKey(String key) {
        RemoteRepo remoteRepo = remoteRepositoryByKey(key);
        return remoteRepo != null ? (RemoteRepoDescriptor) remoteRepo.getDescriptor() : null;
    }

    @Override
    public RemoteRepoConfig remoteRepoConfigByKey(String key) {
        return repositoryConfigService.getRemoteRepoConfigByKey(key);
    }

    @Override
    public boolean isRemoteRepoOffline(String key) {
        RemoteRepo remoteRepo = remoteRepositoryByKey(key);
        return remoteRepo == null || ((RemoteRepoDescriptor) remoteRepo.getDescriptor()).isOffline();
    }

    @Override
    public String remoteRepoActualNameByKey(String key) {
        RemoteRepo remoteRepo = remoteRepositoryByKey(key);
        if (remoteRepo == null) {
            return "";
        }
        RepoPath repoPath = remoteRepo.getRepoPath(remoteRepo.getUrl());

        return repoPath.getName();
    }

    @Override
    public List<VirtualRepoDescriptor> getVirtualRepoDescriptors() {
        return getVirtualRepositories().stream()
                .map(RepoBase::getDescriptor)
                .collect(Collectors.toList());
    }

    @Override
    public boolean virtualRepoContainsTransitiveValidRepos(String repoKey) {
        VirtualRepo virtualRepo = virtualRepositoryByKey(repoKey);

        return virtualRepo != null && virtualRepo.getVirtualRepositoriesResolver().getResolvedRemoteRepos()
                .stream().anyMatch(curr -> this.isRepoTransitiveValid(curr.getKey()));
    }

    @Override
    public List<String> getTransitiveRepoKeysFromVirtual(String repoKey) {
        VirtualRepo virtualRepo = virtualRepositoryByKey(repoKey);
        int repoAmount = 0;
        List<String> transitiveKeys = new ArrayList<>();
        if (virtualRepo != null) {
            List<String> repoKeys =
                    virtualRepo.getVirtualRepositoriesResolver().getResolvedRemoteRepos().stream().map(Repo::getKey)
                            .collect(Collectors.toList());
            for (int i = 0;
                    i < repoKeys.size() && repoAmount != ConstantValues.aqlTransitiveRemoteReposLimit.getInt(); i++) {
                String currKey = repoKeys.get(i);
                if (this.isRepoTransitiveValid(currKey)) {
                    repoAmount++;
                    transitiveKeys.add(currKey);
                }
            }
        }

        return transitiveKeys;
    }

    @Override
    public void assertValidDeployPathAndPermissions(ValidDeployPathContext validDeployPathContext)
            throws RepoRejectException {
        LocalRepo repo = validDeployPathContext.getRepo();
        RepoPath repoPath = validDeployPathContext.getRepoPath();
        String path = repoPath.getPath();
        if (!repo.getKey().equals(repoPath.getRepoKey())) {
            // the repo path should point to the given repo (e.g, in case the repo path points to the remote repo)
            repoPath = InternalRepoPathFactory.create(repo.getKey(), path, repoPath.isFolder());
        }
        BasicStatusHolder status = repo.assertValidPath(repoPath, false);
        if (!status.isError()) {
            // if it is metadata, assert annotate privileges. Maven metadata is treated as regular file
            // (needs deploy permissions).
            if (NamingUtils.isMetadata(path)) {
                if (!authService.canAnnotate(repoPath)) {
                    String msg = USER + securityService.currentUsername() + " is not permitted to annotate '" +
                            path + "' on '" + repoPath + "'.";
                    status.error(msg, SC_FORBIDDEN, log);
                    AccessLogger.annotateDenied(repoPath);
                }
            } else {
                //Assert deploy privileges
                boolean canDeploy = authService.canDeploy(repoPath);
                if (!canDeploy) {
                    String msg = USER + securityService.currentUsername() + " is not permitted to deploy '" +
                            path + "' into '" + repoPath + "'.";
                    status.error(msg, SC_FORBIDDEN, log);
                    AccessLogger.deployDenied(repoPath);
                }
            }
            if (!status.isError()) {
                PathDeletionContext pathDeletionContext = new PathDeletionContext.Builder(repo, path, status)
                        .assertOverwrite(true).requestSha1(validDeployPathContext.getRequestSha1())
                        .requestSha2(validDeployPathContext.getRequestSha2())
                        .forceExpiryCheck(validDeployPathContext.isForceExpiryCheck()).build();
                assertDeleteIncludingRoot(pathDeletionContext);
            }

            if (!status.isError()) {
                // Assert that we don't exceed the user configured maximum storage size
                assertStorageQuota(repo.getKey(), status, validDeployPathContext.getContentLength());
            }
        }
        if (status.isError()) {
            if (status.getException() != null) {
                Throwable throwable = status.getException();
                if (throwable instanceof RepoRejectException) {
                    throw (RepoRejectException) throwable;
                }
                throw new RepoRejectException(throwable);
            }
            throw new RepoRejectException(status.getStatusMsg(), status.getStatusCode());
        }
    }

    private void assertStorageQuota(MutableStatusHolder statusHolder, long contentLength) {
        StorageQuotaInfo info = storageService.getStorageQuotaInfo(contentLength);
        if (info == null) {
            return;
        }

        if (info.isLimitReached()) {
            // Note: don't display the disk usage in the status holder - this message is written back to the user
            statusHolder.error(
                    "Datastore disk usage is too high. Contact your Artifactory administrator to add additional " +
                            "storage space or change the disk quota limits.", SC_REQUEST_TOO_LONG, log
            );

            log.error(info.getErrorMessage());
        } else if (info.isWarningLimitReached()) {
            log.warn(info.getWarningMessage());
        }
    }

    @Override
    public <T extends RemoteRepoDescriptor, S extends RemoteRepoConfig> ResourceStreamHandle downloadAndSave(
            InternalRequestContext requestContext,
            RemoteRepo<T, S> remoteRepo, RepoResource res) throws IOException, RepoRejectException {
        return remoteRepo.downloadAndSave(requestContext, res);
    }

    @Override
    public RepoResource unexpireIfExists(LocalRepo<?, ?> localCacheRepo, String path) {
        RepoResource resource = internalUnexpireIfExists(localCacheRepo, path, System.currentTimeMillis());
        if (resource == null) {
            return new UnfoundRepoResource(InternalRepoPathFactory.create(localCacheRepo.getKey(), path),
                    "Object is not in cache");
        }
        return resource;
    }

    @Override
    @Nonnull
    public RepoResource unexpireIfExists(VirtualRepo virtualRepo, String path) {
        RepoResource resource = setUpdatedTimeIfFileExists(virtualRepo, path, System.currentTimeMillis());
        if (resource == null) {
            return new UnfoundRepoResource(InternalRepoPathFactory.create(virtualRepo.getKey(), path),
                    "Object is not in cache");
        }
        return resource;
    }

    @Override
    public ResourceStreamHandle unexpireAndRetrieveIfExists(InternalRequestContext requestContext,
            LocalRepo localCacheRepo, String path) throws IOException, RepoRejectException {
        RepoResource resource = internalUnexpireIfExists(localCacheRepo, path, System.currentTimeMillis());
        if (resource != null && resource.isFound()) {
            return localCacheRepo.getResourceStreamHandle(requestContext, resource);
        }
        return null;
    }

    @Override
    public ResourceStreamHandle getResourceStreamHandle(InternalRequestContext requestContext, Repo repo,
            RepoResource res) throws IOException, RepoRejectException {
        if (res instanceof ResolvedResource) {
            RepoRequests.logToContext("The requested resource is already resolved - using a string resource handle");
            // resource already contains the content - just extract it and return a string resource handle
            String content = ((ResolvedResource) res).getContent();
            return new StringResourceStreamHandle(content);
        } else {
            RepoRequests.logToContext("The requested resource isn't pre-resolved");
            RepoPath repoPath = res.getRepoPath();
            if (repo.isReal()) {
                RepoRequests.logToContext("Target repository isn't virtual - verifying that downloading is allowed");
                //Permissions apply only to real repos
                StatusHolder holder = ((RealRepo) repo).checkDownloadIsAllowed(repoPath);
                if (!holder.isError()) {
                    holder = ((RealRepo) repo).checkDownloadIsNotBlocked(requestContext, repoPath);
                }
                if (holder.isError()) {
                    RepoRequests.logToContext("Download isn't allowed - received status {} and message '%s'",
                            holder.getStatusCode(), holder.getStatusMsg());
                    throw new RepoRejectException(holder.getStatusMsg(), holder.getStatusCode());
                }
            }
            return repo.getResourceStreamHandle(requestContext, res);
        }
    }

    @Override
    public RepoResource saveResource(StoringRepo repo, SaveResourceContext saveContext)
            throws IOException, RepoRejectException, BinaryRejectedException {
        // save binary early without opening DB transaction (except in full db mode)
        SaveResourceContext newSaveContext;
        
        try {
            BinaryElement binaryInfo;
            InputStream in = saveContext.getInputStream();
            if (in instanceof BinaryServiceInputStream) {
                // input stream is from existing binary
                binaryInfo = ((BinaryServiceInputStream) in).getBinaryInfo();
            } else {
                ChecksumsInfo expectedChecksums = getExpectedChecksums(repo, saveContext);
                BinaryHeaders binaryHeaders = new BinaryHeaders(expectedChecksums, saveContext.getContentLength(),
                        repo.getKey());
                Map<String, String> headers = BinaryHeadersUtil.getExpectedHeaders(binaryHeaders);

                // Check for pre-checksummed parallel download - pass checksums via headers
                headers.putAll(saveContext.getPreCalculatedHeaders());
                binaryInfo = binaryService.addBinary(in, headers);
            }
            newSaveContext = new SaveResourceContext.Builder(saveContext).binaryInfo(binaryInfo).build();
        } catch (IOException | BinaryStorageException e) {
            saveContext.setException(e);    // signal error
            log.debug("", e);
            throw e;
        }
        if (lockResourceBeforeOpenTransaction.getBoolean()) {
            return lockAndSaveResourceInTransaction(repo, newSaveContext);
        } else {
            return getTransactionalMe().saveResourceInTransaction(repo, newSaveContext);
        }
    }

    /**
     * Saves a resource in a transaction, ensuring that the resource is locked if necessary.
     * If we're already running in a transaction, we will not try to lock the resource again.
     * Since the database connection is already open within the current transaction,
     * attempting to acquire an additional lock may be redundant and could lead to unnecessary waits.
     * Instead, the status remains "idle in transaction".
     * and in case we are running in non HA mode we will skip the lock attempt as well,
     * since we are not using the distributed lock's mechanism.
     *
     * @param repo        the repository where the resource will be saved
     * @param saveContext the context containing the resource to be saved
     * @return the saved resource
     * @throws IOException             if an I/O error occurs during saving
     * @throws RepoRejectException     if the resource cannot be locked or saved
     * @throws BinaryRejectedException if the binary data is rejected
     */
    private RepoResource lockAndSaveResourceInTransaction(StoringRepo repo, SaveResourceContext saveContext)
            throws IOException, RepoRejectException, BinaryRejectedException {
        if (isLockingNotNeeded()) {
            return getTransactionalMe().saveResourceInTransaction(repo, saveContext);
        }
        String category = saveContext.getRepoResource().getRepoKey();
        String key = saveContext.getRepoResource().getRepoPath().getPath();
        try {
            tryToLockResource(category, key);
            // If another thread wrote to the same location while this thread was waiting on the lock,
            // we need to clear the cache to ensure fresh data is fetched from the DB.
            ReposItemsThreadLocalCacheService.invalidateCache(repo.getKey());
            return getTransactionalMe().saveResourceInTransaction(repo, saveContext);
        } catch (TimeoutException e) {
            log.warn("Failed to lock resource: {}", saveContext.getRepoResource().getRepoPath().toPath(), e);
            throw new RepoRejectException(
                    "Failed to lock resource: " + saveContext.getRepoResource().getRepoPath().toPath());
        } finally {
            releaseLock(category, key);
        }
    }

    private boolean isLockingNotNeeded() {
        boolean isTxActive = TransactionSynchronizationManager.isActualTransactionActive();
        HaAddon haAddon = addonsManager.addonByType(HaAddon.class);
        return isTxActive || !haAddon.isHaEnabled();
    }

    private void releaseLock(String category, String key) {
        if (dbLocksService.isLockedByMe(category, key)) {
            dbLocksService.unlock(category, key, ContextHelper.get().getServerId());
        }
    }

    private void tryToLockResource(String category, String key) throws TimeoutException {
        dbLocksService.acquireLock(category, key, ContextHelper.get().getServerId(), locksTimeoutSecs.getLong(),
                SECONDS);
    }

    private ChecksumsInfo getExpectedChecksums(StoringRepo repo, SaveResourceContext saveContext) {
        //Expected checksums are passed on the RepoResource, use them only if the checksum policy requires it.
        return repo.getChecksumPolicy().shouldVerifyBadClientChecksum()
                ? saveContext.getRepoResource().getInfo().getChecksumsInfo() : null;
    }

    @Override
    public RepoResource saveResourceInTransaction(StoringRepo repo, SaveResourceContext saveContext)
            throws IOException, RepoRejectException {
        return repo.saveResource(saveContext);
    }

    @Override
    public VersionSearchResults getVersionUnitsUnder(RepoPath repoPath) {
        VersionUnitSearchControls controls = new VersionUnitSearchControls(repoPath);
        return searchService.searchVersionUnits(controls);
    }

    @Override
    public long getArtifactCount() {
        return fileService.getFilesCount();
    }

    @Override
    public List<VirtualRepoDescriptor> getVirtualReposContainingRepo(RepoDescriptor repoDescriptor) {
        return getVirtualReposContainingRepo(repoDescriptor, Integer.MAX_VALUE);
    }

    @Override
    public List<VirtualRepoDescriptor> getVirtualReposContainingRepo(RepoDescriptor repoDescriptor, int depth) {
        RepoDescriptor descriptor = repoDescriptor;
        if (repoDescriptor instanceof LocalCacheRepoDescriptor localCacheRepoDescriptor) {
            //VirtualRepoResolver does not directly support local cache repos, so if the items descriptor is a cache,
            //We extract the caches remote repo, and use it instead
            descriptor = localCacheRepoDescriptor.getRemoteRepo();
        }
        List<VirtualRepoDescriptor> reposToDisplay = new ArrayList<>();
        List<VirtualRepoDescriptor> virtualRepos = getVirtualRepoDescriptors();
        for (VirtualRepoDescriptor virtualRepo : virtualRepos) {
            if (packageSpecification.isVirtualCanContainRepoWithType(virtualRepo.getType(), descriptor.getType())) {
                VirtualRepoResolver resolver = new VirtualRepoResolver(virtualRepo, depth);
                if (resolver.contains(descriptor)) {
                    reposToDisplay.add(virtualRepo);
                }
            }
        }
        return reposToDisplay;
    }

    @Override
    public boolean hasAnyDeployableRepo() {
        List<String> localAndFederatedRepoKeys =
                repositoryConfigService.getLocalAndFederatedRepoConfigsExcludingInfoRepoKeys();
        if (localAndFederatedRepoKeys.isEmpty()) {
            return hasDeployableBuildInfoRepo();
        }
        if (securityService.isAdmin()) {
            return true;
        }
        // The current permission check is an improvement based on previous logic,
        // but still avoids using authService for permission checks due to performance issues. See RTDEV-47484
        Set<String> repoKeys = new HashSet<>(localAndFederatedRepoKeys);
        boolean hasDeployableLocalOrFederatedRepo = authService.getRepoPermissionTargets(ArtifactoryPermission.DEPLOY)
                .stream()
                .anyMatch(repoPermissionTargetInfo ->
                        repoPermissionTargetInfo.getRepoKeys().stream()
                                .anyMatch(key ->
                                        PermissionTarget.ANY_REPO.equals(key) ||
                                                PermissionTarget.ANY_LOCAL_REPO.equals(key) ||
                                                repoKeys.contains(key)));
        return hasDeployableLocalOrFederatedRepo || hasDeployableBuildInfoRepo();
    }

    private boolean hasDeployableBuildInfoRepo() {
        LocalConfig localConfig = localOrFederatedRepoConfigByKey(buildFacade.getBuildInfoRepoForDefaultProject());
        return localConfig != null && authService.hasBuildPermission(localConfig.getKey(),
                ArtifactoryPermission.DEPLOY);
    }

    @Override
    public boolean isLocalFederatedOrCachedRepoPathAccepted(RepoPath repoPath) {
        LocalRepo repo = getLocalFederatedOrCachedRepository(repoPath);
        return repo == null || repo.accepts(repoPath);
    }

    @Override
    public boolean isRepoPathAccepted(RepoPath repoPath) {
        Repo repo = repositoryByKey(repoPath.getRepoKey());
        return !(repo instanceof RepoBase) || ((RepoBase) repo).accepts(repoPath);
    }

    @Override
    public boolean isRepoPathVisible(RepoPath repoPath) {
        return (repoPath != null) && authService.canRead(repoPath) &&
                (isLocalFederatedOrCachedRepoPathAccepted(repoPath) || authService.canAnnotate(repoPath));
    }

    @Override
    public boolean isLocalFederatedOrCachedRepoPathAcceptedOrCanAnnotate(RepoPath repoPath) {
        return (repoPath != null) &&
                (isLocalFederatedOrCachedRepoPathAccepted(repoPath) || authService.canAnnotate(repoPath));
    }

    @Override
    public boolean isRepoPathHandled(RepoPath repoPath) {
        String path = repoPath.getPath();
        if (repoPath.isRoot()) {
            return true;
        }
        LocalRepo repo = getLocalFederatedOrCachedRepository(repoPath);
        return repo.handlesReleaseSnapshot(path);
    }

    @Override
    public List<RemoteRepoDescriptor> getSharedRemoteRepoConfigs(String remoteUrl, Map<String, String> headersMap) {

        List<RemoteRepoDescriptor> remoteRepos = Lists.newArrayList();
        List<RepoDetails> remoteReposDetails = getSharedRemoteRepoDetails(remoteUrl, headersMap);
        ProxyDescriptor defaultProxy = centralConfigService.getMutableDescriptor().getDefaultProxy();
        for (RepoDetails remoteRepoDetails : remoteReposDetails) {
            String configurationUrl = remoteRepoDetails.getConfiguration();
            if (StringUtils.isNotBlank(configurationUrl)) {
                RemoteRepoDescriptor remoteRepoConfig = getSharedRemoteRepoConfig(configurationUrl, headersMap);
                if (remoteRepoConfig != null) {
                    if (defaultProxy != null) {
                        ((HttpRepoDescriptor) remoteRepoConfig).setProxy(defaultProxy);
                    }
                    RepoLayout repoLayout = remoteRepoConfig.getRepoLayout();

                    //If there is no contained layout or if it doesn't exist locally, just add the default
                    if ((repoLayout == null) ||
                            centralConfigService.getDescriptor().getRepoLayout(repoLayout.getName()) == null) {
                        remoteRepoConfig.setRepoLayout(RepoLayoutUtils.MAVEN_2_DEFAULT);
                    }

                    RepoLayout remoteRepoLayout = remoteRepoConfig.getRemoteRepoLayout();
                    //If there is contained layout doesn't exist locally, remove it
                    if ((remoteRepoLayout != null) &&
                            centralConfigService.getDescriptor().getRepoLayout(remoteRepoLayout.getName()) == null) {
                        remoteRepoConfig.setRemoteRepoLayout(null);
                    }

                    remoteRepos.add(remoteRepoConfig);
                }
            }
        }

        return remoteRepos;
    }

    @Override
    public Tree<ZipEntryInfo> zipEntriesToTree(RepoPath zipPath) throws IOException {
        LocalRepo localRepo = getLocalFederatedOrCachedRepository(zipPath);
        VfsFile file = localRepo.getImmutableFile(zipPath);
        ZipInputStream zin = null;
        try {
            Tree<ZipEntryInfo> tree;
            zin = new ZipInputStream(file.getStream());
            ZipEntry zipEntry;
            tree = InfoFactoryHolder.get().createZipEntriesTree();
            try {
                while ((zipEntry = zin.getNextEntry()) != null) {
                    tree.insert(InfoFactoryHolder.get().createZipEntry(zipEntry));
                }
                // IllegalArgumentException is being thrown from: java.util.zip.ZipInputStream.getUTF8String on a
                // bad archive
            } catch (IllegalArgumentException e) {
                throw new IOException(
                        "An error occurred while reading entries from zip file: " + file.getRepoPath());
            }
            return tree;
        } finally {
            IOUtils.closeQuietly(zin);
        }
    }

    @Override
    public ArchiveInputStream archiveInputStream(RepoPath zipPath) throws IOException {
        LocalRepo localRepo = getLocalFederatedOrCachedRepository(zipPath);
        String zipSuffix = zipPath.getPath().toLowerCase();
        VfsFile file = localRepo.getImmutableFile(zipPath);
        ArchiveInputStream archiveInputStream = null;
        if (file != null) {
            InputStream stream = file.getStream();
            try {
                archiveInputStream = ZipUtils.convertToArchiveInputStream(stream, zipSuffix);
            } catch (IOException e) {
                Closeables.closeQuietly(stream);
                throw e;
            }
        }
        return archiveInputStream;
    }

    @Override
    public ItemInfo getLastModified(RepoPath pathToSearch) {
        if (pathToSearch == null) {
            throw new IllegalArgumentException("Repo path cannot be null.");
        }
        if (!exists(pathToSearch)) {
            throw new ItemNotFoundRuntimeException("Could not find item: " + pathToSearch.getId());
        }

        return collectLastModified(pathToSearch);
    }

    private ItemInfo collectLastModified(RepoPath pathToSearch) {
        TreeBrowsingCriteria criteria = new TreeBrowsingCriteriaBuilder().applySecurity().build();
        ItemTree itemTree = new ItemTree(pathToSearch, criteria);
        LinkedList<ItemNode> fringe = Lists.newLinkedList();
        fringe.add(itemTree.getRootNode());
        ItemInfo lastModified = null;
        while (!fringe.isEmpty()) {
            ItemNode last = fringe.removeLast();
            if (last.hasChildren()) {
                fringe.addAll(last.getChildren());
            }
            if (!last.isFolder()) {
                if (lastModified == null || last.getItemInfo().getLastModified() > lastModified.getLastModified()) {
                    lastModified = last.getItemInfo();
                }
            }
        }
        return lastModified;
    }

    @Override
    public void touch(RepoPath repoPath) {
        if (repoPath == null) {
            throw new IllegalArgumentException("Repo path cannot be null.");
        }
        LocalRepo localOrCachedRepository = getLocalFederatedOrCachedRepository(repoPath);
        if (localOrCachedRepository == null) {
            throw new IllegalArgumentException(repoPath + " is not local or cache repository path");
        }
        MutableVfsItem mutableFsItem = localOrCachedRepository.getMutableFsItem(repoPath);
        if (mutableFsItem == null) {
            throw new ItemNotFoundRuntimeException("Could not find item: " + repoPath.getId());
        }
        mutableFsItem.setModified(System.currentTimeMillis());
    }

    @Override
    public void fixChecksums(RepoPath fileRepoPath) {
        VfsFile<?> immutableFile = getImmutableFile(fileRepoPath);
        FileInfo fileInfo = immutableFile.getInfo();
        ChecksumsInfo checksumsInfo = fileInfo.getChecksumsInfo();
        MutableVfsFile mutableFile = null;
        for (ChecksumInfo checksumInfo : checksumsInfo.getChecksums()) {
            if (!checksumInfo.checksumsMatch()) {
                if (mutableFile == null) {
                    mutableFile = getMutableFile(fileRepoPath);
                }
                mutableFile.setClientChecksum(checksumInfo.getType(), TRUSTED_FILE_MARKER);
            }
        }
    }

    private void exportAsync(@Nonnull String repoKey, ExportSettings settings) {
        MutableStatusHolder status = settings.getStatusHolder();
        Task task = TaskUtils.createManualTask(ExportJob.class, 0L, "Export Repository - " + repoKey);
        task.addAttribute(Task.REPO_KEY, repoKey);
        task.addAttribute(ExportSettingsImpl.class.getName(), settings);
        taskService.startTask(task, true);
        boolean completed = taskService.waitForTaskCompletion(task.getTaskId());
        if (!completed) {
            if (!status.isError()) {
                // Add Error of no completion
                status.error("The task " + task + " did not complete correctly", log);
            }
        }
    }

    @Override
    @Nonnull
    public LocalRepo getLocalRepository(RepoPath repoPath) {
        if (RepoPathUtils.isTrash(repoPath)) {
            return cache.get().trashcan;
        }
        String repoKey = repoPath.getRepoKey();
        LocalRepo localRepo = localFederatedOrCachedRepositoryByKey(repoKey);
        if (localRepo == null) {
            localRepo = releaseBundleRepositoryByKey(repoKey);
        }
        if (localRepo == null) {
            throw new IllegalArgumentException("Repository '" + repoKey + "' is not a local repository");
        }
        return localRepo;
    }

    @Override
    public List<String> getAllLocalRepoTypeKeys() {
        List<String> result = new ArrayList<>();
        for (LocalConfig localConfig : getLocalFederatedAndCachedRepoConfigs()) {
            result.add(localConfig.getKey());
        }
        result.addAll(getReleaseBundleRepoConfigs().stream().map(RepoConfig::getKey).toList());
        return result;
    }

    private List<LocalRepoDescriptor> getLocalRepoDescriptorsIncludingInfoRepos() {
        return cache.get().localRepositoriesMap.values().stream()
                .map(Repo::getDescriptor)
                .map(LocalRepoDescriptor.class::cast)
                .collect(Collectors.toList());
    }

    @VisibleForTesting
    RepoResource internalUnexpireIfExists(LocalRepo<?, ?> repo, String path, long currentTime) {
        RepoPath repoPath = InternalRepoPathFactory.create(repo.getKey(), path);
        RepoPath fsItemRepoPath = NamingUtils.getLockingTargetRepoPath(repoPath);
        if (isUpdateRequired(repo, currentTime, fsItemRepoPath)) {
            MutableVfsItem<?> fsItem = repo.getMutableFsItem(fsItemRepoPath);
            if (fsItem != null) {
                fsItem.setUpdated(currentTime);
            }
            return repo.getInfo(new NullRequestContext(repoPath));
        }
        return null;
    }


    /**
     * Determines if the LastUpdated should be updated.
     *
     * <p>For cache repositories with disabled retrieval cache, update only if the time since the last update exceeds
     * a configured threshold</p>
     *
     * @param repo        the local repository
     * @param currentTime the current time in milliseconds
     * @param repoPath    the file system item
     * @return true if the timestamp should be updated; false otherwise
     */
    @VisibleForTesting
    boolean isUpdateRequired(LocalRepo<?, ?> repo, long currentTime, RepoPath repoPath) {
        if (repo instanceof DbCacheRepo dbCacheRepo && dbCacheRepo.getRemoteRepo().getRetrievalCachePeriodSecs() == 0) {
            VfsItem<?> immutableFsItem = repo.getImmutableFsItem(repoPath);
            return currentTime - immutableFsItem.getInfo().getLastUpdated() > nodeLastUpdatedThresholdMillis.getLong();
        }
        return true;
    }

    private RepoResource setUpdatedTimeIfFileExists(VirtualRepo repo, String path, long timestamp) {
        // Need to release the read lock first
        RepoPath repoPath = InternalRepoPathFactory.create(repo.getKey(), path);
        RepoPath fsItemRepoPath = NamingUtils.getLockingTargetRepoPath(repoPath);
        // Write lock auto upgrade supported LockingHelper.releaseReadLock(fsItemRepoPath);
        MutableVfsFile fsItem = repo.getMutableFile(fsItemRepoPath);
        if (fsItem != null) {
            log.debug("{}: falling back to using cache entry for resource info at '{}'.", this, path);
            // Reset the resource age so it is kept being cached
            fsItem.setUpdated(timestamp);
            return repo.getInfoFromVirtualCache(new NullRequestContext(repoPath));
        }
        return null;
    }

    @VisibleForTesting
    void assertDeleteIncludingRoot(PathDeletionContext pathDeletionContext) {
        StoringRepo<?, ?> repo = pathDeletionContext.getRepo();
        String path = pathDeletionContext.getPath();
        BasicStatusHolder statusHolder = pathDeletionContext.getStatus();
        if (isForbiddenRootPathDelete(path, repo, statusHolder)) {
            return;
        }
        assertNonRootPathDelete(pathDeletionContext, repo, path, statusHolder);
    }

    // RTFACT-14918: In case the delete request is to the root of the repository, only admin will succeed.
    private boolean isForbiddenRootPathDelete(String path, StoringRepo<?, ?> repo, BasicStatusHolder statusHolder) {
        if (StringUtils.isBlank(path) &&
                !securityService.isProjectAdmin(RepoPathFactory.create(repo.getKey()))) {
            addErrorStatusLogAsInfo(statusHolder, REQUIRES_DELETE_PERMISSION, repo.getKey(),
                    securityService.currentUsername());
            return true;
        }
        return false;
    }

    @VisibleForTesting
    void assertNonRootPathDelete(PathDeletionContext pathDeletionContext, StoringRepo<?, ?> repo, String path,
            BasicStatusHolder statusHolder) {
        RepoPath repoPath = RepoPathFactory.create(repo.getKey(), path);
        if (repoPath.isRoot()) {
            return;
        }
        //Check that has delete rights to replace an exiting item
        if (repo.shouldProtectPathDeletion(pathDeletionContext)) {
            if (!authService.canDelete(repoPath)) {
                AccessLogger.deleteDenied(repoPath);
                if (centralConfigService.getDescriptor().getSecurity().isHideUnauthorizedResources()) {
                    addErrorStatusLogAsInfo(statusHolder, ARTIFACT_NOT_FOUND, repoPath.toPrettyPath());
                } else {
                    addErrorStatusLogAsInfo(statusHolder, REQUIRES_DELETE_PERMISSION,
                            repoPath.toPrettyPath(), securityService.currentUsername());
                }
            }
        }

        //For deletion (as opposed to overwrite), check that path actually exists
        if (!pathDeletionContext.isAssertOverwrite() && !repo.itemExists(repoPath.getPath())) {
            if (pathDeletionContext.isIdempotentDelete()) {
                // overwrite/republish cleanup delete - a concurrent request may have already removed
                // the artifact. The desired end-state (artifact absent) is already met, so treat the missing
                // artifact as an idempotent no-op instead of failing with a not-found error (which surfaced as an
                // HTTP 500 on concurrent PUT /api/build).
                log.debug("Idempotent delete: artifact already absent at '{}', skipping not-found error",
                        repoPath.toPrettyPath());
            } else {
                statusHolder.status(ARTIFACT_NOT_FOUND.getReason(repoPath.toPrettyPath()),
                        ARTIFACT_NOT_FOUND.getStatusCode(), log);
            }
        }

        if (pathDeletionContext.isAssertOverwrite()) {
            fireAssertOverwriteAllowedEvent(repoPath, statusHolder);
        } else {
            fireAssertDeletePathAllowedEvent(repoPath, statusHolder);
        }
    }

    private void addErrorStatusLogAsInfo(BasicStatusHolder statusHolder, ArtifactDeleteFailStatus failStatus,
            Object... params) {
        String reason = failStatus.getReason(params);
        statusHolder.setActivateLogging(false);
        statusHolder.error(reason, failStatus.getStatusCode(), log);
        log.info(reason);
        statusHolder.setActivateLogging(true);
    }

    @VisibleForTesting
    void fireAssertDeletePathAllowedEvent(RepoPath repoPath, BasicStatusHolder status) {
        storageInterceptors.assertDeletePathAllowed(repoPath, status);
    }

    @VisibleForTesting
    void fireAssertOverwriteAllowedEvent(RepoPath repoPath, BasicStatusHolder status) {
        storageInterceptors.assertOverwriteAllowed(repoPath, status);
    }

    /**
     * Remove export folders of repositories that are not part of the current backup included repositories
     * this cleanup is needed in incremental backup when a repository is excluded from the backup or removed
     */
    private void cleanupIncrementalBackupDirectory(File targetDir, List<String> reposToBackup) {
        if (!targetDir.exists()) {
            log.debug("Repositories backup directory doesn't exist: {}", targetDir.getAbsolutePath());
            return; // nothing to clean
        }
        File[] childFiles = targetDir.listFiles();
        if (childFiles == null) {
            return;
        }
        for (File childFile : childFiles) {
            String fileName = childFile.getName();
            if (fileName.endsWith(METADATA_FOLDER)) {
                continue;  // skip metadata folders, will delete them with the actual folder if needed
            }
            boolean includedInBackup = false;
            for (String repoKey : reposToBackup) {
                if (fileName.equals(repoKey)) {
                    includedInBackup = true;
                    break;
                }
            }
            if (!includedInBackup) {
                log.info("Deleting {} from the incremental backup dir since it is not part " +
                        "of the backup included repositories", childFile.getAbsolutePath());
                boolean deleted = FileUtils.deleteQuietly(childFile);
                if (!deleted) {
                    log.warn("Failed to delete {}", childFile.getAbsolutePath());
                }
                // now delete the metadata folder of the repository is it exists
                File metadataFolder = new File(childFile.getParentFile(), childFile.getName() + METADATA_FOLDER);
                if (metadataFolder.exists()) {
                    deleted = FileUtils.deleteQuietly(metadataFolder);
                    if (!deleted) {
                        log.warn("Failed to delete metadata folder {}", metadataFolder.getAbsolutePath());
                    }
                }
            }
        }
    }

    private LocalRepo getLocalFederatedOrCachedRepository(RepoPath repoPath) {
        return localFederatedOrCachedRepositoryByKey(repoPath.getRepoKey());
    }

    /**
     * check if repo exist already in case a new repo is created
     *
     * @return true if exist in cache
     */
    @Override
    public boolean isRepoExistInCache(RepoPath repoPath) {
        return getLocalFederatedOrCachedRepository(repoPath) != null;
    }

    // ----- SHA256 migration logic ----- \\
    @Override
    public void updateSha2(ChecksumCalculationWorkItem workItem) {
        String sha2 = null;
        Sha256MigrationJobDelegate delegate = workItem.getDelegate();
        String sha1 = workItem.getSha1();
        try {
            try {
                sha2 = delegate.getOrCalculateSha2(sha1);
            } catch (Sha256CalculationFatalException e) {
                //Exception downstream are already logged, just need to mark bad paths.
                delegate.handleExceptionDuringMigration(workItem.getPaths(), e);
            }
            if (StringUtils.isNotBlank(sha2)) {
                for (RepoPath path : workItem.getPaths()) {
                    //do each file in a separate (sync) transaction, we don't want long locks while the conversion runs]
                    try {
                        delegate.incrementCurrentBatchCount();
                        if (delegate.log().isDebugEnabled()) {
                            delegate.log().debug("Updating sha256 value '{}' for path {}", sha2, path.toPath());
                        }
                        getTransactionalMe().updateSha2ForPath(path, sha2);
                        delegate.incrementTotalDone();
                    } catch (ItemNotFoundRuntimeException e) {
                        delegate.markNonFatalErrorOnPaths(Lists.newArrayList(path), e.getMessage());
                        delegate.log()
                                .error("Failed to update sha256 value '" + sha2 + "' on path " + path.toPath(), e);
                        addSha2ToOrphanItem(path, sha2, delegate);
                        delegate.incrementTotalDone();
                    } catch (Exception e) {
                        delegate.markFatalErrorOnPaths(Lists.newArrayList(path), e.getMessage());
                        delegate.log()
                                .error("Failed to update sha256 value '" + sha2 + "' on path " + path.toPath(), e);
                    }
                }
            }
        } finally {
            delegate.markTaskAsFinished(sha1);
        }
    }

    private void addSha2ToOrphanItem(RepoPath path, String sha2, Sha256MigrationJobDelegate delegate) {
        try {
            getTransactionalMe().updateSha2ForPathNotFound(path, sha2);
            delegate.log()
                    .warn("Updated sha256 value '" + sha2 + "' on path " + path.toPath() +
                            " manually");
        } catch (VfsException | NullPointerException e) {
            delegate.log().debug("Failed to update sha256 value '" + sha2 + "' on path " + path.toPath() + " manually ",
                    e);
            delegate.log()
                    .error("Failed to update sha256 value '" + sha2 + "' on path " + path.toPath() + " manually",
                            e.getMessage());
        }
    }

    @Override
    public void updateSha2ForPath(RepoPath path, String sha2) {
        MutableVfsFile vfsFile = getMutableFileForMigration(path);
        //Once acquiring a write lock, double-check no one updated sha2 in the meantime (i.e. virtual cache)
        if (vfsFile != null && StringUtils.isBlank(vfsFile.getSha2())) {
            ((Sha256ConvertableMutableVfsFile) vfsFile).setSha2(sha2);
        }
    }

    @Override
    public void updateSha2ForPathNotFound(RepoPath path, String sha2) {
        MutableFileInfo mutableFileInfo = (MutableFileInfo) fileService.loadItem(path);
        mutableFileInfo.addChecksumInfo(new ChecksumInfo(ChecksumType.sha256, TRUSTED_FILE_MARKER, sha2));
        fileService.updateFile(mutableFileInfo.getId(), mutableFileInfo);
    }

    private MutableVfsFile getMutableFileForMigration(RepoPath path) {
        String repoKey = path.getRepoKey();
        //Trashcan is in local map
        VfsItemFactory storingRepo = localFederatedOrCachedRepositoryByKey(path.getRepoKey());
        if (storingRepo == null) {
            storingRepo = virtualRepositoryByKey(path.getRepoKey());
        }
        if (storingRepo == null) {
            throw new ItemNotFoundRuntimeException("Repository '" + repoKey + "' not local, cache or virtual");
        }
        return storingRepo.getMutableFile(path);
    }

    // ----- SHA256 migration logic ----- \\

    /**
     * Aggregates and unifies the given paths by parent
     *
     * @param pathsToMove        Paths to be moved\copied
     * @param targetLocalRepoKey Key of target local repo
     * @return Set of aggregated paths to move
     */
    private Set<RepoPath> aggregatePathsToMove(Set<RepoPath> pathsToMove, String targetLocalRepoKey, boolean copy) {
        // aggregate paths by parent repo path
        Multimap<RepoPath, RepoPath> pathsByParent = HashMultimap.create();
        for (RepoPath pathToMove : pathsToMove) {
            if (!pathToMove.getRepoKey().equals(targetLocalRepoKey)) {
                pathsByParent.put(pathToMove.getParent(), pathToMove);
            }
        }

        if (!moveCopyAggregatePathsEnabled.getBoolean()) {
            return new HashSet<>(pathsByParent.values());
        }

        // now for each parent check if all its files are moved, and if they do, we will move
        // the parent folder and its children instead of just the children
        Set<RepoPath> pathsToMoveIncludingParents = new HashSet<>();
        for (RepoPath parentPath : pathsByParent.keySet()) {
            Collection<RepoPath> children = pathsByParent.get(parentPath);
            if (parentPath.isRoot()) {
                // parent is the repository itself and cannot be moved, just add the children
                pathsToMoveIncludingParents.addAll(children);
            } else {
                // if the parent children count equals to the number of files to be moved, move the folder instead
                LocalRepo repository = getLocalRepository(parentPath);
                VfsFolder folder =
                        copy ? repository.getImmutableFolder(parentPath) : repository.getMutableFolder(parentPath);
                // get all the folder children using write lock
                List<VfsItem> folderChildren = folder.getImmutableChildren();
                if (folderChildren.size() == children.size()) {
                    pathsToMoveIncludingParents.add(parentPath);
                } else {
                    pathsToMoveIncludingParents.addAll(children);
                }
            }
        }
        return pathsToMoveIncludingParents;
    }

    

    /**
     * Returns a list of shared repository details
     *
     * @param remoteUrl  URL of remote Artifactory instance
     * @param headersMap Map of headers to set for client
     * @return List of shared repository details
     */
    private List<RepoDetails> getSharedRemoteRepoDetails(String remoteUrl, Map<String, String> headersMap) {
        StringBuilder urlBuilder = new StringBuilder();
        urlBuilder.append(remoteUrl);
        if (!remoteUrl.endsWith("/")) {
            urlBuilder.append("/");
        }
        urlBuilder.append(RestConstants.PATH_API).append("/").append(RepositoriesRestConstants.PATH_ROOT).
                append("?").append(RepositoriesRestConstants.PARAM_TYPE).append("=").
                append(REMOTE.name());

        try (CloseableHttpResponse response = executeGetMethod(urlBuilder.toString(), headersMap)) {
            if (response.getStatusLine().getStatusCode() == SC_OK) {
                return JacksonReader.streamAsValueTypeReference(response.getEntity().getContent(),
                        new TypeReference<List<RepoDetails>>() {
                        }
                );
            } else {
                return Lists.newArrayList();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns the shared remote repository descriptor from the given configuration URL
     *
     * @param configUrl  URL of repository configuration
     * @param headersMap Map of headers to set for client
     * @return RemoteRepoDescriptor
     */
    private RemoteRepoDescriptor getSharedRemoteRepoConfig(String configUrl, Map<String, String> headersMap) {
        try (CloseableHttpResponse response = executeGetMethod(configUrl, headersMap)) {
            if (response.getStatusLine().getStatusCode() == SC_OK) {
                return JacksonReader.streamAsValueTypeReference(response.getEntity().getContent(),
                        new TypeReference<HttpRepoDescriptor>() {
                        }
                );
            } else {
                return null;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Executes an HTTP GET method
     *
     * @param url        URL to query
     * @param headersMap Map of headers to set for client
     * @return The http response
     */
    private CloseableHttpResponse executeGetMethod(String url, Map<String, String> headersMap) throws IOException {
        HttpGet getMethod = new HttpGet(url);
        setHeader(getMethod, headersMap, HttpHeaders.USER_AGENT);
        setHeader(getMethod, headersMap, HttpHeaders.REFERER);
        return client.execute(getMethod);
    }

    /**
     * Sets the HTTP headers for the given method
     *
     * @param getMethod  Get method that should be set with the headers
     * @param headersMap Map of headers to set
     * @param headerKey  Key of header to set
     */
    private void setHeader(HttpGet getMethod, Map<String, String> headersMap, String headerKey) {
        String headerVal = HttpUtils.getHeaderIgnoreCase(headersMap, headerKey);
        if (HttpHeaders.REFERER.equalsIgnoreCase(headerKey)) {
            headerVal = HttpUtils.adjustRefererValue(headersMap, headerVal);
        }
        if (headerVal != null) {
            getMethod.setHeader(headerKey, headerVal);
        }
    }

    private void registerRepositoriesMBeans() {
        MBeanRegistrationServiceImpl registrationService =
                ContextHelper.get().beanForType(MBeanRegistrationServiceImpl.class);
        registrationService.unregisterAll(REPOSITORIES_MBEAN_TYPE);
        for (LocalConfig localConfig : getLocalFederatedAndCachedRepoConfigs()) {
            registrationService.register(new ManagedRepository(localConfig.getKey()), REPOSITORIES_MBEAN_TYPE,
                    localConfig.getKey());
        }
    }

    private void startSha2CalculationJob() {
        Task sha2Job = TaskUtils.createManualTask(Sha256MigrationJob.class, 0L);
        taskService.startTask(sha2Job, false);
    }

    @Override
    public Set<String> getAllLocalRepoKeys() {
        return getLocalFederatedAndCachedRepoConfigs().stream().map(RepoConfig::getKey).collect(Collectors.toSet());
    }

    @Override
    public Set<RepoConfig> getLocalCacheRepoConfigsByProject(String projectKey) {
        Stream<RepoConfig> repoConfigStream = cache.get().localCacheRepositoriesMap.values().stream()
                .map(Repo::getRepoConfig);
        repoConfigStream = filterByProject(repoConfigStream, projectKey);
        return repoConfigStream.collect(Collectors.toSet());
    }

    @Override
    public String getDefaultDeploymentRepoKey(String localOrVirtualRepoKey) {
        if (StringUtils.isBlank(localOrVirtualRepoKey)) {
            return null;
        }
        // TODO: 31/01/2022 why type is unknown?
        LocalRepo<?, ?> localRepo = localOrFederatedRepositoryByKey(localOrVirtualRepoKey);
        if (nonNull(localRepo)) {
            return localOrVirtualRepoKey;
        }
        VirtualRepo virtualRepo = virtualRepositoryByKey(localOrVirtualRepoKey);
        if (isNull(virtualRepo)) {
            throw new DoesNotExistException("Unable to find repo " + localOrVirtualRepoKey);
        }
        LocalRepoDescriptor defaultDeploymentRepo = virtualRepo.getDescriptor().getDefaultDeploymentRepo();
        if (isNull(defaultDeploymentRepo)) {
            throw new DoesNotExistException(
                    "Unable to find default deployment repo for virtual repo " + localOrVirtualRepoKey);
        }
        return defaultDeploymentRepo.getKey();
    }

    @Override
    public BasicStatusHolder removeRepository(String repoKey) {
        return withQueryRateLimiter(LOW_PRIORITY, () -> removeRepositoryFlow(repoKey));
    }

    @Override
    public RepoBulkRemovalReport removeRepositories(LinkedHashSet<String> repoKeys) {
        return removeRepositoryService.removeRepositories(repoKeys);
    }

    private BasicStatusHolder removeRepositoryFlow(String repoKey) {
        if (isRemoveRepositoryFlowV2Enabled()) {
            return removeRepositoryService.removeRepository(repoKey);
        }
        return deprecatedRemoveRepositoryFlow(repoKey);
    }

    private boolean isRemoveRepositoryFlowV2Enabled() {
        return ConstantValues.removeRepositoryFlowV2Enabled.getBoolean();
    }

    /**
     * @deprecated for removal soon
     */
    @Nonnull
    @Deprecated(since = "7.104.x")
    public BasicStatusHolder deprecatedRemoveRepositoryFlow(String repoKey) {
        BasicStatusHolder status = new BasicStatusHolder();
        storageInterceptors.assertDeleteRepoAllowed(repoKey, status);
        if (status.hasErrors()) {
            return status;
        }
        return removeRepositoryInternal(repoKey, status);
    }

    @Nonnull
    private BasicStatusHolder removeRepositoryInternal(String repoKey, BasicStatusHolder statusHolder) {
        prepareRepoForRemoval(repoKey);
        deleteRepositoryContent(repoKey, statusHolder);
        removeRepoFromFederatedRemoteMembers(repoKey, statusHolder);
        if (!statusHolder.isError()) {
            String message = getSuccessMsg(repoKey);
            repositoryConfigService.deleteRepoConfig(repoKey);
            statusHolder.status(message, SC_OK, log);
        }
        return statusHolder;
    }

    @Override
    public void deleteRepositoryContent(String repoKey, BasicStatusHolder statusHolder) {
        deleteRepositoryContent(repoKey, statusHolder, false);
    }

    @Override
    public void deleteRepositoryContent(String repoKey, BasicStatusHolder statusHolder,
            boolean fullRepoContentDeletion) {
        StoringRepo<?, ?> storingRepo = storingRepositoryByKey(repoKey);
        if (storingRepo == null) {
            statusHolder.status(REPOSITORY_NOT_FOUND.getReason(repoKey), REPOSITORY_NOT_FOUND.getStatusCode(), log);
            return;
        }
        if (storingRepo.isLocal()) {
            log.info("Deleting repository: '{}'s content", storingRepo.getKey());
            EventLogHintManager.put(RemoveRepo, traceId());
            try {
                undeployRepoContentMultiTransaction(storingRepo, statusHolder, fullRepoContentDeletion);
            } finally {
                EventLogHintManager.remove(RemoveRepo);
            }
        } else {
            log.debug("Skipping deletion of non-local repository: '{}'s content", storingRepo.getKey());
        }
    }

    @Nonnull
    private static InternalRepositoryConfigService getInternalRepositoryConfigService() {
        return ContextHelper.get().beanForType(InternalRepositoryConfigService.class);
    }

    @Nonnull
    private String getSuccessMsg(String repoKey) {
        boolean isVirtual = existsByType(VIRTUAL, repoKey);
        return isVirtual ?
                "Repository '%s' has been removed successfully.%n".formatted(repoKey) :
                "Repository '%s' and all its content have been removed successfully.%n".formatted(repoKey);
    }

    public void removeRepoFromFederatedRemoteMembers(String repoKey, BasicStatusHolder status) {
        removeRepoFromFederatedRemoteMembers(repoKey, status, repositoryConfigService);
    }

    private void removeRepoFromFederatedRemoteMembers(String repoKey, BasicStatusHolder status,
            RepositoryConfigService repoConfigService) {
        RepoConfig repoConfig = repoConfigService.getRepoConfigByKey(repoKey);
        if (repoConfig != null && repoConfig.getBaseConfig().isFederated()) {
            FederatedRepoAddon federatedRepoAddon = addonsManager.addonByType(FederatedRepoAddon.class);
            try {
                federatedRepoAddon.removeRepoFromFederationMembers(repoConfig);
            } catch (FederatedRemoteMembersUpdateException e) {
                status.error(e.getMessage(), SC_FAILED_DEPENDENCY, e, log);
            }
        }
    }

    @VisibleForTesting
    void prepareRepoForRemoval(String repoKey) {
        prepareRepoForRemoval(repoKey, getInternalRepositoryConfigService());
    }


    @VisibleForTesting
    void prepareRepoForRemoval(String repoKey, InternalRepositoryConfigService repoConfigService) {
        boolean toUpdate = false;
        RepoConfig repoToRemove = repoConfigService.getRepoConfigByKey(repoKey);
        if (repoToRemove instanceof RealRepoConfig realRepoConfig &&
                !realRepoConfig.getRepoTypeConfig().isBlackedOut()) {
            toUpdate = true;
            realRepoConfig.getRepoTypeConfig().setBlackedOut(true);
        }
        if (repoToRemove instanceof RemoteRepoConfig remoteRepoConfig &&
                !remoteRepoConfig.getRepoTypeConfig().isStoreArtifactsLocally()) {
            toUpdate = true;
            // Enabling -cache repo since it's possible it was enabled before and then disabled, and we don't want to
            // miss it in the delete flow
            remoteRepoConfig.getRepoTypeConfig().setStoreArtifactsLocally(true);
        }
        if (toUpdate) {
            securityService.doAsSystem(() ->
                    repoConfigService.updateRepoConfig(repoToRemove, true));
        }
    }

    @Override
    public void onLicenseLoaded() {
        createEdgeUploadsRepoIfNeeded();
        if (ConstantValues.repoCacheReloadAfterLicenseLoaded.getBoolean()) {
            log.debug("Reloading repository cache due to license load");
            cache.promoteVersion();
        }
    }

    /**
     * For Edges, create a local generic repo named EDGE_UPLOADS_REPO_KEY, to which uploads will be allowed.
     */
    private void createEdgeUploadsRepoIfNeeded() {
        if (!addonsManager.isEntitled(EntitlementType.EDGE)) {
            return;
        }
        LocalConfig edgeUploadsRepo =
                repositoryConfigService.getLocalOrFederatedRepoConfigByKey(EDGE_UPLOADS_REPO_KEY);
        if (edgeUploadsRepo != null && RepoType.Generic.equals(edgeUploadsRepo.getPackageType())) {
            log.trace("Repository '" + EDGE_UPLOADS_REPO_KEY + "' already exists");
            return;
        }
        if (repositoryConfigService.existsByRepoKey(EDGE_UPLOADS_REPO_KEY)) {
            log.warn("Failed to create repository '" + EDGE_UPLOADS_REPO_KEY +
                    "' because a repository with that key already exists");
            return;
        }

        try {
            if (USER_SYSTEM.equals(securityService.currentUsername()) &&
                    AuthenticationHelper.getAuthentication() == null) {
                // This can happen when Artifactory starts with a licence file
                securityService.doAsSystem(() -> ContextHelper.get().beanForType(SystemRepoConfigFactory.class)
                        .buildSystemRepo(EDGE_UPLOADS, EDGE_UPLOADS_REPO_KEY));
            } else {
                ContextHelper.get().beanForType(SystemRepoConfigFactory.class)
                        .buildSystemRepo(EDGE_UPLOADS, EDGE_UPLOADS_REPO_KEY);
            }
            String messageSuccess =
                    "Successfully created local repository " + EDGE_UPLOADS_REPO_KEY + " with package type " +
                            (nonNull(edgeUploadsRepo) ? edgeUploadsRepo.getPackageType() : "unknown");
            log.info(messageSuccess);
        } catch (Exception e) {
            log.error("Failed to create repository '" + EDGE_UPLOADS_REPO_KEY + "'", e);
        }
    }

    @Nonnull
    @Override
    public List<RepoPath> getExistingItems(List<RepoPath> repoPats) {
        if (repoPats.isEmpty()) {
            return Lists.newArrayList();
        }
        StoringRepo<?, ?> storingRepo = storingRepositoryByKey(repoPats.get(0).getRepoKey());
        return storingRepo.getExistingItems(repoPats);
    }

    @Override
    public FederatedRepoDescriptor federatedRepoDescriptorByKey(String repoKey) {
        if (StringUtils.isBlank(repoKey)) {
            return null;
        }
        FederatedRepo federatedRepo = cache.get().federatedRepositoriesMap.get(repoKey);
        return federatedRepo != null ? federatedRepo.getDescriptor() : null;
    }

    @Override
    public FederatedRepoConfig federatedRepoConfigByKey(String repoKey) {
        return repositoryConfigService.getFederatedRepoConfigByKey(repoKey);
    }

    /**
     * If providing a null project name or {@link ProjectsService#DEFAULT_PROJECT} then an empty filter will return
     */
    @Nonnull
    public Function<String, Boolean> createProjectReposFilter(@Nullable String projectKey) {
        if (ProjectsUtils.isDefaultProject(projectKey)) {
            return repoKey -> true;
        }
        Set<String> projectRepoKeys = projectsService.getProjectRepositoriesKeys(projectKey, false);
        return projectRepoKeys::contains;
    }

    @Override
    public List<LocalConfig> getLocalRepoConfigsByProject(String projectKey) {
        Stream<RepoConfig> repoConfigs = cache.get().localRepositoriesMap.values().stream()
                .map(Repo::getRepoConfig)
                .filter(repoConfig -> !isInfoRepo(repoConfig.getPackageType()) &&
                        !isHiddenRepo(repoConfig.getKey()));
        repoConfigs = filterByProject(repoConfigs, projectKey);
        return repoConfigs.map(LocalConfig.class::cast).toList();
    }

    @Autowired
    public void setTaskService(TaskService taskService) {
        this.taskService = taskService;
    }

    @Autowired
    public void setMetadataEventService(MetadataEventService metadataEventService) {
        this.metadataEventService = metadataEventService;
    }

    @Override
    public boolean isForceBaseUrl() {
        Stream<? extends RepoBaseDescriptor> local = getLocalRepoDescriptorsIncludingInfoRepos().stream();
        Stream<? extends RepoBaseDescriptor> virtual = getVirtualRepoDescriptors().stream();
        Stream<? extends RepoBaseDescriptor> federated = getFederatedRepoConfigs().stream()
                .map(repoConfig -> FederatedRepoDescriptor.fromRepositoryConfig(centralConfigService.getDescriptor(),
                        repoConfig)).toList().stream();
        Stream<RepoBaseDescriptor> concat = concat(local, concat(federated, virtual));
        SshServerSettings sshServerSettings = centralConfigService.getDescriptor().getSecurity().getSshServerSettings();
        boolean activeSshServer = sshServerSettings != null && sshServerSettings.isEnableSshServer();
        boolean cocoaPodsRepoExist = concat.anyMatch(descriptor -> RepoType.CocoaPods.equals(descriptor.getType()));
        if (!cocoaPodsRepoExist) {
            cocoaPodsRepoExist = getRemoteRepoConfigs().stream()
                    .anyMatch(config -> RepoType.CocoaPods.equals(config.getPackageType()));
        }
        return cocoaPodsRepoExist || activeSshServer;
    }

    @Nonnull
    private InternalRepositoryConfigService getInternalRepoConfigService() {
        return ContextHelper.get().beanForType(InternalRepositoryConfigService.class);
    }

    @Nonnull
    private static List<String> extractRemoteRepoKeysToUpdateOrDelete(RepoConfigChangesReload repoConfigChanges) {
        return repoConfigChanges
                .filterBy(REMOTE, Set.of(REPO_DELETE, REPO_UPDATE))
                .stream()
                .map(RepoConfigChangeReload::getRepoKey)
                .toList();
    }

    private Stream<RepoConfig> filterByProject(Stream<RepoConfig> repoConfigs, String projectKey) {
        if (!ProjectsUtils.isDefaultProject(projectKey)) {
            Set<String> projectRepoKeys = projectsService.getProjectRepositoriesKeys(projectKey, false);
            return repoConfigs.filter(repoConfig -> projectRepoKeys.contains(repoConfig.getKey()));
        }
        return repoConfigs;
    }


    private class RepositoriesCacheBuilder {

        private final GenericHttpRepoFactory genericRemoteRepoFactory = new GenericHttpRepoFactory();
        // a cache of all the repository keys
        private Set<String> allRepoKeysCache;
        private LocalRepo<?, ?> trashcan;
        private LocalRepo<?, ?> supportBundles;
        private Map<String, LocalRepo> localRepositoriesMap = Maps.newLinkedHashMap();
        private Map<String, RemoteRepo> remoteRepositoriesMap = Maps.newLinkedHashMap();
        private Map<String, LocalCacheRepo> localCacheRepositoriesMap = Maps.newLinkedHashMap();
        private Map<String, VirtualRepo> virtualRepositoriesMap = Maps.newLinkedHashMap();
        private Map<String, FederatedRepo> federatedRepositoriesMap = Maps.newLinkedHashMap();
        private Map<String, ReleaseBundlesRepo> releaseBundleRepositoriesMap = Maps.newLinkedHashMap();
        private Map<RepoType, Collection<Repo>> repoTypeRepositoriesMap =
                Maps.newTreeMap(new RepoType.RepoNameComparator());

        RepositoriesCache rebuildRepositories() {
            //Create the repository objects from the descriptor
            CentralConfigDescriptor centralConfig = centralConfigService.getDescriptor();
            InternalRepositoryService transactionalMe = getTransactionalMe();

            Supplier<List<RepoLayout>> layoutSupplier = centralConfig::getRepoLayouts;
            Supplier<List<KeyPairDescriptor>> keyPairsSupplier = centralConfig::getKeyPairs;
            Supplier<List<PropertySet>> propertySetsSupplier = centralConfig::getPropertySets;
            ProxyDescriptor defaultProxy = centralConfig.getDefaultProxy();
            List<LocalRepoConfig> localRepos = new ArrayList<>();
            List<VirtualRepoConfig> virtualRepos = new ArrayList<>();
            List<FederatedRepoConfig> federatedRepos = new ArrayList<>();
            List<ReleaseBundleRepoConfig> releaseBundleRepos = new ArrayList<>();
            List<RemoteRepoConfig> remoteRepos = new ArrayList<>();

            getInternalRepoConfigService().getAllRepoConfigsForRebuild().forEach(repoConfig -> {
                if (repoConfig.getRepoType() == LOCAL) {
                    localRepos.add((LocalRepoConfig) repoConfig);
                } else if (repoConfig.getRepoType() == REMOTE) {
                    RemoteRepoConfig remoteRepoConfig = (RemoteRepoConfig) repoConfig;
                    if (remoteRepoConfig.getProxyRef() == null && !remoteRepoConfig.isProxyDisabled() &&
                            defaultProxy != null) {
                        remoteRepoConfig = RepoConfigCopyFactory.copy(remoteRepoConfig);
                        remoteRepoConfig.setProxyRef(defaultProxy.getKey());
                    }
                    remoteRepos.add(remoteRepoConfig);
                } else if (repoConfig.getRepoType() == VIRTUAL) {
                    virtualRepos.add((VirtualRepoConfig) repoConfig);
                } else if (repoConfig.getRepoType() == FEDERATED) {
                    FederatedRepoConfig federatedRepoConfig = (FederatedRepoConfig) repoConfig;
                    if (federatedRepoConfig.getProxyRef() == null && !federatedRepoConfig.isProxyDisabled() &&
                            defaultProxy != null) {
                        federatedRepoConfig = RepoConfigCopyFactory.copy(federatedRepoConfig);
                        federatedRepoConfig.setProxyRef(defaultProxy.getKey());
                    }
                    federatedRepos.add(federatedRepoConfig);
                } else {
                    releaseBundleRepos.add((ReleaseBundleRepoConfig) repoConfig);
                }
            });

            // When atomic repo root creation is disabled, query for missing root paths
            // to determine which repos need root folder creation during init
            Set<String> missingRepoConfigRootPaths = ConstantValues.repoRootAtomicCreation.getBoolean()
                    ? Set.of()
                    : getInternalRepoConfigService().findAllMissingRepoRootPaths();

            Set<String> existingSystemRepoRootPaths =
                    fileService.findExistingRepoRootPaths(Set.of(TRASH_REPO_KEY, SUPPORT_BUNDLE_REPO_KEY));

            //Trashcan
            measureAndLogTime(log, Level.DEBUG, "Rebuild trashcan took: {}ms", () ->
                    rebuildTrashcan(transactionalMe, existingSystemRepoRootPaths.contains(TRASH_REPO_KEY))
            );

            //SupportBundles
            measureAndLogTime(log, Level.DEBUG, "Rebuild support bundles took: {}ms", () ->
                    rebuildSupportBundles(transactionalMe,
                            existingSystemRepoRootPaths.contains(SUPPORT_BUNDLE_REPO_KEY))
            );

            //Locals
            measureAndLogTime(log, Level.DEBUG, "Rebuild local repositories took: {}ms", () ->
                    rebuildLocalRepositories(repositoryConfigService, localRepos, transactionalMe,
                            missingRepoConfigRootPaths, layoutSupplier, keyPairsSupplier, propertySetsSupplier)
            );

            // Remotes and caches
            measureAndLogTime(log, Level.DEBUG, "Rebuild remote and cache repositories took: {}ms", () ->
                    rebuildRemoteAndCacheRepositories(centralConfig, remoteRepos, missingRepoConfigRootPaths)
            );

            // Federated
            measureAndLogTime(log, Level.DEBUG, "Rebuild federated repositories took: {}ms", () ->
                    rebuildFederatedRepositories(repositoryConfigService, centralConfig, federatedRepos,
                            transactionalMe, missingRepoConfigRootPaths)
            );

            // Virtuals
            measureAndLogTime(log, Level.DEBUG, "Rebuild virtual repositories took: {}ms", () ->
                    rebuildVirtualRepositories(centralConfig, virtualRepos, localRepos, remoteRepos, federatedRepos,
                            transactionalMe, missingRepoConfigRootPaths)
            );

            // Release Bundle
            measureAndLogTime(log, Level.INFO, "Rebuild release bundle repositories took: {}ms", () ->
                    rebuildReleaseBundleRepositories(repositoryConfigService, centralConfig, releaseBundleRepos,
                            transactionalMe, missingRepoConfigRootPaths)
            );

            measureAndLogTime(log, Level.INFO, "Rebuild all repo keys cache took: {}ms", this::initAllRepoKeysCache);

            measureAndLogTime(log, Level.INFO, "Rebuild repo type repositories map took: {}ms",
                    this::initRepoTypeRepositoriesMap
            );

            cachedReposByFirstCharMap.set(null);

            return new RepositoriesCache(releaseBundleRepositoriesMap, virtualRepositoriesMap,
                    federatedRepositoriesMap, localCacheRepositoriesMap, remoteRepositoriesMap,
                    localRepositoriesMap, trashcan, allRepoKeysCache, supportBundles, repoTypeRepositoriesMap);
        }

        private void initRepoTypeRepositoriesMap() {
            Map<RepoType, List<Repo>> sorted = new EnumMap<>(RepoType.class);
            for (LocalRepo localRepo : localRepositoriesMap.values()) {
                addToRepoTypeToRepoMap(localRepo, sorted);
            }
            for (RemoteRepo remoteRepo : remoteRepositoriesMap.values()) {
                addToRepoTypeToRepoMap(remoteRepo, sorted);
            }
            for (VirtualRepo virtualRepo : virtualRepositoriesMap.values()) {
                addToRepoTypeToRepoMap(virtualRepo, sorted);
            }
            for (LocalCacheRepo localCacheRepo : localCacheRepositoriesMap.values()) {
                addToRepoTypeToRepoMap(localCacheRepo, sorted);
            }
            for (FederatedRepo federatedRepo : federatedRepositoriesMap.values()) {
                addToRepoTypeToRepoMap(federatedRepo, sorted);
            }

            sorted.values().stream()
                    .parallel()
                    .forEach(list -> list.sort(Comparator.comparing(Repo::getKey)));
            repoTypeRepositoriesMap = new TreeMap<>(new RepoType.RepoNameComparator());
            repoTypeRepositoriesMap.putAll(sorted);
        }

        private static void addToRepoTypeToRepoMap(Repo repo, Map<RepoType, List<Repo>> sorted) {
            sorted.computeIfAbsent(repo.getRepoConfig().getPackageType(), repoType -> new ArrayList<>())
                    .add(repo);
        }

        private void rebuildTrashcan(InternalRepositoryService transactionalMe,
                boolean existenceValidated) {
            DbLocalRepo oldTrashcanRepo = null;
            if (trashcan != null) {
                // TODO: 31/01/2022 do we really need trash typeconfig?
                oldTrashcanRepo =
                        (DbLocalRepo<TrashRepoDescriptor, LocalRepoConfig>) trashcan;
            }
            LocalRepoConfig trashRepoConfig =
                    new TrashRepo().createSystemRepoConfig(centralConfigService.getDescriptor(), TRASH_REPO_KEY);
            trashcan = new DbLocalRepo<>(
                    TrashRepoDescriptor.fromRepositoryConfig(centralConfigService.getDescriptor(), trashRepoConfig),
                    trashRepoConfig, transactionalMe, oldTrashcanRepo, centralConfigService);
            initRepoInNewTransaction(trashcan, existenceValidated);
        }

        private void rebuildSupportBundles(InternalRepositoryService transactionalMe, boolean existenceValidated) {
            DbLocalRepo oldSupportBundles = null;
            if (supportBundles != null) {
                // TODO: 31/01/2022 do we really need support bundle typeconfig?
                oldSupportBundles = (DbLocalRepo<SupportBundleRepoDescriptor,
                        LocalRepoConfig>) supportBundles;
            }
            LocalRepoConfig supportBundleDesc =
                    new SupportBundleRepo().createSystemRepoConfig(centralConfigService.getDescriptor(),
                            SUPPORT_BUNDLE_REPO_KEY);

            supportBundles = new DbLocalRepo<>(
                    SupportBundleRepoDescriptor.fromRepositoryConfig(centralConfigService.getDescriptor(),
                            supportBundleDesc),
                    supportBundleDesc, transactionalMe, oldSupportBundles, centralConfigService);
            initRepoInNewTransaction(supportBundles, existenceValidated);
        }

        private void rebuildLocalRepositories(RepositoryConfigService repositoryConfigService,
                List<LocalRepoConfig> localRepos, InternalRepositoryService transactionalMe,
                Set<String> missingRepoRootPaths, Supplier<List<RepoLayout>> layoutSupplier,
                Supplier<List<KeyPairDescriptor>> keyPairsSupplier, Supplier<List<PropertySet>> propertySetsSupplier) {
            Map<String, LocalRepo> newLocalRepositoriesMap = Maps.newLinkedHashMap();
            StoringRepoInitContext initContext = StoringRepoInitContext.create(ContextHelper.get());
            List<RepoLayout> layouts = layoutSupplier.get();
            List<PropertySet> propertySets = propertySetsSupplier.get();
            List<KeyPairDescriptor> keyPairs = keyPairsSupplier.get();

            for (LocalRepoConfig repoConfig : localRepos) {
                DbLocalRepo<LocalRepoDescriptor, LocalRepoConfig> oldLocalRepo = null;
                String key = repoConfig.getKey();
                if (!localRepositoriesMap.isEmpty()) {
                    LocalRepo oldRepo = localRepositoriesMap.get(key);
                    if (oldRepo != null) {
                        if (!(oldRepo instanceof DbLocalRepo)) {
                            log.error("Reloading configuration did not find local repository {}", key);
                        } else {
                            //noinspection unchecked
                            oldLocalRepo = (DbLocalRepo<LocalRepoDescriptor, LocalRepoConfig>) oldRepo;
                        }
                    } else {
                        // This could be a new repo that is in the newly saved config but not in the global map yet.
                        // Only if we do not find it there as well then it is an error
                        if (!repositoryConfigService.existsByRepoKey(key)) {
                            log.error("Reloading configuration did not find local repository {}", key);
                        }
                    }
                }
                LocalRepoDescriptor localRepoDescriptor = new LocalRepoDescriptor(repoConfig, layouts, propertySets,
                        keyPairs);
                LocalRepo repo = new DbLocalRepo<>(localRepoDescriptor, repoConfig, transactionalMe, oldLocalRepo,
                        centralConfigService);
                try {
                    boolean existenceValidated = !missingRepoRootPaths.contains(key);
                    initRepoInNewTransaction(repo, initContext, existenceValidated);
                } catch (Exception e) {
                    log.error("Failed to initialize local repository '{}'. Repository will be blacked-out",
                            repo.getKey(), e);
                    LocalRepoConfig mutableRepoConfig = ((MutableRepo<LocalRepoConfig>) repo).getMutableRepoConfig();
                    mutableRepoConfig.getRepoTypeConfig().setBlackedOut(true);
                    ((LocalRepoDescriptor) repo.getDescriptor()).setBlackedOut(true);
                }
                newLocalRepositoriesMap.put(repo.getKey(), repo);
            }
            localRepositoriesMap = newLocalRepositoriesMap;
        }

        private void rebuildRemoteAndCacheRepositories(CentralConfigDescriptor config,
                List<RemoteRepoConfig> remoteRepos, Set<String> missingRepoRootPaths) {
            // stop remote repo online monitors
            Map<String, LocalCacheRepo> newMap = Maps.newLinkedHashMap();
            Map<String, RemoteRepo> newRemoteRepositoriesMap = Maps.newLinkedHashMap();
            for (RemoteRepoConfig repoConfig : remoteRepos) {
                String repoKey = repoConfig.getKey();
                HttpRepoDescriptor httpRepoDescriptor = new HttpRepoDescriptor(repoConfig, config.getRepoLayouts(),
                        config.getProxies(), config.getPropertySets(), config.getKeyPairs());
                RemoteRepo remoteRepo = createRemoteRepo(repoConfig, httpRepoDescriptor,
                        remoteRepositoriesMap.get(repoKey));
                if (onlineMonitorService.isRemoteRepoMonitored(repoKey)) {
                    remoteRepo.setAssumedOffline();
                    log.debug("Remote repository '{}' is assumed offline", repoKey);
                }
                try {
                    initRepoInNewTransaction(remoteRepo,
                            !missingRepoRootPaths.contains(repoKey + LocalCacheRepoDescriptor.PATH_SUFFIX));
                } catch (Exception e) {
                    log.error("Failed to initialize remote repository '" + remoteRepo.getKey() + "'. " +
                            "Repository will be blacked-out!", e);
                    RemoteRepoConfig mutableRepoConfig =
                            ((MutableRepo<RemoteRepoConfig>)remoteRepo).getMutableRepoConfig();
                    mutableRepoConfig.getRepoTypeConfig().setBlackedOut(true);
                    ((HttpRepoDescriptor) remoteRepo.getDescriptor()).setBlackedOut(true);
                }
                newRemoteRepositoriesMap.put(remoteRepo.getKey(), remoteRepo);
                if (remoteRepo.isStoreArtifactsLocally()) {
                    LocalCacheRepo localCacheRepo = remoteRepo.getLocalCacheRepo();
                    if (localCacheRepo != null) {
                        newMap.put(localCacheRepo.getKey(), localCacheRepo);
                    }
                }
            }
            localCacheRepositoriesMap = newMap;
            remoteRepositoriesMap = newRemoteRepositoriesMap;
        }

        private void rebuildVirtualRepositories(CentralConfigDescriptor centralConfig,
                List<VirtualRepoConfig> virtualRepos, List<LocalRepoConfig> localRepos,
                List<RemoteRepoConfig> remoteRepos, List<FederatedRepoConfig> federatedRepo,
                InternalRepositoryService transactionalMe, Set<String> missingRepoRootPaths) {
            // Build the virtual repo cache in new map and once the map is read, replace it atomically
            Map<String, VirtualRepo> newMap = Maps.newLinkedHashMap();
            WebstartAddon webstartAddon = addonsManager.addonByType(WebstartAddon.class);
            List<LocalRepoDescriptor> localRepoDescriptors = localRepos.stream()
                    .map(localRepo -> new LocalRepoDescriptor(localRepo,
                            centralConfig.getRepoLayouts(), centralConfig.getPropertySets(),
                            centralConfig.getKeyPairs())).toList();
            List<HttpRepoDescriptor> remoteRepoDescriptors = remoteRepos.stream()
                    .map(remoteRepo -> new HttpRepoDescriptor(remoteRepo,
                            centralConfig.getRepoLayouts(), centralConfig.getProxies(), centralConfig.getPropertySets(),
                            centralConfig.getKeyPairs())).toList();
            List<FederatedRepoDescriptor> federatedRepoDescriptors = federatedRepo.stream()
                    .map(repo -> new FederatedRepoDescriptor(repo, centralConfig.getRepoLayouts(),
                            centralConfig.getPropertySets(), centralConfig.getKeyPairs(), centralConfig.getProxies()))
                    .toList();
            ArrayList<RepoDescriptor> realRepos = new ArrayList<>(localRepoDescriptors);
            realRepos.addAll(remoteRepoDescriptors);
            realRepos.addAll(federatedRepoDescriptors);
            Map<String, RepoDescriptor> realReposMap = realRepos.stream()
                    .collect(Collectors.toMap(RepoDescriptor::getKey, Function.identity()));
            Map<String, VirtualRepoConfig> virtualReposMap = virtualRepos.stream()
                    .collect(Collectors.toMap(RepoConfig::getKey, Function.identity()));

            HashMap<String, VirtualRepoDescriptor> aggregatedVirtualReposMap = new HashMap<>();
            long startTime = System.currentTimeMillis();
            for (VirtualRepoConfig repoConfig : virtualRepos) {
                VirtualRepoDescriptor virtualRepoDescriptor =
                        new VirtualRepoDescriptor(repoConfig, centralConfig.getRepoLayouts(),
                                aggregatedVirtualReposMap, virtualReposMap, realReposMap, centralConfig.getKeyPairs(),
                                centralConfig.getPropertySets(), centralConfig.getProxies());
                VirtualRepo repo = webstartAddon.createVirtualRepo(transactionalMe, repoConfig, virtualRepoDescriptor
                        , centralConfigService);
                newMap.put(repo.getKey(), repo);
            }
            log.debug("Total time to rebuild all virtual repos: {}", (System.currentTimeMillis() - startTime));
            for (VirtualRepo virtualRepo : newMap.values()) {
                virtualRepo.updateRepos(newMap, localRepositoriesMap, federatedRepositoriesMap, remoteRepositoriesMap);
            }

            // 2. call the init method only after all virtual repos exist
            StoringRepoInitContext initContext = StoringRepoInitContext.create(ContextHelper.get());
            for (VirtualRepo virtualRepo : newMap.values()) {
                try {
                    initRepoInNewTransaction(virtualRepo, initContext,
                            !missingRepoRootPaths.contains(virtualRepo.getKey()));
                } catch (Exception e) {
                    log.error("Failed to initialize virtual repository '" + virtualRepo.getKey() + "'. ", e);
                }
            }
            virtualRepositoriesMap = newMap;
        }

        private void rebuildFederatedRepositories(RepositoryConfigService repositoryConfigService,
                CentralConfigDescriptor centralConfig, List<FederatedRepoConfig> federatedRepos,
                InternalRepositoryService transactionalMe, Set<String> missingRepoRootPaths) {
            Map<String, FederatedRepo> newMap = Maps.newLinkedHashMap();
            FederatedRepoAddon federatedRepoAddon = addonsManager.addonByType(FederatedRepoAddon.class);
            for (FederatedRepoConfig repoConfig : federatedRepos) {
                final String repoKey = repoConfig.getKey();
                FederatedRepo oldRepo = null;
                if (!federatedRepositoriesMap.isEmpty()) {
                    oldRepo = federatedRepositoriesMap.get(repoKey);
                    if (oldRepo == null) {
                        // This could be a new repo that is in the newly saved config but not in the global map yet.
                        // Only if we do not find it there as well then it is an error
                        if (!repositoryConfigService.existsByRepoKey(repoKey)) {
                            log.error("Reloading configuration did not find local repository {}", repoKey);
                        }
                    }
                }
                DbLocalRepo<FederatedRepoDescriptor, FederatedRepoConfig> oldFederatedRepo =
                        (DbLocalRepo<FederatedRepoDescriptor, FederatedRepoConfig>) oldRepo;
                FederatedRepoDescriptor repoDescriptor = new FederatedRepoDescriptor(repoConfig,
                        centralConfig.getRepoLayouts(), centralConfig.getPropertySets(), centralConfig.getKeyPairs(),
                        centralConfig.getProxies());
                FederatedRepo repo = federatedRepoAddon.createFederatedRepo(transactionalMe, repoDescriptor,
                        repoConfig, oldFederatedRepo);
                try {
                    initRepoInNewTransaction(repo, !missingRepoRootPaths.contains(repoKey));
                } catch (Exception e) {
                    log.error("Failed to initialize federated repository '" + repo.getKey() + "'. " +
                            "Repository will be blacked-out!", e);
                    FederatedRepoConfig mutableRepoConfig =
                            ((MutableRepo<FederatedRepoConfig>) repo).getMutableRepoConfig();
                    mutableRepoConfig.getRepoTypeConfig().setBlackedOut(true);
                    repo.getDescriptor().setBlackedOut(true);
                }
                newMap.put(repo.getKey(), repo);
            }
            federatedRepositoriesMap = newMap;
            log.trace("Updated federated repositories map");
        }

        public Repo repositoryByKey(String key) {
            Repo repo = null;
            if (localRepositoriesMap.containsKey(key)) {
                repo = (localRepositoriesMap.get(key));
            } else if (localCacheRepositoriesMap.containsKey(key)) {
                repo = localCacheRepositoriesMap.get(key);
            } else if (remoteRepositoriesMap.containsKey(key)) {
                repo = remoteRepositoriesMap.get(key);
            } else if (virtualRepositoriesMap.containsKey(key)) {
                repo = virtualRepositoriesMap.get(key);
            } else if (federatedRepositoriesMap.containsKey(key)) {
                repo = federatedRepositoriesMap.get(key);
            } else if (releaseBundleRepositoriesMap.containsKey(key)) {
                repo = releaseBundleRepositoriesMap.get(key);
            } else if (supportBundles != null && key.equals(supportBundles.getKey())) {
                repo = supportBundles;
            } else if (trashcan != null && key.equals(trashcan.getKey())) {
                repo = trashcan;
            }
            return repo;
        }

        private void rebuildReleaseBundleRepositories(RepositoryConfigService repositoryConfigService,
                CentralConfigDescriptor centralConfig, List<ReleaseBundleRepoConfig> releaseBundleRepos,
                InternalRepositoryService transactionalMe, Set<String> missingRepoRootPaths) {
            Map<String, ReleaseBundlesRepo> newReleaseBundlesRepositoriesMap = Maps.newLinkedHashMap();
            for (ReleaseBundleRepoConfig repoConfig : releaseBundleRepos) {
                DbLocalRepo<ReleaseBundlesRepoDescriptor, LocalRepoConfig> oldRepo = null;
                String repoKey = repoConfig.getKey();
                if (!releaseBundleRepositoriesMap.isEmpty()) {
                    ReleaseBundlesRepo oldReleaseRepo = releaseBundleRepositoriesMap.get(repoKey);
                    if (oldReleaseRepo != null) {
                        //noinspection unchecked
                        oldRepo = oldReleaseRepo;
                    } else {
                        // This could be a new repo that is in the newly saved config but not in the global map yet.
                        // Only if we do not find it there as well then it is an error
                        if (!repositoryConfigService.existsByRepoKey(repoKey)) {
                            log.error("Reloading configuration did not find release bundle repository {}", repoKey);
                        }
                    }
                }

                ReleaseBundlesRepoDescriptor repoDescriptor =
                        new ReleaseBundlesRepoDescriptor(repoConfig, centralConfig.getRepoLayouts(),
                                centralConfig.getPropertySets(), centralConfig.getKeyPairs());
                ReleaseBundlesRepo repo = new ReleaseBundlesRepo(repoDescriptor, repoConfig,
                        transactionalMe, oldRepo, centralConfigService);
                try {
                    initRepoInNewTransaction(repo, !missingRepoRootPaths.contains(repoKey));
                } catch (Exception e) {
                    log.error("Failed to initialize release bundle repository '{}'. Repository will be blacked-out",
                            repo.getKey(), e);
                    LocalRepoConfig mutableRepoConfig = repo.getMutableRepoConfig();
                    mutableRepoConfig.getRepoTypeConfig().setBlackedOut(true);
                    repo.getDescriptor().setBlackedOut(true);
                }
                newReleaseBundlesRepositoriesMap.put(repo.getKey(), repo);
            }
            releaseBundleRepositoriesMap = newReleaseBundlesRepositoriesMap;
        }

        private void initAllRepoKeysCache() {
            int size = localRepositoriesMap.size() + remoteRepositoriesMap.size()
                    + localCacheRepositoriesMap.size() + virtualRepositoriesMap.size()
                    + federatedRepositoriesMap.size() + releaseBundleRepositoriesMap.size()
                    + 1; // for support bundles
            Set<String> newKeys = new HashSet<>(size);
            newKeys.addAll(localRepositoriesMap.keySet());
            newKeys.addAll(remoteRepositoriesMap.keySet());
            newKeys.addAll(localCacheRepositoriesMap.keySet());
            newKeys.addAll(virtualRepositoriesMap.keySet());
            newKeys.addAll(federatedRepositoriesMap.keySet());
            newKeys.addAll(releaseBundleRepositoriesMap.keySet());
            newKeys.add(supportBundles.getKey());
            allRepoKeysCache = newKeys;
        }

        private HttpRepo createRemoteRepo(RemoteRepoConfig repoConfig,
                HttpRepoDescriptor descriptor, RemoteRepo oldRemote) {
            return remoteRepoProviders.getOrDefault(descriptor.getType(), genericRemoteRepoFactory)
                    .build(repoConfig, descriptor, oldRemote);
        }

        private void initRepoInNewTransaction(Repo<?, ?> repo, StoringRepoInitContext initContext,
                boolean existenceValidated) {
            if (existenceValidated) {
                // In case Repo exists, no need to run in new transaction because no root dir going to be created.
                initRepo(repo, initContext, true);
            } else {
                // Repo doesn't exists, so need to run in new transaction to create the root dir.
                getTransactionalMe().initRepo(repo, initContext, false);
            }
        }

        private void initRepoInNewTransaction(Repo<?, ?> repo, boolean existenceValidated) {
            if (existenceValidated) {
                initRepo(repo, existenceValidated);
            } else {
                getTransactionalMe().initRepo(repo, existenceValidated);
            }
        }

        private class GenericHttpRepoFactory implements HttpRepoFactory {
            @Override
            public HttpRepo build(RemoteRepoConfig repoConfig, HttpRepoDescriptor repoDescriptor,
                    RemoteRepo oldRemoteRepo) {

                return new HttpRepo(repoDescriptor, repoConfig, getTransactionalMe(), addonsManager, researchService,
                        oldRemoteRepo, onlineMonitorService, centralConfigService, rateLimiterService,
                        curationCompliantVersionService);
            }

            @Override
            public RepoType getRepoType() {
                return RepoType.Generic;
            }
        }
    }

    @Override
    public void initRepo(Repo repo, boolean existenceValidated) {
        repo.init(existenceValidated);
    }

    /**
     * Initializes the repo with pre-resolved context (used during rebuild to avoid N×context lookups).
     */
    @Override
    public void initRepo(Repo repo, @Nonnull StoringRepoInitContext initContext, boolean existenceValidated) {
        repo.init(initContext, existenceValidated);
    }

    @Override
    public boolean containsRealContent(String repoKey) {
        RepoConfig repoConfig = getRepoConfigByKey(repoKey);
        if (!repoConfig.isReal()) {
            return false;
        }
        return !fileService.isEmpty(repoKey);
    }

    @Override
    public int deleteOrphanNodes(String repoKey, BasicStatusHolder statusHolder) {
        return deleteOrphanNodes(repoKey, statusHolder, false);
    }

    @Override
    public int deleteOrphanNodes(String repoKey, BasicStatusHolder statusHolder, boolean fullRepoContentDeletion) {
        int batchSize = ConstantValues.repoDeleteOrphanBatchSize.getInt();
        List<RepoPath> orphanPaths = fileService.getNodeRepoPaths(repoKey, batchSize);
        if (orphanPaths.isEmpty()) {
            return 0;
        }
        InternalRepositoryService txMe = getTransactionalMe();
        int deleted = 0;
        for (RepoPath orphanPath : orphanPaths) {
            try {
                boolean orphanDeleted = fullRepoContentDeletion ?
                        txMe.undeployInTx(orphanPath, false, statusHolder, true) :
                        txMe.undeployInTx(orphanPath, false, statusHolder);
                if (orphanDeleted) {
                    deleted++;
                }
            } catch (Exception e) {
                statusHolder.error(
                        "Failed to undeploy orphan node '" + orphanPath + "'",
                        e, log);
            }
        }
        return deleted;
    }

    private class RepositoriesCacheLoader implements Callable<RepositoriesCache> {

        @Override
        public RepositoriesCache call() throws Exception {
            return withQueryRateLimiter(SYSTEM, this::callInternal);
        }

        private RepositoriesCache callInternal() {
            InternalRepositoryService transactionalMe = getTransactionalMe();
            return transactionalMe.rebuildRepositoriesInTransaction();
        }
    }

    private String traceId() {
        String traceId = null;
        if (tracer != null) {
            try {
                traceId = tracer.getCurrentSpanTraceId();
            } catch (Exception e) {
                //Could occur in tests
                //Exception is harmless, the trace id is for debugging only for now.
            }
        }
        if (StringUtils.isBlank(traceId)) {
            traceId = "unknown";
        }
        return traceId;
    }

    @Autowired
    public void setOnlineMonitorService(OnlineMonitorService onlineMonitorService) {
        this.onlineMonitorService = onlineMonitorService;
    }

    @Override
    public boolean isExternalSystemRepo(@Nonnull String repoKey) {
        return externalSystemRepos.contains(repoKey);
    }

    @Override
    public boolean isFolderContainsArtifacts(RepoPath folderRepoPath, String... filesNames) {
        return fileService.isFolderContainsArtifacts(folderRepoPath, filesNames);
    }

    /**
     * This method was moved here from AuthorizationService.
     */
    @Override
    public Set<String> convertCachedRepoKeysToRemote(Set<String> repoKeys) {
        Set<String> altered = new LinkedHashSet<>();
        for (String repoKey : repoKeys) {
            String repoKeyCacheOmitted;

            if (repoKey.contains(LocalCacheRepoDescriptor.PATH_SUFFIX)) {
                repoKeyCacheOmitted = repoKey.substring(0,
                        repoKey.lastIndexOf(LocalCacheRepoDescriptor.PATH_SUFFIX.charAt(0)));
            } else {
                altered.add(repoKey);
                continue;
            }
            if (existsByType(REMOTE, repoKeyCacheOmitted)) {
                altered.add(repoKeyCacheOmitted);
            } else {
                altered.add(repoKey); //Its Possible that someone named their local repo '*-cache'
            }
        }
        return altered;
    }

    @Override
    public boolean canZap(RepoPath repoPath) {
        return authService.canDelete(repoPath) || authService.canManage(repoPath);
    }

    @Override
    public void assertStorageQuota(String repoKey, BasicStatusHolder status, long contentLength) {
        assertStorageQuota(status, contentLength);
        projectsService.assertProjectStorageQuota(repoKey, status, contentLength);
    }

    private RepoPath ensureStoringRepoPathResolved(LocalRepo localRepo, RepoPath repoPath) {
        RepoPath resolvedRepoPath = repoPath;
        if (localRepo.isCache()) {
            // Make sure the repo path is pointing to the cache repo and not the corresponding remote repo
            resolvedRepoPath = RepoPathFactory.create(localRepo.getKey(), repoPath.getPath());
        }
        return resolvedRepoPath;
    }
}