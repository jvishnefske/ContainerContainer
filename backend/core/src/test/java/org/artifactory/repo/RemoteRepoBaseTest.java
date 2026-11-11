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

package org.artifactory.repo;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.collect.ArrayListMultimap;
import com.jfrog.ph.acl.exception.PackageBlockedByCurationException;
import org.apache.commons.compress.utils.Sets;
import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.utils.DateUtils;
import org.apache.http.message.BasicHeader;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.HaAddon;
import org.artifactory.addon.LayoutsCoreAddon;
import org.artifactory.addon.RestCoreAddon;
import org.artifactory.addon.curation.CurationAddon;
import org.artifactory.addon.curation.CurationCachedPackageStatus;
import org.artifactory.addon.curation.CurationPackageStatus;
import org.artifactory.addon.curation.CurationPackageStatusPayload;
import org.artifactory.addon.curation.SecurityDecision;
import org.artifactory.addon.curation.packages.PackagesCurationCompliantVersionService;
import org.artifactory.addon.plugin.PluginsAddon;
import org.artifactory.addon.plugin.ResourceStreamCtx;
import org.artifactory.addon.worker.WorkersAddon;
import org.artifactory.addon.xray.XrayAddon;
import org.artifactory.aol.AolService;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.ResearchService;
import org.artifactory.api.repo.exception.RepoRejectException;
import org.artifactory.api.request.InternalArtifactoryRequest;
import org.artifactory.api.request.TranslatedArtifactoryRequest;
import org.artifactory.api.search.exception.InvalidChecksumException;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.checksum.ChecksumInfo;
import org.artifactory.checksum.ChecksumType;
import org.artifactory.checksum.ChecksumsInfo;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.status.StatusHolder;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.config.CentralConfigDescriptorImpl;
import org.artifactory.descriptor.repo.HttpRepoDescriptor;
import org.artifactory.descriptor.repo.RemoteRepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.engine.ChecksumDeployPermissionValidator;
import org.artifactory.exception.WorkerStopException;
import org.artifactory.fs.RepoResource;
import org.artifactory.io.checksum.ChecksumUtils;
import org.artifactory.md.Properties;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RemoteRepositoryRepoTypeConfig;
import org.artifactory.model.fs.FileInfoImpl;
import org.artifactory.opentracing.ArtifactoryTracer;
import org.artifactory.properties.PropertiesImpl;
import org.artifactory.repo.cache.expirable.CacheExpiry;
import org.artifactory.repo.db.DbCacheRepo;
import org.artifactory.repo.db.DbStoringRepoMixin;
import org.artifactory.repo.http.ConnectionManagersHolder;
import org.artifactory.repo.http.DenyListCacheHolderService;
import org.artifactory.repo.http.HttpConnectionPoolMonitorService;
import org.artifactory.repo.http.IdleConnectionMonitorService;
import org.artifactory.repo.http.IdleConnectionMonitorServiceImpl;
import org.artifactory.repo.remote.browse.RemoteItem;
import org.artifactory.repo.remote.browse.RemoteRepositoryBrowser;
import org.artifactory.repo.remote.interceptor.ExpiryAction;
import org.artifactory.repo.remote.interceptor.RemoteRepoInterceptor;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.repo.service.RepositoryServiceImpl;
import org.artifactory.repo.service.deploy.UIBuildArtifactoryRequest;
import org.artifactory.api.request.ArtifactoryHeaders;
import org.artifactory.repo.multipart.ParallelDownloadService;
import org.artifactory.request.AlteredDownloadRequestContext;
import org.artifactory.request.InternalRequestContext;
import org.artifactory.request.RemoteRequestException;
import org.artifactory.request.Request;
import org.artifactory.request.RequestContext;
import org.artifactory.request.UrlVerifier;
import org.artifactory.resource.BlockedRepoResource;
import org.artifactory.resource.ExpiredRepoResource;
import org.artifactory.resource.FileResource;
import org.artifactory.resource.RemoteRepoResource;
import org.artifactory.resource.RepoResourceInfo;
import org.artifactory.resource.ResourceStreamHandle;
import org.artifactory.resource.ResourceWithChecksumStreamHandle;
import org.artifactory.resource.UnfoundRepoResource;
import org.artifactory.resource.UnfoundRepoResourceReason.Reason;
import org.artifactory.sh.acl.ArtifactoryBinaryService;
import org.artifactory.spring.InternalArtifactoryContext;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.fs.service.FileService;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.unifiedevent.UnifiedEventService;
import org.artifactory.unifiedevent.api.request.ArtifactResourceContext;
import org.artifactory.unifiedevent.constants.SupportedUnifiedEvents;
import org.artifactory.util.RepoLayoutUtils;
import org.artifactory.util.StringInputStream;
import org.artifactory.version.ArtifactoryServiceVersion;
import org.artifactory.webapp.servlet.HttpArtifactoryRequest;
import org.jfrog.common.concurrency.ConflictsGuard;
import org.jfrog.storage.binstore.exceptions.BinaryNotFoundException;
import org.jfrog.storage.binstore.ifc.BinaryServiceInputStream;
import org.jfrog.storage.binstore.ifc.model.BinaryElement;
import org.jfrog.worker.proto.execution.Action;
import org.junit.jupiter.api.Assertions;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static jakarta.ws.rs.core.Response.Status.TOO_MANY_REQUESTS;
import static org.artifactory.common.ConstantValues.artifactoryProductVersion;
import static org.artifactory.request.ArtifactoryRequest.CHECKSUM_MD5;
import static org.artifactory.request.ArtifactoryRequest.CHECKSUM_SHA1;
import static org.artifactory.request.ArtifactoryRequest.CHECKSUM_SHA256;
import static org.artifactory.request.ArtifactoryRequest.PARAM_ALTERNATIVE_REMOTE_DOWNLOAD_URL;
import static org.artifactory.request.ArtifactoryRequest.PARAM_ALTERNATIVE_REMOTE_SITE_URL;
import static org.artifactory.request.ArtifactoryRequest.PARAM_REPLICATION_ORIGINATED_DOWNLOAD_REQUEST;
import static org.fest.assertions.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertNull;
import static org.testng.AssertJUnit.assertTrue;

/**
 * This class tests the behaviour of the allowsDownloadMethod and checks that it returns the proper results on different
 * scenarios
 *
 * @author Noam Tenne
 */
@Test
public class RemoteRepoBaseTest extends ArtifactoryHomeBoundTest {

    private static final CurationPackageStatus PERMITTED_STATUS = new CurationPackageStatus(true, null, null);
    private static final CurationPackageStatus BLOCKED_STATUS = new CurationPackageStatus(false, null, null);

    @Mock
    private AuthorizationService authService;
    @Mock
    private SecurityService securityService;
    @Mock
    private InternalRepositoryService internalRepoService;
    @Mock
    private ResearchService researchService;
    @Mock
    private InternalArtifactoryContext context;
    @Mock
    private FileService fileService;
    @Mock
    private AolService aolService;
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private LayoutsCoreAddon layoutsCoreAddon;
    @Mock
    private HaAddon haAddon;
    @Mock
    private XrayAddon xrayAddon;
    @Mock
    private CurationAddon curationAddon;
    @Mock
    private RestCoreAddon restCoreAddon;
    @Mock
    private PluginsAddon pluginsAddon;
    @Mock
    private WorkersAddon workersAddon;
    @Mock
    private InternalRequestContext requestContext;
    @Mock
    private Request request;
    @Mock
    private CloseableHttpResponse response;
    @Mock
    private HttpRepo.TrafficAwareRemoteResourceStreamHandle trafficAwareRemoteResourceStreamHandle;
    @Mock
    private CentralConfigService configService;
    @Mock
    private UrlVerifier urlVerifier;
    @Mock
    private ArtifactoryBinaryService binaryService;
    @Mock
    private ChecksumDeployPermissionValidator checksumDeployPermissionValidator;
    @Mock
    private PackagesCurationCompliantVersionService curationCompliantVersionService;
    @Mock
    private UnifiedEventService unifiedEventService;

    private HttpRepoDescriptor httpRepoDescriptor = new HttpRepoDescriptor();
    private RemoteRepoConfig remoteRepoConfig;
    private HttpRepo httpRepo;
    private RepoPath repoPath = InternalRepoPathFactory.create("remote-repo-cache", "test/test/1.0/test-1.0.jar");
    private RepoPath nonCachedRepoPath = InternalRepoPathFactory.create("remote-repo", "test/test/1.0/test-1.0.jar");
    private String timeStr = "Tue, 17 Feb 2015 13:15:35 GMT";
    private ChecksumsInfo checksumsInfo;
    private AutoCloseable mocks;
    private String sha1 = "1234567890123456789012345678901234567890";

    @BeforeMethod
    public void setup() {
        remoteRepoConfig = RemoteRepoConfig.create("remoteRepoKey", RepoType.Generic);
        System.setProperty(artifactoryProductVersion.getPropertyName(),
                ArtifactoryServiceVersion.getCurrent().getVersion());
        getBound().setProperty(artifactoryProductVersion.getPropertyName(),
                ArtifactoryServiceVersion.getCurrent().getVersion());
        mocks = openMocks(this);
        createOriginalChecksum();
        when(context.getAuthorizationService()).thenReturn(authService);
        when(context.beanForType(SecurityService.class)).thenReturn(securityService);
        when(context.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        when(aolService.isAol()).thenReturn(false);
        when(context.beanForType(AolService.class)).thenReturn(aolService);
        when(context.beanForType(HttpConnectionPoolMonitorService.class)).thenReturn(
                mock(HttpConnectionPoolMonitorService.class));
        when(fileService.exists(any())).thenReturn(true);
        when(context.beanForType(FileService.class)).thenReturn(fileService);
        when(addonsManager.addonByType(LayoutsCoreAddon.class)).thenReturn(layoutsCoreAddon);
        when(addonsManager.addonByType(CurationAddon.class)).thenReturn(mock(CurationAddon.class));
        when(addonsManager.addonByType(HaAddon.class)).thenReturn(haAddon);
        when(addonsManager.addonByType(XrayAddon.class)).thenReturn(xrayAddon);
        when(addonsManager.addonByType(CurationAddon.class)).thenReturn(curationAddon);
        when(curationAddon.getPackageStatus(any(), any(), any(), any(), anyBoolean())).thenReturn(
                new CurationCachedPackageStatus(null, PERMITTED_STATUS));
        when(addonsManager.addonByType(RestCoreAddon.class)).thenReturn(restCoreAddon);
        when(addonsManager.addonByType(PluginsAddon.class)).thenReturn(pluginsAddon);
        when(addonsManager.addonByType(WorkersAddon.class)).thenReturn(workersAddon);
        when(context.beanForType(ArtifactoryTracer.class)).thenReturn(mock(ArtifactoryTracer.class));
        IdleConnectionMonitorServiceImpl idleConnectionMonitorService =
                new IdleConnectionMonitorServiceImpl(mock(ConnectionManagersHolder.class));
        when(context.beanForType(IdleConnectionMonitorService.class)).thenReturn(idleConnectionMonitorService);
        when(context.beanForType(DenyListCacheHolderService.class)).thenReturn(
                mock(DenyListCacheHolderService.class));
        when(context.beanForType(UrlVerifier.class)).thenReturn(urlVerifier);
        when(requestContext.getRequest()).thenReturn(request);
        when(request.getRepoPath()).thenReturn(repoPath);
        when(context.beanForType(ArtifactoryBinaryService.class)).thenReturn(binaryService);
        ArtifactoryContextThreadBinder.bind(context);
        CentralConfigDescriptorImpl centralConfigDescriptor = new CentralConfigDescriptorImpl();
        centralConfigDescriptor.setRepoLayouts(List.of(RepoLayoutUtils.MAVEN_2_DEFAULT));
        when(configService.getDescriptor()).thenReturn(centralConfigDescriptor);
        when(context.beanForType(ChecksumDeployPermissionValidator.class)).thenReturn(
                checksumDeployPermissionValidator);
        remoteRepoConfig.getBaseConfig().setRepoLayoutRef(RepoLayoutUtils.MAVEN_2_DEFAULT.getName());
        remoteRepoConfig.getRepoTypeConfig().setUrl("http://someUrl");
        httpRepo =
                spy(new HttpRepo(httpRepoDescriptor, remoteRepoConfig, internalRepoService, addonsManager,
                        researchService, null, null, configService));
    }

    @AfterMethod
    public void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @Test
    public void testSendAfterRemoteDownloadUnifiedEvent_delegatesToUnifiedEventService() {
        when(context.beanForType(UnifiedEventService.class)).thenReturn(unifiedEventService);
        when(context.beanForType(HttpConnectionPoolMonitorService.class))
                .thenReturn(new HttpConnectionPoolMonitorService());
        httpRepo.init(false);

        ArtifactResourceContext resourceContext = mock(ArtifactResourceContext.class);

        httpRepo.sendAfterRemoteDownloadUnifiedEvent(resourceContext);

        verify(unifiedEventService).sendRemoteArtifactEvent(
                eq(SupportedUnifiedEvents.AFTER_REMOTE_DOWNLOAD), eq(resourceContext));
    }

    private void createOriginalChecksum() {
        String sha1 = "f8237d8959e03355010bb85cc3dc46a46fb31110";
        String sha256 = "0f8eb4b72b6e0c9e88b388eb967b49e067ef1004bf07bffc22c3acb13b43580a";
        String md5 = "a35fe7f7fe8217b4369a0af4244d1fca";

        ChecksumInfo sha1ChecksumInfo = new ChecksumInfo(ChecksumType.sha1, sha1, null);
        ChecksumInfo sha256ChecksumInfo = new ChecksumInfo(ChecksumType.sha256, sha256, null);
        ChecksumInfo md5ChecksumInfo = new ChecksumInfo(ChecksumType.md5, md5, null);
        checksumsInfo = new ChecksumsInfo();
        checksumsInfo.addChecksumInfo(sha1ChecksumInfo);
        checksumsInfo.addChecksumInfo(sha256ChecksumInfo);
        checksumsInfo.addChecksumInfo(md5ChecksumInfo);
        when(trafficAwareRemoteResourceStreamHandle.getResponse())
                .thenReturn(response);
        when(response.getFirstHeader(HttpHeaders.LAST_MODIFIED))
                .thenReturn(new BasicHeader(HttpHeaders.LAST_MODIFIED, "Tue, 17 Feb 2015 13:15:35 GMT"));
        when(response.getFirstHeader(CHECKSUM_SHA1))
                .thenReturn(new BasicHeader(CHECKSUM_SHA1, sha1));
        when(response.getFirstHeader(CHECKSUM_SHA256))
                .thenReturn(new BasicHeader(CHECKSUM_SHA256, sha256));
        when(response.getFirstHeader(CHECKSUM_MD5))
                .thenReturn(new BasicHeader(CHECKSUM_MD5, md5));
        when(response.getFirstHeader(HttpHeaders.CONTENT_LENGTH))
                .thenReturn(new BasicHeader(HttpHeaders.CONTENT_LENGTH, "4"));
        when(response.getFirstHeader(HttpHeaders.ETAG))
                .thenReturn(new BasicHeader(HttpHeaders.ETAG, "etag-1"));

    }

    public void testInternalGetInfoFoundRemoteAndCachedResource() {
        RepoResource remoteResource = new RemoteRepoResource(repoPath, 1234, "etag", 50,
                Sets.newHashSet(), null);
        ExpiredRepoResource cachedResource = new ExpiredRepoResource(new FileResource(
                new FileInfoImpl(repoPath) {{setLastModified(123);}}));
        when(context.beanForType(HttpConnectionPoolMonitorService.class))
                .thenReturn(new HttpConnectionPoolMonitorService());
        httpRepo.init(false);
        doReturn(cachedResource).when(httpRepo).getInfoFromCache(requestContext);
        doReturn(remoteResource).when(httpRepo)
                .getRemoteResource(any(RequestContext.class), any(RepoPath.class), anyBoolean());

        RepoResource repoResource = httpRepo.internalGetInfo(nonCachedRepoPath, requestContext);
        assertTrue(repoResource instanceof RemoteRepoResource);
        RemoteRepoResource remoteRepoResource = (RemoteRepoResource) repoResource;
        assertEquals(remoteRepoResource.getCorrespondingCachedResource(), cachedResource);
    }

    public void testInternalGetInfoUnfoundResource() {
        RepoResource remoteResource = new UnfoundRepoResource(repoPath, TOO_MANY_REQUESTS.getReasonPhrase(),
                TOO_MANY_REQUESTS.getStatusCode());
        ExpiredRepoResource cachedResource = new ExpiredRepoResource(new FileResource(
                new FileInfoImpl(repoPath) {{setLastModified(123);}}));
        when(context.beanForType(HttpConnectionPoolMonitorService.class))
                .thenReturn(new HttpConnectionPoolMonitorService());
        httpRepo.init(false);
        doReturn(cachedResource).when(httpRepo).getInfoFromCache(requestContext);
        doReturn(remoteResource).when(httpRepo)
                .getRemoteResource(any(RequestContext.class), any(RepoPath.class), anyBoolean());

        RepoResource repoResource = httpRepo.internalGetInfo(nonCachedRepoPath, requestContext);
        assertTrue(repoResource instanceof ExpiredRepoResource);
        assertEquals(repoResource, cachedResource);
    }

    @Test(dataProvider = "testInternalGetInfoBlockedRemoteDownloadProvider")
    public void testInternalGetInfoBlockedRemoteDownload(boolean foundExpiredInCache) {
        RepoResource notFoundResource = new UnfoundRepoResource(repoPath, TOO_MANY_REQUESTS.getReasonPhrase(),
                TOO_MANY_REQUESTS.getStatusCode());
        RepoResource expiredCachedResource = new ExpiredRepoResource(new FileResource(
                new FileInfoImpl(repoPath) {{setLastModified(123);}}));
        when(context.beanForType(HttpConnectionPoolMonitorService.class))
                .thenReturn(new HttpConnectionPoolMonitorService());
        RemoteRepoInterceptor remoteRepoInterceptor = mock(RemoteRepoInterceptor.class);
        when(remoteRepoInterceptor.isRemoteDownloadAllowed(any(), any())).thenReturn(false);
        when(context.beansForType(RemoteRepoInterceptor.class))
                .thenReturn(Map.of("remoteRepoInterceptor", remoteRepoInterceptor));
        httpRepo.init(false);
        doReturn(foundExpiredInCache ? expiredCachedResource : notFoundResource).when(httpRepo)
                .getInfoFromCache(requestContext);
        RepoResource repoResource = httpRepo.internalGetInfo(nonCachedRepoPath, requestContext);
        if (foundExpiredInCache) {
            assertEquals(repoResource, expiredCachedResource);
        } else {
            assertTrue(repoResource instanceof UnfoundRepoResource);
        }
    }

    /**
     * Try to download while supplying a path with no "-cache" prefix
     */
    public void testDownloadWithNoCachePrefix() {
        RepoPath repoPathNoPrefix = InternalRepoPathFactory.create("remote-repo", "test/test/1.0/test-1.0.jar");
        expectCanRead(true);
        createCacheRepo();
        StatusHolder statusHolder = tryAllowsDownload(repoPathNoPrefix);
        Assert.assertFalse(statusHolder.isError());
        verifyCanRead();
    }

    /**
     * Try to download when Anonymous mode is enabled
     */
    public void testDownloadWithAnonymousEnabledAndNoReadPermissions() {
        expectCanRead(false);       // don't give read access
        expectXrayBlocked();
        createCacheRepo();   // and enable anon
        StatusHolder statusHolder = tryAllowsDownload(repoPath);
        Assert.assertTrue(statusHolder.isError());
        verifyCanRead();
    }

    private void verifyCanRead() {
        verify(authService).canRead(repoPath);
        reset(authService);
    }

    public void testGetResourceOfflineRepoPath() {
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.useRepoPathOnRemoteOffline.getPropertyName(), Boolean.toString(true));
        assertEquals("repoPath", httpRepo.getResourceOfflineRepoPath("path", "repoPath"));

        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.useRepoPathOnRemoteOffline.getPropertyName(), Boolean.toString(false));
        assertEquals("path", httpRepo.getResourceOfflineRepoPath("path", "repoPath"));
    }

    /**
     * Try to download when the repo is blacked out
     */
    public void testDownloadWithBlackedOut() {
        remoteRepoConfig.getRepoTypeConfig().setBlackedOut(true);
        createCacheRepo();
        StatusHolder statusHolder = tryAllowsDownload(repoPath);
        Assert.assertEquals(statusHolder.getLastError().getMessage(),
                "The repository '" + nonCachedRepoPath.getRepoKey() + "' is blacked out and cannot serve " +
                        "artifact '" + nonCachedRepoPath + "'.");
        remoteRepoConfig.getRepoTypeConfig().setBlackedOut(false);
    }

    /**
     * Try to download when the repo does not serve releases
     */
    public void testDownloadWithNoReleases() {
        remoteRepoConfig.getRepoTypeConfig().setHandleReleases(false);
        createCacheRepo();
        StatusHolder statusHolder = tryAllowsDownload(repoPath);
        Assert.assertEquals(statusHolder.getLastError().getMessage(),
                "The repository '" + nonCachedRepoPath.getRepoKey() + "' rejected the resolution of an artifact " +
                        "'" + nonCachedRepoPath + "' due to conflict in the snapshot release handling policy.");
        remoteRepoConfig.getRepoTypeConfig().setHandleReleases(true);
    }

    /**
     * Try to download when requested resource is excluded
     */
    public void testDownloadWithExcludes() {
        remoteRepoConfig.getBaseConfig().setExcludesPattern("test/test/1.0/**");
        createCacheRepo();
        StatusHolder statusHolder = tryAllowsDownload(repoPath);
        Assert.assertEquals(statusHolder.getLastError().getMessage(),
                "The repository '" + repoPath.getRepoKey() + "' rejected the artifact '"
                        + repoPath + "' due to its include/exclude pattern settings.");
        remoteRepoConfig.getBaseConfig().setExcludesPattern("");
    }

    /**
     * Verify reading of valid checksum file to test the {@link ChecksumUtils#checksumStringFromStream(InputStream)}
     * method
     */
    public void testReadValidChecksum() throws Exception {
        invokeReadChecksum("valid", "dasfasdf4r234234q32asdfadfasasdfasdf");
    }

    /**
     * Verify reading of a checksum file containing comments to test the
     * {@link ChecksumUtils#checksumStringFromStream(InputStream)}
     * method
     */
    public void testReadCommentChecksum() throws Exception {
        invokeReadChecksum("comment", "asdfaeaef435345435asdf");
    }

    /**
     * Verify reading of a checksum file containing file description to test the {@link
     * ChecksumUtils#checksumStringFromStream(InputStream)} method
     */
    public void testReadDescChecksum() throws Exception {
        invokeReadChecksum("desc", "dasfasdf4r234234q32asdfadfasdf");
    }

    /**
     * Verify reading of an empty checksum file to test the {@link ChecksumUtils#checksumStringFromStream(InputStream)}
     * method
     */
    public void testReadEmptyChecksum() throws Exception {
        invokeReadChecksum("empty", "");
    }

    public void returnCachedResourceResourceIsFound() {
        FileResource cachedResource = mock(FileResource.class);
        when(cachedResource.isFound()).thenReturn(true);
        assertThat(httpRepo.shouldReturnCachedResource(requestContext, cachedResource)).isTrue();
    }

    public void returnCachedResourceWhenPropMismatch() {
        UnfoundRepoResource cachedResource = mock(UnfoundRepoResource.class);
        when(cachedResource.getReason()).thenReturn(Reason.PROPERTY_MISMATCH);
        assertThat(httpRepo.shouldReturnCachedResource(requestContext, cachedResource)).isTrue();
    }

    public void returnCachedResourceWhenRemoteReplicationOriginated() {
        RepoResource cachedResource = mock(RepoResource.class);
        when(requestContext.getRequest()).thenReturn(request);
        when(request.getParameter(PARAM_REPLICATION_ORIGINATED_DOWNLOAD_REQUEST)).thenReturn("true");
        assertThat(httpRepo.shouldReturnCachedResource(requestContext, cachedResource)).isTrue();

        when(request.getParameter(PARAM_REPLICATION_ORIGINATED_DOWNLOAD_REQUEST)).thenReturn(null);
        assertThat(httpRepo.shouldReturnCachedResource(requestContext, cachedResource)).isFalse();
    }

    @Test(dataProvider = "providePathsContainsURL")
    public void testAppendAndGetUrlWithURLInPath(String pathWithURL, String expectedIfAllowed) throws IOException {
        try {
            when(layoutsCoreAddon.translateArtifactPath(any(), any(), anyString())).thenReturn(pathWithURL);
            httpRepo.downloadResource(pathWithURL, requestContext);
            assertThat(true).isFalse();
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).contains(pathWithURL);
        }

        assertThat(httpRepo.appendAndGetUrl(pathWithURL, requestContext))
                .isEqualTo(expectedIfAllowed);

    }

    @Test
    public void testBuildRequestMatrixParamsWithNullProperties() {
        String result = httpRepo.buildRequestMatrixParams(null);
        Assert.assertEquals(result, "");
    }

    @Test
    public void testBuildRequestMatrixParamsWithEmptyProperties() {
        Properties properties = new PropertiesImpl();
        String result = httpRepo.buildRequestMatrixParams(properties);
        Assert.assertEquals(result, "");
    }

    @Test
    public void testBuildRequestMatrixParamsWithSingleProperty() {
        Properties properties = new PropertiesImpl();
        properties.put("key1", "value1");
        String result = httpRepo.buildRequestMatrixParams(properties);
        Assert.assertEquals(result, ";key1=value1");
    }

    @Test
    public void testBuildRequestMatrixParamsWithMultipleProperties() {
        Properties properties = new PropertiesImpl();
        properties.put("key1", "value1");
        properties.put("key2", "value2");
        String result = httpRepo.buildRequestMatrixParams(properties);
        Assert.assertEquals(result, ";key1=value1;key2=value2");
    }

    @Test(dataProvider = "provideValidPaths")
    public void testAppendAndGetUrlValidPath(String validPath) {
        assertThat(httpRepo.appendAndGetUrl(validPath, requestContext)).isEqualTo("http://someUrl/" + validPath);
    }

    @DataProvider
    private Object[][] provideValidPaths() {
        return new Object[][] {
                {"some/path/to/append/"},
                {"some/path/to/append/:properties"},
        };
    }

    @DataProvider
    private Object[][] providePathsContainsURL() {
        return new Object[][] {
                // RTDEV-97315: a url in the artifact path is appended to the configured origin rather than
                // replacing it, because this context carries no alternative download url.
                {"http://localhost:8080/some/path/to/append/",
                        "http://someUrl/http://localhost:8080/some/path/to/append/"},
                {"http://localhost:8080/some/path/to/append/:properties", "http://someUrl/http://localhost:8080/some" +
                        "/path/to/append/:properties"},
        };
    }

    /**
     * RTDEV-97315. An absolute url reaching {@code appendAndGetUrl} from anywhere other than the
     * alternative-download-url channel is appended to the configured origin, never used as the destination.
     * <p>
     * Parameterized over the spellings that decide the outcome, not over one: the literal and the
     * percent-encoded port name the same host and port to the http client, yet the predicate this fix
     * replaced answered differently for each - the encoded one was reported as an unparseable path and so
     * admitted. Testing only the literal form reproduces exactly the blind spot that produced this finding.
     * The plain off-origin row is here because no encoding trick is needed once provenance is ignored.
     */
    // R-IB-1.2: both branches of the new provenance guard. R-LOG-02: identifiers carrying '/' and percent escapes.
    @Test(dataProvider = "provideAbsoluteUrlsInArtifactPath")
    public void appendAndGetUrlAppendsAbsoluteUrlWhenNotFromAlternativeDownloadChannel(String absoluteUrlInPath) {
        when(requestContext.getRequest()).thenReturn(request);

        assertThat(httpRepo.appendAndGetUrl(absoluteUrlInPath, requestContext))
                .isEqualTo("http://someUrl/" + absoluteUrlInPath);
    }

    @DataProvider
    private Object[][] provideAbsoluteUrlsInArtifactPath() {
        return new Object[][] {
                // Rejected before this fix too - the literal spelling parses as a url.
                {"http://u@127.0.0.1:8888/hello.tgz"},
                // The reported bypass: '%38888' is what '%2538888' decodes to after the request path is
                // decoded once, and URI#getAuthority decodes it once more into port 8888.
                {"http://u@127.0.0.1:%38888/hello.tgz"},
                {"https://cf-debug.example.invalid:%3443/artifactory-ssrf/001"},
                // Percent-encoded host, the RTDEV-95692 family.
                {"http://%31%32%37%2E%30%2E%30%2E%31/x"},
                // No encoding at all: provenance is what decides, so this needs no trick.
                {"https://evil.example/x.tgz"},
        };
    }

    /**
     * The counterpart of the rule above: a download url resolved by a package handler is the one input that
     * may name a destination of its own, and it keeps doing so. PyPI serves from {@code files.pythonhosted.org}
     * while the repository is configured at {@code pypi.org}, so appending here would break every such fetch.
     */
    @Test
    public void appendAndGetUrlUsesAbsoluteUrlVerbatimWhenSuppliedByAlternativeDownloadChannel() {
        String handlerSuppliedUrl = "https://files.pythonhosted.org/packages/ab/cd/foo-1.0.tar.gz";
        InternalArtifactoryRequest internalRequest = mock(InternalArtifactoryRequest.class);
        when(internalRequest.getParameter(ArtifactoryHeaders.PARAM_ALTERNATIVE_REMOTE_DOWNLOAD_URL))
                .thenReturn(handlerSuppliedUrl);
        when(requestContext.getRequest()).thenReturn(internalRequest);

        assertThat(httpRepo.appendAndGetUrl(handlerSuppliedUrl, requestContext)).isEqualTo(handlerSuppliedUrl);
    }

    /**
     * Both halves of the guard are required, and this covers the half a type check alone would miss: the
     * parameter is present, but on a client request, which may not rewrite it. {@code RepoFilter} already
     * rejects that parameter at the edge for repository requests; this asserts the sink does not rely on it.
     */
    // R-IB-1.2: the isAllowedQueryParamRewrite half of the guard.
    @Test
    public void appendAndGetUrlAppendsAbsoluteUrlWhenParameterCarriedByClientRequest() {
        String absoluteUrl = "https://evil.example/x.tgz";
        HttpArtifactoryRequest clientRequest = mock(HttpArtifactoryRequest.class);
        when(clientRequest.getParameter(ArtifactoryHeaders.PARAM_ALTERNATIVE_REMOTE_DOWNLOAD_URL))
                .thenReturn(absoluteUrl);
        when(requestContext.getRequest()).thenReturn(clientRequest);

        assertThat(httpRepo.appendAndGetUrl(absoluteUrl, requestContext))
                .isEqualTo("http://someUrl/" + absoluteUrl);
    }

    /**
     * A handler that resolves a <em>relative</em> download path - which is what Docker, Cargo, Puppet and npm
     * pass - is appended to the configured origin exactly as before, so carrying the flag does not change the
     * majority of the alternative-download traffic.
     */
    @Test
    public void appendAndGetUrlAppendsRelativeAlternativeDownloadPath() {
        String relativePath = "v2/library/nginx/manifests/latest";
        InternalArtifactoryRequest internalRequest = mock(InternalArtifactoryRequest.class);
        when(internalRequest.getParameter(ArtifactoryHeaders.PARAM_ALTERNATIVE_REMOTE_DOWNLOAD_URL))
                .thenReturn(relativePath);
        when(requestContext.getRequest()).thenReturn(internalRequest);

        assertThat(httpRepo.appendAndGetUrl(relativePath, requestContext))
                .isEqualTo("http://someUrl/" + relativePath);
    }

    /**
     * The flag is an emergency switch, so it has to restore the previous behaviour rather than merely stop
     * logging: with it off, an absolute artifact path is used verbatim again. Set through
     * {@code ArtifactoryHome} properties, which is the path
     * {@link org.artifactory.common.property.ArtifactoryPropertyResolver} reads after SysConfig
     * (the dynamic-properties overlay).
     */
    // R-LOG-16: assert the flag's effect on behaviour, not that it parses.
    @Test
    public void appendAndGetUrlRestoresLegacyBehaviourWhenFlagDisabled() {
        String absoluteUrlInPath = "http://u@127.0.0.1:%38888/hello.tgz";
        setStringSystemProperty(ConstantValues.remoteRepoBlockAbsoluteArtifactPath, "false");
        try {
            when(requestContext.getRequest()).thenReturn(request);

            assertThat(httpRepo.appendAndGetUrl(absoluteUrlInPath, requestContext)).isEqualTo(absoluteUrlInPath);
        } finally {
            setStringSystemProperty(ConstantValues.remoteRepoBlockAbsoluteArtifactPath,
                    ConstantValues.remoteRepoBlockAbsoluteArtifactPath.getDefValue());
        }
    }

    /**
     * A context with no request at all - the shape internal callers pass - grants nothing. Failing closed is
     * the safe direction here: a value whose origin cannot be established is treated as an artifact path.
     */
    // R-IB-1.3: null input on the guard's own parameter.
    @Test
    public void appendAndGetUrlAppendsAbsoluteUrlWhenContextCarriesNoRequest() {
        String absoluteUrlInPath = "https://evil.example/x.tgz";
        when(requestContext.getRequest()).thenReturn(null);

        assertThat(httpRepo.appendAndGetUrl(absoluteUrlInPath, requestContext))
                .isEqualTo("http://someUrl/" + absoluteUrlInPath);
    }

    /**
     * The finding was reported against several package types, so the guard is asserted against each rather
     * than against the generic repository alone.
     * <p>
     * They share one sink: a remote of any package type is an {@link HttpRepo} carrying that type in its
     * config ({@code RepositoryServiceImpl.createRemoteRepo} falls back to the generic factory for every type
     * without a dedicated factory), and the handful of subclasses that do exist - Docker, NuGet, Terraform,
     * Ansible, Swift, NimModel - inherit {@code appendAndGetUrl} untouched. PyPI is the one subclass that
     * overrides it, and {@code PypiHttpRepoTest} covers both of its branches. This test is what fails if a
     * future package type reaches the sink by some other route.
     */
    // R-LOG-04: package-type matrix over the shared remote-download sink.
    @Test(dataProvider = "providePackageTypes")
    public void appendAndGetUrlAppendsAbsoluteUrlForEveryPackageType(RepoType packageType) {
        String absoluteUrlInPath = "http://u@127.0.0.1:%38888/hello.tgz";
        HttpRepo packageTypeRepo = remoteRepoOfType(packageType);
        when(requestContext.getRequest()).thenReturn(request);

        assertThat(packageTypeRepo.appendAndGetUrl(absoluteUrlInPath, requestContext))
                .describedAs("absolute artifact path must not replace the configured origin for " + packageType)
                .isEqualTo("http://someUrl/" + absoluteUrlInPath);
    }

    /**
     * The same matrix for the channel that is allowed to name its own destination, so the guard is shown to
     * be package-type agnostic in both directions - a fix that silently broke npm or NuGet upstream fetches
     * would pass the test above.
     */
    // R-LOG-04: same matrix, opposite branch.
    @Test(dataProvider = "providePackageTypes")
    public void appendAndGetUrlKeepsAlternativeDownloadUrlForEveryPackageType(RepoType packageType) {
        String handlerSuppliedUrl = "https://cdn.example.com/packages/ab/cd/artifact.bin";
        HttpRepo packageTypeRepo = remoteRepoOfType(packageType);
        InternalArtifactoryRequest internalRequest = mock(InternalArtifactoryRequest.class);
        when(internalRequest.getParameter(ArtifactoryHeaders.PARAM_ALTERNATIVE_REMOTE_DOWNLOAD_URL))
                .thenReturn(handlerSuppliedUrl);
        when(requestContext.getRequest()).thenReturn(internalRequest);

        assertThat(packageTypeRepo.appendAndGetUrl(handlerSuppliedUrl, requestContext))
                .describedAs("handler-resolved download url must still be used verbatim for " + packageType)
                .isEqualTo(handlerSuppliedUrl);
    }

    private HttpRepo remoteRepoOfType(RepoType packageType) {
        RemoteRepoConfig typedConfig = RemoteRepoConfig.create("remoteRepoKey", packageType);
        typedConfig.getBaseConfig().setRepoLayoutRef(RepoLayoutUtils.MAVEN_2_DEFAULT.getName());
        typedConfig.getRepoTypeConfig().setUrl("http://someUrl");
        return new HttpRepo(httpRepoDescriptor, typedConfig, internalRepoService, addonsManager,
                researchService, null, null, configService);
    }

    @DataProvider
    private Object[][] providePackageTypes() {
        // The types Ron's report names explicitly, plus the generic repository the PoC was demonstrated on.
        return new Object[][] {
                {RepoType.Generic},
                {RepoType.Npm},
                {RepoType.Pypi},
                {RepoType.NuGet},
                {RepoType.Docker},
                {RepoType.Maven},
                {RepoType.Gems},
                {RepoType.Debian},
                {RepoType.Go},
                {RepoType.Helm},
        };
    }

    public void testAlternativeRemoteSiteUrl() {
        Request request = mock(UIBuildArtifactoryRequest.class);
        assertAlternateUrl(request, "http://alternativeSite/");

        request = mock(InternalArtifactoryRequest.class);
        assertAlternateUrl(request, "http://alternativeSite/");

        request = mock(HttpArtifactoryRequest.class);
        assertAlternateUrl(request, "http://someUrl/");
    }

    private void assertAlternateUrl(Request request, String expected) {
        when(requestContext.getRequest()).thenReturn(request);
        when(request.getRepoPath()).thenReturn(mock(RepoPath.class));
        when(request.getParameter(PARAM_ALTERNATIVE_REMOTE_SITE_URL)).thenReturn("http://alternativeSite");
        assertThat(httpRepo.appendAndGetUrl("", requestContext)).isEqualTo(expected);
    }

    @Test(dataProvider = "booleans")
    void testAppendAndGetUrlWithAlternativeUrl(boolean blocked) {
        RepoPath path = RepoPathFactory.create(httpRepo.getKey(), "test.jar");
        InternalArtifactoryRequest artifactoryRequest = mock(InternalArtifactoryRequest.class);
        when(artifactoryRequest.getParameter(PARAM_ALTERNATIVE_REMOTE_SITE_URL)).thenReturn(
                "https://alternative-url.com");
        when(artifactoryRequest.getRepoPath()).thenReturn(path);
        InternalRequestContext internalRequestContext = mock(InternalRequestContext.class);
        when(internalRequestContext.getRequest()).thenReturn(artifactoryRequest);
        when(urlVerifier.isRemoteRepoBlocked("https://alternative-url.com", path.getRepoKey()))
                .thenReturn(blocked);

        String result = httpRepo.appendAndGetUrl(path.toPath(), internalRequestContext);

        String expectedUrl = blocked
                ? httpRepo.getUrl() + "/" + path.toPath()
                : "https://alternative-url.com/" + path.toPath();

        assertEquals(expectedUrl, result);
    }

    public void testAlternativeRemoteDownloadUrl() {
        Request request = mock(UIBuildArtifactoryRequest.class);
        assertAlternateDownloadUrl(request, "http://alternativeSite/");

        request = mock(InternalArtifactoryRequest.class);
        assertAlternateDownloadUrl(request, "http://alternativeSite/");

        request = mock(HttpArtifactoryRequest.class);
        assertAlternateDownloadUrl(request, "");
    }

    private void assertAlternateDownloadUrl(Request request, String expected) {
        when(request.getParameter(PARAM_ALTERNATIVE_REMOTE_DOWNLOAD_URL)).thenReturn("http://alternativeSite/");
        assertThat(httpRepo.resolvePathForUrl("", request)).isEqualTo(expected);
    }

    public void testCreateRemoteResource() {
        boolean isSavedResource = ConstantValues.saveGetResource.getBoolean();
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.saveGetResource.getPropertyName(), Boolean.toString(true));
        long timeMillis = DateUtils.parseDate(timeStr, new String[] {"E, d MMM yyyy HH:mm:ss Z"}).getTime();
        CurationPackageStatusPayload
                payload = new CurationPackageStatusPayload("packageType", "packageUrl",
                "repoKey", "projectKey", "user", "email", "originRepoKey",
                "originProjectKey", "originServer", "downloadUrl",
                ArrayListMultimap.create(), false, "waiverRequest", false, null, null, null);
        RemoteRepoResource repoResource =
                httpRepo.createRemoteResourceFromResponse(repoPath, response, checksumsInfo.getChecksums(), payload);
        assertTrue(checksumsInfo.isIdentical(repoResource.getInfo().getChecksumsInfo()));
        assertEquals(timeMillis, repoResource.getLastModified());
        assertEquals(4, repoResource.getSize());
        assertEquals(repoPath, repoResource.getRepoPath());
        assertEquals("etag-1", repoResource.getEtag());
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.saveGetResource.getPropertyName(), Boolean.toString(isSavedResource));
    }

    public void testReconstructResourceFromGetResponseIfNeeded() {
        Set<ChecksumInfo> remoteChecksums = Sets.newHashSet();
        boolean isSavedResource = ConstantValues.saveGetResource.getBoolean();
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.saveGetResource.getPropertyName(), Boolean.toString(true));

        RemoteRepoResource repoResource = new RemoteRepoResource(repoPath, 123, "etag", 50, remoteChecksums, null);
        long timeMillis = DateUtils.parseDate(timeStr, new String[] {"E, d MMM yyyy HH:mm:ss Z"}).getTime();

        httpRepo.reconstructResourceFromGetResponseIfNeeded(repoResource, trafficAwareRemoteResourceStreamHandle,
                requestContext.getRequest().getRepoPath());

        assertTrue(checksumsInfo.isIdentical(repoResource.getInfo().getChecksumsInfo()));
        assertEquals(timeMillis, repoResource.getLastModified());
        assertEquals(4, repoResource.getSize());
        assertEquals(repoPath, repoResource.getRepoPath());
        Properties properties = new PropertiesImpl();
        properties.put(DbStoringRepoMixin.ETAG_PROP_KEY, repoResource.getEtag());
        properties = httpRepo.updateEtagInProperties(repoResource, properties);
        assertEquals("etag-1", properties.getFirst("artifactory.internal.etag"));
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.saveGetResource.getPropertyName(), Boolean.toString(isSavedResource));

    }

    public void testReconstructResourceFromGetResponseNotNeeded() {
        Set<ChecksumInfo> remoteChecksums = Sets.newHashSet();
        boolean isSavedResource = ConstantValues.saveGetResource.getBoolean();
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.saveGetResource.getPropertyName(), Boolean.toString(false));
        RemoteRepoResource repoResource = new RemoteRepoResource(repoPath, 123, "etag", 50, remoteChecksums, null);

        httpRepo.reconstructResourceFromGetResponseIfNeeded(repoResource, trafficAwareRemoteResourceStreamHandle,
                requestContext.getRequest().getRepoPath());

        assertEquals(remoteChecksums, repoResource.getChecksums());
        assertEquals(123, repoResource.getLastModified());
        assertEquals(50, repoResource.getSize());
        assertEquals(repoPath, repoResource.getRepoPath());
        Properties properties = new PropertiesImpl();
        properties.put(DbStoringRepoMixin.ETAG_PROP_KEY, repoResource.getEtag());
        properties = httpRepo.updateEtagInProperties(repoResource, properties);
        assertEquals("etag", properties.getFirst("artifactory.internal.etag"));
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.saveGetResource.getPropertyName(), Boolean.toString(isSavedResource));

    }

    @Test
    public void shouldExpireTrueWithRemoteAndExpiredResourcesTest() {
        prepInterceptorMocks(ExpiryAction.DONT_EXPIRE_CONTINUE_NEXT, ExpiryAction.DONT_EXPIRE_CONTINUE_NEXT);
        RemoteRepoResource remoteResource = new RemoteRepoResource(repoPath, 1234, "etag", 50, Sets.newHashSet(), null);
        ExpiredRepoResource cachedResource = new ExpiredRepoResource(
                new FileResource(new FileInfoImpl(repoPath) {{setLastModified(123);}}));
        httpRepo.init(false);

        assertTrue(httpRepo.shouldExpire(cachedResource, remoteResource));
        verify(httpRepo, times(1)).shouldExpireWithLastModified(cachedResource, remoteResource);
    }

    @Test
    public void shouldExpireFalseWithRemoteAndExpiredResourcesButStoppedByInterceptorTest() {
        prepInterceptorMocks(ExpiryAction.DONT_EXPIRE_CONTINUE_NEXT, ExpiryAction.DONT_EXPIRE_STOP);
        RemoteRepoResource remoteResource = new RemoteRepoResource(repoPath, 1234, "etag", 50, Sets.newHashSet(), null);
        ExpiredRepoResource cachedResource = new ExpiredRepoResource(
                new FileResource(new FileInfoImpl(repoPath) {{setLastModified(123);}}));
        httpRepo.init(false);

        assertFalse(httpRepo.shouldExpire(cachedResource, remoteResource));
        verify(httpRepo, times(0)).shouldExpireWithLastModified(cachedResource, remoteResource);
    }

    @Test
    public void shouldExpireFalseWithRemoteAndExpiredResourcesButExpiredByInterceptorTest() {
        prepInterceptorMocks(ExpiryAction.DONT_EXPIRE_CONTINUE_NEXT, ExpiryAction.EXPIRE);
        RemoteRepoResource remoteResource = new RemoteRepoResource(repoPath, 1234, "etag", 50, Sets.newHashSet(), null);
        ExpiredRepoResource cachedResource = new ExpiredRepoResource(
                new FileResource(new FileInfoImpl(repoPath) {{setLastModified(123);}}));
        httpRepo.init(false);

        assertTrue(httpRepo.shouldExpire(cachedResource, remoteResource));
        verify(httpRepo, times(0)).shouldExpireWithLastModified(cachedResource, remoteResource);
    }

    @Test
    public void shouldExpireTrueWithRemoteAndExpiredResourcesTestWithDontExpireStop() {
        HashMap<String, RemoteRepoInterceptor> interceptorsMap =
                prepInterceptorMocks(ExpiryAction.DONT_EXPIRE_STOP, ExpiryAction.DONT_EXPIRE_CONTINUE_NEXT);
        RemoteRepoResource remoteResource = new RemoteRepoResource(repoPath, 1234, "etag", 50, Sets.newHashSet(), null);
        ExpiredRepoResource cachedResource = new ExpiredRepoResource(
                new FileResource(new FileInfoImpl(repoPath) {{setLastModified(123);}}));
        httpRepo.init(false);

        assertFalse(httpRepo.shouldExpire(cachedResource, remoteResource));
        verify(interceptorsMap.get("1"), times(1)).shouldExpireCachedResource(cachedResource, remoteResource);
        verify(interceptorsMap.get("2"), times(0)).shouldExpireCachedResource(cachedResource, remoteResource);
        verify(httpRepo, times(0)).shouldExpireWithLastModified(cachedResource, remoteResource);
    }

    @Test
    public void shouldExpireTrueWithRemoteAndExpiredResourcesTestWithExpire() {
        HashMap<String, RemoteRepoInterceptor> interceptorsMap =
                prepInterceptorMocks(ExpiryAction.EXPIRE, ExpiryAction.DONT_EXPIRE_CONTINUE_NEXT);
        RemoteRepoResource remoteResource = new RemoteRepoResource(repoPath, 1234, "etag", 50, Sets.newHashSet(), null);
        ExpiredRepoResource cachedResource = new ExpiredRepoResource(
                new FileResource(new FileInfoImpl(repoPath) {{setLastModified(123);}}));
        httpRepo.init(false);

        assertTrue(httpRepo.shouldExpire(cachedResource, remoteResource));
        verify(interceptorsMap.get("1"), times(1)).shouldExpireCachedResource(cachedResource, remoteResource);
        verify(interceptorsMap.get("2"), times(0)).shouldExpireCachedResource(cachedResource, remoteResource);
        verify(httpRepo, times(0)).shouldExpireWithLastModified(cachedResource, remoteResource);
    }

    @Test
    public void shouldExpireFalseWithRemoteAndExpiredResourcesSameLastModifiedTest() {
        HashMap<String, RemoteRepoInterceptor> interceptorsMap =
                prepInterceptorMocks(ExpiryAction.DONT_EXPIRE_CONTINUE_NEXT, ExpiryAction.DONT_EXPIRE_CONTINUE_NEXT);
        RemoteRepoResource remoteResource = new RemoteRepoResource(repoPath, 1234, "etag", 50, Sets.newHashSet(), null);
        ExpiredRepoResource cachedResource = new ExpiredRepoResource(
                new FileResource(new FileInfoImpl(repoPath) {{setLastModified(1234);}}));
        httpRepo.init(false);

        assertFalse(httpRepo.shouldExpire(cachedResource, remoteResource));
        verify(interceptorsMap.get("1"), times(1)).shouldExpireCachedResource(cachedResource, remoteResource);
        verify(interceptorsMap.get("2"), times(1)).shouldExpireCachedResource(cachedResource, remoteResource);
        verify(httpRepo, times(1)).shouldExpireWithLastModified(cachedResource, remoteResource);
    }

    @Test
    public void shouldExpireTrueTwoInterceptorsReturnExpireTest() {
        HashMap<String, RemoteRepoInterceptor> interceptorsMap =
                prepInterceptorMocks(ExpiryAction.EXPIRE, ExpiryAction.EXPIRE);
        RemoteRepoResource remoteResource = new RemoteRepoResource(repoPath, 1234, "etag", 50, Sets.newHashSet(), null);
        ExpiredRepoResource cachedResource = new ExpiredRepoResource(
                new FileResource(new FileInfoImpl(repoPath) {{setLastModified(1234);}}));
        httpRepo.init(false);

        assertTrue(httpRepo.shouldExpire(cachedResource, remoteResource));
        verify(interceptorsMap.get("1"), times(1)).shouldExpireCachedResource(cachedResource, remoteResource);
        verify(interceptorsMap.get("2"), times(0)).shouldExpireCachedResource(cachedResource, remoteResource);
        verify(httpRepo, times(0)).shouldExpireWithLastModified(cachedResource, remoteResource);
    }

    @Test
    public void shouldNotCreateOrGetFolderIfExistsTest() {
        httpRepoDescriptor.setKey("remote-repo");
        DbCacheRepo localCacheRepo = new DbCacheRepo(httpRepo, null, configService);

        DbStoringRepoMixin mixinSpy = spy(new DbStoringRepoMixin(httpRepoDescriptor, null, null));
        Mockito.doReturn(null).when(mixinSpy).createOrGetFolder(any());

        ReflectionTestUtils.setField(localCacheRepo, "mixin", mixinSpy);

        localCacheRepo.init(true);

        verify(fileService, times(0)).exists(any());
        verify(mixinSpy, times(0)).createOrGetFolder(any());
    }

    @Test
    public void shouldCreateOrGetFolderIfNotExistsTest() {
        httpRepoDescriptor.setKey("remote-repo");
        DbCacheRepo localCacheRepo = new DbCacheRepo(httpRepo, null, configService);

        DbStoringRepoMixin mixinSpy = spy(new DbStoringRepoMixin(httpRepoDescriptor, null, null));
        Mockito.doReturn(null).when(mixinSpy).createOrGetFolder(any());

        ReflectionTestUtils.setField(localCacheRepo, "mixin", mixinSpy);

        localCacheRepo.init(false);

        verify(fileService, times(1)).exists(any());
        verify(mixinSpy, times(0)).createOrGetFolder(any());
    }

    @Test
    public void shouldCallFileServiceExistsIfUnknownTest() {
        httpRepoDescriptor.setKey("remote-repo");
        DbCacheRepo localCacheRepo = new DbCacheRepo(httpRepo, null, configService);

        DbStoringRepoMixin mixinSpy = spy(new DbStoringRepoMixin(httpRepoDescriptor, null, null));
        Mockito.doReturn(null).when(mixinSpy).createOrGetFolder(any());

        ReflectionTestUtils.setField(localCacheRepo, "mixin", mixinSpy);
        localCacheRepo.init(false);

        verify(fileService, times(1)).exists(any());
        verify(mixinSpy, times(0)).createOrGetFolder(any());

        when(fileService.exists(any())).thenReturn(false);
        localCacheRepo.init(false);

        verify(fileService, times(2)).exists(any());
        verify(mixinSpy, times(1)).createOrGetFolder(any());
    }

    @Test
    public void testAcquireLock() throws InterruptedException {
        ConflictsGuard conflictGuard = mock(ConflictsGuard.class);
        when(httpRepo.getRemoteDownloaderLockingMap()).thenReturn(conflictGuard);
        doAnswer(invocationOnMock -> true).when(conflictGuard).tryToLock("path/to/lock", 2, TimeUnit.SECONDS);
        assertTrue(httpRepo.acquireLock("path/to/lock", 2));

        doThrow(new InterruptedException()).when(conflictGuard).tryToLock("path/to/lock", 2, TimeUnit.SECONDS);
        assertFalse(httpRepo.acquireLock("path/to/lock", 2));
    }

    @Test
    public void testDownloadAndSave() throws IOException, RepoRejectException {
        InternalRequestContext requestContext = mock(InternalRequestContext.class);
        RepoResource repoResource = mock(RepoResource.class);
        RepoPath repoPath = RepoPathFactory.create("repo", "path");
        RepoResourceInfo repoResourceInfo = new FileInfoImpl(repoPath);
        when(repoResource.getInfo()).thenReturn(repoResourceInfo);
        when(repoResource.getRepoPath()).thenReturn(repoPath);
        when(repoResource.isFound()).thenReturn(true);
        LocalCacheRepo localCacheRepo = mock(LocalCacheRepo.class);
        ReflectionTestUtils.setField(httpRepo, "localCacheRepo", localCacheRepo);
        ExpiredRepoResource expiredRepoResource = new ExpiredRepoResource(repoResource);
        when(localCacheRepo.getInfo(requestContext)).thenReturn(expiredRepoResource);
        ResourceStreamHandle streamHandle = mock(ResourceStreamHandle.class);
        when(localCacheRepo.getResourceStreamHandle(requestContext, expiredRepoResource)).thenReturn(streamHandle);
        Collection<RemoteRepoInterceptor> remoteRepoInterceptors = new ArrayList<>();
        ReflectionTestUtils.setField(httpRepo, "remoteRepoInterceptors", remoteRepoInterceptors);
        doReturn(true).when(httpRepo).shouldExpire(expiredRepoResource, repoResource);
        InternalRepositoryService repositoryService = mock(InternalRepositoryService.class);
        doReturn(repositoryService).when(httpRepo).getRepositoryService();
        doReturn(false).when(httpRepo).acquireLock("repo/path", 60);
        ResourceStreamHandle resourceStreamHandle = httpRepo.downloadAndSave(requestContext, repoResource);
        verify(repoResource, times(1)).isFound();
        assertEquals(resourceStreamHandle, streamHandle);
        verify(localCacheRepo, times(1)).getResourceStreamHandle(requestContext, expiredRepoResource);
    }

    @Test(expectedExceptionsMessageRegExp = ".*Not found", expectedExceptions = FileNotFoundException.class)
    public void testDownloadAndSaveWithException() throws IOException, RepoRejectException {
        InternalRequestContext requestContext = mock(InternalRequestContext.class);
        RepoResource repoResource = mock(RepoResource.class);
        RepoPath repoPath = RepoPathFactory.create("repo", "path");
        RepoResourceInfo repoResourceInfo = new FileInfoImpl(repoPath);
        when(repoResource.getInfo()).thenReturn(repoResourceInfo);
        when(repoResource.getRepoPath()).thenReturn(repoPath);
        when(repoResource.isFound()).thenReturn(true);
        LocalCacheRepo localCacheRepo = mock(LocalCacheRepo.class);
        ReflectionTestUtils.setField(httpRepo, "localCacheRepo", localCacheRepo);
        ExpiredRepoResource expiredRepoResource = new ExpiredRepoResource(repoResource);
        when(localCacheRepo.getInfo(requestContext)).thenReturn(expiredRepoResource);
        ResourceStreamHandle streamHandle = mock(ResourceStreamHandle.class);
        doThrow(new FileNotFoundException("Not found")).when(localCacheRepo)
                .getResourceStreamHandle(requestContext, expiredRepoResource);
        when(localCacheRepo.getResourceStreamHandle(requestContext, expiredRepoResource)).thenReturn(streamHandle);
        Collection<RemoteRepoInterceptor> remoteRepoInterceptors = new ArrayList<>();
        ReflectionTestUtils.setField(httpRepo, "remoteRepoInterceptors", remoteRepoInterceptors);
        doReturn(true).when(httpRepo).shouldExpire(expiredRepoResource, repoResource);
        InternalRepositoryService repositoryService = mock(InternalRepositoryService.class);
        doReturn(repositoryService).when(httpRepo).getRepositoryService();
        doReturn(false).when(httpRepo).acquireLock("repo/path", 60);
        ResourceStreamHandle resourceStreamHandle = httpRepo.downloadAndSave(requestContext, repoResource);
        verify(repoResource, times(1)).isFound();
        assertEquals(resourceStreamHandle, streamHandle);
        verify(localCacheRepo, times(1)).getResourceStreamHandle(requestContext, expiredRepoResource);
    }

    @Test
    public void testDownloadAndSaveNoFallback() throws IOException, RepoRejectException {
        InternalRequestContext requestContext = mock(InternalRequestContext.class);
        RepoResource repoResource = mock(RepoResource.class);
        RepoPath repoPath = RepoPathFactory.create("repo", "path");
        RepoResourceInfo repoResourceInfo = new FileInfoImpl(repoPath);
        when(repoResource.getInfo()).thenReturn(repoResourceInfo);
        when(repoResource.getRepoPath()).thenReturn(repoPath);
        when(repoResource.isFound()).thenReturn(false);
        LocalCacheRepo localCacheRepo = mock(LocalCacheRepo.class);
        ReflectionTestUtils.setField(httpRepo, "localCacheRepo", localCacheRepo);
        ExpiredRepoResource expiredRepoResource = new ExpiredRepoResource(repoResource);
        when(localCacheRepo.getInfo(requestContext)).thenReturn(expiredRepoResource);
        Collection<RemoteRepoInterceptor> remoteRepoInterceptors = new ArrayList<>();
        ReflectionTestUtils.setField(httpRepo, "remoteRepoInterceptors", remoteRepoInterceptors);
        doReturn(true).when(httpRepo).shouldExpire(expiredRepoResource, repoResource);
        InternalRepositoryService repositoryService = mock(InternalRepositoryService.class);
        doReturn(repositoryService).when(httpRepo).getRepositoryService();
        doReturn(false).when(httpRepo).acquireLock("repo/path", 60 * 15);
        ResourceStreamHandle resourceStreamHandle = httpRepo.downloadAndSave(requestContext, repoResource);
        verify(repoResource, times(1)).isFound();
        assertNull(resourceStreamHandle);
        verify(localCacheRepo, times(0)).getResourceStreamHandle(requestContext, expiredRepoResource);
    }

    @Test
    public void testGetRemoteResourceWorkerStopException() {
        // Mock the necessary objects
        RepoPath path = mock(RepoPath.class);

        // Set up the mock behavior
        when(path.getPath()).thenReturn("path");
        when(path.isFolder()).thenReturn(false);
        doThrow(new WorkerStopException(Action.ALT_REMOTE_PATH, "Failed"))
                .when(httpRepo).retrieveInfo(anyString(), eq(false), any());
        // Mock
        WorkersAddon workersAddon = mock(WorkersAddon.class);
        when(requestContext.getProperties()).thenReturn(mock(Properties.class));
        when(requestContext.getProperties().hasMandatoryProperty()).thenReturn(false);
        ReflectionTestUtils.setField(httpRepo, "addonsManager", addonsManager);
        doReturn(workersAddon).when(addonsManager).addonByType(WorkersAddon.class);

        // Call the method and verify the exception is handled
        assertThrows(WorkerStopException.class, () -> httpRepo.getRemoteResource(requestContext, path, false));
    }

    @Test
    public void testGetRemoteProperties() throws IOException {
        String relPath = "some/relative/path";
        ResourceStreamHandle resourceStreamHandle = mock(ResourceStreamHandle.class);
        // Mock the downloadResource method to throw an exception
        doThrow(new WorkerStopException(Action.ALT_REMOTE_PATH, "Download failed"))
                .when(httpRepo).downloadResource(anyString());
        assertThrows(WorkerStopException.class, () -> httpRepo.getRemoteProperties(relPath));
        // Mock the downloadResource method to return a resourceStreamHandle
        doReturn(resourceStreamHandle).when(httpRepo).downloadResource(anyString());
        doThrow(new WorkerStopException(Action.ALT_REMOTE_PATH, "InputStream failed"))
                .when(resourceStreamHandle).getInputStream();
        assertThrows(WorkerStopException.class, () -> httpRepo.getRemoteProperties(relPath));
    }

    @Test
    public void testGetHandleFromCacheNotFound() throws IOException, RepoRejectException {
        InternalRequestContext requestContext = mock(InternalRequestContext.class);
        RepoResource remoteResource = mock(FileResource.class);
        RepoResource cachedResource = mock(RepoResource.class);
        String message = "message";
        LocalCacheRepo localCacheRepo = mock(LocalCacheRepo.class);
        ReflectionTestUtils.setField(httpRepo, "localCacheRepo", localCacheRepo);
        assertNull(httpRepo.getHandleFromCache(requestContext, remoteResource, cachedResource, message));
    }

    @Test
    public void testGetHandleFromCacheWithChecksumFromResource() throws IOException, RepoRejectException {
        InternalRequestContext requestContext = mock(InternalRequestContext.class);
        RepoResource remoteResource = mock(RepoResource.class);
        RepoResourceInfo repoResourceInfo = mock(RepoResourceInfo.class);
        ChecksumsInfo remoteChecksumsInfo = new ChecksumsInfo();
        when(repoResourceInfo.getChecksumsInfo()).thenReturn(remoteChecksumsInfo);
        when(remoteResource.getInfo()).thenReturn(repoResourceInfo);
        ExpiredRepoResource expiredRepoResource = new ExpiredRepoResource(remoteResource);
        RepoResource cachedResource = mock(RepoResource.class);
        String message = "message";
        LocalCacheRepo localCacheRepo = mock(LocalCacheRepo.class);
        ReflectionTestUtils.setField(httpRepo, "localCacheRepo", localCacheRepo);
        ChecksumsInfo checksumsInfo = new ChecksumsInfo();
        ChecksumInfo sha1 = new ChecksumInfo(ChecksumType.sha1, "dda2437c5d609ff934bc83d4f5af72c764aaa680",
                "dda2437c5d609ff934bc83d4f5af72c764aaa680");
        checksumsInfo.setChecksums(Set.of(sha1));
        ResourceWithChecksumStreamHandle resourceStreamHandle = new TestResourceWithChecksum(checksumsInfo);
        when(localCacheRepo.getResourceStreamHandle(requestContext, cachedResource)).thenReturn(resourceStreamHandle);
        httpRepo.getHandleFromCache(requestContext, expiredRepoResource, cachedResource, message);
        assertEquals(remoteChecksumsInfo.getSha1(), sha1.getActual());
        verify(localCacheRepo, times(0)).getInfo(requestContext);
    }

    @Test
    public void testGetHandleFromCacheWithoutChecksumFromResource() throws IOException, RepoRejectException {
        InternalRequestContext requestContext = mock(InternalRequestContext.class);
        RepoResource remoteResource = mock(RepoResource.class);
        RepoResourceInfo repoResourceInfo = mock(RepoResourceInfo.class);
        ChecksumsInfo remoteChecksumsInfo = new ChecksumsInfo();
        when(repoResourceInfo.getChecksumsInfo()).thenReturn(remoteChecksumsInfo);
        ChecksumInfo sha1 = new ChecksumInfo(ChecksumType.sha1, "dda2437c5d609ff934bc83d4f5af72c764aaa680",
                "dda2437c5d609ff934bc83d4f5af72c764aaa680");
        remoteChecksumsInfo.setChecksums(Set.of(sha1));

        when(remoteResource.getInfo()).thenReturn(repoResourceInfo);
        ExpiredRepoResource expiredRepoResource = new ExpiredRepoResource(remoteResource);
        RepoResource cachedResource = mock(RepoResource.class);
        String message = "message";
        LocalCacheRepo localCacheRepo = mock(LocalCacheRepo.class);
        ReflectionTestUtils.setField(httpRepo, "localCacheRepo", localCacheRepo);

        ResourceStreamHandle resourceStreamHandle = mock(ResourceStreamHandle.class);
        when(localCacheRepo.getInfo(requestContext)).thenReturn(expiredRepoResource);
        when(localCacheRepo.getResourceStreamHandle(requestContext, cachedResource)).thenReturn(resourceStreamHandle);
        httpRepo.getHandleFromCache(requestContext, expiredRepoResource, cachedResource, message);
        assertEquals(remoteChecksumsInfo.getSha1(), sha1.getActual());
    }

    @Test
    public void testGetInfoPermissionsCheckNotCalledIfMavenAndNotFoundAndConstEnabled() {
        InternalRequestContext requestContext = mock(InternalRequestContext.class);
        RepoResource res = mock(RepoResource.class);
        Request request = mock(Request.class);
        HttpRepoDescriptor descriptor = mock(HttpRepoDescriptor.class);
        HttpConnectionPoolMonitorService connectionPool = new HttpConnectionPoolMonitorService();

        httpRepo.init(false);

        // perquisites
        when(requestContext.getResourcePath()).thenReturn("colors");
        when(requestContext.getRequest()).thenReturn(request);
        when(context.beanForType(HttpConnectionPoolMonitorService.class)).thenReturn(connectionPool);
        when(restCoreAddon.getDynamicVersionContext(httpRepo, requestContext, true)).thenReturn(requestContext);
        when(httpRepo.getDescriptor()).thenReturn(descriptor);
        doReturn(res).when(httpRepo).internalGetInfo(any(), any());
        doReturn("repoKey").when(httpRepo).getKey();
        doReturn(res).when(httpRepo).getInfoFromCache(requestContext);

        // test essence
        when(descriptor.getType()).thenReturn(RepoType.Maven);
        when(res.isFound()).thenReturn(false);
        ArtifactorySystemProperties artifactoryProperties = ArtifactoryHome.get().getArtifactoryProperties();
        artifactoryProperties.setProperty(ConstantValues.enableMavenNonPreemptiveAuthentication.getPropertyName(),
                "true");

        httpRepo.getInfo(requestContext);
        verify(httpRepo, times(0)).checkDownloadIsAllowed(any());
    }

    @Test
    public void testGetInfoPermissionsCheckCalledIfMavenAndFoundAndConstEnabled() {
        InternalRequestContext requestContext = mock(InternalRequestContext.class);
        RepoResource res = mock(RepoResource.class);
        Request request = mock(Request.class);
        HttpRepoDescriptor descriptor = mock(HttpRepoDescriptor.class);
        RepoPath repoPath = mock(RepoPath.class);
        HttpConnectionPoolMonitorService connectionPool = new HttpConnectionPoolMonitorService();

        httpRepo.init(false);

        // perquisites
        when(requestContext.getResourcePath()).thenReturn("colors");
        when(requestContext.getRequest()).thenReturn(request);
        when(context.beanForType(HttpConnectionPoolMonitorService.class)).thenReturn(connectionPool);
        when(restCoreAddon.getDynamicVersionContext(httpRepo, requestContext, true)).thenReturn(requestContext);
        when(httpRepo.getDescriptor()).thenReturn(descriptor);
        when(res.getRepoPath()).thenReturn(repoPath);
        when(repoPath.getPath()).thenReturn("colors");
        doReturn(res).when(httpRepo).internalGetInfo(any(), any());
        doReturn("repoKey").when(httpRepo).getKey();
        doReturn(res).when(httpRepo).getInfoFromCache(requestContext);

        // test essence
        when(descriptor.getType()).thenReturn(RepoType.Maven);
        when(res.isFound()).thenReturn(true);
        ArtifactorySystemProperties artifactoryProperties = ArtifactoryHome.get().getArtifactoryProperties();
        artifactoryProperties.setProperty(ConstantValues.enableMavenNonPreemptiveAuthentication.getPropertyName(),
                "true");

        httpRepo.getInfo(requestContext);
        verify(httpRepo, times(1)).checkDownloadIsAllowed(any());
    }

    @Test
    public void testGetInfoPermissionsCheckCalledIfNotMaven() {
        InternalRequestContext requestContext = mock(InternalRequestContext.class);
        RepoResource res = mock(RepoResource.class);
        Request request = mock(Request.class);
        HttpRepoDescriptor descriptor = mock(HttpRepoDescriptor.class);
        HttpConnectionPoolMonitorService connectionPool = new HttpConnectionPoolMonitorService();

        httpRepo.init(false);

        // perquisites
        when(requestContext.getResourcePath()).thenReturn("colors");
        when(requestContext.getRequest()).thenReturn(request);
        when(context.beanForType(HttpConnectionPoolMonitorService.class)).thenReturn(connectionPool);
        when(restCoreAddon.getDynamicVersionContext(httpRepo, requestContext, true)).thenReturn(requestContext);
        when(httpRepo.getDescriptor()).thenReturn(descriptor);
        doReturn(res).when(httpRepo).internalGetInfo(any(), any());
        doReturn("repoKey").when(httpRepo).getKey();
        doReturn(res).when(httpRepo).getInfoFromCache(requestContext);

        // test essence
        when(descriptor.getType()).thenReturn(RepoType.Docker);

        httpRepo.getInfo(requestContext);
        verify(httpRepo, times(1)).checkDownloadIsAllowed(any());
    }

    @Test
    public void testGetInfoPermissionsCheckCalledIfMavenButConstDisabled() {
        InternalRequestContext requestContext = mock(InternalRequestContext.class);
        RepoResource res = mock(RepoResource.class);
        Request request = mock(Request.class);
        HttpRepoDescriptor descriptor = mock(HttpRepoDescriptor.class);
        RepoPath repoPath = mock(RepoPath.class);
        HttpConnectionPoolMonitorService connectionPool = new HttpConnectionPoolMonitorService();

        httpRepo.init(false);

        // perquisites
        when(requestContext.getResourcePath()).thenReturn("colors");
        when(requestContext.getRequest()).thenReturn(request);
        when(context.beanForType(HttpConnectionPoolMonitorService.class)).thenReturn(connectionPool);
        when(restCoreAddon.getDynamicVersionContext(httpRepo, requestContext, true)).thenReturn(requestContext);
        when(httpRepo.getDescriptor()).thenReturn(descriptor);
        when(res.getRepoPath()).thenReturn(repoPath);
        when(repoPath.getPath()).thenReturn("colors");
        doReturn(res).when(httpRepo).internalGetInfo(any(), any());
        doReturn("repoKey").when(httpRepo).getKey();
        doReturn(res).when(httpRepo).getInfoFromCache(requestContext);

        // test essence
        when(descriptor.getType()).thenReturn(RepoType.Maven);
        ArtifactorySystemProperties artifactoryProperties = ArtifactoryHome.get().getArtifactoryProperties();
        artifactoryProperties.setProperty(ConstantValues.enableMavenNonPreemptiveAuthentication.getPropertyName(),
                "false");

        httpRepo.getInfo(requestContext);
        verify(httpRepo, times(1)).checkDownloadIsAllowed(any());
    }

    @DataProvider(name = "requestClasses")
    public static Object[][] requestClasses() {
        return new Object[][] {
                {Request.class, false},
                {InternalArtifactoryRequest.class, true},
                {TranslatedArtifactoryRequest.class, true},
        };
    }

    @Test(dataProvider = "requestClasses")
    public void testAllowedQueryParamRewrite(Class<Request> requestClass, boolean queryParamRewriteAllowed) {
        assertEquals(httpRepo
                .isAllowedQueryParamRewrite(mock(requestClass)), queryParamRewriteAllowed);
    }

    @Test(dataProvider = "urls")
    public void testListRemoteResources(String url, List<RemoteItem> expectedItems) throws IOException {
        Cache<String, List<RemoteItem>> remoteResourceCache = Caffeine.newBuilder().build();
        ReflectionTestUtils.setField(httpRepo, "remoteResourceCache", remoteResourceCache);
        Cache<String, RepoResource> missedRetrievalsCache = Caffeine.newBuilder().build();
        ReflectionTestUtils.setField(httpRepo, "missedRetrievalsCache", missedRetrievalsCache);
        RemoteRepositoryBrowser remoteBrowser = mock(RemoteRepositoryBrowser.class);
        ReflectionTestUtils.setField(httpRepo, "remoteBrowser", remoteBrowser);
        when(remoteBrowser.listContent(any())).thenReturn(List.of(new RemoteItem("cool-url", false)));
        List<RemoteItem> remoteItems = httpRepo.listRemoteResources(url);
        assertEquals(expectedItems, remoteItems);
    }

    @Test
    public void testUnExpireCachedResourceIfNeeded() {
        InternalArtifactoryRequest artifactoryRequest = mock(InternalArtifactoryRequest.class);
        RemoteRepoResource remoteResource = new RemoteRepoResource(repoPath, 1234, "etag", 50, Sets.newHashSet(), null);
        RequestContext requestContext = new AlteredDownloadRequestContext(artifactoryRequest, false,
                response -> request.getRepoPath().getPath(), resource -> true);
        httpRepo.unExpireCachedResourceIfNeeded(remoteResource, requestContext);
        verify(internalRepoService, times(1)).unexpireIfExists((LocalRepo) any(), any());
    }

    @DataProvider(name = "urls")
    private Object[][] urls() {
        return new Object[][] {
                {"http://www.cool.com/", List.of()},
                {"http://www.cool.com", List.of()},
                {"www.cool.com/", List.of(new RemoteItem("cool-url", false))},
                {"www.cool.com", List.of(new RemoteItem("cool-url", false))}
        };
    }

    @Test
    public void testCurationNoCacheHeadRequest() {
        RemoteRepoInterceptor remoteRepoInterceptor = mock(RemoteRepoInterceptor.class);
        when(remoteRepoInterceptor.isRemoteDownloadAllowed(any(), any())).thenReturn(false);
        ReflectionTestUtils.setField(httpRepo, "remoteRepoInterceptors", List.of(remoteRepoInterceptor));
        ReflectionTestUtils.setField(httpRepo, "localCacheRepo", null);
        when(request.isHeadOnly()).thenReturn(true);
        RepoResource repoResource = httpRepo.internalGetInfo(nonCachedRepoPath, requestContext);
        assertTrue(repoResource instanceof UnfoundRepoResource);
        verify(curationAddon, times(0)).getPackageStatus(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    public void testCheckBlockedByCurationSkippedForHeadOnlyNoCache() {
        ReflectionTestUtils.setField(httpRepo, "localCacheRepo", null);
        when(request.isHeadOnly()).thenReturn(true);

        CurationCachedPackageStatus result =
                httpRepo.checkBlockedByCuration(request, nonCachedRepoPath, null, null, true);

        assertTrue(result.status().permitted());
        assertEquals(SecurityDecision.CURATION_NO_LOCAL_CACHE_HEAD_ONLY, result.status().securityDecision());
        verify(curationAddon, times(0)).getPackageStatus(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    public void internalGetInfoBlockedByCurationTest() {
        ReflectionTestUtils.setField(httpRepo, "remoteRepoInterceptors", List.of());
        ReflectionTestUtils.setField(httpRepo, "localCacheRepo", null);
        when(curationAddon.getPackageStatus(any(), any(), any(), any(), anyBoolean())).thenReturn(
                new CurationCachedPackageStatus(null, BLOCKED_STATUS));
        RepoResource repoResource = httpRepo.internalGetInfo(nonCachedRepoPath, requestContext);
        assertTrue(repoResource instanceof BlockedRepoResource);
        assertEquals(BlockedRepoResource.BlockedType.CURATION, ((BlockedRepoResource) repoResource).getBlockedType());
        verify(curationAddon, times(1)).getPackageStatus(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    public void testChecksumIsNull() throws IOException {
        LocalCacheRepo localCacheRepo = mock(LocalCacheRepo.class);
        ReflectionTestUtils.setField(httpRepo, "localCacheRepo", localCacheRepo);
        RemoteRepoResource repoResource =
                httpRepo.createRemoteResourceFromResponse(repoPath, response, checksumsInfo.getChecksums(), null);
        String repo = httpRepo.getChecksum("repo", repoResource);
        assertNull(repo);
    }

    @Test(dataProvider = "booleans")
    public void testChecksumWhenRetrieveSha256FromServer(boolean retrieveSha256FromServer) throws IOException {
        String sha256 = "ThisIsSha256Checksum";
        LocalCacheRepo localCacheRepo = mock(LocalCacheRepo.class);
        ReflectionTestUtils.setField(httpRepo, "localCacheRepo", localCacheRepo);
        RemoteRepoResource repoResource =
                httpRepo.createRemoteResourceFromResponse(repoPath, response, checksumsInfo.getChecksums(), null);
        httpRepo.getRepoConfig().getRepoTypeConfig().setRetrieveSha256FromServer(retrieveSha256FromServer);

        ResourceStreamHandle handle = mock(ResourceStreamHandle.class);
        StringInputStream is = new StringInputStream(sha256);
        when(handle.getInputStream()).thenReturn(is);
        doReturn(handle).when(httpRepo).downloadResource("repo");

        String checksum = httpRepo.getChecksum("repo", repoResource);
        if (retrieveSha256FromServer) {
            assertEquals(checksum, sha256);
        } else {
            assertNull(checksum);
        }
    }

    @Test(dataProvider = "remoteRepoTypes")
    public void testChecksumWhenArtifactsNotStoredLocally(RepoType repoType) throws IOException {
        RemoteRepoResource repoResource =
                httpRepo.createRemoteResourceFromResponse(repoPath, response, checksumsInfo.getChecksums(), null);
        httpRepo.getRepoConfig().getRepoTypeConfig().setStoreArtifactsLocally(false);

        RemoteRepoDescriptor remoteRepoDescriptor = mock(RemoteRepoDescriptor.class);
        when(remoteRepoDescriptor.getType()).thenReturn(repoType);
        when(internalRepoService.remoteRepoDescriptorByKey(any())).thenReturn(remoteRepoDescriptor);

        ResourceStreamHandle handle = mock(ResourceStreamHandle.class);
        StringInputStream is = new StringInputStream("sha256");
        when(handle.getInputStream()).thenReturn(is);
        doReturn(handle).when(httpRepo).downloadResource("repo");

        String checksum = httpRepo.getChecksum("repo", repoResource);
        if (repoType == RepoType.Maven) {
            assertEquals(checksum, "sha256");
        } else {
            assertNull(checksum);
        }
    }

    @Test(dataProvider = "booleans")
    public void testChecksumIsNullForNotFoundRemoteRequestException(boolean storeArtifactsLocally) throws IOException {
        LocalCacheRepo localCacheRepo = mock(LocalCacheRepo.class);
        ReflectionTestUtils.setField(httpRepo, "localCacheRepo", localCacheRepo);
        RemoteRepoResource repoResource =
                httpRepo.createRemoteResourceFromResponse(repoPath, response, checksumsInfo.getChecksums(), null);
        httpRepo.getRepoConfig().getRepoTypeConfig().setStoreArtifactsLocally(storeArtifactsLocally);
        httpRepo.getRepoConfig().getRepoTypeConfig().setRetrieveSha256FromServer(true);

        RemoteRepoDescriptor remoteRepoDescriptor = mock(RemoteRepoDescriptor.class);
        when(remoteRepoDescriptor.getType()).thenReturn(RepoType.Maven);
        when(internalRepoService.remoteRepoDescriptorByKey(any())).thenReturn(remoteRepoDescriptor);

        RemoteRequestException remoteRequestException = new RemoteRequestException("", HttpStatus.SC_NOT_FOUND, "");
        doThrow(remoteRequestException).when(httpRepo).downloadResource("repo");

        String checksum = httpRepo.getChecksum("repo", repoResource);
        assertNull(checksum);
    }

    @DataProvider(name = "booleans")
    public static Object[][] booleans() {
        return new Object[][] {
                {false}, {true}
        };
    }

    @DataProvider(name = "remoteRepoTypes")
    public static Object[][] remoteRepoTypes() {
        return new Object[][] {
                {RepoType.Generic},
                {RepoType.Maven},
        };
    }

    @Test(dataProvider = "testIsPassThroughEnabledData")
    public void testIsPassThroughEnabled(boolean storeArtifactsLocally, boolean performPassThrough, int invocations)
            throws Exception {
        RepoPath repoPath = RepoPathFactory.create("cool-repo", "cool-path");
        RepoResource repoResource = mock(RepoResource.class);
        when(repoResource.getResponseRepoPath()).thenReturn(repoPath);
        when(repoResource.getRepoPath()).thenReturn(repoPath);
        when(repoResource.getInfo()).thenReturn(mock(RepoResourceInfo.class));
        HttpRepo httpRepo = mock(HttpRepo.class);
        RemoteRepoConfig remoteRepoConfig = mock(RemoteRepoConfig.class);
        when(httpRepo.getRepoConfig()).thenReturn(remoteRepoConfig);
        RemoteRepositoryRepoTypeConfig repoTypeConfig = mock(RemoteRepositoryRepoTypeConfig.class);
        when(remoteRepoConfig.getRepoTypeConfig()).thenReturn(repoTypeConfig);
        when(repoTypeConfig.isStoreArtifactsLocally()).thenReturn(storeArtifactsLocally);
        LocalCacheRepo localCacheRepo = mock(LocalCacheRepo.class);
        when(localCacheRepo.getKey()).thenReturn(repoPath.getRepoKey());
        ReflectionTestUtils.setField(httpRepo, "localCacheRepo", localCacheRepo);
        when(httpRepo.downloadResource(anyString(), any(RequestContext.class)))
                .thenReturn(mock(ResourceStreamHandle.class));
        when(httpRepo.getResourceStreamHandle(any(), any())).thenCallRealMethod();
        when(httpRepo.isStoreArtifactsLocally()).thenCallRealMethod();
        when(httpRepo.getRepositoryService()).thenReturn(internalRepoService);
        when(curationAddon.shouldPerformPassThrough(any(), any())).thenReturn(performPassThrough);
        when(restCoreAddon.getDynamicVersionContext(any(), any(), anyBoolean()))
                .thenReturn(requestContext);
        httpRepo.getResourceStreamHandle(requestContext, repoResource);
        verify(internalRepoService, times(invocations))
                .downloadAndSave(any(), any(RemoteRepo.class), any(RepoResource.class));
    }

    @DataProvider(name = "testIsPassThroughEnabledData")
    private static Object[][] testIsPassThroughEnabledData() {
        return new Object[][] {
                {true, true, 0},
                {true, false, 1},
                {false, true, 0},
                {false, false, 0}
        };
    }

    @Test
    public void getAltContentTest() {
        RepoPath repoPath = RepoPathFactory.create("cool-repo", "cool-path");
        httpRepo.getAltContent(repoPath);
        verify(workersAddon, times(1)).run(eq(SupportedUnifiedEvents.ALT_REMOTE_CONTENT),
                any(ResourceStreamCtx.class),
                eq(repoPath));

    }

    @Test
    public void saveExistingInStorageResourceShouldNotBeUploadBackToStorageTest()
            throws RepoRejectException, IOException {
        String repoKey = "repo-key";
        RepoPath repoPath = RepoPathFactory.create(repoKey);
        InternalRepositoryService repoService = spy(RepositoryServiceImpl.class);
        HttpRepo httpRepo =
                spy(new HttpRepo(null, remoteRepoConfig, repoService, addonsManager, researchService,
                        null, null, configService));
        RepoResource remoteResource = mock(RepoResource.class);
        RepoResourceInfo resourceInfo = mock(RepoResourceInfo.class);
        ChecksumsInfo checksumInfo = new ChecksumsInfo();
        checksumInfo.addChecksumInfo(
                new ChecksumInfo(ChecksumType.sha1, sha1, sha1));
        LocalCacheRepo localCacheRepo = mock(LocalCacheRepo.class);
        ReflectionTestUtils.setField(httpRepo, "localCacheRepo", localCacheRepo);
        homeStub.setProperty(ConstantValues.lockResourceBeforeOpenTransaction, "false");

        when(localCacheRepo.getKey()).thenReturn(repoKey);
        when(remoteResource.getRepoPath()).thenReturn(repoPath);
        when(remoteResource.getInfo()).thenReturn(resourceInfo);
        when(resourceInfo.getChecksumsInfo()).thenReturn(checksumInfo);
        when(context.beanForType(InternalRepositoryService.class)).thenReturn(repoService);
        doReturn(true).when(httpRepo).hasChecksumDeployPermissionOnRepoPath(any());
        when(configService.getDescriptor()).thenReturn(mock(CentralConfigDescriptor.class));

        MockInputStream inputStream = mock(MockInputStream.class);
        when(binaryService.getBinaryForChecksumDeploy(any(), anyBoolean())).thenReturn(inputStream);
        when(inputStream.getBinaryInfo()).thenReturn(mock(BinaryElement.class));
        httpRepo.doDownloadAndSave(requestContext, remoteResource);

        verify(binaryService, times(1)).getBinaryForChecksumDeploy(any(), anyBoolean());
        verify(repoService, times(1)).saveResource(any(), any());
        verify(inputStream, times(1)).getBinaryInfo();
        verify(binaryService, never()).addBinary(any(), any());

        // Here we check the binary is not uploaded back to storage during the RepoServiceImpl.saveResource process
        verify(binaryService, never()).addBinary(any(), any());
    }

    abstract static class MockInputStream extends BufferedInputStream implements BinaryServiceInputStream {
        public MockInputStream() {
            super(new ByteArrayInputStream(new byte[] {}));
        }
    }

    @Test
    public void hasChecksumDeployPermissionOnRepoPathTrueTest() throws InvalidChecksumException, IOException {
        ChecksumInfo sha1ChecksumInfo = new ChecksumInfo(ChecksumType.sha1, sha1, null);
        Set<ChecksumInfo> checksum = Set.of(sha1ChecksumInfo);
        InternalRepositoryService repoService = mock(RepositoryServiceImpl.class);
        HttpRepo httpRepo =
                new HttpRepo(null, remoteRepoConfig, repoService, addonsManager, researchService,
                        null, null, configService);
        when(checksumDeployPermissionValidator.hasReadPermission(sha1ChecksumInfo, null)).thenReturn(
                Boolean.TRUE);
        assertTrue(httpRepo.hasChecksumDeployPermissionOnRepoPath(checksum));
    }

    @Test
    public void hasChecksumDeployPermissionOnRepoPathFalseTest() throws InvalidChecksumException, IOException {
        ChecksumInfo sha1ChecksumInfo = new ChecksumInfo(ChecksumType.sha1, sha1, null);
        Set<ChecksumInfo> checksum = Set.of(sha1ChecksumInfo);
        InternalRepositoryService repoService = mock(RepositoryServiceImpl.class);
        HttpRepo httpRepo =
                new HttpRepo(null, remoteRepoConfig, repoService, addonsManager, researchService,
                        null, null, configService);
        when(checksumDeployPermissionValidator.hasReadPermission(sha1ChecksumInfo, null)).thenReturn(
                Boolean.FALSE);
        assertFalse(httpRepo.hasChecksumDeployPermissionOnRepoPath(checksum));
    }

    @Test
    public void hasChecksumDeployPermissionOnRepoPathWhenInvalidChecksumTest()
            throws InvalidChecksumException, IOException {
        ChecksumInfo sha1ChecksumInfo = new ChecksumInfo(ChecksumType.sha1, sha1, null);
        Set<ChecksumInfo> checksum = Set.of(sha1ChecksumInfo);
        InternalRepositoryService repoService = mock(RepositoryServiceImpl.class);
        HttpRepo httpRepo =
                new HttpRepo(null, remoteRepoConfig, repoService, addonsManager, researchService,
                        null, null, configService);
        when(checksumDeployPermissionValidator.hasReadPermission(sha1ChecksumInfo, null)).thenThrow(
                InvalidChecksumException.class);
        assertFalse(httpRepo.hasChecksumDeployPermissionOnRepoPath(checksum));
    }

    @Test
    void testGetExistingResourceByChecksum() {
        ChecksumInfo sha1ChecksumInfo = new ChecksumInfo(ChecksumType.sha1, sha1, null);
        InternalRepositoryService repoService = mock(RepositoryServiceImpl.class);
        HttpRepo httpRepo =
                new HttpRepo(null, remoteRepoConfig, repoService, addonsManager, researchService,
                        null, null, configService);
        long size = 1024L;
        InputStream mockStream = new ByteArrayInputStream(new byte[] {1, 2, 3});
        BinaryElement mockElement = mock(BinaryElement.class);

        when(binaryService.createBinaryElement(eq(sha1), any(), any(), eq(-1L), anyMap()))
                .thenReturn(mockElement);
        when(binaryService.getBinaryForChecksumDeploy(eq(mockElement), eq(false))).thenReturn(mockStream);

        ResourceStreamHandle result = httpRepo.getExistingResourceByChecksum(Set.of(sha1ChecksumInfo), size);

        assertNotNull(result);
        assertEquals(size, result.getSize());
        verify(binaryService, times(1)).getBinaryForChecksumDeploy(mockElement, false);
    }

    @Test
    void testGetExistingResourceByChecksumBinaryNotFound() {
        ChecksumInfo sha1ChecksumInfo = new ChecksumInfo(ChecksumType.sha1, sha1, null);
        InternalRepositoryService repoService = mock(RepositoryServiceImpl.class);
        HttpRepo httpRepo =
                new HttpRepo(null, remoteRepoConfig, repoService, addonsManager, researchService,
                        null, null, configService);
        long size = 1024L;
        BinaryElement mockElement = mock(BinaryElement.class);

        when(binaryService.createBinaryElement(eq(sha1), any(), any(), eq(-1L), anyMap()))
                .thenReturn(mockElement);
        when(binaryService.getBinaryForChecksumDeploy(eq(mockElement), eq(false)))
                .thenThrow(BinaryNotFoundException.class);

        ResourceStreamHandle result = httpRepo.getExistingResourceByChecksum(Set.of(sha1ChecksumInfo), size);

        Assertions.assertNull(result);
        verify(binaryService, times(1)).getBinaryForChecksumDeploy(mockElement, false);
    }

    @Test
    void testGetExistingResourceByChecksumStorageException() {
        ChecksumInfo sha1ChecksumInfo = new ChecksumInfo(ChecksumType.sha1, sha1, null);
        InternalRepositoryService repoService = mock(RepositoryServiceImpl.class);
        HttpRepo httpRepo =
                new HttpRepo(null, remoteRepoConfig, repoService, addonsManager, researchService,
                        null, null, configService);
        long size = 1024L;
        BinaryElement mockElement = mock(BinaryElement.class);

        when(binaryService.createBinaryElement(eq(sha1), any(), any(), eq(-1L), anyMap()))
                .thenReturn(mockElement);
        when(binaryService.getBinaryForChecksumDeploy(eq(mockElement), eq(false)))
                .thenThrow(StorageException.class);

        assertThrows(StorageException.class,
                () -> httpRepo.getExistingResourceByChecksum(Set.of(sha1ChecksumInfo), size));
        verify(binaryService, times(1)).getBinaryForChecksumDeploy(mockElement, false);
    }

    @Test
    public void whenBlockDownloadDisabled() throws RepoRejectException {
        doReturn(Boolean.FALSE).when(httpRepo).isBlockDownloadEnabled();

        httpRepo.curationOnDemand(createRemoteRepoResource(""));

        verify(curationAddon, times(0)).getCachedPackageStatus(any(), any(), any(), any());
    }

    @Test
    public void whenContentTypeInHeaders() throws RepoRejectException {
        doReturn(Boolean.TRUE).when(httpRepo).isBlockDownloadEnabled();
        doReturn(PERMITTED_STATUS).when(curationAddon).getCachedPackageStatus(any(), any(), any(), any());
        String contentType = "application/vnd.docker.distribution.manifest.list.v2+json";

        httpRepo.curationOnDemand(createRemoteRepoResource(contentType));

        verify(curationAddon, times(1)).getCachedPackageStatus(
                any(),
                eq(RepoPathFactory.create("repo", "path")),
                any(),
                eq(contentType)
        );
    }

    @Test(expectedExceptions = RepoRejectException.class)
    public void whenCurationBlocks() throws RepoRejectException {
        doReturn(Boolean.TRUE).when(httpRepo).isBlockDownloadEnabled();
        doReturn(BLOCKED_STATUS).when(curationAddon).getCachedPackageStatus(any(), any(), any(), any());
        String contentType = "application/vnd.docker.distribution.manifest.list.v2+json";

        httpRepo.curationOnDemand(createRemoteRepoResource(contentType));
    }

    @Test
    public void whenNoContentTypeInHeaders() throws RepoRejectException {
        doReturn(Boolean.TRUE).when(httpRepo).isBlockDownloadEnabled();
        doReturn(PERMITTED_STATUS).when(curationAddon).getCachedPackageStatus(any(), any(), any(), any());
        String contentType = "application/json";

        RepoResource repoResource = mock(RepoResource.class);
        when(repoResource.getMimeType()).thenReturn(contentType);
        when(repoResource.getResponseRepoPath()).thenReturn(RepoPathFactory.create("repo", "path"));
        when(repoResource.getOriginRemotePath()).thenReturn("OriginRemotePath");

        httpRepo.curationOnDemand(repoResource);

        verify(curationAddon, times(1)).getCachedPackageStatus(
                any(),
                eq(RepoPathFactory.create("repo", "path")),
                eq("OriginRemotePath"),
                eq(contentType)
        );
    }

    @Test
    public void testGetCuratedRepoResourceWhenCurationCompliantVersionServiceIsNull()
            throws PackageBlockedByCurationException {
        // Setup
        InternalRequestContext requestContext = mock(InternalRequestContext.class);
        RepoResource repoResource = mock(RepoResource.class);

        // Ensure curationCompliantVersionService is null
        ReflectionTestUtils.setField(httpRepo, "curationCompliantVersionService", null);

        // Execute
        Optional<RepoResource> result = httpRepo.getCuratedRepoResource(requestContext, repoResource);

        // Verify
        assertFalse(result.isPresent());
        verify(curationCompliantVersionService, never()).canCallFilterBlockedVersions(any(), any());
        verify(curationCompliantVersionService, never()).getCuratedMetadataResource(any(), any(), any());
    }

    @Test
    public void testGetCuratedRepoResourceWhenCanCallFilterBlockedVersionsReturnsFalse()
            throws PackageBlockedByCurationException {
        // Setup
        InternalRequestContext requestContext = mock(InternalRequestContext.class);
        RepoResource repoResource = mock(RepoResource.class);

        ReflectionTestUtils.setField(httpRepo, "curationCompliantVersionService", curationCompliantVersionService);
        when(curationCompliantVersionService.canCallFilterBlockedVersions(repoResource, httpRepo)).thenReturn(false);

        // Execute
        Optional<RepoResource> result = httpRepo.getCuratedRepoResource(requestContext, repoResource);

        // Verify
        assertFalse(result.isPresent());
        verify(curationCompliantVersionService, times(1)).canCallFilterBlockedVersions(repoResource, httpRepo);
        verify(curationCompliantVersionService, never()).getCuratedMetadataResource(any(), any(), any());
    }

    @Test
    public void testGetCuratedRepoResourceWhenGetCuratedMetadataResourceThrowsException()
            throws PackageBlockedByCurationException {
        // Setup
        InternalRequestContext requestContext = mock(InternalRequestContext.class);
        RepoResource repoResource = mock(RepoResource.class);

        ReflectionTestUtils.setField(httpRepo, "curationCompliantVersionService", curationCompliantVersionService);
        when(curationCompliantVersionService.canCallFilterBlockedVersions(repoResource, httpRepo)).thenReturn(true);
        when(curationCompliantVersionService.getCuratedMetadataResource(requestContext, httpRepo, repoResource))
                .thenThrow(new RuntimeException("Service error"));

        // Execute
        Optional<RepoResource> result = httpRepo.getCuratedRepoResource(requestContext, repoResource);

        // Verify
        assertFalse(result.isPresent());
        verify(curationCompliantVersionService, times(1)).canCallFilterBlockedVersions(repoResource, httpRepo);
        verify(curationCompliantVersionService, times(1)).getCuratedMetadataResource(requestContext, httpRepo,
                repoResource);
    }

    @Test
    public void testGetCuratedRepoResourceWhenGetCuratedMetadataResourceReturnsNull()
            throws PackageBlockedByCurationException {
        // Setup
        InternalRequestContext requestContext = mock(InternalRequestContext.class);
        RepoResource repoResource = mock(RepoResource.class);

        ReflectionTestUtils.setField(httpRepo, "curationCompliantVersionService", curationCompliantVersionService);
        when(curationCompliantVersionService.canCallFilterBlockedVersions(repoResource, httpRepo)).thenReturn(true);
        when(curationCompliantVersionService.getCuratedMetadataResource(requestContext, httpRepo, repoResource))
                .thenReturn(null);

        // Execute
        Optional<RepoResource> result = httpRepo.getCuratedRepoResource(requestContext, repoResource);

        // Verify
        assertFalse(result.isPresent());
        verify(curationCompliantVersionService, times(1)).canCallFilterBlockedVersions(repoResource, httpRepo);
        verify(curationCompliantVersionService, times(1)).getCuratedMetadataResource(requestContext, httpRepo,
                repoResource);
    }

    @Test
    public void testGetCuratedRepoResourceWhenGetCuratedMetadataResourceReturnsResult()
            throws PackageBlockedByCurationException {
        // Setup
        InternalRequestContext requestContext = mock(InternalRequestContext.class);
        RepoResource repoResource = mock(RepoResource.class);
        RepoResource curatedResource = mock(RepoResource.class);

        ReflectionTestUtils.setField(httpRepo, "curationCompliantVersionService", curationCompliantVersionService);
        when(curationCompliantVersionService.canCallFilterBlockedVersions(repoResource, httpRepo)).thenReturn(true);
        when(curationCompliantVersionService.getCuratedMetadataResource(requestContext, httpRepo, repoResource))
                .thenReturn(curatedResource);

        // Execute
        Optional<RepoResource> result = httpRepo.getCuratedRepoResource(requestContext, repoResource);

        // Verify
        assertTrue(result.isPresent());
        assertEquals(curatedResource, result.get());
        verify(curationCompliantVersionService, times(1)).canCallFilterBlockedVersions(repoResource, httpRepo);
        verify(curationCompliantVersionService, times(1)).getCuratedMetadataResource(requestContext, httpRepo,
                repoResource);
    }

    @Test
    public void testGetCuratedRepoResourceWhenGetCuratedMetadataResourceThrowsBlockedByCuration() throws Exception {
        InternalRequestContext requestContext = mock(InternalRequestContext.class);
        RepoResource repoResource = mock(RepoResource.class);
        RepoPath repoPath = RepoPathFactory.create("remoteRepoKey", "maven-metadata.xml");

        ReflectionTestUtils.setField(httpRepo, "curationCompliantVersionService", curationCompliantVersionService);
        when(repoResource.getRepoPath()).thenReturn(repoPath);
        when(curationCompliantVersionService.canCallFilterBlockedVersions(repoResource, httpRepo)).thenReturn(true);
        when(curationCompliantVersionService.getCuratedMetadataResource(requestContext, httpRepo, repoResource))
                .thenThrow(new com.jfrog.ph.acl.exception.PackageBlockedByCurationException("blocked by policy"));

        Optional<RepoResource> result = httpRepo.getCuratedRepoResource(requestContext, repoResource);

        assertTrue(result.isPresent());
        assertTrue(result.get() instanceof BlockedRepoResource);
        BlockedRepoResource blocked = (BlockedRepoResource) result.get();
        assertEquals(BlockedRepoResource.BlockedType.CURATION, blocked.getBlockedType());
        assertEquals("blocked by policy", blocked.getMessage());
        assertEquals(repoPath, blocked.getRepoPath());
    }

    @DataProvider(name = "cacheControlCuratedIndexScenarios")
    private Object[][] cacheControlCuratedIndexScenarios() {
        return new Object[][] {
                {true},   // curated branch taken: the path targeted by the fix RTDEV-74973
                {false},  // original branch: pre-existing behavior, confirms fix aligns with it
        };
    }

    @Test(dataProvider = "cacheControlCuratedIndexScenarios")
    public void getInfo_shouldReturnExpirableResource_whenPathIsExpirable(boolean curatedBranchTaken)
            throws PackageBlockedByCurationException {
        // Arrange - common setup
        InternalRequestContext localContext = mock(InternalRequestContext.class);
        RepoResource originalResource = mock(RepoResource.class);
        Request localRequest = mock(Request.class);
        RepoPath originalRepoPath = RepoPathFactory.create("remoteRepoKey", "info/rails");

        httpRepo.init(false);
        when(localContext.getResourcePath()).thenReturn("info/rails");
        when(localContext.getRequest()).thenReturn(localRequest);
        when(localRequest.getRepoPath()).thenReturn(originalRepoPath);
        when(context.beanForType(HttpConnectionPoolMonitorService.class))
                .thenReturn(new HttpConnectionPoolMonitorService());
        when(restCoreAddon.getDynamicVersionContext(httpRepo, localContext, true)).thenReturn(localContext);
        doReturn(null).when(httpRepo).checkResourceStatusBeforeDownload(any(), any());
        doReturn(originalResource).when(httpRepo).internalGetInfo(any(), any());
        ReflectionTestUtils.setField(httpRepo, "curationCompliantVersionService", curationCompliantVersionService);

        if (curatedBranchTaken) {
            RepoPath curatedRepoPath = InternalRepoPathFactory.create("remoteRepoKey-cache",
                    ".jfrog/curated/info/rails");
            FileResource curatedResource = new FileResource(new FileInfoImpl(curatedRepoPath));
            CacheExpiry cacheExpiry = mock(CacheExpiry.class);
            when(internalRepoService.getRepositoryConfigByKey("remoteRepoKey-cache"))
                    .thenReturn(RemoteRepoConfig.create("remoteRepoKey-cache", RepoType.Gems));
            when(context.beanForType(CacheExpiry.class)).thenReturn(cacheExpiry);
            when(cacheExpiry.isExpirable(any(), any(), eq(".jfrog/curated/info/rails"))).thenReturn(true);
            when(curationCompliantVersionService.canCallFilterBlockedVersions(originalResource, httpRepo))
                    .thenReturn(true);
            when(curationCompliantVersionService.getCuratedMetadataResource(localContext, httpRepo, originalResource))
                    .thenReturn(curatedResource);
        } else {
            RepoPath nonCuratedRepoPath = InternalRepoPathFactory.create("remoteRepoKey-cache", "info/rails");
            FileResource nonCuratedResource = new FileResource(new FileInfoImpl(nonCuratedRepoPath));
            nonCuratedResource.expirable();
            when(curationCompliantVersionService.canCallFilterBlockedVersions(originalResource, httpRepo))
                    .thenReturn(false);
            doReturn(nonCuratedResource).when(httpRepo).checkResourceStatusAfterDownload(any(), any(), any());
        }

        // Act
        RepoResource result = httpRepo.getInfo(localContext);

        // Assert
        assertTrue(result.isExpirable());
    }

    private static RemoteRepoResource createRemoteRepoResource(String headerContentType) {
        RepoPath repoPath = RepoPathFactory.create("repo", "path");
        Header[] headers = new Header[] {new BasicHeader("Content-Type", headerContentType)};
        RemoteRepoResource remoteRepoResource = new RemoteRepoResource(repoPath, 0L, null, 0L, Set.of(), headers);
        return remoteRepoResource;
    }

    private HashMap<String, RemoteRepoInterceptor> prepInterceptorMocks(ExpiryAction expiryAction,
            ExpiryAction expiryAction2) {
        RemoteRepoInterceptor remoteRepoInterceptorMock = mock(RemoteRepoInterceptor.class);
        RemoteRepoInterceptor remoteRepoInterceptorMock2 = mock(RemoteRepoInterceptor.class);
        when(remoteRepoInterceptorMock.shouldExpireCachedResource(any(), any())).thenReturn(expiryAction);
        when(remoteRepoInterceptorMock2.shouldExpireCachedResource(any(), any())).thenReturn(expiryAction2);
        HashMap<String, RemoteRepoInterceptor> remoteInterceptors = new LinkedHashMap<>() {
            {
                put("1", remoteRepoInterceptorMock);
                put("2", remoteRepoInterceptorMock2);
            }
        };
        when(context.beansForType(RemoteRepoInterceptor.class))
                .thenReturn(remoteInterceptors);
        return remoteInterceptors;
    }

    /**
     * Check the different values that are read from the given checksum are as expected
     *
     * @param checksumType  Type of checksum test file to read
     * @param expectedValue Expected checksum value
     */
    private void invokeReadChecksum(String checksumType, String expectedValue) throws Exception {
        InputStream stream = getClass().getResourceAsStream("/org/artifactory/repo/test-" + checksumType + ".md5");
        try {
            String checksum = ChecksumUtils.checksumStringFromStream(stream);
            Assert.assertEquals(expectedValue, checksum, "Incorrect checksum value returned.");
        } finally {
            IOUtils.closeQuietly(stream);
        }
    }

    private void createCacheRepo() {
        ReflectionTestUtils.setField(remoteRepoConfig, "key", "remote-repo");
        DbCacheRepo localCacheRepo = new DbCacheRepo(httpRepo, null, configService);
        ReflectionTestUtils.setField(httpRepo, "localCacheRepo", localCacheRepo);

        DbStoringRepoMixin mixin = mock(DbStoringRepoMixin.class);
        ReflectionTestUtils.setField(localCacheRepo, "mixin", mixin);
        when(mixin.itemExists(any())).thenReturn(true);
        when(mixin.getKey()).thenReturn("remote-repo-cache");
    }

    private void expectCanRead(boolean canRead) {
        when(authService.canRead(repoPath)).thenReturn(canRead);
    }

    private void expectXrayBlocked() {
        when(xrayAddon.isDownloadBlocked(any(RepoPath.class))).thenReturn(false);
    }

    /**
     * Try to download the the given path
     *
     * @param path Repopath to test the download with
     * @return StatusHolder - Contains any errors which might be monitored
     */
    private StatusHolder tryAllowsDownload(RepoPath path) {
        return httpRepo.checkDownloadIsAllowed(path);
    }

    @DataProvider
    public static Object[][] testInternalGetInfoBlockedRemoteDownloadProvider() {
        return new Object[][] {
                {false}, {true}
        };
    }

    @Test
    public void removeApiFromUrlAndAppendBridgeUsesStorageApiOnlyForProperties() {
        remoteRepoConfig.getRepoTypeConfig()
                .setUrl("https://acme.example/bridge-tenant1/artifactory/libs-release");
        String listingUrl =
                (String) ReflectionTestUtils.invokeMethod(httpRepo,
                        "removeApiFromUrlAndAppend", "com/example/");
        Assert.assertEquals(listingUrl,
                "https://acme.example/bridge-tenant1/artifactory/libs-release/com/example/");

        String propsUrl = (String) ReflectionTestUtils.invokeMethod(httpRepo, "removeApiFromUrlAndAppend",
                "com/example/foo.jar:properties");
        Assert.assertEquals(propsUrl,
                "https://acme.example/bridge-tenant1/artifactory/api/storage/libs-release/com/example/foo.jar"
                        + "?propertiesXml");
    }

    @DataProvider(name = "bridgePropertiesStorageApiUrlProvider")
    public static Object[][] bridgePropertiesStorageApiUrlProvider() {
        return new Object[][] {
                // {baseUrl, repoKey, pathToAppend, expectedUrl}
                // encoded spaces → decoded so downstream encodeIfNeeded encodes once
                {"http://localhost:8046/bridge-tenant/artifactory", "local",
                        "Screenshot%202026-03-17%20at%2014.52.14.png:properties",
                        "http://localhost:8046/bridge-tenant/artifactory/api/storage/local/"
                                + "Screenshot 2026-03-17 at 14.52.14.png?propertiesXml"},
                // simple path without encoding — unchanged
                {"http://localhost:8046/bridge-tenant/artifactory", "libs-release",
                        "com/example/foo.jar:properties",
                        "http://localhost:8046/bridge-tenant/artifactory/api/storage/libs-release/"
                                + "com/example/foo.jar?propertiesXml"},
                // multiple encoded chars all decoded
                {"https://acme.example/bridge-prod/artifactory", "generic-local",
                        "path/with%20spaces/file%2Bname%28v2%29.tar.gz:properties",
                        "https://acme.example/bridge-prod/artifactory/api/storage/generic-local/"
                                + "path/with spaces/file+name(v2).tar.gz?propertiesXml"},
                // trailing slash on base URL
                {"http://localhost:8046/bridge-tenant/artifactory/", "local",
                        "artifact.jar:properties",
                        "http://localhost:8046/bridge-tenant/artifactory/api/storage/local/"
                                + "artifact.jar?propertiesXml"},
                // deep nested path
                {"http://localhost:8046/bridge-tenant/artifactory", "maven-remote",
                        "org/apache/commons/commons-lang3/3.12.0/commons-lang3-3.12.0.jar:properties",
                        "http://localhost:8046/bridge-tenant/artifactory/api/storage/maven-remote/"
                                + "org/apache/commons/commons-lang3/3.12.0/commons-lang3-3.12.0.jar?propertiesXml"},
                // non-bridge URL → null
                {"http://remote-host/artifactory", "libs-release",
                        "com/example/foo.jar:properties", null},
                // non-properties path → null
                {"http://localhost:8046/bridge-tenant/artifactory", "local",
                        "com/example/foo.jar", null},
        };
    }

    @Test(dataProvider = "bridgePropertiesStorageApiUrlProvider")
    public void buildBridgePropertiesStorageApiUrlParameterized(
            String baseUrl, String repoKey, String pathToAppend, String expectedUrl) {
        String url = httpRepo.buildBridgePropertiesStorageApiUrl(baseUrl, repoKey, pathToAppend);
        Assert.assertEquals(url, expectedUrl);
    }

    @Test
    public void setParallelDownloadAttributes_setsAllAttributes_whenRemoteResourceWithSha1AndRangeSupport() {
        ChecksumInfo sha1Info = new ChecksumInfo(ChecksumType.sha1, sha1, null);
        ChecksumsInfo checksums = new ChecksumsInfo();
        checksums.addChecksumInfo(sha1Info);
        RemoteRepoResource remoteRes = new RemoteRepoResource(repoPath, 0, null, 2000L,
                checksums.getChecksums(), new Header[]{new BasicHeader(ArtifactoryHeaders.ACCEPT_RANGES, "bytes")});

        httpRepo.setParallelDownloadAttributes(requestContext, remoteRes);

        verify(requestContext).setAttribute("fileSize", "2000");
        verify(requestContext).setAttribute(ParallelDownloadService.RCD_EXPECTED_SHA1, sha1);
        verify(requestContext).setAttribute(ParallelDownloadService.RCD_SUPPORTS_RANGE, "true");
    }

    @Test
    public void setParallelDownloadAttributes_setsOnlyFileSize_whenPlainResourceWithNoInfo() {
        RepoResource res = mock(RepoResource.class);
        when(res.getSize()).thenReturn(5000L);
        when(res.getInfo()).thenReturn(null);

        httpRepo.setParallelDownloadAttributes(requestContext, res);

        verify(requestContext).setAttribute("fileSize", "5000");
        verify(requestContext, never()).setAttribute(eq(ParallelDownloadService.RCD_EXPECTED_SHA1), anyString());
        verify(requestContext, never()).setAttribute(eq(ParallelDownloadService.RCD_SUPPORTS_RANGE), anyString());
    }

    private static class TestResourceWithChecksum implements ResourceWithChecksumStreamHandle {
        private ChecksumsInfo checksumsInfo;

        public TestResourceWithChecksum(ChecksumsInfo checksumsInfo) {
            this.checksumsInfo = checksumsInfo;
        }

        @Override
        public Optional<ChecksumsInfo> getChecksumsInfo() {
            return Optional.ofNullable(checksumsInfo);
        }

        @Override
        public InputStream getInputStream() {
            return null;
        }

        @Override
        public long getSize() {
            return 0;
        }

        @Override
        public void close() {

        }
    }
}
