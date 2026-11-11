package org.artifactory.ph.curation;

import com.jfrog.ph.acl.exception.PackageException;
import com.jfrog.ph.acl.model.curation.CurationPackageForResponse;
import com.jfrog.ph.acl.model.curation.CurationResponse;
import com.jfrog.ph.acl.service.PackageRepoService;
import org.artifactory.repo.RemoteRepo;
import org.artifactory.repo.service.InternalRepositoryService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.artifactory.ph.curation.PackagesCurationCompliantVersionUtil.CurationLockKind;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PackagesCurationCompliantVersionUtilTest {
    @Test
    void testGetPassThroughCuratedLockKey() {
        String result = PackagesCurationCompliantVersionUtil.getCuratedLockKey("test-repo", "path/to/file",
                CurationLockKind.PASS_THROUGH);

        assertEquals("curated-lock/test-repo/path/to/file.passThrough", result);
    }

    @Test
    void testGetCuratedLockKey() {
        String result = PackagesCurationCompliantVersionUtil.getCuratedLockKey("test-repo", "path/to/file",
                CurationLockKind.BUILD);

        assertEquals("curated-lock/test-repo/path/to/file", result);
    }

    @Test
    void testExtractRemoteUrlThrowsExceptionShouldReturnNull() throws PackageException {

        PackageRepoService packageRepoService = mock(PackageRepoService.class);
        // Given
        when(packageRepoService.getRemoteRepoBaseUrl("test-repo"))
                .thenThrow(new PackageException(500, "Failed to get remote URL", "Failed to get remote URL"));

        // When
        String result = PackagesCurationCompliantVersionUtil.extractRemoteUrl("test-repo", packageRepoService);

        // Then
        assertNull(result);
        verify(packageRepoService).getRemoteRepoBaseUrl("test-repo");
    }

    @Test
    void testGetRepoKeyWithoutCacheRemoteRepoExists() {
        // Given
        InternalRepositoryService repositoryService = mock(InternalRepositoryService.class);
        RemoteRepo<?, ?> mockRemoteRepo = mock(RemoteRepo.class);
        when(repositoryService.remoteRepositoryByKey("test-repo")).thenReturn(mockRemoteRepo);

        // When
        String result =
                PackagesCurationCompliantVersionUtil.getRepoKeyWithoutCache("test-repo-cache", repositoryService);

        // Then
        assertEquals("test-repo", result);
        verify(repositoryService).remoteRepositoryByKey("test-repo");
    }

    @Test
    void testGetRepoKeyWithoutCacheRemoteRepoNotFound() {
        // Given
        InternalRepositoryService repositoryService = mock(InternalRepositoryService.class);
        when(repositoryService.remoteRepositoryByKey("test-repo")).thenReturn(null);

        // When
        String result =
                PackagesCurationCompliantVersionUtil.getRepoKeyWithoutCache("test-repo-cache", repositoryService);

        // Then
        assertEquals("test-repo-cache", result);
        verify(repositoryService).remoteRepositoryByKey("test-repo");
    }

    @Test
    void testGetRepoKeyWithoutCacheNoCacheSuffix() {
        // Given
        InternalRepositoryService repositoryService = mock(InternalRepositoryService.class);
        RemoteRepo<?, ?> mockRemoteRepo = mock(RemoteRepo.class);
        when(repositoryService.remoteRepositoryByKey("test-repo")).thenReturn(mockRemoteRepo);

        // When
        String result = PackagesCurationCompliantVersionUtil.getRepoKeyWithoutCache("test-repo", repositoryService);

        // Then
        assertEquals("test-repo", result);
        verify(repositoryService).remoteRepositoryByKey("test-repo");
    }


    @Test
    void testExtractRemoteUrlSuccess() throws PackageException {
        // Given
        PackageRepoService packageRepoService = mock(PackageRepoService.class);
        when(packageRepoService.getRemoteRepoBaseUrl("test-repo")).thenReturn("https://example.com");

        // When
        String result = PackagesCurationCompliantVersionUtil.extractRemoteUrl("test-repo", packageRepoService);

        // Then
        assertEquals("https://example.com/", result);
        verify(packageRepoService).getRemoteRepoBaseUrl("test-repo");
    }

    @Test
    void testExtractRemoteUrlSuccessWithTrailingSlash() throws PackageException {
        // Given
        PackageRepoService packageRepoService = mock(PackageRepoService.class);
        when(packageRepoService.getRemoteRepoBaseUrl("test-repo")).thenReturn("https://example.com/");

        // When
        String result = PackagesCurationCompliantVersionUtil.extractRemoteUrl("test-repo", packageRepoService);

        // Then
        assertEquals("https://example.com/", result);
        verify(packageRepoService).getRemoteRepoBaseUrl("test-repo");
    }

    @Test
    void verifyCurationResponse() {
        assertFalse(PackagesCurationCompliantVersionUtil.verifyCurationResponse(null));

        CurationResponse response = new CurationResponse(List.of());
        assertFalse(PackagesCurationCompliantVersionUtil.verifyCurationResponse(response));

        response = new CurationResponse(List.of(new CurationPackageForResponse("pack", List.of())));
        assertTrue(PackagesCurationCompliantVersionUtil.verifyCurationResponse(response));
    }

    @Test
    void getForbiddenMarkerPath_appendsSuffix() {
        assertEquals(".jfrog/curated/maven-metadata.xml.forbiddenMarker",
                PackagesCurationCompliantVersionUtil.getForbiddenMarkerPath(".jfrog/curated/maven-metadata.xml"));
    }

    @Test
    void getDefaultForbiddenMessage_withAndWithoutPackageName() {
        assertEquals("This Package metadata with path info/rails is blocked by curation",
                PackagesCurationCompliantVersionUtil.getDefaultForbiddenMessage(null, "info/rails"));
        assertEquals("This Package metadata mygem with path info/mygem is blocked by curation",
                PackagesCurationCompliantVersionUtil.getDefaultForbiddenMessage("mygem", "info/mygem"));
    }
}