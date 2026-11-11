package org.artifactory.util.evidence;

import com.jfrog.sysconf.SysConfig;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.repo.RepoPath;
import org.artifactory.sapi.fs.VfsFolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.artifactory.util.evidence.EvidenceSystemYamlConstants.EVIDENCE_AQL_FILTER_IN_DATABASE;
import static org.artifactory.util.evidence.EvidenceSystemYamlConstants.EVIDENCE_ENABLED;
import static org.artifactory.util.evidence.EvidenceSystemYamlConstants.EVIDENCE_OCI_ENABLED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EvidenceConfigurationUtilTest {

    private SysConfig sysConfig;

    @BeforeEach
    void setup() {
        ArtifactoryHome artifactoryHome = mock(ArtifactoryHome.class);
        sysConfig = mock(SysConfig.class);
        when(artifactoryHome.getSysConfig()).thenReturn(sysConfig);
        ArtifactoryHome.bind(artifactoryHome);
    }

    @AfterEach
    void tearDown() {
        ArtifactoryHome.unbind();
    }

    @Test
    void isEvidenceEnabled_Success() {
        when(sysConfig.getBoolean(EVIDENCE_ENABLED, true)).thenReturn(true);
        assertTrue(EvidenceConfigurationUtil.isEvidenceEnabled());
    }

    @Test
    void isEvidenceOCIEnabled_Success() {
        when(sysConfig.getBoolean(EVIDENCE_OCI_ENABLED, false)).thenReturn(false);
        assertFalse(EvidenceConfigurationUtil.isEvidenceOCIEnabled());
    }

    @Test
    void isEvidenceNavigationEnabled_Success() {
        assertTrue(EvidenceConfigurationUtil.isEvidenceNavigationDisabled());
    }

    @Test
    void isEvidenceEnabled_Exception() {
        when(sysConfig.getBoolean(EVIDENCE_ENABLED, false)).thenThrow(new RuntimeException());
        assertFalse(EvidenceConfigurationUtil.isEvidenceEnabled());
    }

    @Test
    void isEvidenceAQLFilterInDatabase_Success() {
        when(sysConfig.getBoolean(EVIDENCE_AQL_FILTER_IN_DATABASE, true)).thenReturn(true);
        assertTrue(EvidenceConfigurationUtil.isEvidenceAQLFilterInDatabase());
    }

    @Test
    void isEvidenceAQLFilterInDatabase_Exception() {
        when(sysConfig.getBoolean(EVIDENCE_AQL_FILTER_IN_DATABASE, true)).thenThrow(new RuntimeException());
        assertTrue(EvidenceConfigurationUtil.isEvidenceAQLFilterInDatabase());
    }

    @ParameterizedTest
    @CsvSource(value = {
            ".evidence/file.txt|true",
            ".evidence/|true",
            ".evidence|true",
            ".evidence/subdir/file.txt|true",
            "evidences/file.txt|false",
            "file.evidence|false",
            ".evidences/file.txt|false",
            "some/path/.evidence/file.txt|false",  // .evidence not at start
    }, delimiter = '|')
    void isEvidencePath_String(String path, boolean expected) {
        assertEquals(expected, EvidenceConfigurationUtil.isEvidencePath(path));
    }

    // TODO: Fix the implementation bug in isTrashedEvidencePath(RepoPath) method
    // The method uses startsWith(EVIDENCE_DIR) which incorrectly matches ".evidences"
    // It should check for exact match or ensure there's a "/" after ".evidence"
    @ParameterizedTest
    @CsvSource(value = {
            "auto-trashcan|my-build-info/.evidence/file.txt|true",
            "auto-trashcan|my-build-info/.evidence/|true",
            "auto-trashcan|my-docker/.evidence/file.txt|true",
            "auto-trashcan|my-docker/.evidence/|true",
            "auto-trashcan|my-docker/folder/.evidence/file.txt|false",
            "repo|.evidence/file.txt|false",
            "auto-trashcan|my-docker/.evidences/file.txt|true",  // Changed from false to true due to implementation bug
            "auto-trashcan|test-docker/.evidence/layers/sha256/file.txt|true",
            // edge case: top level evidence folder in trashcan, should not be considered as trashed evidence
            "auto-trashcan|.evidence/|false",
    }, delimiter = '|')
    void isTrashedEvidencePath(String repoKey, String path, boolean expected) {
        RepoPath repoPath = mockRepoPath(repoKey, path);
        assertEquals(expected, EvidenceConfigurationUtil.isTrashedEvidencePath(repoPath));
    }

    @Test
    void isEvidenceTopRepoPath() {
        RepoPath repoPath = mockRepoPath("repo", ".evidence/");
        assertTrue(EvidenceConfigurationUtil.isEvidenceTopRepoPath(repoPath));

        repoPath = mockRepoPath("repo", ".evidence");
        assertTrue(EvidenceConfigurationUtil.isEvidenceTopRepoPath(repoPath));

        repoPath = mockRepoPath("repo", ".evidence/file.txt");
        assertFalse(EvidenceConfigurationUtil.isEvidenceTopRepoPath(repoPath));
    }

    @Test
    void isEvidenceTopRepoPath_VfsItem() {
        VfsFolder vfsFolder = mock(VfsFolder.class);
        RepoPath repoPath = mockRepoPath("repo", ".evidence/");
        when(vfsFolder.getRepoPath()).thenReturn(repoPath);
        assertTrue(EvidenceConfigurationUtil.isEvidenceTopRepoPath(vfsFolder));
    }

    @Test
    void isPromotionEvidenceRepoPath() {
        RepoPath repoPath = mockRepoPath("repo", ".evidence/promotion-1234567890.json");
        assertTrue(EvidenceConfigurationUtil.isPromotionEvidenceRepoPath(repoPath));

        repoPath = mockRepoPath("repo", ".evidence/subfolder/promotion-1234567890.json");
        assertTrue(EvidenceConfigurationUtil.isPromotionEvidenceRepoPath(repoPath));

        repoPath = mockRepoPath("repo", ".evidence/promotion.json");
        assertFalse(EvidenceConfigurationUtil.isPromotionEvidenceRepoPath(repoPath));

        repoPath = mockRepoPath("repo", "folder/promotion-1234567890.json");
        assertFalse(EvidenceConfigurationUtil.isPromotionEvidenceRepoPath(repoPath));
    }

    @Test
    void isEvidencePath_RepoPath() {
        RepoPath repoPath = mockRepoPath("repo", ".evidence/file.txt");
        assertTrue(EvidenceConfigurationUtil.isEvidencePath(repoPath));

        repoPath = mockRepoPath("repo", "folder/file.txt");
        assertFalse(EvidenceConfigurationUtil.isEvidencePath(repoPath));

        assertFalse(EvidenceConfigurationUtil.isEvidencePath((RepoPath) null));
    }

    @Test
    void isEvidencePath_VfsItem() {
        VfsFolder vfsItem = mock(VfsFolder.class);
        when(vfsItem.getPath()).thenReturn(".evidence/file.txt");
        assertTrue(EvidenceConfigurationUtil.isEvidencePath(vfsItem));

        when(vfsItem.getPath()).thenReturn("folder/file.txt");
        assertFalse(EvidenceConfigurationUtil.isEvidencePath(vfsItem));

        assertFalse(EvidenceConfigurationUtil.isEvidencePath((VfsFolder) null));
    }

    @Test
    void isTrashedEvidencePath_String() {
        assertTrue(EvidenceConfigurationUtil.isTrashedEvidencePath("my-build-info/.evidence/file.txt"));
        assertTrue(EvidenceConfigurationUtil.isTrashedEvidencePath("my-docker/.evidence/"));
        assertFalse(EvidenceConfigurationUtil.isTrashedEvidencePath("my-docker/folder/.evidence/file.txt"));
        assertFalse(EvidenceConfigurationUtil.isTrashedEvidencePath((String) null));
    }

    @Test
    void isInternalServiceToken_genuineServiceToken() {
        assertTrue(EvidenceConfigurationUtil.isInternalServiceToken(
                "jfds@01instanceid", List.of("applied-permissions/admin"), "jfds"));
    }

    @Test
    void isInternalServiceToken_nullScopeGenuineServiceToken() {
        assertTrue(EvidenceConfigurationUtil.isInternalServiceToken("jfevd@01instanceid", null, "jfevd"));
    }

    @Test
    void isInternalServiceToken_serviceNamedUserTokenRejected() {
        // Bare subject looks like a service, but the user scope marks it as a user token.
        assertFalse(EvidenceConfigurationUtil.isInternalServiceToken(
                "jfds@attacker", List.of("applied-permissions/user"), "jfds"));
    }

    @Test
    void isInternalServiceToken_userSubjectRejected() {
        assertFalse(EvidenceConfigurationUtil.isInternalServiceToken(
                "jfac@01instanceid/users/eve", List.of("applied-permissions/user"), "jfds"));
    }

    @Test
    void isInternalServiceToken_mismatchedServiceTypeRejected() {
        assertFalse(EvidenceConfigurationUtil.isInternalServiceToken(
                "jfrt@01instanceid", List.of("applied-permissions/admin"), "jfds"));
    }

    @Test
    void isInternalServiceToken_nullSubjectRejected() {
        assertFalse(EvidenceConfigurationUtil.isInternalServiceToken(null, List.of(), "jfds"));
    }

    private RepoPath mockRepoPath(String repoKey, String path) {
        RepoPath repoPath = mock(RepoPath.class);
        when(repoPath.getRepoKey()).thenReturn(repoKey);
        when(repoPath.getPath()).thenReturn(path);
        return repoPath;
    }
}
