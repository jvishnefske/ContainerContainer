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

package org.artifactory.repo.db;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.checksum.ChecksumType;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.fs.FileInfo;
import org.artifactory.model.LocalCacheRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.repo.LocalCacheRepo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.cache.expirable.CacheExpiry;
import org.artifactory.repo.local.LocalNonCacheOverridables;
import org.artifactory.repo.local.PathDeletionContext;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * @author Noam Y. Tenne
 * @author Yossi Shaul
 */
@Test
public class DbStoringRepoMixinDeleteProtectionTest extends ArtifactoryHomeBoundTest {
    private static final String REPO_KEY = "repoKey";

    private LocalCacheRepo storingRepo = mock(LocalCacheRepo.class);
    private ArtifactoryContext context = mock(ArtifactoryContext.class);
    private LocalRepoDescriptor localRepoDescriptor = new LocalRepoDescriptor();

    private DbStoringRepoMixin storingRepoMixin =
            new DbStoringRepoMixin<>(localRepoDescriptor, null, null);

    @BeforeMethod
    public void setUp() throws Exception {
        ArtifactoryContextThreadBinder.bind(context);
        localRepoDescriptor.setKey(REPO_KEY);
    }

    @AfterMethod
    public void tearDown() {
        ArtifactoryContextThreadBinder.unbind();
    }

    public void testChecksumProtection() {
        for (ChecksumType checksumType : ChecksumType.BASE_CHECKSUM_TYPES) {
            PathDeletionContext deletionContext = new PathDeletionContext.Builder(storingRepo,
                    checksumType.ext()).assertOverwrite(true).build();
            assertFalse(storingRepoMixin.shouldProtectPathDeletion(deletionContext),
                    "Checksum should never be protected.");
            deletionContext = new PathDeletionContext.Builder(storingRepo, checksumType.ext()).assertOverwrite(false)
                    .build();
            assertFalse(storingRepoMixin.shouldProtectPathDeletion(deletionContext),
                    "Checksum should never be protected.");
        }
    }

    public void testNonExpirableIsDeleteProtected() {
        PathDeletionContext deletionContext = new PathDeletionContext.Builder(storingRepo, "somepath")
                .assertOverwrite(false).build();
        assertTrue(storingRepoMixin.shouldProtectPathDeletion(deletionContext),
                "Non-checksum item should never be protected when not overriding.");
    }

    public void testCacheRepoExpirableIsNotDeleteProtected() {
        LocalCacheRepoConfig repoConfig =
                LocalCacheRepoConfig.create(RemoteRepoConfig.create("repoKey", RepoType.Generic));
        CacheExpiry expiry = mock(CacheExpiry.class);
        when(storingRepo.isCache()).thenReturn(true);
        when(storingRepo.getRepoConfig()).thenReturn(repoConfig);
        when(context.beanForType(CacheExpiry.class)).thenReturn(expiry);
        InternalRepositoryService repoService = createAndSetMockRepoService();
        when(repoService.storingRepositoryByKey(REPO_KEY)).thenReturn(storingRepo);
        PathDeletionContext deletionContext = new PathDeletionContext.Builder(storingRepo, "somepath")
                .assertOverwrite(true).build();
        assertFalse(storingRepoMixin.shouldProtectPathDeletion(deletionContext),
                "Expired path shouldn't be protected.");
    }

    public void testLocalNotCacheAndOverridableProtection() {
        when(storingRepo.isCache()).thenReturn(false);
        LocalNonCacheOverridables overridables = mock(LocalNonCacheOverridables.class);
        when(overridables.isOverridable(storingRepo, "somepath")).thenReturn(true);
        when(context.beanForType(LocalNonCacheOverridables.class)).thenReturn(overridables);
        InternalRepositoryService repoService = createAndSetMockRepoService();
        when(repoService.storingRepositoryByKey(REPO_KEY)).thenReturn(storingRepo);

        PathDeletionContext deletionContext = new PathDeletionContext.Builder(storingRepo, "somepath")
                .assertOverwrite(true).build();
        assertFalse(storingRepoMixin.shouldProtectPathDeletion(deletionContext),
                "Overridable path shouldn't be protected.");
    }

    public void testLocalNotCacheAndMetadataProtection() {
        InternalRepositoryService repoService = createAndSetMockRepoService();
        when(repoService.storingRepositoryByKey(REPO_KEY)).thenReturn(storingRepo);

        PathDeletionContext deletionContext = new PathDeletionContext.Builder(storingRepo, "maven-metadata.xml")
                .assertOverwrite(true).build();
        assertFalse(storingRepoMixin.shouldProtectPathDeletion(deletionContext),
                "Metadata path shouldn't be protected.");
    }

    public void testLocalNotCacheAndNotFileProtection() {
        when(storingRepo.isCache()).thenReturn(false);
        LocalNonCacheOverridables overridables = mock(LocalNonCacheOverridables.class);
        when(overridables.isOverridable(storingRepo, "somefile")).thenReturn(false);
        when(context.beanForType(LocalNonCacheOverridables.class)).thenReturn(overridables);
        InternalRepositoryService repoService = createAndSetMockRepoService();
        when(repoService.exists(new RepoPathImpl(REPO_KEY, "somefile"))).thenReturn(false);
        when(repoService.storingRepositoryByKey(REPO_KEY)).thenReturn(storingRepo);
        PathDeletionContext deletionContext = new PathDeletionContext.Builder(storingRepo, "somefile")
                .assertOverwrite(true).build();
        assertFalse(storingRepoMixin.shouldProtectPathDeletion(deletionContext),
                "Items which aren't files shouldn't be protected.");
    }

    @Test(dataProvider = "testIsFileOverwriteEnforceChecksumDeployPermissionCheckDataProvider")
    public void testIsFileOverwriteEnforceChecksumDeployPermissionCheck(
            boolean enforcePermissionCheck, String providedSha1, String providedSha2, boolean expectedIsFileOverwrite) {
        setStringSystemProperty(ConstantValues.enforcePermissionCheckOnIdenticalChecksumDeploy,
                String.valueOf(enforcePermissionCheck));
        InternalRepositoryService repoService = createAndSetMockRepoService();
        setRepositoryService(storingRepoMixin, repoService);
        FileInfo fileInfo = mock(FileInfo.class);
        final RepoPath repoPath = InternalRepoPathFactory.create(REPO_KEY, "path");
        when(repoService.getFileInfo(repoPath)).thenReturn(fileInfo);
        when(fileInfo.getSha1()).thenReturn("existingSha1");
        when(fileInfo.getSha2()).thenReturn("existingSha2");
        PathDeletionContext pathDeletionContext = mockPathDeletionContext("path", providedSha1, providedSha2);

        boolean isFileOverwrite = storingRepoMixin.isFileOverwrite(pathDeletionContext);

        assertEquals(isFileOverwrite, expectedIsFileOverwrite);

        setStringSystemProperty(ConstantValues.enforcePermissionCheckOnIdenticalChecksumDeploy,
                ConstantValues.enforcePermissionCheckOnIdenticalChecksumDeploy.getDefValue());
    }

    public void pathDeletionTestNullSafteyCheck() {
        InternalRepositoryService repositoryService = mock(InternalRepositoryService.class);
        setRepositoryService(storingRepoMixin, repositoryService);
        FileInfo fileInfo = mock(FileInfo.class);
        final RepoPath repoPath = InternalRepoPathFactory.create(REPO_KEY, "path");

        //null safety checks
        when(repositoryService.getFileInfo(repoPath)).thenReturn(fileInfo);
        when(fileInfo.getSha1()).thenReturn("sha1");
        when(fileInfo.getSha2()).thenReturn(null);
        PathDeletionContext pathDeletionContext = mockPathDeletionContext("path", "sha1", "sha2");
        assertTrue(storingRepoMixin.isFileOverwrite(pathDeletionContext),
                "existing sha2 is null while request has sha2 - this is an overwrite");

        when(repositoryService.getFileInfo(repoPath)).thenReturn(fileInfo);
        when(fileInfo.getSha1()).thenReturn("sha1");
        when(fileInfo.getSha2()).thenReturn(null);
        pathDeletionContext = mockPathDeletionContext("path", "sha1", null);
        assertFalse(storingRepoMixin.isFileOverwrite(pathDeletionContext),
                "no existing sha2 and request did not pass sha2 - this is not an overwrite");

        when(repositoryService.getFileInfo(repoPath)).thenReturn(fileInfo);
        when(fileInfo.getSha1()).thenReturn(null);
        when(fileInfo.getSha2()).thenReturn("sha2");
        pathDeletionContext = mockPathDeletionContext("path", "sha1", "sha2");
        assertTrue(storingRepoMixin.isFileOverwrite(pathDeletionContext),
                "existing sha1 is null while request has sha1 - this is an overwrite");

        when(repositoryService.getFileInfo(repoPath)).thenReturn(fileInfo);
        when(fileInfo.getSha1()).thenReturn(null);
        when(fileInfo.getSha2()).thenReturn("sha2");
        pathDeletionContext = mockPathDeletionContext("path", null, "sha2");
        assertFalse(storingRepoMixin.isFileOverwrite(pathDeletionContext),
                "no existing sha1 and request did not pass sha1 - this is not an overwrite");
    }

    @DataProvider
    private Object[][] testIsFileOverwriteEnforceChecksumDeployPermissionCheckDataProvider() {
        return new Object[][] {
                {true, "sha1", null, true},
                {true, "existingSha2", "existingSha2", true},
                {true, null, "sha2", true},
                {false, "sha1", null, true},
                {false, "existingSha1", "existingSha2", false},
                {false, null, "sha2", true},
                {false, "sha1", "sha2", true}
        };
    }

    private InternalRepositoryService createAndSetMockRepoService() {
        InternalRepositoryService repositoryService = mock(InternalRepositoryService.class);
        setRepositoryService(storingRepoMixin, repositoryService);
        return repositoryService;
    }

    private void setRepositoryService(DbStoringRepoMixin repoMixin, RepositoryService repoService) {
        ReflectionTestUtils.setField(repoMixin, "repositoryService", repoService);
    }

    private PathDeletionContext mockPathDeletionContext(String path, String requestSha1, String requestSha2) {
        return new PathDeletionContext.Builder(null, path, null).requestSha1(requestSha1).requestSha2(requestSha2)
                .build();
    }
}
