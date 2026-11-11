package org.artifactory.ui.rest.service.artifacts.browse.generic;

import org.apache.http.HttpStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.CoreAddons;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.BaseBrowsableItem;
import org.artifactory.api.repo.BrowsableItem;
import org.artifactory.api.repo.BrowsableItemCriteria;
import org.artifactory.api.repo.RepositoryBrowsingService;
import org.artifactory.api.repo.RootNodesFilterResult;
import org.artifactory.api.repo.exception.ItemNotFoundRuntimeException;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.descriptor.config.CentralConfigDescriptorImpl;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
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
import org.artifactory.ui.rest.model.artifacts.browse.generic.NativeBrowserModel;
import org.artifactory.ui.rest.model.artifacts.browse.generic.NativeBrowserPaginatedModel;
import org.artifactory.ui.rest.resource.artifacts.browse.generic.NativeBrowserResource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import static java.util.Collections.emptyList;
import static org.artifactory.common.ConstantValues.treeBrowserStreamEnabled;
import static org.artifactory.common.ConstantValues.uiHideChecksums;
import static org.artifactory.ui.rest.resource.artifacts.browse.generic.NativeBrowserResource.REPO_PATH;
import static org.artifactory.util.PackageTypeConfigFields.FORCE_MAVEN_AUTHENTICATION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * @author Hussein Alshqerat
 */
class BrowseNativeModelV2ServiceTest extends ArtifactoryHomeBoundJUnitTest {

    @Spy
    @InjectMocks
    private BrowseNativeModelV2Service browseNativeModelV2Service;
    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private SecurityService securityService;
    @Mock
    private InternalRepositoryService repositoryService;
    @Mock
    private CentralConfigService centralConfigService;
    @Mock
    private RepositoryBrowsingService repoBrowsingService;
    @Mock
    private RepoDescriptor repoDescriptor;
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private CoreAddons coreAddons;
    @Mock
    private ArtifactoryContext context;
    private AutoCloseable autoCloseable;

    @BeforeEach
    void setup() {
        autoCloseable = openMocks(this);
        when(context.beanForType(AuthorizationService.class)).thenReturn(authorizationService);
        ArtifactoryContextThreadBinder.bind(context);
        when(context.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        when(addonsManager.addonByType(CoreAddons.class)).thenReturn(coreAddons);
        when(coreAddons.getListBrowsingVersion()).thenReturn("1.0.0");
        ArtifactoryHome.get().getArtifactoryProperties().setProperty(uiHideChecksums.getPropertyName(), "FALSE");
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(treeBrowserStreamEnabled.getPropertyName(), "FALSE");
    }

    @AfterEach
    void cleanup() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        autoCloseable.close();
    }

    @Test
    void testFetchNativeItemsInvalidPath() {
        TestRestRequest restRequest = mock(TestRestRequest.class);
        RestResponse<?> response = new ArtifactoryRestResponse<>();
        when(restRequest.getPathParamByKey(REPO_PATH)).thenReturn("");
        when(securityService.isAnonAccessEnabled()).thenReturn(false);
        when(securityService.isAnonymous()).thenReturn(false);
        browseNativeModelV2Service.execute(restRequest, response);

        assertEquals(HttpStatus.SC_NOT_FOUND, response.getResponseCode());
    }

    @Test
    void testFetchNativeItems() {
        TestRestRequest restRequest = mock(TestRestRequest.class);
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getContextPath()).thenReturn("");
        when(servletRequest.getRequestURI()).thenReturn("");
        when(restRequest.getServletRequest()).thenReturn(servletRequest);
        RestResponse<?> response = new ArtifactoryRestResponse<>();
        when(authorizationService.canRead(any(RepoPath.class))).thenReturn(true);
        when(restRequest.getQueryParamByKey("recordNum")).thenReturn("");
        when(centralConfigService.getMutableDescriptor()).thenReturn(new CentralConfigDescriptorImpl());
        when(restRequest.getPathParamByKey(REPO_PATH)).thenReturn("cool-repo/");
        when(repositoryService.getAllRepoKeys()).thenReturn(Set.of("cool-repo"));
        List<BaseBrowsableItem> expectedList = new LinkedList<>();
        expectedList.add(
                new BrowsableItem("folder", true, 123L, 1234L, 200L, RepoPathFactory.create("generic", "folder")));
        expectedList.add(new BrowsableItem("file", false, 124L, 1235L, 30L, RepoPathFactory.create("generic", "file")));
        when(repositoryService.localFederatedCachedRepoDescriptorByKey("cool-repo")).thenReturn(
                new LocalRepoDescriptor());

        when(repoDescriptor.getType()).thenReturn(RepoType.Generic);
        when(repositoryService.repoDescriptorByKey(any())).thenReturn(repoDescriptor);
        when(repoBrowsingService.getLocalRepoBrowsableChildren(any(BrowsableItemCriteria.class), anyBoolean(),
                any(RootNodesFilterResult.class))).thenReturn(expectedList);
        browseNativeModelV2Service.execute(restRequest, response);

        assertEquals(2, ((NativeBrowserPaginatedModel) response.getIModel()).getData().size());
        assertEquals("-1", ((NativeBrowserPaginatedModel) response.getIModel()).getContinueState());
    }

    @Test
    void testFetchNativeItemsWithRecordNum() {
        TestRestRequest restRequest = mock(TestRestRequest.class);
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getContextPath()).thenReturn("");
        when(servletRequest.getRequestURI()).thenReturn("");
        when(restRequest.getServletRequest()).thenReturn(servletRequest);
        RestResponse<?> response = new ArtifactoryRestResponse<>();
        when(authorizationService.canRead(any(RepoPath.class))).thenReturn(true);
        when(restRequest.getQueryParamByKey("recordNum")).thenReturn("1");
        CentralConfigDescriptorImpl centralConfigDescriptor = new CentralConfigDescriptorImpl();
        centralConfigDescriptor.setItemsBrowserPagingSize(3);
        when(centralConfigService.getMutableDescriptor()).thenReturn(centralConfigDescriptor);
        when(restRequest.getPathParamByKey(REPO_PATH)).thenReturn("cool-repo/");
        when(repositoryService.getAllRepoKeys()).thenReturn(Set.of("cool-repo"));
        List<BaseBrowsableItem> expectedList = new LinkedList<>();
        expectedList.add(
                new BrowsableItem("folder", true, 123L, 1234L, 200L, RepoPathFactory.create("generic", "folder")));
        expectedList.add(new BrowsableItem("file", false, 124L, 1235L, 30L, RepoPathFactory.create("generic", "file")));
        expectedList.add(
                new BrowsableItem("file1", false, 124L, 1235L, 30L, RepoPathFactory.create("generic", "file1")));
        expectedList.add(
                new BrowsableItem("file2", false, 124L, 1235L, 30L, RepoPathFactory.create("generic", "file2")));
        expectedList.add(
                new BrowsableItem("file3", false, 124L, 1235L, 30L, RepoPathFactory.create("generic", "file3")));
        when(repositoryService.localFederatedCachedRepoDescriptorByKey("cool-repo")).thenReturn(
                new LocalRepoDescriptor());
        when(repoDescriptor.getType()).thenReturn(RepoType.Generic);
        when(repositoryService.repoDescriptorByKey(any())).thenReturn(repoDescriptor);
        when(repoBrowsingService.getLocalRepoBrowsableChildren(any(BrowsableItemCriteria.class), anyBoolean(),
                any(RootNodesFilterResult.class))).thenReturn(expectedList);
        browseNativeModelV2Service.execute(restRequest, response);

        assertEquals(3, ((NativeBrowserPaginatedModel) response.getIModel()).getData().size());
        assertEquals("4", ((NativeBrowserPaginatedModel) response.getIModel()).getContinueState());
    }

    @Test
    public void testWithEmptyRepoPathAndAnonUser() {
        BrowseNativeModelV2ServiceTest.TestRestRequest restRequest =
                mock(BrowseNativeModelV2ServiceTest.TestRestRequest.class);
        when(securityService.isAnonAccessEnabled()).thenReturn(true);
        when(securityService.isAnonymous()).thenReturn(true);
        ArtifactoryRestResponse<NativeBrowserModel> response = new ArtifactoryRestResponse<>();
        browseNativeModelV2Service.execute(restRequest, response);

        assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getResponseCode());
    }

    @Test
    public void testWithUnauthorizedUserAnonAccessEnabledMavenVirtualForceAuth() {
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
        when(restRequest.getPathParamByKey(NativeBrowserResource.REPO_PATH)).thenReturn("maven/somePath/");
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("maven", RepoType.Maven);
        virtualRepoConfig.getPackageTypeConfig().put(FORCE_MAVEN_AUTHENTICATION.getKey(), "true");
        when(repositoryService.repoConfigByKey("maven")).thenReturn(virtualRepoConfig);
        when(repositoryService.virtualRepoConfigByKey("maven")).thenReturn(virtualRepoConfig);
        when(repositoryService.virtualRepoDescriptorByKey("maven")).thenReturn(virtualRepoDescriptor);
        CentralConfigDescriptorImpl centralConfigDescriptor = new CentralConfigDescriptorImpl();
        centralConfigDescriptor.setItemsBrowserPagingSize(3);
        when(centralConfigService.getMutableDescriptor()).thenReturn(centralConfigDescriptor);
        doThrow(ItemNotFoundRuntimeException.class).when(repoBrowsingService)
                .getVirtualRepoBrowsableChildren(any(BrowsableItemCriteria.class), anyBoolean(),
                        any(RootNodesFilterResult.class));

        HttpServletResponse httpServletResponse = new MockHttpServletResponse();
        ArtifactoryRestResponse<NativeBrowserModel> response = new ArtifactoryRestResponse<>(httpServletResponse);
        browseNativeModelV2Service.execute(restRequest, response);

        assertEquals(401, response.getResponseCode());
        assertEquals("Browse request for repo:path maven:somePath is forbidden " +
                "for user 'anonymous'.", response.getFeedbackMsg().getError());
    }

    @Test
    public void testModelWithNullChildren() {
        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        String path = "repo/file.txt";
        MutableCentralConfigDescriptor mutableDescriptor = mock(MutableCentralConfigDescriptor.class);
        when(centralConfigService.getMutableDescriptor()).thenReturn(mutableDescriptor);
        when(mutableDescriptor.getItemsBrowserPagingSize()).thenReturn(3);
        when(repositoryService.getAllRepoKeys()).thenReturn(Set.of("repo"));
        when(repositoryService.localFederatedCachedRepoDescriptorByKey("repo")).thenReturn(new LocalRepoDescriptor());
        when(repoDescriptor.getType()).thenReturn(RepoType.Generic);
        when(repositoryService.repoDescriptorByKey("repo")).thenReturn(repoDescriptor);
        when(repoBrowsingService.getLocalRepoBrowsableChildren(any(BrowsableItemCriteria.class), anyBoolean(),
                any(RootNodesFilterResult.class))).thenReturn(emptyList());
        assertFalse(browseNativeModelV2Service.isFolderPath(path, request));
    }

    private static class TestRestRequest extends ArtifactoryRestRequest<String> {
        public TestRestRequest(RequestBuilder<String> requestBuilder) {
            super(requestBuilder);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "true, TRUE",
            "true, FALSE",
            "false, TRUE",
            "false, FALSE"
    })
    void testItemNotFoundRuntimeExceptionHandling(boolean exceptionThrown, String setTreeBrowserStreamEnabled) {
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(treeBrowserStreamEnabled.getPropertyName(), setTreeBrowserStreamEnabled);
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
        doThrow(ItemNotFoundRuntimeException.class).when(browseNativeModelV2Service)
                .isFolderPath(any(), any());
        if (exceptionThrown) {
            when(restRequest.getPathParamByKey("repoPath")).thenReturn("cool-repo/somePath/file.txt");
            doReturn(false).when(browseNativeModelV2Service).isRemoteFileDownload(any(RepoPath.class));
            assertThrows(ItemNotFoundRuntimeException.class,
                    () -> browseNativeModelV2Service.execute(restRequest, response));
        } else {
            doReturn(true).when(browseNativeModelV2Service).isRemoteFileDownload(any(RepoPath.class));
            browseNativeModelV2Service.execute(restRequest, response);
            assertEquals(HttpStatus.SC_OK, response.getResponseCode());
            NativeBrowserPaginatedModel nativeBrowserPaginatedModel = response.getIModel();
            assertEquals("cool-repo", nativeBrowserPaginatedModel.getRepo());
            assertEquals("somePath!/file.txt", nativeBrowserPaginatedModel.getPath());
            assertEquals("somePath!/file.txt", nativeBrowserPaginatedModel.getPath());
            assertEquals("somePath!/file.txt", nativeBrowserPaginatedModel.getPath());
            assertNull(nativeBrowserPaginatedModel.getFooter());
            assertNull(nativeBrowserPaginatedModel.getTotalRequiredDataAmount());
            assertEquals("-1", nativeBrowserPaginatedModel.getContinueState());
        }
    }
}