package org.artifactory.ph;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.jfrog.ph.acl.exception.PackageException;
import com.jfrog.ph.acl.exception.PackageNotFoundException;
import com.jfrog.ph.acl.exception.PackageRepoNotFoundException;
import com.jfrog.ph.acl.model.PackageDownloadContext;
import com.jfrog.ph.acl.virtual.resolution.PackageRepo;
import org.artifactory.api.events.EventLogHintManager;
import org.artifactory.api.properties.PropertiesService;
import org.artifactory.api.repo.UpstreamRepoKind;
import org.artifactory.common.repo.PackageType;
import org.artifactory.common.repo.RepoInfo;
import org.artifactory.common.repo.RepoType;
import org.artifactory.descriptor.repo.RealRepoDescriptor;
import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.descriptor.repo.VirtualRepoDescriptor;
import org.artifactory.event.EventHintType;
import org.artifactory.fs.ItemInfo;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.properties.PropertiesImpl;
import org.artifactory.repo.HttpRepo;
import org.artifactory.repo.LocalCacheRepo;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.RealRepo;
import org.artifactory.repo.RemoteRepo;
import org.artifactory.repo.Repo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.federated.FederatedRepo;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.repo.virtual.VirtualRepo;
import org.artifactory.repo.virtual.VirtualRepositoriesResolver;
import org.artifactory.util.DoesNotExistException;
import org.junit.jupiter.api.Assertions;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.artifactory.common.repo.RepoType.LOCAL;
import static org.artifactory.common.repo.RepoType.REMOTE;
import static org.artifactory.common.repo.RepoType.VIRTUAL;
import static org.artifactory.descriptor.repo.RepoType.CRAN;
import static org.artifactory.descriptor.repo.RepoType.Docker;
import static org.artifactory.descriptor.repo.RepoType.Helm;
import static org.artifactory.repo.system.SystemRepos.TRASH_REPO_KEY;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

@SuppressWarnings("rawtypes")
@Test
public class PackageRepoServiceImplTest {

    @Mock
    private InternalRepositoryService repositoryService;
    @Mock
    private PropertiesService propertiesService;
    private PackageRepoServiceImpl packageRepoService;
    private AutoCloseable closeable;

    @BeforeMethod
    private void setUp() {
        closeable = openMocks(this);
        packageRepoService = Mockito.spy(new PackageRepoServiceImpl(repositoryService, propertiesService));
    }

    @AfterMethod
    private void tearDown() throws Exception {
        closeable.close();
    }

    @Test
    public void testIsVirtual() {
        when(repositoryService.virtualRepositoryByKey(any())).thenReturn(null);
        assertFalse(packageRepoService.isVirtual("repoKey"));
        verify(repositoryService, times(1)).virtualRepositoryByKey(eq("repoKey"));

        reset(repositoryService);

        when(repositoryService.virtualRepositoryByKey(any())).thenReturn(mock(VirtualRepo.class));
        assertTrue(packageRepoService.isVirtual("repoKey"));
        verify(repositoryService, times(1)).virtualRepositoryByKey(eq("repoKey"));
    }

    @Test
    public void testIsRemote() {
        when(repositoryService.remoteRepositoryByKey(any())).thenReturn(null);
        assertFalse(packageRepoService.isRemote("repoKey"));
        verify(repositoryService, times(1)).remoteRepositoryByKey(eq("repoKey"));

        reset(repositoryService);

        when(repositoryService.remoteRepositoryByKey(any())).thenReturn(mock(RemoteRepo.class));
        assertTrue(packageRepoService.isRemote("repoKey"));
        verify(repositoryService, times(1)).remoteRepositoryByKey(eq("repoKey"));
    }

    @Test
    public void testIsLocal() {
        when(repositoryService.localOrFederatedRepositoryByKey(any())).thenReturn(null);
        assertFalse(packageRepoService.isLocal("repoKey"));
        verify(repositoryService, times(1)).localOrFederatedRepositoryByKey(eq("repoKey"));

        reset(repositoryService);

        when(repositoryService.localOrFederatedRepositoryByKey(any())).thenReturn(mock(LocalRepo.class));
        assertTrue(packageRepoService.isLocal("repoKey"));
        verify(repositoryService, times(1)).localOrFederatedRepositoryByKey(eq("repoKey"));
    }

    @Test
    public void testMove() throws PackageException {
        when(repositoryService.move(any(RepoPath.class), any(RepoPath.class), anyBoolean(), anyBoolean(),
                anyBoolean())).thenReturn(null);

        packageRepoService.move("repoKey", "repoKey", "source", "target");

        ArgumentCaptor<RepoPath> source = ArgumentCaptor.forClass(RepoPath.class);
        ArgumentCaptor<RepoPath> target = ArgumentCaptor.forClass(RepoPath.class);

        verify(repositoryService, times(1)).move(source.capture(), target.capture(), eq(false), eq(true), eq(true),
                eq(false));

        assertEquals(source.getValue().toPath(), "repoKey/source");
        assertEquals(target.getValue().toPath(), "repoKey/target");

        reset(repositoryService);

        when(repositoryService.move(any(RepoPath.class), any(RepoPath.class), anyBoolean(), anyBoolean(),
                anyBoolean(), anyBoolean())).thenThrow(new RuntimeException("test"));

        try {
            packageRepoService.move("repoKey", "repoKey", "source", "target");
            fail();
        } catch (PackageException e) {
            assertEquals(e.getStatus(), SC_INTERNAL_SERVER_ERROR);
        }
    }

    @Test
    public void testCopy() throws PackageException {
        when(repositoryService.copy(any(RepoPath.class), any(RepoPath.class), anyBoolean(), anyBoolean(),
                anyBoolean())).thenReturn(null);

        packageRepoService.copy("repoKey", "repoKey", "source", "target");

        ArgumentCaptor<RepoPath> source = ArgumentCaptor.forClass(RepoPath.class);
        ArgumentCaptor<RepoPath> target = ArgumentCaptor.forClass(RepoPath.class);

        verify(repositoryService, times(1)).copy(source.capture(), target.capture(), eq(false), eq(true), eq(true));

        assertEquals(source.getValue().toPath(), "repoKey/source");
        assertEquals(target.getValue().toPath(), "repoKey/target");

        reset(repositoryService);

        when(repositoryService.copy(any(RepoPath.class), any(RepoPath.class), anyBoolean(), anyBoolean(),
                anyBoolean())).thenThrow(new RuntimeException("test"));

        try {
            packageRepoService.copy("repoKey", "repoKey", "source", "target");
            fail();
        } catch (PackageException e) {
            assertEquals(e.getStatus(), SC_INTERNAL_SERVER_ERROR);
        }
    }

    @Test
    public void testCopyToCache() throws PackageException {
        when(repositoryService.copyToCache(any(RepoPath.class), any(RepoPath.class), anyBoolean(), anyBoolean(),
                anyBoolean(), anyBoolean(), any(), anyBoolean())).thenReturn(null);

        packageRepoService.copyToCache("repoKey", "repoKey", "source", "target");

        ArgumentCaptor<RepoPath> source = ArgumentCaptor.forClass(RepoPath.class);
        ArgumentCaptor<RepoPath> target = ArgumentCaptor.forClass(RepoPath.class);

        verify(repositoryService, times(1)).copyToCache(source.capture(), target.capture(), eq(false), eq(true),
                eq(true), eq(false), eq(null), eq(true));

        assertEquals(source.getValue().toPath(), "repoKey/source");
        assertEquals(target.getValue().toPath(), "repoKey/target");
    }

    @Test
    public void testCopyToCacheWhenCopyFailed() {
        when(repositoryService.copyToCache(any(RepoPath.class), any(RepoPath.class), anyBoolean(), anyBoolean(),
                anyBoolean(), anyBoolean(), any(), anyBoolean())).thenThrow(new RuntimeException("test"));
        try {
            packageRepoService.copyToCache("repoKey", "repoKey", "source", "target");
            fail();
        } catch (PackageException e) {
            assertEquals(e.getStatus(), SC_INTERNAL_SERVER_ERROR);
        }
    }

    @DataProvider
    private Object[][] testGetRepoInfoDP() {
        return new Object[][] {
                {(Runnable) () -> {
                    LocalRepoConfig localRepoConfig = mock(LocalRepoConfig.class);
                    when(localRepoConfig.getPackageType()).thenReturn(CRAN);
                    LocalRepo repo = mock(LocalRepo.class);
                    when(repo.isLocal()).thenReturn(true);
                    when(repo.getRepoConfig()).thenReturn(localRepoConfig);
                    when(repositoryService.getRepositoryConfigByKey(anyString())).thenReturn(localRepoConfig);
                    when(repositoryService.localOrFederatedRepositoryByKey(anyString())).thenReturn(repo);
                }, LOCAL},
                {(Runnable) () -> {
                    RemoteRepoConfig remoteRepoConfig = mock(RemoteRepoConfig.class);
                    when(remoteRepoConfig.getPackageType()).thenReturn(CRAN);
                    when(remoteRepoConfig.isReal()).thenReturn(true);
                    RemoteRepo repo = mock(RemoteRepo.class);
                    when(repo.isReal()).thenReturn(true);
                    when(repo.getRepoConfig()).thenReturn(remoteRepoConfig);
                    when(repositoryService.getRepositoryConfigByKey(anyString())).thenReturn(remoteRepoConfig);
                    when(repositoryService.remoteRepositoryByKey(anyString())).thenReturn(repo);
                }, REMOTE},
                {(Runnable) () -> {
                    VirtualRepoConfig virtualRepoConfig = mock(VirtualRepoConfig.class);
                    when(virtualRepoConfig.getPackageType()).thenReturn(CRAN);
                    VirtualRepo repo = mock(VirtualRepo.class);
                    when(repo.getRepoConfig()).thenReturn(virtualRepoConfig);
                    when(repositoryService.getRepositoryConfigByKey(anyString())).thenReturn(virtualRepoConfig);
                    when(repositoryService.virtualRepositoryByKey(anyString())).thenReturn(repo);
                }, VIRTUAL},

        };
    }

    @Test(dataProvider = "testGetRepoInfoDP")
    public void testGetRepoInfo(Runnable env, RepoType expectedRepoType) throws PackageException {
        env.run();
        RepoInfo repoInfo = packageRepoService.getRepoInfo("repoKey");
        assertNotNull(repoInfo);
        assertEquals(repoInfo.getRepoType(), expectedRepoType);
        assertEquals(repoInfo.getPackageType(), PackageType.CRAN);
        verify(repositoryService, times(1)).getRepositoryConfigByKey("repoKey");
        verifyNoMoreInteractions(repositoryService);
    }

    public void testGetRepoInfoNotFound() {
        try {
            packageRepoService.getRepoInfo("repoKey");
            Assertions.fail();
        } catch (PackageException e) {
            Assertions.assertTrue(true);
        }
    }

    @Test(dataProvider = "testGetRepoInfoDP")
    public void testGetPackageType(Runnable env, RepoType expectedRepoType) {
        env.run();
        PackageType packageType = packageRepoService.getPackageType("repoKey");
        if (expectedRepoType != null) {
            assertEquals(packageType, PackageType.CRAN);
        } else {
            assertNull(packageType);
        }
    }

    @Test
    public void testAddAttribute() throws PackageException {
        LocalRepo localRepo = Mockito.mock(LocalRepo.class);
        when(localRepo.getKey()).thenReturn("repoKey");
        when(repositoryService.localFederatedOrCachedRepositoryByKey(anyString())).thenReturn(localRepo);
        packageRepoService.addAttribute("repoKey", "path", "k1", Set.of("v11"));
        ArgumentCaptor<RepoPath> repoPath = ArgumentCaptor.forClass(RepoPath.class);
        verify(propertiesService, times(1)).addProperties(repoPath.capture(), eq("k1"), eq(Set.of("v11")), eq(true));

        assertEquals(repoPath.getValue().toPath(), "repoKey/path");
    }

    @Test
    public void testSetAttributes() throws PackageException {
        LocalRepo localRepo = Mockito.mock(LocalRepo.class);
        when(localRepo.getKey()).thenReturn("repoKey");
        when(repositoryService.localFederatedOrCachedRepositoryByKey(anyString())).thenReturn(localRepo);
        PropertiesImpl properties = new PropertiesImpl();
        properties.put("k1", "v1");
        properties.put("k2", "v2");
        when(propertiesService.getProperties(any(RepoPath.class))).thenReturn(properties);
        Multimap<String, String> props = ArrayListMultimap.create();
        props.put("k1", "v11");
        props.put("k2", null);
        props.put("k3", "v3");

        packageRepoService.setAttributes("repoKey", "path", props);
        ArgumentCaptor<RepoPath> repoPath = ArgumentCaptor.forClass(RepoPath.class);
        verify(propertiesService, times(1)).setProperties(repoPath.capture(), eq(properties), eq(true));

        assertEquals(repoPath.getValue().toPath(), "repoKey/path");
        assertEquals(properties.size(), 3);
        assertEquals(properties.get("k1"), List.of("v11"));
        ArrayList<Object> list = Lists.newArrayList();
        list.add(null);
        assertEquals(properties.get("k2"), list);
        assertEquals(properties.get("k3"), List.of("v3"));
    }

    @Test
    public void testSetAttributesWithRemoteWithoutCache() throws PackageException {
        RemoteRepo remoteRepo = Mockito.mock(RemoteRepo.class);
        when(remoteRepo.getKey()).thenReturn("repoKey");
        when(repositoryService.localFederatedOrCachedRepositoryByKey("repoKey")).thenReturn(null);
        when(repositoryService.remoteRepositoryByKey("repoKey")).thenReturn(remoteRepo);
        Multimap<String, String> props = ArrayListMultimap.create();
        props.put("k1", "v11");
        props.put("k2", null);
        props.put("k3", "v3");

        packageRepoService.setAttributes("repoKey", "path", props);
        verifyNoMoreInteractions(propertiesService);
        verify(repositoryService, times(1)).localFederatedOrCachedRepositoryByKey(anyString());
        verify(repositoryService, times(1)).remoteRepositoryByKey(anyString());
        verifyNoMoreInteractions(repositoryService);
    }

    @Test
    public void testRemoveAttributes() throws PackageException {
        LocalRepo localRepo = Mockito.mock(LocalRepo.class);
        when(localRepo.getKey()).thenReturn("repoKey");
        when(repositoryService.localFederatedOrCachedRepositoryByKey(anyString())).thenReturn(localRepo);
        PropertiesImpl properties = new PropertiesImpl();
        properties.put("k1", "v1");
        properties.put("k2", "v2");
        when(propertiesService
                .removePropertyValues(any(RepoPath.class), eq("k1"), argThat(strings -> strings.contains("v1")),
                        eq(true))).thenReturn(true);
        packageRepoService.removeAttributes("repoKey", "path", "k1", Set.of("v1"));
        verify(propertiesService, times(1)).removePropertyValues(any(), any(), any(), eq(true));
        verifyNoMoreInteractions(propertiesService);
    }

    @Test
    public void testRemoveAttributesWithRemoteWithoutCache() throws PackageException {
        RemoteRepo remoteRepo = Mockito.mock(RemoteRepo.class);
        when(remoteRepo.getKey()).thenReturn("repoKey");
        when(repositoryService.localFederatedOrCachedRepositoryByKey("repoKey")).thenReturn(null);
        when(repositoryService.remoteRepositoryByKey("repoKey")).thenReturn(remoteRepo);
        packageRepoService.removeAttributes("repoKey", "path", "k1", Set.of("v1"));
        verifyNoMoreInteractions(propertiesService);
        verify(repositoryService, times(1)).localFederatedOrCachedRepositoryByKey(anyString());
        verify(repositoryService, times(1)).remoteRepositoryByKey(anyString());
        verifyNoMoreInteractions(repositoryService);
    }

    @Test
    public void testRemoveAttributesWithNull() throws PackageException {
        LocalRepo localRepo = Mockito.mock(LocalRepo.class);
        when(localRepo.getKey()).thenReturn("repoKey");
        when(repositoryService.localFederatedOrCachedRepositoryByKey(anyString())).thenReturn(localRepo);
        when(propertiesService.deleteProperty(any(RepoPath.class), eq("k1"))).thenReturn(true);
        packageRepoService.removeAttributes("repoKey", "path", "k1", null);
        verify(propertiesService, times(1)).deleteProperty(any(), any());
        verifyNoMoreInteractions(propertiesService);
    }

    @Test
    public void testGetAttributes() throws PackageException {
        LocalRepo localRepo = Mockito.mock(LocalRepo.class);
        when(localRepo.getKey()).thenReturn("repoKey");
        when(repositoryService.localFederatedOrCachedRepositoryByKey(anyString())).thenReturn(localRepo);
        PropertiesImpl properties = new PropertiesImpl();
        properties.put("k1", "v1");
        properties.put("k2", "v2");
        when(propertiesService.getProperties(any(RepoPath.class))).thenReturn(properties);

        Map<String, Collection<String>> props = packageRepoService.getAttributes("repoKey", "some/file.txt");
        verify(propertiesService, times(1)).getProperties(any(RepoPath.class));
        assertEquals(props.size(), 2);
        assertEquals(props.get("k1"), List.of("v1"));
        assertEquals(properties.get("k2"), List.of("v2"));
    }

    @Test
    public void testGetAttributesWithRemoteWithoutCache() throws PackageException {
        RemoteRepo remoteRepo = Mockito.mock(RemoteRepo.class);
        when(remoteRepo.getKey()).thenReturn("repoKey");
        when(repositoryService.localFederatedOrCachedRepositoryByKey("repoKey")).thenReturn(null);
        when(repositoryService.remoteRepositoryByKey("repoKey")).thenReturn(remoteRepo);
        Map<String, Collection<String>> props = packageRepoService.getAttributes("repoKey", "some/file.txt");
        assertEquals(props.size(), 0);
        verifyNoMoreInteractions(propertiesService);
        verify(repositoryService, times(1)).localFederatedOrCachedRepositoryByKey(anyString());
        verify(repositoryService, times(1)).remoteRepositoryByKey(anyString());
        verifyNoMoreInteractions(repositoryService);
    }

    @Test
    public void testGetAttributesWithNonRealRepo() {
        when(repositoryService.localFederatedOrCachedRepositoryByKey(anyString())).thenReturn(null);
        try {
            packageRepoService.getAttributes("repoKey", "some/file.txt");
            fail("Expected PackageException");
        } catch (PackageException e) {
            assertNotNull(e);
            assertEquals(e.getInternalReason(), "Cannot read properties on repository repoKey");
        }
        verify(propertiesService, times(0)).getProperties(any(RepoPath.class));
    }

    @Test
    public void testExist() {
        when(repositoryService.exists(any(RepoPath.class))).thenReturn(false);
        assertFalse(packageRepoService.existLocally("repoKey", "path"));
        verify(repositoryService, times(1)).exists(eq(RepoPathFactory.create("repoKey", "path")));

        reset(repositoryService);

        when(repositoryService.exists(any(RepoPath.class))).thenReturn(true);
        assertTrue(packageRepoService.existLocally("repoKey", "path"));
        verify(repositoryService, times(1)).exists(eq(RepoPathFactory.create("repoKey", "path")));
    }

    @Test(dataProvider = "testExistWithVirtualRepoProvider")
    public void testExistWithVirtualRepo(boolean exists) {
        String repoKey = "virtual";
        String path = "path";
        VirtualRepo virtualRepo = mock(VirtualRepo.class);
        when(repositoryService.virtualRepositoryByKey(repoKey)).thenReturn(virtualRepo);
        when(virtualRepo.itemExists(path)).thenReturn(exists);
        assertEquals(exists, packageRepoService.existLocally(repoKey, path));
        verify(virtualRepo, times(1)).itemExists(path);
        verify(repositoryService, times(1)).virtualRepositoryByKey(repoKey);
        verifyNoMoreInteractions(repositoryService, virtualRepo);
    }

    @Test
    public void testUndeployArtifact() {
        packageRepoService.undeployArtifact("repoKey", "path");
        verify(repositoryService, times(1)).undeploy(eq(RepoPathFactory.create("repoKey", "path")));
    }

    @Test
    public void testGetSubRepositories() throws PackageException {
        VirtualRepo virtualRepo = mock(VirtualRepo.class);
        VirtualRepositoriesResolver resolver = mock(VirtualRepositoriesResolver.class);
        RealRepo<?, ?> subRepoCache = mock(RealRepo.class);
        when(subRepoCache.isCache()).thenReturn(true);
        when(subRepoCache.isLocal()).thenReturn(true);
        when(virtualRepo.getVirtualRepositoriesResolver()).thenReturn(resolver);
        when(repositoryService.virtualRepositoryByKey(anyString())).thenReturn(virtualRepo);
        when(resolver.assembleSearchRepositoriesList(any(RepoPath.class), anyBoolean()))
                .thenReturn(List.of(subRepoCache));
        PackageDownloadContext packageDownloadContext = new PackageDownloadContext("repoKey", "");
        List<? extends PackageRepo> subs = packageRepoService.getSubRepositories(packageDownloadContext, false);

        verify(repositoryService, times(1)).virtualRepositoryByKey(eq("repoKey"));
        verify(virtualRepo, times(1)).getVirtualRepositoriesResolver();
        verify(resolver, times(1)).assembleSearchRepositoriesList(eq(RepoPathFactory.create("repoKey", "")), eq(false));
        assertEquals(subs, List.of(subRepoCache));

        //cache shouldn't be filtered because the corresponding remote not exists in the list
        subs = packageRepoService.getSubRepositories(packageDownloadContext, true);
        assertEquals(subs.size(), 1);

        RealRepo<?, ?> subRepo = mock(RealRepo.class);
        when(subRepo.getKey()).thenReturn("remote");
        when(resolver.assembleSearchRepositoriesList(any(RepoPath.class), anyBoolean()))
                .thenReturn(List.of(subRepoCache, subRepo));

        subs = packageRepoService.getSubRepositories(packageDownloadContext, true);
        assertEquals(subs.size(), 1);
        assertEquals("remote", subs.get(0).getKey());


        try {
            when(repositoryService.virtualRepositoryByKey(anyString())).thenReturn(null);
            packageRepoService.getSubRepositories(packageDownloadContext, false);
            fail();
        } catch (PackageException e) {
            assertEquals(e.getStatus(), SC_NOT_FOUND);
        }
    }

    @Test
    public void testGetDefaultDeploymentRepoKey() throws PackageException {
        when(repositoryService.isVirtualRepoExist(anyString())).thenReturn(true);
        when(repositoryService.getDefaultDeploymentRepoKey(anyString())).thenReturn("depRepoKey");
        assertEquals(packageRepoService.getDefaultDeploymentRepoKey("repoKey"), "depRepoKey");
        verify(repositoryService, times(1)).getDefaultDeploymentRepoKey(eq("repoKey"));

        reset(repositoryService);

        try {
            when(repositoryService.isVirtualRepoExist(anyString())).thenReturn(true);
            when(repositoryService.getDefaultDeploymentRepoKey(anyString())).thenThrow(
                    new DoesNotExistException("error"));
            packageRepoService.getDefaultDeploymentRepoKey("repoKey");
            fail();
        } catch (PackageException e) {
            verify(repositoryService, times(1)).getDefaultDeploymentRepoKey(eq("repoKey"));
            assertEquals(e.getStatus(), SC_NOT_FOUND);
        }
    }

    @Test
    public void testGetRemoteRepoBaseUrl() throws PackageException {
        final String remoteKey = "repoKey";
        final String expectedUrl = "http:://domain.com";
        RemoteRepo remoteRepo = mock(RemoteRepo.class);
        when(remoteRepo.getUrl()).thenReturn(expectedUrl);
        when(repositoryService.remoteRepositoryByKey(remoteKey)).thenReturn(remoteRepo);
        String baseUrl = packageRepoService.getRemoteRepoBaseUrl("repoKey");
        assertEquals(expectedUrl, baseUrl);
    }

    @Test
    public void testGetVirtualReposContainingRepoByPackageType() throws PackageException {

        try {
            when(repositoryService.repoDescriptorByKey(anyString())).thenReturn(null);
            packageRepoService.getVirtualReposContainingRepo("repoKey");
            fail();
        } catch (PackageException e) {
            assertEquals(e.getStatus(), SC_NOT_FOUND);
        }

        reset(repositoryService);

        RepoDescriptor descriptor = mock(RepoDescriptor.class);
        when(descriptor.getType()).thenReturn(CRAN);
        when(repositoryService.repoDescriptorByKey(anyString())).thenReturn(descriptor);

        VirtualRepoDescriptor virtualRepoDescriptor1 = mock(VirtualRepoDescriptor.class);
        when(virtualRepoDescriptor1.getKey()).thenReturn("repoKey1");
        when(virtualRepoDescriptor1.getType()).thenReturn(CRAN);

        VirtualRepoDescriptor virtualRepoDescriptor2 = mock(VirtualRepoDescriptor.class);
        when(virtualRepoDescriptor2.getKey()).thenReturn("repoKey2");
        when(virtualRepoDescriptor2.getType()).thenReturn(Docker);

        when(repositoryService.getVirtualReposContainingRepo(any(RepoDescriptor.class))).thenReturn(
                List.of(virtualRepoDescriptor1, virtualRepoDescriptor2));


        Repo result = mock(Repo.class);
        when(repositoryService.repositoryByKey(anyString())).thenReturn(result);

        List<PackageRepo> repos = packageRepoService.getVirtualReposContainingRepo("repoKey");

        assertEquals(repos, List.of(result));
        verify(repositoryService, times(1)).repoDescriptorByKey(eq("repoKey"));
        verify(repositoryService, times(1)).getVirtualReposContainingRepo(eq(descriptor));
        verify(repositoryService, times(1)).repositoryByKey(anyString());
    }

    @Test
    public void testGetAllReposByType() throws PackageException {
        String expectedLocalRepo = "local";
        String expectedRemoteRepo = "remote";

        RealRepo localRepo = mockRealRepo(true, false, expectedLocalRepo, Helm);
        RealRepo remoteRepo = mockRealRepo(false, false, expectedRemoteRepo, Helm);
        RealRepo remoteCache = mockRealRepo(true, true, "cache", Helm);
        RealRepo wrongRepo = mockRealRepo(true, false, "wrong", CRAN);
        when(repositoryService.getLocalFederatedAndRemoteRepositories())
                .thenReturn(List.of(localRepo, wrongRepo, remoteCache, remoteRepo));
        List<PackageRepo> localRepoRes = packageRepoService.getAllReposByType(PackageType.HELM, LOCAL);
        List<PackageRepo> remoteRepoRes = packageRepoService.getAllReposByType(PackageType.HELM, REMOTE);
        assertEquals(localRepoRes.size(), 1);
        assertEquals(remoteRepoRes.size(), 1);
        assertEquals(localRepoRes.get(0).getKey(), expectedLocalRepo);
        assertEquals(remoteRepoRes.get(0).getKey(), expectedRemoteRepo);
        verify(repositoryService, times(2)).getLocalFederatedAndRemoteRepositories();
        verifyNoMoreInteractions(repositoryService);
        List<PackageRepo> virtualRepoRes = packageRepoService.getAllReposByType(PackageType.HELM, VIRTUAL);
        assertEquals(virtualRepoRes.size(), 0);
        verify(repositoryService, times(1)).getVirtualRepositories();
        verifyNoMoreInteractions(repositoryService);
    }

    @Test
    public void testClearRemoteRepoCache() {
        RemoteRepo remoteRepo = mock(RemoteRepo.class);
        when(repositoryService.remoteRepositoryByKey("npm-remote")).thenReturn(remoteRepo);
        packageRepoService.clearRemoteRepoCache("npm-remote");

        verify(repositoryService).remoteRepositoryByKey("npm-remote");
        verify(remoteRepo).clearCaches();
    }

    @Test
    public void testClearRemoteRepoCacheWhenRepoNotFound() {
        packageRepoService.clearRemoteRepoCache("npm-remote");
        verify(repositoryService).remoteRepositoryByKey("npm-remote");
    }

    @Test
    public void testIsFederatedRepo() throws PackageException {
        FederatedRepo federatedRepo = mock(FederatedRepo.class);
        when(repositoryService.localOrFederatedRepositoryByKey(anyString())).thenReturn(federatedRepo);
        assertTrue(packageRepoService.isFederatedRepo("repoKey"));
    }

    @Test
    public void testGetRemoteCacheRepoKeyWhenPackageIsNotFound() {
        when(repositoryService.remoteRepositoryByKey(any())).thenReturn(null);
        PackageNotFoundException exception = Assertions.assertThrows(PackageNotFoundException.class,
                () -> packageRepoService.getRemoteCacheRepoKey("repo-key"));
        Assertions.assertEquals("Remote repository repo-key was not found",
                exception.getInternalReason());
    }

    @Test
    public void testGetRemoteCacheRepoKeyWhenRepositoryDoesNotStoreLocally() {
        RemoteRepo remoteRepo = Mockito.mock(RemoteRepo.class);
        when(remoteRepo.isStoreArtifactsLocally()).thenReturn(false);
        when(repositoryService.remoteRepositoryByKey(anyString())).thenReturn(remoteRepo);
        PackageNotFoundException exception = Assertions.assertThrows(PackageNotFoundException.class,
                () -> packageRepoService.getRemoteCacheRepoKey("repo-key"));
        Assertions.assertEquals("Remote repository repo-key does not allow storing artifacts locally.",
                exception.getInternalReason());
    }

    @Test
    public void testIsFederatedRepoWhenRepoNotFound() {
        when(repositoryService.localOrFederatedRepositoryByKey(anyString())).thenReturn(null);
        try {
            packageRepoService.isFederatedRepo("repoKey");
            fail("Expected package not found exception");
        } catch (PackageException e) {
            assertEquals(e.getInternalReason(), "Repository 'repoKey' was not found");
        }
    }

    @Test(dataProvider = "booleanProvider")
    void testUndeploy(boolean pruneEmptyFolders) {
        packageRepoService.undeployArtifact("repoKey", "path", pruneEmptyFolders);
        verify(repositoryService, times(1))
                .undeploy(RepoPathFactory.create("repoKey", "path"), true, pruneEmptyFolders);
    }

    @Test
    void testAtomicUndeploy() throws PackageException {
        packageRepoService.undeployArtifactsAtomically("repoKey", Set.of("path"));
        verify(repositoryService, times(1)).move(any(Set.class), eq(TRASH_REPO_KEY), eq(null), eq(false), eq(false));
    }

    @Test
    void atomicUndeployHandlesException() {
        when(repositoryService.move(any(Set.class), eq(TRASH_REPO_KEY), eq(null), eq(false), eq(false)))
                .thenThrow(new RuntimeException("test"));
        assertThrowsExactly(PackageException.class,
                () -> packageRepoService.undeployArtifactsAtomically("repoKey", Set.of("path")));
    }

    @Test
    void undeployVirtualMetadataCache() throws PackageException {
        final String repoKey = "repoKey";
        final String path = "path";
        final String virtualRepoKey = "virtualRepoKey";
        final Repo virtualRepo = mock(Repo.class);
        when(virtualRepo.getKey()).thenReturn(virtualRepoKey);
        doReturn(List.of(virtualRepo)).when(packageRepoService).getVirtualReposContainingRepo(repoKey);
        doReturn(true).when(packageRepoService).existLocally(virtualRepoKey, path);
        when(repositoryService.virtualRepositoryByKey(virtualRepoKey)).thenReturn(mock(VirtualRepo.class));
        packageRepoService.undeployVirtualMetadataCache(repoKey, path);
        verify(repositoryService, times(1)).undeploy(any(RepoPath.class));
    }

    @Test
    void undeployVirtualMetadataCacheExceptionHandled() throws PackageException {
        final String repoKey = "repoKey";
        final String path = "path";
        final String virtualRepoKey = "virtualRepoKey";
        final Repo virtualRepo = mock(Repo.class);
        when(virtualRepo.getKey()).thenReturn(virtualRepoKey);
        doThrow(new PackageNotFoundException()).when(packageRepoService).getVirtualReposContainingRepo(repoKey);
        Assertions.assertDoesNotThrow(() -> packageRepoService.undeployVirtualMetadataCache(repoKey, path));
        verify(repositoryService, times(0)).undeploy(any(RepoPath.class));
    }

    @Test
    void zapVirtualMetadataCache() throws PackageException {
        final String repoKey = "repoKey";
        final String path = ".npm/pkg";
        final String virtualRepoKey = "virtualRepoKey";
        final Repo virtualRepo = mock(Repo.class);
        when(virtualRepo.getKey()).thenReturn(virtualRepoKey);
        doReturn(List.of(virtualRepo)).when(packageRepoService).getVirtualReposContainingRepo(repoKey);
        doReturn(true).when(packageRepoService).existLocally(virtualRepoKey, path);

        // The cache folder holds one file per permission-hash/strategy variant. A folder entry should be
        // skipped, while every file should be zapped (expired) individually, keeping the bytes on disk.
        RepoPath filePath1 = RepoPathFactory.create(virtualRepoKey, path + "/package--1-FULL_STREAM.json");
        RepoPath filePath2 = RepoPathFactory.create(virtualRepoKey, path + "/package--2-FULL_STREAM.json");
        ItemInfo file1 = mock(ItemInfo.class);
        when(file1.isFolder()).thenReturn(false);
        when(file1.getRepoPath()).thenReturn(filePath1);
        ItemInfo file2 = mock(ItemInfo.class);
        when(file2.isFolder()).thenReturn(false);
        when(file2.getRepoPath()).thenReturn(filePath2);
        ItemInfo folder = mock(ItemInfo.class);
        when(folder.isFolder()).thenReturn(true);
        when(repositoryService.getChildrenStream(any(RepoPath.class)))
                .thenReturn(Stream.of(file1, folder, file2));

        packageRepoService.zapVirtualMetadataCache(repoKey, path);

        verify(repositoryService, times(1)).zapFileFromVirtual(filePath1);
        verify(repositoryService, times(1)).zapFileFromVirtual(filePath2);
        verify(repositoryService, times(2)).zapFileFromVirtual(any(RepoPath.class));
        // The folder child must never be zapped.
        verify(folder, times(0)).getRepoPath();
    }

    @Test
    void zapVirtualMetadataCacheNoCacheToZap() throws PackageException {
        final String repoKey = "repoKey";
        final String path = ".npm/pkg";
        final String virtualRepoKey = "virtualRepoKey";
        final Repo virtualRepo = mock(Repo.class);
        when(virtualRepo.getKey()).thenReturn(virtualRepoKey);
        doReturn(List.of(virtualRepo)).when(packageRepoService).getVirtualReposContainingRepo(repoKey);
        doReturn(false).when(packageRepoService).existLocally(virtualRepoKey, path);

        packageRepoService.zapVirtualMetadataCache(repoKey, path);

        // Nothing exists at the cache root, so we never enumerate or zap anything.
        verify(repositoryService, times(0)).getChildrenStream(any(RepoPath.class));
        verify(repositoryService, times(0)).zapFileFromVirtual(any(RepoPath.class));
    }

    @Test
    void zapVirtualMetadataCacheOnlyFoldersNothingZapped() throws PackageException {
        final String repoKey = "repoKey";
        final String path = ".npm/pkg";
        final String virtualRepoKey = "virtualRepoKey";
        final Repo virtualRepo = mock(Repo.class);
        when(virtualRepo.getKey()).thenReturn(virtualRepoKey);
        doReturn(List.of(virtualRepo)).when(packageRepoService).getVirtualReposContainingRepo(repoKey);
        doReturn(true).when(packageRepoService).existLocally(virtualRepoKey, path);
        ItemInfo folder = mock(ItemInfo.class);
        when(folder.isFolder()).thenReturn(true);
        when(repositoryService.getChildrenStream(any(RepoPath.class))).thenReturn(Stream.of(folder));

        packageRepoService.zapVirtualMetadataCache(repoKey, path);

        // Only a folder child exists, so nothing is zapped (the warn branch is exercised).
        verify(repositoryService, times(0)).zapFileFromVirtual(any(RepoPath.class));
    }

    @Test
    void zapVirtualMetadataCacheExceptionHandled() throws PackageException {
        final String repoKey = "repoKey";
        final String path = ".npm/pkg";
        doThrow(new PackageNotFoundException()).when(packageRepoService).getVirtualReposContainingRepo(repoKey);
        Assertions.assertDoesNotThrow(() -> packageRepoService.zapVirtualMetadataCache(repoKey, path));
        verify(repositoryService, times(0)).zapFileFromVirtual(any(RepoPath.class));
    }

    @Test
    void removeFromCaches() throws PackageException {
        final String repoKey = "repoKey";
        final String path = "path";
        RemoteRepo repo = mock(RemoteRepo.class);
        LocalCacheRepo cacheRepo = mock(LocalCacheRepo.class);
        when(repo.isStoreArtifactsLocally()).thenReturn(true);
        when(repo.getLocalCacheRepo()).thenReturn(cacheRepo);
        when(repositoryService.remoteRepositoryByKey(repoKey)).thenReturn(repo);
        packageRepoService.removeFromRemoteRepoCaches(repoKey, path, false);
        verify(repo, times(1)).removeFromCaches(path, false);
    }

    @Test
    void removeFromCachesNotRemote() {
        final String repoKey = "repoKey";
        final String path = "path";
        when(repositoryService.remoteRepositoryByKey(repoKey)).thenReturn(null);
        assertThrows(PackageException.class,
                () -> packageRepoService.removeFromRemoteRepoCaches(repoKey, path, false));
    }

    @Test
    void removeFromCachesNotStoreLocally() {
        final String repoKey = "repoKey";
        final String path = "path";
        RemoteRepo repo = mock(RemoteRepo.class);
        when(repositoryService.remoteRepositoryByKey(repoKey)).thenReturn(repo);
        when(repo.isStoreArtifactsLocally()).thenReturn(false);
        assertThrows(PackageException.class,
                () -> packageRepoService.removeFromRemoteRepoCaches(repoKey, path, false));
    }

    @Test
    void removeFromCachesNotLocalCache() {
        final String repoKey = "repoKey";
        final String path = "path";
        RemoteRepo repo = mock(RemoteRepo.class);
        when(repositoryService.remoteRepositoryByKey(repoKey)).thenReturn(repo);
        when(repo.isStoreArtifactsLocally()).thenReturn(true);
        when(repo.getLocalCacheRepo()).thenReturn(null);
        when(repositoryService.repositoryByKey(repoKey)).thenReturn(repo);
        assertThrows(PackageException.class,
                () -> packageRepoService.removeFromRemoteRepoCaches(repoKey, path, false));
    }

    @Test(dataProvider = "booleanProvider")
    public void addAttributeWithMirroringExclusionHandling(boolean allowReplication) throws PackageException {
        String repoKey = "repoKey";
        String path = "path";
        String key = "key";
        String valueToExclude = "valueToExclude";
        Set<String> valuesToAdd = Set.of("value1", "value2");

        doNothing().when(packageRepoService).addAttribute(repoKey, path, key, valuesToAdd);

        packageRepoService.addAttributeWithMirroringExclusionHandling(repoKey, path, key, valueToExclude, valuesToAdd,
                allowReplication);
        verify(packageRepoService, times(1)).addAttribute(repoKey, path, key, valuesToAdd);
        if (allowReplication) {
            assertTrue(EventLogHintManager.contains(EventHintType.ReplicationAllowed));
        } else {
            assertTrue(EventLogHintManager.contains(EventHintType.FederatedReplication));
        }
        EventLogHintManager.clear();
    }

    @Test
    public void addAttributeWithMirroringExclusionHandlingException() throws PackageException {
        String repoKey = "repoKey";
        String path = "path";
        String key = "key";
        String valueToExclude = "valueToExclude";
        Set<String> valuesToAdd = Set.of("value1", "value2");
        boolean allowReplication = true;

        doThrow(PackageException.class).when(packageRepoService).addAttribute(repoKey, path, key, valuesToAdd);

        assertThrows(PackageException.class,
                () -> packageRepoService.addAttributeWithMirroringExclusionHandling(repoKey, path, key, valueToExclude,
                        valuesToAdd, allowReplication));
        verify(packageRepoService, times(1)).addAttribute(repoKey, path, key, valuesToAdd);
        assertNull(EventLogHintManager.getHints());
    }

    @Test(dataProvider = "booleanProvider")
    public void removeAttributeWithMirroringExclusionHandling(boolean allowReplication)
            throws PackageException {
        String repoKey = "repoKey";
        String path = "path";
        String key = "key";
        String valueToExclude = "valueToExclude";
        Set<String> valuesToRemove = Set.of("value1", "value2");

        doNothing().when(packageRepoService).removeAttributes(repoKey, path, key, valuesToRemove);

        packageRepoService.removeAttributeWithMirroringExclusionHandling(repoKey, path, key, valueToExclude,
                valuesToRemove, allowReplication);
        verify(packageRepoService, times(1)).removeAttributes(repoKey, path, key, valuesToRemove);
        if (allowReplication) {
            assertTrue(EventLogHintManager.contains(EventHintType.ReplicationAllowed));
        } else {
            assertTrue(EventLogHintManager.contains(EventHintType.FederatedReplication));
        }
        EventLogHintManager.clear();
    }

    @Test
    public void removeAttributeWithMirroringExclusionHandlingException() throws PackageException {
        String repoKey = "repoKey";
        String path = "path";
        String key = "key";
        String valueToExclude = "valueToExclude";
        Set<String> valuesToRemove = Set.of("value1", "value2");
        boolean allowReplication = true;

        doThrow(PackageException.class).when(packageRepoService).removeAttributes(repoKey, path, key, valuesToRemove);

        assertThrows(PackageException.class,
                () -> packageRepoService.removeAttributeWithMirroringExclusionHandling(repoKey, path, key,
                        valueToExclude, valuesToRemove, allowReplication));
        verify(packageRepoService, times(1)).removeAttributes(repoKey, path, key, valuesToRemove);
        assertNull(EventLogHintManager.getHints());
    }

    @Test
    public void testIsRemoteRepoOfflineRepoNotFound() {
        PackageRepoNotFoundException exception = assertThrows(PackageRepoNotFoundException.class,
                () -> packageRepoService.isRemoteRepoOffline("repoKey"));
        assertEquals(exception.getInternalReason(), "Remote repository repoKey was not found");
        assertEquals(exception.getPublicReason(), "Not found");
        assertEquals(exception.getMessage(), "Not found");
    }

    @Test
    public void testGetRemoteRepoBaseUrlRepoNotFound() {
        PackageRepoNotFoundException exception = assertThrows(PackageRepoNotFoundException.class,
                () -> packageRepoService.getRemoteRepoBaseUrl("repoKey"));
        assertEquals(exception.getInternalReason(), "Remote repo repoKey does not exist");
        assertEquals(exception.getPublicReason(), "Not found");
        assertEquals(exception.getMessage(), "Not found");
    }

    @Test
    void testIsPathAcceptedByRepoAccepted() {
        String repoKey = "repoKey";
        String path = "some/accept-path/file.zip";
        RepoPath repoPath = RepoPathFactory.create(repoKey, path);
        when(repositoryService.isRepoPathAccepted(repoPath)).thenReturn(true);
        boolean result = packageRepoService.isPathAcceptedByRepo(repoKey, path);
        assertTrue(result, "Expected the path to be accepted by the repository.");
        verify(repositoryService, times(1)).isRepoPathAccepted(repoPath);
    }

    @Test
    void testIsPathAcceptedByRepoRejected() {
        String repoKey = "repoKey";
        String path = "some/not-accept-path/file.zip";
        RepoPath repoPath = RepoPathFactory.create(repoKey, path);
        when(repositoryService.isRepoPathAccepted(repoPath)).thenReturn(false);
        boolean result = packageRepoService.isPathAcceptedByRepo(repoKey, path);
        assertFalse(result, "Expected the path to be rejected by the repository.");
        verify(repositoryService, times(1)).isRepoPathAccepted(repoPath);
    }

    @Test
    void testIsSmartRepoWhenContentSyncEnabled() {
        HttpRepo httpRepo = mock(HttpRepo.class);
        RemoteRepoConfig repoConfig = mock(RemoteRepoConfig.class);
        when(repositoryService.remoteRepositoryByKey("repoKey")).thenReturn(httpRepo);
        when(httpRepo.getRepoConfig()).thenReturn(repoConfig);
        when(repoConfig.isSmart()).thenReturn(true);
        assertTrue(packageRepoService.isSmartRepo("repoKey"));
        verify(httpRepo, times(0)).getUpstreamRepoKind();
    }

    @Test(dataProvider = "smartRepoProvider")
    void testIsSmartRepo(UpstreamRepoKind upstreamKind, boolean expected) {
        HttpRepo httpRepo = mock(HttpRepo.class);
        RemoteRepoConfig repoConfig = mock(RemoteRepoConfig.class);
        when(repositoryService.remoteRepositoryByKey("repoKey")).thenReturn(httpRepo);
        when(httpRepo.getRepoConfig()).thenReturn(repoConfig);
        when(repoConfig.isSmart()).thenReturn(false);
        when(httpRepo.getUpstreamRepoKind()).thenReturn(upstreamKind);
        assertEquals(packageRepoService.isSmartRepo("repoKey"), expected);
        verify(httpRepo, times(1)).getUpstreamRepoKind();
    }

    @Test
    void testIsSmartRepoNotFound() {
        when(repositoryService.remoteRepositoryByKey("repoKey")).thenReturn(null);
        assertFalse(packageRepoService.isSmartRepo("repoKey"));
    }

    @DataProvider
    private Object[][] smartRepoProvider() {
        return new Object[][] {
                {UpstreamRepoKind.SMART, true},
                {UpstreamRepoKind.COMMON, true},
                {UpstreamRepoKind.UNAUTHORIZED, true},
                {UpstreamRepoKind.UNKNOWN, true},
                {UpstreamRepoKind.NOT_AVAILABLE, true},
                {UpstreamRepoKind.NOT_ARTIFACTORY, false},
                {UpstreamRepoKind.NOT_CONFIGURED, false}
        };
    }

    @DataProvider
    private Object[][] booleanProvider() {
        return new Object[][] {
                {true}, {false}
        };
    }

    private RealRepo mockRealRepo(boolean isLocal, boolean isCache, String repoName,
            org.artifactory.descriptor.repo.RepoType repoType) {
        RealRepoDescriptor repoDescriptor = mock(RealRepoDescriptor.class);
        when(repoDescriptor.getType()).thenReturn(repoType);
        when(repoDescriptor.getKey()).thenReturn(repoName);
        RealRepo repo = mock(RealRepo.class);
        when(repo.isLocal()).thenReturn(isLocal);
        when(repo.isCache()).thenReturn(isCache);
        when(repo.getDescriptor()).thenReturn(repoDescriptor);
        when(repo.getKey()).thenReturn(repoName);
        return repo;
    }

    @DataProvider
    private Object[][] testExistWithVirtualRepoProvider() {
        return new Object[][] {
                {true}, {false}
        };
    }
}