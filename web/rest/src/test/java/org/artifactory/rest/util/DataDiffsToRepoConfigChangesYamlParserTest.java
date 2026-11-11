package org.artifactory.rest.util;

import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.federation.GridTopologyService;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.GridTopology;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.common.ConstantValues;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.common.config.diff.DataDiff;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.getField;

/**
 * Tests for DataDiffsToRepoConfigChangesYamlParser, specifically for federation detection
 * when creating repositories from YAML configuration.
 */
class DataDiffsToRepoConfigChangesYamlParserTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private ArtifactoryContext context;

    @Mock
    private RepositoryConfigService repositoryConfigService;

    @Mock
    private CentralConfigService centralConfigService;

    @Mock
    private CentralConfigDescriptor centralConfigDescriptor;

    @Mock
    private GridTopologyService gridTopologyService;

    private AutoCloseable mocks;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        // Enable grid by default - must set in BOTH caches:
        // 1. artifactoryProperties (String EnumMap) - used by getProperty(ConstantValues)
        // 2. artifactoryBooleanProperties (Boolean EnumMap) - used by getBooleanProperty() cache
        Object props = homeStub.getArtifactoryProperties();
        ((EnumMap<ConstantValues, String>) getField(props, "artifactoryProperties"))
                .put(ConstantValues.gridEnabled, "true");
        ((EnumMap<ConstantValues, Boolean>) getField(props, "artifactoryBooleanProperties"))
                .put(ConstantValues.gridEnabled, Boolean.TRUE);
        ArtifactoryContextThreadBinder.bind(context);
        when(context.beanForType(RepositoryConfigService.class)).thenReturn(repositoryConfigService);
        when(context.beanForType(GridTopologyService.class)).thenReturn(gridTopologyService);
        when(context.getCentralConfig()).thenReturn(centralConfigService);
        when(centralConfigService.getDescriptor()).thenReturn(centralConfigDescriptor);
        when(centralConfigDescriptor.getRepoLayouts()).thenReturn(List.of());
        when(centralConfigDescriptor.getProxies()).thenReturn(List.of());
        when(repositoryConfigService.getRepoConfigsByKeys(anySet())).thenReturn(Set.of());
    }

    @AfterEach
    void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"gridTopologyId", "members"})
    void parse_shouldCreateFederatedRepoConfig_whenLocalRepoHasFederationTrigger(String federationField) {
        Set<DataDiff<?>> diffs = new HashSet<>();
        diffs.add(new DataDiff<>("localRepositories»fed-local-repo»type", null, "generic"));

        if ("gridTopologyId".equals(federationField)) {
            // Mock MIRROR topology - triggers federation
            GridTopology mirrorTopology = new GridTopology();
            mirrorTopology.setGridTopologyId("topology-123");
            mirrorTopology.setTopologyType(GridTopology.TopologyType.MIRROR);
            when(gridTopologyService.getGridTopologyById("topology-123")).thenReturn(mirrorTopology);
            diffs.add(new DataDiff<>("localRepositories»fed-local-repo»gridTopologyId", null, "topology-123"));
        } else {
            diffs.add(new DataDiff<>("localRepositories»fed-local-repo»members»url", null,
                    "http://site1.example.com/artifactory/fed-local-repo"));
        }

        DataDiffsToRepoConfigChangesYamlParser.RepoConfigChanges changes =
                DataDiffsToRepoConfigChangesYamlParser.parse(diffs);

        assertEquals(1, changes.repoConfigsToUpsert().size());
        RepoConfig repoConfig = changes.repoConfigsToUpsert().get(0);
        assertInstanceOf(FederatedRepoConfig.class, repoConfig);
        assertEquals("fed-local-repo", repoConfig.getKey());
    }

    @Test
    void parse_shouldCreateLocalRepoConfig_whenLocalRepoHasNoFederationConfig() {
        Set<DataDiff<?>> diffs = new HashSet<>();
        diffs.add(new DataDiff<>("localRepositories»standard-local-repo»type", null, "generic"));
        diffs.add(new DataDiff<>("localRepositories»standard-local-repo»description", null, "A standard local repo"));

        DataDiffsToRepoConfigChangesYamlParser.RepoConfigChanges changes =
                DataDiffsToRepoConfigChangesYamlParser.parse(diffs);

        assertEquals(1, changes.repoConfigsToUpsert().size());
        RepoConfig repoConfig = changes.repoConfigsToUpsert().get(0);
        assertInstanceOf(LocalRepoConfig.class, repoConfig);
        assertEquals("standard-local-repo", repoConfig.getKey());
    }

    @Test
    void parse_shouldCreateLocalRepoConfig_whenGridTopologyIsSelfDefined() {
        // SELF_DEFINED topology should NOT trigger federation
        GridTopology selfDefinedTopology = new GridTopology();
        selfDefinedTopology.setGridTopologyId("self-topology");
        selfDefinedTopology.setTopologyType(GridTopology.TopologyType.SELF_DEFINED);
        when(gridTopologyService.getGridTopologyById("self-topology")).thenReturn(selfDefinedTopology);

        Set<DataDiff<?>> diffs = new HashSet<>();
        diffs.add(new DataDiff<>("localRepositories»local-repo»type", null, "generic"));
        diffs.add(new DataDiff<>("localRepositories»local-repo»gridTopologyId", null, "self-topology"));

        DataDiffsToRepoConfigChangesYamlParser.RepoConfigChanges changes =
                DataDiffsToRepoConfigChangesYamlParser.parse(diffs);

        assertEquals(1, changes.repoConfigsToUpsert().size());
        RepoConfig repoConfig = changes.repoConfigsToUpsert().get(0);
        assertInstanceOf(LocalRepoConfig.class, repoConfig);
        assertEquals("local-repo", repoConfig.getKey());
    }

    @Test
    void parse_shouldThrowException_whenGridTopologyNotFound() {
        // Mock topology lookup failure
        when(gridTopologyService.getGridTopologyById("non-existent-topology"))
                .thenThrow(new RuntimeException("Grid topology not found"));

        Set<DataDiff<?>> diffs = new HashSet<>();
        diffs.add(new DataDiff<>("localRepositories»local-repo»type", null, "generic"));
        diffs.add(new DataDiff<>("localRepositories»local-repo»gridTopologyId", null, "non-existent-topology"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> DataDiffsToRepoConfigChangesYamlParser.parse(diffs));

        assertTrue(exception.getMessage().contains("Failed to get grid topology"));
        assertTrue(exception.getMessage().contains("non-existent-topology"));
    }

    @Test
    void parse_shouldHandleMixedRepos_localWithAndWithoutFederationConfig() {
        // Mock MIRROR topology for fed-repo
        GridTopology mirrorTopology = new GridTopology();
        mirrorTopology.setGridTopologyId("topo-456");
        mirrorTopology.setTopologyType(GridTopology.TopologyType.MIRROR);
        when(gridTopologyService.getGridTopologyById("topo-456")).thenReturn(mirrorTopology);

        Set<DataDiff<?>> diffs = new HashSet<>();
        diffs.add(new DataDiff<>("localRepositories»local-repo»type", null, "generic"));
        diffs.add(new DataDiff<>("localRepositories»fed-repo»type", null, "maven"));
        diffs.add(new DataDiff<>("localRepositories»fed-repo»gridTopologyId", null, "topo-456"));

        DataDiffsToRepoConfigChangesYamlParser.RepoConfigChanges changes =
                DataDiffsToRepoConfigChangesYamlParser.parse(diffs);

        assertEquals(2, changes.repoConfigsToUpsert().size());

        RepoConfig localRepo = changes.repoConfigsToUpsert().stream()
                .filter(c -> "local-repo".equals(c.getKey()))
                .findFirst()
                .orElseThrow();
        assertInstanceOf(LocalRepoConfig.class, localRepo);

        RepoConfig fedRepo = changes.repoConfigsToUpsert().stream()
                .filter(c -> "fed-repo".equals(c.getKey()))
                .findFirst()
                .orElseThrow();
        assertInstanceOf(FederatedRepoConfig.class, fedRepo);
    }

    @ParameterizedTest(name = "parse {0} → {2}")
    @MethodSource("repoTypesProvider")
    void parse_shouldCreateCorrectRepoConfigType_forYamlSection(
            String yamlSection, String repoKey, Class<? extends RepoConfig> expectedType) {
        Set<DataDiff<?>> diffs = new HashSet<>();
        diffs.add(new DataDiff<>(yamlSection + "»" + repoKey + "»type", null, "generic"));

        DataDiffsToRepoConfigChangesYamlParser.RepoConfigChanges changes =
                DataDiffsToRepoConfigChangesYamlParser.parse(diffs);

        assertEquals(1, changes.repoConfigsToUpsert().size());
        RepoConfig repoConfig = changes.repoConfigsToUpsert().get(0);
        assertInstanceOf(expectedType, repoConfig);
        assertEquals(repoKey, repoConfig.getKey());
    }

    private static Stream<Arguments> repoTypesProvider() {
        return Stream.of(
                // Remote repositories section → RemoteRepoConfig
                Arguments.of("remoteRepositories", "remote-repo", RemoteRepoConfig.class),
                // Virtual repositories section → VirtualRepoConfig
                Arguments.of("virtualRepositories", "virtual-repo", VirtualRepoConfig.class),
                // Federated repositories section → FederatedRepoConfig
                Arguments.of("federatedRepositories", "federated-repo", FederatedRepoConfig.class)
        );
    }
}
