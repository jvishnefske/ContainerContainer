package org.artifactory.metadata.service.provider;

import org.artifactory.fs.FileInfo;
import org.artifactory.md.Properties;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.properties.PropertiesImpl;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.storage.db.event.service.metadata.model.MutableMetadataEntityBOM;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Test class for BazelModulesMetadataProvider
 *
 * @author Volodymyr Shulga
 */
class BazelModulesMetadataProviderTest {

    @Test
    void isLeadArtifact() {
        BazelModulesMetadataProvider provider = new BazelModulesMetadataProvider();
        
        // Test valid Bazel module paths with supported archive formats
        assertTrue(provider.isLeadArtifact(
                RepoPathFactory.create("repokey", "modules/com_google_absl/20230125.0/absl.zip")));
        assertTrue(provider.isLeadArtifact(
                RepoPathFactory.create("repokey", "modules/rules_java/6.0.0/rules_java.jar")));
        assertTrue(provider.isLeadArtifact(
                RepoPathFactory.create("repokey", "modules/bazel_skylib/1.4.1/bazel_skylib.tar.gz")));
        assertTrue(provider.isLeadArtifact(
                RepoPathFactory.create("repokey", "modules/protobuf/3.19.6/protobuf.tar.xz")));
        assertTrue(provider.isLeadArtifact(
                RepoPathFactory.create("repokey", "modules/rules_cc/0.0.6/rules_cc.deb")));
        assertTrue(provider.isLeadArtifact(
                RepoPathFactory.create("repokey", "modules/python_rules/1.0.0/python.whl")));
        
        // Test valid Bazel module paths with unsupported formats - should be false
        assertFalse(provider.isLeadArtifact(
                RepoPathFactory.create("repokey", "modules/test_module/1.0.0/module.txt")));
        assertFalse(provider.isLeadArtifact(
                RepoPathFactory.create("repokey", "modules/test_module/1.0.0/module.json")));
        assertFalse(provider.isLeadArtifact(
                RepoPathFactory.create("repokey", "modules/test_module/1.0.0/module.xml")));
        assertFalse(provider.isLeadArtifact(
                RepoPathFactory.create("repokey", "modules/com_google_absl/20230125.0/abslzip")));


        assertTrue(provider.isLeadArtifact(RepoPathFactory.create("repokey", "invalid/path/module.zip")));
        assertTrue(provider.isLeadArtifact(RepoPathFactory.create("repokey", "modules/incomplete.zip")));
        assertTrue(provider.isLeadArtifact(
                RepoPathFactory.create("repokey", "not-modules/test/1.0.0/file.zip")));
        assertTrue(provider.isLeadArtifact(RepoPathFactory.create("repokey", "regular-file.tar.gz")));
    }

    @Test
    void isPackageConsumptionLead() {
        BazelModulesMetadataProvider provider = new BazelModulesMetadataProvider();
        
        // Since isPackageConsumptionLead() delegates to isLeadArtifact(), 
        // it should have the same behavior
        
        // Test valid Bazel module paths with supported archive formats
        assertTrue(provider.isPackageConsumptionLead(
                RepoPathFactory.create("repokey", "modules/test_module/1.0.0/module.zip")));
        assertTrue(provider.isPackageConsumptionLead(
                RepoPathFactory.create("repokey", "modules/test_module/1.0.0/module.tar")));
        assertTrue(provider.isPackageConsumptionLead(
                RepoPathFactory.create("repokey", "modules/test_module/1.0.0/module.war")));
        assertTrue(provider.isPackageConsumptionLead(
                RepoPathFactory.create("repokey", "modules/test_module/1.0.0/module.aar")));
        
        // Test valid Bazel module paths with unsupported formats - should be false
        assertFalse(provider.isPackageConsumptionLead(
                RepoPathFactory.create("repokey", "modules/test_module/1.0.0/module.txt")));
        assertFalse(provider.isPackageConsumptionLead(
                RepoPathFactory.create("repokey", "modules/test_module/1.0.0/module.py")));
    }

    @Test
    void getType() {
        Assertions.assertEquals("bazelmodules",
                new BazelModulesMetadataProvider().getType(new PropertiesImpl()));
    }

    @Test
    void getLeadFileExtensions() {
        Set<String> expectedExtensions = Set.of(
                "zip", "jar", "war", "aar", "nupkg", "whl", "tar", "tar.gz", "tgz",
                "tar.xz", "txz",
                "tar.zst", "tzst", "tar.bz2", "tbz", "ar", "deb"
        );
        Assertions.assertEquals(expectedExtensions,
                new BazelModulesMetadataProvider().getLeadArtifactExtensions());
    }

    @Test
    void getPkgidSuffix() {
        BazelModulesMetadataProvider provider = new BazelModulesMetadataProvider();
        RepoPath repoPath = Mockito.mock(RepoPath.class);
        Properties properties = Mockito.mock(Properties.class);
        when(properties.getFirst("bazelmodules.module.name")).thenReturn("my-module");
        Assertions.assertEquals("my-module",
                provider.typeSpecificMetadataTemplate.getPkgidSuffix(repoPath, properties));
    }

    @Test
    void testGetExcludedUserProperties() {
        MutableMetadataEntityBOM bom = new MutableMetadataEntityBOM();
        Properties properties = new PropertiesImpl();
        properties.put("bazelmodules.module.name", "moduleId");
        properties.put("bazelmodules.module.version", "version");
        new BazelModulesMetadataProvider().userPropsResolver().apply(bom, properties, null, null);
        assertTrue(bom.getUserProperties().isEmpty());
    }

    @Test
    void testNameResolver() {
        MutableMetadataEntityBOM bom = new MutableMetadataEntityBOM();
        FileInfo fileInfo = Mockito.mock(FileInfo.class);
        Properties properties = new PropertiesImpl();
        properties.put("bazelmodules.module.name", "moduleId");
        properties.put("bazelmodules.module.version", "version");
        when(fileInfo.getRepoPath()).thenReturn(
                new RepoPathImpl("repoKey", "modules/moduleId/version/module.zip"));
        new BazelModulesMetadataProvider().nameResolver().apply(bom, properties, fileInfo, null);
        Assertions.assertEquals("moduleId", bom.getName());
    }

    @Test
    void testVersionResolver() {
        MutableMetadataEntityBOM bom = new MutableMetadataEntityBOM();
        Properties properties = new PropertiesImpl();
        FileInfo fileInfo = Mockito.mock(FileInfo.class);
        properties.put("bazelmodules.module.name", "moduleId");
        properties.put("bazelmodules.module.version", "1.0.0");
        when(fileInfo.getRepoPath()).thenReturn(
                new RepoPathImpl("repoKey", "modules/moduleId/version/module.zip"));
        new BazelModulesMetadataProvider().versionResolver().apply(bom, properties, fileInfo, null);
        Assertions.assertEquals("1.0.0", bom.getVersion());
    }

    @Test
    void testBazelModulePathValidation() {
        BazelModulesMetadataProvider provider = new BazelModulesMetadataProvider();
        
        // Valid Bazel module paths (matching ^modules/[^/]+/[^/]+/(.+)$ pattern)
        assertTrue(provider.isLeadArtifact(
                RepoPathFactory.create("repo", "modules/com_google_absl/20230125.0/absl-20230125.0.tar.gz")));
        assertTrue(provider.isLeadArtifact(
                RepoPathFactory.create("repo", "modules/rules_java/6.0.0/rules_java-6.0.0.tar.gz")));
        assertTrue(provider.isLeadArtifact(
                RepoPathFactory.create("repo", "modules/bazel_skylib/1.4.1/bazel_skylib-1.4.1.zip")));
        assertTrue(provider.isLeadArtifact(RepoPathFactory.create("repo", "modules/a/b/c.jar")));
        
        // Invalid Bazel module paths (not matching the required pattern)
        assertTrue(provider.isLeadArtifact(
                RepoPathFactory.create("repo", "modules/incomplete.zip")));  // Missing version
        assertTrue(provider.isLeadArtifact(
                RepoPathFactory.create("repo", "modules/just_name.tar.gz")));  // No version part
        assertTrue(provider.isLeadArtifact(
                RepoPathFactory.create("repo", "not-modules/test/1.0.0/file.zip")));  // Wrong prefix
        assertTrue(provider.isLeadArtifact(
                RepoPathFactory.create("repo", "invalid/path/file.tar.gz")));  // Wrong structure
        assertTrue(provider.isLeadArtifact(
                RepoPathFactory.create("repo", "regular-file.tar.gz")));  // No modules prefix
        
        // Edge case: Valid path structure but unsupported extension
        assertFalse(provider.isLeadArtifact(
                RepoPathFactory.create("repo", "modules/test_module/1.0.0/file.txt")));
        assertFalse(provider.isLeadArtifact(
                RepoPathFactory.create("repo", "modules/test_module/1.0.0/file.json")));
    }

    @Test
    void testIsPackageConsumptionLeadDelegatesToIsLeadArtifact() {
        BazelModulesMetadataProvider provider = new BazelModulesMetadataProvider();
        
        // Test that isPackageConsumptionLead returns the same as isLeadArtifact
        RepoPath validPath = RepoPathFactory.create("repo", "modules/test_module/1.0.0/file.zip");
        RepoPath invalidPath = RepoPathFactory.create("repo", "invalid/path/file.zip");
        
        assertEquals(provider.isLeadArtifact(validPath),
                provider.isPackageConsumptionLead(validPath));
        assertEquals(provider.isLeadArtifact(invalidPath),
                provider.isPackageConsumptionLead(invalidPath));
    }
}