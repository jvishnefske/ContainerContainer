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

package org.artifactory.repo.cache.expirable;

import com.google.common.collect.ImmutableMap;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.HaAddon;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.descriptor.repo.HttpRepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.factory.InfoFactory;
import org.artifactory.factory.InfoFactoryHolder;
import org.artifactory.fs.MutableItemInfo;
import org.artifactory.fs.RepoResource;
import org.artifactory.mime.MavenNaming;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.repo.ChecksumPolicyType;
import org.artifactory.repo.HttpRepo;
import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.repo.RemoteRepo;
import org.artifactory.repo.db.DbCacheRepo;
import org.artifactory.resource.FileResource;
import org.artifactory.resource.MetadataResource;
import org.artifactory.resource.MutableRepoResourceInfo;
import org.artifactory.sapi.interceptor.context.DeleteContext;
import org.artifactory.spring.ArtifactoryApplicationContext;
import org.artifactory.storage.fs.MutableVfsItem;
import org.artifactory.storage.fs.VfsItemProvider;
import org.artifactory.storage.fs.VfsItemProviderFactory;
import org.artifactory.storage.fs.VfsMutableFolderProvider;
import org.artifactory.storage.fs.service.FileService;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.util.RepoLayoutUtils;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * @author Yoav Landman
 */
public class DbCacheRepoTest extends ArtifactoryHomeBoundTest {
    public static final String REPO_KEY = "repox";
    protected InfoFactory factory = InfoFactoryHolder.get();
    @Mock
    private ArtifactoryApplicationContext context;
    @Mock
    private CentralConfigService configService;
    @Mock
    private VfsItemProviderFactory vfsItemProviderFactory;

    private AutoCloseable mocks;

    @BeforeMethod
    public void beforeMethod() {
        mocks = MockitoAnnotations.openMocks(this);

        CacheExpiryImpl cacheExpiry = new CacheExpiryImpl();
        Mockito.when(context.beanForType(CacheExpiry.class)).thenReturn(cacheExpiry);
        cacheExpiry.setApplicationContext(context);
        Mockito.when(context.beansForType(CacheExpiryChecker.class))
                .thenReturn(ImmutableMap.of(
                        "non-unique", new NonUniqueSnapshotArtifactExpirableOrOverridable(),
                        "maven", new MavenIndexChecker(),
                        "maven-metadata", new MavenMetadataChecker()));
        cacheExpiry.init();

        ArtifactoryContextThreadBinder.bind(context);
    }

    @AfterMethod
    public void afterMethod() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @Test
    public void testExpiry() throws InterruptedException {
        FileResource releaseRes = new FileResource(factory.createFileInfo(
                factory.createRepoPath(REPO_KEY, "g1/g2/g3/a/v1/a.jar")));
        releaseRes.getInfo().setLastModified(0);
        FileResource snapRes = new FileResource(factory.createFileInfo(
                factory.createRepoPath(REPO_KEY, "g1/g2/g3/a/v1-SNAPSHOT/a-v1-SNAPSHOT.pom")));
        snapRes.getInfo().setLastModified(0);
        FileResource uniqueSnapRes = new FileResource(factory.createFileInfo(
                factory.createRepoPath(REPO_KEY, "g1/g2/g3/a/v1-SNAPSHOT/a-v1-20081214.090217-4.pom")));
        uniqueSnapRes.getInfo().setLastModified(0);
        FileResource nonSnapRes = new FileResource(factory.createFileInfo(
                factory.createRepoPath(REPO_KEY, "g1/g2/g3/a/v1/aSNAPSHOT.pom")));
        nonSnapRes.getInfo().setLastModified(0);
        MetadataResource relMdRes = new MetadataResource(
                InternalRepoPathFactory.create(REPO_KEY, "g1/g2/g3/a/v1/maven-metadata.xml"));
        ((MutableRepoResourceInfo) relMdRes.getInfo()).setLastModified(0);
        FileResource snapMdRes = new FileResource(factory.createFileInfo(
                factory.createRepoPath(REPO_KEY, "g1/g2/g3/a/v1-SNAPSHOT/maven-metadata.xml")));
        snapMdRes.getInfo().setLastModified(0);
        FileResource nonsnapMdRes = new FileResource(factory.createFileInfo(
                factory.createRepoPath(REPO_KEY, "g1/g2/g3/a/v1/maven-metadata.metadata")));
        nonsnapMdRes.getInfo().setLastModified(0);
        FileResource indexRes = new FileResource(factory.createFileInfo(
                factory.createRepoPath(REPO_KEY, MavenNaming.NEXUS_INDEX_GZ)));
        indexRes.getInfo().setLastModified(0);

        // Sleep so we will have difference with the last updated and the current time
        Thread.sleep(100);

        RemoteRepo<?, ?> remoteRepo = createRemoteRepoMock(0L);

        DbCacheRepo cacheRepo = new DbCacheRepo(remoteRepo, null, configService);

        assertFalse(isExpired(cacheRepo, releaseRes));
        assertTrue(isExpired(cacheRepo, snapRes));
        assertFalse(isExpired(cacheRepo, uniqueSnapRes));
        assertFalse(isExpired(cacheRepo, nonSnapRes));
        assertTrue(isExpired(cacheRepo, relMdRes));
        assertTrue(isExpired(cacheRepo, snapMdRes));
        assertFalse(isExpired(cacheRepo, nonsnapMdRes));
        assertTrue(isExpired(cacheRepo, indexRes));

        //offline case
        Mockito.when(remoteRepo.isOffline()).thenReturn(true);
        assertFalse(isExpired(cacheRepo, snapRes));
        assertFalse(isExpired(cacheRepo, relMdRes));
        assertFalse(isExpired(cacheRepo, snapMdRes));
        assertFalse(isExpired(cacheRepo, indexRes));

        //file should not be expired due to cache period time
        remoteRepo = createRemoteRepoMock(10L);
        cacheRepo = new DbCacheRepo(remoteRepo, null, configService);

        assertFalse(isExpired(cacheRepo, indexRes));
    }

    @Test
    public void testUndeploy() {
        VfsMutableFolderProvider vfsMutableFolderProvider = Mockito.mock(VfsMutableFolderProvider.class);
        when(vfsItemProviderFactory.createFolderProvider(any(), any(), any(), any()))
                .thenReturn(vfsMutableFolderProvider);
        VfsItemProvider vfsItemProvider = Mockito.mock(VfsItemProvider.class);
        MutableVfsItem<MutableItemInfo> mutableFsItem = Mockito.mock(MutableVfsItem.class);
        when(vfsItemProvider.getMutableFsItem()).thenReturn(mutableFsItem);
        when(vfsItemProviderFactory.createItemProvider(any(), any(), any(), any())).thenReturn(vfsItemProvider);

        when(context.beanForType(VfsItemProviderFactory.class)).thenReturn(vfsItemProviderFactory);
        AddonsManager addonsManager = Mockito.mock(AddonsManager.class);
        when(context.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        when(addonsManager.addonByType(HaAddon.class)).thenReturn(Mockito.mock(HaAddon.class));
        when(context.beanForType(FileService.class)).thenReturn(Mockito.mock(FileService.class));

        RemoteRepo<?, ?> remoteRepo = createRemoteRepoMock(0L);

        DbCacheRepo cacheRepo = new DbCacheRepo(remoteRepo, null, configService);
        cacheRepo.init(false);
        DeleteContext deleteContext = new DeleteContext(new RepoPathImpl("repoKey", "path"));
        cacheRepo.undeploy(deleteContext);

        verify(mutableFsItem, times(1)).delete(any(), any());
    }

    public boolean isExpired(DbCacheRepo cacheRepo, RepoResource releaseRes) {
        return Boolean.TRUE.equals(ReflectionTestUtils.invokeMethod(cacheRepo, "isExpired", releaseRes));
    }

    private RemoteRepo<?, ?> createRemoteRepoMock(long expiry) {
        HttpRepo remoteRepo = Mockito.mock(HttpRepo.class);
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create(REPO_KEY, RepoType.Maven);
        remoteRepoConfig.getRepoTypeConfig().setChecksumPolicyType(ChecksumPolicyType.FAIL);
        remoteRepoConfig.getBaseConfig().setRepoLayoutRef(RepoLayoutUtils.MAVEN_2_DEFAULT.getName());
        Mockito.when(remoteRepo.getRepoConfig()).thenReturn(remoteRepoConfig);
        Mockito.when(remoteRepo.getDescription()).thenReturn("desc");
        Mockito.when(remoteRepo.getKey()).thenReturn(REPO_KEY);
        Mockito.when(remoteRepo.getRetrievalCachePeriodSecs()).thenReturn(expiry);
        Mockito.when(remoteRepo.getUrl()).thenReturn("http://jfrog");

        HttpRepoDescriptor httpRepoDescriptor = new HttpRepoDescriptor();
        Mockito.when(remoteRepo.getDescriptor()).thenReturn(httpRepoDescriptor);

        httpRepoDescriptor.setKey(REPO_KEY);
        return remoteRepo;
    }
}