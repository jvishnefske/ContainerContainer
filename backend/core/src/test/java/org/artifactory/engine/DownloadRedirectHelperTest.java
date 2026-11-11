package org.artifactory.engine;

import org.artifactory.aol.AolService;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.fs.RepoResource;
import org.artifactory.repo.Repo;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.jfrog.storage.common.StorageUnit;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

@Test
public class DownloadRedirectHelperTest extends ArtifactoryHomeBoundTest {

    @Mock
    CentralConfigService centralConfig;
    private AutoCloseable mocks;

    @BeforeClass
    public void init() {
        mocks = MockitoAnnotations.openMocks(this);
        AolService aolService = mock(AolService.class);
        ArtifactoryContext context = Mockito.mock(ArtifactoryContext.class);
        when(aolService.isAol()).thenReturn(true);
        when(context.beanForType(AolService.class)).thenReturn(aolService);
        ArtifactoryContextThreadBinder.bind(context);
    }

    @AfterClass
    public void unbind() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    public void isRedirectEnableForRepoType() {
        final DownloadRedirectHelper downloadRedirectHelper = new DownloadRedirectHelper(centralConfig);
        setStringSystemProperty(ConstantValues.listOfReposAllowedSendRedirectUrl, "NimModel");
        Repo responseRepo = Mockito.mock(Repo.class);
        RepoDescriptor repoDescriptor = Mockito.mock(RepoDescriptor.class);
        when(responseRepo.getDescriptor()).thenReturn(repoDescriptor);
        when(repoDescriptor.getType()).thenReturn(RepoType.NimModel);
        assertTrue(downloadRedirectHelper.isRedirectEnableForThisRepoType(responseRepo));
    }

    public void isCdnForcedForThisRepoType() {
        final DownloadRedirectHelper downloadRedirectHelper = new DownloadRedirectHelper(centralConfig);
        setStringSystemProperty(ConstantValues.listOfReposForcedCdnRedirectUrl, "NimModel");
        Repo responseRepo = Mockito.mock(Repo.class);
        RepoDescriptor repoDescriptor = Mockito.mock(RepoDescriptor.class);
        when(responseRepo.getDescriptor()).thenReturn(repoDescriptor);
        when(repoDescriptor.getType()).thenReturn(RepoType.NimModel);
        assertTrue(downloadRedirectHelper.isCdnForcedForThisRepoType(responseRepo));
    }

    public void testThresholdWithoutChangingConstantValue() {
        final DownloadRedirectHelper downloadRedirectHelper = new DownloadRedirectHelper(centralConfig);
        RepoResource resource = mock(RepoResource.class);
        when(resource.getRepoPath()).thenReturn((RepoPathFactory.create("foo", "bar")));

        when(resource.getSize()).thenReturn(200L * 1024L);
        assertTrue(downloadRedirectHelper.isArtifactGreaterEqualsRedirectThreshold(resource));

        when(resource.getSize()).thenReturn(10L);
        assertFalse(downloadRedirectHelper.isArtifactGreaterEqualsRedirectThreshold(resource));

        when(resource.getSize()).thenReturn((long) StorageUnit.MB.toBytes(2));
        assertTrue(downloadRedirectHelper.isArtifactGreaterEqualsRedirectThreshold(resource));


    }

    public void testThresholdSmallerThanConstantValue() {
        final DownloadRedirectHelper downloadRedirectHelper = new DownloadRedirectHelper(centralConfig);

        setStringSystemProperty(ConstantValues.cloudBinaryProviderRedirectThresholdInBytes, "100");
        RepoResource resource = mock(RepoResource.class);
        when(resource.getRepoPath()).thenReturn((RepoPathFactory.create("foo", "bar")));

        when(resource.getSize()).thenReturn((long) StorageUnit.MB.toBytes(1));
        assertTrue(downloadRedirectHelper.isArtifactGreaterEqualsRedirectThreshold(resource));

        when(resource.getSize()).thenReturn(50L);
        assertFalse(downloadRedirectHelper.isArtifactGreaterEqualsRedirectThreshold(resource));

    }

}