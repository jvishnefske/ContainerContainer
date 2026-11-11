package org.artifactory.ph.exception;

import com.jfrog.ph.acl.exception.PackageException;
import com.jfrog.ph.acl.exception.PackageExpiredException;
import com.jfrog.ph.acl.exception.PackageForbiddenException;
import com.jfrog.ph.acl.exception.PackageNotFoundException;
import com.jfrog.ph.acl.exception.PackagePatternAssertionException;
import lombok.SneakyThrows;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.security.SecurityService;
import org.artifactory.fs.RepoResource;
import org.artifactory.model.common.RemoteRepoPathImpl;
import org.artifactory.repo.RepoPath;
import org.artifactory.resource.BlockedRepoResource;
import org.artifactory.resource.ExpiredRepoResource;
import org.artifactory.resource.MetadataResource;
import org.artifactory.resource.UnfoundRepoResource;
import org.artifactory.resource.UnfoundRepoResourceReason;
import org.artifactory.spring.InternalArtifactoryContext;
import org.artifactory.spring.InternalContextHelper;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;

import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_CONFLICT;
import static org.apache.http.HttpStatus.SC_FORBIDDEN;
import static org.apache.http.HttpStatus.SC_UNAUTHORIZED;
import static org.artifactory.api.request.ArtifactoryHeaders.CURATION_ORIGIN;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * @author Shahaf Golan
 */
class PackageResourceExceptionUtilTest extends ArtifactoryHomeBoundJUnitTest {
    @Mock
    private SecurityService securityServiceMock;

    @Mock
    private InternalArtifactoryContext context;

    private AutoCloseable autoCloseable;

    private static final String REPO_KEY = "repoKey";

    private static final String PATH = "path";

    public static final String NOT_FOUND = "not found";


    @BeforeEach
    void setup() {
        autoCloseable = MockitoAnnotations.openMocks(this);
        ArtifactoryContextThreadBinder.bind(context);
    }

    @AfterEach
    void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        autoCloseable.close();
    }

    @Test
    void createResourceExceptionWithConflict() {
        String expectedExceptionMessage =
                String.format("Failed to find artifact in repo:%s, path:%s, cause:%s", REPO_KEY, PATH, "detail");
        PackageException expectedException = new PackageException(SC_CONFLICT, "Conflict", expectedExceptionMessage);

        RepoPath remoteRepoPath = setUpRemoteRepo();
        RepoResource unfoundRepoResource = new UnfoundRepoResource(remoteRepoPath, "detail", SC_CONFLICT);

        PackageException actualException = PackageResourceExceptionUtil.createResourceException(unfoundRepoResource);

        assertPackageExceptionDetails(expectedException, actualException);
    }

    @SneakyThrows
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void whenUnFoundRepoResourceWithAnonymousUserShouldThrowExceptionAccordingly(boolean isAnonymous) {
        PackageException expectedException;
        String expectedExceptionMessage;

        when(InternalContextHelper.get().getSecurityService()).thenReturn(securityServiceMock);
        if (isAnonymous) {
            when(securityServiceMock.isAnonymous()).thenReturn(true);
            expectedExceptionMessage =
                    String.format("User doesn't have the required authorization to access artifact" +
                            " in repo:%s, path:%s", REPO_KEY, PATH);
            expectedException =
                    new PackageException(SC_UNAUTHORIZED, "Unauthorized", expectedExceptionMessage);
        } else {
            expectedExceptionMessage =
                    String.format("User doesn't have permissions to access artifact" +
                            " in repo:%s, path:%s", REPO_KEY, PATH);
            expectedException =
                    new PackageForbiddenException("Forbidden", expectedExceptionMessage);
            when(securityServiceMock.isAnonymous()).thenReturn(false);
        }

        RepoPath remoteRepoPath = setUpRemoteRepo();
        RepoResource unfoundRepoResource = new UnfoundRepoResource(remoteRepoPath, "detail", SC_FORBIDDEN);

        PackageException actualException = PackageResourceExceptionUtil.createResourceException(unfoundRepoResource);

        assertPackageExceptionDetails(expectedException, actualException);
    }

    @Test
    void testCreateResourceExceptionUnFoundRepoResource_Default() {
        String expectedExceptionMessage =
                String.format("Failed to find artifact in repo:%s, path:%s, cause: not found", REPO_KEY, PATH);
        PackageNotFoundException expectedException = new PackageNotFoundException(expectedExceptionMessage);

        RepoPath remoteRepoPath = setUpRemoteRepo();
        RepoResource unfoundRepoResource = new UnfoundRepoResource(remoteRepoPath, "detail");

        PackageException actualException = PackageResourceExceptionUtil.createResourceException(unfoundRepoResource);

        assertPackageExceptionDetails(expectedException, actualException);
    }

    @ParameterizedTest
    @EnumSource(BlockedRepoResource.BlockedType.class)
    void createResourceExceptionWithBlockedRepoResourceBlockTypeXray(BlockedRepoResource.BlockedType blockedType) {
        when(context.getSecurityService()).thenReturn(securityServiceMock);
        when(securityServiceMock.isAnonymous()).thenReturn(false);
        String expectedExceptionMessage =
                String.format("Failed to find artifact in repo:%s, path:%s, cause: artifact blocked", REPO_KEY, PATH);
        String blockedMessage = "detail";
        String publicResourceMessage = "Forbidden: artifact is blocked";
        if (blockedType == BlockedRepoResource.BlockedType.CURATION) {
            publicResourceMessage = blockedMessage;
        }
        PackageForbiddenException expectedException;
        if (blockedType == BlockedRepoResource.BlockedType.CURATION) {
            expectedException =
                    new PackageForbiddenException(publicResourceMessage, expectedExceptionMessage,
                            Map.of(CURATION_ORIGIN, "true"));
        } else {
            expectedException =
                    new PackageForbiddenException(publicResourceMessage, expectedExceptionMessage);
        }

        RepoPath remoteRepoPath = setUpRemoteRepo();
        RepoResource blockedRepoResource =
                new BlockedRepoResource(remoteRepoPath, blockedMessage, blockedType);
        PackageException actualException = PackageResourceExceptionUtil.createResourceException(blockedRepoResource);
        assertPackageExceptionDetails(expectedException, actualException);
    }

    @Test
    void createResourceExceptionWithBlockedRepoResourceCurationWithoutMessage() {
        when(context.getSecurityService()).thenReturn(securityServiceMock);
        when(securityServiceMock.isAnonymous()).thenReturn(false);
        String expectedExceptionMessage =
                String.format("Failed to find artifact in repo:%s, path:%s, cause: artifact blocked", REPO_KEY, PATH);
        PackageForbiddenException expectedException =
                new PackageForbiddenException("Forbidden: artifact is blocked", expectedExceptionMessage,
                        Map.of(CURATION_ORIGIN, "true"));

        RepoPath remoteRepoPath = setUpRemoteRepo();
        RepoResource blockedRepoResource =
                new BlockedRepoResource(remoteRepoPath, null, BlockedRepoResource.BlockedType.CURATION);
        PackageException actualException = PackageResourceExceptionUtil.createResourceException(blockedRepoResource);
        assertPackageExceptionDetails(expectedException, actualException);
    }

    @Test
    void createResourceExceptionWithExpiredRepoResource() {
        String expectedExceptionMessage =
                String.format("Failed to download artifact in repo:%s, path:%s, cause: artifact is expired", REPO_KEY,
                        PATH);
        PackageExpiredException expectedException = new PackageExpiredException(expectedExceptionMessage);

        RepoResource repoResource = mock(RepoResource.class);
        ExpiredRepoResource expiredRepoResource = new ExpiredRepoResource(repoResource);

        when(expiredRepoResource.getPath()).thenReturn(PATH);
        when(expiredRepoResource.getRepoKey()).thenReturn(REPO_KEY);

        PackageException actualException = PackageResourceExceptionUtil.createResourceException(expiredRepoResource);

        assertPackageExceptionDetails(expectedException, actualException);
    }

    @Test
    void createResourceExceptionWithArtifactNotFound() {
        String expectedExceptionMessage =
                String.format("Failed to find artifact in repo:%s, path:%s, cause: not found", REPO_KEY, PATH);
        PackageNotFoundException expectedException = new PackageNotFoundException(expectedExceptionMessage);

        RepoPath remoteRepoPath = setUpRemoteRepo();
        RepoResource repoResource = new MetadataResource(remoteRepoPath);

        PackageException actualException = PackageResourceExceptionUtil.createResourceException(repoResource);

        assertPackageExceptionDetails(expectedException, actualException);
    }

    @Test
    void isValidResourceWithValidResourceReturnsTrue() {
        RepoPath remoteRepoPath = setUpRemoteRepo();
        RepoResource validRepoResource = new MetadataResource(remoteRepoPath);
        boolean actual = PackageResourceExceptionUtil.isValidResource(validRepoResource, false);

        assertTrue(actual);
    }

    @Test
    void isValidResourceWithBlockedResourceReturnsFalse() {
        RepoPath remoteRepoPath = setUpRemoteRepo();
        BlockedRepoResource blockedRepoResource =
                new BlockedRepoResource(remoteRepoPath, "detail", BlockedRepoResource.BlockedType.XRAY);
        boolean actual = PackageResourceExceptionUtil.isValidResource(blockedRepoResource, false);

        assertFalse(actual);
    }

    @Test
    void testPackagePatternAssertionException() {
        String expectedExceptionMessage = "message";
        PackagePatternAssertionException
                expectedException =
                new PackagePatternAssertionException(expectedExceptionMessage, expectedExceptionMessage);
        RepoPath remoteRepoPath = setUpRemoteRepo();
        RepoResource repoResource = new UnfoundRepoResource(remoteRepoPath, expectedExceptionMessage, SC_NOT_FOUND,
                                                            UnfoundRepoResourceReason.Reason.INCLUDE_EXCLUDE_PATTERN);
        PackageException actualException = PackageResourceExceptionUtil.createResourceException(repoResource);
        assertPackageExceptionDetails(expectedException, actualException);
    }

    private RepoPath setUpRemoteRepo() {
        RepoPath repoPathMock = mock(RepoPath.class);

        when(repoPathMock.getPath()).thenReturn(PATH);
        when(repoPathMock.getRepoKey()).thenReturn(REPO_KEY);

        return new RemoteRepoPathImpl(REPO_KEY, repoPathMock);
    }

    private static void assertPackageExceptionDetails(PackageException expectedException,
            PackageException actualException) {
        Assertions.assertEquals(expectedException.getMessage(), actualException.getMessage());
        Assertions.assertEquals(expectedException.getStatus(), actualException.getStatus());
        Assertions.assertEquals(expectedException.getInternalReason(), actualException.getInternalReason());
        Assertions.assertEquals(expectedException.getPublicReason(), actualException.getPublicReason());
        Assertions.assertEquals(expectedException.getAdditionalHeaders(), actualException.getAdditionalHeaders());
    }
}