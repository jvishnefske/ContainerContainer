package org.artifactory.metadata.service.provider;

import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.fs.FileInfo;
import org.artifactory.md.Properties;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.storage.db.event.service.metadata.model.MutableMetadataEntityBOM;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.AssertionsKt.assertNotNull;
import static org.mockito.Mockito.when;

/**
 * @author Tomer Nir
 */
class AIEditorExtensionsMetadataProviderTest {
    @Mock
    Properties properties;
    AutoCloseable closeable;
    AIEditorExtensionsMetadataProvider AIEditorExtensionsMetadataProvider;
    AIEditorExtensionsMetadataProvider.VSCodeTypeSpecificMetadataTemplate vsCodeTypeSpecificMetadataTemplate;

    @BeforeEach
    void setup() {
        closeable = MockitoAnnotations.openMocks(this);
        AIEditorExtensionsMetadataProvider = new AIEditorExtensionsMetadataProvider();
        vsCodeTypeSpecificMetadataTemplate =
                AIEditorExtensionsMetadataProvider.new VSCodeTypeSpecificMetadataTemplate();
    }

    @AfterEach
    void cleanup() throws Exception {
        closeable.close();
    }

    @Test
    void getRepoType() {
        assertEquals(RepoType.AIEditorExtensions.getType(), AIEditorExtensionsMetadataProvider.getType(properties));
    }

    @Test
    void testIsLeadArtifact() {
        assertTrue(AIEditorExtensionsMetadataProvider.isLeadArtifact(
                RepoPathFactory.create("repo", "Microsoft.VisualStudio.Services.VSIXPackage-Universal")));
        assertTrue(AIEditorExtensionsMetadataProvider.isLeadArtifact(
                RepoPathFactory.create("repo", "other-package-1.0.0.vsix")));
        assertFalse(AIEditorExtensionsMetadataProvider.isLeadArtifact(
                RepoPathFactory.create("repo", "some-other-file.txt")));
    }

    @Test
    void testGetVersion() {
        RepoPath repoPath = RepoPathFactory.create("repo", "Microsoft.VisualStudio.Services.VSIXPackage-Universal");
        MutableMetadataEntityBOM bom = new MutableMetadataEntityBOM();
        FileInfo fileInfo = Mockito.mock(FileInfo.class);
        when(fileInfo.getRepoPath()).thenReturn(repoPath);
        when(properties.getFirst("aieditorextensions.extension.version")).thenReturn("0.2.5");
        AIEditorExtensionsMetadataProvider.versionResolver().apply(bom, properties, fileInfo, null);
        assertEquals("0.2.5", bom.getVersion());

        // Test with a non-lead artifact
        bom = new MutableMetadataEntityBOM();
        when(fileInfo.getRepoPath()).thenReturn(RepoPathFactory.create("repo", "other-package-1.0.0"));
        AIEditorExtensionsMetadataProvider.versionResolver().apply(bom, properties, fileInfo, null);
        assertNull(bom.getVersion());
    }

    @Test
    void testGetName() {
        RepoPath repoPath = RepoPathFactory.create("repo", "Microsoft.VisualStudio.Services.VSIXPackage-Universal");
        MutableMetadataEntityBOM bom = new MutableMetadataEntityBOM();
        FileInfo fileInfo = Mockito.mock(FileInfo.class);
        when(fileInfo.getRepoPath()).thenReturn(repoPath);
        when(properties.getFirst("aieditorextensions.extension.name")).thenReturn("terraform");
        AIEditorExtensionsMetadataProvider.nameResolver().apply(bom, properties, fileInfo, null);
        assertEquals("terraform", bom.getName());

        // Test with a non-lead artifact
        bom = new MutableMetadataEntityBOM();
        when(fileInfo.getRepoPath()).thenReturn(RepoPathFactory.create("repo", "other-package-1.0.0"));
        AIEditorExtensionsMetadataProvider.nameResolver().apply(bom, properties, fileInfo, null);
        assertNull(bom.getName());
    }

    @Test
    void testGetExcludedUserProperties() {
        Set<String> excludedUserProperties = vsCodeTypeSpecificMetadataTemplate.getExcludedUserProperties();
        assertNotNull(excludedUserProperties);
        assertTrue(excludedUserProperties.contains("aieditorextensions.extension.name"));
        assertTrue(excludedUserProperties.contains("aieditorextensions.extension.version"));
    }

    @Test
    void testGetLeadFileExtensions() {
        Set<String> leadFileExtensions = vsCodeTypeSpecificMetadataTemplate.getLeadFileExtensions();
        assertNotNull(leadFileExtensions);
        assertTrue(leadFileExtensions.contains("Microsoft.VisualStudio.Services.VSIXPackage*"));
    }
}