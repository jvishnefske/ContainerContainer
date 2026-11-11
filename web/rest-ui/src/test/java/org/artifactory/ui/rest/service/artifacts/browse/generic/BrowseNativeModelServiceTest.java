package org.artifactory.ui.rest.service.artifacts.browse.generic;

import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.http.HttpStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.CoreAddons;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.BaseBrowsableItem;
import org.artifactory.api.repo.BrowsableItem;
import org.artifactory.api.repo.BrowsableItemCriteria;
import org.artifactory.api.repo.RepositoryBrowsingService;
import org.artifactory.api.repo.RootNodesFilterResult;
import org.artifactory.api.repo.exception.FolderExpectedException;
import org.artifactory.api.repo.exception.ItemNotFoundRuntimeException;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.descriptor.repo.HttpRepoDescriptor;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.descriptor.repo.VirtualRepoDescriptor;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.ui.rest.model.artifacts.browse.generic.NativeBrowserChild;
import org.artifactory.ui.rest.model.artifacts.browse.generic.NativeBrowserModel;
import org.artifactory.ui.rest.resource.artifacts.browse.generic.NativeBrowserResource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.stubbing.Answer;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.artifactory.common.ConstantValues.uiHideChecksums;
import static org.artifactory.util.PackageTypeConfigFields.FORCE_MAVEN_AUTHENTICATION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BrowseNativeModelServiceTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private InternalRepositoryService repositoryService;
    @Mock
    private RepositoryBrowsingService repoBrowsingService;
    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private SecurityService securityService;
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private CoreAddons coreAddons;
    @Mock
    private ArtifactoryContext artifactoryContext;
    @Mock
    private RepoDescriptor repoDescriptor;
    @Spy
    @InjectMocks
    private BrowseNativeModelService browseNativeService;
    private AutoCloseable mocks;

    @BeforeEach
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        when(artifactoryContext.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        when(addonsManager.addonByType(CoreAddons.class)).thenReturn(coreAddons);
        when(coreAddons.getListBrowsingVersion()).thenReturn("1.0.0");
        ArtifactoryHome.get().getArtifactoryProperties().setProperty(uiHideChecksums.getPropertyName(), "FALSE");
    }

    @AfterEach
    public void tearDown() throws Exception {
        ArtifactoryHome.unbind();
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @Test
    public void testWithEmptyRepoPath() {
        TestRestRequest restRequest = mock(TestRestRequest.class);
        ArtifactoryRestResponse<NativeBrowserModel> response = new ArtifactoryRestResponse<>();
        browseNativeService.execute(restRequest, response);

        assertEquals(HttpStatus.SC_NOT_FOUND, response.getResponseCode());
    }

    @Test
    public void testWithEmptyRepoPathAndAnonUser() {
        TestRestRequest restRequest = mock(TestRestRequest.class);
        when(securityService.isAnonAccessEnabled()).thenReturn(true);
        when(securityService.isAnonymous()).thenReturn(true);
        ArtifactoryRestResponse<NativeBrowserModel> response = new ArtifactoryRestResponse<>();
        browseNativeService.execute(restRequest, response);

        assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getResponseCode());
    }

    @ParameterizedTest
    @MethodSource("uiRequestAndStatus")
    public void testWithUnauthorizedUserAnonAccessEnabled(String requestURI, int expectedStatus) {
        when(authorizationService.canRead(any(RepoPath.class))).thenReturn(false);
        when(securityService.isAnonAccessEnabled()).thenReturn(true);
        when(securityService.isAnonymous()).thenReturn(true);
        when(repositoryService.getAllRepoKeys()).thenReturn(Set.of("generic"));
        when(repoDescriptor.getType()).thenReturn(RepoType.Generic);
        when(repositoryService.repoDescriptorByKey(any())).thenReturn(repoDescriptor);
        TestRestRequest restRequest = mock(TestRestRequest.class);
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getContextPath()).thenReturn("");
        when(servletRequest.getRequestURI()).thenReturn(requestURI);
        when(restRequest.getServletRequest()).thenReturn(servletRequest);
        when(restRequest.getPathParamByKey(NativeBrowserResource.REPO_PATH)).thenReturn("generic/somePath");
        List<BaseBrowsableItem> expectedList = new LinkedList<>();
        when(repositoryService.localFederatedCachedRepoDescriptorByKey("generic"))
                .thenReturn(new LocalRepoDescriptor());
        Answer<List<BaseBrowsableItem>> answer = invocationOnMock -> {
            RootNodesFilterResult rootNodesFilterResult = invocationOnMock.getArgument(2, RootNodesFilterResult.class);
            rootNodesFilterResult.setAllItemNodesCanRead(true);
            return expectedList;
        };
        doAnswer(answer).when(repoBrowsingService)
                .getLocalRepoBrowsableChildren(any(BrowsableItemCriteria.class), anyBoolean(),
                        any(RootNodesFilterResult.class));

        HttpServletResponse httpServletResponse = new MockHttpServletResponse();
        ArtifactoryRestResponse<NativeBrowserModel> response = new ArtifactoryRestResponse<>(httpServletResponse);
        browseNativeService.execute(restRequest, response);

        assertEquals(expectedStatus, response.getResponseCode());
    }

    @ParameterizedTest
    @MethodSource("uiRequestAndStatus")
    public void testWithUnauthorizedAnonUserWhenAnonAccessEnabled(String requestURI, int expectedStatus) {
        when(authorizationService.canRead(any(RepoPath.class))).thenReturn(false);
        when(securityService.isAnonAccessEnabled()).thenReturn(true);
        when(securityService.isAnonymous()).thenReturn(true);
        when(repositoryService.getAllRepoKeys()).thenReturn(Set.of("generic"));
        when(repoDescriptor.getType()).thenReturn(RepoType.Generic);
        when(repositoryService.repoDescriptorByKey(any())).thenReturn(repoDescriptor);
        TestRestRequest restRequest = mock(TestRestRequest.class);
        when(restRequest.getPathParamByKey(NativeBrowserResource.REPO_PATH)).thenReturn("generic/somePath");
        when(repositoryService.localFederatedCachedRepoDescriptorByKey("generic"))
                .thenReturn(new LocalRepoDescriptor());
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getContextPath()).thenReturn("");
        when(servletRequest.getRequestURI()).thenReturn(requestURI);
        when(restRequest.getServletRequest()).thenReturn(servletRequest);

        List<BaseBrowsableItem> expectedList = new LinkedList<>();
        Answer<List<BaseBrowsableItem>> answer = invocationOnMock -> {
            RootNodesFilterResult rootNodesFilterResult = invocationOnMock.getArgument(2, RootNodesFilterResult.class);
            rootNodesFilterResult.setAllItemNodesCanRead(false);
            return expectedList;
        };
        doAnswer(answer).when(repoBrowsingService)
                .getLocalRepoBrowsableChildren(any(BrowsableItemCriteria.class), anyBoolean(),
                        any(RootNodesFilterResult.class));

        HttpServletResponse httpServletResponse = new MockHttpServletResponse();
        ArtifactoryRestResponse<NativeBrowserModel> response = new ArtifactoryRestResponse<>(httpServletResponse);
        browseNativeService.execute(restRequest, response);

        assertEquals(expectedStatus, response.getResponseCode());
    }

    @Test
    public void testWithUnauthorizedUserAnonAccessEnabledЬMavenVirtualForceAuth() {
        when(authorizationService.canRead(any(RepoPath.class))).thenReturn(false);
        when(securityService.isAnonAccessEnabled()).thenReturn(true);
        when(securityService.isAnonymous()).thenReturn(true);
        when(repositoryService.getAllRepoKeys()).thenReturn(Set.of("maven"));
        VirtualRepoDescriptor virtualRepoDescriptor = new VirtualRepoDescriptor();
        virtualRepoDescriptor.setForceMavenAuthentication(true);
        virtualRepoDescriptor.setType(RepoType.Maven);
        when(repositoryService.repoDescriptorByKey(any())).thenReturn(virtualRepoDescriptor);
        TestRestRequest restRequest = mock(TestRestRequest.class);
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getContextPath()).thenReturn("");
        when(servletRequest.getRequestURI()).thenReturn("/ui/");
        when(restRequest.getServletRequest()).thenReturn(servletRequest);
        when(restRequest.getPathParamByKey(NativeBrowserResource.REPO_PATH)).thenReturn("maven/somePath");
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("maven", RepoType.Maven);
        virtualRepoConfig.getPackageTypeConfig().put(FORCE_MAVEN_AUTHENTICATION.getKey(), "true");
        when(repositoryService.repoConfigByKey("maven")).thenReturn(virtualRepoConfig);
        when(repositoryService.virtualRepoConfigByKey("maven")).thenReturn(virtualRepoConfig);
        when(repositoryService.virtualRepoDescriptorByKey("maven")).thenReturn(virtualRepoDescriptor);
        doThrow(ItemNotFoundRuntimeException.class).when(repoBrowsingService)
                .getVirtualRepoBrowsableChildren(any(BrowsableItemCriteria.class), anyBoolean(),
                        any(RootNodesFilterResult.class));

        HttpServletResponse httpServletResponse = new MockHttpServletResponse();
        ArtifactoryRestResponse<NativeBrowserModel> response = new ArtifactoryRestResponse<>(httpServletResponse);
        browseNativeService.execute(restRequest, response);

        assertEquals(401, response.getResponseCode());
        assertEquals("Browse request for repo:path maven:somePath is forbidden " +
                "for user 'anonymous'.", response.getFeedbackMsg().getError());
    }

    @Test
    public void testWithUnExistRepoKeyAndAuthorizedUser() {
        when(authorizationService.canRead(any(RepoPath.class))).thenReturn(true);
        when(repositoryService.getAllRepoKeys()).thenReturn(Set.of(""));
        TestRestRequest restRequest = mock(TestRestRequest.class);
        when(restRequest.getPathParamByKey(NativeBrowserResource.REPO_PATH)).thenReturn("generic/somePath");
        ArtifactoryRestResponse<NativeBrowserModel> response = new ArtifactoryRestResponse<>();
        browseNativeService.execute(restRequest, response);

        assertEquals(HttpStatus.SC_NOT_FOUND, response.getResponseCode());
    }

    @Test
    public void testWithUnExistRepoKeyAndAnonUser() {
        when(authorizationService.canRead(any(RepoPath.class))).thenReturn(true);
        when(securityService.isAnonAccessEnabled()).thenReturn(true);
        when(securityService.isAnonymous()).thenReturn(true);
        when(repositoryService.getAllRepoKeys()).thenReturn(Set.of(""));
        TestRestRequest restRequest = mock(TestRestRequest.class);
        when(restRequest.getPathParamByKey(NativeBrowserResource.REPO_PATH)).thenReturn("generic/somePath");
        ArtifactoryRestResponse<NativeBrowserModel> response = new ArtifactoryRestResponse<>();
        browseNativeService.execute(restRequest, response);

        assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getResponseCode());
    }

    @ParameterizedTest
    @CsvSource({
            "generic/folder/file, folder/file",
            "generic/folder/file.zip, folder/file.zip"
    })
    public void testWithFileDownload(String path, String expected) {
        RepoPath repoPath = RepoPathFactory.create(path);
        when(repositoryService.getAllRepoKeys()).thenReturn(Set.of(repoPath.getRepoKey()));
        when(authorizationService.canRead(any(RepoPath.class))).thenReturn(true);
        TestRestRequest restRequest = mock(TestRestRequest.class);
        when(restRequest.getServletRequest()).thenReturn(mock(HttpServletRequest.class));
        ArtifactoryRestResponse<NativeBrowserModel> response = new ArtifactoryRestResponse<>();
        when(restRequest.getPathParamByKey(NativeBrowserResource.REPO_PATH)).thenReturn(repoPath.toPath());
        when(repositoryService.localFederatedCachedRepoDescriptorByKey(repoPath.getRepoKey()))
                .thenReturn(new LocalRepoDescriptor());
        when(repoBrowsingService.getLocalRepoBrowsableChildren(any(BrowsableItemCriteria.class), anyBoolean(),
                any(RootNodesFilterResult.class))).thenThrow(new FolderExpectedException(repoPath));

        browseNativeService.execute(restRequest, response);
        NativeBrowserModel model = response.getIModel();
        assertEquals("generic", model.getRepo());
        assertEquals(expected, model.getPath());
        assertFalse(model.isRoot());
        assertFalse(model.isFolder());
        assertNull(model.getChildren());
        assertNull(model.getFooter());
    }

    @Test
    public void testWithRealRepoPath() {
        when(repositoryService.getAllRepoKeys()).thenReturn(Set.of("generic"));
        when(authorizationService.canRead(any(RepoPath.class))).thenReturn(true);
        TestRestRequest restRequest = mock(TestRestRequest.class);
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getContextPath()).thenReturn("");
        when(servletRequest.getRequestURI()).thenReturn("");
        when(restRequest.getServletRequest()).thenReturn(servletRequest);
        ArtifactoryRestResponse<NativeBrowserModel> response = new ArtifactoryRestResponse<>();
        List<BaseBrowsableItem> expectedList = new LinkedList<>();
        expectedList
                .add(new BrowsableItem("folder", true, 123L, 1234L, 200L, RepoPathFactory.create("generic", "folder")));
        expectedList
                .add(new BrowsableItem("file", false, 124L, 1235L, 30L, RepoPathFactory.create("generic", "file")));
        when(restRequest.getPathParamByKey(NativeBrowserResource.REPO_PATH)).thenReturn("generic/somePath");
        when(repositoryService.localFederatedCachedRepoDescriptorByKey("generic")).thenReturn(
                new LocalRepoDescriptor());
        when(repoDescriptor.getType()).thenReturn(RepoType.Generic);
        when(repositoryService.repoDescriptorByKey(any())).thenReturn(repoDescriptor);
        when(repoBrowsingService.getLocalRepoBrowsableChildren(any(BrowsableItemCriteria.class), anyBoolean(),
                any(RootNodesFilterResult.class))).thenReturn(expectedList);
        browseNativeService.execute(restRequest, response);

        assertEquals(HttpStatus.SC_OK, response.getResponseCode());
        NativeBrowserModel model = response.getIModel();
        assertEquals("generic", model.getRepo());
        assertEquals("somePath", model.getPath());
        assertEquals("1.0.0 Server", model.getFooter());
        assertFalse(model.isRoot());
        assertTrue(model.isFolder());
        List<NativeBrowserChild> items = model.getChildren();
        assertEquals("folder", items.get(0).getName());
        assertEquals(200L, items.get(0).getSize());
        assertEquals("file", items.get(1).getName());
        assertEquals(30L, items.get(1).getSize());
    }

    @ParameterizedTest
    @MethodSource("canManageAmountOfReturnedItems")
    public void testWithRealRepoPathAndInternalMetadataExclude(boolean canManage, int amountOfReturnedItems) {
        when(repositoryService.getAllRepoKeys()).thenReturn(Set.of("generic"));
        when(authorizationService.canRead(any(RepoPath.class))).thenReturn(true);
        when(authorizationService.canManage(any(RepoPath.class))).thenReturn(canManage);
        TestRestRequest restRequest = mock(TestRestRequest.class);
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getContextPath()).thenReturn("");
        when(servletRequest.getRequestURI()).thenReturn("");
        when(restRequest.getServletRequest()).thenReturn(servletRequest);
        ArtifactoryRestResponse<NativeBrowserModel> response = new ArtifactoryRestResponse<>();
        List<BaseBrowsableItem> expectedList = new LinkedList<>();
        expectedList
                .add(new BrowsableItem("folder", true, 123L, 1234L, 200L, RepoPathFactory.create("generic", "folder")));
        expectedList
                .add(new BrowsableItem("file", false, 124L, 1235L, 30L, RepoPathFactory.create("generic", "file")));
        expectedList
                .add(new BrowsableItem("repository.catalog", false, 124L, 1235L, 30L,
                        RepoPathFactory.create("generic", ".jfrog/repository.catalog")));
        when(restRequest.getPathParamByKey(NativeBrowserResource.REPO_PATH)).thenReturn("generic/somePath");
        when(repositoryService.localFederatedCachedRepoDescriptorByKey("generic"))
                .thenReturn(new LocalRepoDescriptor());
        when(repoDescriptor.getType()).thenReturn(RepoType.Docker);
        when(repositoryService.repoDescriptorByKey(any())).thenReturn(repoDescriptor);
        when(repoBrowsingService.getLocalRepoBrowsableChildren(any(BrowsableItemCriteria.class), anyBoolean(),
                any(RootNodesFilterResult.class))).thenReturn(expectedList);
        browseNativeService.execute(restRequest, response);

        assertEquals(HttpStatus.SC_OK, response.getResponseCode());
        NativeBrowserModel model = response.getIModel();
        assertEquals("generic", model.getRepo());
        assertEquals("somePath", model.getPath());
        assertEquals("1.0.0 Server", model.getFooter());
        assertFalse(model.isRoot());
        assertTrue(model.isFolder());
        List<NativeBrowserChild> items = model.getChildren();
        assertEquals(items.size(), amountOfReturnedItems);
        assertEquals("folder", items.get(0).getName());
        assertEquals(200L, items.get(0).getSize());
        assertEquals("file", items.get(1).getName());
        assertEquals(30L, items.get(1).getSize());
    }

    private static Stream<Arguments> canManageAmountOfReturnedItems() {
        return Stream.of(
                // In this case we expect to get the items inside .jfrog folder
                arguments(true, 3),
                // In this case we expect to filter out the items inside .jfrog folder
                arguments(false, 2));
    }

    @Test
    public void testWithRealRepoArchivePath() {
        when(repositoryService.getAllRepoKeys()).thenReturn(Set.of("generic"));
        when(authorizationService.canRead(any(RepoPath.class))).thenReturn(true);
        TestRestRequest restRequest = mock(TestRestRequest.class);
        when(restRequest.getServletRequest()).thenReturn(mock(HttpServletRequest.class));
        ArtifactoryRestResponse<NativeBrowserModel> response = new ArtifactoryRestResponse<>();
        when(restRequest.getPathParamByKey(NativeBrowserResource.REPO_PATH))
                .thenReturn("generic/someArchive.zip!/somePath/someFile");
        when(repositoryService.localFederatedCachedRepoDescriptorByKey("generic"))
                .thenReturn(new LocalRepoDescriptor());
        when(repoBrowsingService
                .getLocalRepoBrowsableChildren(any(BrowsableItemCriteria.class), anyBoolean(),
                        any(RootNodesFilterResult.class))).thenThrow(ItemNotFoundRuntimeException.class);
        browseNativeService.execute(restRequest, response);

        assertEquals(HttpStatus.SC_OK, response.getResponseCode());
        NativeBrowserModel model = response.getIModel();
        assertEquals("generic", model.getRepo());
        assertEquals("someArchive.zip!/somePath/someFile", model.getPath());
        assertNull(model.getFooter());
        assertFalse(model.isRoot());
        assertFalse(model.isFolder());
        assertNull(model.getChildren());
    }

    @Test
    public void testWithRealRepoRemoteFile() {
        when(repositoryService.getAllRepoKeys()).thenReturn(Set.of("rpm-remote"));
        when(authorizationService.canRead(any(RepoPath.class))).thenReturn(true);
        TestRestRequest restRequest = mock(TestRestRequest.class);
        when(restRequest.getServletRequest()).thenReturn(mock(HttpServletRequest.class));
        ArtifactoryRestResponse<NativeBrowserModel> response = new ArtifactoryRestResponse<>();
        when(restRequest.getPathParamByKey(NativeBrowserResource.REPO_PATH)).thenReturn("rpm-remote/cool.zip");
        when(repositoryService.remoteRepoDescriptorByKey("rpm-remote")).thenReturn(new HttpRepoDescriptor());
        when(repositoryService.isRemoteRepoExist("rpm-remote")).thenReturn(true);
        when(repoBrowsingService.getRemoteRepoBrowsableChildren(any(BrowsableItemCriteria.class), anyBoolean(),
                any(RootNodesFilterResult.class))).thenThrow(ItemNotFoundRuntimeException.class);
        browseNativeService.execute(restRequest, response);

        assertEquals(HttpStatus.SC_OK, response.getResponseCode());
        NativeBrowserModel model = response.getIModel();
        assertEquals("rpm-remote", model.getRepo());
        assertEquals("cool.zip", model.getPath());
        assertNull(model.getFooter());
        assertFalse(model.isRoot());
        assertFalse(model.isFolder());
        assertNull(model.getChildren());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testItemNotFoundRuntimeExceptionHandling(boolean exceptionThrown) {
        ArtifactoryRestRequest<String> restRequest = mock(ArtifactoryRestRequest.class);
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getContextPath()).thenReturn("");
        when(servletRequest.getRequestURI()).thenReturn("");
        when(restRequest.getServletRequest()).thenReturn(servletRequest);
        RestResponse<?> response = new ArtifactoryRestResponse<>();
        when(restRequest.getPathParamByKey("repoPath")).thenReturn("cool-repo/somePath!/file.txt");
        when(repositoryService.getAllRepoKeys()).thenReturn(Set.of("cool-repo"));
        when(repositoryService.localFederatedCachedRepoDescriptorByKey("cool-repo")).thenReturn(
                new LocalRepoDescriptor());
        when(repoDescriptor.getType()).thenReturn(RepoType.Generic);
        when(repositoryService.repoDescriptorByKey(any())).thenReturn(repoDescriptor);
        doThrow(ItemNotFoundRuntimeException.class).when(browseNativeService)
                .isFolderPath(any(), any(), any());
        if (exceptionThrown) {
            when(restRequest.getPathParamByKey("repoPath")).thenReturn("cool-repo/somePath/file.txt");
            doReturn(false).when(browseNativeService).isRemoteFileDownload(any(RepoPath.class));
            assertThrows(ItemNotFoundRuntimeException.class,
                    () -> browseNativeService.execute(restRequest, response));
        } else {
            doReturn(true).when(browseNativeService).isRemoteFileDownload(any(RepoPath.class));
            browseNativeService.execute(restRequest, response);
            assertEquals(HttpStatus.SC_OK, response.getResponseCode());
            NativeBrowserModel nativeBrowserModel = response.getIModel();
            assertEquals("cool-repo", nativeBrowserModel.getRepo());
            assertEquals("somePath!/file.txt", nativeBrowserModel.getPath());
            assertEquals("somePath!/file.txt", nativeBrowserModel.getPath());
            assertEquals("somePath!/file.txt", nativeBrowserModel.getPath());
            assertNull(nativeBrowserModel.getFooter());
            assertNull(nativeBrowserModel.getChildren());
        }
    }

    @Test
    public void testWithRealRepoPathAndAuthorizedAnonUser() {
        when(authorizationService.canRead(any(RepoPath.class))).thenReturn(false);
        when(securityService.isAnonAccessEnabled()).thenReturn(true);
        when(securityService.isAnonymous()).thenReturn(true);
        when(repositoryService.getAllRepoKeys()).thenReturn(Set.of("generic"));
        when(repoDescriptor.getType()).thenReturn(RepoType.Generic);
        when(repositoryService.repoDescriptorByKey(any())).thenReturn(repoDescriptor);
        TestRestRequest restRequest = mock(TestRestRequest.class);
        when(restRequest.getPathParamByKey(NativeBrowserResource.REPO_PATH)).thenReturn("generic/somePath");
        when(repositoryService.localFederatedCachedRepoDescriptorByKey("generic"))
                .thenReturn(new LocalRepoDescriptor());
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getContextPath()).thenReturn("");
        when(servletRequest.getRequestURI()).thenReturn("");
        when(restRequest.getServletRequest()).thenReturn(servletRequest);

        List<BaseBrowsableItem> expectedList = new LinkedList<>();
        expectedList
                .add(new BrowsableItem("folder", true, 123L, 1234L, 200L, RepoPathFactory.create("generic", "folder")));
        Answer<List<BaseBrowsableItem>> answer = invocationOnMock -> {
            RootNodesFilterResult rootNodesFilterResult = invocationOnMock.getArgument(2, RootNodesFilterResult.class);
            rootNodesFilterResult.setAllItemNodesCanRead(false);
            return expectedList;
        };
        doAnswer(answer).when(repoBrowsingService)
                .getLocalRepoBrowsableChildren(any(BrowsableItemCriteria.class), anyBoolean(),
                        any(RootNodesFilterResult.class));

        HttpServletResponse httpServletResponse = new MockHttpServletResponse();
        ArtifactoryRestResponse<NativeBrowserModel> response = new ArtifactoryRestResponse<>(httpServletResponse);
        browseNativeService.execute(restRequest, response);

        assertEquals(HttpStatus.SC_OK, response.getResponseCode());
        NativeBrowserModel model = response.getIModel();
        assertEquals("generic", model.getRepo());
        assertEquals("somePath", model.getPath());
        assertEquals("1.0.0 Server", model.getFooter());
        assertFalse(model.isRoot());
        assertTrue(model.isFolder());
        List<NativeBrowserChild> items = model.getChildren();
        assertEquals(1, items.size());
    }

    @Test
    public void testWithFileAndUnAuthorizedAnonUser() {
        when(authorizationService.canRead(any(RepoPath.class))).thenReturn(false);
        when(securityService.isAnonAccessEnabled()).thenReturn(true);
        when(securityService.isAnonymous()).thenReturn(true);
        when(repositoryService.getAllRepoKeys()).thenReturn(Set.of("virtual"));
        TestRestRequest restRequest = mock(TestRestRequest.class);
        when(restRequest.getPathParamByKey(NativeBrowserResource.REPO_PATH))
                .thenReturn("virtual/somePath/file.zip");
        when(repositoryService.virtualRepoDescriptorByKey("virtual"))
                .thenReturn(new VirtualRepoDescriptor());
        when(repoBrowsingService
                .getVirtualRepoBrowsableChildren(any(BrowsableItemCriteria.class), any(Boolean.class),
                        any(RootNodesFilterResult.class)))
                .thenThrow(new FolderExpectedException(RepoPathFactory.create("virtual", "somePath/file.zip")));
        when(restRequest.getServletRequest()).thenReturn(mock(HttpServletRequest.class));

        HttpServletResponse httpServletResponse = new MockHttpServletResponse();
        ArtifactoryRestResponse<NativeBrowserModel> response = new ArtifactoryRestResponse<>(httpServletResponse);
        browseNativeService.execute(restRequest, response);

        assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getResponseCode());
    }

    @Test
    @ValueSource(strings = {"", "/", "/folder/path/"})
    public void testDefaultFolderDefinition() {
        assertTrue(browseNativeService.isFolderPath("/", mock(ArtifactoryRestRequest.class),
                mock(RestResponse.class)));
    }

    @Test
    public void testFolderDefinitionForNullablePath() {
        assertTrue(browseNativeService.isFolderPath(null, mock(ArtifactoryRestRequest.class),
                mock(RestResponse.class)));
    }

    @Test
    public void testFolderDefinitionForNonExistingParentItem() {
        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        RestResponse response = mock(RestResponse.class);
        String path = "path/file";

        TestBrowseNativeModelService browseNativeModelService = new TestBrowseNativeModelService(null);
        assertTrue(browseNativeModelService.isFolderPath(path, request, response));
    }

    @Test
    public void testFolderDefinitionForParentWithNoChildren() {
        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        ArtifactoryRestResponse response = mock(ArtifactoryRestResponse.class);
        String path = "path/file";
        NativeBrowserModel nativeBrowserModel =
                new NativeBrowserModel("repo", path, "footer", false, false, List.of());

        TestBrowseNativeModelService browseNativeModelService = new TestBrowseNativeModelService(nativeBrowserModel);
        assertTrue(browseNativeModelService.isFolderPath(path, request, response));
    }

    @Test
    public void testFolderDefinitionForParentThatDoesNotHaveChildItem() {
        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        ArtifactoryRestResponse response = mock(ArtifactoryRestResponse.class);
        String filePath = "path/file1.txt";
        NativeBrowserModel nativeBrowserModel =
                new NativeBrowserModel("repo", filePath, "footer", false, false,
                        List.of(new NativeBrowserChild("file.txt", 1L, 1L, false, false)));

        TestBrowseNativeModelService browseNativeModelService = new TestBrowseNativeModelService(nativeBrowserModel);
        assertFalse(browseNativeModelService.isFolderPath(filePath, request, response));
    }

    @Test
    public void testFolderDefinitionForParentWithFileChildItem() {
        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        ArtifactoryRestResponse response = mock(ArtifactoryRestResponse.class);
        String fileName = "file.txt";
        String filePath = "path/" + fileName;
        NativeBrowserModel nativeBrowserModel =
                new NativeBrowserModel("repo", filePath, "footer", false, false, List.of(
                        new NativeBrowserChild("file1", 1L, 1L, false, false),
                        new NativeBrowserChild(fileName, 1L, 1L, false, false)
                ));

        TestBrowseNativeModelService browseNativeModelService = new TestBrowseNativeModelService(nativeBrowserModel);
        assertFalse(browseNativeModelService.isFolderPath(filePath, request, response));
    }

    @Test
    public void testFolderDefinitionForParentWithFolderChildItem() {
        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        ArtifactoryRestResponse response = mock(ArtifactoryRestResponse.class);
        String folderName = "folder";
        String folderPath = "path/" + folderName;
        NativeBrowserModel nativeBrowserModel =
                new NativeBrowserModel("repo", folderPath, "footer", false, false, List.of(
                        new NativeBrowserChild(folderName, 1L, 1L, true, false)
                ));

        TestBrowseNativeModelService browseNativeModelService = new TestBrowseNativeModelService(nativeBrowserModel);
        assertTrue(browseNativeModelService.isFolderPath(folderPath, request, response));
    }

    @Test
    public void testFolderDefinitionForRepoKey() {
        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        ArtifactoryRestResponse response = mock(ArtifactoryRestResponse.class);
        String repoName = "repo";
        TestBrowseNativeModelService browseNativeModelService = new TestBrowseNativeModelService(null);
        assertTrue(browseNativeModelService.isFolderPath(repoName, request, response));
    }

    private static Stream<Arguments> uiRequestAndStatus() {
        return Stream.of(
                arguments("", HttpStatus.SC_FORBIDDEN),
                arguments("/ui/", HttpStatus.SC_UNAUTHORIZED));
    }

    private static class TestRestRequest extends ArtifactoryRestRequest<String> {
        public TestRestRequest(RequestBuilder<String> requestBuilder) {
            super(requestBuilder);
        }
    }

    private class TestBrowseNativeModelService extends BrowseNativeModelService {
        private NativeBrowserModel nativeBrowserModel;

        public TestBrowseNativeModelService(NativeBrowserModel nativeBrowserModel) {
            this.nativeBrowserModel = nativeBrowserModel;
        }

        @Nullable
        @Override
        NativeBrowserModel buildNativeBrowserData(ArtifactoryRestRequest<String> request, RestResponse response,
                RepoPath repoPath) {
            return nativeBrowserModel;
        }
    }
}
