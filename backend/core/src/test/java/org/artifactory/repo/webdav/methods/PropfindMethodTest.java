package org.artifactory.repo.webdav.methods;

import org.apache.http.HttpStatus;
import org.artifactory.api.repo.BaseBrowsableItem;
import org.artifactory.api.repo.BrowsableItem;
import org.artifactory.api.repo.BrowsableItemCriteria;
import org.artifactory.api.repo.RepositoryBrowsingService;
import org.artifactory.api.repo.exception.ItemNotFoundRuntimeException;
import org.artifactory.api.request.ArtifactoryResponse;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.descriptor.repo.VirtualRepoDescriptor;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.repo.virtual.VirtualRepo;
import org.artifactory.request.ArtifactoryRequest;
import org.artifactory.request.InternalArtifactoryResponse;
import org.artifactory.security.InternalSecurityService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the read-permission gate of {@link PropfindMethod}: an unreadable path must not be answered with a
 * multi-status listing (RTDEV-95374), while readable and partially-readable paths keep listing as before.
 */
class PropfindMethodTest extends ArtifactoryHomeBoundJUnitTest {

    private static final String REALM = "Artifactory Realm";
    private static final RepoPath REPO_ROOT = RepoPathFactory.create("some-repo", "");
    private static final RepoPath FOLDER = RepoPathFactory.create("some-repo", "folder");
    private static final String PROPFIND_BODY = """
            <?xml version="1.0" encoding="utf-8"?>
            <D:propfind xmlns:D="DAV:">
              <D:prop><D:displayname/></D:prop>
            </D:propfind>
            """;

    @Mock
    private InternalRepositoryService repoService;
    @Mock
    private RepositoryBrowsingService repoBrowsing;
    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private InternalSecurityService securityService;
    @Mock
    private BasicAuthenticationEntryPoint authenticationEntryPoint;
    @InjectMocks
    private PropfindMethod propfindMethod;

    private AutoCloseable mocks;

    @BeforeEach
    void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        when(authenticationEntryPoint.getRealmName()).thenReturn(REALM);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void canHandle() {
        assertTrue(propfindMethod.canHandle("propfind"));
        assertTrue(propfindMethod.canHandle("PROPFIND"));
        assertFalse(propfindMethod.canHandle("get"));
    }

    @Test
    void permittedRequestStillReturnsMultiStatusListingWithChildren() throws IOException {
        BrowsableItem root = folderItem(REPO_ROOT);
        BaseBrowsableItem child = fileItem(RepoPathFactory.create("some-repo", "file.txt"));
        when(repoService.exists(REPO_ROOT)).thenReturn(true);
        when(repoBrowsing.getLocalRepoBrowsableItem(REPO_ROOT)).thenReturn(root);
        when(repoBrowsing.getLocalRepoBrowsableChildren(any(BrowsableItemCriteria.class))).thenReturn(List.of(child));
        when(authorizationService.canRead(REPO_ROOT)).thenReturn(true);

        CapturingResponse response = handleCapturing(REPO_ROOT, "1");

        assertEquals(HttpStatus.SC_MULTI_STATUS, response.getStatus());
        String body = response.body();
        assertTrue(body.contains("<D:multistatus"), body);
        assertTrue(body.contains("file.txt"), body);
        verify(authenticationEntryPoint, never()).getRealmName();
    }

    @Test
    void permittedRequestWithDepthZeroDoesNotListChildren() throws IOException {
        BrowsableItem root = folderItem(REPO_ROOT);
        when(repoService.exists(REPO_ROOT)).thenReturn(true);
        when(repoBrowsing.getLocalRepoBrowsableItem(REPO_ROOT)).thenReturn(root);
        when(authorizationService.canRead(REPO_ROOT)).thenReturn(true);

        CapturingResponse response = handleCapturing(REPO_ROOT, "0");

        assertEquals(HttpStatus.SC_MULTI_STATUS, response.getStatus());
        assertTrue(response.body().contains("<D:multistatus"), response.body());
        verify(repoBrowsing, never()).getLocalRepoBrowsableChildren(any(BrowsableItemCriteria.class));
    }

    @Test
    void anonymousWithoutReadPermissionGetsChallengeInsteadOfMultiStatus() throws IOException {
        givenFolder(REPO_ROOT, List.of());
        when(authorizationService.canRead(REPO_ROOT)).thenReturn(false);
        when(securityService.isAnonymous()).thenReturn(true);

        ArtifactoryResponse response = handleWithMockedResponse(REPO_ROOT);

        verify(response).sendAuthorizationRequired(anyString(), eq(REALM));
        verify(response, never()).sendError(anyInt(), anyString(), any(Logger.class));
        verify(response, never()).setStatus(HttpStatus.SC_MULTI_STATUS);
    }

    @Test
    void authenticatedUserWithoutReadPermissionIsForbidden() throws IOException {
        givenFolder(REPO_ROOT, List.of());
        when(authorizationService.canRead(REPO_ROOT)).thenReturn(false);
        when(securityService.isAnonymous()).thenReturn(false);
        when(securityService.currentUsername()).thenReturn("some-user");

        ArtifactoryResponse response = handleWithMockedResponse(REPO_ROOT);

        verify(response).sendError(eq(HttpStatus.SC_FORBIDDEN), anyString(), any(Logger.class));
        verify(response, never()).sendAuthorizationRequired(anyString(), anyString());
        verify(response, never()).setStatus(HttpStatus.SC_MULTI_STATUS);
    }

    @Test
    void deniedRequestNeverParsesTheRequestBody() throws IOException {
        givenFolder(REPO_ROOT, List.of());
        when(authorizationService.canRead(REPO_ROOT)).thenReturn(false);
        when(securityService.isAnonymous()).thenReturn(true);

        // A body the parser would happily consume, so the request is only left unread because the permission gate
        // runs before the parser - not because there is nothing to read.
        ArtifactoryRequest request = mock(ArtifactoryRequest.class);
        when(request.getRepoPath()).thenReturn(REPO_ROOT);
        when(request.getContentLength()).thenReturn((long) PROPFIND_BODY.getBytes(StandardCharsets.UTF_8).length);
        doReturn(new ByteArrayInputStream(PROPFIND_BODY.getBytes(StandardCharsets.UTF_8)))
                .when(request).getInputStream();

        propfindMethod.handle(request, mock(ArtifactoryResponse.class));

        verify(request, never()).getContentLength();
        verify(request, never()).getInputStream();
    }

    @Test
    void unreadablePathIsHiddenAsNotFoundWhenConfiguredToHideUnauthorizedResources() throws IOException {
        when(securityService.shouldHideUnauthorizedResources()).thenReturn(true);
        givenFolder(REPO_ROOT, List.of());
        when(authorizationService.canRead(REPO_ROOT)).thenReturn(false);
        when(securityService.isAnonymous()).thenReturn(true);

        ArtifactoryResponse response = handleWithMockedResponse(REPO_ROOT);

        verify(response).sendError(eq(HttpStatus.SC_NOT_FOUND), anyString(), any(Logger.class));
        verify(response, never()).sendAuthorizationRequired(anyString(), anyString());
    }

    @Test
    void unreadablePathIsHiddenAsNotFoundWhenVirtualRepoHidesUnauthorizedResources() throws IOException {
        VirtualRepoDescriptor virtualRepo = new VirtualRepoDescriptor();
        virtualRepo.setHideUnauthorizedResources(true);
        when(repoService.virtualRepoDescriptorByKey(REPO_ROOT.getRepoKey())).thenReturn(virtualRepo);
        givenFolder(REPO_ROOT, List.of());
        when(authorizationService.canRead(REPO_ROOT)).thenReturn(false);
        when(securityService.isAnonymous()).thenReturn(true);

        ArtifactoryResponse response = handleWithMockedResponse(REPO_ROOT);

        verify(response).sendError(eq(HttpStatus.SC_NOT_FOUND), anyString(), any(Logger.class));
        verify(response, never()).sendAuthorizationRequired(anyString(), anyString());
    }

    @Test
    void unreadablePathStillChallengesWhenVirtualRepoDoesNotHideUnauthorizedResources() throws IOException {
        VirtualRepoDescriptor virtualRepo = new VirtualRepoDescriptor();
        virtualRepo.setHideUnauthorizedResources(false);
        when(repoService.virtualRepoDescriptorByKey(REPO_ROOT.getRepoKey())).thenReturn(virtualRepo);
        givenFolder(REPO_ROOT, List.of());
        when(authorizationService.canRead(REPO_ROOT)).thenReturn(false);
        when(securityService.isAnonymous()).thenReturn(true);

        ArtifactoryResponse response = handleWithMockedResponse(REPO_ROOT);

        verify(response).sendAuthorizationRequired(anyString(), eq(REALM));
        verify(response, never()).sendError(anyInt(), anyString(), any(Logger.class));
    }

    @Test
    void unreadableFolderIsDeniedWhenListingChildrenFails() {
        BrowsableItem folder = folderItem();
        when(authorizationService.canRead(FOLDER)).thenReturn(false);
        when(repoBrowsing.getLocalRepoBrowsableChildrenStream(any(BrowsableItemCriteria.class), anyBoolean(), any()))
                .thenThrow(new ItemNotFoundRuntimeException("gone"));

        assertFalse(propfindMethod.hasReadAccess(FOLDER, folder));
    }

    @Test
    void unreadableFolderIsDeniedWhenListingChildrenFailsUnexpectedly() {
        BrowsableItem folder = folderItem();
        when(authorizationService.canRead(FOLDER)).thenReturn(false);
        when(repoBrowsing.getLocalRepoBrowsableChildrenStream(any(BrowsableItemCriteria.class), anyBoolean(), any()))
                .thenThrow(new IllegalStateException("storage fault"));

        assertFalse(propfindMethod.hasReadAccess(FOLDER, folder));
    }

    @Test
    void virtualRepoPathIsAllowedWhenAnAggregatedRepoGrantsRead() {
        RepoPath virtualPath = RepoPathFactory.create("virt-repo", "some/path");
        LocalRepo<?, ?> member = mock(LocalRepo.class);
        when(member.getKey()).thenReturn("member-repo");
        VirtualRepo virtualRepo = mock(VirtualRepo.class);
        when(virtualRepo.getResolvedLocalAndFederatedRepos()).thenReturn(Set.of(member));
        when(virtualRepo.getResolvedRemoteRepos()).thenReturn(Set.of());
        when(repoService.virtualRepositoryByKey("virt-repo")).thenReturn(virtualRepo);
        // no permission target names the virtual key, so canRead on it is false
        when(authorizationService.canRead(virtualPath)).thenReturn(false);
        when(authorizationService.canRead(RepoPathFactory.create("member-repo", "some/path"))).thenReturn(true);

        assertTrue(propfindMethod.hasReadAccess(virtualPath, null));
    }

    @Test
    void virtualRepoPathIsDeniedWhenNoAggregatedRepoGrantsRead() {
        RepoPath virtualPath = RepoPathFactory.create("virt-repo", "some/path");
        LocalRepo<?, ?> member = mock(LocalRepo.class);
        when(member.getKey()).thenReturn("member-repo");
        VirtualRepo virtualRepo = mock(VirtualRepo.class);
        when(virtualRepo.getResolvedLocalAndFederatedRepos()).thenReturn(Set.of(member));
        when(virtualRepo.getResolvedRemoteRepos()).thenReturn(Set.of());
        when(repoService.virtualRepositoryByKey("virt-repo")).thenReturn(virtualRepo);
        when(authorizationService.canRead(any(RepoPath.class))).thenReturn(false);

        assertFalse(propfindMethod.hasReadAccess(virtualPath, null));
    }

    @Test
    void nonVirtualRepoPathDoesNotAttemptAggregationLookup() {
        when(authorizationService.canRead(FOLDER)).thenReturn(true);

        assertTrue(propfindMethod.hasReadAccess(FOLDER, null));

        verify(repoService, never()).virtualRepositoryByKey(anyString());
    }

    @Test
    void unreadableFolderWithNoReadableChildrenIsDenied() {
        BrowsableItem folder = folderItem();
        when(authorizationService.canRead(FOLDER)).thenReturn(false);
        givenBrowsableChildrenStream(List.of());

        assertFalse(propfindMethod.hasReadAccess(FOLDER, folder));
    }

    @Test
    void unreadableFolderWithReadableChildrenIsAllowed() {
        BrowsableItem folder = folderItem();
        when(authorizationService.canRead(FOLDER)).thenReturn(false);
        givenBrowsableChildrenStream(List.of(mock(BaseBrowsableItem.class)));

        assertTrue(propfindMethod.hasReadAccess(FOLDER, folder));
    }

    /**
     * The children stream is backed by an open result set, and the gate short-circuits on the first item, so nothing
     * closes it implicitly - the gate has to.
     */
    @Test
    void childrenStreamIsClosedAfterTheExistenceCheckShortCircuits() {
        BrowsableItem folder = folderItem();
        AtomicBoolean closed = new AtomicBoolean();
        when(authorizationService.canRead(FOLDER)).thenReturn(false);
        when(repoBrowsing.getLocalRepoBrowsableChildrenStream(any(BrowsableItemCriteria.class), anyBoolean(), any()))
                .thenAnswer(invocation -> Stream.of(mock(BaseBrowsableItem.class)).onClose(() -> closed.set(true)));

        assertTrue(propfindMethod.hasReadAccess(FOLDER, folder));

        assertTrue(closed.get(), "children stream was left open");
    }

    @Test
    void readableEmptyFolderIsAllowed() {
        BrowsableItem folder = folderItem();
        when(authorizationService.canRead(FOLDER)).thenReturn(true);

        assertTrue(propfindMethod.hasReadAccess(FOLDER, folder));

        verifyChildrenWereNeverListed();
    }

    @Test
    void unreadableFileIsDenied() {
        BrowsableItem file = mock(BrowsableItem.class);
        when(file.isFolder()).thenReturn(false);
        when(authorizationService.canRead(FOLDER)).thenReturn(false);

        assertFalse(propfindMethod.hasReadAccess(FOLDER, file));

        verifyChildrenWereNeverListed();
    }

    @Test
    void unreadableMissingItemIsDenied() {
        when(authorizationService.canRead(FOLDER)).thenReturn(false);

        assertFalse(propfindMethod.hasReadAccess(FOLDER, null));
    }

    @Test
    void readableMissingItemIsAllowed() {
        when(authorizationService.canRead(FOLDER)).thenReturn(true);

        assertTrue(propfindMethod.hasReadAccess(FOLDER, null));
    }

    @Test
    void permittedRequestWithPropfindBodyStillReturnsMultiStatus() throws IOException {
        BrowsableItem root = folderItem(REPO_ROOT);
        when(repoService.exists(REPO_ROOT)).thenReturn(true);
        when(repoBrowsing.getLocalRepoBrowsableItem(REPO_ROOT)).thenReturn(root);
        when(authorizationService.canRead(REPO_ROOT)).thenReturn(true);

        // Exercise resolvePropfindType (contentLength > 0) so the extracted parse path is covered.
        ArtifactoryRequest request = mock(ArtifactoryRequest.class);
        when(request.getRepoPath()).thenReturn(REPO_ROOT);
        when(request.getHeader("Depth")).thenReturn("0");
        when(request.getPath()).thenReturn(REPO_ROOT.getPath());
        when(request.getUri()).thenReturn("/" + REPO_ROOT.getRepoKey());
        when(request.getContentLength()).thenReturn((long) PROPFIND_BODY.getBytes(StandardCharsets.UTF_8).length);
        when(request.getInputStream())
                .thenReturn(new ByteArrayInputStream(PROPFIND_BODY.getBytes(StandardCharsets.UTF_8)));

        CapturingResponse response = new CapturingResponse();
        propfindMethod.handle(request, response);

        assertEquals(HttpStatus.SC_MULTI_STATUS, response.getStatus());
        assertTrue(response.body().contains("<D:multistatus"), response.body());
    }

    @Test
    void permittedRequestWithInvalidPropfindBodyFailsFast() throws IOException {
        BrowsableItem root = folderItem(REPO_ROOT);
        when(repoService.exists(REPO_ROOT)).thenReturn(true);
        when(repoBrowsing.getLocalRepoBrowsableItem(REPO_ROOT)).thenReturn(root);
        when(authorizationService.canRead(REPO_ROOT)).thenReturn(true);

        ArtifactoryRequest request = mock(ArtifactoryRequest.class);
        when(request.getRepoPath()).thenReturn(REPO_ROOT);
        when(request.getContentLength()).thenReturn(12L);
        when(request.getInputStream())
                .thenReturn(new ByteArrayInputStream("not-xml-body".getBytes(StandardCharsets.UTF_8)));

        assertThrows(IllegalStateException.class,
                () -> propfindMethod.handle(request, mock(ArtifactoryResponse.class)));
    }

    private void givenFolder(RepoPath repoPath, List<BaseBrowsableItem> children) {
        BrowsableItem folder = folderItem();
        when(repoService.exists(repoPath)).thenReturn(true);
        when(repoBrowsing.getLocalRepoBrowsableItem(repoPath)).thenReturn(folder);
        givenBrowsableChildrenStream(children);
    }

    /**
     * Answers with a fresh stream per call, since a stream can only be consumed once.
     */
    private void givenBrowsableChildrenStream(List<BaseBrowsableItem> children) {
        when(repoBrowsing.getLocalRepoBrowsableChildrenStream(any(BrowsableItemCriteria.class), anyBoolean(), any()))
                .thenAnswer(invocation -> children.stream());
    }

    private void verifyChildrenWereNeverListed() {
        verify(repoBrowsing, never()).getLocalRepoBrowsableChildren(any(BrowsableItemCriteria.class));
        verify(repoBrowsing, never())
                .getLocalRepoBrowsableChildrenStream(any(BrowsableItemCriteria.class), anyBoolean(), any());
    }

    private BrowsableItem folderItem() {
        BrowsableItem item = mock(BrowsableItem.class);
        when(item.isFolder()).thenReturn(true);
        return item;
    }

    private BrowsableItem folderItem(RepoPath repoPath) {
        BrowsableItem item = folderItem();
        when(item.getRepoPath()).thenReturn(repoPath);
        return item;
    }

    private BaseBrowsableItem fileItem(RepoPath repoPath) {
        BaseBrowsableItem item = mock(BaseBrowsableItem.class);
        when(item.isFolder()).thenReturn(false);
        when(item.getRepoPath()).thenReturn(repoPath);
        return item;
    }

    private CapturingResponse handleCapturing(RepoPath repoPath, String depth) throws IOException {
        ArtifactoryRequest request = mock(ArtifactoryRequest.class);
        when(request.getRepoPath()).thenReturn(repoPath);
        when(request.getHeader("Depth")).thenReturn(depth);
        when(request.getPath()).thenReturn(repoPath.getPath());
        when(request.getUri()).thenReturn("/" + repoPath.getRepoKey());
        CapturingResponse response = new CapturingResponse();
        propfindMethod.handle(request, response);
        return response;
    }

    /**
     * {@link InternalArtifactoryResponse} discards the body, so capture it to assert the generated multi-status XML.
     */
    private static class CapturingResponse extends InternalArtifactoryResponse {

        private final StringWriter writer = new StringWriter();

        @Override
        public Writer getWriter() {
            return writer;
        }

        String body() {
            return writer.toString();
        }
    }

    /**
     * Asserts against the real {@link ArtifactoryResponse} contract, so a change that stopped issuing the challenge
     * (or started issuing it for an identified user) fails here rather than passing on a coincidentally equal status.
     */
    private ArtifactoryResponse handleWithMockedResponse(RepoPath repoPath) throws IOException {
        ArtifactoryRequest request = mock(ArtifactoryRequest.class);
        when(request.getRepoPath()).thenReturn(repoPath);
        ArtifactoryResponse response = mock(ArtifactoryResponse.class);
        propfindMethod.handle(request, response);
        return response;
    }

}
