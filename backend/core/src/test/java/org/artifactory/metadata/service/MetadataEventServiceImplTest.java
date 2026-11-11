package org.artifactory.metadata.service;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import jakarta.ws.rs.core.Response;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.PropertiesAddon;
import org.artifactory.addon.retention.ArtifactRetentionAddon;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.rest.artifact.ItemStatsInfo;
import org.artifactory.application.exception.ApplicationServiceDisableException;
import org.artifactory.application.exception.ApplicationServiceException;
import org.artifactory.application.exception.NotifyExistsPackageException;
import org.artifactory.application.exception.NotifyPackageCreatedException;
import org.artifactory.application.model.ApplicationServiceSBOM;
import org.artifactory.application.service.ApplicationService;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.config.CentralConfigDescriptorImpl;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.repo.HttpRepoDescriptor;
import org.artifactory.descriptor.repo.LocalCacheRepoDescriptor;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.event.EventOperatorId;
import org.artifactory.event.EventType;
import org.artifactory.event.priority.service.model.PrioritizedNodeEvent;
import org.artifactory.event.work.NodeEventTaskManager;
import org.artifactory.event.work.NodeEventsErrorService;
import org.artifactory.event.work.StatusProbe;
import org.artifactory.event.work.StatusProbeImpl;
import org.artifactory.leadartifact.LeadArtifactFacade;
import org.artifactory.metadata.service.indexer.InternalMetadataServiceIndexHandler;
import org.artifactory.metadata.service.provider.RpmMetadataProvider;
import org.artifactory.model.LocalCacheRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.fs.FileInfoImpl;
import org.artifactory.model.fs.StatsImpl;
import org.artifactory.opentracing.ArtifactoryTracer;
import org.artifactory.opentracing.ArtifactoryTracerConfiguration;
import org.artifactory.properties.PropertiesImpl;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.security.access.AccessService;
import org.artifactory.spring.ArtifactoryApplicationContext;
import org.artifactory.storage.db.event.service.NodeEventCursorService;
import org.artifactory.storage.db.event.service.metadata.mapper.MetadataEntityMapperImpl;
import org.artifactory.storage.db.event.service.metadata.model.MetadataEvent;
import org.artifactory.storage.db.fs.model.MetadataStats;
import org.artifactory.storage.db.fs.service.AbstractStatsService;
import org.artifactory.storage.db.fs.service.StatsPersistingServiceImpl;
import org.artifactory.storage.db.fs.service.UpdateArtifactStatsEvent;
import org.artifactory.storage.db.init.migration.service.MigrationStatusStorageService;
import org.artifactory.storage.event.EventsService;
import org.artifactory.storage.fs.service.FileService;
import org.artifactory.storage.fs.service.PropertiesService;
import org.artifactory.storage.fs.service.StatsService;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.jfrog.common.ClockUtils;
import org.jfrog.metadata.client.MetadataClient;
import org.jfrog.metadata.client.MetadataClientBuilder;
import org.jfrog.metadata.client.PackageClient;
import org.jfrog.metadata.client.PathsClient;
import org.jfrog.metadata.client.SystemClient;
import org.jfrog.metadata.client.VersionClient;
import org.jfrog.metadata.client.confstore.MetadataClientConfigStore;
import org.jfrog.metadata.client.exception.MetadataClientException;
import org.jfrog.metadata.client.model.MetadataFile;
import org.jfrog.metadata.client.model.MetadataLicense;
import org.jfrog.metadata.client.model.MetadataPackageImpl;
import org.jfrog.metadata.client.model.MetadataVersion;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertThrows;

/**
 * @author Uriah Levy
 */
@Test
public class MetadataEventServiceImplTest extends ArtifactoryHomeBoundTest {

    @Mock
    FileService fileService;
    @Mock
    PropertiesService propertiesService;
    @Mock
    InternalRepositoryService repositoryService;
    @Mock
    LeadArtifactFacade leadArtifactFacade;
    @Mock
    NodeEventCursorService nodeEventCursorService;
    @Mock
    StatsService statsService;
    @Mock
    MetadataClient metadataClient;
    @Mock
    ApplicationService applicationService;
    @Mock
    NodeEventTaskManager taskManager;
    @Mock
    PackageClient packageClient;
    @Mock
    PathsClient pathsClient;
    @Mock
    ArtifactoryApplicationContext context;
    @Mock
    MigrationStatusStorageService migrationStatusStorageService;
    @Mock
    EventsService eventsService;
    @Mock
    private AccessService accessService;
    @Mock
    private NodeEventsErrorService nodeEventsErrorService;
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private ArtifactRetentionAddon artifactRetentionAddon;
    @Mock
    private PropertiesAddon propertiesAddon;
    @Mock
    private StatsPersistingServiceImpl statsPersistingService;
    @Mock
    private StatsEventService statsEventService;
    @Mock
    private VersionClient versionsClient;

    private final RpmMetadataProvider rpmMetadataResolver = new RpmMetadataProvider();
    private AutoCloseable mocks;

    @BeforeMethod
    void beforeMethod() {
        mocks = MockitoAnnotations.openMocks(this);
        homeStub.setProperty(ConstantValues.metadataServerEventsEnabled, "true");
        ArtifactoryContextThreadBinder.bind(context);
        when(context.beanForType(ArtifactoryTracer.class)).thenReturn(new ArtifactoryTracer(
                ArtifactoryTracerConfiguration.testTracer()));
        when(context.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        when(addonsManager.addonByType(ArtifactRetentionAddon.class)).thenReturn(artifactRetentionAddon);
        when(artifactRetentionAddon.isColdInstance()).thenReturn(false);
        when(addonsManager.addonByType(PropertiesAddon.class)).thenReturn(propertiesAddon);
    }

    @AfterMethod
    public void afterMethod() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    public void testStatsRecreateSync() {
        MetadataEventServiceImpl metadataEventService = createMetadataEventService(
                new MetadataEntityFacade(ImmutableList.of(rpmMetadataResolver), fileService, propertiesService,
                        repositoryService, null, leadArtifactFacade));
        MetadataClientConfigStore configStore = mock(MetadataClientConfigStore.class);
        when(configStore.authenticatedClientBuilder()).thenReturn(mock(MetadataClientBuilder.class));
        when(configStore.authenticatedClientBuilder().create()).thenReturn(metadataClient);
        when(metadataClient.system()).thenReturn(mock(SystemClient.class));
        when(metadataClient.system().ping()).thenReturn(true);
        metadataEventService.setConfigStore(configStore);
        RepoPath repoPath = RepoPathFactory.create("path/file.tgz");
        when(leadArtifactFacade.getLeadArtifactExtensions(repoPath))
                .thenReturn(Set.of(".tgz"));
        when(statsPersistingService.getStats(repoPath)).thenReturn(
                new ItemStatsInfo(repoPath.getPath(), 15L, 1L, null));
        ConcurrentMap<RepoPath, AbstractStatsService.StatsEvent> statsEvents = Maps.newConcurrentMap();
        when(statsPersistingService.getStatsEvents()).thenReturn(statsEvents);
        metadataEventService.setStatsPersistingService(statsPersistingService);

        metadataEventService.initMetadataClient();
        try (Response response = metadataEventService.statsRecreateSync(Collections.singletonList("path/file.tgz"))) {
            assertNotNull(response);
            assertEquals(response.getStatus(), 200);
            assertEquals(statsPersistingService.getStatsEvents().size(), 1);
            assertEquals(statsPersistingService.getStatsEvents().get(repoPath).getLocalEventCount().get(), 15L);
            assertEquals(statsPersistingService.getStatsEvents().get(repoPath).getLocalDownloadedTime(), 1L);
        }

        // Confirm, that executes without any exception
        metadataEventService.statsRecreateAsync(Collections.singletonList("path/file.tgz"));

        // Test metadata events operator disabled
        when(metadataClient.system().ping()).thenReturn(false);
        metadataEventService.initMetadataClient();
        try (Response response = metadataEventService.statsRecreateSync(Collections.singletonList("path/file.tgz"))) {
            assertNotNull(response);
            assertEquals(response.getStatus(), 500);
            assertEquals(response.getEntity(),
                    "Unable to start Metadata Stats Re-creation since the Metadata Events Operator is " +
                            "disabled. Please review the logs to see why");
        }

        // Confirm, that executes without any exception
        metadataEventService.statsRecreateAsync(Collections.singletonList("path/file.tgz"));

        // Test event service throws exception
        when(metadataClient.system().ping()).thenReturn(true);
        metadataEventService.initMetadataClient();
        when(statsPersistingService.getStatsEvents()).thenThrow(new RuntimeException("Error"));
        try (Response response = metadataEventService.statsRecreateSync(Collections.singletonList("path/file.tgz"))) {
            assertNotNull(response);
            assertEquals(response.getStatus(), 500);
            assertEquals(response.getEntity(),
                    "Caught exception while attempting to reindex paths [path/file.tgz]");
        }

        // Confirm, that executes without any exception
        metadataEventService.statsRecreateAsync(Collections.singletonList("path/file.tgz"));
    }

    public void testEventEntryToMetadataEvent() {
        CentralConfigService configService = mock(CentralConfigService.class);
        MetadataEntityMapperImpl metadataEntityMapper = mock(MetadataEntityMapperImpl.class);
        MetadataEventServiceImpl metadataEventService = createMetadataEventService(
                new MetadataEntityFacade(ImmutableList.of(rpmMetadataResolver), fileService, propertiesService,
                        repositoryService, metadataEntityMapper, leadArtifactFacade), configService);
        setFields(metadataEventService);

        setFields(metadataEventService);

        PrioritizedNodeEvent prioritizedNodeEvent = new PrioritizedNodeEvent(123456L, "yum-local/curl/curl.rpm",
                EventType.props,
                EventOperatorId.METADATA_OPERATOR_ID.getId(), 0, ClockUtils.epochMillis(), 0,
                PrioritizedNodeEvent.EventStatus.PENDING);
        MetadataPackageImpl metadataEntity = getMdsPackage();
        MetadataVersion metadataVersion = getMdsVersion();
        metadataVersion.setFiles(Collections.singletonList(getMdsFile()));
        metadataEntity.setVersions(Collections.singletonList(metadataVersion));
        FileInfoImpl fileInfo = new FileInfoImpl(prioritizedNodeEvent.getRepoPath(), 1);

        when(metadataEntityMapper.metadataBomToMdsPackage(any())).thenReturn(getMdsPackage());
        when(metadataEntityMapper.metadataBomToMdsVersion(any())).thenReturn(getMdsVersion());
        when(metadataEntityMapper.metadataBomToMdsFile(any())).thenReturn(getMdsFile());
        when(fileService.loadItem(prioritizedNodeEvent.getRepoPath())).thenReturn(fileInfo);
        when(propertiesService.getProperties(fileInfo)).thenReturn(getRpmProperties());
        when(repositoryService.localFederatedOrCachedRepoDescriptorByKey("yum-local"))
                .thenReturn(getYumLocalRepoDescriptor());
        when(repositoryService.getRepoConfigByKey("yum-local")).thenReturn(mock(RepoConfig.class));
        when(context.beanForType(RepositoryService.class)).thenReturn(repositoryService);
        when(context.beanForType(StatsService.class)).thenReturn(statsService);
        //        when(taskManager.isInfinite(metadataEventService.getOperatorId())).thenReturn(true);
        StatsImpl stats = new StatsImpl();
        stats.setDownloadCount(10);
        when(statsService.getStats(fileInfo)).thenReturn(stats);
        MutableCentralConfigDescriptor descriptor = new CentralConfigDescriptorImpl();
        descriptor.setUrlBase("http://localhost:8080/artifactory");
        when(context.beanForType(CentralConfigService.class)).thenReturn(configService);
        when(configService.getDescriptor()).thenReturn(descriptor);
        metadataEventService.setMetadataMigrationHelper(mock(MetadataMigrationHelper.class));
        MetadataEvent metadataEvent = metadataEventService.prioritizedEventToMetadataEvent(prioritizedNodeEvent);

        assertNotNull(metadataEvent);
        assertEquals(metadataEvent.getMetadataEntity(), metadataEntity);
    }

    public void testCacheSuffixStrippedFromMetadataEventPath() {
        CentralConfigService configService = mock(CentralConfigService.class);
        MetadataEntityMapperImpl metadataEntityMapper = mock(MetadataEntityMapperImpl.class);
        MetadataEventServiceImpl metadataEventService = createMetadataEventService(
                new MetadataEntityFacade(ImmutableList.of(rpmMetadataResolver), fileService, propertiesService,
                        repositoryService, metadataEntityMapper, leadArtifactFacade), configService);
        setFields(metadataEventService);

        // Create event with -cache suffix in the repo key
        PrioritizedNodeEvent prioritizedNodeEvent = new PrioritizedNodeEvent(123456L,
                "yum-remote-cache/curl/curl.rpm",
                EventType.props,
                EventOperatorId.METADATA_OPERATOR_ID.getId(), 0, ClockUtils.epochMillis(), 0,
                PrioritizedNodeEvent.EventStatus.PENDING);
        FileInfoImpl fileInfo = new FileInfoImpl(prioritizedNodeEvent.getRepoPath(), 1);

        when(metadataEntityMapper.metadataBomToMdsPackage(any())).thenReturn(getMdsPackage());
        when(metadataEntityMapper.metadataBomToMdsVersion(any())).thenReturn(getMdsVersion());
        when(metadataEntityMapper.metadataBomToMdsFile(any())).thenReturn(getMdsFile());
        when(fileService.loadItem(prioritizedNodeEvent.getRepoPath())).thenReturn(fileInfo);
        when(propertiesService.getProperties(fileInfo)).thenReturn(getRpmProperties());
        LocalCacheRepoDescriptor cacheDescriptor = new LocalCacheRepoDescriptor();
        HttpRepoDescriptor remoteDescriptor = new HttpRepoDescriptor();
        remoteDescriptor.setKey("yum-remote");
        remoteDescriptor.setType(RepoType.YUM);
        cacheDescriptor.setRemoteRepo(remoteDescriptor);
        when(repositoryService.localFederatedOrCachedRepoDescriptorByKey("yum-remote-cache"))
                .thenReturn(cacheDescriptor);
        LocalCacheRepoConfig cacheConfig = mock(LocalCacheRepoConfig.class);
        when(cacheConfig.isCache()).thenReturn(true);
        RemoteRepoConfig remoteRepoConfig = mock(RemoteRepoConfig.class);
        when(remoteRepoConfig.getKey()).thenReturn("yum-remote");
        when(cacheConfig.getRemoteRepoConfig()).thenReturn(remoteRepoConfig);
        when(repositoryService.getRepoConfigByKey("yum-remote-cache")).thenReturn(cacheConfig);
        when(context.beanForType(RepositoryService.class)).thenReturn(repositoryService);
        when(context.beanForType(StatsService.class)).thenReturn(statsService);
        StatsImpl stats = new StatsImpl();
        stats.setDownloadCount(10);
        when(statsService.getStats(fileInfo)).thenReturn(stats);
        MutableCentralConfigDescriptor descriptor = new CentralConfigDescriptorImpl();
        descriptor.setUrlBase("http://localhost:8080/artifactory");
        when(context.beanForType(CentralConfigService.class)).thenReturn(configService);
        when(configService.getDescriptor()).thenReturn(descriptor);
        metadataEventService.setMetadataMigrationHelper(mock(MetadataMigrationHelper.class));
        MetadataEvent metadataEvent = metadataEventService.prioritizedEventToMetadataEvent(prioritizedNodeEvent);

        assertNotNull(metadataEvent);
        // The -cache suffix should be stripped from the event path
        assertEquals(metadataEvent.getPath(), "yum-remote/curl/curl.rpm");
    }

    public void testNoCacheSuffixUnchangedInMetadataEventPath() {
        CentralConfigService configService = mock(CentralConfigService.class);
        MetadataEntityMapperImpl metadataEntityMapper = mock(MetadataEntityMapperImpl.class);
        MetadataEventServiceImpl metadataEventService = createMetadataEventService(
                new MetadataEntityFacade(ImmutableList.of(rpmMetadataResolver), fileService, propertiesService,
                        repositoryService, metadataEntityMapper, leadArtifactFacade), configService);
        setFields(metadataEventService);

        // Create event without -cache suffix (local repo)
        PrioritizedNodeEvent prioritizedNodeEvent = new PrioritizedNodeEvent(123456L, "yum-local/curl/curl.rpm",
                EventType.props,
                EventOperatorId.METADATA_OPERATOR_ID.getId(), 0, ClockUtils.epochMillis(), 0,
                PrioritizedNodeEvent.EventStatus.PENDING);
        FileInfoImpl fileInfo = new FileInfoImpl(prioritizedNodeEvent.getRepoPath(), 1);

        when(metadataEntityMapper.metadataBomToMdsPackage(any())).thenReturn(getMdsPackage());
        when(metadataEntityMapper.metadataBomToMdsVersion(any())).thenReturn(getMdsVersion());
        when(metadataEntityMapper.metadataBomToMdsFile(any())).thenReturn(getMdsFile());
        when(fileService.loadItem(prioritizedNodeEvent.getRepoPath())).thenReturn(fileInfo);
        when(propertiesService.getProperties(fileInfo)).thenReturn(getRpmProperties());
        when(repositoryService.localFederatedOrCachedRepoDescriptorByKey("yum-local"))
                .thenReturn(getYumLocalRepoDescriptor());
        when(repositoryService.getRepoConfigByKey("yum-local")).thenReturn(mock(RepoConfig.class));
        when(context.beanForType(RepositoryService.class)).thenReturn(repositoryService);
        when(context.beanForType(StatsService.class)).thenReturn(statsService);
        StatsImpl stats = new StatsImpl();
        stats.setDownloadCount(10);
        when(statsService.getStats(fileInfo)).thenReturn(stats);
        MutableCentralConfigDescriptor descriptor = new CentralConfigDescriptorImpl();
        descriptor.setUrlBase("http://localhost:8080/artifactory");
        when(context.beanForType(CentralConfigService.class)).thenReturn(configService);
        when(configService.getDescriptor()).thenReturn(descriptor);
        metadataEventService.setMetadataMigrationHelper(mock(MetadataMigrationHelper.class));
        MetadataEvent metadataEvent = metadataEventService.prioritizedEventToMetadataEvent(prioritizedNodeEvent);

        assertNotNull(metadataEvent);
        // Local repo path should remain unchanged
        assertEquals(metadataEvent.getPath(), "yum-local/curl/curl.rpm");
    }

    public void testLocalRepoEndingWithCacheSuffixNotStripped() {
        CentralConfigService configService = mock(CentralConfigService.class);
        MetadataEntityMapperImpl metadataEntityMapper = mock(MetadataEntityMapperImpl.class);
        MetadataEventServiceImpl metadataEventService = createMetadataEventService(
                new MetadataEntityFacade(ImmutableList.of(rpmMetadataResolver), fileService, propertiesService,
                        repositoryService, metadataEntityMapper, leadArtifactFacade), configService);
        setFields(metadataEventService);

        // Local repo whose name ends with -cache (not a remote cache repo)
        PrioritizedNodeEvent prioritizedNodeEvent = new PrioritizedNodeEvent(123456L,
                "my-yum-cache/curl/curl.rpm",
                EventType.props,
                EventOperatorId.METADATA_OPERATOR_ID.getId(), 0, ClockUtils.epochMillis(), 0,
                PrioritizedNodeEvent.EventStatus.PENDING);
        FileInfoImpl fileInfo = new FileInfoImpl(prioritizedNodeEvent.getRepoPath(), 1);

        when(metadataEntityMapper.metadataBomToMdsPackage(any())).thenReturn(getMdsPackage());
        when(metadataEntityMapper.metadataBomToMdsVersion(any())).thenReturn(getMdsVersion());
        when(metadataEntityMapper.metadataBomToMdsFile(any())).thenReturn(getMdsFile());
        when(fileService.loadItem(prioritizedNodeEvent.getRepoPath())).thenReturn(fileInfo);
        when(propertiesService.getProperties(fileInfo)).thenReturn(getRpmProperties());
        // This is a regular local repo, not a cache
        LocalRepoDescriptor localDescriptor = new LocalRepoDescriptor();
        localDescriptor.setKey("my-yum-cache");
        localDescriptor.setType(RepoType.YUM);
        when(repositoryService.localFederatedOrCachedRepoDescriptorByKey("my-yum-cache"))
                .thenReturn(localDescriptor);
        when(repositoryService.getRepoConfigByKey("my-yum-cache")).thenReturn(mock(RepoConfig.class));
        when(context.beanForType(RepositoryService.class)).thenReturn(repositoryService);
        when(context.beanForType(StatsService.class)).thenReturn(statsService);
        StatsImpl stats = new StatsImpl();
        stats.setDownloadCount(10);
        when(statsService.getStats(fileInfo)).thenReturn(stats);
        MutableCentralConfigDescriptor descriptor = new CentralConfigDescriptorImpl();
        descriptor.setUrlBase("http://localhost:8080/artifactory");
        when(context.beanForType(CentralConfigService.class)).thenReturn(configService);
        when(configService.getDescriptor()).thenReturn(descriptor);
        metadataEventService.setMetadataMigrationHelper(mock(MetadataMigrationHelper.class));
        MetadataEvent metadataEvent = metadataEventService.prioritizedEventToMetadataEvent(prioritizedNodeEvent);

        assertNotNull(metadataEvent);
        // Local repo ending with -cache should NOT have the suffix stripped
        assertEquals(metadataEvent.getPath(), "my-yum-cache/curl/curl.rpm");
    }

    private void setFields(MetadataEventServiceImpl metadataEventService) {
        metadataEventService.setAccessService(accessService);
        metadataEventService.setRepositoryService(repositoryService);
        metadataEventService.setNodeEventCursorService(nodeEventCursorService);
        metadataEventService.setNodeEventTaskManager(taskManager);
        metadataEventService.setMigrationStatusStorageService(migrationStatusStorageService);
        metadataEventService.setEventsService(eventsService);
        metadataEventService.setMetadataMigrationHelper(mock(MetadataMigrationHelper.class));
        metadataEventService.setStatsPersistingService(statsPersistingService);
        metadataEventService.setAddonsManager(addonsManager);
        metadataEventService.onContextCreated();
    }

    public void testOperateOnNodeEvent() {
        MetadataEntityMapperImpl metadataEntityMapper = mock(MetadataEntityMapperImpl.class);
        CentralConfigService configService = mock(CentralConfigService.class);
        MetadataEventServiceImpl metadataEventService = createMetadataEventService(
                new MetadataEntityFacade(ImmutableList.of(rpmMetadataResolver), fileService, propertiesService,
                        repositoryService, metadataEntityMapper, leadArtifactFacade), configService);
        setFields(metadataEventService);
        when(metadataClient.packages()).thenReturn(packageClient);
        PrioritizedNodeEvent prioritizedNodeEvent = new PrioritizedNodeEvent(123456L, "yum-local/curl/curl.rpm",
                EventType.props,
                EventOperatorId.METADATA_OPERATOR_ID.getId() + "-migration", 0, ClockUtils.epochMillis(), 0,
                PrioritizedNodeEvent.EventStatus.PENDING);
        FileInfoImpl fileInfo = new FileInfoImpl(prioritizedNodeEvent.getRepoPath(), 1);

        when(metadataEntityMapper.metadataBomToMdsPackage(any())).thenReturn(getMdsPackage());
        when(metadataEntityMapper.metadataBomToMdsVersion(any())).thenReturn(getMdsVersion());
        when(metadataEntityMapper.metadataBomToMdsFile(any())).thenReturn(getMdsFile());
        when(fileService.loadItem(prioritizedNodeEvent.getRepoPath())).thenReturn(fileInfo);
        when(propertiesService.getProperties(fileInfo)).thenReturn(getRpmProperties());
        when(repositoryService.localFederatedOrCachedRepoDescriptorByKey("yum-local"))
                .thenReturn(getYumLocalRepoDescriptor());
        when(repositoryService.getRepoConfigByKey("yum-local")).thenReturn(mock(RepoConfig.class));
        StatsImpl stats = new StatsImpl();
        stats.setDownloadCount(10);
        when(statsService.getStats(fileInfo)).thenReturn(stats);
        when(context.beanForType(RepositoryService.class)).thenReturn(repositoryService);
        when(context.beanForType(StatsService.class)).thenReturn(statsService);
        MetadataMigrationHelper migrationHelper = mock(MetadataMigrationHelper.class);
        when(migrationHelper.isMigrating()).thenReturn(true);
        metadataEventService.setMetadataMigrationHelper(migrationHelper);

        MutableCentralConfigDescriptor descriptor = new CentralConfigDescriptorImpl();
        descriptor.setUrlBase("http://localhost:8080/artifactory");
        when(context.beanForType(CentralConfigService.class)).thenReturn(configService);
        when(configService.getDescriptor()).thenReturn(descriptor);
        doNothing().when(packageClient).createOrUpdate(any());
        metadataEventService.setMetadataClient(metadataClient);
        metadataEventService.setApplicationService(applicationService);
        metadataEventService.operateOnEvent(prioritizedNodeEvent);
        verify(packageClient).createOrUpdate(any());
    }

    public void testOperateOnNodeEventDeleteEven() {
        MetadataEntityMapperImpl metadataEntityMapper = mock(MetadataEntityMapperImpl.class);
        CentralConfigService configService = mock(CentralConfigService.class);
        MetadataEventServiceImpl metadataEventService = createMetadataEventService(
                new MetadataEntityFacade(List.of(rpmMetadataResolver), fileService, propertiesService,
                        repositoryService, metadataEntityMapper, leadArtifactFacade), configService);
        setFields(metadataEventService);
        PrioritizedNodeEvent prioritizedNodeEvent = new PrioritizedNodeEvent(123456L, "yum-local/curl/curl.rpm",
                EventType.delete,
                EventOperatorId.METADATA_OPERATOR_ID.getId() + "-migration", 0, ClockUtils.epochMillis(), 0,
                PrioritizedNodeEvent.EventStatus.PENDING);

        metadataEventService.operateOnEvent(prioritizedNodeEvent);
        verify(packageClient,times(0)).createOrUpdate(any());
    }

    public void testCreateOrUpdatePackageWithNullEntity() {
        MetadataEventServiceImpl metadataEventService = createMetadataEventService(
                new MetadataEntityFacade(ImmutableList.of(rpmMetadataResolver), fileService, propertiesService,
                        repositoryService, null, leadArtifactFacade));
        metadataEventService.setMetadataClient(metadataClient);
        when(metadataClient.packages()).thenReturn(packageClient);
        doNothing().when(packageClient).createOrUpdate(any());

        MetadataEvent metadataEvent = mock(MetadataEvent.class);
        when(metadataEvent.getMetadataEntity()).thenReturn(null);
        when(metadataEvent.getPath()).thenReturn("yum-local/curl/curl.rpm");

        metadataEventService.createOrUpdatePackage(metadataEvent);

        // No package client interaction expected when metadata entity is null
        verify(packageClient, times(0)).createOrUpdate(any());
    }

    public void testCreateOrUpdatePackageCallsClientWhenJfbusNotActive() {
        MetadataEventServiceImpl metadataEventService = createMetadataEventService(
                new MetadataEntityFacade(ImmutableList.of(rpmMetadataResolver), fileService, propertiesService,
                        repositoryService, null, leadArtifactFacade));
        metadataEventService.setMetadataClient(metadataClient);
        when(metadataClient.packages()).thenReturn(packageClient);
        doNothing().when(packageClient).createOrUpdate(any());

        MetadataPackageImpl mdsPackage = getMdsPackage(); // packageType = "rpm" by helper
        MetadataEvent metadataEvent = mock(MetadataEvent.class);
        when(metadataEvent.getMetadataEntity()).thenReturn(mdsPackage);
        when(metadataEvent.getPath()).thenReturn("yum-local/curl/curl.rpm");
        when(metadataEvent.getApplicationKey()).thenReturn(null);

        metadataEventService.createOrUpdatePackage(metadataEvent);

        // Expect the metadata client package createOrUpdate to be invoked for non-ACTIVE jfbus mode (default SHADOW)
        verify(packageClient, times(1)).createOrUpdate(mdsPackage);
    }

    public void testCreateOrUpdatePackageDoesNotCallClientWhenJfbusActive() {
        // Ensure JFBus mode ACTIVE for supported package types
        homeStub.setProperty(ConstantValues.jfbusOperationMode.getPropertyName(), "ACTIVE");
        MetadataEventServiceImpl metadataEventService = createMetadataEventService(
                new MetadataEntityFacade(ImmutableList.of(rpmMetadataResolver), fileService, propertiesService,
                        repositoryService, null, leadArtifactFacade));
        metadataEventService.setMetadataClient(metadataClient);
        when(metadataClient.packages()).thenReturn(packageClient);
        doNothing().when(packageClient).createOrUpdate(any());

        // Create a Docker package which is supported by default (ConstantValues.jfbusSupportedPackageTypes = "Docker")
        MetadataPackageImpl dockerPackage = new MetadataPackageImpl();
        dockerPackage.setPkgid("docker:alpine");
        dockerPackage.setPackageType("docker");
        dockerPackage.setName("alpine");

        MetadataEvent metadataEvent = mock(MetadataEvent.class);
        when(metadataEvent.getMetadataEntity()).thenReturn(dockerPackage);
        when(metadataEvent.getPath()).thenReturn("docker-local/alpine/alpine.tar");
        // No application key -> notification path is skipped
        when(metadataEvent.getApplicationKey()).thenReturn(null);

        metadataEventService.createOrUpdatePackage(metadataEvent);

        // When JFBus is ACTIVE for Docker, metadata client should NOT be called
        verify(packageClient, times(0)).createOrUpdate(any());
    }

    public void testNotifyApplicationServiceDeletesPropertiesOnExistsException() {
        // Enable application service feature and prepare metadata event with application properties
        homeStub.setProperty(ConstantValues.applicationServiceFeatureEnabled, "true");
        MetadataEventServiceImpl metadataEventService = createMetadataEventService(
                new MetadataEntityFacade(ImmutableList.of(rpmMetadataResolver), fileService, propertiesService,
                        repositoryService, null, leadArtifactFacade));
        metadataEventService.setMetadataClient(metadataClient);
        metadataEventService.setApplicationService(applicationService);
        metadataEventService.setAddonsManager(addonsManager);
        when(metadataClient.packages()).thenReturn(packageClient);
        doNothing().when(packageClient).createOrUpdate(any());

        MetadataPackageImpl mdsPackage = getMdsPackage();
        MetadataEvent metadataEvent = mock(MetadataEvent.class);
        when(metadataEvent.getMetadataEntity()).thenReturn(mdsPackage);
        when(metadataEvent.getPath()).thenReturn("yum-local/curl/curl.rpm");
        when(metadataEvent.getApplicationKey()).thenReturn("appKey");
        // application properties map - keys will be deleted via PropertiesAddon
        when(metadataEvent.getApplicationProperties()).thenReturn(Map.of("application.id", "appId"));

        ApplicationServiceSBOM applicationServiceSBOM = new ApplicationServiceSBOM(
                "token", mdsPackage.getPackageType(), mdsPackage.getName(), "1.0.0", new HashMap<>());
        when(applicationService.createApplicationServiceSBOM(any(), any()))
                .thenReturn(Optional.of(applicationServiceSBOM));
        // notifyPackageCreated throws NotifyExistsPackageException -> should trigger deletion of application properties
        doThrow(new NotifyExistsPackageException("exists")).when(applicationService).notifyPackageCreated(any());
        when(addonsManager.addonByType(PropertiesAddon.class)).thenReturn(propertiesAddon);
        doNothing().when(propertiesAddon).deleteProperty(any(), any());

        // Execute
        metadataEventService.createOrUpdatePackage(metadataEvent);

        // Verify deleteProperty called for the application property key
        verify(propertiesAddon, times(1)).deleteProperty(any(), org.mockito.Mockito.eq("application.id"));
    }

    public void testNotifyApplicationServiceRethrowsNotifyPackageCreatedException() {
        homeStub.setProperty(ConstantValues.applicationServiceFeatureEnabled, "true");
        MetadataEventServiceImpl metadataEventService = createMetadataEventService(
                new MetadataEntityFacade(ImmutableList.of(rpmMetadataResolver), fileService, propertiesService,
                        repositoryService, null, leadArtifactFacade));
        metadataEventService.setMetadataClient(metadataClient);
        metadataEventService.setApplicationService(applicationService);
        when(metadataClient.packages()).thenReturn(packageClient);
        doNothing().when(packageClient).createOrUpdate(any());

        MetadataPackageImpl mdsPackage = getMdsPackage();
        MetadataEvent metadataEvent = mock(MetadataEvent.class);
        when(metadataEvent.getMetadataEntity()).thenReturn(mdsPackage);
        when(metadataEvent.getPath()).thenReturn("yum-local/curl/curl.rpm");
        when(metadataEvent.getApplicationKey()).thenReturn("appKey");
        when(metadataEvent.getApplicationProperties()).thenReturn(Map.of("application.id", "appId"));

        ApplicationServiceSBOM applicationServiceSBOM = new ApplicationServiceSBOM(
                "token", mdsPackage.getPackageType(), mdsPackage.getName(), "1.0.0", new HashMap<>());
        when(applicationService.createApplicationServiceSBOM(any(), any()))
                .thenReturn(Optional.of(applicationServiceSBOM));
        doThrow(new NotifyPackageCreatedException("failed")).when(applicationService).notifyPackageCreated(any());

        // Expect the NotifyPackageCreatedException to bubble up from createOrUpdatePackage
        assertThrows(NotifyPackageCreatedException.class,
                () -> metadataEventService.createOrUpdatePackage(metadataEvent));
    }

    @Test
    public void testApplicationServiceNotifyHappyFlow() {
        homeStub.setProperty(ConstantValues.applicationServiceFeatureEnabled, "true");
        MetadataEntityMapperImpl metadataEntityMapper = mock(MetadataEntityMapperImpl.class);
        CentralConfigService configService = mock(CentralConfigService.class);
        MetadataEventServiceImpl metadataEventService = createMetadataEventService(
                new MetadataEntityFacade(List.of(rpmMetadataResolver), fileService, propertiesService,
                        repositoryService, metadataEntityMapper, leadArtifactFacade), configService);
        setFields(metadataEventService);
        when(metadataClient.packages()).thenReturn(packageClient);
        PrioritizedNodeEvent prioritizedNodeEvent = new PrioritizedNodeEvent(123456L, "yum-local/curl/curl.rpm",
                EventType.create,
                EventOperatorId.METADATA_OPERATOR_ID.getId() + "-migration", 0, ClockUtils.epochMillis(), 0,
                PrioritizedNodeEvent.EventStatus.PENDING);
        FileInfoImpl fileInfo = new FileInfoImpl(prioritizedNodeEvent.getRepoPath(), 1);

        when(metadataEntityMapper.metadataBomToMdsPackage(any())).thenReturn(getMdsPackage());
        when(metadataEntityMapper.metadataBomToMdsVersion(any())).thenReturn(getMdsVersion());
        when(metadataEntityMapper.metadataBomToMdsFile(any())).thenReturn(getMdsFile());
        when(fileService.loadItem(prioritizedNodeEvent.getRepoPath())).thenReturn(fileInfo);
        when(propertiesService.getProperties(fileInfo)).thenReturn(getRpmProperties());
        when(repositoryService.localFederatedOrCachedRepoDescriptorByKey("yum-local"))
                .thenReturn(getYumLocalRepoDescriptor());
        when(repositoryService.getRepoConfigByKey("yum-local")).thenReturn(mock(RepoConfig.class));
        StatsImpl stats = new StatsImpl();
        stats.setDownloadCount(10);
        when(statsService.getStats(fileInfo)).thenReturn(stats);
        when(context.beanForType(RepositoryService.class)).thenReturn(repositoryService);
        when(context.beanForType(StatsService.class)).thenReturn(statsService);
        MetadataMigrationHelper migrationHelper = mock(MetadataMigrationHelper.class);
        when(migrationHelper.isMigrating()).thenReturn(true);
        metadataEventService.setMetadataMigrationHelper(migrationHelper);

        MutableCentralConfigDescriptor descriptor = new CentralConfigDescriptorImpl();
        descriptor.setUrlBase("http://localhost:8080/artifactory");
        when(context.beanForType(CentralConfigService.class)).thenReturn(configService);
        when(configService.getDescriptor()).thenReturn(descriptor);
        doNothing().when(packageClient).createOrUpdate(any());
        metadataEventService.setMetadataClient(metadataClient);
        metadataEventService.setApplicationService(applicationService);

        ApplicationServiceSBOM applicationServiceSBOM = new ApplicationServiceSBOM(
                "applicationservicetokennotreal", RepoType.YUM.getType(), "curl", "1.0.0", new HashMap<>());
        when(applicationService.createApplicationServiceSBOM(any(), any()))
                .thenReturn(Optional.of(applicationServiceSBOM));

        metadataEventService.operateOnEvent(prioritizedNodeEvent);
        verify(packageClient).createOrUpdate(any());

        verify(applicationService, times(1)).createApplicationServiceSBOM(any(), any());
        verify(applicationService, times(1)).notifyPackageCreated(any());
        verify(addonsManager, times(1)).addonByType(PropertiesAddon.class);
    }
    @Test
    public void testApplicationServiceSupportSpecialCharacters() {
        homeStub.setProperty(ConstantValues.applicationServiceFeatureEnabled, "true");
        MetadataEntityMapperImpl metadataEntityMapper = mock(MetadataEntityMapperImpl.class);
        CentralConfigService configService = mock(CentralConfigService.class);
        MetadataEventServiceImpl metadataEventService = createMetadataEventService(
                new MetadataEntityFacade(List.of(rpmMetadataResolver), fileService, propertiesService,
                        repositoryService, metadataEntityMapper, leadArtifactFacade), configService);
        setFields(metadataEventService);
        when(metadataClient.packages()).thenReturn(packageClient);
        PrioritizedNodeEvent prioritizedNodeEvent = new PrioritizedNodeEvent(123456L, "yum-local/@scope/pkgName.rpm",
                EventType.create,
                EventOperatorId.METADATA_OPERATOR_ID.getId() + "-migration", 0, ClockUtils.epochMillis(), 0,
                PrioritizedNodeEvent.EventStatus.PENDING);
        FileInfoImpl fileInfo = new FileInfoImpl(prioritizedNodeEvent.getRepoPath(), 1);

        when(metadataEntityMapper.metadataBomToMdsPackage(any())).thenReturn(getMdsPackage());
        when(metadataEntityMapper.metadataBomToMdsVersion(any())).thenReturn(getMdsVersion());
        when(metadataEntityMapper.metadataBomToMdsFile(any())).thenReturn(getMdsFile());
        when(fileService.loadItem(prioritizedNodeEvent.getRepoPath())).thenReturn(fileInfo);
        when(propertiesService.getProperties(fileInfo)).thenReturn(getRpmProperties());
        when(repositoryService.localFederatedOrCachedRepoDescriptorByKey("yum-local"))
                .thenReturn(getYumLocalRepoDescriptor());
        when(repositoryService.getRepoConfigByKey("yum-local")).thenReturn(mock(RepoConfig.class));
        StatsImpl stats = new StatsImpl();
        stats.setDownloadCount(10);
        when(statsService.getStats(fileInfo)).thenReturn(stats);
        when(context.beanForType(RepositoryService.class)).thenReturn(repositoryService);
        when(context.beanForType(StatsService.class)).thenReturn(statsService);
        MetadataMigrationHelper migrationHelper = mock(MetadataMigrationHelper.class);
        when(migrationHelper.isMigrating()).thenReturn(true);
        metadataEventService.setMetadataMigrationHelper(migrationHelper);

        MutableCentralConfigDescriptor descriptor = new CentralConfigDescriptorImpl();
        descriptor.setUrlBase("http://localhost:8080/artifactory");
        when(context.beanForType(CentralConfigService.class)).thenReturn(configService);
        when(configService.getDescriptor()).thenReturn(descriptor);
        doNothing().when(packageClient).createOrUpdate(any());
        metadataEventService.setMetadataClient(metadataClient);
        metadataEventService.setApplicationService(applicationService);

        ApplicationServiceSBOM applicationServiceSBOM = new ApplicationServiceSBOM(
                "applicationservicetokennotreal", RepoType.Npm.getType(),"@scope/pkgName", "1.0.0", new HashMap<>());
        when(applicationService.createApplicationServiceSBOM(any(), any()))
                .thenReturn(Optional.of(applicationServiceSBOM));

        metadataEventService.operateOnEvent(prioritizedNodeEvent);
        verify(packageClient).createOrUpdate(any());

        verify(applicationService, times(1)).createApplicationServiceSBOM(any(), any());
        verify(applicationService, times(1)).notifyPackageCreated(any());
        verify(addonsManager, times(1)).addonByType(PropertiesAddon.class);
    }

    @Test
    public void testApplicationServiceNotifyThrowException() {
        homeStub.setProperty(ConstantValues.applicationServiceFeatureEnabled, "true");
        MetadataEntityMapperImpl metadataEntityMapper = mock(MetadataEntityMapperImpl.class);
        CentralConfigService configService = mock(CentralConfigService.class);
        MetadataEventServiceImpl metadataEventService = createMetadataEventService(
                new MetadataEntityFacade(List.of(rpmMetadataResolver), fileService, propertiesService,
                        repositoryService, metadataEntityMapper, leadArtifactFacade), configService);
        setFields(metadataEventService);
        when(metadataClient.packages()).thenReturn(packageClient);
        PrioritizedNodeEvent prioritizedNodeEvent = new PrioritizedNodeEvent(123456L, "yum-local/curl/curl.rpm",
                EventType.create,
                EventOperatorId.METADATA_OPERATOR_ID.getId() + "-migration", 0, ClockUtils.epochMillis(), 0,
                PrioritizedNodeEvent.EventStatus.PENDING);
        FileInfoImpl fileInfo = new FileInfoImpl(prioritizedNodeEvent.getRepoPath(), 1);

        when(metadataEntityMapper.metadataBomToMdsPackage(any())).thenReturn(getMdsPackage());
        when(metadataEntityMapper.metadataBomToMdsVersion(any())).thenReturn(getMdsVersion());
        when(metadataEntityMapper.metadataBomToMdsFile(any())).thenReturn(getMdsFile());
        when(fileService.loadItem(prioritizedNodeEvent.getRepoPath())).thenReturn(fileInfo);
        when(propertiesService.getProperties(fileInfo)).thenReturn(getRpmProperties());
        when(repositoryService.localFederatedOrCachedRepoDescriptorByKey("yum-local"))
                .thenReturn(getYumLocalRepoDescriptor());
        when(repositoryService.getRepoConfigByKey("yum-local")).thenReturn(mock(RepoConfig.class));
        StatsImpl stats = new StatsImpl();
        stats.setDownloadCount(10);
        when(statsService.getStats(fileInfo)).thenReturn(stats);
        when(context.beanForType(RepositoryService.class)).thenReturn(repositoryService);
        when(context.beanForType(StatsService.class)).thenReturn(statsService);
        MetadataMigrationHelper migrationHelper = mock(MetadataMigrationHelper.class);
        when(migrationHelper.isMigrating()).thenReturn(true);
        metadataEventService.setMetadataMigrationHelper(migrationHelper);

        MutableCentralConfigDescriptor descriptor = new CentralConfigDescriptorImpl();
        descriptor.setUrlBase("http://localhost:8080/artifactory");
        when(context.beanForType(CentralConfigService.class)).thenReturn(configService);
        when(configService.getDescriptor()).thenReturn(descriptor);
        doNothing().when(packageClient).createOrUpdate(any());
        metadataEventService.setMetadataClient(metadataClient);
        metadataEventService.setApplicationService(applicationService);

        ApplicationServiceSBOM applicationServiceSBOM = new ApplicationServiceSBOM(
                "applicationservicetokennotreal", RepoType.YUM.getType(), "curl", "1.0.0", new HashMap<>());
        when(applicationService.createApplicationServiceSBOM(any(), any()))
                .thenReturn(Optional.of(applicationServiceSBOM));
        when(applicationService.isApplicationServiceEnabled()).thenReturn(true);

        doThrow(ApplicationServiceDisableException.class).when(applicationService).notifyPackageCreated(any());

        assertThrows(ApplicationServiceException.class, () ->
                metadataEventService.operateOnEvent(prioritizedNodeEvent));
        verify(packageClient).createOrUpdate(any());
        verify(applicationService, times(1)).createApplicationServiceSBOM(any(), any());
        verify(applicationService, times(1)).notifyPackageCreated(any());
        verify(addonsManager, times(0)).addonByType(PropertiesAddon.class);
    }

    @Test(expectedExceptions = RejectedExecutionException.class)
    public void testOnlyOneProbeThread() {
        MetadataEventServiceImpl metadataEventService = createMetadataEventService(
                new MetadataEntityFacade(ImmutableList.of(rpmMetadataResolver), fileService, propertiesService,
                        repositoryService, null, leadArtifactFacade));
        MetadataMigrationHelper migrationHelper = mock(MetadataMigrationHelper.class);
        metadataEventService.setMetadataMigrationHelper(migrationHelper);
        StatusProbe statusProbe = new StatusProbeImpl(TimeUnit.MINUTES.toMillis(2), 11_000, "aService");
        metadataEventService.setStatusProbe(statusProbe);
        metadataEventService.initMetadataClient();
        // Using the probe directly to avoid having to mock other entities
        setFields(metadataEventService);
        IntStream.range(0, 50).forEach(i -> statusProbe
                .startProbing(metadataEventService.getAsyncProbeExecutor(), () -> false, () -> {
                }));
    }

    public void testImportReInitsMetadataClient() {
        MetadataEventServiceImpl metadataEventService = spy(createMetadataEventService(
                new MetadataEntityFacade(List.of(), fileService, propertiesService, repositoryService,
                        null, leadArtifactFacade)));
        doNothing().when(metadataEventService).initMetadataClient();

        metadataEventService.importFrom(null);

        verify(metadataEventService).initMetadataClient();
    }

    public void testInitFlow() {
        MetadataEventServiceImpl metadataEventService = createMetadataEventService(
                new MetadataEntityFacade(ImmutableList.of(rpmMetadataResolver), fileService, propertiesService,
                        repositoryService, null, leadArtifactFacade));
        MetadataClientConfigStore configStore = mock(MetadataClientConfigStore.class);
        MetadataClientBuilder clientBuilder = mock(MetadataClientBuilder.class);

        metadataEventService.setConfigStore(configStore);
        StatusProbeImpl probeSpy = spy(new StatusProbeImpl(TimeUnit.MINUTES.toMillis(2), 11_000, "aService"));
        metadataEventService.setStatusProbe(probeSpy);

        when(configStore.authenticatedClientBuilder()).thenReturn(clientBuilder);
        when(configStore.noAuthClientBuilder()).thenReturn(clientBuilder);
        when(clientBuilder.create()).thenReturn(metadataClient);
        SystemClient systemClient = mock(SystemClient.class);
        when(metadataClient.system()).thenReturn(systemClient);
        when(systemClient.ping()).thenThrow(MetadataClientException.class).thenReturn(true);

        setFields(metadataEventService);
        metadataEventService.init();

        verify(probeSpy).startProbing(any(), any(), any());
        verify(systemClient, timeout(15000).times(3)).ping();
    }

    public void testInitFlowWithDisabledNativeUI() {
        homeStub.setProperty(ConstantValues.metadataServerEventsEnabled, "false");
        MetadataEventServiceImpl metadataEventService = createMetadataEventService(
                new MetadataEntityFacade(ImmutableList.of(rpmMetadataResolver), fileService, propertiesService,
                        repositoryService, null, leadArtifactFacade));
        metadataEventService.setAccessService(accessService);
        MetadataClientConfigStore configStore = mock(MetadataClientConfigStore.class);
        MetadataClientBuilder clientBuilder = mock(MetadataClientBuilder.class);

        metadataEventService.setConfigStore(configStore);
        StatusProbeImpl probeSpy = spy(new StatusProbeImpl(TimeUnit.MINUTES.toMillis(2), 11_000, "aService"));
        metadataEventService.setStatusProbe(probeSpy);

        when(configStore.authenticatedClientBuilder()).thenReturn(clientBuilder);
        when(configStore.noAuthClientBuilder()).thenReturn(clientBuilder);
        when(clientBuilder.create()).thenReturn(metadataClient);
        SystemClient systemClient = mock(SystemClient.class);
        when(metadataClient.system()).thenReturn(systemClient);
        when(systemClient.ping()).thenThrow(MetadataClientException.class).thenReturn(true);

        metadataEventService.setAddonsManager(addonsManager);
        metadataEventService.init();

        verify(probeSpy, times(0)).startProbing(any(), any(), any());
    }

    public void testInitFlowNonCyclicProbe() {
        MetadataEventServiceImpl metadataEventService = createMetadataEventService(
                new MetadataEntityFacade(ImmutableList.of(rpmMetadataResolver), fileService, propertiesService,
                        repositoryService, null, leadArtifactFacade));
        MetadataClientConfigStore configStore = mock(MetadataClientConfigStore.class);
        MetadataClientBuilder clientBuilder = mock(MetadataClientBuilder.class);
        metadataEventService.setConfigStore(configStore);

        StatusProbeImpl probeSpy = spy(new StatusProbeImpl(TimeUnit.MINUTES.toMillis(2), 11_000, "aService"));
        metadataEventService.setStatusProbe(probeSpy);

        when(configStore.authenticatedClientBuilder()).thenReturn(clientBuilder);
        when(configStore.noAuthClientBuilder()).thenReturn(clientBuilder);
        when(clientBuilder.create()).thenReturn(metadataClient);

        SystemClient systemClient = mock(SystemClient.class);
        when(metadataClient.system()).thenReturn(systemClient);
        when(systemClient.ping()).thenThrow(MetadataClientException.class).thenReturn(false);

        setFields(metadataEventService);
        metadataEventService.init();

        verify(probeSpy).startProbing(any(), any(), any());
        verify(systemClient, atMost(2)).ping(); // Async probe may or may not start, but never more than 2.
    }

    @Test(dataProvider = "isMigrating")
    public void testGetNumberOfEventThreads(boolean isMigrating) {
        MetadataEventServiceImpl metadataEventService = createMetadataEventService(
                new MetadataEntityFacade(ImmutableList.of(rpmMetadataResolver), fileService, propertiesService,
                        repositoryService, null, leadArtifactFacade));
        MetadataMigrationHelper migrationHelper = mock(MetadataMigrationHelper.class);
        when(migrationHelper.isMigrating()).thenReturn(isMigrating);
        metadataEventService.setMetadataMigrationHelper(migrationHelper);
        if (isMigrating) {
            assertEquals(metadataEventService.getNumberOfEventThreads(), 5);
        } else {
            assertEquals(metadataEventService.getNumberOfEventThreads(), 2);
        }
    }

    public void testDeletePathFromMetadataServer_skipsWhenOperatorDisabled() {
        MetadataEventServiceImpl metadataEventService = createMetadataEventService(
                new MetadataEntityFacade(ImmutableList.of(rpmMetadataResolver), fileService, propertiesService,
                        repositoryService, null, leadArtifactFacade));
        when(metadataClient.paths()).thenReturn(pathsClient);

        metadataEventService.deletePathFromMetadataServerUsingMetadataClient(
                RepoPathFactory.create("yum-local/curl/curl.rpm"));

        verify(pathsClient, times(0)).deletePath(any());
    }

    public void testDeletePathFromMetadataServer_callsClientWhenJfbusNotActive() {
        MetadataEventServiceImpl metadataEventService = createMetadataEventService(
                new MetadataEntityFacade(ImmutableList.of(rpmMetadataResolver), fileService, propertiesService,
                        repositoryService, null, leadArtifactFacade));
        initOperatorEnabled(metadataEventService);
        when(metadataClient.paths()).thenReturn(pathsClient);
        RepoConfig repoConfig = mock(RepoConfig.class);
        when(repoConfig.getPackageType()).thenReturn(RepoType.YUM);
        when(repositoryService.getRepoConfigByKey("yum-local")).thenReturn(repoConfig);

        metadataEventService.deletePathFromMetadataServerUsingMetadataClient(
                RepoPathFactory.create("yum-local/curl/curl.rpm"));

        verify(pathsClient, times(1)).deletePath(any());
    }

    private void initOperatorEnabled(MetadataEventServiceImpl metadataEventService) {
        MetadataClientConfigStore configStore = mock(MetadataClientConfigStore.class);
        MetadataClientBuilder builder = mock(MetadataClientBuilder.class);
        SystemClient systemClient = mock(SystemClient.class);
        when(configStore.authenticatedClientBuilder()).thenReturn(builder);
        when(builder.create()).thenReturn(metadataClient);
        when(metadataClient.system()).thenReturn(systemClient);
        when(systemClient.ping()).thenReturn(true);
        metadataEventService.setConfigStore(configStore);
        metadataEventService.initMetadataClient();
    }

    @DataProvider
    public Object[][] isMigrating() {
        return new Object[][] {
                {true},
                {false}
        };
    }

    private LocalRepoDescriptor getYumLocalRepoDescriptor() {
        LocalRepoDescriptor localRepoDescriptor = new LocalRepoDescriptor();
        localRepoDescriptor.setType(RepoType.YUM);
        return localRepoDescriptor;
    }

    private PropertiesImpl getRpmProperties() {
        PropertiesImpl itemProperties = new PropertiesImpl();
        itemProperties.put("rpm.metadata.name", "curl");
        itemProperties.put("artifactory.licenses", "Apache-2.0");
        itemProperties.put("artifactory.licenses", "LGPL");
        itemProperties.put("application.id", "applicationservicetokennotreal");
        return itemProperties;
    }

    private MetadataPackageImpl getMdsPackage() {
        MetadataPackageImpl mdsPackage = new MetadataPackageImpl();
        mdsPackage.setPkgid("rpm:curl");
        mdsPackage.setPackageType("rpm");
        mdsPackage.setName("curl");
        mdsPackage
                .setLicenses(
                        Arrays.asList(new MetadataLicense("Apache-2.0", "apache..bla", "http://apache", 123L, "OSS?"),
                                new MetadataLicense("LGPL", "lgpl..bla", "http://lgpl", 123L, "OSS?")));
        return mdsPackage;
    }

    private MetadataVersion getMdsVersion() {
        MetadataVersion metadataVersion = new MetadataVersion();
        metadataVersion.setPkgid("rpm:curl");
        metadataVersion.setName("1.0.0");
        return metadataVersion;
    }

    private MetadataFile getMdsFile() {
        MetadataFile metadataFile = new MetadataFile();
        metadataFile.setLead(true);
        metadataFile.setName("curl.rpm");
        return metadataFile;
    }

    private MetadataEventServiceImpl createMetadataEventService(MetadataEntityFacade metadataEntityFacade) {
        return createMetadataEventService(metadataEntityFacade, null);
    }

    private MetadataEventServiceImpl createMetadataEventService(MetadataEntityFacade metadataEntityFacade,
            CentralConfigService configsService) {
        MetadataEventServiceImpl metadataEventService = new MetadataEventServiceImpl();
        metadataEventService.setMetadataEntityFacade(metadataEntityFacade);
        metadataEventService.setLeadArtifactFacade(leadArtifactFacade);
        metadataEventService.setConfigService(configsService);
        metadataEventService.setNodeEventsErrorService(nodeEventsErrorService);
        metadataEventService.setRepositoryService(repositoryService);
        metadataEventService.setStatsEventService(statsEventService);
        metadataEntityFacade.setMetadataService(metadataEventService);
        metadataEventService.setInternalMetadataServiceIndexHandler(mock(InternalMetadataServiceIndexHandler.class));
        return metadataEventService;
    }

    @Test
    public void onApplicationEvent_jfbusActive_operatorEnabled_routesToBus() throws Exception {
        homeStub.setProperty(ConstantValues.jfbusStatsEventsEnabled.getPropertyName(), "true");
        homeStub.setProperty(ConstantValues.jfbusOperationMode.getPropertyName(), "ACTIVE");
        MetadataEventServiceImpl metadataEventService = createMetadataEventService(
                new MetadataEntityFacade(ImmutableList.of(rpmMetadataResolver), fileService, propertiesService,
                        repositoryService, null, leadArtifactFacade));
        initOperatorEnabled(metadataEventService);
        when(metadataClient.versions()).thenReturn(versionsClient);
        RepoPath repoPath = RepoPathFactory.create("docker-local", "library/nginx/manifest.json");
        Map<RepoPath, MetadataStats> pathToStats =
                Map.of(repoPath, new MetadataStats(1L, 100L, 0L, false));
        when(leadArtifactFacade.isPackageConsumptionLead(repoPath)).thenReturn(true);
        UpdateArtifactStatsEvent event = mock(UpdateArtifactStatsEvent.class);
        when(event.getPathToStats()).thenReturn(pathToStats);

        metadataEventService.onApplicationEvent(event);

        verify(statsEventService, times(1)).publishStats(pathToStats);
        verify(versionsClient, never()).updateVersionStatistics(any());
    }

    @Test
    public void onApplicationEvent_jfbusFlagDisabled_operatorEnabled_routesToRest() throws Exception {
        homeStub.setProperty(ConstantValues.jfbusStatsEventsEnabled.getPropertyName(), "false");
        homeStub.setProperty(ConstantValues.jfbusOperationMode.getPropertyName(), "ACTIVE");
        MetadataEventServiceImpl metadataEventService = createMetadataEventService(
                new MetadataEntityFacade(ImmutableList.of(rpmMetadataResolver), fileService, propertiesService,
                        repositoryService, null, leadArtifactFacade));
        initOperatorEnabled(metadataEventService);
        when(metadataClient.versions()).thenReturn(versionsClient);
        doNothing().when(versionsClient).updateVersionStatistics(any());
        RepoPath repoPath = RepoPathFactory.create("docker-local", "library/nginx/manifest.json");
        Map<RepoPath, MetadataStats> pathToStats =
                Map.of(repoPath, new MetadataStats(1L, 100L, 0L, false));
        when(leadArtifactFacade.isPackageConsumptionLead(repoPath)).thenReturn(true);
        UpdateArtifactStatsEvent event = mock(UpdateArtifactStatsEvent.class);
        when(event.getPathToStats()).thenReturn(pathToStats);

        metadataEventService.onApplicationEvent(event);

        verify(statsEventService, never()).publishStats(any());
        verify(versionsClient, times(1)).updateVersionStatistics(any());
    }

    @Test
    public void onApplicationEvent_jfbusModeOff_operatorEnabled_routesToRest() throws Exception {
        homeStub.setProperty(ConstantValues.jfbusStatsEventsEnabled.getPropertyName(), "true");
        homeStub.setProperty(ConstantValues.jfbusOperationMode.getPropertyName(), "OFF");
        MetadataEventServiceImpl metadataEventService = createMetadataEventService(
                new MetadataEntityFacade(ImmutableList.of(rpmMetadataResolver), fileService, propertiesService,
                        repositoryService, null, leadArtifactFacade));
        initOperatorEnabled(metadataEventService);
        when(metadataClient.versions()).thenReturn(versionsClient);
        doNothing().when(versionsClient).updateVersionStatistics(any());
        RepoPath repoPath = RepoPathFactory.create("docker-local", "library/nginx/manifest.json");
        Map<RepoPath, MetadataStats> pathToStats =
                Map.of(repoPath, new MetadataStats(1L, 100L, 0L, false));
        when(leadArtifactFacade.isPackageConsumptionLead(repoPath)).thenReturn(true);
        UpdateArtifactStatsEvent event = mock(UpdateArtifactStatsEvent.class);
        when(event.getPathToStats()).thenReturn(pathToStats);

        metadataEventService.onApplicationEvent(event);

        verify(statsEventService, never()).publishStats(any());
        verify(versionsClient, times(1)).updateVersionStatistics(any());
    }

    /**
     * Key architectural invariant: the jfbus path must fire even when the Metadata Server is
     * unreachable (operatorEnabled=false). The whole point of jfbus is that an MDS outage does
     * not block the producer; rollback is via jfbus.working.mode=OFF, not via MDS ping.
     */
    @Test
    public void onApplicationEvent_jfbusActive_routesToBusEvenWithoutOperatorEnabled() throws Exception {
        homeStub.setProperty(ConstantValues.jfbusStatsEventsEnabled.getPropertyName(), "true");
        homeStub.setProperty(ConstantValues.jfbusOperationMode.getPropertyName(), "ACTIVE");
        MetadataEventServiceImpl metadataEventService = createMetadataEventService(
                new MetadataEntityFacade(ImmutableList.of(rpmMetadataResolver), fileService, propertiesService,
                        repositoryService, null, leadArtifactFacade));
        // operatorEnabled is intentionally NOT initialised — MDS unreachable scenario.
        Map<RepoPath, MetadataStats> stats = Map.of(
                RepoPathFactory.create("npm-local", "lodash/-/lodash-4.17.21.tgz"),
                new MetadataStats(1L, 0L, 0L, false));
        UpdateArtifactStatsEvent event = mock(UpdateArtifactStatsEvent.class);
        when(event.getPathToStats()).thenReturn(stats);

        metadataEventService.onApplicationEvent(event);

        verify(statsEventService, times(1)).publishStats(stats);
        verify(versionsClient, never()).updateVersionStatistics(any());
    }

    @Test
    public void onApplicationEvent_jfbusInactive_operatorEnabled_routesToRest() throws Exception {
        homeStub.setProperty(ConstantValues.jfbusStatsEventsEnabled.getPropertyName(), "false");
        homeStub.setProperty(ConstantValues.jfbusOperationMode.getPropertyName(), "ACTIVE");
        MetadataEventServiceImpl metadataEventService = createMetadataEventService(
                new MetadataEntityFacade(ImmutableList.of(rpmMetadataResolver), fileService, propertiesService,
                        repositoryService, null, leadArtifactFacade));
        initOperatorEnabled(metadataEventService);
        when(metadataClient.versions()).thenReturn(versionsClient);
        doNothing().when(versionsClient).updateVersionStatistics(any());
        Map<RepoPath, MetadataStats> stats = Map.of(
                RepoPathFactory.create("npm-local", "lodash/-/lodash-4.17.21.tgz"),
                new MetadataStats(1L, 0L, 0L, false));
        UpdateArtifactStatsEvent event = mock(UpdateArtifactStatsEvent.class);
        when(event.getPathToStats()).thenReturn(stats);

        metadataEventService.onApplicationEvent(event);

        verify(statsEventService, never()).publishStats(any());
        // REST path calls getMdsItemStatistics which returns empty for mocked leadArtifactFacade —
        // verify no NPE and the jfbus service was not invoked.
    }

    @Test
    public void onApplicationEvent_jfbusInactiveAndOperatorDisabled_skipsPublish() throws Exception {
        // Default: jfbusStatsEventsEnabled=false, jfbusOperationMode=OFF, operatorEnabled=false.
        MetadataEventServiceImpl metadataEventService = createMetadataEventService(
                new MetadataEntityFacade(ImmutableList.of(rpmMetadataResolver), fileService, propertiesService,
                        repositoryService, null, leadArtifactFacade));
        UpdateArtifactStatsEvent event = mock(UpdateArtifactStatsEvent.class);
        when(event.getPathToStats()).thenReturn(Map.of());

        metadataEventService.onApplicationEvent(event);

        verify(statsEventService, never()).publishStats(any());
        verify(versionsClient, never()).updateVersionStatistics(any());
    }

    @Test
    public void onApplicationEvent_publishStatsThrows_isSwallowed() throws Exception {
        homeStub.setProperty(ConstantValues.jfbusStatsEventsEnabled.getPropertyName(), "true");
        homeStub.setProperty(ConstantValues.jfbusOperationMode.getPropertyName(), "ACTIVE");
        MetadataEventServiceImpl metadataEventService = createMetadataEventService(
                new MetadataEntityFacade(ImmutableList.of(rpmMetadataResolver), fileService, propertiesService,
                        repositoryService, null, leadArtifactFacade));
        Map<RepoPath, MetadataStats> stats = Map.of(
                RepoPathFactory.create("npm-local", "a.tgz"), new MetadataStats(1L, 0L, 0L, false));
        UpdateArtifactStatsEvent event = mock(UpdateArtifactStatsEvent.class);
        when(event.getPathToStats()).thenReturn(stats);
        doThrow(new RuntimeException("simulated failure")).when(statsEventService).publishStats(any());

        // Must not propagate — the dispatcher thread must survive a bad tick.
        metadataEventService.onApplicationEvent(event);
    }
}
