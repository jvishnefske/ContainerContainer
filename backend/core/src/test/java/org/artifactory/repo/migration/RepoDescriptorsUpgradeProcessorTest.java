package org.artifactory.repo.migration;

import com.jfrog.sysconf.SysConfig;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.HaAddon;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.config.CentralConfigDescriptorImpl;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.repo.ChecksumPolicyTypeDescriptor;
import org.artifactory.descriptor.repo.FederatedRepoDescriptor;
import org.artifactory.descriptor.repo.HttpRepoDescriptor;
import org.artifactory.descriptor.repo.LocalRepoChecksumPolicyTypeDescriptor;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.RemoteRepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.descriptor.repo.SnapshotVersionBehaviorDescriptor;
import org.artifactory.descriptor.repo.VirtualRepoDescriptor;
import org.artifactory.descriptor.repo.releasebundle.ReleaseBundlesRepoDescriptor;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.service.RepoConfigBootstrapStorageHelper;
import org.artifactory.spring.ArtifactoryApplicationContext;
import org.artifactory.storage.db.DbServiceImpl;
import org.artifactory.storage.db.InternalDbService;
import org.artifactory.storage.fs.service.ConfigsService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RepoDescriptorsUpgradeProcessorTest extends ArtifactoryHomeBoundJUnitTest {
    private ArtifactoryApplicationContext artifactoryContext;

    @BeforeMethod
    public void setUp() {
        artifactoryContext = mock(ArtifactoryApplicationContext.class);
        AddonsManager addonsManager = mock(AddonsManager.class);
        when(artifactoryContext.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        when(addonsManager.addonByType(HaAddon.class)).thenReturn(mock(HaAddon.class));
        InternalDbService internalDbService = spy(new DbServiceImpl());
        when(artifactoryContext.beanForType(InternalDbService.class)).thenReturn(internalDbService);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);

        ArtifactoryHome artifactoryHome = mock(ArtifactoryHome.class);
        SysConfig sysConfig = mock(SysConfig.class);
        when(artifactoryHome.getSysConfig()).thenReturn(sysConfig);
        ArtifactoryHome.bind(artifactoryHome);
    }

    @AfterMethod
    private void cleanup() {
        ArtifactoryContextThreadBinder.unbind();
        ArtifactoryHome.unbind();
    }

    @Test
    public void testUpgradeRepositories() {
        // GIVEN
        MutableCentralConfigDescriptor centralConfigDescriptor = createCentralConfigDescriptor();

        CentralConfigService centralConfigService = mock(CentralConfigService.class);
        when(centralConfigService.getDescriptor()).thenReturn(centralConfigDescriptor);

        ConfigsService configsService = mock(ConfigsService.class);

        RepoConfigBootstrapStorageHelper repoConfigBootstrapStorageHelper =
                mock(RepoConfigBootstrapStorageHelper.class);
        when(repoConfigBootstrapStorageHelper.countAll()).thenReturn(5);
        Consumer<CentralConfigDescriptor> updateCentralConfigOperator = mock(Consumer.class);

        RepoDescriptorsUpgradeProcessor
                repositoriesMigrationService = spy(new RepoDescriptorsUpgradeProcessor(
                updateCentralConfigOperator, configsService, Set.of(),
                repoConfigBootstrapStorageHelper, artifactoryContext));

        // WHEN
        repositoriesMigrationService.doUpgradeIfNeeded(centralConfigDescriptor);

        // THEN
        // 1. All required steps are called
        verify(configsService, times(1)).backupConfig(any(String.class), any(Long.class));
        verify(repoConfigBootstrapStorageHelper, times(1)).deleteAll();

        // 2. All repo descriptors were mapped into db entity
        ArgumentCaptor<Set<RepoConfig>> dbRepoConfigs = ArgumentCaptor.forClass(Set.class);
        verify(repoConfigBootstrapStorageHelper, times(1)).bulkInsertRepositoryConfigs(dbRepoConfigs.capture(),
                eq(true));
        assertEquals(dbRepoConfigs.getValue().size(), 5);

        verify(repositoriesMigrationService, times(1)).verifyMigratedRepoConfigCount(any(Integer.class));
        verify(repositoriesMigrationService, times(1)).cleanupConfigDescriptorRepositories(any());

        ArgumentCaptor<CentralConfigDescriptor> centralConfigBeforeSave =
                ArgumentCaptor.forClass(CentralConfigDescriptor.class);
        verify(updateCentralConfigOperator, times(1)).accept(centralConfigBeforeSave.capture());

        // 3. Config descriptor does not have repositories before saving
        assertTrue(centralConfigBeforeSave.getValue().getLocalRepositoriesMap().isEmpty());
        assertTrue(centralConfigBeforeSave.getValue().getVirtualRepositoriesMap().isEmpty());
        assertTrue(centralConfigBeforeSave.getValue().getRemoteRepositoriesMap().isEmpty());
        assertTrue(centralConfigBeforeSave.getValue().getFederatedRepositoriesMap().isEmpty());
        assertTrue(centralConfigBeforeSave.getValue().getReleaseBundlesRepositoriesMap().isEmpty());
    }

    @Test
    public void testVerifyResults() {
        MutableCentralConfigDescriptor centralConfigDescriptor = mock(MutableCentralConfigDescriptor.class);
        Map<String, LocalRepoDescriptor> localRepos = Map.of("local1", mock(LocalRepoDescriptor.class));
        when(centralConfigDescriptor.getLocalRepositoriesMap()).thenReturn(localRepos);

        Map<String, VirtualRepoDescriptor> virtualRepos = IntStream.range(0, 2)
                .mapToObj(Integer::toString)
                .collect(Collectors.toMap(index -> index, index -> mock(VirtualRepoDescriptor.class)));
        when(centralConfigDescriptor.getVirtualRepositoriesMap()).thenReturn(virtualRepos);

        Map<String, RemoteRepoDescriptor> remoteRepos = IntStream.range(0, 3)
                .mapToObj(Integer::toString)
                .collect(Collectors.toMap(index -> index, index -> mock(RemoteRepoDescriptor.class)));
        when(centralConfigDescriptor.getRemoteRepositoriesMap()).thenReturn(remoteRepos);

        Map<String, FederatedRepoDescriptor> fedRepos = IntStream.range(0, 4)
                .mapToObj(Integer::toString)
                .collect(Collectors.toMap(index -> index, index -> mock(FederatedRepoDescriptor.class)));
        when(centralConfigDescriptor.getFederatedRepositoriesMap()).thenReturn(fedRepos);

        Map<String, ReleaseBundlesRepoDescriptor> releaseBundlesRepos = IntStream.range(0, 5)
                .mapToObj(Integer::toString)
                .collect(Collectors.toMap(index -> index, index -> mock(ReleaseBundlesRepoDescriptor.class)));
        when(centralConfigDescriptor.getReleaseBundlesRepositoriesMap()).thenReturn(releaseBundlesRepos);

        CentralConfigService centralConfigService = mock(CentralConfigService.class);
        when(centralConfigService.getDescriptor()).thenReturn(centralConfigDescriptor);

        RepoConfigBootstrapStorageHelper repoConfigBootstrapStorageHelper =
                mock(RepoConfigBootstrapStorageHelper.class);
        when(repoConfigBootstrapStorageHelper.countAll()).thenReturn(15);

        RepoDescriptorsUpgradeProcessor repoDescriptorsUpgradeProcessor =
                new RepoDescriptorsUpgradeProcessor(null, null, null, repoConfigBootstrapStorageHelper, null);

        int totalRepoDescriptors = ConfigDescriptorRepoUtils.getRepoDescriptorTotalCount(centralConfigDescriptor);
        repoDescriptorsUpgradeProcessor.verifyMigratedRepoConfigCount(totalRepoDescriptors);
    }

    @Test(expectedExceptions = IllegalStateException.class, expectedExceptionsMessageRegExp = "Migrated repositories " +
            "count is less than repositories count in the system")
    public void testVerifyResultsWhenNotAllReposAreMigrated() {
        MutableCentralConfigDescriptor centralConfigDescriptor = mock(MutableCentralConfigDescriptor.class);

        Map<String, VirtualRepoDescriptor> virtualRepos = IntStream.range(0, 2)
                .mapToObj(Integer::toString)
                .collect(Collectors.toMap(index -> index, index -> mock(VirtualRepoDescriptor.class)));
        when(centralConfigDescriptor.getVirtualRepositoriesMap()).thenReturn(virtualRepos);

        CentralConfigService centralConfigService = mock(CentralConfigService.class);
        when(centralConfigService.getDescriptor()).thenReturn(centralConfigDescriptor);

        RepoConfigBootstrapStorageHelper repoConfigBootstrapStorageHelper =
                mock(RepoConfigBootstrapStorageHelper.class);
        when(repoConfigBootstrapStorageHelper.countAll()).thenReturn(1);

        RepoDescriptorsUpgradeProcessor repoDescriptorsUpgradeProcessor =
                new RepoDescriptorsUpgradeProcessor(null, null, null, repoConfigBootstrapStorageHelper, null);

        int totalRepoDescriptors = ConfigDescriptorRepoUtils.getRepoDescriptorTotalCount(centralConfigDescriptor);
        repoDescriptorsUpgradeProcessor.verifyMigratedRepoConfigCount(totalRepoDescriptors);
    }

    @Test
    public void testVerifyResultsWhenDbTableContainsMoreReposThanConfigDescriptor() {
        MutableCentralConfigDescriptor centralConfigDescriptor = mock(MutableCentralConfigDescriptor.class);

        Map<String, VirtualRepoDescriptor> virtualRepos = IntStream.range(0, 2)
                .mapToObj(Integer::toString)
                .collect(Collectors.toMap(index -> index, index -> mock(VirtualRepoDescriptor.class)));
        when(centralConfigDescriptor.getVirtualRepositoriesMap()).thenReturn(virtualRepos);

        CentralConfigService centralConfigService = mock(CentralConfigService.class);
        when(centralConfigService.getDescriptor()).thenReturn(centralConfigDescriptor);

        RepoConfigBootstrapStorageHelper repoConfigBootstrapStorageHelper =
                mock(RepoConfigBootstrapStorageHelper.class);
        when(repoConfigBootstrapStorageHelper.countAll()).thenReturn(5);

        RepoDescriptorsUpgradeProcessor repoDescriptorsUpgradeProcessor =
                new RepoDescriptorsUpgradeProcessor(null, null, null, repoConfigBootstrapStorageHelper, null);

        int totalRepoDescriptors = ConfigDescriptorRepoUtils.getRepoDescriptorTotalCount(centralConfigDescriptor);
        repoDescriptorsUpgradeProcessor.verifyMigratedRepoConfigCount(totalRepoDescriptors);
    }

    @Test
    public void testDeleteExistingRepoConfigsIfNeeded() {
        RepoConfigBootstrapStorageHelper repoConfigBootstrapStorageHelper =
                mock(RepoConfigBootstrapStorageHelper.class);
        when(repoConfigBootstrapStorageHelper.countAll()).thenReturn(1);

        RepoDescriptorsUpgradeProcessor repoDescriptorsUpgradeProcessor =
                new RepoDescriptorsUpgradeProcessor(null, null, null, repoConfigBootstrapStorageHelper, null);

        repoDescriptorsUpgradeProcessor.deleteExistingRepoConfigs();

        verify(repoConfigBootstrapStorageHelper, times(1)).deleteAll();
    }

    private MutableCentralConfigDescriptor createCentralConfigDescriptor() {
        MutableCentralConfigDescriptor centralConfigDescriptor = new CentralConfigDescriptorImpl();
        String localKey1 = "local1";
        LocalRepoDescriptor localRepoDescriptor = new LocalRepoDescriptor();
        localRepoDescriptor.setKey(localKey1);
        localRepoDescriptor.setSnapshotVersionBehavior(SnapshotVersionBehaviorDescriptor.UNIQUE);
        localRepoDescriptor.setChecksumPolicyType(LocalRepoChecksumPolicyTypeDescriptor.CLIENT);
        localRepoDescriptor.setType(RepoType.Alpine);
        Map<String, LocalRepoDescriptor> localRepos = Map.of(localKey1, localRepoDescriptor);
        centralConfigDescriptor.setLocalRepositoriesMap(localRepos);

        String virtualKey11 = "virtual11";
        VirtualRepoDescriptor virtualRepoDescriptor = new VirtualRepoDescriptor();
        virtualRepoDescriptor.setKey(virtualKey11);
        virtualRepoDescriptor.setType(RepoType.Cargo);
        Map<String, VirtualRepoDescriptor> virtualRepos = Map.of(virtualKey11, virtualRepoDescriptor);
        centralConfigDescriptor.setVirtualRepositoriesMap(virtualRepos);

        String remoteKey1 = "remote1";
        HttpRepoDescriptor remoteRepoDescriptor = new HttpRepoDescriptor();
        remoteRepoDescriptor.setKey(remoteKey1);
        remoteRepoDescriptor.setType(RepoType.CocoaPods);
        remoteRepoDescriptor.setChecksumPolicyType(ChecksumPolicyTypeDescriptor.PASS_THRU);
        Map<String, RemoteRepoDescriptor> remoteRepos = Map.of(remoteKey1, remoteRepoDescriptor);
        centralConfigDescriptor.setRemoteRepositoriesMap(remoteRepos);

        String fedKey1 = "fed1";
        FederatedRepoDescriptor fedRepoDescriptor = new FederatedRepoDescriptor();
        fedRepoDescriptor.setKey(fedKey1);
        fedRepoDescriptor.setType(RepoType.Conda);
        fedRepoDescriptor.setSnapshotVersionBehavior(SnapshotVersionBehaviorDescriptor.UNIQUE);
        fedRepoDescriptor.setChecksumPolicyType(LocalRepoChecksumPolicyTypeDescriptor.CLIENT);
        Map<String, FederatedRepoDescriptor> fedRepos = Map.of(fedKey1, fedRepoDescriptor);
        centralConfigDescriptor.setFederatedRepositoriesMap(fedRepos);

        String bundleKey1 = "bundle1";
        ReleaseBundlesRepoDescriptor releaseBundlesRepoDescriptor = new ReleaseBundlesRepoDescriptor();
        releaseBundlesRepoDescriptor.setKey(bundleKey1);
        releaseBundlesRepoDescriptor.setType(RepoType.Ivy);
        releaseBundlesRepoDescriptor.setSnapshotVersionBehavior(SnapshotVersionBehaviorDescriptor.UNIQUE);
        releaseBundlesRepoDescriptor.setChecksumPolicyType(LocalRepoChecksumPolicyTypeDescriptor.CLIENT);
        Map<String, ReleaseBundlesRepoDescriptor> bundleRepos = Map.of(bundleKey1, releaseBundlesRepoDescriptor);
        centralConfigDescriptor.setReleaseBundlesRepositoriesMap(bundleRepos);
        return centralConfigDescriptor;
    }

    @DataProvider
    private static Object[][] emptyIdentifierDataProvider() {
        return new Object[][] {
                {null},
                {" "},
                {""}
        };
    }

    @DataProvider
    private static Object[][] invalidIdentifierDataProvider() {
        return new Object[][] {
                {null},
                {" "},
                {""},
                {"hello-hello"},
                {"hello_hello_hello"}
        };
    }
}