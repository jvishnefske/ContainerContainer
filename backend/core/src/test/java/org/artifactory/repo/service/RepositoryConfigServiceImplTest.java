package org.artifactory.repo.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import org.apache.commons.io.FileUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.HaAddon;
import org.artifactory.addon.curation.CurationAddon;
import org.artifactory.addon.ha.HaCommonAddon;
import org.artifactory.addon.plugin.pkgtype.PluginPackageTypes;
import org.artifactory.addon.xray.XrayAddon;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.ConstantValues;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.home.TestArtifactoryHome;
import org.artifactory.config.InternalCentralConfigService;
import org.artifactory.descriptor.backup.BackupDescriptor;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.config.CentralConfigDescriptorImpl;
import org.artifactory.descriptor.config.CentralConfigTestUtils;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.index.IndexerDescriptor;
import org.artifactory.descriptor.replication.LocalReplicationDescriptor;
import org.artifactory.descriptor.replication.RemoteReplicationDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.descriptor.repo.ReverseProxyDescriptor;
import org.artifactory.metrics.MetricsInfo;
import org.artifactory.metrics.MetricsMetadata;
import org.artifactory.metrics.MetricsThreadLocal;
import org.artifactory.model.AutoCompleteRepoConfigRecord;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.LocalCacheRepoConfig;
import org.artifactory.model.LocalConfig;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RealRepoConfig;
import org.artifactory.model.ReleaseBundleRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.RepoConfigPropagationModel;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.projects.ProjectsService;
import org.artifactory.projects.ProjectsUtils;
import org.artifactory.repo.ExtendedRepositoryConfig;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.RepoConfigChangeReload;
import org.artifactory.repo.RepoConfigChangesReload;
import org.artifactory.repo.RepoConfigReload;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.cache.RepoConfigCache;
import org.artifactory.repo.converter.PackageTypeConfigConverter;
import org.artifactory.repo.converter.RepositoryConfigBaseConverter;
import org.artifactory.repo.federated.FederatedMember;
import org.artifactory.repo.interceptor.RepoConfigChangeInterceptor;
import org.artifactory.repo.interceptor.RepoConfigChanges;
import org.artifactory.repo.packagetype.PomCleanupPolicy;
import org.artifactory.repo.packagetype.configuration.MavenConfiguration;
import org.artifactory.repo.packagetype.configuration.NpmConfiguration;
import org.artifactory.repo.system.SystemRepos;
import org.artifactory.repo.xray.XrayNotAvailableMode;
import org.artifactory.federation.FederatedRepoService;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.exception.http.NotFoundException;
import org.artifactory.service.ConfigValidationService;
import org.artifactory.storage.db.repo.entity.DbRepositoryConfig;
import org.artifactory.storage.db.repo.service.RepositoryConfigStorageService;
import org.artifactory.storage.db.repo.service.mapper.RepositoryConfigMapperImpl;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.util.ExternalDependenciesPackageTypeConfigMapper;
import org.artifactory.util.bundles.BundleConfigurationUtil;
import org.artifactory.version.ArtifactoryServiceVersionProvider;
import org.artifactory.version.CompoundVersionDetails;
import org.hibernate.validator.internal.util.Contracts;
import org.artifactory.concurrent.LockAlreadyTakenException;
import org.artifactory.concurrent.LockingFailedException;
import org.jfrog.common.concurrency.ConflictsGuard;
import org.jfrog.common.config.validation.NotUnicode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.artifactory.descriptor.repo.RepoType.BuildInfo;
import static org.artifactory.descriptor.repo.RepoType.Docker;
import static org.artifactory.descriptor.repo.RepoType.Generic;
import static org.artifactory.descriptor.repo.RepoType.Gradle;
import static org.artifactory.descriptor.repo.RepoType.Maven;
import static org.artifactory.descriptor.repo.RepoType.Npm;
import static org.artifactory.descriptor.repo.RepoType.PipeInfo;
import static org.artifactory.descriptor.repo.RepoType.ReleaseBundles;
import static org.artifactory.descriptor.repo.RepoType.Vagrant;
import static org.artifactory.projects.ProjectsService.DEFAULT_PROJECT;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_CREATE;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_DELETE;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_UPDATE;
import static org.artifactory.repo.RepoDetailsType.FEDERATED;
import static org.artifactory.repo.RepoDetailsType.LOCAL;
import static org.artifactory.repo.RepoDetailsType.RELEASE_BUNDLE;
import static org.artifactory.repo.RepoDetailsType.REMOTE;
import static org.artifactory.repo.RepoDetailsType.VIRTUAL;
import static org.artifactory.repo.system.SystemRepos.BILLING_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.BUILDINFO_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.FULL_SYNC_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.IN_TRANSIT_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.LOG_SHIPPING_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.SUPPORT_BUNDLE_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.TRASH_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.USAGE_REPO_KEY;
import static org.artifactory.util.PackageTypeConfigFields.EXTERNAL_DEPENDENCIES_ENABLED;
import static org.artifactory.util.PackageTypeConfigFields.FETCH_JARS_EAGERLY;
import static org.artifactory.util.PackageTypeConfigFields.POM_REPOSITORY_REFERENCES_CLEANUP_POLICY;
import static org.artifactory.util.bundles.BundleConfigurationUtil.DEFAULT_RBV2_DISTRIBUTION_REPO;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Tamar Rosen
 */
class RepositoryConfigServiceImplTest extends ArtifactoryHomeBoundJUnitTest {

    public static final String CACHE_REPO_KEY = "remote-cache";

    private static MockedStatic<BundleConfigurationUtil> bundleConfigurationUtil;
    private RepositoryConfigMapperImpl repositoryConfigMapper;
    private ArtifactoryContext context;
    private InternalCentralConfigService configService;
    private CurationAddon curationAddon;
    private ProjectsService projectsService;
    private AddonsManager addonsManager;

    private RepositoryConfigStorageService storageService;

    public static Stream<Arguments> providerAllRepoConfigTypesWithNonUnicodeKey() {
        return Stream.of(Arguments.of(LocalRepoConfig.create("\u0000", Generic)),
                Arguments.of(RemoteRepoConfig.create("\u0000", Generic)),
                Arguments.of(VirtualRepoConfig.create("\u0000", Generic)),
                Arguments.of(ReleaseBundleRepoConfig.create("\u0000")),
                Arguments.of(FederatedRepoConfig.create("\u0000", Generic)));
    }

    public static Stream<Arguments> provideRealRepoTypeConfigs() {
        return Stream.of(Arguments.of(LocalRepoConfig.create("\u0000", Generic)),
                Arguments.of(RemoteRepoConfig.create("\u0000", Generic)),
                Arguments.of(FederatedRepoConfig.create("\u0000", Generic)),
                Arguments.of(ReleaseBundleRepoConfig.create("\u0000")));
    }

    public static Stream<Arguments> provideCrudOperationAndExpectedLogMsg() {
        return Stream.of(Arguments.of("insert", "Cannot insert repository: repository config cannot be null"),
                Arguments.of("update", "Cannot update repository: repository config cannot be null"),
                Arguments.of("upsert", "Cannot upsert repository: repository config cannot be null"),
                Arguments.of("delete", "Cannot delete repository: repository key cannot be null"));
    }

    @BeforeAll
    static void beforeAll() {
        bundleConfigurationUtil = Mockito.mockStatic(BundleConfigurationUtil.class);
    }

    @AfterAll
    static void afterAll() {
        bundleConfigurationUtil.close();
    }

    @BeforeEach
    public void setup() throws Exception {
        createAndBindToArtifactoryHome();
        repositoryConfigMapper = new RepositoryConfigMapperImpl();
        context = mock(ArtifactoryContext.class);
        curationAddon = mock(CurationAddon.class);
        projectsService = mock(ProjectsService.class);
        XrayAddon xrayAddon = mock(XrayAddon.class);
        ArtifactoryContextThreadBinder.bind(context);
        configService = mock(InternalCentralConfigService.class);
        storageService = mock(RepositoryConfigStorageService.class);
        addonsManager = mock(AddonsManager.class);
        HaCommonAddon haCommonAddon = mock(HaCommonAddon.class);
        when(context.beanForType(InternalCentralConfigService.class)).thenReturn(configService);
        when(context.getCentralConfig()).thenReturn(configService);
        when(configService.getMutableDescriptor()).thenReturn(mock(MutableCentralConfigDescriptor.class));
        when(context.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        when(context.beanForType(ProjectsService.class)).thenReturn(projectsService);
        when(addonsManager.addonByType(HaCommonAddon.class)).thenReturn(haCommonAddon);
        @SuppressWarnings("unchecked")
        ConflictsGuard<Object> conflictsGuard = mock(ConflictsGuard.class);
        when(haCommonAddon.getConflictsGuard(any())).thenReturn(conflictsGuard);
        try {
            when(conflictsGuard.tryToLock(any(), anyLong(), any())).thenReturn(true);
        } catch (InterruptedException e) {
            // Won't happen in setup
        }
        when(addonsManager.addonByType(HaAddon.class)).thenReturn(mock(HaAddon.class));
        when(addonsManager.addonByType(CurationAddon.class)).thenReturn(curationAddon);
        when(curationAddon.isCurationConfigValid(any())).thenReturn(true);
        when(addonsManager.addonByType(XrayAddon.class)).thenReturn(xrayAddon);
        InternalRepositoryService internalRepositoryService = mock(InternalRepositoryService.class);
        when(context.beanForType(InternalRepositoryService.class)).thenReturn(internalRepositoryService);
        doNothing().when(internalRepositoryService).onRepoConfigReload(anyMap());
    }

    @AfterEach
    public void afterClass() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        unbindAndCleanHome();
    }

    private RepositoryConfigServiceImpl createService() {
        return new RepositoryConfigServiceImpl(storageService, Set.of(), addonsManager);
    }

    private RepositoryConfigServiceImpl createService(RepositoryConfigStorageService storageService) {
        return new RepositoryConfigServiceImpl(storageService, Set.of(), addonsManager);
    }

    @ParameterizedTest
    @MethodSource("provideCrudOperationAndExpectedLogMsg")
    public void crudNullRepoConfigShouldBeSwallowedAndLogged(String action, String expectedLogMsg) {
        Logger logger = (Logger) LoggerFactory.getLogger(RepositoryConfigServiceImpl.class);
        Level originalLevel = logger.getLevel();
        try {
            // given
            logger.setLevel(Level.DEBUG);
            ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
            listAppender.start();
            logger.addAppender(listAppender);

            // when
            RepositoryConfigServiceImpl repositoryConfigService =
                    createService(mock(RepositoryConfigStorageService.class));

            switch (action) {
                case "insert" -> repositoryConfigService.insertRepoConfig(null);
                case "update" -> repositoryConfigService.updateRepoConfig(null);
                case "upsert" -> repositoryConfigService.upsertRepoConfig(null);
                case "delete" -> assertNull(repositoryConfigService.deleteRepoConfig(null));
            }

            // then
            List<ILoggingEvent> logsList = listAppender.list;
            boolean warnLogExists = logsList.stream().anyMatch(
                    iLoggingEvent -> iLoggingEvent.getMessage().equals(expectedLogMsg) &&
                            iLoggingEvent.getLevel().equals(Level.WARN));
            assertTrue(warnLogExists);
        } finally {
            logger.setLevel(originalLevel);
        }
    }

    @Test
    void testGetRepositoryConfigByKey() {
        // given
        LocalRepoConfig repositoryConfig = LocalRepoConfig.create("repoKey", Generic);
        DbRepositoryConfig dbRepositoryConfig = getDbRepositoryConfig(repositoryConfig);

        when(storageService.getAllRepositories()).thenReturn(List.of(dbRepositoryConfig));

        // when
        RepositoryConfigServiceImpl repositoryConfigService = createService();
        RepoConfig retrievedRepositoryConfig = repositoryConfigService.getRepoConfigByKey("repoKey");

        // then
        assertNotNull(retrievedRepositoryConfig);
        assertEquals(retrievedRepositoryConfig, repositoryConfig);
    }

    @ParameterizedTest
    @ValueSource(strings = {SUPPORT_BUNDLE_REPO_KEY, TRASH_REPO_KEY, CACHE_REPO_KEY})
    void testCannotGetUnmanagedRepoConfigs(String repoKey) {
        RepositoryConfigServiceImpl repositoryConfigService = createService();
        assertThrows(IllegalArgumentException.class, () -> repositoryConfigService.getRepoConfigByKey(repoKey));
    }

    @Test
    void testIsInfoOrHiddenRepo_buildInfoType_returnsTrue() {
        RepositoryConfigServiceImpl service = createService();
        assertTrue(service.isInfoOrHiddenRepo(LocalRepoConfig.create("build-info", BuildInfo)));
    }

    @Test
    void testIsInfoOrHiddenRepo_pipeInfoType_returnsTrue() {
        RepositoryConfigServiceImpl service = createService();
        assertTrue(service.isInfoOrHiddenRepo(LocalRepoConfig.create("pipe-info", PipeInfo)));
    }

    @Test
    void testIsInfoOrHiddenRepo_hiddenRepoByKey_returnsTrue() {
        RepositoryConfigServiceImpl service = createService();
        assertTrue(service.isInfoOrHiddenRepo(LocalRepoConfig.create(IN_TRANSIT_REPO_KEY, Generic)));
    }

    @Test
    void testIsInfoOrHiddenRepo_normalRepo_returnsFalse() {
        RepositoryConfigServiceImpl service = createService();
        assertFalse(service.isInfoOrHiddenRepo(LocalRepoConfig.create("my-repo", Generic)));
    }

    @Test
    void testGetLocalRepoConfigsIncludingInfoRepos() {
        List<RepoConfig> expectedRepoConfigs = List.of(
                LocalRepoConfig.create("local-repo-1", RepoType.Generic),
                LocalRepoConfig.create("local-repo-2", RepoType.Maven),
                LocalRepoConfig.create("pipe-info", PipeInfo)
        );

        RepositoryConfigServiceImpl repositoryConfigService =
                spy(createService());
        when(repositoryConfigService.getRepoConfigsByType(LOCAL)).thenReturn(expectedRepoConfigs);

        List<LocalConfig> actualRepoConfigs = repositoryConfigService.getLocalRepoConfigsIncludingInfoRepos();

        assertNotNull(actualRepoConfigs);
        assertEquals(expectedRepoConfigs.size(), actualRepoConfigs.size());
        assertTrue(actualRepoConfigs.containsAll(expectedRepoConfigs.stream().map(LocalConfig.class::cast).toList()));
    }

    @Test
    void testGetLocalRepoConfigsIncludingInfoReposByProject() {
        List<RepoConfig> repoConfigs = List.of(
                LocalRepoConfig.create("local-repo-1", RepoType.Generic),
                LocalRepoConfig.create("local-repo-2", RepoType.Maven),
                LocalRepoConfig.create("pipe-info", PipeInfo),
                LocalRepoConfig.create(IN_TRANSIT_REPO_KEY, Generic),
                LocalRepoConfig.create(DEFAULT_RBV2_DISTRIBUTION_REPO, Generic)
        );

        RepositoryConfigServiceImpl repositoryConfigService =
                spy(createService());
        when(repositoryConfigService.getRepoConfigsByType(LOCAL)).thenReturn(repoConfigs);
        when(projectsService.getProjectRepositoriesKeys(any(), anyBoolean())).thenReturn(
                Set.of("local-repo-1"));

        List<LocalConfig> actualRepoConfigs =
                repositoryConfigService.getLocalRepoConfigsIncludingInfoReposByProject("some-project-key");

        assertNotNull(actualRepoConfigs);
        assertEquals(1, actualRepoConfigs.size());
        assertTrue(actualRepoConfigs.stream().map(RepoConfig::getKey).collect(Collectors.toSet())
                .contains("local-repo-1"));
    }

    @Test
    void testGetLocalRepoConfigsIncludingInfoReposByDefaultProject() {
        List<RepoConfig> repoConfigs = List.of(
                LocalRepoConfig.create("local-repo-1", RepoType.Generic),
                LocalRepoConfig.create("local-repo-2", RepoType.Maven),
                LocalRepoConfig.create("pipe-info", PipeInfo),
                LocalRepoConfig.create(IN_TRANSIT_REPO_KEY, Generic),
                LocalRepoConfig.create(DEFAULT_RBV2_DISTRIBUTION_REPO, Generic)
        );

        RepositoryConfigServiceImpl repositoryConfigService =
                spy(createService());
        when(repositoryConfigService.getRepoConfigsByType(LOCAL)).thenReturn(repoConfigs);

        List<LocalConfig> actualRepoConfigs =
                repositoryConfigService.getLocalRepoConfigsIncludingInfoReposByProject(DEFAULT_PROJECT);

        assertNotNull(actualRepoConfigs);
        assertEquals(4, actualRepoConfigs.size(), actualRepoConfigs.toString());
    }

    @Test
    void testGetLocalFederatedAndRemoteRepoConfigsByProject() {
        List<RepoConfig> repoConfigs = List.of(
                LocalRepoConfig.create("local-repo-1", RepoType.Generic),
                LocalRepoConfig.create("local-repo-2", RepoType.Maven),
                FederatedRepoConfig.create("federated-repo-1", RepoType.Generic),
                RemoteRepoConfig.create("remote-repo-1", RepoType.Generic)
        );

        RepositoryConfigServiceImpl repositoryConfigService =
                spy(createService());
        when(repositoryConfigService.getRepoConfigsByTypes(Set.of(LOCAL, REMOTE, FEDERATED))).thenReturn(repoConfigs);
        when(projectsService.getProjectRepositoriesKeys(any(), anyBoolean())).thenReturn(
                Set.of("local-repo-1", "federated-repo-1", "remote-repo-1"));

        List<RealRepoConfig> actualRepoConfigs =
                repositoryConfigService.getLocalFederatedAndRemoteRepoConfigsByProject("some-project-key");

        assertNotNull(actualRepoConfigs);
        assertEquals(3, actualRepoConfigs.size());
        assertTrue(actualRepoConfigs.stream().map(RepoConfig::getKey).collect(Collectors.toSet())
                .containsAll(Set.of("local-repo-1", "federated-repo-1", "remote-repo-1")));
    }

    @Test
    void testGetLocalFederatedAndRemoteRepoConfigs() {
        List<RepoConfig> repoConfigs = List.of(
                LocalRepoConfig.create("local-repo-1", RepoType.Generic),
                LocalRepoConfig.create("local-repo-2", RepoType.Maven),
                FederatedRepoConfig.create("federated-repo-1", RepoType.Generic),
                RemoteRepoConfig.create("remote-repo-1", RepoType.Generic)
        );

        RepositoryConfigServiceImpl repositoryConfigService =
                spy(createService());
        when(repositoryConfigService.getRepoConfigsByTypes(Set.of(LOCAL, REMOTE, FEDERATED))).thenReturn(repoConfigs);

        List<RealRepoConfig> actualRepoConfigs = repositoryConfigService.getLocalFederatedAndRemoteRepoConfigs();

        assertNotNull(actualRepoConfigs);
        assertEquals(4, actualRepoConfigs.size());
        assertTrue(actualRepoConfigs.stream().map(RepoConfig::getKey).collect(Collectors.toSet())
                .containsAll(Set.of("local-repo-1", "local-repo-2", "federated-repo-1", "remote-repo-1")));
    }

    @Test
    void testGetRemoteRepoConfigs() {
        List<RepoConfig> expectedRemoteRepoConfigs = List.of(
                RemoteRepoConfig.create("remote-repo-1", RepoType.Generic),
                RemoteRepoConfig.create("remote-repo-2", RepoType.Maven)
        );

        RepositoryConfigServiceImpl repositoryConfigService =
                spy(createService());
        when(repositoryConfigService.getRepoConfigsByType(REMOTE)).thenReturn(expectedRemoteRepoConfigs);

        List<RemoteRepoConfig> actualRemoteRepoConfigs = repositoryConfigService.getRemoteRepoConfigs();

        assertNotNull(actualRemoteRepoConfigs);
        assertEquals(expectedRemoteRepoConfigs.size(), actualRemoteRepoConfigs.size());
        assertTrue(actualRemoteRepoConfigs.containsAll(
                expectedRemoteRepoConfigs.stream().map(RemoteRepoConfig.class::cast).toList()));
    }

    /**
     * Covers the case where a remote repository key might include "-cache" suffix, before it was restricted.
     */
    @Test
    void testGetRepoConfigByKeySkipRepoCacheValidation() {
        String repoKeyWithForbiddenDashCacheSuffix = "remote-repo-with-cache";
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create(repoKeyWithForbiddenDashCacheSuffix, Generic);

        RepositoryConfigServiceImpl repositoryConfigService = createService();

        RepoConfigCache repoConfigCache = mock(RepoConfigCache.class);
        when(repoConfigCache.getByKey(repoKeyWithForbiddenDashCacheSuffix)).thenReturn(remoteRepoConfig);
        ReflectionTestUtils.setField(repositoryConfigService, "repoConfigCache", repoConfigCache);

        when(storageService.findRepositoryByKey(repoKeyWithForbiddenDashCacheSuffix))
                .thenReturn(getDbRepositoryConfig(remoteRepoConfig));

        RepoConfig repoConfig = repositoryConfigService.getRepoConfigByKey(repoKeyWithForbiddenDashCacheSuffix, true);
        assertEquals(remoteRepoConfig, repoConfig);
    }

    @Test
    void testGetRepoConfigByKeyDashCacheSuffixThrowsException() {
        String repoKeyWithForbiddenDashCacheSuffix = "remote-repo-with-cache";

        RepositoryConfigServiceImpl repositoryConfigService = createService();

        assertThrows(IllegalArgumentException.class,
                () -> repositoryConfigService.getRepoConfigByKey(repoKeyWithForbiddenDashCacheSuffix, false));
    }

    @Test
    void testGetReleaseBundleRepoConfigs() {
        List<RepoConfig> expectedReleaseBundleRepoConfigs = List.of(
                ReleaseBundleRepoConfig.create("release-bundle-repo-1"),
                ReleaseBundleRepoConfig.create("release-bundle-repo-2")
        );

        RepositoryConfigServiceImpl repositoryConfigService =
                spy(createService());
        when(repositoryConfigService.getRepoConfigsByType(RELEASE_BUNDLE)).thenReturn(expectedReleaseBundleRepoConfigs);

        List<ReleaseBundleRepoConfig> actualReleaseBundleRepoConfigs =
                repositoryConfigService.getReleaseBundleRepoConfigs();

        assertNotNull(actualReleaseBundleRepoConfigs);
        assertEquals(expectedReleaseBundleRepoConfigs.size(), actualReleaseBundleRepoConfigs.size());
        assertTrue(actualReleaseBundleRepoConfigs.containsAll(
                expectedReleaseBundleRepoConfigs.stream().map(ReleaseBundleRepoConfig.class::cast).toList()));
    }

    @Test
    void testGetAllFederatedRepoConfigsWithSystemRepo() {
        List<RepoConfig> expectedFederatedRepoConfigs = List.of(
                FederatedRepoConfig.create("federated-repo-1", Generic),
                FederatedRepoConfig.create("federated-repo-2", RepoType.Maven),
                FederatedRepoConfig.create("pipe-info", PipeInfo));

        RepositoryConfigServiceImpl repositoryConfigService =
                spy(createService());
        when(repositoryConfigService.getRepoConfigsByType(FEDERATED)).thenReturn(expectedFederatedRepoConfigs);

        List<FederatedRepoConfig> actualFederatedRepoConfigs =
                repositoryConfigService.getAllFederatedRepoConfigsWithSystemRepo();

        assertNotNull(actualFederatedRepoConfigs);
        assertEquals(expectedFederatedRepoConfigs.size(), actualFederatedRepoConfigs.size());
        assertTrue(actualFederatedRepoConfigs.containsAll(
                expectedFederatedRepoConfigs.stream().map(FederatedRepoConfig.class::cast).toList()));
    }

    @Test
    void testGetFederatedRepoConfigs() {
        List<RepoConfig> expectedFederatedRepoConfigs = List.of(
                FederatedRepoConfig.create("federated-repo-1", Generic),
                FederatedRepoConfig.create("federated-repo-2", RepoType.Maven),
                FederatedRepoConfig.create("pipe-info", PipeInfo));

        RepositoryConfigServiceImpl repositoryConfigService =
                spy(createService());
        when(repositoryConfigService.getRepoConfigsByType(FEDERATED)).thenReturn(expectedFederatedRepoConfigs);

        List<FederatedRepoConfig> actualFederatedRepoConfigs = repositoryConfigService.getFederatedRepoConfigs();

        assertNotNull(actualFederatedRepoConfigs);
        assertEquals(2, actualFederatedRepoConfigs.size());
        assertTrue(actualFederatedRepoConfigs.stream().map(FederatedRepoConfig::getKey).toList()
                .containsAll(List.of("federated-repo-1", "federated-repo-2")));
        assertTrue(actualFederatedRepoConfigs.stream().map(RepoConfig::getKey).noneMatch("pipe-info"::equals));
    }

    @Test
    void testGetVirtualRepoConfigs() {
        List<RepoConfig> expectedVirtualRepoConfigs = List.of(
                VirtualRepoConfig.create("virtual-repo-1", Generic),
                VirtualRepoConfig.create("virtual-repo-2", RepoType.Maven)
        );

        RepositoryConfigServiceImpl repositoryConfigService =
                spy(createService());
        when(repositoryConfigService.getRepoConfigsByType(VIRTUAL)).thenReturn(expectedVirtualRepoConfigs);

        List<VirtualRepoConfig> actualVirtualRepoConfigs = repositoryConfigService.getVirtualRepoConfigs();

        assertNotNull(actualVirtualRepoConfigs);
        assertEquals(expectedVirtualRepoConfigs.size(), actualVirtualRepoConfigs.size());
        assertTrue(actualVirtualRepoConfigs.containsAll(
                expectedVirtualRepoConfigs.stream().map(VirtualRepoConfig.class::cast).toList()));
    }

    @Test
    void findRepositoryConfigsByKeysInSpecificOrder() {
        // given
        String repoKey1 = "repoKey";
        String repoKey2 = "Zopa";
        String repoKey3 = "Superman";
        LocalRepoConfig repositoryConfig1 = LocalRepoConfig.create(repoKey1, Generic);
        LocalRepoConfig repositoryConfig2 = LocalRepoConfig.create(repoKey2, Generic);
        LocalRepoConfig repositoryConfig3 = LocalRepoConfig.create(repoKey3, Generic);
        Set<String> repoKeys = new LinkedHashSet<>();
        repoKeys.add(repoKey1);
        repoKeys.add(repoKey2);
        repoKeys.add(repoKey3);
        List<DbRepositoryConfig> dbRepositoryConfigs = new ArrayList<>();
        Set<LocalRepoConfig> expected = Sets.newLinkedHashSet();
        DbRepositoryConfig dbRepositoryConfig1 = getDbRepositoryConfig(repositoryConfig1);
        DbRepositoryConfig dbRepositoryConfig2 = getDbRepositoryConfig(repositoryConfig2);
        DbRepositoryConfig dbRepositoryConfig3 = getDbRepositoryConfig(repositoryConfig3);
        dbRepositoryConfigs.add(dbRepositoryConfig1);
        dbRepositoryConfigs.add(dbRepositoryConfig3);
        dbRepositoryConfigs.add(dbRepositoryConfig2);
        expected.add(repositoryConfig1);
        expected.add(repositoryConfig2);
        expected.add(repositoryConfig3);
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("repo-key", Generic);
        virtualRepoConfig.getRepoTypeConfig().setRepositoryRefs(new ArrayList<>(repoKeys));
        when(storageService.getAllRepositories()).thenReturn(dbRepositoryConfigs);

        // when
        RepositoryConfigServiceImpl repositoryConfigService = createService();
        Set<RepoConfig> actual = repositoryConfigService.getRepoConfigsByKeysInSpecificOrder(virtualRepoConfig);

        // then
        assertNotNull(actual);
        assertIterableEquals(expected, actual);
    }

    @Test
    void findRepositoryConfigsByKeysInSpecificOrderWithDuplicateRefs() {
        // given
        String repoKey1 = "repoKey";
        String repoKey2 = "Zopa";
        String repoKey3 = "Superman";
        LocalRepoConfig repositoryConfig1 = LocalRepoConfig.create(repoKey1, Generic);
        LocalRepoConfig repositoryConfig2 = LocalRepoConfig.create(repoKey2, Generic);
        LocalRepoConfig repositoryConfig3 = LocalRepoConfig.create(repoKey3, Generic);
        List<String> orderedRefsWithDuplicates = new ArrayList<>();
        orderedRefsWithDuplicates.add(repoKey1);
        orderedRefsWithDuplicates.add(repoKey2);
        orderedRefsWithDuplicates.add(repoKey1);
        orderedRefsWithDuplicates.add(repoKey3);
        List<DbRepositoryConfig> dbRepositoryConfigs = new ArrayList<>();
        Set<LocalRepoConfig> expected = Sets.newLinkedHashSet();
        dbRepositoryConfigs.add(getDbRepositoryConfig(repositoryConfig1));
        dbRepositoryConfigs.add(getDbRepositoryConfig(repositoryConfig3));
        dbRepositoryConfigs.add(getDbRepositoryConfig(repositoryConfig2));
        expected.add(repositoryConfig1);
        expected.add(repositoryConfig2);
        expected.add(repositoryConfig3);
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("repo-key", Generic);
        virtualRepoConfig.getRepoTypeConfig().setRepositoryRefs(orderedRefsWithDuplicates);
        when(storageService.getAllRepositories()).thenReturn(dbRepositoryConfigs);

        // when
        RepositoryConfigServiceImpl repositoryConfigService = createService();
        Set<RepoConfig> actual = repositoryConfigService.getRepoConfigsByKeysInSpecificOrder(virtualRepoConfig);

        // then
        assertNotNull(actual);
        assertIterableEquals(expected, actual);
    }

    @Test
    void findRepositoryConfigsByKeys() {
        // given
        String repoKey1 = "repoKey";
        String repoKey2 = "Zopa";
        String repoKey3 = "Superman";
        LocalRepoConfig repositoryConfig1 = LocalRepoConfig.create(repoKey1, Generic);
        LocalRepoConfig repositoryConfig2 = LocalRepoConfig.create(repoKey2, Generic);
        LocalRepoConfig repositoryConfig3 = LocalRepoConfig.create(repoKey3, Generic);
        Set<String> repoKeys = new LinkedHashSet<>();
        repoKeys.add(repoKey1);
        repoKeys.add(repoKey2);
        repoKeys.add(repoKey3);
        List<DbRepositoryConfig> dbRepositoryConfigs = new ArrayList<>();
        Set<LocalRepoConfig> expected = Sets.newLinkedHashSet();
        DbRepositoryConfig dbRepositoryConfig1 = getDbRepositoryConfig(repositoryConfig1);
        DbRepositoryConfig dbRepositoryConfig2 = getDbRepositoryConfig(repositoryConfig2);
        DbRepositoryConfig dbRepositoryConfig3 = getDbRepositoryConfig(repositoryConfig3);
        dbRepositoryConfigs.add(dbRepositoryConfig1);
        dbRepositoryConfigs.add(dbRepositoryConfig3);
        dbRepositoryConfigs.add(dbRepositoryConfig2);
        expected.add(repositoryConfig1);
        expected.add(repositoryConfig3);
        expected.add(repositoryConfig2);
        when(storageService.getAllRepositories()).thenReturn(dbRepositoryConfigs);

        // when
        RepositoryConfigServiceImpl repositoryConfigService = createService();
        Set<RepoConfig> actual = repositoryConfigService.getRepoConfigsByKeys(repoKeys);

        // then
        assertNotNull(actual);
        assertEquals(actual, expected);
    }

    @Test
    void existsByType() {
        // given
        String repoKey1 = "repoKey";
        String repoKey2 = "Zopa";
        String repoKey3 = "Superman";
        String repoKey4 = "repoKey4";
        LocalRepoConfig repositoryConfig1 = LocalRepoConfig.create(repoKey1, Generic);
        RemoteRepoConfig repositoryConfig2 = RemoteRepoConfig.create(repoKey2, Generic);
        VirtualRepoConfig repositoryConfig3 = VirtualRepoConfig.create(repoKey3, Generic);
        FederatedRepoConfig repositoryConfig4 = FederatedRepoConfig.create(repoKey4, Generic);
        List<DbRepositoryConfig> dbRepositoryConfigs = new ArrayList<>();

        dbRepositoryConfigs.add(getDbRepositoryConfig(repositoryConfig1));
        dbRepositoryConfigs.add(getDbRepositoryConfig(repositoryConfig2));
        dbRepositoryConfigs.add(getDbRepositoryConfig(repositoryConfig3));
        dbRepositoryConfigs.add(getDbRepositoryConfig(repositoryConfig4));

        when(storageService.getAllRepositories()).thenReturn(dbRepositoryConfigs);

        // when
        RepositoryConfigServiceImpl repositoryConfigService = createService();
        // then
        assertTrue(repositoryConfigService.existsByType(LOCAL, repoKey1));
        assertFalse(repositoryConfigService.existsByType(REMOTE, repoKey1));
        assertTrue(repositoryConfigService.existsByType(REMOTE, repoKey2));
        assertFalse(repositoryConfigService.existsByType(REMOTE, repoKey2 + "-cache"));
        assertTrue(repositoryConfigService.existsByType(VIRTUAL, repoKey3));
        assertTrue(repositoryConfigService.existsByType(FEDERATED, repoKey4));
        assertFalse(repositoryConfigService.existsByType(VIRTUAL, "repoKey3"));

    }

    @Test
    void testLocalRepositoryByKeyForUnfoundedRepo() {
        when(storageService.findRepositoryByKey("repo-key")).thenReturn(null);

        RepositoryConfigServiceImpl repositoryConfigService = createService();
        RepoConfig retrievedRepositoryConfig = repositoryConfigService.getLocalRepoConfigByKey("repo-key");
        assertNull(retrievedRepositoryConfig);
    }

    @Test
    void testLocalRepositoryByKeyForFoundedRepo() {
        LocalRepoConfig repositoryConfig = LocalRepoConfig.create("repo-key", Generic);
        DbRepositoryConfig dbRepoConfig = repositoryConfigMapper.repositoryConfigToDbRepositoryConfig(repositoryConfig);
        when(storageService.getAllRepositories()).thenReturn(List.of(dbRepoConfig));

        RepositoryConfigServiceImpl repositoryConfigService = createService();
        RepoConfig retrievedRepositoryConfig = repositoryConfigService.getLocalRepoConfigByKey("repo-key");
        assertNotNull(retrievedRepositoryConfig);
        assertEquals(retrievedRepositoryConfig, repositoryConfig);
    }

    @Test
    void testLocalRepositoryByKeyWithInvalidRepoKey() {
        RepositoryConfigServiceImpl repositoryConfigService = createService();
        RepoConfig retrievedRepositoryConfig = repositoryConfigService.getLocalRepoConfigByKey("");
        assertNull(retrievedRepositoryConfig);
    }

    @Test
    void testLocalRepositoryByKeyWithWrongRepoType() {
        FederatedRepoConfig repositoryConfig = FederatedRepoConfig.create("repo-key", Generic);
        DbRepositoryConfig dbRepoConfig = repositoryConfigMapper.repositoryConfigToDbRepositoryConfig(repositoryConfig);
        when(storageService.getAllRepositories()).thenReturn(List.of(dbRepoConfig));

        RepositoryConfigServiceImpl repositoryConfigService = createService();
        RepoConfig retrievedRepositoryConfig = repositoryConfigService.getLocalRepoConfigByKey("repo-key");
        assertNull(retrievedRepositoryConfig);
    }

    @Test
    void testLocalOrFederatedRepoConfigByKeyForUnfoundRepo() {
        when(storageService.findRepositoryByKey("repo-key")).thenReturn(null);

        RepositoryConfigServiceImpl repositoryConfigService = createService();
        RepoConfig retrievedRepositoryConfig = repositoryConfigService.getLocalOrFederatedRepoConfigByKey("repo-key");
        assertNull(retrievedRepositoryConfig);
    }

    @Test
    void testLocalOrFederatedRepoConfigByKeyForLocal() {
        LocalRepoConfig repositoryConfig = LocalRepoConfig.create("repo-key", Generic);
        repositoryConfig.getSecurityConfig().setXrayDataTtl(repositoryConfig.getSecurityConfig().getXrayDataTtl());
        repositoryConfig.getSecurityConfig().setXrayIndex(repositoryConfig.getSecurityConfig().isXrayIndex());
        DbRepositoryConfig dbRepoConfig = repositoryConfigMapper.repositoryConfigToDbRepositoryConfig(repositoryConfig);
        when(storageService.getAllRepositories()).thenReturn(List.of(dbRepoConfig));

        RepositoryConfigServiceImpl repositoryConfigService = createService();
        RepoConfig retrievedRepositoryConfig = repositoryConfigService.getLocalOrFederatedRepoConfigByKey("repo-key");
        assertNotNull(retrievedRepositoryConfig);
        assertEquals(retrievedRepositoryConfig, repositoryConfig);
    }

    @Test
    void testLocalOrFederatedRepoConfigByKeyForFederated() {
        FederatedRepoConfig repositoryConfig = FederatedRepoConfig.create("repo-key", Generic);
        repositoryConfig.getSecurityConfig().setXrayDataTtl(repositoryConfig.getSecurityConfig().getXrayDataTtl());
        repositoryConfig.getSecurityConfig().setXrayIndex(repositoryConfig.getSecurityConfig().isXrayIndex());
        DbRepositoryConfig dbRepoConfig = repositoryConfigMapper.repositoryConfigToDbRepositoryConfig(repositoryConfig);
        when(storageService.getAllRepositories()).thenReturn(List.of(dbRepoConfig));

        RepositoryConfigServiceImpl repositoryConfigService = createService();
        RepoConfig retrievedRepositoryConfig = repositoryConfigService.getLocalOrFederatedRepoConfigByKey("repo-key");
        assertNotNull(retrievedRepositoryConfig);
        assertEquals(retrievedRepositoryConfig, repositoryConfig);
    }

    @Test
    void testLocalOrFederatedRepoConfigByKeyForRemote() {
        RemoteRepoConfig repositoryConfig = RemoteRepoConfig.create("repo-key", Generic);
        repositoryConfig.getSecurityConfig().setXrayDataTtl(repositoryConfig.getSecurityConfig().getXrayDataTtl());
        repositoryConfig.getSecurityConfig().setXrayIndex(repositoryConfig.getSecurityConfig().isXrayIndex());
        DbRepositoryConfig dbRepoConfig = repositoryConfigMapper.repositoryConfigToDbRepositoryConfig(repositoryConfig);
        when(storageService.findRepositoryByKey("repo-key")).thenReturn(dbRepoConfig);

        RepositoryConfigServiceImpl repositoryConfigService = createService();
        RepoConfig retrievedRepositoryConfig = repositoryConfigService.getLocalOrFederatedRepoConfigByKey("repo-key");
        assertNull(retrievedRepositoryConfig);
    }

    @Test
    void testLocalOrFederatedRepoConfigByKeyForVirtual() {
        VirtualRepoConfig repositoryConfig = VirtualRepoConfig.create("repo-key", Generic);
        repositoryConfig.getSecurityConfig().setXrayDataTtl(repositoryConfig.getSecurityConfig().getXrayDataTtl());
        repositoryConfig.getSecurityConfig().setXrayIndex(repositoryConfig.getSecurityConfig().isXrayIndex());
        DbRepositoryConfig dbRepoConfig = repositoryConfigMapper.repositoryConfigToDbRepositoryConfig(repositoryConfig);
        when(storageService.findRepositoryByKey("repo-key")).thenReturn(dbRepoConfig);

        RepositoryConfigServiceImpl repositoryConfigService = createService();
        RepoConfig retrievedRepositoryConfig = repositoryConfigService.getLocalOrFederatedRepoConfigByKey("repo-key");
        assertNull(retrievedRepositoryConfig);
    }

    @Test
    void findRepositoriesByType() {
        LocalRepoConfig repositoryConfig1 = LocalRepoConfig.create("repo1", Generic);
        VirtualRepoConfig repositoryConfig2 = VirtualRepoConfig.create("repo2", Generic);

        List<RepoConfig> repoConfigs = List.of(repositoryConfig1, repositoryConfig2);
        List<DbRepositoryConfig> dbRepoConfigs = repoConfigs.stream()
                .map(repoConfig -> repositoryConfigMapper.repositoryConfigToDbRepositoryConfig(repoConfig)).toList();
        when(storageService.getAllRepositories()).thenReturn(dbRepoConfigs);

        RepositoryConfigServiceImpl repositoryConfigService = createService();
        List<RepoConfig> returnedRepos = repositoryConfigService.getRepoConfigsByType(LOCAL);

        assertEquals(1, returnedRepos.size());
        assertEquals(LOCAL, returnedRepos.get(0).getRepoType());
    }

    @Test
    void findRepositoriesByPackageType() {
        LocalRepoConfig repositoryConfig1 = LocalRepoConfig.create("repo1", Docker);
        LocalRepoConfig repositoryConfig2 = LocalRepoConfig.create("repo2", BuildInfo);

        List<LocalRepoConfig> repoConfigs = List.of(repositoryConfig1, repositoryConfig2);
        List<DbRepositoryConfig> dbRepoConfigs = repoConfigs.stream()
                .map(repoConfig -> repositoryConfigMapper.repositoryConfigToDbRepositoryConfig(repoConfig)).toList();
        when(storageService.getAllRepositories()).thenReturn(dbRepoConfigs);

        RepositoryConfigServiceImpl repositoryConfigService = createService();
        List<RepoConfig> returnedRepos = repositoryConfigService.getRepoConfigsByPackageType(BuildInfo);

        assertEquals(1, returnedRepos.size());
        assertEquals(BuildInfo, returnedRepos.get(0).getPackageType());
    }

    @Test
    void findRepositoriesByTypeAndPackageType() {
        VirtualRepoConfig repositoryConfig1 = VirtualRepoConfig.create("repoKey1", Docker);
        RemoteRepoConfig repositoryConfig2 = RemoteRepoConfig.create("repoKey2", Vagrant);
        LocalRepoConfig repositoryConfig3 = LocalRepoConfig.create("repoKey3", Docker);

        List<RepoConfig> repoConfigs = List.of(repositoryConfig1, repositoryConfig2, repositoryConfig3);
        List<DbRepositoryConfig> dbRepoConfigs = repoConfigs.stream()
                .map(repoConfig -> repositoryConfigMapper.repositoryConfigToDbRepositoryConfig(repoConfig)).toList();

        when(storageService.getAllRepositories()).thenReturn(dbRepoConfigs);

        RepositoryConfigServiceImpl repositoryConfigService = createService();
        List<RepoConfig> returnedRepos =
                repositoryConfigService.getRepoConfigsByTypeAndPackageType(Set.of(REMOTE), Vagrant);

        assertEquals(1, returnedRepos.size());
        assertEquals(Vagrant, returnedRepos.get(0).getPackageType());
        assertEquals(REMOTE, returnedRepos.get(0).getRepoType());
    }

    @ParameterizedTest
    @MethodSource("repoTypesProvider")
    void testGetRepoConfigsByType(RepoDetailsType repoType) {
        RepoConfigCache repoConfigCache = mock(RepoConfigCache.class);
        RepositoryConfigServiceImpl repositoryConfigService = spy(
                createService(mock(RepositoryConfigStorageService.class)));
        repositoryConfigService.setCache(repoConfigCache);
        List<RepoConfig> repoConfigs = createRepoConfigs();
        Set<String> filteredKeys = repoConfigs.stream()
                .filter(config -> config.getRepoType().equals(repoType))
                .map(RepoConfig::getKey)
                .collect(Collectors.toSet());
        List<RepoConfig> filteredRepoConfigs =
                repoConfigs.stream().filter(config -> filteredKeys.contains(config.getKey())).toList();
        when(repositoryConfigService.getFilteredRepoKeysByTypes(Set.of(repoType))).thenReturn(filteredKeys.stream());
        when(repoConfigCache.getRepositoryConfigsByKeys(filteredKeys)).thenReturn(
                ImmutableList.copyOf(filteredRepoConfigs));

        List<RepoConfig> result = repositoryConfigService.getRepoConfigsByType(repoType);

        ArgumentCaptor<Set<String>> keysCaptor = ArgumentCaptor.forClass(Set.class);
        verify(repoConfigCache).getRepositoryConfigsByKeys(keysCaptor.capture());
        Set<String> capturedKeys = keysCaptor.getValue();
        assertEquals(filteredKeys, capturedKeys, "Captured keys should match the filtered keys");
        List<String> resultKeys = result.stream().map(RepoConfig::getKey).toList();
        assertTrue(resultKeys.containsAll(filteredKeys), "Result should contain all filtered RepoConfigs");
    }

    @ParameterizedTest
    @MethodSource("repoTypesSetProvider")
    void testGetRepoConfigsByTypes(Set<RepoDetailsType> repoTypes) {
        RepoConfigCache repoConfigCache = mock(RepoConfigCache.class);
        RepositoryConfigServiceImpl repositoryConfigService = spy(
                createService(mock(RepositoryConfigStorageService.class))
        );
        repositoryConfigService.setCache(repoConfigCache);
        List<RepoConfig> repoConfigs = createRepoConfigs();
        Set<String> filteredKeys = repoConfigs.stream()
                .filter(config -> repoTypes.contains(config.getRepoType()))
                .map(RepoConfig::getKey)
                .collect(Collectors.toSet());
        List<RepoConfig> filteredRepoConfigs = repoConfigs.stream()
                .filter(config -> filteredKeys.contains(config.getKey()))
                .toList();
        when(repositoryConfigService.getFilteredRepoKeysByTypes(repoTypes)).thenReturn(filteredKeys.stream());
        when(repoConfigCache.getRepositoryConfigsByKeys(filteredKeys)).thenReturn(
                ImmutableList.copyOf(filteredRepoConfigs));

        List<RepoConfig> result = repositoryConfigService.getRepoConfigsByTypes(repoTypes);

        ArgumentCaptor<Set<String>> keysCaptor = ArgumentCaptor.forClass(Set.class);
        verify(repoConfigCache).getRepositoryConfigsByKeys(keysCaptor.capture());
        assertEquals(filteredKeys, keysCaptor.getValue(), "Captured keys should match the filtered keys");
        List<String> resultKeys = result.stream().map(RepoConfig::getKey).toList();
        assertTrue(resultKeys.containsAll(filteredKeys), "Result should contain all filtered RepoConfigs");
    }

    @ParameterizedTest
    @MethodSource("repoTypesAndReleaseBundleEnabledProvider")
    void testGetFilteredRepoKeysByTypes(Set<RepoDetailsType> repoTypes) {
        List<RepoConfig> repoConfigs = createRepoConfigs();
        RepoConfigCache repoConfigCache = mock(RepoConfigCache.class);
        when(repoConfigCache.getAllBasicConfigs()).thenReturn(repoConfigs.stream().map(RepoConfig::toBasic).collect(
                Collectors.toSet()));
        RepositoryConfigServiceImpl repositoryConfigService =
                createService(mock(RepositoryConfigStorageService.class));
        repositoryConfigService.setCache(repoConfigCache);

        var repoKeys = repositoryConfigService.getFilteredRepoKeysByTypes(repoTypes).toList();

        repoConfigs = repoConfigs.stream().filter(repoConfig -> repoTypes.contains(repoConfig.getRepoType())).toList();
        assertEquals(repoKeys.size(), repoConfigs.size());
        repoConfigs.forEach(repoConfig -> assertTrue(repoKeys.contains(repoConfig.getKey())));
    }

    @ParameterizedTest
    @MethodSource("provideFiltersAndTypes")
    void testGetRepoConfigKeysByProjectAndType(Function<String, Boolean> projectFilter,
            Set<RepoDetailsType> repoTypes) {
        RepoConfigCache repoConfigCache = mock(RepoConfigCache.class);
        RepositoryConfigServiceImpl repositoryConfigService = spy(
                createService(mock(RepositoryConfigStorageService.class)));
        repositoryConfigService.setCache(repoConfigCache);
        List<RepoConfig> repoConfigs = createRepoConfigs();
        Set<String> keysByType = repoConfigs.stream()
                .filter(config -> repoTypes.contains(config.getRepoType()))
                .map(RepoConfig::getKey)
                .collect(Collectors.toSet());
        Set<String> expectedFilteredKeys = repoConfigs.stream()
                .filter(config -> repoTypes.contains(config.getRepoType()) &&
                        (projectFilter == null || projectFilter.apply(config.getKey())))
                .map(RepoConfig::getKey)
                .collect(Collectors.toSet());
        when(repositoryConfigService.getFilteredRepoKeysByTypes(repoTypes)).thenReturn(keysByType.stream());

        List<String> result = repositoryConfigService.getRepoConfigKeysByProjectAndType(projectFilter, repoTypes);

        List<String> expectedResult = expectedFilteredKeys.stream().toList();
        assertEquals(expectedResult, result);
    }

    @ParameterizedTest
    @MethodSource("repoTypesSetProvider")
    void testGetRepoConfigsByProjectAndType(Set<RepoDetailsType> repoTypes) {
        RepoConfigCache repoConfigCache = mock(RepoConfigCache.class);
        RepositoryConfigServiceImpl repositoryConfigService = spy(
                createService(mock(RepositoryConfigStorageService.class))
        );
        repositoryConfigService.setCache(repoConfigCache);
        Function<String, Boolean> projectFilter = key -> key.startsWith("local") || key.startsWith("federated");
        List<RepoConfig> repoConfigs = createRepoConfigs();
        Set<String> filteredKeys = repoConfigs.stream()
                .filter(config -> repoTypes.contains(config.getRepoType()) && projectFilter.apply(config.getKey()))
                .map(RepoConfig::getKey)
                .collect(Collectors.toSet());
        List<RepoConfig> filteredRepoConfigs = repoConfigs.stream()
                .filter(config -> filteredKeys.contains(config.getKey()))
                .toList();
        when(repositoryConfigService.getFilteredRepoKeysByProjectAndTypes(projectFilter, repoTypes)).thenReturn(
                filteredKeys.stream());
        when(repoConfigCache.getRepositoryConfigsByKeys(filteredKeys)).thenReturn(
                ImmutableList.copyOf(filteredRepoConfigs));

        List<RepoConfig> result = repositoryConfigService.getRepoConfigsByProjectAndType(projectFilter, repoTypes);

        ArgumentCaptor<Set<String>> keysCaptor = ArgumentCaptor.forClass(Set.class);
        verify(repoConfigCache).getRepositoryConfigsByKeys(keysCaptor.capture());
        assertEquals(filteredKeys, keysCaptor.getValue());
        List<String> resultKeys = result.stream().map(RepoConfig::getKey).toList();
        assertTrue(resultKeys.containsAll(filteredKeys));
    }

    @ParameterizedTest
    @MethodSource("provideFiltersAndTypes")
    void testGetFilteredRepoKeysByProjectAndTypes(Function<String, Boolean> projectFilter,
            Set<RepoDetailsType> repoTypes) {
        RepoConfigCache repoConfigCache = mock(RepoConfigCache.class);
        RepositoryConfigServiceImpl repositoryConfigService = spy(
                createService(mock(RepositoryConfigStorageService.class)));
        repositoryConfigService.setCache(repoConfigCache);
        List<RepoConfig> repoConfigs = createRepoConfigs();

        Set<String> keysByType = repoConfigs.stream()
                .filter(repoConfig -> repoTypes.contains(repoConfig.getRepoType()))
                .map(RepoConfig::getKey)
                .collect(Collectors.toSet());

        Set<String> expectedFilteredKeys = repoConfigs.stream()
                .filter(repoConfig -> repoTypes.contains(repoConfig.getRepoType()))
                .map(RepoConfig::getKey)
                .filter(key -> projectFilter == null || projectFilter.apply(key))
                .collect(Collectors.toSet());

        when(repositoryConfigService.getFilteredRepoKeysByTypes(repoTypes)).thenReturn(keysByType.stream());

        Set<String> resultWithFilterSet =
                repositoryConfigService.getFilteredRepoKeysByProjectAndTypes(projectFilter, repoTypes)
                        .collect(Collectors.toSet());
        assertEquals(expectedFilteredKeys, resultWithFilterSet);
    }

    @Test
    void testGetOrderedRepoConfigsByTypes() {
        RepoConfigCache repoConfigCache = mock(RepoConfigCache.class);
        RepositoryConfigServiceImpl repositoryConfigService = spy(
                createService(mock(RepositoryConfigStorageService.class))
        );
        repositoryConfigService.setCache(repoConfigCache);

        List<RepoConfig> repoConfigs = createRepoConfigs();
        LinkedHashSet<RepoDetailsType> repoTypes = new LinkedHashSet<>(List.of(LOCAL, REMOTE, FEDERATED));
        List<RepoConfig> expectedOrderedRepoConfigs = repoConfigs.stream()
                .filter(config -> repoTypes.contains(config.getRepoType()))
                .collect(Collectors.groupingBy(RepoConfig::getRepoType))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .flatMap(entry -> entry.getValue().stream())
                .toList();

        doReturn(repoConfigs).when(repositoryConfigService).getRepoConfigsByTypes(repoTypes);

        List<RepoConfig> result = repositoryConfigService.getOrderedRepoConfigsByTypes(repoTypes);

        assertEquals(expectedOrderedRepoConfigs, result);
    }

    @ParameterizedTest
    @MethodSource("provideTypesAndProject")
    void testHasRepositoriesByProjectAndTypes(Set<RepoDetailsType> types, String projectKey,
            boolean expectedHasAnyRepos) {
        RepoConfigCache repoConfigCache = mock(RepoConfigCache.class);
        RepositoryConfigServiceImpl repositoryConfigService = spy(
                createService(mock(RepositoryConfigStorageService.class)));
        repositoryConfigService.setCache(repoConfigCache);
        List<RepoConfig> repoConfigs = createRepoConfigs();
        when(repoConfigCache.getAllBasicConfigs()).thenReturn(repoConfigs.stream().map(RepoConfig::toBasic).collect(
                Collectors.toSet()));
        when(projectsService.getProjectRepositoriesKeys(eq(projectKey), anyBoolean())).thenReturn(
                repoConfigs.stream().map(
                        RepoConfig::getKey).filter(key -> !key.equals("local-repo-2")).collect(Collectors.toSet()));
        when(projectsService.getProjectRepositoriesKeys(eq("proj-2"), anyBoolean())).thenReturn(Set.of());

        boolean hasAnyRepos = repositoryConfigService.hasRepositoriesByProjectAndTypes(projectKey, types);

        assertEquals(expectedHasAnyRepos, hasAnyRepos);
    }

    @ParameterizedTest
    @ValueSource(strings = {"proj-1", "proj-2"})
    @NullAndEmptySource
    void testGetRepoConfigsByTypesAndProjectAndProject(String projectKey) {
        RepositoryConfigServiceImpl repositoryConfigService = setupRepositoryConfigService(projectKey);

        assertRepoConfigsByType(repositoryConfigService, projectKey, RepoDetailsType.LOCAL);
        assertRepoConfigsByType(repositoryConfigService, projectKey, RepoDetailsType.REMOTE);
        assertRepoConfigsByType(repositoryConfigService, projectKey, RepoDetailsType.VIRTUAL);
        assertRepoConfigsByType(repositoryConfigService, projectKey, RepoDetailsType.FEDERATED);
        assertRepoConfigsByType(repositoryConfigService, projectKey, RepoDetailsType.RELEASE_BUNDLE);

        List<RepoConfig> realRepos =
                repositoryConfigService.getRepoConfigsByTypesAndProject(Set.of(LOCAL, FEDERATED, REMOTE), projectKey);
        assertNotNull(realRepos);
        assertEquals(ProjectsUtils.isDefaultProject(projectKey) ? 9 : 3, realRepos.size());
        assertTrue(realRepos.stream().allMatch(RepoConfig::isReal));
    }

    private RepositoryConfigServiceImpl setupRepositoryConfigService(String projectKey) {
        List<RepoConfig> repoConfigs = createProjectRepoConfigs();
        mockProjectRepositoriesKeys(projectKey, repoConfigs);
        when(storageService.getAllRepositories()).thenReturn(createDbRepositoryConfigs(repoConfigs));
        return createService();
    }

    private void assertRepoConfigsByType(RepositoryConfigServiceImpl repositoryConfigService, String projectKey,
            RepoDetailsType type) {
        List<RepoConfig> repoConfigs =
                repositoryConfigService.getRepoConfigsByTypesAndProject(Set.of(type), projectKey);
        assertNotNull(repoConfigs);
        List<RepoConfig> expectedRepoConfigs = createProjectRepoConfigs().stream()
                .filter(repoConfig -> repoConfig.getRepoType() == type)
                .filter(repoConfig -> ProjectsUtils.isDefaultProject(projectKey) ||
                        repoConfig.getKey().startsWith(projectKey))
                .toList();
        assertEquals(expectedRepoConfigs.size(), repoConfigs.size());
        assertTrue(expectedRepoConfigs.containsAll(repoConfigs));
    }

    private List<RepoConfig> createProjectRepoConfigs() {
        return List.of(
                LocalRepoConfig.create("proj-1-localRepo", Generic),
                RemoteRepoConfig.create("proj-1-remoteRepo", Generic),
                VirtualRepoConfig.create("proj-1-virtualRepo", Generic),
                FederatedRepoConfig.create("proj-1-federatedRepo", Generic),
                ReleaseBundleRepoConfig.create("proj-1-releaseBundleRepo"),
                LocalRepoConfig.create("proj-2-localRepo", Generic),
                RemoteRepoConfig.create("proj-2-remoteRepo", Generic),
                VirtualRepoConfig.create("proj-2-virtualRepo", Generic),
                FederatedRepoConfig.create("proj-2-federatedRepo", Generic),
                LocalRepoConfig.create("proj-3-localRepo", Maven),
                RemoteRepoConfig.create("proj-3-remoteRepo", Maven),
                VirtualRepoConfig.create("proj-3-virtualRepo", Npm),
                FederatedRepoConfig.create("proj-3-federatedRepo", Gradle),
                ReleaseBundleRepoConfig.create("proj-3-releaseBundleRepo")
        );
    }

    private void mockProjectRepositoriesKeys(String projectKey, List<RepoConfig> repoConfigs) {
        when(projectsService.getProjectRepositoriesKeys(eq(projectKey), anyBoolean())).thenReturn(repoConfigs.stream()
                .map(RepoConfig::getKey)
                .filter(key -> ProjectsUtils.isDefaultProject(projectKey) || key.startsWith(projectKey))
                .collect(Collectors.toSet()));
    }

    private List<RepoConfig> createRepoConfigs() {
        LocalRepoConfig localRepoConfig1 = LocalRepoConfig.create("local-repo-1", Generic);
        LocalRepoConfig localRepoConfig2 = LocalRepoConfig.create("local-repo-2", ReleaseBundles);
        FederatedRepoConfig federatedRepoConfig = FederatedRepoConfig.create("federated-repo", Generic);
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("remote-repo", Generic);
        ReleaseBundleRepoConfig releaseBundleRepoConfig = ReleaseBundleRepoConfig.create("release-bundle-repo");
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("virtual-repo", Generic);
        LocalRepoConfig pipeInfo = LocalRepoConfig.create("pipe-info", PipeInfo);
        LocalRepoConfig inTransit = LocalRepoConfig.create(IN_TRANSIT_REPO_KEY, Generic);
        return List.of(localRepoConfig1, localRepoConfig2, federatedRepoConfig, remoteRepoConfig,
                releaseBundleRepoConfig, virtualRepoConfig, pipeInfo, inTransit);
    }

    private static Stream<Arguments> repoTypesProvider() {
        return Stream.of(
                Arguments.of(LOCAL),
                Arguments.of(REMOTE),
                Arguments.of(FEDERATED),
                Arguments.of(RELEASE_BUNDLE),
                Arguments.of(VIRTUAL));
    }

    private static Stream<Arguments> repoTypesSetProvider() {
        return Stream.of(
                Arguments.of(Set.of(LOCAL)),
                Arguments.of(Set.of(REMOTE)),
                Arguments.of(Set.of(FEDERATED)),
                Arguments.of(Set.of(RepoDetailsType.RELEASE_BUNDLE)),
                Arguments.of(Set.of(VIRTUAL)),
                Arguments.of(Set.of(LOCAL, REMOTE)),
                Arguments.of(Set.of(FEDERATED, RepoDetailsType.RELEASE_BUNDLE)),
                Arguments.of(Set.of(LOCAL, VIRTUAL, RepoDetailsType.RELEASE_BUNDLE))
        );
    }

    private static Stream<Arguments> repoTypesAndReleaseBundleEnabledProvider() {
        return Stream.of(
                Arguments.of(Set.of(), false),
                Arguments.of(Set.of(LOCAL), false),
                Arguments.of(Set.of(REMOTE), false),
                Arguments.of(Set.of(FEDERATED), false),
                Arguments.of(Set.of(RELEASE_BUNDLE), true),
                Arguments.of(Set.of(VIRTUAL), false),
                Arguments.of(Set.of(LOCAL, REMOTE), false),
                Arguments.of(Set.of(LOCAL, FEDERATED), false),
                Arguments.of(Set.of(LOCAL, RELEASE_BUNDLE), true),
                Arguments.of(Set.of(LOCAL, VIRTUAL), false),
                Arguments.of(Set.of(REMOTE, FEDERATED), false),
                Arguments.of(Set.of(REMOTE, RELEASE_BUNDLE), true),
                Arguments.of(Set.of(REMOTE, VIRTUAL), false),
                Arguments.of(Set.of(FEDERATED, RELEASE_BUNDLE), true),
                Arguments.of(Set.of(FEDERATED, VIRTUAL), false),
                Arguments.of(Set.of(RELEASE_BUNDLE, VIRTUAL), true),
                Arguments.of(Set.of(LOCAL, FEDERATED, REMOTE, RELEASE_BUNDLE, VIRTUAL), false),
                Arguments.of(Set.of(LOCAL, FEDERATED, REMOTE, RELEASE_BUNDLE, VIRTUAL), true)
        );
    }

    private static Stream<Arguments> provideTypesAndProject() {
        return Stream.of(
                Arguments.of(Set.of(), "proj-1", false),
                Arguments.of(Set.of(LOCAL), "proj-1", true),
                Arguments.of(Set.of(LOCAL, REMOTE), "proj-1", true),
                Arguments.of(Set.of(REMOTE), "proj-1", true),
                Arguments.of(Set.of(FEDERATED, REMOTE), "proj-1", true),
                Arguments.of(Set.of(REMOTE, VIRTUAL), "proj-1", true),
                Arguments.of(Set.of(REMOTE, VIRTUAL, FEDERATED), "proj-1", true),
                Arguments.of(Set.of(REMOTE, VIRTUAL, FEDERATED, LOCAL), "proj-1", true),
                Arguments.of(Set.of(), DEFAULT_PROJECT, false),
                Arguments.of(Set.of(LOCAL), DEFAULT_PROJECT, true),
                Arguments.of(Set.of(LOCAL, REMOTE), DEFAULT_PROJECT, true),
                Arguments.of(Set.of(REMOTE), DEFAULT_PROJECT, true),
                Arguments.of(Set.of(FEDERATED, REMOTE), DEFAULT_PROJECT, true),
                Arguments.of(Set.of(REMOTE, VIRTUAL), DEFAULT_PROJECT, true),
                Arguments.of(Set.of(REMOTE, VIRTUAL, FEDERATED), DEFAULT_PROJECT, true),
                Arguments.of(Set.of(LOCAL, REMOTE, FEDERATED, VIRTUAL, RELEASE_BUNDLE), DEFAULT_PROJECT, true),
                Arguments.of(null, DEFAULT_PROJECT, true),
                Arguments.of(Set.of(REMOTE, VIRTUAL, FEDERATED, LOCAL), DEFAULT_PROJECT, true),
                Arguments.of(Set.of(), "", false),
                Arguments.of(Set.of(LOCAL), "", true),
                Arguments.of(Set.of(LOCAL, REMOTE), "", true),
                Arguments.of(Set.of(REMOTE), "", true),
                Arguments.of(Set.of(FEDERATED, REMOTE), "", true),
                Arguments.of(Set.of(REMOTE, VIRTUAL), "", true),
                Arguments.of(Set.of(REMOTE, VIRTUAL, FEDERATED), "", true),
                Arguments.of(Set.of(REMOTE, VIRTUAL, FEDERATED, LOCAL), "", true),
                Arguments.of(Set.of(REMOTE, VIRTUAL, FEDERATED, LOCAL), "proj-2", false)
        );
    }

    private static Stream<Arguments> provideFiltersAndTypes() {
        Function<String, Boolean> filterWithPrefixLocal = key -> key.startsWith("local");
        Function<String, Boolean> filterWithPrefixRemote = key -> key.startsWith("remote");

        return Stream.of(
                Arguments.of(filterWithPrefixLocal, Set.of(LOCAL, REMOTE)),
                Arguments.of(filterWithPrefixRemote, Set.of(REMOTE)),
                Arguments.of(null, Set.of(LOCAL, REMOTE))
        );
    }

    @Test
    void testFindRepositoriesByKeys() {

        List<DbRepositoryConfig> reposInStore = new ArrayList<>();
        Set<String> repoKeys = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            RepoConfig repoConfig = LocalRepoConfig.create("repo-" + i, Generic);
            repoKeys.add(repoConfig.getKey());
            reposInStore.add(repositoryConfigMapper.repositoryConfigToDbRepositoryConfig(repoConfig));
        }
        when(storageService.getAllRepositories()).thenReturn(reposInStore);

        RepositoryConfigServiceImpl repositoryConfigService = createService();
        Set<RepoConfig> repoConfigs = repositoryConfigService.getRepoConfigsByKeys(repoKeys);

        Contracts.assertNotNull(repoConfigs);
        assertTrue(repoConfigs.containsAll(reposInStore.stream()
                .map(dbRepoConfig -> repositoryConfigMapper.dbInfoBlobToRepositoryConfig(dbRepoConfig)).toList()));
    }

    @Test
    void testGetExtendedRepositoryConfig() {
        // given
        LocalRepoConfig repositoryConfig = LocalRepoConfig.create("repoKey", Maven);
        repositoryConfig.getPackageTypeConfig()
                .put(POM_REPOSITORY_REFERENCES_CLEANUP_POLICY.getKey(), "discard_any_reference");
        repositoryConfig.getPackageTypeConfig().put(FETCH_JARS_EAGERLY.getKey(), Boolean.TRUE.toString());
        DbRepositoryConfig dbRepositoryConfig =
                repositoryConfigMapper.repositoryConfigToDbRepositoryConfig(repositoryConfig);
        when(storageService.getAllRepositories()).thenReturn(List.of(dbRepositoryConfig));

        // when
        RepositoryConfigServiceImpl repositoryConfigService = createService();
        ExtendedRepositoryConfig<MavenConfiguration> extendedConfig =
                repositoryConfigService.getExtendedRepoConfigByKey("repoKey", MavenConfiguration.class);

        // then
        assertNotNull(extendedConfig);
        assertEquals(PomCleanupPolicy.discard_any_reference,
                extendedConfig.getExtendedRepoConfig().getPomRepositoryReferencesCleanupPolicy());
    }

    @Test
    void testUpsertRepositoryConfigByKeyWithSupportBundleRepoKey() {
        // given
        LocalRepoConfig repositoryConfig = LocalRepoConfig.create(SUPPORT_BUNDLE_REPO_KEY, Generic);

        // when
        RepositoryConfigServiceImpl repositoryConfigService = createService();
        ConfigValidationService configValidationService = mock(ConfigValidationService.class);
        doNothing().when(configValidationService).validate(any());
        repositoryConfigService.setConfigValidationService(configValidationService);
        RepositoryConfigServiceImpl spy = spy(repositoryConfigService);
        doReturn(LocalRepoConfig.create("repoKey-cache", Generic)).when(spy)
                .getRepoConfigByKeyNoValidationChecks(any(), anyBoolean());
        // then
        BadRequestException thrown =
                assertThrows(BadRequestException.class, () -> spy.upsertRepoConfig(repositoryConfig),
                        "'jfrog-support-bundle' repository, the repo name is forbidden");
        assertEquals("'jfrog-support-bundle' repository, the repo name is forbidden", thrown.getMessage());
    }

    @Test
    void testInsertRepositoryConfigByKeyWithSupportBundleRepoKey() {
        // given
        LocalRepoConfig repositoryConfig = LocalRepoConfig.create(SUPPORT_BUNDLE_REPO_KEY, Generic);

        // when
        RepositoryConfigServiceImpl repositoryConfigService = createService();
        // then
        BadRequestException thrown = assertThrows(BadRequestException.class,
                () -> repositoryConfigService.insertRepoConfig(repositoryConfig),
                "'jfrog-support-bundle' repository, the repo name is forbidden");
        assertEquals("'jfrog-support-bundle' repository, the repo name is forbidden", thrown.getMessage());
    }

    @Test
    void testUpsertDoUpdateRepositoryConfigByKeyWithDashCache() {
        try {
            // given
            LocalRepoConfig repositoryConfig = LocalRepoConfig.create("repoKey-cache", Generic);

            // when
            RepositoryConfigServiceImpl repositoryConfigService =
                    createService();
            ConfigValidationService configValidationService = mock(ConfigValidationService.class);
            doNothing().when(configValidationService).validate(any());
            repositoryConfigService.setConfigValidationService(configValidationService);
            RepositoryConfigServiceImpl spy = spy(repositoryConfigService);
            doReturn(LocalRepoConfig.create("repoKey-cache", Generic)).when(spy)
                    .getRepoConfigByKeyNoValidationChecks(any(), anyBoolean());

            spy.upsertRepoConfig(repositoryConfig);

            // then
            ArgumentCaptor<Set<DbRepositoryConfig>> insert = ArgumentCaptor.forClass(Set.class);
            ArgumentCaptor<Set<DbRepositoryConfig>> update = ArgumentCaptor.forClass(Set.class);
            ArgumentCaptor<Set<String>> delete = ArgumentCaptor.forClass(Set.class);
            verify(storageService).flushBulkChanges(insert.capture(), update.capture(), delete.capture(),
                    any(), any());

            Set<DbRepositoryConfig> updatedDbRepositoryConfigs = update.getValue();
            assertEquals(1, updatedDbRepositoryConfigs.size());
            RepoConfig updatedRepositoryConfig = repositoryConfigMapper.dbInfoBlobToRepositoryConfig(
                    updatedDbRepositoryConfigs.stream().findFirst().orElse(new DbRepositoryConfig()));

            assertEquals(updatedRepositoryConfig, repositoryConfig);

            assertTrue(insert.getValue().isEmpty());
            assertTrue(delete.getValue().isEmpty());
        } finally {
            ArtifactoryContextThreadBinder.unbind();
        }
    }

    @Test
    void deleteLocalRepoWithDashCacheInSuffixName() {
        try {
            ConfigValidationService configValidationService = mock(ConfigValidationService.class);
            doNothing().when(configValidationService).validate(any());
            SecurityService securityService = mock(SecurityService.class);
            when(securityService.isAnyProjectAdmin()).thenReturn(true);
            when(context.beanForType(SecurityService.class)).thenReturn(securityService);
            RepositoryConfigServiceImpl repositoryConfigService =
                    createService();
            repositoryConfigService.setConfigValidationService(configValidationService);
            RepositoryConfigServiceImpl spy = spy(repositoryConfigService);
            LocalRepoConfig localRepoConfig = LocalRepoConfig.create("repoKey-cache", Generic);
            doReturn(localRepoConfig).when(spy).getRepoConfigByKeyNoValidationChecks(any(), anyBoolean());
            doNothing().when(spy).dereferenceRepositoryFromConfigDescriptor(any());
            spy.deleteRepoConfig("repoKey-cache");

            // then
            ArgumentCaptor<Set<DbRepositoryConfig>> insert = ArgumentCaptor.forClass(Set.class);
            ArgumentCaptor<Set<DbRepositoryConfig>> update = ArgumentCaptor.forClass(Set.class);
            ArgumentCaptor<Set<String>> delete = ArgumentCaptor.forClass(Set.class);
            verify(storageService).flushBulkChanges(insert.capture(), update.capture(), delete.capture(),
                    any(), any());

            Set<String> deletedKeys = delete.getValue();
            assertEquals(1, deletedKeys.size());
            assertTrue(deletedKeys.contains("repoKey-cache"));
            assertTrue(insert.getValue().isEmpty());
            assertTrue(update.getValue().isEmpty());
        } finally {
            ArtifactoryContextThreadBinder.unbind();
        }
    }

    @Test
    void deleteRemoteRepoWithDashCacheInSuffixName() {
        try {
            ConfigValidationService configValidationService = mock(ConfigValidationService.class);
            doNothing().when(configValidationService).validate(any());
            SecurityService securityService = mock(SecurityService.class);
            when(securityService.isAnyProjectAdmin()).thenReturn(true);
            when(context.beanForType(SecurityService.class)).thenReturn(securityService);
            RepositoryConfigServiceImpl repositoryConfigService =
                    createService();
            repositoryConfigService.setConfigValidationService(configValidationService);
            RepositoryConfigServiceImpl spy = spy(repositoryConfigService);
            RemoteRepoConfig localRepoConfig = RemoteRepoConfig.create("repoKey-cache", Generic);
            doReturn(localRepoConfig).when(spy).getRepoConfigByKeyNoValidationChecks(any(), anyBoolean());
            doNothing().when(spy).dereferenceRepositoryFromConfigDescriptor(any());
            spy.deleteRepoConfig("repoKey-cache");

            // then
            ArgumentCaptor<Set<DbRepositoryConfig>> insert = ArgumentCaptor.forClass(Set.class);
            ArgumentCaptor<Set<DbRepositoryConfig>> update = ArgumentCaptor.forClass(Set.class);
            ArgumentCaptor<Set<String>> delete = ArgumentCaptor.forClass(Set.class);
            verify(storageService).flushBulkChanges(insert.capture(), update.capture(), delete.capture(),
                    any(), any());

            Set<String> deletedKeys = delete.getValue();
            assertEquals(1, deletedKeys.size());
            assertTrue(deletedKeys.contains("repoKey-cache"));
            assertTrue(insert.getValue().isEmpty());
            assertTrue(update.getValue().isEmpty());
        } finally {
            ArtifactoryContextThreadBinder.unbind();
        }
    }

    @Test
    void testUpsertDoInsertRepositoryConfigByKeyWithDashCache() {
        // given
        LocalRepoConfig repositoryConfig = LocalRepoConfig.create("repoKey-cache", Generic);

        // when
        ConfigValidationService configValidationService = mock(ConfigValidationService.class);
        doNothing().when(configValidationService).validate(any());
        RepositoryConfigServiceImpl repositoryConfigService = createService();
        repositoryConfigService.setConfigValidationService(configValidationService);
        RepositoryConfigServiceImpl spy = spy(repositoryConfigService);
        doReturn(null).when(spy).getRepoConfigByKeyNoValidationChecks(any(), anyBoolean());
        // then
        BadRequestException thrown =
                assertThrows(BadRequestException.class, () -> spy.upsertRepoConfig(repositoryConfig),
                        "Unable to create a local repository 'repoKey-cache' with '-cache' suffix");
        assertEquals("Unable to create a local repository 'repoKey-cache' with '-cache' suffix", thrown.getMessage());
    }

    @Test
    void testInsertRepositoryConfigByKey() {
        try {
            // given
            LocalRepoConfig repositoryConfig = LocalRepoConfig.create("repoKey", Generic);
            repositoryConfig.getSecurityConfig().setXrayDataTtl(repositoryConfig.getSecurityConfig().getXrayDataTtl());
            repositoryConfig.getSecurityConfig().setXrayIndex(repositoryConfig.getSecurityConfig().isXrayIndex());

            // when
            SecurityService securityService = mock(SecurityService.class);
            when(securityService.isAnyProjectAdmin()).thenReturn(true);
            when(context.beanForType(SecurityService.class)).thenReturn(securityService);
            RepositoryConfigServiceImpl repositoryConfigService =
                    createService();
            repositoryConfigService.setConfigValidationService(new ConfigValidationService());
            repositoryConfigService.insertRepoConfig(repositoryConfig);


            // then
            ArgumentCaptor<Set<DbRepositoryConfig>> insert = ArgumentCaptor.forClass(Set.class);
            ArgumentCaptor<Set<DbRepositoryConfig>> update = ArgumentCaptor.forClass(Set.class);
            ArgumentCaptor<Set<String>> delete = ArgumentCaptor.forClass(Set.class);
            verify(storageService).flushBulkChanges(insert.capture(), update.capture(), delete.capture(),
                    any(), any());

            Set<DbRepositoryConfig> insertedDbRepositoryConfigs = insert.getValue();
            assertEquals(1, insertedDbRepositoryConfigs.size());
            RepoConfig insertedRepositoryConfig = repositoryConfigMapper.dbInfoBlobToRepositoryConfig(
                    insertedDbRepositoryConfigs.stream().findFirst().orElse(new DbRepositoryConfig()));

            assertEquals(insertedRepositoryConfig, repositoryConfig);

            assertTrue(update.getValue().isEmpty());
            assertTrue(delete.getValue().isEmpty());
        } finally {
            ArtifactoryContextThreadBinder.unbind();
        }
    }

    @Test
    void testInsertRepositoryConfigProcessUrlCredentials() {
        RemoteRepoConfig repositoryConfig = spy(RemoteRepoConfig.create("repoKey", Generic));
        RepositoryConfigServiceImpl repositoryConfigService =
                spy(createService());
        doNothing().when(repositoryConfigService).globalValidation(any(), anyBoolean());

        repositoryConfigService.insertRepoConfig(repositoryConfig);

        verify(repositoryConfig).processUrlCredentials();
    }

    @Test
    void testInsertRepositoryConfigByKeyWithDashCache() {
        // given
        LocalRepoConfig repositoryConfig = LocalRepoConfig.create("repoKey-cache", Generic);

        // when
        RepositoryConfigServiceImpl repositoryConfigService = createService();
        // then
        BadRequestException thrown = assertThrows(BadRequestException.class,
                () -> repositoryConfigService.insertRepoConfig(repositoryConfig),
                "Unable to create a local repository 'repoKey-cache' with '-cache' suffix");
        assertEquals("Unable to create a local repository 'repoKey-cache' with '-cache' suffix", thrown.getMessage());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testUpdateRepositoryConfigByKey(boolean skipRepoKeyValidation) {
        try {
            // given
            SecurityService securityService = mock(SecurityService.class);
            when(securityService.isAnyProjectAdmin()).thenReturn(true);
            when(context.beanForType(SecurityService.class)).thenReturn(securityService);

            LocalRepoConfig oldRepoConfig = LocalRepoConfig.create("repoKey", Generic);
            DbRepositoryConfig dbRepositoryConfig = new DbRepositoryConfig();
            dbRepositoryConfig.setKey("repoKey");
            dbRepositoryConfig.setPackageType(Gradle.toString());
            dbRepositoryConfig.setType(LOCAL.typeNameLowercase());
            when(storageService.getAllBasicRepoConfigs()).thenReturn(Set.of(dbRepositoryConfig));
            when(storageService.getAllRepositories()).thenReturn(
                    List.of(repositoryConfigMapper.repositoryConfigToDbRepositoryConfig(oldRepoConfig)));

            LocalRepoConfig newRepoConfig = LocalRepoConfig.create("repoKey", Generic);
            newRepoConfig.getBaseConfig().setDescription("description-y description");

            // when
            RepositoryConfigServiceImpl repositoryConfigService =
                    spy(createService());
            repositoryConfigService.setConfigValidationService(new ConfigValidationService());
            repositoryConfigService.updateRepoConfig(newRepoConfig, skipRepoKeyValidation);


            // then
            ArgumentCaptor<Set<DbRepositoryConfig>> insert = ArgumentCaptor.forClass(Set.class);
            ArgumentCaptor<Set<DbRepositoryConfig>> update = ArgumentCaptor.forClass(Set.class);
            ArgumentCaptor<Set<String>> delete = ArgumentCaptor.forClass(Set.class);
            ArgumentCaptor<Boolean> validation = ArgumentCaptor.forClass(Boolean.class);
            verify(storageService).flushBulkChanges(insert.capture(), update.capture(), delete.capture(),
                    any(), any());

            Set<DbRepositoryConfig> updatedDbRepositoryConfigs = update.getValue();
            assertEquals(1, updatedDbRepositoryConfigs.size());
            RepoConfig updatedRepositoryConfig =
                    repositoryConfigMapper.dbInfoBlobToRepositoryConfig(updatedDbRepositoryConfigs.iterator().next());
            assertEquals(updatedRepositoryConfig.getBaseConfig().getDescription(),
                    newRepoConfig.getBaseConfig().getDescription());

            assertTrue(insert.getValue().isEmpty());
            assertTrue(delete.getValue().isEmpty());
            verify(repositoryConfigService).globalValidation(any(), validation.capture());
            assertEquals(skipRepoKeyValidation, validation.getValue());
        } finally {
            ArtifactoryContextThreadBinder.unbind();
        }
    }

    @Test
    void testUpdateRepoConfigWithGlobalValidation() {
        RepositoryConfigServiceImpl repositoryConfigService =
                spy(createService());
        repositoryConfigService.setConfigValidationService(new ConfigValidationService());
        RepoConfig repoConfig = LocalRepoConfig.create("repoKey", Generic);
        doReturn(repoConfig).when(repositoryConfigService).getRepoConfigByKey(repoConfig.getKey());

        repositoryConfigService.updateRepoConfig(repoConfig);

        verify(repositoryConfigService).updateRepoConfig(repoConfig, false);
    }

    @Test
    void testUpdateRepoConfigWithProcessUrlCredentials() {
        RepositoryConfigServiceImpl repositoryConfigService =
                spy(createService());
        RemoteRepoConfig repoConfig = spy(RemoteRepoConfig.create("repoKey", Generic));
        doNothing().when(repositoryConfigService).globalValidation(any(), anyBoolean());
        when(repositoryConfigService.getRepoConfigByKey("repoKey")).thenReturn(repoConfig);

        repositoryConfigService.updateRepoConfig(repoConfig);

        verify(repoConfig).processUrlCredentials();
    }

    @Test
    void testGetFirstLevelAssociateVirtualReposCountWhenNull() {
        RepositoryConfigServiceImpl repositoryConfigService = createService();
        assertFalse(repositoryConfigService.hasDirectlyAssociatedVirtualRepos(null));
    }

    @Test
    void testGetFirstLevelAssociateVirtualReposCountWithoutRefs() {
        RepoConfig repoConfig = LocalRepoConfig.create("local", Generic);
        RepositoryConfigServiceImpl repositoryConfigService =
                spy(createService());
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("virtual", Generic);
        List<RepoConfig> repoConfigList = List.of(virtualRepoConfig);
        when(repositoryConfigService.getRepoConfigsByType(VIRTUAL)).thenReturn(repoConfigList);
        virtualRepoConfig.getRepoTypeConfig().setRepositoryRefs(List.of());
        assertFalse(repositoryConfigService.hasDirectlyAssociatedVirtualRepos(repoConfig));
    }

    @Test
    void testHasDirectlyAssociatedVirtualRepos_LocalCacheRepoConfig() {
        String remoteRepoKey = "remote-repo-key";
        String virtualRepoKey = "virtual-repo-key";
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create(remoteRepoKey, Generic);

        LocalCacheRepoConfig localCacheRepoConfig = LocalCacheRepoConfig.create(remoteRepoConfig);

        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create(virtualRepoKey, Generic);
        virtualRepoConfig.getRepoTypeConfig().setRepositoryRefs(List.of(remoteRepoKey));

        RepositoryConfigServiceImpl repositoryConfigService =
                spy(createService());
        doReturn(List.of(virtualRepoConfig)).when(repositoryConfigService).getVirtualRepoConfigs();

        assertTrue(repositoryConfigService.hasDirectlyAssociatedVirtualRepos(localCacheRepoConfig));
    }

    @Test
    void testGetFirstLevelAssociateVirtualReposCountWhenVirtualRepoConfigNull() {
        RepoConfig repoConfig = LocalRepoConfig.create("local", Generic);
        RepositoryConfigServiceImpl repositoryConfigService =
                spy(createService());
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("virtual", Generic);
        List<RepoConfig> repoConfigList = List.of(virtualRepoConfig);
        when(repositoryConfigService.getRepoConfigsByType(VIRTUAL)).thenReturn(repoConfigList);
        assertFalse(repositoryConfigService.hasDirectlyAssociatedVirtualRepos(repoConfig));
    }

    @Test
    void testGetFirstLevelAssociateVirtualReposCount() {
        RepoConfig repoConfig = LocalRepoConfig.create("local", Generic);
        RepositoryConfigServiceImpl repositoryConfigService =
                spy(createService());
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("virtual", Generic);
        List<RepoConfig> repoConfigList = List.of(virtualRepoConfig);
        List<@NotUnicode String> repositoryRefs = List.of("local");
        when(repositoryConfigService.getRepoConfigsByType(VIRTUAL)).thenReturn(repoConfigList);
        when(repositoryConfigService.getVirtualRepoConfigByKey(virtualRepoConfig.getKey())).thenReturn(
                virtualRepoConfig);
        virtualRepoConfig.getRepoTypeConfig().setRepositoryRefs(repositoryRefs);
        assertTrue(repositoryConfigService.hasDirectlyAssociatedVirtualRepos(repoConfig));
    }

    @Test
    void testCannotUpdateRepositoryThatDoesNotExist() {
        try {
            // given
            SecurityService securityService = mock(SecurityService.class);
            when(securityService.isAnyProjectAdmin()).thenReturn(true);
            when(context.beanForType(SecurityService.class)).thenReturn(securityService);

            when(storageService.findRepositoryByKey("repoKey")).thenReturn(null);

            LocalRepoConfig newRepoConfig = LocalRepoConfig.create("repoKey", Generic);
            newRepoConfig.getBaseConfig().setDescription("description-y description");

            // when
            RepositoryConfigServiceImpl repositoryConfigService =
                    createService();
            repositoryConfigService.setConfigValidationService(new ConfigValidationService());
            assertThrows(NotFoundException.class, () -> repositoryConfigService.updateRepoConfig(newRepoConfig));
        } finally {
            ArtifactoryContextThreadBinder.unbind();
        }
    }

    @Test
    void testExistsByPackageType() {
        LocalRepoConfig repositoryConfig1 = LocalRepoConfig.create("repo1", Docker);
        LocalRepoConfig repositoryConfig3 = LocalRepoConfig.create(SUPPORT_BUNDLE_REPO_KEY, Generic);

        List<LocalRepoConfig> repoConfigs = List.of(repositoryConfig1, repositoryConfig3);
        List<DbRepositoryConfig> dbRepoConfigs = repoConfigs.stream()
                .map(repoConfig -> repositoryConfigMapper.repositoryConfigToDbRepositoryConfig(repoConfig))
                .toList();
        when(storageService.getAllRepositories()).thenReturn(dbRepoConfigs);

        RepositoryConfigServiceImpl repositoryConfigService = createService();
        assertTrue(repositoryConfigService.existsByPackageType(Docker, "repo1"));
        assertFalse(repositoryConfigService.existsByPackageType(Generic, SUPPORT_BUNDLE_REPO_KEY));
        assertFalse(repositoryConfigService.existsByPackageType(Docker, "repo2"));
        assertFalse(repositoryConfigService.existsByPackageType(null, "repo1"));
    }

    @Test
    void testDeleteAndDereferenceRepository() {
        try {
            // given
            SecurityService securityService = mock(SecurityService.class);
            when(securityService.isAnyProjectAdmin()).thenReturn(true);
            when(context.beanForType(SecurityService.class)).thenReturn(securityService);

            String repoKey = "repoKey";
            LocalRepoConfig oldRepoConfig = LocalRepoConfig.create(repoKey, Generic);
            DbRepositoryConfig oldRepoDbConfig =
                    repositoryConfigMapper.repositoryConfigToDbRepositoryConfig(oldRepoConfig);

            String virtualRepoKey = "virtual-repo";
            VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create(virtualRepoKey, Generic);
            virtualRepoConfig.getRepoTypeConfig().getRepositoryRefs().add(repoKey);
            // Set defaultDeploymentRepoRef which will be dereferenced on delete
            // (repositoryRefs are intentionally kept for eventual consistency - dangling refs allowed)
            virtualRepoConfig.getRepoTypeConfig().setDefaultDeploymentRepoRef(repoKey);
            DbRepositoryConfig virtualDbRepositoryConfig =
                    repositoryConfigMapper.repositoryConfigToDbRepositoryConfig(virtualRepoConfig);

            DbRepositoryConfig localRepo = new DbRepositoryConfig();
            localRepo.setKey(repoKey);
            localRepo.setPackageType(Gradle.toString());
            localRepo.setType(LOCAL.typeNameLowercase());

            DbRepositoryConfig virtualRepo = new DbRepositoryConfig();
            virtualRepo.setKey(virtualRepoKey);
            virtualRepo.setPackageType(Gradle.toString());
            virtualRepo.setType(VIRTUAL.typeNameLowercase());

            when(storageService.getAllBasicRepoConfigs()).thenReturn(Set.of(localRepo, virtualRepo));
            when(storageService.getAllRepositories()).thenReturn(List.of(oldRepoDbConfig, virtualDbRepositoryConfig));

            // config descriptor references
            CentralConfigDescriptorImpl configDescriptor = new CentralConfigDescriptorImpl();
            populateConfigDescriptorWithRefsToRepo(repoKey, configDescriptor);
            when(configService.getMutableDescriptor()).thenReturn(configDescriptor);

            // when
            RepositoryConfigServiceImpl repositoryConfigService =
                    createService();
            repositoryConfigService.setConfigValidationService(new ConfigValidationService());
            repositoryConfigService.deleteRepoConfig(repoKey);

            // then
            ArgumentCaptor<Set<DbRepositoryConfig>> insert = ArgumentCaptor.forClass(Set.class);
            ArgumentCaptor<Set<DbRepositoryConfig>> update = ArgumentCaptor.forClass(Set.class);
            ArgumentCaptor<Set<String>> delete = ArgumentCaptor.forClass(Set.class);
            verify(storageService, times(1)).flushBulkChanges(insert.capture(), update.capture(), delete.capture(),
                    any(), any());

            // repo was deleted
            Set<String> deletedRepositoryConfigs = delete.getValue();
            assertEquals(1, deletedRepositoryConfigs.size());
            assertEquals(repoKey, deletedRepositoryConfigs.iterator().next());

            // referencing virtual repo was updated (defaultDeploymentRepoRef was removed)
            Set<DbRepositoryConfig> updatedRepoConfigs = update.getAllValues().get(0);

            assertEquals(1, updatedRepoConfigs.size());
            assertEquals(VIRTUAL.typeNameLowercase(), updatedRepoConfigs.iterator().next().getType());
            VirtualRepoConfig updatedVirtualRepo =
                    (VirtualRepoConfig) repositoryConfigMapper.dbInfoBlobToRepositoryConfig(
                            updatedRepoConfigs.iterator().next());
            // defaultDeploymentRepoRef should be removed when the referenced local repo is deleted
            assertNull(updatedVirtualRepo.getRepoTypeConfig().getDefaultDeploymentRepoRef());
            // repositoryRefs are removed when a referenced repo is deleted (unless the virtual is federated)
            assertFalse(updatedVirtualRepo.getRepoTypeConfig().getRepositoryRefs().contains(repoKey));

            // config descriptor references were deleted
            assertConfigDescriptorRefsWereRemoved(repoKey, configDescriptor);
        } finally {
            ArtifactoryContextThreadBinder.unbind();
        }
    }

    private enum ClearVirtualRefsScenario {
        LOCAL_DEFAULT_MATCH(true),
        REMOTE_DEFAULT_NO_CLEAR(false),
        REMOTE_EXTERNAL_DEPS_MATCH(true),
        NO_REFS_MATCH(false);

        final boolean expectedChanged;

        ClearVirtualRefsScenario(boolean expectedChanged) {
            this.expectedChanged = expectedChanged;
        }
    }

    @ParameterizedTest(name = "clearVirtualRepoRefsForRemovedChild: {0}")
    @MethodSource("clearVirtualRefsScenarios")
    void clearVirtualRepoRefsForRemovedChild_handlesAllScenarios(ClearVirtualRefsScenario scenario) {
        RepositoryConfigService service = createService();
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("virtual", Generic);
        RepoConfig removedRepo;
        switch (scenario) {
            case LOCAL_DEFAULT_MATCH -> {
                virtualRepoConfig.getRepoTypeConfig().setDefaultDeploymentRepoRef("local-repo");
                removedRepo = LocalRepoConfig.create("local-repo", Generic);
            }
            case REMOTE_DEFAULT_NO_CLEAR -> {
                virtualRepoConfig.getRepoTypeConfig().setDefaultDeploymentRepoRef("some-local");
                removedRepo = RemoteRepoConfig.create("some-local", Generic);
            }
            case REMOTE_EXTERNAL_DEPS_MATCH -> {
                virtualRepoConfig.getPackageTypeConfig().put(
                        ExternalDependenciesPackageTypeConfigMapper.EXTERNAL_DEPENDENCIES_ENABLED, "true");
                virtualRepoConfig.getPackageTypeConfig().put(
                        ExternalDependenciesPackageTypeConfigMapper.EXTERNAL_DEPENDENCIES_REMOTE_REPO_REF,
                        "remote-repo");
                virtualRepoConfig.getPackageTypeConfig().put(
                        ExternalDependenciesPackageTypeConfigMapper.EXTERNAL_DEPENDENCIES_PATTERNS, "[]");
                removedRepo = RemoteRepoConfig.create("remote-repo", Generic);
            }
            case NO_REFS_MATCH -> {
                virtualRepoConfig.getRepoTypeConfig().setDefaultDeploymentRepoRef("other-local");
                removedRepo = LocalRepoConfig.create("deleted-repo", Generic);
            }
            default -> throw new IllegalArgumentException(String.valueOf(scenario));
        }

        boolean changed = service.clearVirtualRepoRefsForRemovedChild(virtualRepoConfig, removedRepo);

        assertEquals(scenario.expectedChanged, changed);
        switch (scenario) {
            case LOCAL_DEFAULT_MATCH -> assertNull(
                    virtualRepoConfig.getRepoTypeConfig().getDefaultDeploymentRepoRef());
            case REMOTE_DEFAULT_NO_CLEAR -> assertEquals("some-local",
                    virtualRepoConfig.getRepoTypeConfig().getDefaultDeploymentRepoRef());
            case REMOTE_EXTERNAL_DEPS_MATCH -> {
                assertNull(virtualRepoConfig.getPackageTypeConfig()
                        .get(ExternalDependenciesPackageTypeConfigMapper.EXTERNAL_DEPENDENCIES_REMOTE_REPO_REF));
                assertNull(virtualRepoConfig.getPackageTypeConfig()
                        .get(ExternalDependenciesPackageTypeConfigMapper.EXTERNAL_DEPENDENCIES_ENABLED));
                assertNull(virtualRepoConfig.getPackageTypeConfig()
                        .get(ExternalDependenciesPackageTypeConfigMapper.EXTERNAL_DEPENDENCIES_PATTERNS));
            }
            case NO_REFS_MATCH -> assertEquals("other-local",
                    virtualRepoConfig.getRepoTypeConfig().getDefaultDeploymentRepoRef());
            default -> { }
        }
    }

    static Stream<Arguments> clearVirtualRefsScenarios() {
        return Stream.of(ClearVirtualRefsScenario.values()).map(Arguments::of);
    }

    @Test
    void deleteRepoConfig_shouldNotUnlinkChildFromFederatedVirtual_whenChildRepoIsDeletedLocally() {
        try {
            // Arrange
            SecurityService securityService = mock(SecurityService.class);
            when(securityService.isAnyProjectAdmin()).thenReturn(true);
            when(context.beanForType(SecurityService.class)).thenReturn(securityService);

            String repoKey = "repoKey";
            LocalRepoConfig oldRepoConfig = LocalRepoConfig.create(repoKey, Generic);
            DbRepositoryConfig oldRepoDbConfig =
                    repositoryConfigMapper.repositoryConfigToDbRepositoryConfig(oldRepoConfig);

            String virtualRepoKey = "virtual-repo";
            VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create(virtualRepoKey, Generic);
            virtualRepoConfig.getRepoTypeConfig().getRepositoryRefs().add(repoKey);
            FederatedMember member = new FederatedMember();
            member.setUrlOrTemplate("https://member-1/artifactory/" + virtualRepoKey);
            virtualRepoConfig.getBaseConfig().getFederationConfig().setMembers(List.of(member));
            DbRepositoryConfig virtualDbRepositoryConfig =
                    repositoryConfigMapper.repositoryConfigToDbRepositoryConfig(virtualRepoConfig);

            DbRepositoryConfig localRepo = new DbRepositoryConfig();
            localRepo.setKey(repoKey);
            localRepo.setPackageType(Gradle.toString());
            localRepo.setType(LOCAL.typeNameLowercase());

            DbRepositoryConfig virtualRepo = new DbRepositoryConfig();
            virtualRepo.setKey(virtualRepoKey);
            virtualRepo.setPackageType(Gradle.toString());
            virtualRepo.setType(VIRTUAL.typeNameLowercase());

            when(storageService.getAllBasicRepoConfigs()).thenReturn(Set.of(localRepo, virtualRepo));
            when(storageService.getAllRepositories()).thenReturn(List.of(oldRepoDbConfig, virtualDbRepositoryConfig));

            CentralConfigDescriptorImpl configDescriptor = new CentralConfigDescriptorImpl();
            populateConfigDescriptorWithRefsToRepo(repoKey, configDescriptor);
            when(configService.getMutableDescriptor()).thenReturn(configDescriptor);

            RepositoryConfigServiceImpl repositoryConfigService = createService(storageService);
            repositoryConfigService.setConfigValidationService(new ConfigValidationService());
            repositoryConfigService.setFederatedRepoService(mock(FederatedRepoService.class));
            ArtifactoryHome.get().getArtifactoryProperties()
                    .setProperty(ConstantValues.virtualRepoFederationEnabled.getPropertyName(), "true");

            // Act
            repositoryConfigService.deleteRepoConfig(repoKey);

            // Assert
            ArgumentCaptor<Set<DbRepositoryConfig>> insert = ArgumentCaptor.forClass(Set.class);
            ArgumentCaptor<Set<DbRepositoryConfig>> update = ArgumentCaptor.forClass(Set.class);
            ArgumentCaptor<Set<String>> delete = ArgumentCaptor.forClass(Set.class);
            verify(storageService, times(1)).flushBulkChanges(
                    insert.capture(), update.capture(), delete.capture(),
                    any(), any());

            assertTrue(update.getValue().isEmpty());

            Set<String> deletedRepositoryConfigs = delete.getValue();
            assertEquals(1, deletedRepositoryConfigs.size());
            assertEquals(repoKey, deletedRepositoryConfigs.iterator().next());
        } finally {
            ArtifactoryContextThreadBinder.unbind();
        }
    }

    @Test
    void deleteRepoConfig_shouldNotScheduleFederatedCleanup_whenFederatedVirtualDoesNotReferenceRemovedRepo() {
        try {
            SecurityService securityService = mock(SecurityService.class);
            when(securityService.isAnyProjectAdmin()).thenReturn(true);
            when(context.beanForType(SecurityService.class)).thenReturn(securityService);

            String deletedRepoKey = "deleted-repo";
            LocalRepoConfig deletedRepoConfig = LocalRepoConfig.create(deletedRepoKey, Generic);
            DbRepositoryConfig deletedRepoDbConfig =
                    repositoryConfigMapper.repositoryConfigToDbRepositoryConfig(deletedRepoConfig);

            String virtualRepoKey = "virtual-repo";
            VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create(virtualRepoKey, Generic);
            virtualRepoConfig.getRepoTypeConfig().getRepositoryRefs().add("other-repo");
            virtualRepoConfig.getRepoTypeConfig().setDefaultDeploymentRepoRef("other-repo");
            FederatedMember member = new FederatedMember();
            member.setUrlOrTemplate("https://member-1/artifactory/" + virtualRepoKey);
            virtualRepoConfig.getBaseConfig().getFederationConfig().setMembers(List.of(member));
            DbRepositoryConfig virtualDbRepositoryConfig =
                    repositoryConfigMapper.repositoryConfigToDbRepositoryConfig(virtualRepoConfig);

            DbRepositoryConfig localRepo = new DbRepositoryConfig();
            localRepo.setKey(deletedRepoKey);
            localRepo.setPackageType(Gradle.toString());
            localRepo.setType(LOCAL.typeNameLowercase());

            DbRepositoryConfig virtualRepo = new DbRepositoryConfig();
            virtualRepo.setKey(virtualRepoKey);
            virtualRepo.setPackageType(Gradle.toString());
            virtualRepo.setType(VIRTUAL.typeNameLowercase());

            when(storageService.getAllBasicRepoConfigs()).thenReturn(Set.of(localRepo, virtualRepo));
            when(storageService.getAllRepositories()).thenReturn(List.of(deletedRepoDbConfig,
                    virtualDbRepositoryConfig));

            CentralConfigDescriptorImpl configDescriptor = new CentralConfigDescriptorImpl();
            populateConfigDescriptorWithRefsToRepo(deletedRepoKey, configDescriptor);
            when(configService.getMutableDescriptor()).thenReturn(configDescriptor);

            FederatedRepoService federatedRepoService = mock(FederatedRepoService.class);
            RepositoryConfigServiceImpl repositoryConfigService = createService(storageService);
            repositoryConfigService.setConfigValidationService(new ConfigValidationService());
            repositoryConfigService.setFederatedRepoService(federatedRepoService);
            ArtifactoryHome.get().getArtifactoryProperties()
                    .setProperty(ConstantValues.virtualRepoFederationEnabled.getPropertyName(), "true");

            repositoryConfigService.deleteRepoConfig(deletedRepoKey);

            verify(federatedRepoService, never()).cleanupDanglingRefAsync(anyString(), any());
        } finally {
            ArtifactoryContextThreadBinder.unbind();
        }
    }

    @Test
    void deleteRepoConfig_shouldNotUnlinkChildFromFederatedVirtual_whenVirtualFederationFlagIsDisabled() {
        try {
            SecurityService securityService = mock(SecurityService.class);
            when(securityService.isAnyProjectAdmin()).thenReturn(true);
            when(context.beanForType(SecurityService.class)).thenReturn(securityService);

            String deletedRepoKey = "deleted-repo";
            LocalRepoConfig deletedRepoConfig = LocalRepoConfig.create(deletedRepoKey, Generic);
            DbRepositoryConfig deletedRepoDbConfig =
                    repositoryConfigMapper.repositoryConfigToDbRepositoryConfig(deletedRepoConfig);

            String virtualRepoKey = "virtual-repo";
            VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create(virtualRepoKey, Generic);
            virtualRepoConfig.getRepoTypeConfig().getRepositoryRefs().add(deletedRepoKey);
            FederatedMember member = new FederatedMember();
            member.setUrlOrTemplate("https://member-1/artifactory/" + virtualRepoKey);
            virtualRepoConfig.getBaseConfig().getFederationConfig().setMembers(List.of(member));
            DbRepositoryConfig virtualDbRepositoryConfig =
                    repositoryConfigMapper.repositoryConfigToDbRepositoryConfig(virtualRepoConfig);

            DbRepositoryConfig localRepo = new DbRepositoryConfig();
            localRepo.setKey(deletedRepoKey);
            localRepo.setPackageType(Gradle.toString());
            localRepo.setType(LOCAL.typeNameLowercase());

            DbRepositoryConfig virtualRepo = new DbRepositoryConfig();
            virtualRepo.setKey(virtualRepoKey);
            virtualRepo.setPackageType(Gradle.toString());
            virtualRepo.setType(VIRTUAL.typeNameLowercase());

            when(storageService.getAllBasicRepoConfigs()).thenReturn(Set.of(localRepo, virtualRepo));
            when(storageService.getAllRepositories()).thenReturn(List.of(deletedRepoDbConfig,
                    virtualDbRepositoryConfig));

            CentralConfigDescriptorImpl configDescriptor = new CentralConfigDescriptorImpl();
            populateConfigDescriptorWithRefsToRepo(deletedRepoKey, configDescriptor);
            when(configService.getMutableDescriptor()).thenReturn(configDescriptor);

            FederatedRepoService federatedRepoService = mock(FederatedRepoService.class);
            RepositoryConfigServiceImpl repositoryConfigService = createService(storageService);
            repositoryConfigService.setConfigValidationService(new ConfigValidationService());
            repositoryConfigService.setFederatedRepoService(federatedRepoService);
            ArtifactoryHome.get().getArtifactoryProperties()
                    .setProperty(ConstantValues.virtualRepoFederationEnabled.getPropertyName(), "false");

            repositoryConfigService.deleteRepoConfig(deletedRepoKey);

            verify(federatedRepoService, never()).cleanupDanglingRefAsync(anyString(), any());
            ArgumentCaptor<Set<DbRepositoryConfig>> update = ArgumentCaptor.forClass(Set.class);
            verify(storageService, times(1)).flushBulkChanges(any(), update.capture(), any(), any(), any());
            assertTrue(update.getValue().isEmpty());
        } finally {
            ArtifactoryContextThreadBinder.unbind();
        }
    }

    @Test
    public void testDereferenceRepositorySavesOnlyModifiedDescriptor() {
        CentralConfigDescriptorImpl configDescriptor = new CentralConfigDescriptorImpl();
        when(configService.getMutableDescriptor()).thenReturn(configDescriptor);

        RepositoryConfigServiceImpl repositoryConfigService =
                createService(mock(RepositoryConfigStorageService.class));
        repositoryConfigService.dereferenceRepositoryFromConfigDescriptor(
                List.of(LocalRepoConfig.create("local", Generic)));

        verify(configService, times(0)).saveEditedDescriptorAndReload(any());
    }

    private void populateConfigDescriptorWithRefsToRepo(String repoKey, CentralConfigDescriptorImpl configDescriptor) {
        BackupDescriptor backupDescriptor = new BackupDescriptor();
        backupDescriptor.setKey("test");
        backupDescriptor.addExcludedRepository(repoKey);
        configDescriptor.addBackup(backupDescriptor);

        IndexerDescriptor indexer = new IndexerDescriptor();
        SortedSet<String> includedRepositories = new TreeSet<>();
        includedRepositories.add(repoKey);
        indexer.setIncludedRepositories(includedRepositories);
        configDescriptor.setIndexer(indexer);

        RemoteReplicationDescriptor remoteReplication = new RemoteReplicationDescriptor();
        remoteReplication.setReplicationKey("test");
        remoteReplication.setRepoKey(repoKey);
        configDescriptor.addRemoteReplication(remoteReplication);

        LocalReplicationDescriptor localReplication = new LocalReplicationDescriptor();
        localReplication.setReplicationKey("test");
        localReplication.setRepoKey(repoKey);
        configDescriptor.addLocalReplication(localReplication);

        ReverseProxyDescriptor reverseProxy = new ReverseProxyDescriptor();
        reverseProxy.setKey(repoKey);
        configDescriptor.addReverseProxy(reverseProxy);
    }

    private void assertConfigDescriptorRefsWereRemoved(String repoKey, CentralConfigDescriptorImpl configDescriptor) {
        assertFalse(configDescriptor.getBackup("test").getExcludedRepositories().contains(repoKey));
        assertNotNull(configDescriptor.getIndexer().getIncludedRepositories());
        assertFalse(configDescriptor.getIndexer().getIncludedRepositories().contains(repoKey));
        assertNull(configDescriptor.getRemoteReplication("test"));
        assertNull(configDescriptor.getLocalReplication("test"));
        assertNull(configDescriptor.getReverseProxy("test"));
    }

    @Test
    void testCannotDeleteRepositoryThatDoesNotExist() {
        try {
            // given
            SecurityService securityService = mock(SecurityService.class);
            when(securityService.isAnyProjectAdmin()).thenReturn(true);
            when(context.beanForType(SecurityService.class)).thenReturn(securityService);

            when(storageService.findRepositoryByKey("repoKey")).thenReturn(null);

            // when
            RepositoryConfigServiceImpl repositoryConfigService =
                    createService();
            repositoryConfigService.setConfigValidationService(new ConfigValidationService());
            assertNull(repositoryConfigService.deleteRepoConfig("repoKey"));
        } finally {
            ArtifactoryContextThreadBinder.unbind();
        }
    }

    @Test
    void testUpsertNewRepositoryConfigByKey() {
        try {
            // given
            LocalRepoConfig repositoryConfig = LocalRepoConfig.create("repoKey", Generic);

            // when
            SecurityService securityService = mock(SecurityService.class);
            when(securityService.isAnyProjectAdmin()).thenReturn(true);
            RepositoryConfigServiceImpl repositoryConfigService =
                    createService();
            repositoryConfigService.setConfigValidationService(new ConfigValidationService());
            repositoryConfigService.upsertRepoConfig(repositoryConfig);

            // then
            ArgumentCaptor<Set<DbRepositoryConfig>> insert = ArgumentCaptor.forClass(Set.class);
            ArgumentCaptor<Set<DbRepositoryConfig>> update = ArgumentCaptor.forClass(Set.class);
            ArgumentCaptor<Set<String>> delete = ArgumentCaptor.forClass(Set.class);

            verify(storageService).flushBulkChanges(insert.capture(), update.capture(), delete.capture(),
                    any(), any());

            Set<DbRepositoryConfig> insertedDbRepositoryConfigs = insert.getValue();
            assertEquals(1, insertedDbRepositoryConfigs.size());
            RepoConfig insertedRepositoryConfig =
                    repositoryConfigMapper.dbInfoBlobToRepositoryConfig(insertedDbRepositoryConfigs.iterator().next());

            assertTrue(update.getValue().isEmpty());
            assertTrue(delete.getValue().isEmpty());
            assertEquals(insertedRepositoryConfig.getKey(), repositoryConfig.getKey());
        } finally {
            ArtifactoryContextThreadBinder.unbind();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testUpsertWithCuration(boolean hasException) {
        try {
            // given
            RemoteRepoConfig repositoryConfig = RemoteRepoConfig.create("repoKey", Generic);
            repositoryConfig.getRepoTypeConfig().setUrl("url");
            SecurityService securityService = mock(SecurityService.class);
            when(securityService.isAnyProjectAdmin()).thenReturn(true);
            // when
            RepositoryConfigServiceImpl repositoryConfigService =
                    createService();
            repositoryConfigService.setConfigValidationService(new ConfigValidationService());
            when(curationAddon.isCurationConfigValid(repositoryConfig)).thenReturn(!hasException);
            if (hasException) {
                assertThrows(IllegalArgumentException.class,
                        () -> repositoryConfigService.upsertRepoConfig(repositoryConfig));
            } else {
                repositoryConfigService.setConfigValidationService(new ConfigValidationService());
                repositoryConfigService.upsertRepoConfig(repositoryConfig);
                ArgumentCaptor<Set<DbRepositoryConfig>> insert = ArgumentCaptor.forClass(Set.class);
                ArgumentCaptor<Set<DbRepositoryConfig>> update = ArgumentCaptor.forClass(Set.class);
                ArgumentCaptor<Set<String>> delete = ArgumentCaptor.forClass(Set.class);
                verify(storageService).flushBulkChanges(insert.capture(), update.capture(), delete.capture(),
                        any(), any());
            }
        } finally {
            ArtifactoryContextThreadBinder.unbind();
        }
    }

    @Test
    void testUpsertExistingRepositoryConfigByKey() {
        try {
            // given
            SecurityService securityService = mock(SecurityService.class);
            when(securityService.isAnyProjectAdmin()).thenReturn(true);
            when(context.beanForType(SecurityService.class)).thenReturn(securityService);

            LocalRepoConfig oldRepoConfig = LocalRepoConfig.create("repoKey", Generic);
            DbRepositoryConfig localRepo = new DbRepositoryConfig();
            localRepo.setKey("repoKey");
            localRepo.setPackageType(Gradle.toString());
            localRepo.setType(LOCAL.typeNameLowercase());
            when(storageService.getAllBasicRepoConfigs()).thenReturn(Set.of(localRepo));
            when(storageService.getAllRepositories()).thenReturn(
                    List.of(repositoryConfigMapper.repositoryConfigToDbRepositoryConfig(oldRepoConfig)));

            LocalRepoConfig newRepoConfig = LocalRepoConfig.create("repoKey", Generic);
            newRepoConfig.getBaseConfig().setDescription("description-y description");

            // when
            RepositoryConfigServiceImpl repositoryConfigService =
                    createService();
            repositoryConfigService.setConfigValidationService(new ConfigValidationService());
            repositoryConfigService.upsertRepoConfig(newRepoConfig);

            ArgumentCaptor<Set<DbRepositoryConfig>> insert = ArgumentCaptor.forClass(Set.class);
            ArgumentCaptor<Set<DbRepositoryConfig>> update = ArgumentCaptor.forClass(Set.class);
            ArgumentCaptor<Set<String>> delete = ArgumentCaptor.forClass(Set.class);
            verify(storageService).flushBulkChanges(insert.capture(), update.capture(), delete.capture(),
                    any(), any());

            Set<DbRepositoryConfig> updatedRepoConfigs = update.getValue();
            assertEquals(1, updatedRepoConfigs.size());
            RepoConfig updatedRepoConfig = repositoryConfigMapper.dbInfoBlobToRepositoryConfig(
                    updatedRepoConfigs.stream().findFirst().orElse(new DbRepositoryConfig()));

            assertEquals(newRepoConfig, updatedRepoConfig);

            assertTrue(insert.getValue().isEmpty());
            assertTrue(delete.getValue().isEmpty());
        } finally {
            ArtifactoryContextThreadBinder.unbind();
        }
    }

    /** Upserts {@code newRepoConfig} over a stored Generic local repo of plugin package type "simple". */
    private RepoConfig upsertOverPluginTypedRepo(LocalRepoConfig newRepoConfig) {
        SecurityService securityService = mock(SecurityService.class);
        when(securityService.isAnyProjectAdmin()).thenReturn(true);
        when(context.beanForType(SecurityService.class)).thenReturn(securityService);
        LocalRepoConfig oldRepoConfig = LocalRepoConfig.create("repoKey", Generic);
        oldRepoConfig.getPackageTypeConfig().put(PluginPackageTypes.CONFIG_KEY, "simple");
        DbRepositoryConfig localRepo = new DbRepositoryConfig();
        localRepo.setKey("repoKey");
        localRepo.setPackageType(Generic.toString());
        localRepo.setType(LOCAL.typeNameLowercase());
        when(storageService.getAllBasicRepoConfigs()).thenReturn(Set.of(localRepo));
        when(storageService.getAllRepositories()).thenReturn(
                List.of(repositoryConfigMapper.repositoryConfigToDbRepositoryConfig(oldRepoConfig)));

        RepositoryConfigServiceImpl repositoryConfigService = createService();
        repositoryConfigService.setConfigValidationService(new ConfigValidationService());
        repositoryConfigService.upsertRepoConfig(newRepoConfig);

        ArgumentCaptor<Set<DbRepositoryConfig>> update = ArgumentCaptor.forClass(Set.class);
        verify(storageService).flushBulkChanges(any(), update.capture(), any(), any(), any());
        assertEquals(1, update.getValue().size());
        return repositoryConfigMapper.dbInfoBlobToRepositoryConfig(update.getValue().iterator().next());
    }

    @Test
    void upsertWithoutPluginPackageTypeKeepsStoredType() {
        // A configuration rebuilt from the UI model has no pluginPackageType entry.
        LocalRepoConfig newRepoConfig = LocalRepoConfig.create("repoKey", Generic);
        newRepoConfig.getBaseConfig().setDescription("edited in the UI");
        RepoConfig saved = upsertOverPluginTypedRepo(newRepoConfig);
        assertEquals("simple", saved.getPackageTypeConfig().get(PluginPackageTypes.CONFIG_KEY));
        assertEquals("edited in the UI", saved.getBaseConfig().getDescription());
    }

    @Test
    void upsertChangingPluginPackageTypeIsHonoured() {
        LocalRepoConfig changed = LocalRepoConfig.create("repoKey", Generic);
        changed.getPackageTypeConfig().put(PluginPackageTypes.CONFIG_KEY, "other");
        assertEquals("other", upsertOverPluginTypedRepo(changed).getPackageTypeConfig()
                .get(PluginPackageTypes.CONFIG_KEY));
    }

    @Test
    void upsertClearingPluginPackageTypeRemovesIt() {
        LocalRepoConfig cleared = LocalRepoConfig.create("repoKey", Generic);
        cleared.getPackageTypeConfig().put(PluginPackageTypes.CONFIG_KEY, "");
        assertFalse(upsertOverPluginTypedRepo(cleared).getPackageTypeConfig()
                .containsKey(PluginPackageTypes.CONFIG_KEY));
    }

    @ParameterizedTest
    @MethodSource("providerAllRepoConfigTypesWithNonUnicodeKey")
    void testInsertRepoWithUnicodeKey(RepoConfig repositoryConfig) {
        // given
        SecurityService securityService = mock(SecurityService.class);
        when(securityService.isAnyProjectAdmin()).thenReturn(true);
        when(context.beanForType(SecurityService.class)).thenReturn(securityService);

        // when
        RepositoryConfigServiceImpl repositoryConfigService = createService();
        repositoryConfigService.setConfigValidationService(new ConfigValidationService());
        assertThrows(IllegalStateException.class, () -> repositoryConfigService.insertRepoConfig(repositoryConfig),
                ".* Unicode is not supported");
    }

    @ParameterizedTest
    @MethodSource("provideRealRepoTypeConfigs")
    void testUpsertRepoWithUnicodePropertySet(RealRepoConfig realRepoConfig) {
        // given
        realRepoConfig.getRepoTypeConfig().setPropertySetRefs(List.of("\u0000"));
        SecurityService securityService = mock(SecurityService.class);
        when(securityService.isAnyProjectAdmin()).thenReturn(true);
        when(context.beanForType(SecurityService.class)).thenReturn(securityService);

        // when
        RepositoryConfigServiceImpl repositoryConfigService = createService();
        repositoryConfigService.setConfigValidationService(new ConfigValidationService());
        List<RealRepoConfig> repositoryConfigs = List.of(realRepoConfig);
        assertThrows(IllegalStateException.class,
                () -> repositoryConfigService.bulkInsertRepoConfigs(repositoryConfigs, false, true, false),
                ".* Unicode is not supported");
    }

    @Test
    void testInsertReposWithReload() {
        // given
        SecurityService securityService = mock(SecurityService.class);
        when(securityService.isAnyProjectAdmin()).thenReturn(true);
        when(context.beanForType(SecurityService.class)).thenReturn(securityService);
        InternalRepositoryService repositoryService = mock(InternalRepositoryService.class);
        when(context.beanForType(InternalRepositoryService.class)).thenReturn(repositoryService);

        // when
        RepositoryConfigServiceImpl repositoryConfigService = createService();
        repositoryConfigService.setConfigValidationService(new ConfigValidationService());
        List<RealRepoConfig> repositoryConfigs = List.of(LocalRepoConfig.create("repo-key", Generic));
        repositoryConfigService.bulkInsertRepoConfigs(repositoryConfigs, true, false, false);

        // then
        verify(repositoryService).onRepoConfigReload(any(RepoConfigChangesReload.class));
    }

    @Test
    void testUpsertReposWithReload() {
        // given
        SecurityService securityService = mock(SecurityService.class);
        when(securityService.isAnyProjectAdmin()).thenReturn(true);
        when(context.beanForType(SecurityService.class)).thenReturn(securityService);
        InternalRepositoryService repositoryService = mock(InternalRepositoryService.class);
        when(context.beanForType(InternalRepositoryService.class)).thenReturn(repositoryService);

        // when
        RepositoryConfigServiceImpl repositoryConfigService = createService();
        repositoryConfigService.setConfigValidationService(new ConfigValidationService());
        List<RealRepoConfig> repositoryConfigs = List.of(LocalRepoConfig.create("repo-key", Generic));
        repositoryConfigService.bulkUpsertRepoConfigs(repositoryConfigs, true);

        // then
        verify(repositoryService).onRepoConfigReload(any(RepoConfigChangesReload.class));
    }

    @Test
    void testGetPackageTypeConfigAsMap() {
        // given
        LocalRepoConfig repositoryConfig = LocalRepoConfig.create("repoKey", Maven);

        repositoryConfig.getPackageTypeConfig()
                .put(POM_REPOSITORY_REFERENCES_CLEANUP_POLICY.getKey(), "discard_any_reference");
        repositoryConfig.getPackageTypeConfig().put(FETCH_JARS_EAGERLY.getKey(), Boolean.TRUE.toString());
        DbRepositoryConfig dbRepositoryConfig =
                repositoryConfigMapper.repositoryConfigToDbRepositoryConfig(repositoryConfig);
        when(storageService.getAllRepositories()).thenReturn(List.of(dbRepositoryConfig));

        // when
        RepositoryConfigServiceImpl repositoryConfigService = createService();
        Map<String, String> retrievedMavenConfig = repositoryConfigService.getPackageConfigByRepoKey("repoKey");

        // then
        assertNotNull(retrievedMavenConfig);
        assertEquals(retrievedMavenConfig.get(POM_REPOSITORY_REFERENCES_CLEANUP_POLICY.getKey()),
                PomCleanupPolicy.discard_any_reference.getType());
    }

    @Disabled
    @Test
    void testGetPackageConfigWithNonTrivialPackageConfig() {
        // given
        LocalRepoConfig repositoryConfig = LocalRepoConfig.create("my-repo", Maven);

        repositoryConfig.getPackageTypeConfig().put(EXTERNAL_DEPENDENCIES_ENABLED.getKey(), Boolean.TRUE.toString());
        DbRepositoryConfig dbRepositoryConfig =
                repositoryConfigMapper.repositoryConfigToDbRepositoryConfig(repositoryConfig);
        when(storageService.findRepositoryByKey("repoKey")).thenReturn(dbRepositoryConfig);

        // when
        RepositoryConfigServiceImpl repositoryConfigService = createService();
        NpmConfiguration retrievedMavenConfig =
                repositoryConfigService.getPackageConfigByRepoKey("repoKey", NpmConfiguration.class);

        // then
        assertNotNull(retrievedMavenConfig);
    }

    @Test
    void testGetPackageConfig() {
        // given
        LocalRepoConfig repositoryConfig = LocalRepoConfig.create("repoKey", Maven);

        repositoryConfig.getPackageTypeConfig()
                .put(POM_REPOSITORY_REFERENCES_CLEANUP_POLICY.getKey(), "discard_any_reference");
        repositoryConfig.getPackageTypeConfig().put(FETCH_JARS_EAGERLY.getKey(), Boolean.TRUE.toString());

        DbRepositoryConfig dbRepositoryConfig =
                repositoryConfigMapper.repositoryConfigToDbRepositoryConfig(repositoryConfig);
        when(storageService.getAllRepositories()).thenReturn(List.of(dbRepositoryConfig));

        // when
        RepositoryConfigServiceImpl repositoryConfigService = createService();
        MavenConfiguration retrievedMavenConfig =
                repositoryConfigService.getPackageConfigByRepoKey("repoKey", MavenConfiguration.class);

        // then
        assertNotNull(retrievedMavenConfig);
        assertEquals(PomCleanupPolicy.discard_any_reference,
                retrievedMavenConfig.getPomRepositoryReferencesCleanupPolicy());
    }

    @Test
    void testInsertPackageConfigAsMap() {
        try {
            // given
            LocalRepoConfig repositoryConfig = LocalRepoConfig.create("repoKey", Generic);
            DbRepositoryConfig dbRepositoryConfig = getDbRepositoryConfig(repositoryConfig);
            when(storageService.getAllRepositories()).thenReturn(List.of(dbRepositoryConfig));
            SecurityService securityService = mock(SecurityService.class);
            when(securityService.isAnyProjectAdmin()).thenReturn(true);
            when(context.beanForType(SecurityService.class)).thenReturn(securityService);

            // when
            RepositoryConfigServiceImpl repositoryConfigService =
                    createService();
            repositoryConfigService.setConfigValidationService(new ConfigValidationService());
            repositoryConfigService.insertPackageConfigByKey("repoKey", Map.of("foo", "bar"));

            // then
            ArgumentCaptor<Set<DbRepositoryConfig>> insert = ArgumentCaptor.forClass(Set.class);
            ArgumentCaptor<Set<DbRepositoryConfig>> update = ArgumentCaptor.forClass(Set.class);
            ArgumentCaptor<Set<String>> delete = ArgumentCaptor.forClass(Set.class);
            verify(storageService).flushBulkChanges(insert.capture(), update.capture(), delete.capture(),
                    any(), any());

            Set<DbRepositoryConfig> updatedRepoConfigs = update.getValue();
            assertEquals(1, updatedRepoConfigs.size());
            LocalRepoConfig insertedRepositoryConfig =
                    (LocalRepoConfig) repositoryConfigMapper.dbInfoBlobToRepositoryConfig(
                            updatedRepoConfigs.iterator().next());
            assertEquals(insertedRepositoryConfig.getPackageTypeConfig(), Map.of("foo", "bar"));

            insertedRepositoryConfig.getPackageTypeConfig()
                    .forEach((k, v) -> repositoryConfig.getPackageTypeConfig().put(k, v));
            assertEquals(insertedRepositoryConfig, repositoryConfig);
        } finally {
            ArtifactoryContextThreadBinder.unbind();
        }
    }

    @Test
    void testExists() {
        RepoConfig repoConfig = LocalRepoConfig.create("exists", Generic);

        when(storageService.getAllRepositories()).thenReturn(
                List.of(repositoryConfigMapper.repositoryConfigToDbRepositoryConfig(repoConfig)));

        RepositoryConfigServiceImpl repositoryConfigService = createService();
        boolean exists = repositoryConfigService.existsByRepoKey("exists");
        assertTrue(exists);
        exists = repositoryConfigService.existsByRepoKey("does-not-exist");
        assertFalse(exists);
    }

    @Test
    void testMutableChangesAreFlushed() {
        try {
            // given
            LocalRepoConfig localRepoConfigToCreate = LocalRepoConfig.create("create-me", Generic);

            // when
            SecurityService securityService = mock(SecurityService.class);
            when(securityService.isAnyProjectAdmin()).thenReturn(true);
            when(context.beanForType(SecurityService.class)).thenReturn(securityService);

            RepositoryConfigServiceImpl repositoryConfigService =
                    createService();
            RepoConfigChangeInterceptor mutatingInterceptor = mock(RepoConfigChangeInterceptor.class);
            when(context.beansForType(RepoConfigChangeInterceptor.class)).thenReturn(
                    Map.of("mutatingInterceptor", mutatingInterceptor));
            repositoryConfigService.init();

            Mockito.doAnswer(invocation -> {
                Object[] args = invocation.getArguments();
                RepoConfigChanges repoConfigChanges = (RepoConfigChanges) args[0];
                Map<RepoDetailsType, List<RepoConfigChange>> mutableRepoConfigChanges =
                        repoConfigChanges.getMappedByType();

                RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("delete-me", Generic);
                mutableRepoConfigChanges.put(REMOTE,
                        List.of(new RepoConfigChange("delete-me", REPO_DELETE,
                                remoteRepoConfig, remoteRepoConfig)));
                VirtualRepoConfig virtualRepoConfigToUpdate = VirtualRepoConfig.create("update-me", Generic);
                mutableRepoConfigChanges.put(VIRTUAL,
                        List.of(new RepoConfigChange("update-me", REPO_UPDATE,
                                virtualRepoConfigToUpdate, virtualRepoConfigToUpdate)));
                return null;
            }).when(mutatingInterceptor).onBeforeSave(any(RepoConfigChanges.class));

            repositoryConfigService.setConfigValidationService(new ConfigValidationService());
            repositoryConfigService.insertRepoConfig(localRepoConfigToCreate);

            ArgumentCaptor<Set<DbRepositoryConfig>> insert = ArgumentCaptor.forClass(Set.class);
            ArgumentCaptor<Set<DbRepositoryConfig>> update = ArgumentCaptor.forClass(Set.class);
            ArgumentCaptor<Set<String>> delete = ArgumentCaptor.forClass(Set.class);
            verify(storageService).flushBulkChanges(insert.capture(), update.capture(), delete.capture(),
                    any(), any());

            assertEquals(1, insert.getValue().size());
            assertEquals("create-me", insert.getValue().iterator().next().getKey());

            assertEquals(1, update.getValue().size());
            assertEquals("update-me", update.getValue().iterator().next().getKey());

            assertEquals(1, update.getValue().size());
            assertEquals("delete-me", delete.getValue().iterator().next());
        } finally {
            ArtifactoryContextThreadBinder.unbind();
        }
    }

    @Test
    void orderOfRepositoryRefs() {
        // given
        List<String> expected = List.of("3", "C", "1", "4");
        VirtualRepoConfig repositoryConfig = VirtualRepoConfig.create("repoKey", Generic);
        repositoryConfig.getRepoTypeConfig().setRepositoryRefs(expected);
        RepoConfigCache repoConfigCache = mock(RepoConfigCache.class);
        when(repoConfigCache.getByKey("repoKey")).thenReturn(repositoryConfig);
        RepositoryConfigServiceImpl repositoryConfigService = createService();
        repositoryConfigService.setCache(repoConfigCache);

        // when
        RepoConfig retrievedRepositoryConfig = repositoryConfigService.getRepoConfigByKey("repoKey");
        assertNotNull(retrievedRepositoryConfig);
        List<@NotUnicode String> actual =
                ((VirtualRepoConfig) retrievedRepositoryConfig).getRepoTypeConfig().getRepositoryRefs();

        // then
        assertEquals(actual, expected);
    }

    @Test
    void testGetVirtualReposContainingRepo() {
        // given
        String referencedRepoKey = "referenced-repo";
        LocalRepoConfig referencedRepo = LocalRepoConfig.create(referencedRepoKey, Generic);
        DbRepositoryConfig dbReferencedRepo =
                repositoryConfigMapper.repositoryConfigToDbRepositoryConfig(referencedRepo);
        String targetRepoKey = "target-repo";
        VirtualRepoConfig targetVirtualRepo = VirtualRepoConfig.create(targetRepoKey, Generic);
        targetVirtualRepo.getRepoTypeConfig().setRepositoryRefs(List.of(referencedRepo.getKey()));
        DbRepositoryConfig dbTargetVirtualRepo =
                repositoryConfigMapper.repositoryConfigToDbRepositoryConfig(targetVirtualRepo);
        VirtualRepoConfig someVirtualRepo = VirtualRepoConfig.create("repo-key", Generic);
        DbRepositoryConfig dbSomeVirtualRepo =
                repositoryConfigMapper.repositoryConfigToDbRepositoryConfig(someVirtualRepo);
        when(storageService.getAllRepositories()).thenReturn(
                List.of(dbSomeVirtualRepo, dbReferencedRepo, dbTargetVirtualRepo));
        // when
        RepositoryConfigServiceImpl repositoryConfigService = createService();
        List<VirtualRepoConfig> virtualReposContainingRepo =
                repositoryConfigService.getVirtualRepoConfigsContainingRepoConfig(referencedRepo, 1);

        // then
        assertEquals(1, virtualReposContainingRepo.size());
        assertEquals(targetRepoKey, virtualReposContainingRepo.get(0).getKey());
    }

    @Test
    void testUpdateRemoteRepositoryConfigByKey() {
        try {
            // given
            SecurityService securityService = mock(SecurityService.class);
            when(securityService.isAnyProjectAdmin()).thenReturn(true);
            when(context.beanForType(SecurityService.class)).thenReturn(securityService);

            MutableCentralConfigDescriptor configDescriptor = mock(MutableCentralConfigDescriptor.class);
            when(configService.getMutableDescriptor()).thenReturn(configDescriptor);

            RemoteRepoConfig oldRemoteRepoConfig = RemoteRepoConfig.create("repoKey", Generic);
            when(storageService.getAllRepositories()).thenReturn(
                    List.of(repositoryConfigMapper.repositoryConfigToDbRepositoryConfig(oldRemoteRepoConfig)));

            RemoteRepoConfig newRemoteRepoConfig = RemoteRepoConfig.create("repoKey", Generic);
            newRemoteRepoConfig.getBaseConfig().setDescription("description-y description");

            // when
            RepositoryConfigServiceImpl repositoryConfigService =
                    createService();
            repositoryConfigService.setConfigValidationService(new ConfigValidationService());
            repositoryConfigService.upsertRepoConfig(newRemoteRepoConfig);

            // then
            ArgumentCaptor<Set<DbRepositoryConfig>> insert = ArgumentCaptor.forClass(Set.class);
            ArgumentCaptor<Set<DbRepositoryConfig>> update = ArgumentCaptor.forClass(Set.class);
            ArgumentCaptor<Set<String>> delete = ArgumentCaptor.forClass(Set.class);
            verify(storageService).flushBulkChanges(insert.capture(), update.capture(), delete.capture(),
                    any(), any());

            Set<DbRepositoryConfig> updatedDbRepositoryConfigs = update.getValue();
            assertEquals(1, updatedDbRepositoryConfigs.size());
            RepoConfig updatedRepositoryConfig =
                    repositoryConfigMapper.dbInfoBlobToRepositoryConfig(updatedDbRepositoryConfigs.iterator().next());
            assertEquals(updatedRepositoryConfig.getBaseConfig().getDescription(),
                    newRemoteRepoConfig.getBaseConfig().getDescription());
            verify(configDescriptor, times(0)).conditionallyAddToBackups(any());
        } finally {
            ArtifactoryContextThreadBinder.unbind();
        }
    }

    @Test
    void testInsertRemoteRepositoryConfigByKey() {
        try {
            // given
            SecurityService securityService = mock(SecurityService.class);
            when(securityService.isAnyProjectAdmin()).thenReturn(true);
            MutableCentralConfigDescriptor configDescriptor = mock(MutableCentralConfigDescriptor.class);
            when(configService.getMutableDescriptor()).thenReturn(configDescriptor);

            RemoteRepoConfig oldRemoteRepoConfig = RemoteRepoConfig.create("repoKey", Generic);
            when(storageService.findRepositoryByKey("repoKey")).thenReturn(
                    repositoryConfigMapper.repositoryConfigToDbRepositoryConfig(oldRemoteRepoConfig));
            when(storageService.exists("repoKey")).thenReturn(false);

            RemoteRepoConfig newRemoteRepoConfig = RemoteRepoConfig.create("repoKey", Generic);
            newRemoteRepoConfig.getBaseConfig().setDescription("description-y description");

            // when
            RepositoryConfigServiceImpl repositoryConfigService =
                    createService();
            repositoryConfigService.setConfigValidationService(new ConfigValidationService());
            repositoryConfigService.upsertRepoConfig(newRemoteRepoConfig);

            // then
            ArgumentCaptor<Set<DbRepositoryConfig>> insert = ArgumentCaptor.forClass(Set.class);
            ArgumentCaptor<Set<DbRepositoryConfig>> update = ArgumentCaptor.forClass(Set.class);
            ArgumentCaptor<Set<String>> delete = ArgumentCaptor.forClass(Set.class);

            verify(storageService).flushBulkChanges(insert.capture(), update.capture(), delete.capture(),
                    any(), any());

            Set<DbRepositoryConfig> insertedDbRepositoryConfigs = insert.getValue();
            assertEquals(1, insertedDbRepositoryConfigs.size());
            RepoConfig insertedRepositoryConfig =
                    repositoryConfigMapper.dbInfoBlobToRepositoryConfig(insertedDbRepositoryConfigs.iterator().next());
            assertEquals(insertedRepositoryConfig.getBaseConfig().getDescription(),
                    newRemoteRepoConfig.getBaseConfig().getDescription());

            assertTrue(update.getValue().isEmpty());
            assertTrue(delete.getValue().isEmpty());
            verify(configDescriptor).conditionallyAddToBackups("repoKey");
        } finally {
            ArtifactoryContextThreadBinder.unbind();
        }
    }

    @Test
    public void testWriteRepoClassToMetricsThreadLocal() {
        try {
            MetricsThreadLocal.bind(new MetricsMetadata());
            RepositoryConfigServiceImpl repositoryConfigService =
                    createService(mock(RepositoryConfigStorageService.class));

            repositoryConfigService.writeMetricsInfoToMetricsThreadLocal(Map.of());
            List<MetricsInfo> metricsInfos = MetricsThreadLocal.getMetricsInfoList();
            assertNotNull(metricsInfos);
            assertTrue(metricsInfos.isEmpty());

            RepoConfigChange createChange =
                    new RepoConfigChange("create-repo", REPO_CREATE,
                            LocalRepoConfig.create("create-repo", Generic), null);
            RepoConfigChange updateChange =
                    new RepoConfigChange("update-repo", REPO_UPDATE,
                            LocalRepoConfig.create("update-repo", Maven),
                            LocalRepoConfig.create("update-repo", Maven));
            RepoConfigChange deleteChange =
                    new RepoConfigChange("delete-repo", REPO_DELETE,
                            LocalRepoConfig.create("delete-repo", Docker),
                            LocalRepoConfig.create("delete-repo", Docker));

            repositoryConfigService.writeMetricsInfoToMetricsThreadLocal(
                    Map.of(LOCAL, List.of(createChange), REMOTE, List.of(createChange, updateChange), VIRTUAL,
                            List.of(createChange, updateChange, deleteChange)));

            metricsInfos = MetricsThreadLocal.getMetricsInfoList();
            assertNotNull(metricsInfos);
            assertEquals(6, metricsInfos.size());

            List<MetricsInfo> createMetrics =
                    metricsInfos.stream().filter(metricsInfo -> metricsInfo.getAction().equals("REPO_CREATED"))
                            .toList();
            assertEquals(3, createMetrics.size());
            List.of(LOCAL, REMOTE, VIRTUAL).forEach(repoDetailsType -> assertEquals(1, createMetrics.stream()
                    .filter(metricsInfo -> metricsInfo.getRepoClass().equals(repoDetailsType.typeNameLowercase()))
                    .count()));
            createMetrics.forEach(metricsInfo -> assertEquals(Generic.getType(), metricsInfo.getRepoType()));

            List<MetricsInfo> updateMetrics =
                    metricsInfos.stream().filter(metricsInfo -> metricsInfo.getAction().equals("REPO_UPDATED"))
                            .toList();
            assertEquals(2, updateMetrics.size());
            List.of(REMOTE, VIRTUAL).forEach(repoDetailsType -> assertEquals(1, updateMetrics.stream()
                    .filter(metricsInfo -> metricsInfo.getRepoClass().equals(repoDetailsType.typeNameLowercase()))
                    .count()));
            updateMetrics.forEach(metricsInfo -> assertEquals(Maven.getType(), metricsInfo.getRepoType()));

            List<MetricsInfo> deleteMetrics =
                    metricsInfos.stream().filter(metricsInfo -> metricsInfo.getAction().equals("REPO_DELETED"))
                            .toList();
            assertEquals(1, deleteMetrics.size());
            assertEquals(VIRTUAL.typeNameLowercase(), deleteMetrics.get(0).getRepoClass());
            assertEquals(Docker.getType(), deleteMetrics.get(0).getRepoType());
        } finally {
            MetricsThreadLocal.unbind();
        }
    }

    @Disabled
    @Test
    void testConvert() {
        String convertMe =
                "{\n" + "  \"baseConfig\": {\n" + "    \"modelVersion\": 0,\n" + "    \"key\": \"my-repo\",\n" +
                        "    \"type\": \"LOCAL\",\n" + "    \"packageType\": \"generic\"\n" + "  }" + "}";
        DbRepositoryConfig dbRepositoryConfig = new DbRepositoryConfig();
        dbRepositoryConfig.setKey("my-repo");
        dbRepositoryConfig.setType("local");
        dbRepositoryConfig.setPackageType("generic");
        dbRepositoryConfig.setRepositoryInfoBlob(convertMe.getBytes());
        when(storageService.getAllRepositories()).thenReturn(List.of(dbRepositoryConfig));

        // when
        RepositoryConfigServiceImpl repositoryConfigService = new RepositoryConfigServiceImpl(storageService,
                Set.of(new PackageTypeConfigConverter(), new RepositoryConfigBaseConverter()), addonsManager);

        repositoryConfigService.convert(
                new CompoundVersionDetails(ArtifactoryServiceVersionProvider.v7140m001.get(), "build", 9),
                new CompoundVersionDetails(ArtifactoryServiceVersionProvider.v7140m001.get(), "build", 9));

        // then (dummy converters)
        ArgumentCaptor<Set<DbRepositoryConfig>> captor = ArgumentCaptor.forClass(Set.class);
        verify(storageService).bulkUpdateRepositoryConfigs(captor.capture(), any());

        Set<DbRepositoryConfig> convertedRepos = captor.getValue();
        assertEquals(1, convertedRepos.size());

        RepoConfig repositoryConfig = convertedRepos.stream()
                .map(convertedRepo -> repositoryConfigMapper.dbInfoBlobToRepositoryConfig(convertedRepo))
                .collect(toList()).get(0);
        assertEquals("default notes", repositoryConfig.getBaseConfig().getNotes());
    }

    @Test
    void testConvertSkipsBlobWithUnknownPackageTypeAndConvertsTheRest() {
        // A repo whose package type is unknown to this Artifactory version (for example, written by a newer
        // Artifactory version before a rollback) must be skipped and left unchanged, not coerced to Generic and
        // re-persisted, so its original type survives a later re-upgrade. The unknown type is detected from the DB
        // column.
        DbRepositoryConfig goodBlob = repositoryConfigMapper.repositoryConfigToDbRepositoryConfig(
                LocalRepoConfig.create("good-repo", Maven));

        DbRepositoryConfig futureBlob = repositoryConfigMapper.repositoryConfigToDbRepositoryConfig(
                LocalRepoConfig.create("future-repo", Generic));
        // Mark the DB column with a package type unknown to this Artifactory version; convert() skips it by column.
        futureBlob.setPackageType("skillsfuture");

        when(storageService.getAllRepositories()).thenReturn(List.of(goodBlob, futureBlob));

        RepositoryConfigServiceImpl service =
                new RepositoryConfigServiceImpl(storageService, Set.of(), addonsManager);

        service.convert(
                new CompoundVersionDetails(ArtifactoryServiceVersionProvider.v7140m001.get(), "build", 9),
                new CompoundVersionDetails(ArtifactoryServiceVersionProvider.v7140m001.get(), "build", 9));

        ArgumentCaptor<Set<DbRepositoryConfig>> captor = ArgumentCaptor.forClass(Set.class);
        verify(storageService).bulkUpdateRepositoryConfigs(captor.capture(), any());
        Set<String> keys = captor.getValue().stream().map(DbRepositoryConfig::getKey).collect(Collectors.toSet());
        assertTrue(keys.contains("good-repo"), "the known-type repo must be converted");
        assertFalse(keys.contains("future-repo"), "the unknown-type repo must be skipped, not coerced and persisted");
    }

    @Test
    void testConvertDoesNotSkipPackageTypeAlias() {
        // A package-type alias such as "rpm" (the YUM alias) is a KNOWN type and must be converted, not skipped.
        // The skip check recognizes aliases the same way the column is read elsewhere (RepoType.fromType), so a
        // legitimate repo whose column holds the alias is not mistaken for an unknown type after a rollback.
        DbRepositoryConfig aliasBlob = repositoryConfigMapper.repositoryConfigToDbRepositoryConfig(
                LocalRepoConfig.create("rpm-repo", RepoType.YUM));
        // The column carries the "rpm" alias rather than the canonical "yum"; it must still be treated as known.
        aliasBlob.setPackageType("rpm");

        when(storageService.getAllRepositories()).thenReturn(List.of(aliasBlob));

        RepositoryConfigServiceImpl service =
                new RepositoryConfigServiceImpl(storageService, Set.of(), addonsManager);

        service.convert(
                new CompoundVersionDetails(ArtifactoryServiceVersionProvider.v7140m001.get(), "build", 9),
                new CompoundVersionDetails(ArtifactoryServiceVersionProvider.v7140m001.get(), "build", 9));

        ArgumentCaptor<Set<DbRepositoryConfig>> captor = ArgumentCaptor.forClass(Set.class);
        verify(storageService).bulkUpdateRepositoryConfigs(captor.capture(), any());
        Set<String> keys = captor.getValue().stream().map(DbRepositoryConfig::getKey).collect(Collectors.toSet());
        assertTrue(keys.contains("rpm-repo"), "repo with the 'rpm' alias column must be converted, not skipped");
    }

    @Test
    void testConvertPropagatesOnCorruptBlobOfKnownType() {
        // A corrupt blob of a KNOWN package type is not skipped during conversion; the failure propagates,
        // preserving the converter's pre-existing fail-fast behavior. Only the unknown-package-type case
        // (detected from the DB column) is skipped.
        DbRepositoryConfig corruptBlob = repositoryConfigMapper.repositoryConfigToDbRepositoryConfig(
                LocalRepoConfig.create("corrupt-repo", Maven));
        corruptBlob.setRepositoryInfoBlob("{ not a valid repo config blob".getBytes(StandardCharsets.UTF_8));

        // Construct with an empty repo set so the eager cache load at construction succeeds, then expose the corrupt
        // blob to the conversion path only.
        when(storageService.getAllRepositories()).thenReturn(List.of());
        RepositoryConfigServiceImpl service =
                new RepositoryConfigServiceImpl(storageService, Set.of(), addonsManager);

        when(storageService.getAllRepositories()).thenReturn(List.of(corruptBlob));
        assertThrows(RuntimeException.class, () -> service.convert(
                new CompoundVersionDetails(ArtifactoryServiceVersionProvider.v7140m001.get(), "build", 9),
                new CompoundVersionDetails(ArtifactoryServiceVersionProvider.v7140m001.get(), "build", 9)));
        verify(storageService, never()).bulkUpdateRepositoryConfigs(any(), any());
    }

    @Test
    public void reloadRepoConfigDeleteTwoKeys() {
        RepoConfigPropagationModel repoConfigPropagationModel = new RepoConfigPropagationModel();
        Set<RepoConfigReload> repoConfigDeletes = new HashSet<>();
        RepoConfigReload repoConfigDelete1 = new RepoConfigReload("localKey1", LOCAL, Docker, REPO_DELETE, 0L);
        RepoConfigReload repoConfigDelete2 = new RepoConfigReload("localKey2", LOCAL, Docker, REPO_DELETE, 0L);
        repoConfigDeletes.add(repoConfigDelete1);
        repoConfigDeletes.add(repoConfigDelete2);
        repoConfigPropagationModel.setRepoConfigDelete(repoConfigDeletes);
        RepositoryConfigServiceImpl repositoryConfigService = createService();
        DbRepositoryConfig localRepo = new DbRepositoryConfig();
        localRepo.setKey("repoKey");
        localRepo.setPackageType(Gradle.toString());
        localRepo.setType(LOCAL.typeNameLowercase());
        when(storageService.getAllBasicRepoConfigs()).thenReturn(Set.of(localRepo));
        when(context.beanForType(RepositoryConfigService.class)).thenReturn(repositoryConfigService);

        RepoConfigCache repoConfigCache = mock(RepoConfigCache.class);
        when(repoConfigCache.getRepositoryConfigsByKeys(anySet())).thenReturn(ImmutableList.of());
        when(repoConfigCache.getRepositoryConfigsByKeys(Set.of("localKey1", "localKey2"))).thenReturn(
                ImmutableList.of(LocalRepoConfig.create("localKey1", Generic),
                        LocalRepoConfig.create("localKey2", Generic)));

        repositoryConfigService.setCache(repoConfigCache);
        repositoryConfigService.reloadRepoConfigs(repoConfigPropagationModel);

        ArgumentCaptor<RepoConfigChangesReload> repoConfigReload =
                ArgumentCaptor.forClass(RepoConfigChangesReload.class);
        verify(ContextHelper.get().beanForType(InternalRepositoryService.class))
                .onPropagatedRepoConfigReload(repoConfigReload.capture());
        List<RepoConfigChangeReload> repoConfigChangeReloads = repoConfigReload.getValue().filterBy(LOCAL);
        assertEquals(2, repoConfigChangeReloads.size());
        List<String> deletedRepoKeys =
                repoConfigChangeReloads.stream().map(RepoConfigChangeReload::getRepoKey).toList();
        assertTrue(deletedRepoKeys.contains("localKey1"));
        assertTrue(deletedRepoKeys.contains("localKey2"));

    }

    /**
     * - Delete change for: localKey1 is outdated (exists in DB)
     * - Delete change for: localKey2 is standard delete (does not exist in DB)
     */
    @Test
    public void reloadRepoConfigDeleteTwoKeysOneOutdated() {
        RepoConfigPropagationModel repoConfigPropagationModel = new RepoConfigPropagationModel();
        Set<RepoConfigReload> repoConfigDeletes = new HashSet<>();
        RepoConfigReload repoConfigDelete1 = new RepoConfigReload("localKey1", LOCAL, Docker, REPO_DELETE, 0L);
        RepoConfigReload repoConfigDelete2 = new RepoConfigReload("localKey2", LOCAL, Docker, REPO_DELETE, 0L);
        repoConfigDeletes.add(repoConfigDelete1);
        repoConfigDeletes.add(repoConfigDelete2);
        repoConfigPropagationModel.setRepoConfigDelete(repoConfigDeletes);
        when(storageService.findKeysFromSet(anySet())).thenReturn(Set.of("localKey1", "localKey3"));
        RepositoryConfigServiceImpl repositoryConfigService = mockRepoConfigService();

        RepoConfigCache repoConfigCache = mock(RepoConfigCache.class);
        when(repoConfigCache.getRepositoryConfigsByKeys(anySet())).thenReturn(ImmutableList.of());
        when(repoConfigCache.getRepositoryConfigsByKeys(Set.of("localKey2"))).thenReturn(
                ImmutableList.of(LocalRepoConfig.create("localKey2", Generic)));

        repositoryConfigService.setCache(repoConfigCache);
        repositoryConfigService.reloadRepoConfigs(repoConfigPropagationModel);

        ArgumentCaptor<RepoConfigChangesReload> argumentCaptor =
                ArgumentCaptor.forClass(RepoConfigChangesReload.class);
        verify(ContextHelper.get().beanForType(InternalRepositoryService.class))
                .onPropagatedRepoConfigReload(argumentCaptor.capture());
        RepoConfigChangesReload repoConfigChangesReload = argumentCaptor.getValue();
        assertEquals(1, repoConfigChangesReload.filterBy(LOCAL).size());
        assertEquals("localKey2", repoConfigChangesReload.filterBy(LOCAL).getFirst().getRepoKey());
    }

    /**
     * Standard create event - Exists in DB but not in cache
     */
    @Test
    public void reloadRepoConfigCreate() {
        String repoKey = "repoKey";
        DbRepositoryConfig dbRepoConfig = repositoryConfigMapper.repositoryConfigToDbRepositoryConfig(
                LocalRepoConfig.create(repoKey, Generic));
        mockStorageService(dbRepoConfig);

        RepoConfigCache configCache = mockRepoConfigCache(ImmutableSet.of());
        RepositoryConfigServiceImpl repositoryConfigService = mockRepoConfigService();

        RepoConfigReload repoCreate = new RepoConfigReload(repoKey, LOCAL, Generic, REPO_CREATE, 0L);
        RepoConfigPropagationModel propagationModel = createPropagationModel(Set.of(repoCreate), Set.of());

        repositoryConfigService.setCache(configCache);
        repositoryConfigService.reloadRepoConfigs(propagationModel);

        ArgumentCaptor<RepoConfigChangesReload> argumentCaptor =
                ArgumentCaptor.forClass(RepoConfigChangesReload.class);
        verify(ContextHelper.get().beanForType(InternalRepositoryService.class))
                .onPropagatedRepoConfigReload(argumentCaptor.capture());
        RepoConfigChangesReload repoConfigChangesReload = argumentCaptor.getValue();
        assertEquals(1, repoConfigChangesReload.getAll().size());
        assertEquals(repoKey, repoConfigChangesReload.filterBy(LOCAL, REPO_CREATE).getFirst().getRepoKey());
    }

    /**
     * Outdated create event - Exists in cache
     */
    @Test
    public void reloadRepoConfigCreateOutdated() {
        String repoKey = "repoKey";
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create(repoKey, Generic);

        RepoConfigCache configCache = mockRepoConfigCache(ImmutableSet.of(localRepoConfig));
        RepositoryConfigServiceImpl repositoryConfigService = mockRepoConfigService();

        RepoConfigReload repoCreate = new RepoConfigReload(repoKey, LOCAL, Generic, REPO_CREATE, 0L);
        RepoConfigPropagationModel propagationModel = createPropagationModel(Set.of(repoCreate), Set.of());

        repositoryConfigService.setCache(configCache);
        repositoryConfigService.reloadRepoConfigs(propagationModel);

        ArgumentCaptor<RepoConfigChangesReload> argumentCaptor =
                ArgumentCaptor.forClass(RepoConfigChangesReload.class);
        verify(ContextHelper.get().beanForType(InternalRepositoryService.class))
                .onPropagatedRepoConfigReload(argumentCaptor.capture());
        RepoConfigChangesReload repoConfigChangesReload = argumentCaptor.getValue();
        assertTrue(repoConfigChangesReload.getAll().isEmpty());
    }

    /**
     * Standard update event - old config exists in cache with different modification date compared to event, new
     * config exists in DB
     */
    @Test
    public void reloadRepoConfigUpdate() {
        String repoKey = "repoKey";
        long oldModification = 1L;
        long newModification = 2L;
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create(repoKey, Generic);
        localRepoConfig.setModification(oldModification);
        DbRepositoryConfig dbRepoConfig = repositoryConfigMapper.repositoryConfigToDbRepositoryConfig(
                localRepoConfig);
        dbRepoConfig.setModified(newModification);
        mockStorageService(dbRepoConfig);

        RepoConfigCache configCache = mockRepoConfigCache(ImmutableSet.of(localRepoConfig));
        RepositoryConfigServiceImpl repositoryConfigService = mockRepoConfigService();

        RepoConfigReload repoCreate = new RepoConfigReload(repoKey, LOCAL, Generic, REPO_UPDATE, newModification);
        RepoConfigPropagationModel propagationModel = createPropagationModel(Set.of(repoCreate), Set.of());

        repositoryConfigService.setCache(configCache);
        repositoryConfigService.reloadRepoConfigs(propagationModel);

        ArgumentCaptor<RepoConfigChangesReload> argumentCaptor =
                ArgumentCaptor.forClass(RepoConfigChangesReload.class);
        verify(ContextHelper.get().beanForType(InternalRepositoryService.class))
                .onPropagatedRepoConfigReload(argumentCaptor.capture());
        RepoConfigChangesReload repoConfigChangesReload = argumentCaptor.getValue();

        assertEquals(1, repoConfigChangesReload.getAll().size());
        RepoConfigChangeReload configChangeReload = repoConfigChangesReload.filterBy(LOCAL, REPO_UPDATE).getFirst();
        assertEquals(repoKey, configChangeReload.getRepoKey());

        RepoConfig actualOldRepoConfig = configChangeReload.getRepoConfigChange().getOldRepoConfig();
        assertEquals(localRepoConfig, actualOldRepoConfig);

        RepoConfig actualNewRepoConfig = configChangeReload.getRepoConfigChange().getNewRepoConfig();
        LocalRepoConfig expectedNewRepoConfig = LocalRepoConfig.create(repoKey, Generic);
        expectedNewRepoConfig.setModification(newModification);
        assertEquals(expectedNewRepoConfig, actualNewRepoConfig);
    }

    /**
     * Non-modified update event - old config exists in cache with same modification date compared to event
     */
    @Test
    public void reloadRepoConfigUpdateNotModified() {
        String repoKey = "repoKey";
        long oldModification = 1L;
        long newModification = 1L;
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create(repoKey, Generic);
        localRepoConfig.setModification(oldModification);

        RepoConfigCache configCache = mockRepoConfigCache(ImmutableSet.of(localRepoConfig));
        RepositoryConfigServiceImpl repositoryConfigService = mockRepoConfigService();

        RepoConfigReload repoCreate = new RepoConfigReload(repoKey, LOCAL, Generic, REPO_UPDATE, newModification);
        RepoConfigPropagationModel propagationModel = createPropagationModel(Set.of(repoCreate), Set.of());

        repositoryConfigService.setCache(configCache);
        repositoryConfigService.reloadRepoConfigs(propagationModel);

        ArgumentCaptor<RepoConfigChangesReload> argumentCaptor =
                ArgumentCaptor.forClass(RepoConfigChangesReload.class);
        verify(ContextHelper.get().beanForType(InternalRepositoryService.class))
                .onPropagatedRepoConfigReload(argumentCaptor.capture());
        RepoConfigChangesReload repoConfigChangesReload = argumentCaptor.getValue();
        assertTrue(repoConfigChangesReload.getAll().isEmpty());

    }

    /**
     * Outdated update event - old config exists in cache with different modification date compared to event, but
     * config does not exist in DB
     */
    @Test
    public void reloadRepoConfigUpdateOutdated() {
        String repoKey = "repoKey";
        long oldModification = 1L;
        long newModification = 2L;
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create(repoKey, Generic);
        localRepoConfig.setModification(oldModification);
        mockStorageService(Set.of());
        RepoConfigCache configCache = mockRepoConfigCache(ImmutableSet.of(localRepoConfig));
        RepositoryConfigServiceImpl repositoryConfigService = mockRepoConfigService();

        RepoConfigReload repoCreate = new RepoConfigReload(repoKey, LOCAL, Generic, REPO_UPDATE, newModification);
        RepoConfigPropagationModel propagationModel = createPropagationModel(Set.of(repoCreate), Set.of());

        repositoryConfigService.setCache(configCache);
        repositoryConfigService.reloadRepoConfigs(propagationModel);

        ArgumentCaptor<RepoConfigChangesReload> argumentCaptor =
                ArgumentCaptor.forClass(RepoConfigChangesReload.class);
        verify(ContextHelper.get().beanForType(InternalRepositoryService.class))
                .onPropagatedRepoConfigReload(argumentCaptor.capture());
        RepoConfigChangesReload repoConfigChangesReload = argumentCaptor.getValue();
        assertTrue(repoConfigChangesReload.getAll().isEmpty());
    }

    /**
     * Missed created update event - Config does not exist in cache but exists in DB
     */
    @Test
    public void reloadRepoConfigUpdateOfMissedCreate() {
        String repoKey = "repoKey";
        long newModification = 2L;
        DbRepositoryConfig dbRepoConfig = repositoryConfigMapper.repositoryConfigToDbRepositoryConfig(
                LocalRepoConfig.create(repoKey, Generic));
        mockStorageService(dbRepoConfig);

        RepoConfigCache configCache = mock(RepoConfigCache.class, RETURNS_DEEP_STUBS);
        when(configCache.getRepositoryConfigsByKeys(Set.of(repoKey))).thenReturn(ImmutableSet.of());
        RepositoryConfigServiceImpl repositoryConfigService = mockRepoConfigService();

        RepoConfigReload repoCreate = new RepoConfigReload(repoKey, LOCAL, Generic, REPO_UPDATE, newModification);
        RepoConfigPropagationModel propagationModel = createPropagationModel(Set.of(repoCreate), Set.of());

        repositoryConfigService.setCache(configCache);
        repositoryConfigService.reloadRepoConfigs(propagationModel);

        ArgumentCaptor<RepoConfigChangesReload> argumentCaptor =
                ArgumentCaptor.forClass(RepoConfigChangesReload.class);
        verify(ContextHelper.get().beanForType(InternalRepositoryService.class))
                .onPropagatedRepoConfigReload(argumentCaptor.capture());
        RepoConfigChangesReload repoConfigChangesReload = argumentCaptor.getValue();
        assertEquals(1, repoConfigChangesReload.getAll().size());
        RepoConfigChangeReload configChangeReload = repoConfigChangesReload.filterBy(LOCAL, REPO_UPDATE).getFirst();
        assertEquals(repoKey, configChangeReload.getRepoKey());

        LocalRepoConfig expectedNewRepoConfig = LocalRepoConfig.create(repoKey, Generic);
        expectedNewRepoConfig.setModification(newModification);

        RepoConfig actualOldRepoConfig = configChangeReload.getRepoConfigChange().getOldRepoConfig();
        assertEquals(expectedNewRepoConfig, actualOldRepoConfig);
        RepoConfig actualNewRepoConfig = configChangeReload.getRepoConfigChange().getNewRepoConfig();
        assertEquals(expectedNewRepoConfig, actualNewRepoConfig);
    }

    private static RepoConfigPropagationModel createPropagationModel(Set<RepoConfigReload> repoCreatedOrUpdatedEvents,
            Set<RepoConfigReload> repoDeletedEvents) {
        RepoConfigPropagationModel propagationModel = new RepoConfigPropagationModel();
        propagationModel.setRepoConfigCreateOrUpdate(repoCreatedOrUpdatedEvents);
        propagationModel.setRepoConfigDelete(repoDeletedEvents);
        return propagationModel;
    }

    private RepositoryConfigServiceImpl mockRepoConfigService() {
        RepositoryConfigServiceImpl repositoryConfigService = createService();
        when(context.beanForType(RepositoryConfigService.class)).thenReturn(repositoryConfigService);
        return repositoryConfigService;
    }

    private void mockStorageService(DbRepositoryConfig dbRepoConfig) {
        mockStorageService(Set.of(dbRepoConfig));
    }

    private void mockStorageService(Set<DbRepositoryConfig> dbRepoConfigs) {
        when(storageService.getAllRepositories()).thenReturn(new ArrayList<>(dbRepoConfigs));
        Set<String> repoKeys = dbRepoConfigs.stream().map(DbRepositoryConfig::getKey).collect(Collectors.toSet());
        when(storageService.findKeysFromSet(repoKeys)).thenReturn(repoKeys);
        when(storageService.findRepositoryConfigsByKeys(repoKeys)).thenReturn(dbRepoConfigs);
    }

    private static RepoConfigCache mockRepoConfigCache(ImmutableSet<RepoConfig> repoConfigsInCache) {
        RepoConfigCache repoConfigCache = mock(RepoConfigCache.class);
        when(repoConfigCache.getRepositoryConfigsByKeys(Set.of())).thenReturn(ImmutableSet.of());
        Set<String> repoKeys =
                repoConfigsInCache.stream().map(RepoConfig::getKey).collect(Collectors.toUnmodifiableSet());
        when(repoConfigCache.getRepositoryConfigsByKeys(repoKeys)).thenReturn(repoConfigsInCache);
        return repoConfigCache;
    }

    @Test
    public void convertRepoConfigsToUpdateChangesCreateAndUpdate() {
        VirtualRepoConfig virtualRepoConfigOld1 = VirtualRepoConfig.create("updateVirtualKey", Maven);
        VirtualRepoConfig virtualRepoConfigNew1 = VirtualRepoConfig.create("updateVirtualKey", Maven);
        virtualRepoConfigOld1.getRepoTypeConfig().setRepositoryRefs(List.of("repoKey1", "repoKey2"));
        virtualRepoConfigNew1.getRepoTypeConfig().setRepositoryRefs(List.of("repoKey1"));
        VirtualRepoConfig virtualRepoConfig2 = VirtualRepoConfig.create("createVirtualKey", Maven);
        List<VirtualRepoConfig> virtualRepoConfigs = List.of(virtualRepoConfigNew1, virtualRepoConfig2);
        Collection<RepoConfig> existingRepos = Set.of(virtualRepoConfigOld1);
        Map<RepoDetailsType, List<RepoConfigChange>> expected = Map.of(VIRTUAL,
                List.of(new RepoConfigChange("updateVirtualKey", REPO_UPDATE,
                        virtualRepoConfigNew1, virtualRepoConfigOld1)));
        Map<RepoDetailsType, List<RepoConfigChange>> actual =
                RepositoryConfigServiceImpl.convertRepoConfigsToUpdateChanges(virtualRepoConfigs, existingRepos);
        assertEquals(expected, actual);
    }

    @Test
    public void convertRepoConfigsToUpdateChangesOnlyUpdate() {
        VirtualRepoConfig virtualRepoConfigOld1 = VirtualRepoConfig.create("updateVirtualKey1", Maven);
        VirtualRepoConfig virtualRepoConfigNew1 = VirtualRepoConfig.create("updateVirtualKey1", Maven);
        virtualRepoConfigOld1.getRepoTypeConfig().setRepositoryRefs(List.of("repoKey1", "repoKey2"));
        virtualRepoConfigNew1.getRepoTypeConfig().setRepositoryRefs(List.of("repoKey1"));
        VirtualRepoConfig virtualRepoConfigOld2 = VirtualRepoConfig.create("updateVirtualKey2", Maven);
        VirtualRepoConfig virtualRepoConfigNew2 = VirtualRepoConfig.create("updateVirtualKey2", Maven);
        virtualRepoConfigNew1.getRepoTypeConfig().setRepositoryRefs(List.of("repoKey11"));
        List<VirtualRepoConfig> virtualRepoConfigs = List.of(virtualRepoConfigNew1, virtualRepoConfigNew2);
        Collection<RepoConfig> existingRepos = Set.of(virtualRepoConfigOld1, virtualRepoConfigOld2);
        Map<RepoDetailsType, List<RepoConfigChange>> expected = Map.of(VIRTUAL,
                List.of(new RepoConfigChange("updateVirtualKey1", REPO_UPDATE,
                                virtualRepoConfigNew1, virtualRepoConfigOld1),
                        new RepoConfigChange("updateVirtualKey2", REPO_UPDATE,
                                virtualRepoConfigNew2, virtualRepoConfigOld2)));
        Map<RepoDetailsType, List<RepoConfigChange>> actual =
                RepositoryConfigServiceImpl.convertRepoConfigsToUpdateChanges(virtualRepoConfigs, existingRepos);
        assertEquals(expected, actual);
    }

    @Test
    public void convertRepoConfigsToUpdateChangesNull() {
        VirtualRepoConfig virtualRepoConfigOld1 = VirtualRepoConfig.create("updateVirtualKey1", Maven);
        VirtualRepoConfig virtualRepoConfigNew1 = VirtualRepoConfig.create("updateVirtualKey1", Maven);
        virtualRepoConfigOld1.getRepoTypeConfig().setRepositoryRefs(List.of("repoKey1", "repoKey2"));
        virtualRepoConfigNew1.getRepoTypeConfig().setRepositoryRefs(List.of("repoKey1"));
        VirtualRepoConfig virtualRepoConfigOld2 = VirtualRepoConfig.create("updateVirtualKey2", Maven);
        VirtualRepoConfig virtualRepoConfigNew2 = VirtualRepoConfig.create("updateVirtualKey2", Maven);
        virtualRepoConfigNew2.getRepoTypeConfig().setRepositoryRefs(List.of("repoKey11"));
        List<VirtualRepoConfig> virtualRepoConfigs = List.of();
        Collection<RepoConfig> existingRepos = Set.of(virtualRepoConfigOld1, virtualRepoConfigOld2);
        Map<RepoDetailsType, List<RepoConfigChange>> expected = Map.of();
        Map<RepoDetailsType, List<RepoConfigChange>> actual =
                RepositoryConfigServiceImpl.convertRepoConfigsToUpdateChanges(virtualRepoConfigs, existingRepos);
        assertEquals(expected, actual);
    }

    @Test
    public void convertRepoConfigsToUpdateChangesOnlyCreate() {
        VirtualRepoConfig virtualRepoConfig1 = VirtualRepoConfig.create("create1VirtualKey", Maven);
        virtualRepoConfig1.getRepoTypeConfig().setRepositoryRefs(List.of("repoKey1", "repoKey2"));
        VirtualRepoConfig virtualRepoConfig2 = VirtualRepoConfig.create("create2VirtualKey", Maven);
        List<VirtualRepoConfig> virtualRepoConfigs = List.of(virtualRepoConfig1, virtualRepoConfig2);
        Collection<RepoConfig> existingRepos = Set.of();
        Map<RepoDetailsType, List<RepoConfigChange>> expected = Map.of();
        Map<RepoDetailsType, List<RepoConfigChange>> actual =
                RepositoryConfigServiceImpl.convertRepoConfigsToUpdateChanges(virtualRepoConfigs, existingRepos);
        assertEquals(expected, actual);
    }

    @Test
    public void convertRepoConfigsToCreateUpdateChanges() {
        VirtualRepoConfig virtualRepoConfigOld1 = VirtualRepoConfig.create("updateVirtualKey", Maven);
        VirtualRepoConfig virtualRepoConfigNew1 = VirtualRepoConfig.create("updateVirtualKey", Maven);
        virtualRepoConfigOld1.getRepoTypeConfig().setRepositoryRefs(List.of("repoKey1", "repoKey2"));
        virtualRepoConfigNew1.getRepoTypeConfig().setRepositoryRefs(List.of("repoKey1"));
        VirtualRepoConfig virtualRepoConfig2 = VirtualRepoConfig.create("createVirtualKey", Maven);
        List<VirtualRepoConfig> virtualRepoConfigs = List.of(virtualRepoConfigNew1, virtualRepoConfig2);
        Collection<RepoConfig> existingRepos = Set.of(virtualRepoConfigOld1);
        Map<RepoDetailsType, List<RepoConfigChange>> expected = Map.of(VIRTUAL,
                List.of(new RepoConfigChange("updateVirtualKey", REPO_UPDATE,
                                virtualRepoConfigNew1, virtualRepoConfigOld1),
                        new RepoConfigChange("createVirtualKey", REPO_CREATE,
                                virtualRepoConfig2, null)));
        Map<RepoDetailsType, List<RepoConfigChange>> actual =
                RepositoryConfigServiceImpl.convertRepoConfigsToCreateUpdateChanges(virtualRepoConfigs, existingRepos);
        assertEquals(expected, actual);
    }

    @Test
    public void callOnRepoConfigsReloadAsync() {
        RepositoryConfigService repositoryConfigService =
                createService(mock(RepositoryConfigStorageService.class));
        InternalRepositoryService repositoryService = mock(InternalRepositoryService.class);
        when(context.beanForType(InternalRepositoryService.class)).thenReturn(repositoryService);
        repositoryConfigService.callOnRepoConfigsReloadAsync(new RepoConfigChangesReload(Map.of()), false);
        verify(repositoryService).onRepoConfigReload(any(RepoConfigChangesReload.class));
    }

    @Test
    public void excludeNewRepoFromBackup() {
        // given
        CentralConfigDescriptorImpl cc = CentralConfigTestUtils.initCentralConfig();
        CentralConfigService centralConfigService = mock(CentralConfigService.class);
        when(centralConfigService.getMutableDescriptor()).thenReturn(cc);
        when(context.getCentralConfig()).thenReturn(centralConfigService);
        cc.conditionallyAddToBackups("newLocal");
        RemoteRepoConfig newRemote = RemoteRepoConfig.create("newRemote", Generic);
        RepositoryConfigServiceImpl repositoryConfigService =
                createService(mock(RepositoryConfigStorageService.class));
        repositoryConfigService.setConfigValidationService(new ConfigValidationService());

        // when
        repositoryConfigService.upsertRepoConfig(newRemote);

        assertTrue(cc.isBackupExists("backup1"));
        BackupDescriptor backup1 =
                Iterables.tryFind(cc.getBackups(), input -> "backup1".equals(input.getKey())).orNull();
        assertNotNull(backup1, "Should find backup1");
        assertEquals(0, backup1.getExcludedRepositories().size());

        assertTrue(cc.isBackupExists("backup3"));
        BackupDescriptor backup3 =
                Iterables.tryFind(cc.getBackups(), input -> "backup3".equals(input.getKey())).orNull();
        assertNotNull(backup3, "Should find backup3");

        List<String> excludedRepositories = backup3.getExcludedRepositories();
        assertEquals(2, excludedRepositories.size());
        assertTrue(excludedRepositories.contains("newLocal"));
        assertTrue(excludedRepositories.contains(newRemote.getKey()));
    }

    @Test
    public void testImport() {
        // given
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("local-repo", Generic);
        FederatedRepoConfig federatedRepoConfig = FederatedRepoConfig.create("il-repo", Generic);
        String localUrl = "https://il.com";
        FederatedMember localMember = new FederatedMember();
        localMember.setUrl(localUrl + "/il-repo");
        FederatedMember usMember = new FederatedMember();
        usMember.setUrl("https://us.com/us-repo");
        federatedRepoConfig.getRepoTypeConfig().setFederatedMembers(List.of(localMember, usMember));
        List<LocalConfig> reposToImport = List.of(localRepoConfig, federatedRepoConfig);
        CentralConfigService centralConfigService = mock(CentralConfigService.class);
        CentralConfigDescriptor centralConfigDescriptor = mock(CentralConfigDescriptor.class);
        when(centralConfigDescriptor.getFederatedRepoUrlBaseOrDefault()).thenReturn(localUrl);
        when(centralConfigService.getDescriptor()).thenReturn(centralConfigDescriptor);
        when(context.getCentralConfig()).thenReturn(centralConfigService);
        ConfigValidationService configValidationService = mock(ConfigValidationService.class);
        RepositoryConfigServiceImpl repositoryConfigService =
                createService(mock(RepositoryConfigStorageService.class));
        repositoryConfigService.setConfigValidationService(configValidationService);

        // when
        repositoryConfigService.importRepoConfigs(reposToImport, false, false);

        // then
        ArgumentCaptor<List<? extends RepoConfig>> actualFilteredRepos = ArgumentCaptor.forClass(List.class);
        verify(configValidationService).validate(actualFilteredRepos.capture());
        assertTrue(actualFilteredRepos.getValue().contains(localRepoConfig));
        Optional<FederatedRepoConfig> federatedRepoConfigWithoutRemoteMembers = actualFilteredRepos.getValue().stream()
                .filter(repoConfig -> repoConfig.getRepoType().equals(FEDERATED))
                .map(repoConfig -> (FederatedRepoConfig) repoConfig)
                .findFirst();
        assertTrue(federatedRepoConfigWithoutRemoteMembers.isPresent());
        List<FederatedMember> membersOfImportedFederatedRepo =
                federatedRepoConfigWithoutRemoteMembers.get().getRepoTypeConfig().getFederatedMembers();
        assertEquals(1, membersOfImportedFederatedRepo.size());
        assertEquals(localMember, membersOfImportedFederatedRepo.get(0));
    }

    /**
     * System repos with importAllowed=false (observability + _intransit + jfrog-full-sync-info) must be skipped by
     * bulkDeleteRepositoryConfigs when isImport=true, otherwise the synchronous content cleanup runs as the importing
     * admin user (not _system) and is rejected by FullSyncStorageInterceptor / ObservabilityInterceptor for repos
     * marked deleteOnlyBySuperUser=true, failing the import with HTTP 500. See RTDEV-86629.
     * On a non-import bulk delete the same keys must still be processed normally.
     */
    @ParameterizedTest
    @ValueSource(strings = {LOG_SHIPPING_REPO_KEY, USAGE_REPO_KEY, BILLING_REPO_KEY,
            IN_TRANSIT_REPO_KEY, FULL_SYNC_REPO_KEY})
    public void testSystemRepoExcludedFromImportIsNotDeletedOnImport(String excludedFromImportRepoKey) {
        RepositoryConfigServiceImpl repositoryConfigService =
                spy(createService());
        doNothing().when(repositoryConfigService).dereferenceRepositoryFromConfigDescriptor(any());
        ConfigValidationService configValidationService = mock(ConfigValidationService.class);
        doNothing().when(configValidationService).validate(any());
        repositoryConfigService.setConfigValidationService(configValidationService);
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("local-repo", Generic);
        when(storageService.findRepositoryByKey(any())).thenReturn(
                repositoryConfigMapper.repositoryConfigToDbRepositoryConfig(localRepoConfig));
        // when import
        repositoryConfigService.bulkDeleteRepositoryConfigs(
                List.of(localRepoConfig.getKey(), excludedFromImportRepoKey), true, true, true);
        // then
        verify(repositoryConfigService, never())
                .getRepoConfigByKeyNoValidationChecks(excludedFromImportRepoKey, true);
        // when not import
        repositoryConfigService.bulkDeleteRepositoryConfigs(
                List.of(localRepoConfig.getKey(), excludedFromImportRepoKey), true, true, false);
        // then
        verify(repositoryConfigService, times(1))
                .getRepoConfigByKeyNoValidationChecks(excludedFromImportRepoKey, false);
    }

    @Test
    void testRepoConfigsBySearchPatternWhenNoRepos() {
        when(storageService.getAllRepositories()).thenReturn(List.of());
        RepositoryConfigServiceImpl repositoryConfigService =
                createService();
        List<AutoCompleteRepoConfigRecord> retrievedRepositoryConfigs =
                repositoryConfigService.getAllRepoConfigsBySearchPattern("local").stream()
                        .map(AutoCompleteRepoConfigRecord::valueOf).toList();
        assertNotNull(retrievedRepositoryConfigs);
        assertTrue(retrievedRepositoryConfigs.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"repo", "Repo"})
    @NullAndEmptySource
    public void testGetRepoConfigsByProjectAndTypeAndSearch(String searchPattern) {
        Set<RepoDetailsType> repoDetailsTypes = Set.of(RepoDetailsType.LOCAL);
        Set<RepoType> repoTypes = Set.of(Generic);
        String projectKey = "project2";

        RepoConfig repoConfig1 = LocalRepoConfig.create("repo1", Generic);
        RepoConfig repoConfig2 = LocalRepoConfig.create("repo2", Generic);
        RepoConfig repoConfig3 = LocalRepoConfig.create("repo3", Docker);
        List<RepoConfig> repoConfigs = List.of(repoConfig1, repoConfig2, repoConfig3);

        when(projectsService.getProjectRepositoriesKeys(eq(projectKey), anyBoolean())).thenReturn(
                Set.of("repo1", "repo2", "repo3"));

        RepoConfigCache repoConfigCache = mock(RepoConfigCache.class);
        when(repoConfigCache.getAllBasicConfigs())
                .thenReturn(repoConfigs.stream().map(RepoConfig::toBasic).collect(Collectors.toSet()));
        when(repoConfigCache.getRepositoryConfigsByKeys(Set.of("repo1", "repo2")))
                .thenReturn(ImmutableList.copyOf(List.of(repoConfig1, repoConfig2)));

        RepositoryConfigServiceImpl repositoryConfigService =
                createService(mock(RepositoryConfigStorageService.class));
        repositoryConfigService.setCache(repoConfigCache);

        List<RepoConfig> result = repositoryConfigService.getRepoConfigsBySearchPattern(
                repoDetailsTypes, repoTypes, projectKey, searchPattern);

        assertEquals(2, result.size());
        assertTrue(result.contains(repoConfig1));
        assertTrue(result.contains(repoConfig2));
        assertFalse(result.contains(repoConfig3));
    }

    @Test
    public void testGetRepoConfigsByProjectAndTypeAndDifferentType() {
        String searchPattern = "repo";
        Set<RepoDetailsType> repoDetailsTypes = Set.of(RepoDetailsType.REMOTE);
        String projectKey = "project2";

        RepoConfig repoConfig1 = LocalRepoConfig.create("repo1", Generic);
        RepoConfig repoConfig2 = LocalRepoConfig.create("repo2", Generic);
        List<RepoConfig> repoConfigs = List.of(repoConfig1, repoConfig2);

        when(projectsService.getProjectRepositoriesKeys(eq(projectKey), anyBoolean())).thenReturn(Set.of("repo1"));

        RepoConfigCache repoConfigCache = mock(RepoConfigCache.class);
        when(repoConfigCache.getAllBasicConfigs())
                .thenReturn(repoConfigs.stream().map(RepoConfig::toBasic).collect(Collectors.toSet()));
        when(repoConfigCache.getRepositoryConfigsByKeys(Set.of("repo1")))
                .thenReturn(ImmutableList.copyOf(Set.of(repoConfig1)));

        RepositoryConfigServiceImpl repositoryConfigService =
                createService(mock(RepositoryConfigStorageService.class));
        repositoryConfigService.setCache(repoConfigCache);

        List<RepoConfig> result = repositoryConfigService.getRepoConfigsBySearchPattern(
                repoDetailsTypes, projectKey, searchPattern);

        assertTrue(result.isEmpty());
    }

    @Test
    public void testGetRepoConfigsByProjectAndTypeAndNoRepos() {
        String searchPattern = "repo";
        Set<RepoDetailsType> repoDetailsTypes = Set.of(RepoDetailsType.LOCAL);
        String projectKey = "project2";

        when(projectsService.getProjectRepositoriesKeys(eq(projectKey), anyBoolean())).thenReturn(Set.of());

        RepoConfigCache repoConfigCache = mock(RepoConfigCache.class);
        when(repoConfigCache.getAllBasicConfigs()).thenReturn(Set.of());
        when(repoConfigCache.getRepositoryConfigsByKeys(Set.of())).thenReturn(ImmutableList.of());

        RepositoryConfigServiceImpl repositoryConfigService =
                createService(mock(RepositoryConfigStorageService.class));
        repositoryConfigService.setCache(repoConfigCache);

        List<RepoConfig> result = repositoryConfigService.getRepoConfigsBySearchPattern(
                repoDetailsTypes, projectKey, searchPattern);

        assertTrue(result.isEmpty());
    }

    @Test
    public void testGetRepoConfigsByProjectAndTypeAndMultipleMatches() {
        String searchPattern = "repo";
        Set<RepoDetailsType> repoDetailsTypes = Set.of(RepoDetailsType.LOCAL);
        String projectKey = "project2";

        RepoConfig repoConfig1 = LocalRepoConfig.create("repo1", Generic);
        RepoConfig repoConfig2 = LocalRepoConfig.create("repo2", Generic);
        RepoConfig repoConfig3 = LocalRepoConfig.create("repo3", Generic);
        List<RepoConfig> repoConfigs = List.of(repoConfig1, repoConfig2, repoConfig3);

        when(projectsService.getProjectRepositoriesKeys(eq(projectKey), anyBoolean())).thenReturn(
                Set.of("repo1", "repo2"));

        RepoConfigCache repoConfigCache = mock(RepoConfigCache.class);
        when(repoConfigCache.getAllBasicConfigs())
                .thenReturn(repoConfigs.stream().map(RepoConfig::toBasic).collect(Collectors.toSet()));
        when(repoConfigCache.getRepositoryConfigsByKeys(Set.of("repo1", "repo2")))
                .thenReturn(ImmutableList.copyOf(List.of(repoConfig1, repoConfig2)));

        RepositoryConfigServiceImpl repositoryConfigService =
                createService(mock(RepositoryConfigStorageService.class));
        repositoryConfigService.setCache(repoConfigCache);

        List<RepoConfig> result = repositoryConfigService.getRepoConfigsBySearchPattern(
                repoDetailsTypes, projectKey, searchPattern);

        assertEquals(2, result.size());
        assertTrue(result.contains(repoConfig1));
        assertTrue(result.contains(repoConfig2));
    }

    @Test
    public void testGetRepoConfigsByProjectAndTypeAndNoMatches() {
        String searchPattern = "nonexistent";
        Set<RepoDetailsType> repoDetailsTypes = Set.of(RepoDetailsType.LOCAL);
        String projectKey = "project2";

        RepoConfig repoConfig1 = LocalRepoConfig.create("repo1", Generic);
        RepoConfig repoConfig2 = LocalRepoConfig.create("repo2", Generic);
        List<RepoConfig> repoConfigs = List.of(repoConfig1, repoConfig2);

        when(projectsService.getProjectRepositoriesKeys(eq(projectKey), anyBoolean())).thenReturn(
                Set.of("repo1", "repo2"));

        RepoConfigCache repoConfigCache = mock(RepoConfigCache.class);
        when(repoConfigCache.getAllBasicConfigs())
                .thenReturn(repoConfigs.stream().map(RepoConfig::toBasic).collect(Collectors.toSet()));
        when(repoConfigCache.getRepositoryConfigsByKeys(Set.of("repo1", "repo2")))
                .thenReturn(ImmutableList.copyOf(repoConfigs));

        RepositoryConfigServiceImpl repositoryConfigService =
                createService(mock(RepositoryConfigStorageService.class));
        repositoryConfigService.setCache(repoConfigCache);

        List<RepoConfig> result = repositoryConfigService.getRepoConfigsBySearchPattern(
                repoDetailsTypes, projectKey, searchPattern);

        assertTrue(result.isEmpty());
    }

    @Test
    void testRepoConfigsBySearchPatternByRepoKey() {
        final String SEARCH_PATTERN = "abc";

        List<RepoConfig> allRepos = List.of(
                LocalRepoConfig.create(String.format("repo-%s-1", SEARCH_PATTERN), Generic),
                LocalRepoConfig.create(String.format("repo-%s-2", SEARCH_PATTERN), Generic),
                LocalRepoConfig.create("repo-xyz-1", Generic),
                LocalRepoConfig.create("repo-xyz-2", Generic)
        );

        when(storageService.getAllRepositories()).thenReturn(createDbRepositoryConfigs(allRepos));

        RepositoryConfigServiceImpl repositoryConfigService = createService();

        List<RepoConfig> retrievedRepositoryConfigs =
                repositoryConfigService.getAllRepoConfigsBySearchPattern(SEARCH_PATTERN);

        assertNotNull(retrievedRepositoryConfigs);
        assertEquals(2, retrievedRepositoryConfigs.size());
        assertTrue(retrievedRepositoryConfigs.stream().allMatch(repo -> repo.getKey().contains(SEARCH_PATTERN)));
    }

    private List<DbRepositoryConfig> createDbRepositoryConfigs(List<RepoConfig> repos) {
        List<DbRepositoryConfig> dbRepoConfigs = new ArrayList<>();
        for (RepoConfig repoConfig : repos) {
            DbRepositoryConfig dbRepoConfig = repositoryConfigMapper.repositoryConfigToDbRepositoryConfig(repoConfig);
            dbRepoConfigs.add(dbRepoConfig);
        }
        return dbRepoConfigs;
    }

    private void createAndBindToArtifactoryHome() throws IOException {
        File homeDir =
                Files.createTempDirectory(this.getClass().getSimpleName() + "_" + new Random().nextLong()).toFile();
        ArtifactoryHome artifactoryHome = new TestArtifactoryHome(homeDir);
        artifactoryHome.initArtifactorySystemProperties();
        ArtifactoryHome.bind(artifactoryHome);
    }

    private void unbindAndCleanHome() {
        if (ArtifactoryHome.isBound()) {
            FileUtils.deleteQuietly(ArtifactoryHome.get().getHomeDir());
            ArtifactoryHome.unbind();
        }
    }

    private DbRepositoryConfig getDbRepositoryConfig(RepoConfig repositoryConfig) {
        repositoryConfig.getSecurityConfig().setXrayDataTtl(repositoryConfig.getSecurityConfig().getXrayDataTtl());
        repositoryConfig.getSecurityConfig().setXrayIndex(repositoryConfig.getSecurityConfig().isXrayIndex());
        return repositoryConfigMapper.repositoryConfigToDbRepositoryConfig(repositoryConfig);
    }

    @Test
    void testAddXrayNotAvailableModeIfNeeded_ValuePreserved() {
        // Create a local repo config with XrayNotAvailableMode set
        LocalRepoConfig oldRepoConfig = LocalRepoConfig.create("repo-key", Generic);
        oldRepoConfig.getSecurityConfig().setXrayNotAvailableMode(XrayNotAvailableMode.FAIL_OPEN);

        DbRepositoryConfig dbRepositoryConfig =
                repositoryConfigMapper.repositoryConfigToDbRepositoryConfig(oldRepoConfig);
        when(storageService.getAllRepositories()).thenReturn(List.of(dbRepositoryConfig));

        // Create a new repo config (same key) but without XrayNotAvailableMode
        LocalRepoConfig newRepoConfig = LocalRepoConfig.create("repo-key", Generic);
        assertNull(newRepoConfig.getXrayNotAvailableMode(),
                "New repo config should have null XrayNotAvailableMode initially");

        // Configure the service
        RepositoryConfigServiceImpl repositoryConfigService =
                spy(createService());
        repositoryConfigService.setConfigValidationService(new ConfigValidationService());
        doReturn(oldRepoConfig).when(repositoryConfigService)
                .getRepoConfigByKeyNoValidationChecks(anyString(), anyBoolean());

        // Call the method that uses addXrayNotAvailableModeIfNeeded internally
        repositoryConfigService.upsertRepoConfig(newRepoConfig);

        // Verify XrayNotAvailableMode was preserved
        assertEquals(XrayNotAvailableMode.FAIL_OPEN, newRepoConfig.getXrayNotAvailableMode(),
                "XrayNotAvailableMode should be preserved from old repo config");

        // Capture and verify the update operation
        ArgumentCaptor<Set<DbRepositoryConfig>> updateCaptor = ArgumentCaptor.forClass(Set.class);
        verify(storageService).flushBulkChanges(any(), updateCaptor.capture(), any(), any(), any());

        Set<DbRepositoryConfig> updatedConfigs = updateCaptor.getValue();
        assertEquals(1, updatedConfigs.size());
        RepoConfig updatedConfig =
                repositoryConfigMapper.dbInfoBlobToRepositoryConfig(updatedConfigs.iterator().next());
        assertEquals(XrayNotAvailableMode.FAIL_OPEN, updatedConfig.getXrayNotAvailableMode(),
                "Updated config should have the preserved XrayNotAvailableMode");
    }

    @Test
    void testAddXrayNotAvailableModeIfNeeded_NullValue() {
        // Create a local repo config with null XrayNotAvailableMode
        LocalRepoConfig oldRepoConfig = LocalRepoConfig.create("repo-key", Generic);
        assertNull(oldRepoConfig.getXrayNotAvailableMode(), "Old repo config should have null XrayNotAvailableMode");

        DbRepositoryConfig dbRepositoryConfig =
                repositoryConfigMapper.repositoryConfigToDbRepositoryConfig(oldRepoConfig);
        when(storageService.getAllRepositories()).thenReturn(List.of(dbRepositoryConfig));

        // Create a new repo config (same key) also without XrayNotAvailableMode
        LocalRepoConfig newRepoConfig = LocalRepoConfig.create("repo-key", Generic);
        assertNull(newRepoConfig.getXrayNotAvailableMode(),
                "New repo config should have null XrayNotAvailableMode initially");

        // Configure the service
        RepositoryConfigServiceImpl repositoryConfigService =
                spy(createService());
        repositoryConfigService.setConfigValidationService(new ConfigValidationService());
        doReturn(oldRepoConfig).when(repositoryConfigService)
                .getRepoConfigByKeyNoValidationChecks(anyString(), anyBoolean());

        // Call the method that uses addXrayNotAvailableModeIfNeeded internally
        repositoryConfigService.upsertRepoConfig(newRepoConfig);

        // Verify XrayNotAvailableMode remains null
        assertNull(newRepoConfig.getXrayNotAvailableMode(),
                "XrayNotAvailableMode should still be null since old config had null value");
    }

    @Test
    void testAddXrayNotAvailableModeIfNeeded_NewRepo() {
        // Create a new repo config (no old config exists)
        LocalRepoConfig newRepoConfig = LocalRepoConfig.create("new-repo-key", Generic);
        assertNull(newRepoConfig.getXrayNotAvailableMode(),
                "New repo config should have null XrayNotAvailableMode initially");

        // Configure the service
        RepositoryConfigServiceImpl repositoryConfigService =
                spy(createService());
        repositoryConfigService.setConfigValidationService(new ConfigValidationService());
        doReturn(null).when(repositoryConfigService).getRepoConfigByKeyNoValidationChecks(anyString(), anyBoolean());

        // Call the method that uses addXrayNotAvailableModeIfNeeded internally
        repositoryConfigService.upsertRepoConfig(newRepoConfig);

        // Verify XrayNotAvailableMode remains null (as there was no old config to copy from)
        assertNull(newRepoConfig.getXrayNotAvailableMode(),
                "XrayNotAvailableMode should still be null since no old config existed");
    }

    @Test
    void testFilterRepoConfigsAllowedForImport() {
        // Given
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("local-repo", Generic);
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("remote-repo", Generic);
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("virtual-repo", Generic);
        FederatedRepoConfig federatedRepoConfig = FederatedRepoConfig.create("federated-repo", Generic);
        LocalRepoConfig usageSystemRepoConfig = LocalRepoConfig.create(SystemRepos.USAGE_REPO_KEY, Generic);
        LocalRepoConfig buildSystemRepoConfig = LocalRepoConfig.create(SystemRepos.BUILDINFO_REPO_KEY, Generic);
        List<RepoConfig> repoConfigs =
                List.of(localRepoConfig, remoteRepoConfig, virtualRepoConfig, federatedRepoConfig,
                        usageSystemRepoConfig, buildSystemRepoConfig);

        // when
        RepositoryConfigServiceImpl repositoryConfigService = createService();
        List<RepoConfig> filteredConfigs = repositoryConfigService.filterRepoConfigsAllowedForImport(repoConfigs);

        // Then
        assertEquals(5, filteredConfigs.size());
        assertTrue(filteredConfigs.contains(localRepoConfig));
        assertTrue(filteredConfigs.contains(remoteRepoConfig));
        assertTrue(filteredConfigs.contains(buildSystemRepoConfig));
        assertFalse(filteredConfigs.contains(usageSystemRepoConfig));
    }

    @Test
    void testFilterReposAllowedForImport(){
        // Given
        List<String> repoKeys = new ArrayList<>();
        repoKeys.add(BUILDINFO_REPO_KEY);
        repoKeys.add(USAGE_REPO_KEY);
        repoKeys.add("local-repo");

        // when
        RepositoryConfigServiceImpl repositoryConfigService = createService();
        List<String> reposAllowedForImport = repositoryConfigService.filterReposAllowedForImport(repoKeys);

        // Then
        assertEquals(2, reposAllowedForImport.size());
        assertFalse(reposAllowedForImport.contains(USAGE_REPO_KEY));
    }

    // ========================================================================================
    // Federation Migration Tests
    // ========================================================================================

    private void setGridEnabled(boolean enabled) {
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.gridEnabled.getPropertyName(), String.valueOf(enabled));
    }

    @Test
    void migrateLocalReposWithFederation_shouldDoNothing_whenRepoKeysEmpty() {
        RepositoryConfigServiceImpl service = createService();
        FederatedRepoService federatedRepoService = mock(FederatedRepoService.class);
        service.setFederatedRepoService(federatedRepoService);

        service.migrateLocalReposWithFederation(List.of());

        verifyNoInteractions(federatedRepoService);
    }

    @Test
    void migrateLocalReposWithFederation_shouldDoNothing_whenFederatedRepoServiceNull() {
        RepositoryConfigServiceImpl service = createService();
        // federatedRepoService is null by default

        LocalRepoConfig config = LocalRepoConfig.create("repo1", Generic);
        service.migrateLocalReposWithFederation(List.of(config));

        // No exception thrown, method returns early
    }

    @ParameterizedTest(name = "migrateLocalConfigToFederatedIfNeeded shouldNotMigrate: gridEnabled={0}")
    @ValueSource(booleans = {true, false})
    void migrateLocalConfigToFederatedIfNeeded_shouldReturnNull_whenNoFederationTrigger(boolean gridEnabled) {
        setGridEnabled(gridEnabled);
        RepositoryConfigServiceImpl service = createService();
        FederatedRepoService federatedRepoService = mock(FederatedRepoService.class);
        service.setFederatedRepoService(federatedRepoService);

        // LocalRepoConfig without gridTopologyId or members - should not migrate
        LocalRepoConfig config = LocalRepoConfig.create("test-repo", Generic);

        String result = service.migrateLocalConfigToFederatedIfNeeded(config);

        assertNull(result);
        verify(federatedRepoService, never()).migrate(anyString());
    }

    @ParameterizedTest(name = "migrateLocalConfigToFederatedIfNeeded shouldMigrate: {0}")
    @MethodSource("migrationScenarioProvider")
    void migrateLocalConfigToFederatedIfNeeded_shouldMigrate_whenHasFederationTrigger(
            String scenario, boolean gridEnabled, LocalRepoConfig config) {
        setGridEnabled(gridEnabled);
        RepositoryConfigServiceImpl service = spy(createService());
        FederatedRepoService federatedRepoService = mock(FederatedRepoService.class);
        service.setFederatedRepoService(federatedRepoService);
        // Mock to return null so restoreFederationDetails returns early
        doReturn(null).when(service).getRepoConfigByKeyNoValidationChecks(anyString(), anyBoolean());

        String result = service.migrateLocalConfigToFederatedIfNeeded(config);

        assertNull(result);
        verify(federatedRepoService).migrate("test-repo");
    }

    private static Stream<Arguments> migrationScenarioProvider() {
        // Grid enabled with gridTopologyId
        LocalRepoConfig configWithTopology = LocalRepoConfig.create("test-repo", Generic);
        configWithTopology.getBaseConfig().setGridTopologyId("topology-123");

        // Grid disabled with federation members - use fromFederated factory
        FederatedRepoConfig federatedConfig = FederatedRepoConfig.create("test-repo", Generic);
        FederatedMember member = new FederatedMember();
        member.setUrl("http://site1.example.com/artifactory/test-repo");
        federatedConfig.getRepoTypeConfig().setFederatedMembers(List.of(member));
        LocalRepoConfig configWithMembers = LocalRepoConfig.fromFederated(federatedConfig);

        return Stream.of(
                // Grid enabled: gridTopologyId triggers migration
                Arguments.of("gridEnabled+topologyId", true, configWithTopology),
                // Grid disabled: federation members trigger migration
                Arguments.of("gridDisabled+members", false, configWithMembers)
        );
    }

    @Test
    void migrateLocalConfigToFederatedIfNeeded_shouldReturnError_whenMigrationFails() {
        setGridEnabled(true);
        RepositoryConfigServiceImpl service = createService();
        FederatedRepoService federatedRepoService = mock(FederatedRepoService.class);
        service.setFederatedRepoService(federatedRepoService);

        LocalRepoConfig config = LocalRepoConfig.create("test-repo", Generic);
        config.getBaseConfig().setGridTopologyId("topology-123");

        doThrow(new BadRequestException("Migration failed")).when(federatedRepoService).migrate("test-repo");

        String result = service.migrateLocalConfigToFederatedIfNeeded(config);

        assertEquals("Migration failed", result);
        verify(federatedRepoService).migrate("test-repo");
    }

    @ParameterizedTest(name = "migrateLocalReposWithFederation: {0}")
    @MethodSource("migrateLocalReposScenarioProvider")
    void migrateLocalReposWithFederation_shouldHandleVariousScenarios(
            String scenario, RepoConfig config, boolean shouldMigrate, boolean shouldFail) {
        setGridEnabled(true);
        RepositoryConfigServiceImpl service = spy(createService());
        FederatedRepoService federatedRepoService = mock(FederatedRepoService.class);
        service.setFederatedRepoService(federatedRepoService);

        String repoKey = config.getKey();
        // Mock to return null so restoreFederationDetails returns early
        doReturn(null).when(service).getRepoConfigByKeyNoValidationChecks(anyString(), eq(true));

        if (shouldFail) {
            doThrow(new BadRequestException("Migration failed")).when(federatedRepoService).migrate(repoKey);
        }

        // Should never throw - failures are collected and logged
        assertDoesNotThrow(() -> service.migrateLocalReposWithFederation(List.of(config)));

        if (shouldMigrate) {
            verify(federatedRepoService).migrate(repoKey);
        } else {
            verify(federatedRepoService, never()).migrate(anyString());
        }
    }

    private static Stream<Arguments> migrateLocalReposScenarioProvider() {
        // Local config with gridTopologyId - should migrate successfully
        LocalRepoConfig localWithTopology = LocalRepoConfig.create("local-repo", Generic);
        localWithTopology.getBaseConfig().setGridTopologyId("topology-123");

        // Local config with gridTopologyId - migration fails but doesn't throw
        LocalRepoConfig localWithTopologyFails = LocalRepoConfig.create("local-repo-fails", Generic);
        localWithTopologyFails.getBaseConfig().setGridTopologyId("topology-456");

        // Federated config - should be skipped (not LocalRepoConfig)
        FederatedRepoConfig federatedConfig = FederatedRepoConfig.create("fed-repo", Generic);

        // Local config without gridTopologyId - should be skipped (no federation trigger)
        LocalRepoConfig localWithoutTopology = LocalRepoConfig.create("local-no-trigger", Generic);

        return Stream.of(
                // scenario, config, shouldMigrate, shouldFail
                Arguments.of("localWithTopology_shouldMigrate", localWithTopology, true, false),
                Arguments.of("localWithTopology_migrationFails", localWithTopologyFails, true, true),
                Arguments.of("federatedConfig_shouldSkip", federatedConfig, false, false),
                Arguments.of("localWithoutTrigger_shouldSkip", localWithoutTopology, false, false)
        );
    }

    // ========================================================================================
    // restoreFederationDetails Tests (via migrateLocalConfigToFederatedIfNeeded)
    // ========================================================================================

    @ParameterizedTest(name = "gridEnabled={0} → membersRestored={1}")
    @CsvSource({"false, true", "true, false"})
    void migrateLocalConfigToFederatedIfNeeded_shouldRestoreMembersBasedOnGridState(
            boolean gridEnabled, boolean expectMembersRestored) {
        setGridEnabled(gridEnabled);
        RepositoryConfigServiceImpl service = createSpiedServiceWithFederatedSupport();

        LocalRepoConfig config = createLocalConfigWithFederationMembers(gridEnabled);
        FederatedRepoConfig migratedConfig = FederatedRepoConfig.create("test-repo", Generic);
        doReturn(migratedConfig).when(service).getRepoConfigByKeyNoValidationChecks("test-repo", true);
        doNothing().when(service).upsertRepoConfig(any());

        service.migrateLocalConfigToFederatedIfNeeded(config);

        assertEquals(expectMembersRestored ? 1 : 0,
                migratedConfig.getRepoTypeConfig().getFederatedMembers().size());
    }

    @Test
    void migrateLocalConfigToFederatedIfNeeded_shouldRestoreProxySettings_whenFederationDataHasProxy() {
        setGridEnabled(true);
        RepositoryConfigServiceImpl service = createSpiedServiceWithFederatedSupport();

        FederatedRepoConfig federatedConfig = FederatedRepoConfig.create("test-repo", Generic);
        federatedConfig.getRepoTypeConfig().setProxyRef("my-proxy");
        federatedConfig.getRepoTypeConfig().setDisableProxy(true);
        LocalRepoConfig config = LocalRepoConfig.fromFederated(federatedConfig);
        config.getBaseConfig().setGridTopologyId("topology-123");

        FederatedRepoConfig migratedConfig = FederatedRepoConfig.create("test-repo", Generic);
        doReturn(migratedConfig).when(service).getRepoConfigByKeyNoValidationChecks("test-repo", true);
        doNothing().when(service).upsertRepoConfig(any());

        service.migrateLocalConfigToFederatedIfNeeded(config);

        assertEquals("my-proxy", migratedConfig.getRepoTypeConfig().getProxyRef());
        assertTrue(migratedConfig.getRepoTypeConfig().isDisableProxy());
    }

    @Test
    void migrateLocalConfigToFederatedIfNeeded_shouldSkipRestore_whenNoFederatedConfigReturned() {
        setGridEnabled(true);
        RepositoryConfigServiceImpl service = createSpiedServiceWithFederatedSupport();

        LocalRepoConfig config = LocalRepoConfig.create("test-repo", Generic);
        config.getBaseConfig().setGridTopologyId("topology-123");
        doReturn(null).when(service).getRepoConfigByKeyNoValidationChecks("test-repo", true);

        service.migrateLocalConfigToFederatedIfNeeded(config);

        verify(service, never()).upsertRepoConfig(any());
    }

    @Test
    void migrateLocalConfigToFederatedIfNeeded_shouldNotCallUpsert_whenNoFederationDataToRestore() {
        setGridEnabled(true);
        RepositoryConfigServiceImpl service = createSpiedServiceWithFederatedSupport();

        LocalRepoConfig config = LocalRepoConfig.create("test-repo", Generic);
        config.getBaseConfig().setGridTopologyId("topology-123");

        FederatedRepoConfig migratedConfig = FederatedRepoConfig.create("test-repo", Generic);
        doReturn(migratedConfig).when(service).getRepoConfigByKeyNoValidationChecks("test-repo", true);

        service.migrateLocalConfigToFederatedIfNeeded(config);

        verify(service, never()).upsertRepoConfig(any());
    }

    private RepositoryConfigServiceImpl createSpiedServiceWithFederatedSupport() {
        RepositoryConfigServiceImpl service = spy(createService());
        service.setFederatedRepoService(mock(FederatedRepoService.class));
        return service;
    }

    private LocalRepoConfig createLocalConfigWithFederationMembers(boolean gridEnabled) {
        FederatedRepoConfig federatedConfig = FederatedRepoConfig.create("test-repo", Generic);
        FederatedMember member = new FederatedMember();
        member.setUrl("http://site1.example.com/artifactory/test-repo");
        federatedConfig.getRepoTypeConfig().setFederatedMembers(List.of(member));
        LocalRepoConfig config = LocalRepoConfig.fromFederated(federatedConfig);
        if (gridEnabled) {
            config.getBaseConfig().setGridTopologyId("topology-123");
        }
        return config;
    }

    @Test
    void findAllMissingRepoRootPaths_shouldDelegateToStorageService() {
        // Arrange
        Set<String> missingRepos = Set.of("repo-1", "repo-2", "repo-3");
        when(storageService.findAllMissingRepoRootPaths()).thenReturn(missingRepos);
        RepositoryConfigServiceImpl service = createService();

        // Act
        Set<String> result = service.findAllMissingRepoRootPaths();

        // Assert
        assertEquals(missingRepos, result);
        verify(storageService).findAllMissingRepoRootPaths();
    }

    @Test
    void createMissingRepoRootFolders_shouldDelegateToStorageService() {
        // Arrange
        when(storageService.createMissingRepoRootFolders()).thenReturn(5);
        RepositoryConfigServiceImpl service = createService();

        // Act
        int result = service.createMissingRepoRootFolders();

        // Assert
        assertEquals(5, result);
        verify(storageService).createMissingRepoRootFolders();
    }

    @Test
    void insertRepoConfig_shouldThrowLockAlreadyTakenException_whenLockAcquisitionFails() throws InterruptedException {
        // Arrange
        SecurityService securityService = mock(SecurityService.class);
        when(securityService.isAnyProjectAdmin()).thenReturn(true);
        when(context.beanForType(SecurityService.class)).thenReturn(securityService);

        HaCommonAddon haCommonAddon = mock(HaCommonAddon.class);
        @SuppressWarnings("unchecked")
        ConflictsGuard<Object> conflictsGuard = mock(ConflictsGuard.class);
        when(addonsManager.addonByType(HaCommonAddon.class)).thenReturn(haCommonAddon);
        when(haCommonAddon.getConflictsGuard(any())).thenReturn(conflictsGuard);
        // Simulate lock acquisition failure
        when(conflictsGuard.tryToLock(any(), anyLong(), any())).thenReturn(false);

        LocalRepoConfig repoConfig = LocalRepoConfig.create("test-repo", Generic);
        RepositoryConfigServiceImpl service = createService();
        service.setConfigValidationService(new ConfigValidationService());

        // Act & Assert
        assertThrows(LockAlreadyTakenException.class, () -> service.insertRepoConfig(repoConfig));
    }

    @Test
    void insertRepoConfig_shouldThrowLockingFailedException_whenLockInterrupted() throws InterruptedException {
        // Arrange
        SecurityService securityService = mock(SecurityService.class);
        when(securityService.isAnyProjectAdmin()).thenReturn(true);
        when(context.beanForType(SecurityService.class)).thenReturn(securityService);

        HaCommonAddon haCommonAddon = mock(HaCommonAddon.class);
        @SuppressWarnings("unchecked")
        ConflictsGuard<Object> conflictsGuard = mock(ConflictsGuard.class);
        when(addonsManager.addonByType(HaCommonAddon.class)).thenReturn(haCommonAddon);
        when(haCommonAddon.getConflictsGuard(any())).thenReturn(conflictsGuard);
        // Simulate lock acquisition interrupted
        when(conflictsGuard.tryToLock(any(), anyLong(), any())).thenThrow(new InterruptedException("Test interrupt"));

        LocalRepoConfig repoConfig = LocalRepoConfig.create("test-repo", Generic);
        RepositoryConfigServiceImpl service = createService();
        service.setConfigValidationService(new ConfigValidationService());

        // Act & Assert
        LockingFailedException exception = assertThrows(LockingFailedException.class,
                () -> service.insertRepoConfig(repoConfig));
        assertTrue(exception.getMessage().contains("Interrupted"));
        assertTrue(Thread.currentThread().isInterrupted());
        // Clear interrupted flag for subsequent tests
        Thread.interrupted();
    }

    @Test
    void bulkInsertRepoConfigs_shouldThrowLockAlreadyTakenException_whenLockAcquisitionFails()
            throws InterruptedException {
        // Arrange
        SecurityService securityService = mock(SecurityService.class);
        when(securityService.isAnyProjectAdmin()).thenReturn(true);
        when(context.beanForType(SecurityService.class)).thenReturn(securityService);

        HaCommonAddon haCommonAddon = mock(HaCommonAddon.class);
        @SuppressWarnings("unchecked")
        ConflictsGuard<Object> conflictsGuard = mock(ConflictsGuard.class);
        when(addonsManager.addonByType(HaCommonAddon.class)).thenReturn(haCommonAddon);
        when(haCommonAddon.getConflictsGuard(any())).thenReturn(conflictsGuard);
        // Simulate lock acquisition failure
        when(conflictsGuard.tryToLock(any(), anyLong(), any())).thenReturn(false);

        List<RealRepoConfig> repoConfigs = List.of(LocalRepoConfig.create("test-repo", Generic));
        RepositoryConfigServiceImpl service = createService();
        service.setConfigValidationService(new ConfigValidationService());

        // Act & Assert
        assertThrows(LockAlreadyTakenException.class,
                () -> service.bulkInsertRepoConfigs(repoConfigs, false, false, false));
    }

    @Test
    void bulkUpsertRepoConfigs_shouldThrowLockAlreadyTakenException_whenLockAcquisitionFails()
            throws InterruptedException {
        // Arrange
        SecurityService securityService = mock(SecurityService.class);
        when(securityService.isAnyProjectAdmin()).thenReturn(true);
        when(context.beanForType(SecurityService.class)).thenReturn(securityService);

        HaCommonAddon haCommonAddon = mock(HaCommonAddon.class);
        @SuppressWarnings("unchecked")
        ConflictsGuard<Object> conflictsGuard = mock(ConflictsGuard.class);
        when(addonsManager.addonByType(HaCommonAddon.class)).thenReturn(haCommonAddon);
        when(haCommonAddon.getConflictsGuard(any())).thenReturn(conflictsGuard);
        // Simulate lock acquisition failure
        when(conflictsGuard.tryToLock(any(), anyLong(), any())).thenReturn(false);

        List<RealRepoConfig> repoConfigs = List.of(LocalRepoConfig.create("test-repo", Generic));
        RepositoryConfigServiceImpl service = createService();
        service.setConfigValidationService(new ConfigValidationService());

        // Act & Assert
        assertThrows(LockAlreadyTakenException.class,
                () -> service.bulkUpsertRepoConfigs(repoConfigs, false));
    }

    @Test
    void updateRepoConfig_shouldThrowLockAlreadyTakenException_whenLockAcquisitionFails() throws InterruptedException {
        // Arrange
        SecurityService securityService = mock(SecurityService.class);
        when(securityService.isAnyProjectAdmin()).thenReturn(true);
        when(context.beanForType(SecurityService.class)).thenReturn(securityService);

        HaCommonAddon haCommonAddon = mock(HaCommonAddon.class);
        @SuppressWarnings("unchecked")
        ConflictsGuard<Object> conflictsGuard = mock(ConflictsGuard.class);
        when(addonsManager.addonByType(HaCommonAddon.class)).thenReturn(haCommonAddon);
        when(haCommonAddon.getConflictsGuard(any())).thenReturn(conflictsGuard);
        // Simulate lock acquisition failure
        when(conflictsGuard.tryToLock(any(), anyLong(), any())).thenReturn(false);

        LocalRepoConfig repoConfig = LocalRepoConfig.create("test-repo", Generic);
        RepositoryConfigServiceImpl service = createService();
        service.setConfigValidationService(new ConfigValidationService());

        // Act & Assert
        assertThrows(LockAlreadyTakenException.class, () -> service.updateRepoConfig(repoConfig));
    }

    @Test
    void existsByKeyIgnoreCase_returnsFalse_whenBlank() {
        RepositoryConfigServiceImpl svc = createService();
        assertFalse(svc.existsByKeyIgnoreCase(""));
        assertFalse(svc.existsByKeyIgnoreCase(null));
    }

    @Test
    void existsByKeyIgnoreCase_returnsTrue_whenKeyExistsInActiveCache() {
        when(storageService.getAllRepositories()).thenReturn(List.of(
                repositoryConfigMapper.repositoryConfigToDbRepositoryConfig(
                        LocalRepoConfig.create("active-repo", Generic))));
        RepositoryConfigServiceImpl svc = createService();
        assertTrue(svc.existsByKeyIgnoreCase("active-repo"));
        assertTrue(svc.existsByKeyIgnoreCase("ACTIVE-REPO"));
    }

    @Test
    void existsByKeyIgnoreCase_returnsFalse_whenKeyNotInActiveCache() {
        when(storageService.getAllRepositories()).thenReturn(List.of());
        RepositoryConfigServiceImpl svc = createService();
        assertFalse(svc.existsByKeyIgnoreCase("missing"));
    }

    @Test
    void removeDanglingChildRefFromVirtual_isNoOp_whenVirtualRepoNotFound() {
        when(storageService.getAllRepositories()).thenReturn(List.of());

        RepositoryConfigServiceImpl service = createService();
        service.removeDanglingChildRefFromVirtual("virtual-repo", "child", null);

        verify(storageService, never()).flushBulkChanges(any(), any(), any(), any(), any());
    }

    @Test
    void removeDanglingChildRefFromVirtual_isNoOp_whenChildRefNotInVirtualRepo() {
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("virtual-repo", Generic);
        virtualRepoConfig.getRepoTypeConfig().getRepositoryRefs().add("other-child");
        when(storageService.getAllRepositories())
                .thenReturn(List.of(getDbRepositoryConfig(virtualRepoConfig)));

        RepositoryConfigServiceImpl service = createService();
        service.removeDanglingChildRefFromVirtual("virtual-repo", "missing-child", null);

        verify(storageService, never()).flushBulkChanges(any(), any(), any(), any(), any());
    }

    @Test
    void removeDanglingChildRefFromVirtual_removesRefAndPersists_whenChildExists() {
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("virtual-repo", Generic);
        virtualRepoConfig.getRepoTypeConfig().getRepositoryRefs().add("child");

        RepositoryConfigServiceImpl service = spy(createService());
        service.setConfigValidationService(new ConfigValidationService());
        doReturn(virtualRepoConfig).when(service).getRepoConfigByKey("virtual-repo");

        service.removeDanglingChildRefFromVirtual("virtual-repo", "child",
                LocalRepoConfig.create("child", Generic));

        assertFalse(virtualRepoConfig.getRepoTypeConfig().getRepositoryRefs().contains("child"),
                "Child ref should be removed from virtual repo");
        verify(storageService).flushBulkChanges(any(), any(), any(), any(), any());
    }

    @Test
    void removeDanglingChildRefFromVirtual_clearsDefaultDeploymentRef_whenRemovedRepoIsDefaultDeployment() {
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("virtual-repo", Generic);
        virtualRepoConfig.getRepoTypeConfig().getRepositoryRefs().add("child");
        virtualRepoConfig.getRepoTypeConfig().setDefaultDeploymentRepoRef("child");

        RepositoryConfigServiceImpl service = spy(createService());
        service.setConfigValidationService(new ConfigValidationService());
        doReturn(virtualRepoConfig).when(service).getRepoConfigByKey("virtual-repo");

        service.removeDanglingChildRefFromVirtual("virtual-repo", "child",
                LocalRepoConfig.create("child", Generic));

        assertNull(virtualRepoConfig.getRepoTypeConfig().getDefaultDeploymentRepoRef(),
                "defaultDeploymentRepoRef should be cleared when the removed repo was the default deployment repo");
        assertFalse(virtualRepoConfig.getRepoTypeConfig().getRepositoryRefs().contains("child"));
    }

    @Test
    void removeDanglingChildRefFromVirtual_isNoOp_whenChildRefAlreadyAbsent() {
        // Simulates the second concurrent call seeing a config where the ref was already removed
        VirtualRepoConfig alreadyUpdated = VirtualRepoConfig.create("virtual-repo", Generic);

        RepositoryConfigServiceImpl service = spy(createService());
        service.setConfigValidationService(new ConfigValidationService());
        doReturn(alreadyUpdated).when(service).getRepoConfigByKey("virtual-repo");

        service.removeDanglingChildRefFromVirtual("virtual-repo", "child", null);

        verify(storageService, never()).flushBulkChanges(any(), any(), any(), any(), any());
    }

    /**
     * Builds a {@link DbRepositoryConfig} whose blob and package-type column both carry an unknown type token,
     * simulating the DB state written by a newer Artifactory version before a rollback.
     */
    private DbRepositoryConfig unknownTypeBlob(String repoKey, String unknownType) {
        LocalRepoConfig genericRepo = LocalRepoConfig.create(repoKey, Generic);
        DbRepositoryConfig dbRepo = repositoryConfigMapper.repositoryConfigToDbRepositoryConfig(genericRepo);
        String originalJson = new String(dbRepo.getRepositoryInfoBlob(), StandardCharsets.UTF_8);
        String patchedJson = originalJson.replaceAll("(\"packageType\"\\s*:\\s*)\"generic\"",
                "$1\"" + unknownType + "\"");
        assertNotEquals(originalJson, patchedJson,
                "test setup: expected a packageType=generic token in the blob but found: " + originalJson);
        dbRepo.setRepositoryInfoBlob(patchedJson.getBytes(StandardCharsets.UTF_8));
        dbRepo.setPackageType(unknownType);
        return dbRepo;
    }

    @Test
    void testStartupWithUnknownRepoTypeSkipsRepo() {
        // default: repoConfigSkipUnknownPackageType = true -> unknown-type repos are skipped
        LocalRepoConfig knownRepo = LocalRepoConfig.create("known-repo", Maven);
        DbRepositoryConfig goodBlob = repositoryConfigMapper.repositoryConfigToDbRepositoryConfig(knownRepo);
        DbRepositoryConfig futureTypeBlob = unknownTypeBlob("future-type-repo", "SkillsFuture");

        when(storageService.getAllRepositories()).thenReturn(List.of(goodBlob, futureTypeBlob));

        // when: the service is constructed — any exception propagating here is the regression
        RepositoryConfigServiceImpl service = createService();

        // then: the known repo is accessible with its original package type
        assertNotNull(service.getRepoConfigByKey("known-repo"));
        assertEquals(Maven, service.getRepoConfigByKey("known-repo").getPackageType());

        // and: the unknown-type repo is absent (skipped), no crash
        assertNull(service.getRepoConfigByKey("future-type-repo"));

        // and: the blob was never written back to the database
        verify(storageService, never()).bulkUpdateRepositoryConfigs(any(), any());
    }

    @Test
    void testStartupWithUnknownRepoTypeFallsBackToGenericWhenSkipIsDisabled() {
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.repoConfigSkipUnknownPackageType.getPropertyName(), "false");
        try {
            LocalRepoConfig knownRepo = LocalRepoConfig.create("known-repo", Maven);
            DbRepositoryConfig goodBlob = repositoryConfigMapper.repositoryConfigToDbRepositoryConfig(knownRepo);
            DbRepositoryConfig futureTypeBlob = unknownTypeBlob("future-type-repo", "SkillsFuture");

            when(storageService.getAllRepositories()).thenReturn(List.of(goodBlob, futureTypeBlob));

            // when
            RepositoryConfigServiceImpl service = createService();

            // then: the unknown-type repo is accessible, coerced to Generic in memory
            RepoConfig futureResult = service.getRepoConfigByKey("future-type-repo");
            assertNotNull(futureResult);
            assertEquals(Generic, futureResult.getPackageType());

            // and: the coerced Generic blob was never written back to the database
            verify(storageService, never()).bulkUpdateRepositoryConfigs(any(), any());
        } finally {
            ArtifactoryHome.get().getArtifactoryProperties()
                    .setProperty(ConstantValues.repoConfigSkipUnknownPackageType.getPropertyName(), "true");
        }
    }

    @Test
    void testBasicRepoConfigsCacheSyncSkipsUnknownPackageType() {
        // The lightweight basics loader (loadAllBasicRepoConfigsFromDb) is the data source for the periodic cache
        // sync. It reads the package_type column directly, bypassing the blob reader. With the skip policy enabled
        // (the default), an unknown-type repo must NOT be re-added to the basics cache; otherwise it would surface in
        // repo-key listings as Generic even though the full-config cache correctly excludes it.
        DbRepositoryConfig knownRow = repositoryConfigMapper.repositoryConfigToDbRepositoryConfig(
                LocalRepoConfig.create("known-repo", Maven));
        DbRepositoryConfig unknownRow = unknownTypeBlob("future-type-repo", "SkillsFuture");

        when(storageService.getAllRepositories()).thenReturn(List.of());
        when(storageService.getAllBasicRepoConfigs()).thenReturn(Set.of(knownRow, unknownRow));

        RepositoryConfigServiceImpl service = createService();

        Map<?, ?> basics = (Map<?, ?>) ReflectionTestUtils.invokeMethod(service, "loadAllBasicRepoConfigsFromDb");

        assertNotNull(basics);
        assertTrue(basics.containsKey("known-repo"), "known-type repo must be present in the basics cache");
        assertFalse(basics.containsKey("future-type-repo"),
                "unknown-type repo must be skipped from the basics cache when the skip policy is enabled");
    }

    @Test
    void testBasicRepoConfigsCacheSyncKeepsUnknownPackageTypeWhenSkipIsDisabled() {
        // With the skip policy disabled, the basics loader keeps the unknown-type row (loaded as Generic via the
        // column mapper), mirroring the read-path load-as-Generic behavior.
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.repoConfigSkipUnknownPackageType.getPropertyName(), "false");
        try {
            DbRepositoryConfig knownRow = repositoryConfigMapper.repositoryConfigToDbRepositoryConfig(
                    LocalRepoConfig.create("known-repo", Maven));
            DbRepositoryConfig unknownRow = unknownTypeBlob("future-type-repo", "SkillsFuture");

            when(storageService.getAllRepositories()).thenReturn(List.of());
            when(storageService.getAllBasicRepoConfigs()).thenReturn(Set.of(knownRow, unknownRow));

            RepositoryConfigServiceImpl service = createService();

            Map<?, ?> basics = (Map<?, ?>) ReflectionTestUtils.invokeMethod(service, "loadAllBasicRepoConfigsFromDb");

            assertNotNull(basics);
            assertTrue(basics.containsKey("known-repo"), "known-type repo must be present in the basics cache");
            assertTrue(basics.containsKey("future-type-repo"),
                    "unknown-type repo must be kept in the basics cache when the skip policy is disabled");
        } finally {
            ArtifactoryHome.get().getArtifactoryProperties()
                    .setProperty(ConstantValues.repoConfigSkipUnknownPackageType.getPropertyName(), "true");
        }
    }

    @Test
    void testGetAllRepoConfigsSkipCacheThrowsOnCorruptBlob() {
        // A corrupt blob of a known package type must propagate, not be silently skipped, so
        // data corruption surfaces rather than the repo appearing to be absent. Unknown package types are a
        // separate, handled case (see testStartupWithUnknownRepoTypeSkipsRepo) and are not exercised here.
        DbRepositoryConfig goodBlob = repositoryConfigMapper.repositoryConfigToDbRepositoryConfig(
                LocalRepoConfig.create("good-repo", Maven));
        DbRepositoryConfig corruptBlob = repositoryConfigMapper.repositoryConfigToDbRepositoryConfig(
                LocalRepoConfig.create("corrupt-repo", Generic));
        corruptBlob.setRepositoryInfoBlob("{ not a valid repo config blob".getBytes(StandardCharsets.UTF_8));

        when(storageService.getAllRepositories()).thenReturn(List.of(goodBlob, corruptBlob));

        // The corrupt blob must surface as an exception on the read path, whether the cache loads eagerly at
        // construction or lazily on the first read.
        assertThrows(IllegalStateException.class, () -> {
            RepositoryConfigServiceImpl service = createService();
            service.getAllRepoConfigsSkipCache();
        });
    }
}
