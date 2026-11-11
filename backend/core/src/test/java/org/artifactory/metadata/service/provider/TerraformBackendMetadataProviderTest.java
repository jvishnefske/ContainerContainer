package org.artifactory.metadata.service.provider;

import org.artifactory.fs.FileInfo;
import org.artifactory.md.Properties;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.properties.PropertiesImpl;
import org.artifactory.repo.RepoPath;
import org.artifactory.storage.db.event.service.metadata.model.MutableMetadataEntityBOM;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TerraformBackendMetadataProviderTest {
    private TerraformBackendMetadataProvider terraformBackendMetadataProvider;
    @Mock
    private RepoPath repoPath;


    @BeforeEach
    void setUp() {
        terraformBackendMetadataProvider = spy(new TerraformBackendMetadataProvider());
    }

    @Test
    void testIsLeadArtifact() {
        when(repoPath.getName()).thenReturn("state.json");
        assertFalse(terraformBackendMetadataProvider.isLeadArtifact(repoPath));
        when(repoPath.getName()).thenReturn("notstate.json");
        assertFalse(terraformBackendMetadataProvider.isLeadArtifact(repoPath));
        when(repoPath.getName()).thenReturn("state.latest.json");
        assertTrue(terraformBackendMetadataProvider.isLeadArtifact(repoPath));
        when(repoPath.getName()).thenReturn("state.1234567.json");
        assertTrue(terraformBackendMetadataProvider.isLeadArtifact(repoPath));
    }

    @ParameterizedTest
    @MethodSource("testNameResolverProvider")
    void testNameResolver(String path, String expectedName) {
        MutableMetadataEntityBOM bom = new MutableMetadataEntityBOM();
        FileInfo fileInfo = Mockito.mock(FileInfo.class);
        when(fileInfo.getRepoPath()).thenReturn(new RepoPathImpl("repoKey", path));
        terraformBackendMetadataProvider.nameResolver().apply(bom, null, fileInfo, null);
        Assertions.assertEquals(expectedName, bom.getName());
    }

    @ParameterizedTest
    @MethodSource("testVersionResolverProvider")
    void testVersionResolver(String path, String expectedName) {
        MutableMetadataEntityBOM bom = new MutableMetadataEntityBOM();
        FileInfo fileInfo = Mockito.mock(FileInfo.class);
        when(fileInfo.getRepoPath()).thenReturn(new RepoPathImpl("repoKey", path));
        terraformBackendMetadataProvider.versionResolver().apply(bom, null, fileInfo, null);
        Assertions.assertEquals(expectedName, bom.getVersion());
    }

    @Test
    void testGetExcludedUserProperties() {
        MutableMetadataEntityBOM bom = new MutableMetadataEntityBOM();
        Properties properties = new PropertiesImpl();
        properties.put("terraform.creator", "creator");
        properties.put("terraform.time.created", "time");
        terraformBackendMetadataProvider.userPropsResolver().apply(bom, properties, null, null);
        Assertions.assertTrue(bom.getUserProperties().isEmpty());
    }

    @Test
    void testGetLeadFileExtensions() {
        Assertions.assertEquals(Set.of("json"), terraformBackendMetadataProvider.getLeadArtifactExtensions());
    }

    private static Stream<Arguments> testNameResolverProvider() {
        return Stream.of(
                Arguments.of("workspaces/test/state.latest.json", "test"),
                Arguments.of("workspaces/test/state.12334.json", "test"),
                Arguments.of("workspaces/test/state.json", null),
                Arguments.of("workspacess/test/state.12334.json", null),
                Arguments.of("workspaces/state.latest.json", null)
        );
    }

    private static Stream<Arguments> testVersionResolverProvider() {
        return Stream.of(
                Arguments.of("workspaces/test/state.latest.json", "latest"),
                Arguments.of("workspaces/test/state.12334.json", "12334"),
                Arguments.of("workspaces/test/state.json", null),
                Arguments.of("workspaces/test/state.12334.latest.json", null)
        );
    }
}
