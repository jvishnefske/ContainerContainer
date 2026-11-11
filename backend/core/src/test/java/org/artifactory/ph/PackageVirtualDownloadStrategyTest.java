package org.artifactory.ph;

import org.apache.http.HttpStatus;
import com.jfrog.ph.acl.exception.PackageException;
import com.jfrog.ph.acl.resource.PackageUnfoundedResource;
import org.artifactory.fs.RepoResource;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.virtual.VirtualRepo;
import org.artifactory.request.InternalRequestContext;
import org.artifactory.resource.UnfoundRepoResource;
import org.mockito.Mock;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

@Test
public class PackageVirtualDownloadStrategyTest {

    private PackageVirtualDownloadStrategy packageVirtualDownloadStrategy;

    @Mock
    private VirtualRepo virtualRepo;
    @Mock
    private PackageVirtualResolutionService packageVirtualAdapter;
    private AutoCloseable mocks;

    @BeforeMethod
    public void setUp() {
        mocks = openMocks(this);
        packageVirtualDownloadStrategy = new PackageVirtualDownloadStrategy(virtualRepo, packageVirtualAdapter);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    public void testGetInfoUnFound() throws PackageException {

        when(packageVirtualAdapter.getItemInfoFromVirtual(any(), any())).thenReturn(
                new PackageUnfoundedResource("repo", "path",
                        HttpStatus.SC_NOT_FOUND, "not found"));

        InternalRequestContext context = mock(InternalRequestContext.class);

        RepoResource result = packageVirtualDownloadStrategy.getInfo(context);
        assertTrue(result instanceof UnfoundRepoResource);
        assertEquals(((UnfoundRepoResource) result).getStatusCode(), HttpStatus.SC_NOT_FOUND);
        assertEquals(result.getRepoPath(), RepoPathFactory.create("repo", "path"));
    }

    @Test
    public void testGetInfo() throws PackageException {

        RepoResource resource = mock(RepoResource.class);
        when(packageVirtualAdapter.getItemInfoFromVirtual(any(), any())).thenReturn(resource);

        InternalRequestContext context = mock(InternalRequestContext.class);

        RepoResource result = packageVirtualDownloadStrategy.getInfo(context);
        assertEquals(result, result);
    }

}