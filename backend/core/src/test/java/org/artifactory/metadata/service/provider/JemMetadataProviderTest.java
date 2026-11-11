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
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JemMetadataProviderTest {
    private JemMetadataProvider jemMetadataProvider;
    @BeforeEach
    void setUp() {
        jemMetadataProvider = spy(new JemMetadataProvider());
    }

    @Test
    void testIsLeadArtifact() {
        RepoPath repoPath = new RepoPathImpl("repo","version.json");
        assertFalse(jemMetadataProvider.isLeadArtifact(repoPath));
        repoPath = new RepoPathImpl("repo","date/version.json");
        assertFalse(jemMetadataProvider.isLeadArtifact(repoPath));
        repoPath = new RepoPathImpl("repo","name/date/bversion.json");
        assertFalse(jemMetadataProvider.isLeadArtifact(repoPath));
        repoPath = new RepoPathImpl("repo","name/date/version.json");
        assertTrue(jemMetadataProvider.isLeadArtifact(repoPath));
    }

    @ParameterizedTest
    @MethodSource("testNameResolverProvider")
    void testNameResolver(String path, String expectedName) {
        MutableMetadataEntityBOM bom = new MutableMetadataEntityBOM();
        FileInfo fileInfo = Mockito.mock(FileInfo.class);
        when(fileInfo.getRepoPath()).thenReturn(new RepoPathImpl("repoKey", path));
        jemMetadataProvider.nameResolver().apply(bom, null, fileInfo, null);
        Assertions.assertEquals(expectedName, bom.getName());
    }

    @ParameterizedTest
    @MethodSource("testVersionResolverProvider")
    void testVersionResolver(String path, String expectedName) {
        MutableMetadataEntityBOM bom = new MutableMetadataEntityBOM();
        FileInfo fileInfo = Mockito.mock(FileInfo.class);
        when(fileInfo.getRepoPath()).thenReturn(new RepoPathImpl("repoKey", path));
        jemMetadataProvider.versionResolver().apply(bom, null, fileInfo, null);
        Assertions.assertEquals(expectedName, bom.getVersion());
    }

    @Test
    void testGetExcludedUserProperties() {
        MutableMetadataEntityBOM bom = new MutableMetadataEntityBOM();
        Properties properties = new PropertiesImpl();
        properties.put("jem.prop", "value");
        properties.put("jem.name", "stam");
        jemMetadataProvider.userPropsResolver().apply(bom, properties, null, null);
        Assertions.assertEquals(Set.of("value"), bom.getUserProperties().get("jem.prop"));
        Assertions.assertEquals(Set.of("stam"), bom.getUserProperties().get("jem.name"));
    }

    @Test
    void testGetLeadFileExtensions() {
        Assertions.assertEquals(Set.of("json"), jemMetadataProvider.getLeadArtifactExtensions());
    }

    private static Stream<Arguments> testNameResolverProvider() {
        return Stream.of(
                Arguments.of("name/version/version.json", "name"),
                Arguments.of("name/version/versionn.json", null),
                Arguments.of("name/name/version/version.json", null)
        );
    }

    private static Stream<Arguments> testVersionResolverProvider() {
        return Stream.of(
                Arguments.of("name/version/version.json", "version"),
                Arguments.of("name/version/versionn.json", null),
                Arguments.of("name/name/version/version.json", null)
        );
    }
}