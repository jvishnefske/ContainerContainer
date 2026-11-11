package org.artifactory.metadata.service.provider;

import org.artifactory.fs.FileInfo;
import org.artifactory.md.Properties;
import org.artifactory.properties.PropertiesImpl;
import org.artifactory.repo.RepoPath;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.storage.db.event.service.metadata.model.MutableMetadataEntityBOM;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Priyanshu Gupta
 */
class DebianMetadataProviderTest {

    private DebianMetadataProvider debianMetadataProvider;
    private DebianMetadataProvider.DebianTypeSpecificMetadataTemplate template;

    @BeforeEach
    void setUp() {
        debianMetadataProvider = new DebianMetadataProvider();
        template = debianMetadataProvider.new DebianTypeSpecificMetadataTemplate();
    }

    @Test
    void testConstructor() {
        assertNotNull(debianMetadataProvider);
        assertNotNull(template);
    }

    @Test
    void testGetTypeDeb() {
        Properties properties = new PropertiesImpl();
        assertEquals("deb", debianMetadataProvider.getType(properties));
    }

    @Test
    void testGetTypeDsc() {
        Properties properties = new PropertiesImpl();
        properties.put("dsc.name", "test-package");
        assertEquals("dsc", debianMetadataProvider.getType(properties));
    }

    @Test
    void testGetNameKey() {
        assertEquals(Optional.of("deb.name"), template.getNameKey());
    }

    @Test
    void testGetVersionKey() {
        assertEquals(Optional.of("deb.version"), template.getVersionKey());
    }

    @Test
    void testGetExcludedUserProperties() {
        Set<String> excludedProps = template.getExcludedUserProperties();
        assertTrue(excludedProps.contains("deb.name"));
        assertTrue(excludedProps.contains("deb.version"));
        assertTrue(excludedProps.contains("dsc.name"));
        assertTrue(excludedProps.contains("dsc.version"));
    }

    @Test
    void testGetLeadFileExtensions() {
        Set<String> leadFileExtensions = template.getLeadFileExtensions();
        assertTrue(leadFileExtensions.contains("deb"));
        assertTrue(leadFileExtensions.contains("ddeb"));
        assertTrue(leadFileExtensions.contains("dsc"));
    }

    @Test
    void testGetArchitectureKey() {
        assertEquals(Optional.of("deb.architecture"), template.getArchitectureKey());
    }

    @Test
    void testGetDistributionKey() {
        assertEquals(Optional.of("deb.distribution"), template.getDistributionKey());
    }

    @Test
    void testGetNameForDebFile() {
        Properties properties = new PropertiesImpl();
        properties.put("deb.name", "test-package");
        RepoPath repoPath = new RepoPathImpl("repo", "package.deb");

        assertEquals("test-package", template.getName(properties, repoPath));
    }

    @Test
    void testGetNameForDscFile() {
        Properties properties = new PropertiesImpl();
        properties.put("dsc.name", "test-dsc-package");
        RepoPath repoPath = new RepoPathImpl("repo", "package.dsc");

        assertEquals("test-dsc-package", template.getName(properties, repoPath));
    }

    @Test
    void testGetVersionForDebFile() {
        Properties properties = new PropertiesImpl();
        properties.put("deb.version", "1.0.0");
        RepoPath repoPath = new RepoPathImpl("repo", "package.deb");

        assertEquals("1.0.0", template.getVersion(repoPath, properties));
    }

    @Test
    void testGetVersionForDscFile() {
        Properties properties = new PropertiesImpl();
        properties.put("dsc.version", "2.0.0");
        RepoPath repoPath = new RepoPathImpl("repo", "package.dsc");

        assertEquals("2.0.0", template.getVersion(repoPath, properties));
    }

    @Test
    void testGetPkgidSuffixForDebFile() {
        Properties properties = new PropertiesImpl();
        properties.put("deb.name", "test-package");
        RepoPath repoPath = new RepoPathImpl("repo", "package.deb");

        assertEquals("test-package", template.getPkgidSuffix(repoPath, properties));
    }

    @Test
    void testGetPkgidSuffixForDscFile() {
        Properties properties = new PropertiesImpl();
        properties.put("dsc.name", "test-dsc-package");
        RepoPath repoPath = new RepoPathImpl("repo", "package.dsc");

        assertEquals("test-dsc-package", template.getPkgidSuffix(repoPath, properties));
    }

    @Test
    void testGetNameWithMissingProperty() {
        Properties properties = new PropertiesImpl();
        RepoPath repoPath = new RepoPathImpl("repo", "package.dsc");

        assertNull(template.getName(properties, repoPath));
    }

    @Test
    void testGetVersionWithMissingProperty() {
        Properties properties = new PropertiesImpl();
        RepoPath repoPath = new RepoPathImpl("repo", "package.dsc");

        assertNull(template.getVersion(repoPath, properties));
    }

    @Test
    void testGetPkgidSuffixWithMissingProperty() {
        Properties properties = new PropertiesImpl();
        RepoPath repoPath = new RepoPathImpl("repo", "package.dsc");

        assertNull(template.getPkgidSuffix(repoPath, properties));
    }

    @ParameterizedTest
    @MethodSource("provideResolvePkgIdParams")
    void testResolvePkgId(String filePath, Map<String, String> propertiesMap, String expectedPkgId) {
        MutableMetadataEntityBOM bom = new MutableMetadataEntityBOM();
        Properties properties = new PropertiesImpl();
        propertiesMap.forEach(properties::put);

        FileInfo fileInfo = Mockito.mock(FileInfo.class);
        RepoPath repoPath = new RepoPathImpl("repo", filePath);
        Mockito.when(fileInfo.getRepoPath()).thenReturn(repoPath);

        debianMetadataProvider.resolvePkgId(bom, properties, fileInfo);
        assertEquals(expectedPkgId, bom.getPkgid());
    }

    private static Stream<Arguments> provideResolvePkgIdParams() {
        return Stream.of(
                Arguments.of(
                        "package.deb",
                        Map.of("deb.name", "package-deb"),
                        "deb://package-deb"
                ),
                Arguments.of(
                        "package.dsc",
                        Map.of("dsc.name", "package-dsc"),
                        "dsc://package-dsc"
                )
        );
    }
}