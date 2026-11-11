package org.artifactory.util;

import com.jfrog.sysconf.SysConfig;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.spring.InternalArtifactoryContext;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.test.ArtifactoryHomeStub;
import org.jfrog.common.ResourceUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.FileInputStream;

import static org.artifactory.common.home.ArtifactoryHome.ARTIFACTORY_CONFIG_BOOTSTRAP_FILE;
import static org.artifactory.common.home.ArtifactoryHome.ARTIFACTORY_CONFIG_IMPORT_FILE;
import static org.artifactory.common.home.ArtifactoryHome.ARTIFACTORY_REPO_CONFIG_IMPORT_FILE;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.AssertJUnit.fail;

/**
 * @author Dor Solomon
 */
public class RepoConfigBootstrapUtilsTest extends ArtifactoryHomeBoundJUnitTest {
    private static File configXmlImportFileWithRepos;
    private static File configXmlImportFileWithoutRepos;
    private static File repoConfigImportFile;
    private static String configXmlWithRepos;
    private static String configXmlWithoutRepos;

    private AutoCloseable mockitoAutoCloseable;
    @Mock
    private InternalArtifactoryContext context;
    @Mock
    private ArtifactoryHome artifactoryHome;


    @BeforeAll
    static void before() {
        RepoConfigBootstrapUtilsTest.repoConfigImportFile = ResourceUtils.getResourceAsFile(
                "/org/artifactory/util/" + ArtifactoryHome.ARTIFACTORY_REPO_CONFIG_IMPORT_FILE);
        RepoConfigBootstrapUtilsTest.configXmlImportFileWithRepos = ResourceUtils.getResourceAsFile(
                "/org/artifactory/util/" + ArtifactoryHome.ARTIFACTORY_CONFIG_IMPORT_FILE);
        RepoConfigBootstrapUtilsTest.configXmlImportFileWithoutRepos = ResourceUtils.getResourceAsFile(
                "/org/artifactory/util/no.conflict." + ArtifactoryHome.ARTIFACTORY_CONFIG_IMPORT_FILE);
        try (   FileInputStream configWithRepos = new FileInputStream(configXmlImportFileWithRepos);
                FileInputStream configWithoutRepos = new FileInputStream(configXmlImportFileWithoutRepos)) {
            RepoConfigBootstrapUtilsTest.configXmlWithRepos = new String(configWithRepos.readAllBytes());
            RepoConfigBootstrapUtilsTest.configXmlWithoutRepos = new String(configWithoutRepos.readAllBytes());
        } catch (Exception e) {
            fail("failed to read config xml import file");
        }
    }

    @BeforeEach
    void setUp() {
        mockitoAutoCloseable = MockitoAnnotations.openMocks(this);
        ArtifactoryContextThreadBinder.bind(context);
        ArtifactoryHomeStub homeStub = new ArtifactoryHomeStub(getClass().getName());
        when(artifactoryHome.getArtifactoryRepoConfigImportFile()).thenReturn(repoConfigImportFile);
        when(artifactoryHome.getArtifactoryProperties()).thenReturn(homeStub.getArtifactoryProperties());
        when(artifactoryHome.getCreateDefaultBuildPermissionMarkerFile()).thenReturn(
                homeStub.getCreateDefaultBuildPermissionMarkerFile());
        File mock = mock(File.class);
        when(mock.exists()).thenReturn(false);
        when(artifactoryHome.getArtifactorySAMLEncryptedAssertionPublicKeyFile()).thenReturn(mock);
        SysConfig sysConfig = mock(SysConfig.class);
        when(artifactoryHome.getSysConfig()).thenReturn(sysConfig);
        ArtifactoryHomeStub.bind(artifactoryHome);
    }

    @AfterEach
    void tearDown() throws Exception {
        mockitoAutoCloseable.close();
        ArtifactoryContextThreadBinder.unbind();
        ArtifactoryHome.unbind();
    }

    @Test
    void testNoConflict() {
        // repo config import only
        when(artifactoryHome.getArtifactoryConfigBootstrapFile()).thenReturn(mock(File.class));
        when(artifactoryHome.getArtifactoryConfigImportFile()).thenReturn(mock(File.class));
        RepoConfigBootstrapUtils.verifyConflictingRepoBootstrap();

        // config xml/bootstrap but no repo config import
        when(artifactoryHome.getArtifactoryConfigBootstrapFile()).thenReturn(configXmlImportFileWithRepos);
        when(artifactoryHome.getArtifactoryConfigImportFile()).thenReturn(configXmlImportFileWithRepos);
        when(artifactoryHome.getArtifactoryRepoConfigImportFile()).thenReturn(mock(File.class));
        RepoConfigBootstrapUtils.verifyConflictingRepoBootstrap();

        // repo config import and config xml/bootstrap without repos
        when(artifactoryHome.getArtifactoryRepoConfigImportFile()).thenReturn(repoConfigImportFile);
        when(artifactoryHome.getArtifactoryConfigImportFile()).thenReturn(configXmlImportFileWithoutRepos);
        when(artifactoryHome.getImportConfigXml()).thenReturn(configXmlWithoutRepos);
        RepoConfigBootstrapUtils.verifyConflictingRepoBootstrap();
    }

    @Test
    void testRepoConfigImportFileAndConfigXmlImportFileConflict() {
        when(artifactoryHome.getArtifactoryConfigImportFile()).thenReturn(configXmlImportFileWithRepos);
        when(artifactoryHome.getArtifactoryConfigBootstrapFile()).thenReturn(mock(File.class));
        when(artifactoryHome.getImportConfigXml()).thenReturn(configXmlWithRepos);

        assertConflict(ARTIFACTORY_CONFIG_IMPORT_FILE);
    }

    @Test
    void testRepoConfigImportFileAndBootstrapImportFileConflict() {
        when(artifactoryHome.getArtifactoryConfigBootstrapFile()).thenReturn(configXmlImportFileWithRepos);
        when(artifactoryHome.getArtifactoryConfigImportFile()).thenReturn(mock(File.class));
        when(artifactoryHome.getBootstrapConfigXml()).thenReturn(configXmlWithRepos);

        assertConflict(ARTIFACTORY_CONFIG_BOOTSTRAP_FILE);
    }

    void assertConflict(String fileName) {
        IllegalStateException exception = Assertions.assertThrows(IllegalStateException.class,
                RepoConfigBootstrapUtils::verifyConflictingRepoBootstrap,
                "Expected conflict");

        assertTrue(exception.getMessage()
                .contains(String.format(
                        "Cannot have both '%s' and '%s' which contains repositories in 'etc/artifactory' dir",
                        ARTIFACTORY_REPO_CONFIG_IMPORT_FILE, fileName)));
    }
}

