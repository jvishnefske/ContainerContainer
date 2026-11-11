package org.artifactory.metrics.providers.features;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.ArtifactoryRunningMode;
import org.artifactory.api.callhome.FeatureGroup;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.storage.RepoStorageSummaryInfo;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.descriptor.repo.VirtualRepoDescriptor;
import org.artifactory.jaxb.JaxbHelper;
import org.artifactory.model.RealRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.repo.LocalCacheRepo;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.RemoteRepo;
import org.artifactory.repo.Repo;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.repo.virtual.VirtualRepo;
import org.artifactory.storage.StorageSummaryInfo;
import org.artifactory.storage.StorageSummaryService;
import org.artifactory.util.RepoLayoutUtils;
import org.jfrog.storage.binstore.BinariesInfo;
import org.json.JSONException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.artifactory.api.repo.storage.RepoStorageSummaryInfo.RepositoryType.CACHE;
import static org.artifactory.api.repo.storage.RepoStorageSummaryInfo.RepositoryType.LOCAL;
import static org.artifactory.descriptor.repo.LocalCacheRepoDescriptor.PATH_SUFFIX;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * This test uses XML and JSON serialization to check that given a predefined configuration,
 * the {@link RepositoriesFeature} produces expected JSON output (kind of "snapshot" testing).
 *
 * <p>
 * Initializing configuration from XML is much easier* than mocking / assigning endless properties.
 * Likewise, verifying the resulting JSON is more valuable than checking  Java properties and getting
 * the wrong result at the end, just because Jackson annotations were wrong.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class RepositoriesFeatureSerializationTest {
    private static final ObjectMapper mapper = JsonMapper.builder()
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
            .build();

    // language=XML
    private static final String DEFAULT_CONFIG = """
        <?xml version="1.0" encoding="UTF-8"?>
        <config xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                xmlns="http://artifactory.jfrog.org/xsd/3.5.11"
                xsi:schemaLocation="http://artifactory.jfrog.org/xsd/3.5.11
                https://releases.jfrog.io/artifactory/static-release-virtual/xsd/artifactory/artifactory-v3_5_11.xsd">
            <trashcanConfig>
                <enabled>true</enabled>
                <allowPermDeletes>false</allowPermDeletes>
                <retentionPeriodDays>14</retentionPeriodDays>
            </trashcanConfig>
            <folderDownloadConfig>
                <enabled>false</enabled>
                <enabledForAnonymous>false</enabledForAnonymous>
                <maxDownloadSizeMb>1024</maxDownloadSizeMb>
                <maxFiles>5000</maxFiles>
                <maxConcurrentRequests>10</maxConcurrentRequests>
                <enabledEmptyDirectories>false</enabledEmptyDirectories>
            </folderDownloadConfig>
            <localReplications>
                <localReplication>
                    <enabled>true</enabled>
                    <syncDeletes>true</syncDeletes>
                    <syncProperties>true</syncProperties>
                    <enableEventReplication>true</enableEventReplication>
                    <repoKey>test-npm-local</repoKey>
                    <replicationKey>test-npm-local</replicationKey>
                </localReplication>
            </localReplications>
            <remoteReplications>
                <remoteReplication>
                    <enabled>true</enabled>
                    <cronExp>0 0 12 * * ?</cronExp>
                    <syncDeletes>true</syncDeletes>
                    <syncProperties>true</syncProperties>
                    <repoKey>remote-repo</repoKey>
                </remoteReplication>
            </remoteReplications>
        </config>
            """;
    @Mock
    private CentralConfigService configService;
    @Mock
    private InternalRepositoryService repositoryService;
    @Mock
    private StorageSummaryService storageSummaryService;
    @Mock
    private AddonsManager addonsManager;
    private RepositoriesFeature repositoriesFeature;
    private StorageSummaryInfo storageSummary;

    @BeforeEach
    void beforeEachTest() {
        // RepositoriesFeature dynamically retrieves the addon manager bean (why not inject it?)
        // and then uses it to check if we are running in the JCR mode.
        ArtifactoryContextThreadBinder.bind(mock(ArtifactoryContext.class));
        when(ContextHelper.get().beanForType(AddonsManager.class)).thenReturn(addonsManager);
        when(addonsManager.getArtifactoryRunningMode()).thenReturn(ArtifactoryRunningMode.PRO);

        when(storageSummaryService.get()).thenAnswer((x) -> this.storageSummary);

        this.repositoriesFeature = new RepositoriesFeature(configService, repositoryService, storageSummaryService);
    }

    @AfterEach
    void tearDown() {
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void testRepositoryFeatureSerialization() throws JsonProcessingException, JSONException {
        // Arrange
        useConfiguration(DEFAULT_CONFIG);
        // language=JSON
        useRepositories("""
                [
                    { "type": "local", "key": "example-local", "packageType": "generic" },
                    { "type": "local", "key": "test-npm-local", "packageType": "npm" },
                    { "type": "remote", "key": "test-npm-remote", "packageType": "npm" },
                    {
                      "type": "virtual", "key": "test-npm", "packageType": "npm",
                      "baseConfig": { "repoLayoutRef": "simple-default" },
                      "repoTypeConfig": { "repositoryRefs": [ "test-npm-local", "test-npm-remote" ] },
                      "securityConfig": {
                        "xrayConfig": { "enabled": true, "dataTtl": 10, "xrayNotAvailableMode": "fail_open" }
                      }
                    }
                ]
                """);
        useRepoStorageSizes(
                new RepoStorageSummaryInfo("test-npm-local", LOCAL, 15, 100, 123_456, "npm", "default"),
                new RepoStorageSummaryInfo("test-npm-remote-cache", CACHE, 7, 75, 100_000, " npm", "default")
        );

        // Act
        FeatureGroup feature = this.repositoriesFeature.getFeature();

        // Assert: compare the resulting JSON with the expected one. The JSON is really long,
        // but it explicitly states how the outcome should look like (when sending it to JFrog as
        // part of the CallHome communication).
        String actual = mapper.writeValueAsString(feature);
        JSONAssert.assertEquals("""
                {
                  "name": "repositories",
                  "features": [
                    {
                      "name": "Trashcan",
                      "attributes": {
                        "enabled": true,
                        "retention_period": 14
                      }
                    },
                    {
                      "name": "Folder Download",
                      "attributes": {
                        "enabled": false,
                        "enabledForAnonymous": false,
                        "max_size": 1024,
                        "max_files": 5000,
                        "max_parallel_downloads": 10,
                        "enabledEmptyDirectories": false
                      }
                    },
                    {
                      "name": "local repositories",
                      "attributes": {
                        "number_of_repositories": 2
                      },
                      "features": [
                        {
                          "name": "example-local",
                          "attributes": {
                            "package_type": "Generic",
                            "push_replication": false,
                            "event_replication": false,
                            "sync_deleted": false,
                            "xray_enabled": false
                          }
                        },
                        {
                          "name": "test-npm-local",
                          "attributes": {
                            "package_type": "Npm",
                            "push_replication": "true",
                            "event_replication": true,
                            "sync_properties": true,
                            "sync_deleted": true,
                            "xray_enabled": false,
                            "total_files": 100,
                            "total_folders": 15,
                            "used_space_bytes": 123456
                          }
                        }
                      ]
                    },
                    {
                      "name": "federated repositories",
                      "attributes": {
                        "number_of_repositories": 0
                      }
                    },
                    {
                      "name": "remote repositories",
                      "attributes": {
                        "number_of_repositories": 1
                      },
                      "features": [
                        {
                          "name": "test-npm-remote",
                          "attributes": {
                            "package_type": "Npm",
                            "pull_replication": false,
                            "xray_enabled": false,
                            "total_cache_files": 75,
                            "total_cache_folders": 7,
                            "used_cache_space_bytes": 100000
                          }
                        }
                      ]
                    },
                    {
                      "name": "distribution repositories"
                    },
                    {
                      "name": "virtual repositories",
                      "attributes": {
                        "number_of_repositories": 1
                      },
                      "features": [
                        {
                          "name": "test-npm",
                          "attributes": {
                            "package_type": "Npm",
                            "number_of_included_repositories": 2,
                            "total_nested_local_repositories": 1,
                            "total_nested_remote_repositories": 1,
                            "repository_layout": "simple-default",
                            "xray_enabled": true,
                            "xray_data_ttl": 10,
                            "xray_failure_mode": "fail_open",
                            "total_files": 175,
                            "total_folders": 22,
                            "used_space_bytes": 223456,
                            "total_cache_files": 75,
                            "total_cache_folders": 7,
                            "used_cache_space_bytes": 100000
                          }
                        }
                      ]
                    }
                  ]
                }
                """, actual, JSONCompareMode.NON_EXTENSIBLE);
    }

    //#region ---------- Mocking and serialization helpers ----------
    void useConfiguration(String configXml) {
        CentralConfigDescriptor configDescriptor = JaxbHelper.readConfig(configXml, false);
        when(configService.getDescriptor()).thenReturn(configDescriptor);
    }

    void useRepositories(String repositoriesJson) throws JsonProcessingException {
        // Parse JSON with repository definitions
        RepoConfig[] repositories = mapper.readValue(repositoriesJson, RepoConfig[].class);

        // Split all definitions to two groups - "real" and "virtual", also keep a map of repo config by name
        List<RealRepoConfig> repoConfigWithoutCache = new ArrayList<>();
        List<VirtualRepoConfig> virtualRepoConfigs = new ArrayList<>();
        Map<String, RepoConfig> repoConfigByName = new HashMap<>();

        for (RepoConfig repoConfig : repositories) {
            repoConfigByName.put(repoConfig.getKey(), repoConfig);
            if (repoConfig instanceof RealRepoConfig realRepoConfig) {
                if (!realRepoConfig.isCache()) {
                    repoConfigWithoutCache.add(realRepoConfig);
                }
            } else if (repoConfig instanceof VirtualRepoConfig virtualConfig) {
                virtualRepoConfigs.add(virtualConfig);
            }
        }

        // Expose real (non-virtual) repository configurations via repository service mock
        when(repositoryService.getLocalFederatedAndRemoteRepoConfigs()).thenReturn(repoConfigWithoutCache);

        // Prepare virtual directories (if any)
        if (!virtualRepoConfigs.isEmpty()) {
            Map<String, VirtualRepoDescriptor> aggregatedVirtualRepoDescriptorMap = new HashMap<>();
            Map<String, VirtualRepoConfig> virtualReposMap = new HashMap<>();
            Map<String, RepoDescriptor> realReposMap = new HashMap<>();
            List<VirtualRepo> virtualRepositories = new ArrayList<>();

            for (VirtualRepoConfig config : virtualRepoConfigs) {
                VirtualRepoDescriptor descriptor = new VirtualRepoDescriptor(
                        config,
                        List.of(RepoLayoutUtils.SIMPLE_DEFAULT), // List<RepoLayout> repoLayouts,
                        aggregatedVirtualRepoDescriptorMap,
                        virtualReposMap, // Map<String, VirtualRepoConfig> virtualReposMap
                        realReposMap, // Map<String, RepoDescriptor > realReposMap,
                        List.of(), // List<KeyPairDescriptor> keyPairs,
                        List.of(), // List<PropertySet> propertySets,
                        List.of() // List<ProxyDescriptor> proxies
                );

                VirtualRepo virtualRepoMock = mockVirtualRepo(config, descriptor, repoConfigByName);
                virtualRepositories.add(virtualRepoMock);
            }

            when(repositoryService.getVirtualRepositories()).thenReturn(virtualRepositories);
        }
    }

    private static VirtualRepo mockVirtualRepo(VirtualRepoConfig repoConfig, VirtualRepoDescriptor descriptor,
            Map<String, RepoConfig> repositoryConfigs) {
        VirtualRepo virtualRepo = mock(VirtualRepo.class);
        when(virtualRepo.getKey()).thenReturn(repoConfig.getKey());
        when(virtualRepo.getRepoConfig()).thenReturn(repoConfig);
        when(virtualRepo.getDescriptor()).thenReturn(descriptor);

        Set<LocalRepo> localRepos = new HashSet<>();
        Set<RemoteRepo> remoteRepos = new HashSet<>();
        Set<LocalRepo> localAndCacheRepos = new HashSet<>();
        for (String memberRef : repoConfig.getRepoTypeConfig().getRepositoryRefs()) {
            RepoConfig memberConfig = repositoryConfigs.get(memberRef);
            String repoKey = memberConfig.getKey();

            if (memberConfig != null) {
                if (memberConfig.getRepoType() == RepoDetailsType.REMOTE) {
                    remoteRepos.add(mockRepo(repoKey, RemoteRepo.class));
                    localAndCacheRepos.add(mockRepo(repoKey + PATH_SUFFIX, LocalCacheRepo.class));
                } else if (memberConfig.getRepoType() == RepoDetailsType.LOCAL ||
                        memberConfig.getRepoType() == RepoDetailsType.FEDERATED) {
                    localRepos.add(mockRepo(repoKey, LocalRepo.class));
                }
            }
        }
        localAndCacheRepos.addAll(localRepos);

        when(virtualRepo.getResolvedLocalAndFederatedRepos()).thenReturn(localRepos);
        when(virtualRepo.getResolvedRemoteRepos()).thenReturn(remoteRepos);
        when(virtualRepo.getResolvedLocalFederatedAndCachedRepos()).thenReturn(localAndCacheRepos);
        return virtualRepo;
    }

    static <T extends Repo> T mockRepo(String key, Class<? extends T> clazz) {
        T repoMock = mock(clazz, withSettings().lenient());
        when(repoMock.getKey()).thenReturn(key);
        if (LocalCacheRepo.class.isAssignableFrom(clazz)) {
            when(repoMock.isCache()).thenReturn(true);
        }
        return repoMock;
    }

    private void useRepoStorageSizes(RepoStorageSummaryInfo... summaries) {
        Map<String, RepoStorageSummaryInfo> repoStorageSummaries = Arrays.stream(summaries).collect(Collectors.toMap(
                RepoStorageSummaryInfo::getRepoKey, Function.identity())
        );

        this.storageSummary = new StorageSummaryInfo(repoStorageSummaries, new BinariesInfo(), Map.of(), Map.of());
    }
    //#endregion
}
