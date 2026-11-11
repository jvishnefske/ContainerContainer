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

/**
 * @author Barak Hacham
 */
@ExtendWith(MockitoExtension.class)
class AgentPluginsMetadataProviderTest {

    private AgentPluginsMetadataProvider agentPluginsMetadataProvider;

    @Mock
    private RepoPath repoPath;

    @BeforeEach
    void setUp() {
        agentPluginsMetadataProvider = new AgentPluginsMetadataProvider();
    }

    @ParameterizedTest
    @CsvSource({
            "'agentplugins-repo/my-plugin-1.0.0.zip', true",
            "'agentplugins-repo/my-plugin-1.0.0.claw', false",
            "'agentplugins-repo/readme.txt', false",
            "'', false"
    })
    void isLeadArtifact_shouldReturnExpected_whenGivenPath(String filePath,
            boolean expectedResult) {
        when(repoPath.getPath()).thenReturn(filePath);

        assertEquals(expectedResult, agentPluginsMetadataProvider.isLeadArtifact(repoPath));
    }

    @Test
    void getType_shouldReturnAgentPlugins_always() {
        String type = agentPluginsMetadataProvider.getType(new PropertiesImpl());

        assertEquals("agentplugins", type);
    }

    @Test
    void getLeadArtifactExtensions_shouldReturnZipExtension() {
        assertEquals(Set.of("zip"), agentPluginsMetadataProvider.getLeadArtifactExtensions());
    }

    @Test
    void getNameKey_shouldReturnAgentPluginsName() {
        assertEquals(Optional.of("agentplugins.name"),
                agentPluginsMetadataProvider.typeSpecificMetadataTemplate.getNameKey());
    }

    @Test
    void getVersionKey_shouldReturnAgentPluginsVersion() {
        assertEquals(Optional.of("agentplugins.version"),
                agentPluginsMetadataProvider.typeSpecificMetadataTemplate.getVersionKey());
    }

    @Test
    void getDescriptionKey_shouldReturnAgentPluginsDescription() {
        assertEquals(Optional.of("agentplugins.description"),
                agentPluginsMetadataProvider.typeSpecificMetadataTemplate.getDescriptionKey());
    }

    @Test
    void getExcludedUserProperties_shouldReturnNameVersionAndDescription() {
        assertEquals(
                Set.of("agentplugins.name", "agentplugins.version", "agentplugins.description"),
                agentPluginsMetadataProvider.typeSpecificMetadataTemplate
                        .getExcludedUserProperties());
    }

    @Test
    void buildVersionFilesQuery_shouldReturnEmpty_whenLeadArtifactHasNoParent() {
        assertTrue(agentPluginsMetadataProvider.buildVersionFilesQuery(
                new RepoPathImpl("agentplugins-remote", "")).isEmpty());
    }

    @Test
    void resolvers_shouldPopulateBom_whenGivenValidProperties() {
        MutableMetadataEntityBOM bom = new MutableMetadataEntityBOM();
        FileInfoImpl fileInfo = new FileInfoImpl(
                new RepoPathImpl("agentplugins-remote", "my-plugin-1.0.0.zip"));
        PropertiesImpl props = new PropertiesImpl();
        props.put("agentplugins.name", "my-plugin");
        props.put("agentplugins.version", "1.0.0");

        agentPluginsMetadataProvider.nameResolver().apply(bom, props, fileInfo, null);
        agentPluginsMetadataProvider.pkgIdResolver().apply(bom, props, fileInfo, null);
        agentPluginsMetadataProvider.versionResolver().apply(bom, props, fileInfo, null);

        assertEquals("my-plugin", bom.getName());
        assertEquals("agentplugins://my-plugin", bom.getPkgid());
        assertEquals("1.0.0", bom.getVersion());
    }

    @Test
    void resolvers_shouldReturnNull_whenPropertiesAreMissing() {
        MutableMetadataEntityBOM bom = new MutableMetadataEntityBOM();
        FileInfoImpl fileInfo = new FileInfoImpl(
                new RepoPathImpl("agentplugins-remote", "my-plugin-1.0.0.zip"));
        PropertiesImpl props = new PropertiesImpl();

        agentPluginsMetadataProvider.nameResolver().apply(bom, props, fileInfo, null);
        agentPluginsMetadataProvider.pkgIdResolver().apply(bom, props, fileInfo, null);
        agentPluginsMetadataProvider.versionResolver().apply(bom, props, fileInfo, null);

        assertNull(bom.getName());
        assertNull(bom.getPkgid());
        assertNull(bom.getVersion());
    }

    @Test
    void userPropsResolver_shouldExcludeNameAndVersion_whenResolvingUserProperties() {
        MutableMetadataEntityBOM bom = new MutableMetadataEntityBOM();
        PropertiesImpl props = new PropertiesImpl();
        props.put("agentplugins.name", "my-plugin");
        props.put("agentplugins.version", "1.0.0");
        props.put("agentplugins.arch", "linux-x86_64");
        props.put("agentplugins.summary", "A useful plugin");

        agentPluginsMetadataProvider.userPropsResolver().apply(bom, props, null, null);

        assertNull(bom.getUserProperties().get("agentplugins.name"));
        assertNull(bom.getUserProperties().get("agentplugins.version"));
        assertEquals(Set.of("linux-x86_64"),
                bom.getUserProperties().get("agentplugins.arch"));
        assertEquals(Set.of("A useful plugin"),
                bom.getUserProperties().get("agentplugins.summary"));
    }
}
