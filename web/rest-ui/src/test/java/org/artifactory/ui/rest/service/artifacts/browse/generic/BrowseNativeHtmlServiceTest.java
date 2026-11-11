package org.artifactory.ui.rest.service.artifacts.browse.generic;

import com.google.common.collect.Lists;
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
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;
import org.springframework.http.MediaType;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;

import static org.artifactory.ui.rest.service.artifacts.browse.generic.BaseBrowseNativeService.ATTR_ARTIFACTORY_REPOSITORY_PATH;
import static org.artifactory.ui.rest.service.artifacts.browse.generic.BaseBrowseNativeService.REALM;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Mor Merhav
 */
public class BrowseNativeHtmlServiceTest extends ArtifactoryHomeBoundTest {

    private MockitoSession mockitoSession;
    private static final RepoPath REPO_PATH = RepoPathFactory.create("repoKey");
    @Mock
    private InternalRepositoryService repositoryService;
    @Mock
    private RepositoryBrowsingService repoBrowsingService;
    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private SecurityService securityService;
    @InjectMocks
    private BrowseNativeHtmlService browseNativeService;

    @BeforeMethod
    public void setUp() {
        mockitoSession = Mockito.mockitoSession()
                .initMocks(this)
                .strictness(Strictness.STRICT_STUBS)
                .startMocking();
    }

    @AfterMethod
    public void tearDown() {
        mockitoSession.finishMocking();
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    public void testExecuteReturns401() {
        ArtifactoryRestRequest artifactoryRestRequestMock = mock(ArtifactoryRestRequest.class);
        HttpServletRequest httpServletRequestMock = mock(HttpServletRequest.class);

        RestResponse restResponseMock = mock(RestResponse.class);
        HttpServletResponse httpServletResponseMock = mock(HttpServletResponse.class);

        when(artifactoryRestRequestMock.getServletRequest()).thenReturn(httpServletRequestMock);
        when(httpServletRequestMock.getAttribute(ATTR_ARTIFACTORY_REPOSITORY_PATH))
                .thenReturn(REPO_PATH);

        when(restResponseMock.getServletResponse()).thenReturn(httpServletResponseMock);

        when(repositoryService.localFederatedCachedRepoDescriptorByKey(REPO_PATH.getRepoKey()))
                .thenReturn(new LocalRepoDescriptor());
        when(repoBrowsingService.getLocalRepoBrowsableChildren(any(BrowsableItemCriteria.class), any(Boolean.class),
                any(RootNodesFilterResult.class)))
                .thenAnswer((Answer<List<BaseBrowsableItem>>) invocation -> {
                    RootNodesFilterResult rootNodesFilterResult = invocation.getArgument(2);
                    rootNodesFilterResult.setAllItemNodesCanRead(false);
                    return Lists.newArrayList();
                });

        browseNativeService.execute(artifactoryRestRequestMock, restResponseMock);

        verifyForbiddenResponse(restResponseMock, httpServletResponseMock);
    }

    @Test
    public void testExecuteReturns200AndBrowsableItemsHtml() {
        ArtifactoryContext artifactoryContextMock = mock(ArtifactoryContext.class);
        ArtifactoryContextThreadBinder.bind(artifactoryContextMock);
        AddonsManager addonsManagerMock = mock(AddonsManager.class);
        CoreAddons coreAddonsMock = mock(CoreAddons.class);

        when(artifactoryContextMock.beanForType(AddonsManager.class)).thenReturn(addonsManagerMock);
        when(addonsManagerMock.addonByType(CoreAddons.class)).thenReturn(coreAddonsMock);
        when(coreAddonsMock.getListBrowsingVersion()).thenReturn("1.0.0");

        RepoPath repoPath = RepoPathFactory.create("repoKey");

        ArtifactoryRestRequest artifactoryRestRequestMock = mock(ArtifactoryRestRequest.class);
        HttpServletRequest httpServletRequestMock = mock(HttpServletRequest.class);

        RestResponse restResponseMock = mock(RestResponse.class);

        when(artifactoryRestRequestMock.getServletRequest()).thenReturn(httpServletRequestMock);
        when(httpServletRequestMock.getAttribute(ATTR_ARTIFACTORY_REPOSITORY_PATH))
                .thenReturn(repoPath);

        when(repositoryService.localFederatedCachedRepoDescriptorByKey(repoPath.getRepoKey()))
                .thenReturn(new LocalRepoDescriptor());

        long time = 1607609635324L;

        when(repoBrowsingService
                .getLocalRepoBrowsableChildren(any(BrowsableItemCriteria.class), any(Boolean.class),
                        any(RootNodesFilterResult.class)))
                .thenAnswer((Answer<List<BaseBrowsableItem>>) invocation -> Lists.newArrayList(
                        new BrowsableItem("folder1", true, time, time,
                                123L, RepoPathFactory.create("repoKey", "folder1")),
                        new BrowsableItem("file", false, time, time,
                                12L, RepoPathFactory.create("repoKey", "file"))));

        browseNativeService.execute(artifactoryRestRequestMock, restResponseMock);

        ArgumentCaptor<String> iModelArgumentCaptor = ArgumentCaptor.forClass(String.class);

        verify(restResponseMock).iModel(iModelArgumentCaptor.capture());

        String iModel = iModelArgumentCaptor.getValue();
        Assert.assertNotNull(iModel);
        Assert.assertTrue(iModel.contains("folder1"));
        Assert.assertTrue(iModel.contains("file"));
    }

    @Test
    public void testExecuteWithoutReadPermissionOnRepoReturns401() {
        ArtifactoryContext artifactoryContextMock = mock(ArtifactoryContext.class);
        ArtifactoryContextThreadBinder.bind(artifactoryContextMock);
        when(authorizationService.canRead(REPO_PATH)).thenReturn(false);

        ArtifactoryRestRequest artifactoryRestRequestMock = mock(ArtifactoryRestRequest.class);
        RestResponse restResponseMock = mock(RestResponse.class);
        HttpServletRequest httpServletRequestMock = mock(HttpServletRequest.class);

        when(artifactoryRestRequestMock.getServletRequest()).thenReturn(httpServletRequestMock);
        when(httpServletRequestMock.getAttribute(ATTR_ARTIFACTORY_REPOSITORY_PATH))
                .thenReturn(REPO_PATH);

        when(repositoryService.localFederatedCachedRepoDescriptorByKey(REPO_PATH.getRepoKey()))
                .thenReturn(new LocalRepoDescriptor());
        when(repoBrowsingService.getLocalRepoBrowsableChildren(any(BrowsableItemCriteria.class), any(Boolean.class),
                any(RootNodesFilterResult.class)))
                .thenAnswer((Answer<List<BaseBrowsableItem>>) invocation -> {
                    RootNodesFilterResult rootNodesFilterResult = invocation.getArgument(2);
                    rootNodesFilterResult.setAllItemNodesCanRead(true);
                    return Lists.newArrayList();
                });

        HttpServletResponse httpServletResponseMock = mock(HttpServletResponse.class);
        when(restResponseMock.getServletResponse()).thenReturn(httpServletResponseMock);

        browseNativeService.execute(artifactoryRestRequestMock, restResponseMock);
        verifyForbiddenResponse(restResponseMock, httpServletResponseMock);
    }

    private void verifyForbiddenResponse(RestResponse restResponseMock, HttpServletResponse httpServletResponseMock) {
        ArgumentCaptor<String> headerKeyArgCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> headerValueArgCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> contentTypeArgCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> statusCodeArgCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<String> errResponseArgCaptor = ArgumentCaptor.forClass(String.class);

        verify(httpServletResponseMock).addHeader(headerKeyArgCaptor.capture(), headerValueArgCaptor.capture());
        verify(httpServletResponseMock).setContentType(contentTypeArgCaptor.capture());
        verify(restResponseMock).responseCode(statusCodeArgCaptor.capture());
        verify(restResponseMock).error(errResponseArgCaptor.capture());

        String headerKey = headerKeyArgCaptor.getValue();
        String headerValue = headerValueArgCaptor.getValue();
        String contentType = contentTypeArgCaptor.getValue();
        Integer statusCode = statusCodeArgCaptor.getValue();
        String errResponse = errResponseArgCaptor.getValue();

        Assert.assertEquals(headerKey, "WWW-Authenticate");
        Assert.assertEquals(headerValue, "Basic realm=\"" + REALM + "\"");
        Assert.assertEquals(contentType, MediaType.APPLICATION_JSON_VALUE);
        Assert.assertEquals(statusCode.intValue(), HttpStatus.SC_UNAUTHORIZED);
        Assert.assertEquals(errResponse, "Browse request for repo:path " + REPO_PATH + " is forbidden " +
                "for user 'anonymous'.");
    }
}