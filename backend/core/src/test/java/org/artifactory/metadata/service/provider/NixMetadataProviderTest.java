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
import org.testng.Assert;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NixMetadataProviderTest {

    private NixMetadataProvider nixMetadataProvider;
    @Mock
    private RepoPath repoPath;

    @BeforeEach
    void setUp() {
        nixMetadataProvider = new NixMetadataProvider();
    }

    @ParameterizedTest
    @CsvSource({
        "'nix-repo/example.narinfo', true",
        "'nix-repo/package.nar', false",
        "'nix-repo/package.nar.xz', false",
        "'nix-repo/package.txt', false"
    })
    void testIsLeadArtifact(String filePath, boolean expectedResult) {
        when(repoPath.getPath()).thenReturn(filePath);
        assertEquals(expectedResult, nixMetadataProvider.isLeadArtifact(repoPath));
    }

    @Test
    void testGetType() {
        PropertiesImpl props = new PropertiesImpl();
        String type = nixMetadataProvider.getType(props);
        Assert.assertEquals(type, "nix");
    }

    @Test
    void testGetLeadArtifactExtensions() {
        Assert.assertEquals(nixMetadataProvider.getLeadArtifactExtensions(), Collections.singletonList("narinfo"));
    }

    @Test
    void testGetKeys() {
        Assert.assertEquals(nixMetadataProvider.typeSpecificMetadataTemplate.getNameKey(),
                Optional.of("nix.name"));
        Assert.assertEquals(nixMetadataProvider.typeSpecificMetadataTemplate.getVersionKey(),
                Optional.of("nix.version"));
    }

    @Test
    public void testResolvers() {
        MutableMetadataEntityBOM bom = new MutableMetadataEntityBOM();
        FileInfoImpl fileInfo = new FileInfoImpl(new RepoPathImpl(
                "nix-local", "nix-local/binary-cache/somehash/somehash.narinfo"));
        PropertiesImpl props = new PropertiesImpl();
        props.put("nix.name", "nix-test");
        props.put("nix.version", "1.2.3");

        nixMetadataProvider.nameResolver().apply(bom, props, fileInfo, null);
        nixMetadataProvider.pkgIdResolver().apply(bom, props, fileInfo, null);
        nixMetadataProvider.versionResolver().apply(bom, props, fileInfo, null);

        Assert.assertEquals(bom.getName(), "nix-test");
        Assert.assertEquals(bom.getPkgid(), "nix://nix-test");
        Assert.assertEquals(bom.getVersion(), "1.2.3");
    }

}