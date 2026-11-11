
package org.artifactory.repo.virtual;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.LayoutsCoreAddon;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.VirtualRepoDescriptor;
import org.artifactory.fs.RepoResource;
import org.artifactory.mime.MimeType;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.model.fs.FileInfoImpl;
import org.artifactory.repo.RealRepo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.request.ArtifactoryRequest;
import org.artifactory.request.DownloadRequestContext;
import org.artifactory.resource.FileResource;
import org.artifactory.resource.UnfoundRepoResource;
import org.artifactory.resource.UnfoundRepoResourceReason;
import org.artifactory.spring.InternalArtifactoryContext;
import org.artifactory.util.RepoLayoutUtils;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class OldVirtualDownloadStrategyTest {

    @Mock
    private DownloadRequestContext context;
    @Mock
    private InternalArtifactoryContext internalArtifactoryContext;
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private CentralConfigService centralConfigService;
    @Mock
    private VirtualRepo virtualRepo;
    @Mock
    private PathTranslator pathTranslator;
    @Mock
    private VirtualRepoDescriptor virtualRepoDescriptor;
    @Mock
    private InternalRepositoryService internalRepositoryService;
    @Mock
    private LayoutsCoreAddon layoutsCoreAddon;
    @Mock
    private ArtifactoryRequest artifactoryRequest;
    @Mock
    private RepoResource mockResource;

    private RepoPath repoPath1, repoPath2;
    private RealRepo repo1, repo2;
    private FileInfoImpl fileInfo1, fileInfo2;
    private AutoCloseable autoCloseable;

    @BeforeClass
    public void setup() {
        autoCloseable = MockitoAnnotations.openMocks(this);
        repoPath1 = new RepoPathImpl("repo1", "/mysql/connector-java/" +
                "5.1.7-SNAPSHOT/connector-java-5.1.7-20190219.131101-4.jar");
        repoPath2 = new RepoPathImpl("repo2", "/mysql/connector-java/" +
                "5.1.7-SNAPSHOT/connector-java-5.1.7-20190225.231101-5.jar");
    }

    @AfterClass
    public void tearDown() throws Exception {
        autoCloseable.close();
    }

    @BeforeMethod
    public void beforeMethod() {
        ArtifactoryContextThreadBinder.bind(internalArtifactoryContext);
        when(internalArtifactoryContext.getCentralConfig())
                .thenReturn(centralConfigService);
        when(internalArtifactoryContext.beanForType(InternalRepositoryService.class))
                .thenReturn(internalRepositoryService);
        when(internalArtifactoryContext.beanForType(AddonsManager.class))
                .thenReturn(addonsManager);
        when(internalArtifactoryContext.beanForType(PathTranslator.class))
                .thenReturn(pathTranslator);
        when(addonsManager.addonByType(LayoutsCoreAddon.class))
                .thenReturn(layoutsCoreAddon);
        when(virtualRepo.getDescriptor())
                .thenReturn(virtualRepoDescriptor);
        when(virtualRepo.getConfigService())
                .thenReturn(centralConfigService);
        when(pathTranslator.translateRepoRequestContext(any(), any(), any()))
                .thenReturn(context);
        when(virtualRepoDescriptor.getRepoLayout())
                .thenReturn(RepoLayoutUtils.MAVEN_2_DEFAULT);
        when(context.getRequest())
                .thenReturn(artifactoryRequest);
        when(layoutsCoreAddon.translateArtifactPath(any(), any(), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(2));

        repo1 = mock(RealRepo.class);
        repo2 = mock(RealRepo.class);

        fileInfo1 = new FileInfoImpl(repoPath1);
        fileInfo1.setMimeType(MimeType.javaArchive);
        fileInfo2 = new FileInfoImpl(repoPath2);
        fileInfo2.setMimeType(MimeType.javaArchive);
        when(repo1.getKey())
                .thenReturn(repoPath1.getRepoKey());
        when(repo2.getKey())
                .thenReturn(repoPath2.getRepoKey());
        when(repo1.isHandleSnapshots())
                .thenReturn(true);
        when(repo2.isHandleSnapshots())
                .thenReturn(true);
        when(repo1.getDescriptor())
                .thenReturn(createLocalDescriptor(repoPath1.getRepoKey()));
        when(repo2.getDescriptor())
                .thenReturn(createLocalDescriptor(repoPath2.getRepoKey()));
    }

    @AfterMethod
    public void afterMethod() {
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    public void processUniqueSnapshot() {

        ArtifactoryVirtualDownloadStrategy tester = new ArtifactoryVirtualDownloadStrategy(virtualRepo);

        when(context.getResourcePath())
                .thenReturn(repoPath1.getPath());
        when(artifactoryRequest.getRepoKey())
                .thenReturn(repoPath1.getRepoKey());

        when(repo1.getInfo(context))
                .thenReturn(new FileResource(fileInfo1));
        when(repo2.getInfo(context))
                .thenReturn(new FileResource(fileInfo2));
        RepoResource repoResource = tester.processSnapshot(context, repoPath1, Arrays.asList(repo1, repo2));
        assertEquals(repoResource.getRepoPath().getRepoKey(), repoPath1.getRepoKey());
        verify(repo2, times(0)).getInfo(any());

    }

    @Test
    public void processUniqueSnapshotNotFoundFirst() {

        ArtifactoryVirtualDownloadStrategy tester = new ArtifactoryVirtualDownloadStrategy(virtualRepo);

        when(context.getResourcePath())
                .thenReturn(repoPath2.getPath());
        when(artifactoryRequest.getRepoKey())
                .thenReturn(repoPath2.getRepoKey());
        when(repo1.getInfo(context))
                .thenReturn(new UnfoundRepoResource(repoPath1, "",
                        UnfoundRepoResourceReason.Reason.PROPERTY_MISMATCH));
        when(repo2.getInfo(context))
                .thenReturn(new FileResource(fileInfo2));
        RepoResource repoResource = tester.processSnapshot(context, repoPath2, Arrays.asList(repo1, repo2));

        assertEquals(repoResource.getRepoPath().getRepoKey(), repoPath2.getRepoKey());
        verify(repo2, times(1)).getInfo(any());

    }

    @Test
    public void processUniqueSnapshotWithPriorityResolution() {

        ArtifactoryVirtualDownloadStrategy tester = new ArtifactoryVirtualDownloadStrategy(virtualRepo);

        when(context.getResourcePath())
                .thenReturn(repoPath2.getPath());
        when(artifactoryRequest.getRepoKey())
                .thenReturn(repoPath2.getRepoKey());
        when(repo1.getInfo(context))
                .thenReturn(new UnfoundRepoResource(repoPath1, "",
                        UnfoundRepoResourceReason.Reason.PROPERTY_MISMATCH));
        when(repo2.getInfo(context))
                .thenReturn(new FileResource(fileInfo2));
        when(repo2.isPriorityResolution()).thenReturn(true);
        RepoResource repoResource = tester.processSnapshot(context, repoPath2, Arrays.asList(repo1, repo2));

        assertEquals(repoResource.getRepoPath().getRepoKey(), repoPath2.getRepoKey());
        verify(repo2, times(1)).getInfo(any());
    }

    @Test
    public void processUniqueSnapshotWithPriorityResolutionGettingLatest() {

        ArtifactoryVirtualDownloadStrategy tester = new ArtifactoryVirtualDownloadStrategy(virtualRepo);

        when(context.getResourcePath())
                .thenReturn(repoPath2.getPath());
        when(artifactoryRequest.getRepoKey())
                .thenReturn(repoPath2.getRepoKey());
        when(repo1.getInfo(context))
                .thenReturn(new UnfoundRepoResource(repoPath1, "",
                        UnfoundRepoResourceReason.Reason.PROPERTY_MISMATCH));
        when(repo2.getInfo(context))
                .thenReturn(new FileResource(fileInfo2));
        when(repo1.isPriorityResolution()).thenReturn(true);
        when(repo2.isPriorityResolution()).thenReturn(true);
        RepoResource repoResource = tester.processSnapshot(context, repoPath2, Arrays.asList(repo1, repo2));

        assertEquals(repoResource.getRepoPath().getRepoKey(), repoPath2.getRepoKey());
        verify(repo2, times(1)).getInfo(any());
    }

    @Test
    public void processSnapshotWithNullResponseRepoPath() {
        final ArtifactoryVirtualDownloadStrategy tester = new ArtifactoryVirtualDownloadStrategy(virtualRepo);

        when(context.getResourcePath()).thenReturn(repoPath1.getPath());
        when(artifactoryRequest.getRepoKey()).thenReturn(repoPath1.getRepoKey());
        when(mockResource.isFound()).thenReturn(true);
        when(mockResource.getResponseRepoPath()).thenReturn(null);
        when(mockResource.getRepoPath()).thenReturn(repoPath1);
        when(mockResource.isExactQueryMatch()).thenReturn(true);
        when(mockResource.getLastModified()).thenReturn(System.currentTimeMillis());
        when(repo1.getInfo(context)).thenReturn(mockResource);

        final RepoResource repoResource = tester.processSnapshot(context, repoPath1, List.of(repo1));

        assertEquals(repoResource.getRepoPath().getRepoKey(), repoPath1.getRepoKey());
        assertNull(repoResource.getResponseRepoPath());
        verify(repo1, times(1)).getInfo(any());
    }

    private LocalRepoDescriptor createLocalDescriptor(String repoKey) {
        LocalRepoDescriptor descriptor = new LocalRepoDescriptor();
        descriptor.setKey(repoKey);
        descriptor.setRepoLayout(RepoLayoutUtils.MAVEN_2_DEFAULT);
        return descriptor;
    }

}

