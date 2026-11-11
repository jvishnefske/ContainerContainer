/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.general.checksums;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.properties.PropertiesService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.checksum.ChecksumInfo;
import org.artifactory.checksum.ChecksumType;
import org.artifactory.checksum.ChecksumsInfo;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.fs.FileInfo;
import org.artifactory.md.Properties;
import org.artifactory.repo.RepoPath;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.mockito.ArgumentMatchers.any;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doReturn;

/**
 * Unit tests for {@link Checksums}, including RTDEV-80481: SHA-256-only deploy must not show
 * "Fix Checksum" or "Client did not publish a checksum value".
 */
class ChecksumsTest extends ArtifactoryHomeBoundJUnitTest {

    @BeforeEach
    void enableSha256Fix() {
        getOrCreateArtifactoryHomeStub().setProperty(
                ConstantValues.sha256ClientChecksumFix.getPropertyName(), "true");
    }

    private static final String VALID_MD5 = "d41d8cd98f00b204e9800998ecf8427e";
    private static final String VALID_SHA1 = "da39a3ee5e6b4b0d3255bfef95601890afd80709";
    private static final String VALID_SHA256 =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    private static FileInfo fileInfoWithChecksums(ChecksumsInfo checksumsInfo) {
        FileInfo fileInfo = mock(FileInfo.class);
        when(fileInfo.getChecksumsInfo()).thenReturn(checksumsInfo);
        RepoPath repoPath = mock(RepoPath.class);
        when(fileInfo.getRepoPath()).thenReturn(repoPath);
        return fileInfo;
    }

    private static LocalRepoDescriptor localNonDockerRepo() {
        LocalRepoDescriptor descriptor = mock(LocalRepoDescriptor.class);
        when(descriptor.isLocal()).thenReturn(true);
        RepoType repoType = mock(RepoType.class);
        when(repoType.isDockerGroup()).thenReturn(false);
        when(descriptor.getType()).thenReturn(repoType);
        return descriptor;
    }

    /**
     * Context that only provides PropertiesService (no auth). Used when SHA-256 is missing and
     * handleSha2 calls getSha2FromProperties.
     */
    private static ArtifactoryContext contextWithPropertiesServiceOnly() {
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        PropertiesService propertiesService = mock(PropertiesService.class);
        Properties emptyProperties = mock(Properties.class);
        doReturn(propertiesService).when(context).beanForType(PropertiesService.class);
        doReturn(emptyProperties).when(propertiesService).getProperties(any(RepoPath.class));
        return context;
    }

    @Test
    void sha256OnlyPresentAndOkDoesNotShowFixChecksum() {
        ChecksumsInfo checksumsInfo = new ChecksumsInfo();
        checksumsInfo.addChecksumInfo(new ChecksumInfo(ChecksumType.sha256,
                ChecksumInfo.TRUSTED_FILE_MARKER, VALID_SHA256, false));

        FileInfo fileInfo = fileInfoWithChecksums(checksumsInfo);
        LocalRepoDescriptor localRepoDescriptor = localNonDockerRepo();

        Checksums checksums = new Checksums();
        checksums.updateFileInfoCheckSum(fileInfo, localRepoDescriptor);

        assertFalse(checksums.isShowFixChecksums(),
                "Fix Checksum should not be shown when only SHA-256 is present and ok (RTDEV-80481)");
        assertNull(checksums.getMessage(),
                "Message should not contain 'Client did not publish' when SHA-256 is present");
    }

    @Test
    void md5AndSha1MissingButSha256PresentAndOkDoesNotShowFixChecksum() {
        ChecksumsInfo checksumsInfo = new ChecksumsInfo();
        checksumsInfo.addChecksumInfo(new ChecksumInfo(ChecksumType.sha256,
                ChecksumInfo.TRUSTED_FILE_MARKER, VALID_SHA256, false));

        FileInfo fileInfo = fileInfoWithChecksums(checksumsInfo);
        LocalRepoDescriptor localRepoDescriptor = localNonDockerRepo();

        Checksums checksums = new Checksums();
        checksums.updateFileInfoCheckSum(fileInfo, localRepoDescriptor);

        assertFalse(checksums.isShowFixChecksums(),
                "Fix Checksum should not be shown when only SHA-256 is present and ok");
        assertNull(checksums.getMessage(),
                "Message should not contain 'Client did not publish' when SHA-256 is present");
    }

    @Test
    void allThreePresentAndMatching() {
        ChecksumsInfo checksumsInfo = new ChecksumsInfo();
        checksumsInfo.addChecksumInfo(new ChecksumInfo(ChecksumType.md5, VALID_MD5, VALID_MD5, false));
        checksumsInfo.addChecksumInfo(new ChecksumInfo(ChecksumType.sha1, VALID_SHA1, VALID_SHA1, false));
        checksumsInfo.addChecksumInfo(new ChecksumInfo(ChecksumType.sha256,
                ChecksumInfo.TRUSTED_FILE_MARKER, VALID_SHA256, false));

        FileInfo fileInfo = fileInfoWithChecksums(checksumsInfo);
        LocalRepoDescriptor localRepoDescriptor = localNonDockerRepo();

        Checksums checksums = new Checksums();
        checksums.updateFileInfoCheckSum(fileInfo, localRepoDescriptor);

        assertFalse(checksums.isShowFixChecksums());
        assertNull(checksums.getMessage());
        assertNotNull(checksums.getSha2());
        assertTrue(checksums.getSha2().contains(VALID_SHA256));
        assertTrue(checksums.getSha2().contains("Identical") || checksums.getSha2().contains("Uploaded"));
        assertNotNull(checksums.getSha1());
        assertTrue(checksums.getSha1().contains(VALID_SHA1));
        assertNotNull(checksums.getMd5());
        assertTrue(checksums.getMd5().contains(VALID_MD5));
        assertEquals(VALID_SHA1, checksums.getSha1Value());
    }

    @Test
    void onlyMd5PresentAndMatching() {
        ChecksumsInfo checksumsInfo = new ChecksumsInfo();
        checksumsInfo.addChecksumInfo(new ChecksumInfo(ChecksumType.md5, VALID_MD5, VALID_MD5, false));

        FileInfo fileInfo = fileInfoWithChecksums(checksumsInfo);
        LocalRepoDescriptor localRepoDescriptor = localNonDockerRepo();

        ArtifactoryContext context = contextWithPropertiesServiceOnly();
        try (MockedStatic<ContextHelper> contextMock = mockStatic(ContextHelper.class)) {
            contextMock.when(ContextHelper::get).thenReturn(context);
            Checksums checksums = new Checksums();
            checksums.updateFileInfoCheckSum(fileInfo, localRepoDescriptor);
            assertFalse(checksums.isShowFixChecksums());
            assertNull(checksums.getMessage());
        }
    }

    @Test
    void onlySha1PresentAndMatching() {
        ChecksumsInfo checksumsInfo = new ChecksumsInfo();
        checksumsInfo.addChecksumInfo(new ChecksumInfo(ChecksumType.sha1, VALID_SHA1, VALID_SHA1, false));

        FileInfo fileInfo = fileInfoWithChecksums(checksumsInfo);
        LocalRepoDescriptor localRepoDescriptor = localNonDockerRepo();

        ArtifactoryContext context = contextWithPropertiesServiceOnly();
        try (MockedStatic<ContextHelper> contextMock = mockStatic(ContextHelper.class)) {
            contextMock.when(ContextHelper::get).thenReturn(context);
            Checksums checksums = new Checksums();
            checksums.updateFileInfoCheckSum(fileInfo, localRepoDescriptor);
            assertFalse(checksums.isShowFixChecksums());
            assertNull(checksums.getMessage());
            assertEquals(VALID_SHA1, checksums.getSha1Value());
        }
    }

    @Test
    void sha1ValueSetWhenSha1Present() {
        ChecksumsInfo checksumsInfo = new ChecksumsInfo();
        checksumsInfo.addChecksumInfo(new ChecksumInfo(ChecksumType.sha1, VALID_SHA1, VALID_SHA1, false));

        FileInfo fileInfo = fileInfoWithChecksums(checksumsInfo);
        LocalRepoDescriptor localRepoDescriptor = localNonDockerRepo();

        ArtifactoryContext context = contextWithPropertiesServiceOnly();
        try (MockedStatic<ContextHelper> contextMock = mockStatic(ContextHelper.class)) {
            contextMock.when(ContextHelper::get).thenReturn(context);
            Checksums checksums = new Checksums();
            checksums.updateFileInfoCheckSum(fileInfo, localRepoDescriptor);
            assertEquals(VALID_SHA1, checksums.getSha1Value());
        }
    }

    @Test
    void allChecksumsMissingLocalShowsFixAndMessage() {
        ChecksumsInfo checksumsInfo = new ChecksumsInfo();
        FileInfo fileInfo = fileInfoWithChecksums(checksumsInfo);
        RepoPath repoPath = fileInfo.getRepoPath();
        LocalRepoDescriptor localRepoDescriptor = localNonDockerRepo();

        ArtifactoryContext context = mock(ArtifactoryContext.class);
        AuthorizationService authService = mock(AuthorizationService.class);
        SecurityService securityService = mock(SecurityService.class);
        PropertiesService propertiesService = mock(PropertiesService.class);
        when(propertiesService.getProperties(any(RepoPath.class))).thenReturn(mock(Properties.class));
        when(context.getAuthorizationService()).thenReturn(authService);
        when(context.beanForType(SecurityService.class)).thenReturn(securityService);
        when(context.beanForType(PropertiesService.class)).thenReturn(propertiesService);
        when(authService.canDeploy(repoPath)).thenReturn(true);
        when(securityService.isAnonymous()).thenReturn(false);

        try (MockedStatic<ContextHelper> contextMock = mockStatic(ContextHelper.class)) {
            contextMock.when(ContextHelper::get).thenReturn(context);
            Checksums checksums = new Checksums();
            checksums.updateFileInfoCheckSum(fileInfo, localRepoDescriptor);
            assertTrue(checksums.isShowFixChecksums());
            assertNotNull(checksums.getMessage());
            assertTrue(checksums.getMessage().contains("Client did not publish a checksum value"));
            assertTrue(checksums.getMessage().contains("Fix Checksum"));
        }
    }

    @Test
    void allChecksumsMissingRemoteShowsMessage() {
        ChecksumsInfo checksumsInfo = new ChecksumsInfo();
        FileInfo fileInfo = fileInfoWithChecksums(checksumsInfo);
        LocalRepoDescriptor remoteDescriptor = mock(LocalRepoDescriptor.class);
        when(remoteDescriptor.isLocal()).thenReturn(false);
        RepoType repoType = mock(RepoType.class);
        when(repoType.isDockerGroup()).thenReturn(false);
        when(remoteDescriptor.getType()).thenReturn(repoType);

        ArtifactoryContext context = mock(ArtifactoryContext.class);
        AuthorizationService authService = mock(AuthorizationService.class);
        SecurityService securityService = mock(SecurityService.class);
        PropertiesService propertiesService = mock(PropertiesService.class);
        when(propertiesService.getProperties(any(RepoPath.class))).thenReturn(mock(Properties.class));
        when(context.getAuthorizationService()).thenReturn(authService);
        when(context.beanForType(SecurityService.class)).thenReturn(securityService);
        when(context.beanForType(PropertiesService.class)).thenReturn(propertiesService);

        try (MockedStatic<ContextHelper> contextMock = mockStatic(ContextHelper.class)) {
            contextMock.when(ContextHelper::get).thenReturn(context);
            Checksums checksums = new Checksums();
            checksums.updateFileInfoCheckSum(fileInfo, remoteDescriptor);
            assertNotNull(checksums.getMessage());
            assertTrue(checksums.getMessage().contains("Remote checksum doesn't exist"));
        }
    }

    @Test
    void allChecksumsBrokenShowsMismatchMessage() {
        String brokenMd5 = "00000000000000000000000000000000";
        String brokenSha1 = "0000000000000000000000000000000000000000";
        ChecksumsInfo checksumsInfo = new ChecksumsInfo();
        checksumsInfo.addChecksumInfo(new ChecksumInfo(ChecksumType.md5, VALID_MD5, brokenMd5, false));
        checksumsInfo.addChecksumInfo(new ChecksumInfo(ChecksumType.sha1, VALID_SHA1, brokenSha1, false));

        FileInfo fileInfo = fileInfoWithChecksums(checksumsInfo);
        LocalRepoDescriptor localRepoDescriptor = localNonDockerRepo();
        RepoPath repoPath = fileInfo.getRepoPath();

        ArtifactoryContext context = mock(ArtifactoryContext.class);
        AuthorizationService authService = mock(AuthorizationService.class);
        SecurityService securityService = mock(SecurityService.class);
        PropertiesService propertiesService = mock(PropertiesService.class);
        when(propertiesService.getProperties(any(RepoPath.class))).thenReturn(mock(Properties.class));
        when(context.getAuthorizationService()).thenReturn(authService);
        when(context.beanForType(SecurityService.class)).thenReturn(securityService);
        when(context.beanForType(PropertiesService.class)).thenReturn(propertiesService);
        when(authService.canDeploy(repoPath)).thenReturn(true);
        when(securityService.isAnonymous()).thenReturn(false);

        try (MockedStatic<ContextHelper> contextMock = mockStatic(ContextHelper.class)) {
            contextMock.when(ContextHelper::get).thenReturn(context);
            Checksums checksums = new Checksums();
            checksums.updateFileInfoCheckSum(fileInfo, localRepoDescriptor);
            assertNotNull(checksums.getMessage());
            assertTrue(checksums.getMessage().contains("doesn't match the actual checksum"));
            assertTrue(checksums.getMessage().contains("Redeploy")
                    || checksums.getMessage().contains("redeploy")
                    || checksums.getMessage().contains("correct checksum"));
        }
    }

    @Test
    void dockerGroupRepoDoesNotShowFixWhenChecksumsNotOk() {
        ChecksumsInfo checksumsInfo = new ChecksumsInfo();
        FileInfo fileInfo = fileInfoWithChecksums(checksumsInfo);
        RepoPath repoPath = fileInfo.getRepoPath();

        LocalRepoDescriptor dockerGroupDescriptor = mock(LocalRepoDescriptor.class);
        when(dockerGroupDescriptor.isLocal()).thenReturn(true);
        RepoType repoType = mock(RepoType.class);
        when(repoType.isDockerGroup()).thenReturn(true);
        when(dockerGroupDescriptor.getType()).thenReturn(repoType);

        ArtifactoryContext context = mock(ArtifactoryContext.class);
        AuthorizationService authService = mock(AuthorizationService.class);
        SecurityService securityService = mock(SecurityService.class);
        PropertiesService propertiesService = mock(PropertiesService.class);
        when(propertiesService.getProperties(any(RepoPath.class))).thenReturn(mock(Properties.class));
        when(context.getAuthorizationService()).thenReturn(authService);
        when(context.beanForType(SecurityService.class)).thenReturn(securityService);
        when(context.beanForType(PropertiesService.class)).thenReturn(propertiesService);
        when(authService.canDeploy(repoPath)).thenReturn(true);
        when(securityService.isAnonymous()).thenReturn(false);

        try (MockedStatic<ContextHelper> contextMock = mockStatic(ContextHelper.class)) {
            contextMock.when(ContextHelper::get).thenReturn(context);
            Checksums checksums = new Checksums();
            checksums.updateFileInfoCheckSum(fileInfo, dockerGroupDescriptor);
            assertFalse(checksums.isShowFixChecksums());
            assertNotNull(checksums.getMessage());
        }
    }

    @Test
    void anonymousUserNoFixButtonInMessage() {
        ChecksumsInfo checksumsInfo = new ChecksumsInfo();
        FileInfo fileInfo = fileInfoWithChecksums(checksumsInfo);
        RepoPath repoPath = fileInfo.getRepoPath();
        LocalRepoDescriptor localRepoDescriptor = localNonDockerRepo();

        ArtifactoryContext context = mock(ArtifactoryContext.class);
        AuthorizationService authService = mock(AuthorizationService.class);
        SecurityService securityService = mock(SecurityService.class);
        PropertiesService propertiesService = mock(PropertiesService.class);
        when(propertiesService.getProperties(any(RepoPath.class))).thenReturn(mock(Properties.class));
        when(context.getAuthorizationService()).thenReturn(authService);
        when(context.beanForType(SecurityService.class)).thenReturn(securityService);
        when(context.beanForType(PropertiesService.class)).thenReturn(propertiesService);
        when(authService.canDeploy(repoPath)).thenReturn(true);
        when(securityService.isAnonymous()).thenReturn(true);

        try (MockedStatic<ContextHelper> contextMock = mockStatic(ContextHelper.class)) {
            contextMock.when(ContextHelper::get).thenReturn(context);
            Checksums checksums = new Checksums();
            checksums.updateFileInfoCheckSum(fileInfo, localRepoDescriptor);
            assertNotNull(checksums.getMessage());
            assertTrue(checksums.getMessage().contains("Client did not publish a checksum value"));
            assertFalse(checksums.getMessage().contains("Fix Checksum"));
        }
    }

    @Test
    void onlySha256PresentAndBrokenShowsMismatchMessage() {
        String wrongSha256 = "0000000000000000000000000000000000000000000000000000000000000000";
        ChecksumsInfo checksumsInfo = new ChecksumsInfo();
        checksumsInfo.addChecksumInfo(new ChecksumInfo(ChecksumType.sha256, VALID_SHA256, wrongSha256, false));

        FileInfo fileInfo = fileInfoWithChecksums(checksumsInfo);
        LocalRepoDescriptor localRepoDescriptor = localNonDockerRepo();
        RepoPath repoPath = fileInfo.getRepoPath();

        ArtifactoryContext context = mock(ArtifactoryContext.class);
        AuthorizationService authService = mock(AuthorizationService.class);
        SecurityService securityService = mock(SecurityService.class);
        PropertiesService propertiesService = mock(PropertiesService.class);
        when(propertiesService.getProperties(any(RepoPath.class))).thenReturn(mock(Properties.class));
        when(context.getAuthorizationService()).thenReturn(authService);
        when(context.beanForType(SecurityService.class)).thenReturn(securityService);
        when(context.beanForType(PropertiesService.class)).thenReturn(propertiesService);
        when(authService.canDeploy(repoPath)).thenReturn(true);
        when(securityService.isAnonymous()).thenReturn(false);

        try (MockedStatic<ContextHelper> contextMock = mockStatic(ContextHelper.class)) {
            contextMock.when(ContextHelper::get).thenReturn(context);
            Checksums checksums = new Checksums();
            checksums.updateFileInfoCheckSum(fileInfo, localRepoDescriptor);
            assertNotNull(checksums.getMessage());
            assertTrue(checksums.getMessage().contains("doesn't match the actual checksum"));
        }
    }

    @Test
    void md5MatchSha1MismatchShowsFixAndMessage() {
        String brokenSha1 = "0000000000000000000000000000000000000000";
        ChecksumsInfo checksumsInfo = new ChecksumsInfo();
        checksumsInfo.addChecksumInfo(new ChecksumInfo(ChecksumType.md5, VALID_MD5, VALID_MD5, false));
        checksumsInfo.addChecksumInfo(new ChecksumInfo(ChecksumType.sha1, VALID_SHA1, brokenSha1, false));

        FileInfo fileInfo = fileInfoWithChecksums(checksumsInfo);
        LocalRepoDescriptor localRepoDescriptor = localNonDockerRepo();
        RepoPath repoPath = fileInfo.getRepoPath();

        ArtifactoryContext context = mock(ArtifactoryContext.class);
        AuthorizationService authService = mock(AuthorizationService.class);
        SecurityService securityService = mock(SecurityService.class);
        PropertiesService propertiesService = mock(PropertiesService.class);
        when(propertiesService.getProperties(any(RepoPath.class))).thenReturn(mock(Properties.class));
        when(context.getAuthorizationService()).thenReturn(authService);
        when(context.beanForType(SecurityService.class)).thenReturn(securityService);
        when(context.beanForType(PropertiesService.class)).thenReturn(propertiesService);
        when(authService.canDeploy(repoPath)).thenReturn(true);
        when(securityService.isAnonymous()).thenReturn(false);

        try (MockedStatic<ContextHelper> contextMock = mockStatic(ContextHelper.class)) {
            contextMock.when(ContextHelper::get).thenReturn(context);
            Checksums checksums = new Checksums();
            checksums.updateFileInfoCheckSum(fileInfo, localRepoDescriptor);
            assertTrue(checksums.isShowFixChecksums());
            assertNotNull(checksums.getMessage());
            assertTrue(checksums.getMessage().contains("doesn't match the actual checksum"));
        }
    }

    @Test
    void md5AndSha1MatchSha256MismatchShowsFixAndMessage() {
        String wrongSha256 = "0000000000000000000000000000000000000000000000000000000000000000";
        ChecksumsInfo checksumsInfo = new ChecksumsInfo();
        checksumsInfo.addChecksumInfo(new ChecksumInfo(ChecksumType.md5, VALID_MD5, VALID_MD5, false));
        checksumsInfo.addChecksumInfo(new ChecksumInfo(ChecksumType.sha1, VALID_SHA1, VALID_SHA1, false));
        checksumsInfo.addChecksumInfo(new ChecksumInfo(ChecksumType.sha256, VALID_SHA256, wrongSha256, false));

        FileInfo fileInfo = fileInfoWithChecksums(checksumsInfo);
        LocalRepoDescriptor localRepoDescriptor = localNonDockerRepo();
        RepoPath repoPath = fileInfo.getRepoPath();

        ArtifactoryContext context = mock(ArtifactoryContext.class);
        AuthorizationService authService = mock(AuthorizationService.class);
        SecurityService securityService = mock(SecurityService.class);
        PropertiesService propertiesService = mock(PropertiesService.class);
        when(propertiesService.getProperties(any(RepoPath.class))).thenReturn(mock(Properties.class));
        when(context.getAuthorizationService()).thenReturn(authService);
        when(context.beanForType(SecurityService.class)).thenReturn(securityService);
        when(context.beanForType(PropertiesService.class)).thenReturn(propertiesService);
        when(authService.canDeploy(repoPath)).thenReturn(true);
        when(securityService.isAnonymous()).thenReturn(false);

        try (MockedStatic<ContextHelper> contextMock = mockStatic(ContextHelper.class)) {
            contextMock.when(ContextHelper::get).thenReturn(context);
            Checksums checksums = new Checksums();
            checksums.updateFileInfoCheckSum(fileInfo, localRepoDescriptor);
            assertTrue(checksums.isShowFixChecksums());
            assertNotNull(checksums.getMessage());
            assertTrue(checksums.getMessage().contains("doesn't match the actual checksum"));
        }
    }

    @Test
    void sha256MatchMd5MismatchShowsFixAndMessage() {
        String brokenMd5 = "00000000000000000000000000000000";
        ChecksumsInfo checksumsInfo = new ChecksumsInfo();
        checksumsInfo.addChecksumInfo(new ChecksumInfo(ChecksumType.md5, VALID_MD5, brokenMd5, false));
        checksumsInfo.addChecksumInfo(new ChecksumInfo(ChecksumType.sha256,
                ChecksumInfo.TRUSTED_FILE_MARKER, VALID_SHA256, false));

        FileInfo fileInfo = fileInfoWithChecksums(checksumsInfo);
        LocalRepoDescriptor localRepoDescriptor = localNonDockerRepo();
        RepoPath repoPath = fileInfo.getRepoPath();

        ArtifactoryContext context = mock(ArtifactoryContext.class);
        AuthorizationService authService = mock(AuthorizationService.class);
        SecurityService securityService = mock(SecurityService.class);
        PropertiesService propertiesService = mock(PropertiesService.class);
        when(propertiesService.getProperties(any(RepoPath.class))).thenReturn(mock(Properties.class));
        when(context.getAuthorizationService()).thenReturn(authService);
        when(context.beanForType(SecurityService.class)).thenReturn(securityService);
        when(context.beanForType(PropertiesService.class)).thenReturn(propertiesService);
        when(authService.canDeploy(repoPath)).thenReturn(true);
        when(securityService.isAnonymous()).thenReturn(false);

        try (MockedStatic<ContextHelper> contextMock = mockStatic(ContextHelper.class)) {
            contextMock.when(ContextHelper::get).thenReturn(context);
            Checksums checksums = new Checksums();
            checksums.updateFileInfoCheckSum(fileInfo, localRepoDescriptor);
            assertTrue(checksums.isShowFixChecksums());
            assertNotNull(checksums.getMessage());
            assertTrue(checksums.getMessage().contains("doesn't match the actual checksum"));
        }
    }

    // ---- Feature flag disabled (sha256.client.checksum.fix=false) tests ----

    @Test
    void sha256OnlyPresent_flagDisabled_showsFixChecksum() {
        getOrCreateArtifactoryHomeStub().setProperty(
                ConstantValues.sha256ClientChecksumFix.getPropertyName(), "false");
        ChecksumsInfo checksumsInfo = new ChecksumsInfo();
        checksumsInfo.addChecksumInfo(new ChecksumInfo(ChecksumType.sha256,
                ChecksumInfo.TRUSTED_FILE_MARKER, VALID_SHA256, false));

        FileInfo fileInfo = fileInfoWithChecksums(checksumsInfo);
        LocalRepoDescriptor localRepoDescriptor = localNonDockerRepo();
        RepoPath repoPath = fileInfo.getRepoPath();

        ArtifactoryContext context = mock(ArtifactoryContext.class);
        AuthorizationService authService = mock(AuthorizationService.class);
        SecurityService securityService = mock(SecurityService.class);
        PropertiesService propertiesService = mock(PropertiesService.class);
        when(propertiesService.getProperties(any(RepoPath.class))).thenReturn(mock(Properties.class));
        when(context.getAuthorizationService()).thenReturn(authService);
        when(context.beanForType(SecurityService.class)).thenReturn(securityService);
        when(context.beanForType(PropertiesService.class)).thenReturn(propertiesService);
        when(authService.canDeploy(repoPath)).thenReturn(true);
        when(securityService.isAnonymous()).thenReturn(false);

        try (MockedStatic<ContextHelper> contextMock = mockStatic(ContextHelper.class)) {
            contextMock.when(ContextHelper::get).thenReturn(context);
            Checksums checksums = new Checksums();
            checksums.updateFileInfoCheckSum(fileInfo, localRepoDescriptor);
            assertTrue(checksums.isShowFixChecksums(),
                    "With flag disabled, sha256-only artifact is treated as missing checksum (old behavior)");
            assertNotNull(checksums.getMessage());
            assertTrue(checksums.getMessage().contains("Client did not publish a checksum value"));
        }
    }

    @Test
    void sha256BrokenOnly_flagDisabled_noMismatchShown() {
        getOrCreateArtifactoryHomeStub().setProperty(
                ConstantValues.sha256ClientChecksumFix.getPropertyName(), "false");
        String wrongSha256 = "0000000000000000000000000000000000000000000000000000000000000000";
        ChecksumsInfo checksumsInfo = new ChecksumsInfo();
        checksumsInfo.addChecksumInfo(new ChecksumInfo(ChecksumType.sha256, VALID_SHA256, wrongSha256, false));

        FileInfo fileInfo = fileInfoWithChecksums(checksumsInfo);
        LocalRepoDescriptor localRepoDescriptor = localNonDockerRepo();
        RepoPath repoPath = fileInfo.getRepoPath();

        ArtifactoryContext context = mock(ArtifactoryContext.class);
        AuthorizationService authService = mock(AuthorizationService.class);
        SecurityService securityService = mock(SecurityService.class);
        PropertiesService propertiesService = mock(PropertiesService.class);
        when(propertiesService.getProperties(any(RepoPath.class))).thenReturn(mock(Properties.class));
        when(context.getAuthorizationService()).thenReturn(authService);
        when(context.beanForType(SecurityService.class)).thenReturn(securityService);
        when(context.beanForType(PropertiesService.class)).thenReturn(propertiesService);
        when(authService.canDeploy(repoPath)).thenReturn(true);
        when(securityService.isAnonymous()).thenReturn(false);

        try (MockedStatic<ContextHelper> contextMock = mockStatic(ContextHelper.class)) {
            contextMock.when(ContextHelper::get).thenReturn(context);
            Checksums checksums = new Checksums();
            checksums.updateFileInfoCheckSum(fileInfo, localRepoDescriptor);
            assertFalse(checksums.getMessage() != null
                            && checksums.getMessage().contains("doesn't match the actual checksum"),
                    "With flag disabled, sha256 mismatch must not trigger mismatch message");
        }
    }

    @Test
    void sha1AndMd5Ok_sha256Broken_flagDisabled_noFixShown() {
        getOrCreateArtifactoryHomeStub().setProperty(
                ConstantValues.sha256ClientChecksumFix.getPropertyName(), "false");
        String wrongSha256 = "0000000000000000000000000000000000000000000000000000000000000000";
        ChecksumsInfo checksumsInfo = new ChecksumsInfo();
        checksumsInfo.addChecksumInfo(new ChecksumInfo(ChecksumType.md5, VALID_MD5, VALID_MD5, false));
        checksumsInfo.addChecksumInfo(new ChecksumInfo(ChecksumType.sha1, VALID_SHA1, VALID_SHA1, false));
        checksumsInfo.addChecksumInfo(new ChecksumInfo(ChecksumType.sha256, VALID_SHA256, wrongSha256, false));

        FileInfo fileInfo = fileInfoWithChecksums(checksumsInfo);
        LocalRepoDescriptor localRepoDescriptor = localNonDockerRepo();

        ArtifactoryContext context = contextWithPropertiesServiceOnly();
        try (MockedStatic<ContextHelper> contextMock = mockStatic(ContextHelper.class)) {
            contextMock.when(ContextHelper::get).thenReturn(context);
            Checksums checksums = new Checksums();
            checksums.updateFileInfoCheckSum(fileInfo, localRepoDescriptor);
            assertFalse(checksums.isShowFixChecksums(),
                    "With flag disabled, sha256 mismatch when sha1+md5 are ok must not show fix button");
            assertNull(checksums.getMessage(),
                    "With flag disabled, sha256 mismatch when sha1+md5 are ok must not show a message");
        }
    }
}