package org.artifactory.metadata.service.provider;

import org.artifactory.fs.FileInfo;
import org.artifactory.md.Properties;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.properties.PropertiesImpl;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.storage.db.event.service.metadata.model.MutableMetadataEntityBOM;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * @author Shahaf Golan
 */
class JetBrainsPluginsMetadataProviderTest {

    private JetBrainsPluginsMetadataProvider metadataProvider;
    private JetBrainsPluginsMetadataProvider.JetBrainsPluginsTypeSpecificMetadataTemplate metadataTemplate;

    @BeforeEach
    void setUp() {
        metadataProvider = new JetBrainsPluginsMetadataProvider();
        metadataTemplate = metadataProvider.new JetBrainsPluginsTypeSpecificMetadataTemplate();
    }

    @Test
    void isLeadArtifact() {
        JetBrainsPluginsMetadataProvider provider = new JetBrainsPluginsMetadataProvider();
        assertTrue(provider.isLeadArtifact(RepoPathFactory.create("repokey", "pluginId/pluginVersion/binaries.zip")));
        assertFalse(provider.isLeadArtifact(RepoPathFactory.create("repokey", "pluginId/pluginVersion/binaries.zip1")));
        assertFalse(provider.isLeadArtifact(RepoPathFactory.create("repokey", "pluginId/pluginVersion/binaries.gzip")));
        assertTrue(provider.isLeadArtifact(RepoPathFactory.create("repokey", "pluginId/pluginVersion/binaries1.zip")));
        assertTrue(provider.isLeadArtifact(
                RepoPathFactory.create("repokey", "pluginId/pluginVersion/this_is_no_binaries.zip")));
    }

    @Test
    void isPackageConsumptionLead() {
        JetBrainsPluginsMetadataProvider provider = new JetBrainsPluginsMetadataProvider();
        assertTrue(provider.isPackageConsumptionLead(
                RepoPathFactory.create("repokey", "pluginId/pluginVersion/plugin.zip")));
        assertFalse(provider.isPackageConsumptionLead(
                RepoPathFactory.create("repokey", "pluginId/pluginVersion/plugin.zip1")));
        assertFalse(provider.isPackageConsumptionLead(
                RepoPathFactory.create("repokey", "pluginId/pluginVersion/plugin.gzip")));
        assertTrue(provider.isPackageConsumptionLead(
                RepoPathFactory.create("repokey", "pluginId/pluginVersion/plugin1.zip")));
        assertTrue(provider.isPackageConsumptionLead(
                RepoPathFactory.create("repokey", "pluginId/pluginVersion/_binaries112.zip")));
    }

    @Test
    void getType() {
        Assertions.assertEquals("jetbrainsplugins",
                new JetBrainsPluginsMetadataProvider().getType(new PropertiesImpl()));
    }

    @Test
    void getLeadFileExtension() {
        Assertions.assertEquals(Set.of(".zip"), new JetBrainsPluginsMetadataProvider().getLeadArtifactExtensions());
    }

    @Test
    void getPkgidSuffix() {
        JetBrainsPluginsMetadataProvider provider = new JetBrainsPluginsMetadataProvider();
        RepoPath repoPath = Mockito.mock(RepoPath.class);
        Properties properties = Mockito.mock(Properties.class);
        when(properties.getFirst("jetbrainsplugins.name")).thenReturn("aqua");
        Assertions.assertEquals("aqua",
                provider.typeSpecificMetadataTemplate.getPkgidSuffix(repoPath, properties));
    }

    @Test
    void testGetExcludedUserProperties() {
        MutableMetadataEntityBOM bom = new MutableMetadataEntityBOM();
        Properties properties = new PropertiesImpl();
        properties.put("jetbrainsplugins.name", "pluginId");
        properties.put("jetbrainsplugins.version", "version");
        new JetBrainsPluginsMetadataProvider().userPropsResolver().apply(bom, properties, null, null);
        assertTrue(bom.getUserProperties().isEmpty());
    }

    @Test
    void testNameResolver() {
        MutableMetadataEntityBOM bom = new MutableMetadataEntityBOM();
        FileInfo fileInfo = Mockito.mock(FileInfo.class);
        Properties properties = new PropertiesImpl();
        properties.put("jetbrainsplugins.name", "pluginId");
        properties.put("jetbrainsplugins.version", "version");
        when(fileInfo.getRepoPath()).thenReturn(new RepoPathImpl("repoKey", "pluginId/pluginVersion/binaries.zip"));
        new JetBrainsPluginsMetadataProvider().nameResolver().apply(bom, properties, fileInfo, null);
        Assertions.assertEquals("pluginId", bom.getName());
    }

    @Test
    void testVersionResolver() {
        MutableMetadataEntityBOM bom = new MutableMetadataEntityBOM();
        Properties properties = new PropertiesImpl();
        FileInfo fileInfo = Mockito.mock(FileInfo.class);
        properties.put("jetbrainsplugins.name", "pluginId");
        properties.put("jetbrainsplugins.version", "version");
        when(fileInfo.getRepoPath()).thenReturn(new RepoPathImpl("repoKey", "pluginId/pluginVersion/binaries.zip"));
        new JetBrainsPluginsMetadataProvider().versionResolver().apply(bom, properties, fileInfo, null);
        Assertions.assertEquals("version", bom.getVersion());
    }

    @Test
    void getTags() {
        final Set<String> tags = metadataTemplate.getTagValueResolver().apply("one,two,three");
        assertEquals(3, tags.size());
        assertTrue(tags.contains("one"));
        assertTrue(tags.contains("two"));
        assertTrue(tags.contains("three"));
        final Set<String> emptyTags = metadataTemplate.getTagValueResolver().apply("");
        assertEquals(0, emptyTags.size());
    }
}