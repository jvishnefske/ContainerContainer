package org.artifactory.metadata.service.provider;

import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.model.fs.FileInfoImpl;
import org.artifactory.properties.PropertiesImpl;
import org.artifactory.repo.RepoPath;
import org.artifactory.storage.db.event.service.metadata.model.MutableMetadataEntityBOM;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentPackagesMetadataProviderTest {

    private AgentPackagesMetadataProvider agentPackagesMetadataProvider;

    @Mock
    private RepoPath repoPath;

    @BeforeEach
    void setUp() {
        agentPackagesMetadataProvider = new AgentPackagesMetadataProvider();
    }

    @ParameterizedTest
    @CsvSource({
            "'acme/my-pkg/my-pkg-1.0.0.zip', true",
            "'acme/my-pkg/my-pkg-1.0.0.tar.gz', false",
            "'acme/my-pkg/my-pkg-1.0.0.tgz', false",
            "'acme/my-pkg/readme.txt', false",
            "'', false"
    })
    void isLeadArtifact_shouldReturnExpected_whenGivenPath(String filePath, boolean expectedResult) {
        when(repoPath.getPath()).thenReturn(filePath);

        assertEquals(expectedResult, agentPackagesMetadataProvider.isLeadArtifact(repoPath));
    }

    @Test
    void getType_shouldReturnAgentPackages_always() {
        String type = agentPackagesMetadataProvider.getType(new PropertiesImpl());

        assertEquals("agentpackages", type);
    }

    @Test
    void getLeadArtifactExtensions_shouldReturnZipExtension() {
        assertEquals(Set.of("zip"), agentPackagesMetadataProvider.getLeadArtifactExtensions());
    }

    @Test
    void getNameKey_shouldReturnAgentPackagesGitrepo() {
        assertEquals(Optional.of("agentpackages.gitrepo"),
                agentPackagesMetadataProvider.typeSpecificMetadataTemplate.getNameKey());
    }

    @Test
    void getVersionKey_shouldReturnAgentPackagesVersion() {
        assertEquals(Optional.of("agentpackages.version"),
                agentPackagesMetadataProvider.typeSpecificMetadataTemplate.getVersionKey());
    }

    @Test
    void getDescriptionKey_shouldReturnAgentPackagesDescription() {
        assertEquals(Optional.of("agentpackages.description"),
                agentPackagesMetadataProvider.typeSpecificMetadataTemplate.getDescriptionKey());
    }

    @Test
    void getExcludedUserProperties_shouldReturnGitrepoVersionAndDescription() {
        assertEquals(
                Set.of("agentpackages.gitrepo", "agentpackages.version", "agentpackages.description"),
                agentPackagesMetadataProvider.typeSpecificMetadataTemplate.getExcludedUserProperties());
    }

    @Test
    void buildVersionFilesQuery_shouldReturnEmpty_whenLeadArtifactHasNoParent() {
        assertTrue(agentPackagesMetadataProvider.buildVersionFilesQuery(
                new RepoPathImpl("agentpackages-remote", "")).isEmpty());
    }

    @Test
    void resolvers_shouldPopulateBom_whenGivenValidProperties() {
        MutableMetadataEntityBOM bom = new MutableMetadataEntityBOM();
        FileInfoImpl fileInfo = new FileInfoImpl(
                new RepoPathImpl("agentpackages-remote", "acme/my-pkg/my-pkg-1.0.0.zip"));
        PropertiesImpl props = new PropertiesImpl();
        props.put("agentpackages.gitrepo", "my-pkg");
        props.put("agentpackages.version", "1.0.0");

        agentPackagesMetadataProvider.nameResolver().apply(bom, props, fileInfo, null);
        agentPackagesMetadataProvider.pkgIdResolver().apply(bom, props, fileInfo, null);
        agentPackagesMetadataProvider.versionResolver().apply(bom, props, fileInfo, null);

        assertEquals("my-pkg", bom.getName());
        assertEquals("agentpackages://my-pkg", bom.getPkgid());
        assertEquals("1.0.0", bom.getVersion());
    }

    @Test
    void resolvers_shouldReturnNull_whenPropertiesAreMissing() {
        MutableMetadataEntityBOM bom = new MutableMetadataEntityBOM();
        FileInfoImpl fileInfo = new FileInfoImpl(
                new RepoPathImpl("agentpackages-remote", "acme/my-pkg/my-pkg-1.0.0.zip"));
        PropertiesImpl props = new PropertiesImpl();

        agentPackagesMetadataProvider.nameResolver().apply(bom, props, fileInfo, null);
        agentPackagesMetadataProvider.pkgIdResolver().apply(bom, props, fileInfo, null);
        agentPackagesMetadataProvider.versionResolver().apply(bom, props, fileInfo, null);

        assertNull(bom.getName());
        assertNull(bom.getPkgid());
        assertNull(bom.getVersion());
    }

    @Test
    void userPropsResolver_shouldExcludeGitrepoAndVersion_whenResolvingUserProperties() {
        MutableMetadataEntityBOM bom = new MutableMetadataEntityBOM();
        PropertiesImpl props = new PropertiesImpl();
        props.put("agentpackages.gitrepo", "my-pkg");
        props.put("agentpackages.version", "1.0.0");
        props.put("agentpackages.type", "prompt");
        props.put("agentpackages.tags", "registry");

        agentPackagesMetadataProvider.userPropsResolver().apply(bom, props, null, null);

        assertNull(bom.getUserProperties().get("agentpackages.gitrepo"));
        assertNull(bom.getUserProperties().get("agentpackages.version"));
        assertEquals(Set.of("prompt"), bom.getUserProperties().get("agentpackages.type"));
        assertEquals(Set.of("registry"), bom.getUserProperties().get("agentpackages.tags"));
    }
}
