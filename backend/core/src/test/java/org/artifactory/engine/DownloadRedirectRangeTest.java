package org.artifactory.engine;

import org.apache.commons.io.FileUtils;
import org.artifactory.api.request.ArtifactoryResponse;
import org.artifactory.api.request.ControlledCacheRedirectService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.fs.RepoResource;
import org.artifactory.repo.Repo;
import org.artifactory.repo.RepoPath;
import org.artifactory.request.InternalRequestContext;
import org.artifactory.request.Request;
import org.artifactory.resource.RepoResourceInfo;
import org.artifactory.resource.ResourceStreamHandle;
import org.artifactory.test.ArtifactoryHomeStub;
import org.artifactory.traffic.TrafficService;
import org.artifactory.traffic.entry.TransferEntry;
import org.jfrog.storage.binstore.ifc.RedirectType;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;

import static com.google.common.io.Files.createTempDir;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

public class DownloadRedirectRangeTest {

    private ArtifactoryHomeStub artifactoryHomeStub;
    private File home;

    @BeforeMethod
    private void setup() {
        home = createTempDir();
        artifactoryHomeStub = new ArtifactoryHomeStub(home.getAbsolutePath());
        artifactoryHomeStub.initArtifactorySystemProperties();
        ArtifactoryHomeStub.bind(artifactoryHomeStub);
    }

    @AfterMethod
    public void tearDown() {
        FileUtils.deleteQuietly(home);
        ArtifactoryHomeStub.unbind();
    }

    @Test(dataProvider = "ranges")
    public void redirectSmoke(boolean useBinarySizeForTrafficLog, String rangeHeader, long entityLength,
            RedirectType redirectType, long expectedLoggedLength) throws Exception {
        artifactoryHomeStub.getArtifactoryProperties()
                .setProperty(ConstantValues.trafficIgnoreRangeHeaderWhenCalcBinarySizeForRedirect.getPropertyName(),
                        Boolean.toString(useBinarySizeForTrafficLog));

        TrafficService trafficService = mock(TrafficService.class);
        ArgumentCaptor<TransferEntry> transferEntryCaptor = ArgumentCaptor.forClass(TransferEntry.class);

        SecurityService securityService = mock(SecurityService.class);

        ControlledCacheRedirectService controlledCacheRedirectService = mock(ControlledCacheRedirectService.class);
        when(controlledCacheRedirectService.shouldRedirect(any(), any(), any())).thenReturn(false);

        DownloadServiceImpl downloadService = new DownloadServiceImpl();
        downloadService.trafficService = trafficService;
        downloadService.securityService = securityService;
        downloadService.controlledCacheRedirectService = controlledCacheRedirectService;

        ArtifactoryResponse response = mock(ArtifactoryResponse.class);
        InternalRequestContext requestContext = mock(InternalRequestContext.class);

        RepoResource resource = mock(RepoResource.class);
        when(resource.getSize()).thenReturn(entityLength);
        when(resource.getLastModified()).thenReturn(0L);

        RepoResourceInfo repoResourceInfo = mock(RepoResourceInfo.class);
        when(repoResourceInfo.getSha1()).thenReturn("sha1");
        when(resource.getInfo()).thenReturn(repoResourceInfo);

        RepoPath responseRepoPath = mock(RepoPath.class);
        ResourceStreamHandle handle = mock(ResourceStreamHandle.class);

        Request request = mock(Request.class);
        when(request.getHeader("Range")).thenReturn(rangeHeader);
        when(requestContext.getRequest()).thenReturn(request);

        Repo responseRepo = mock(Repo.class);

        downloadService.sendStreamOrRedirect(requestContext, response, resource, responseRepoPath, handle,
                "signedUrl", request, responseRepo, redirectType, true);

        verify(trafficService).handleTrafficEntry(transferEntryCaptor.capture());
        TransferEntry transferEntry = transferEntryCaptor.getValue();
        long actualLoggedLength = transferEntry.getContentLength();

        assertEquals(actualLoggedLength, expectedLoggedLength);
    }

    @DataProvider
    public static Object[][] ranges() {
        return new Object[][] {
                //if useBinarySizeForTrafficLog true then range header ignored
                {true, "Range: bytes=0-100", 10000, RedirectType.S3, 10000},
                {true, "Range: bytes=0-100", 10000, RedirectType.CLOUD_FRONT, 10000},
                {true, "Range: bytes=0-100", 10000, RedirectType.DIRECT, 10000},

                //if useBinarySizeForTrafficLog false then range header not ignored
                {false, "Range: bytes=0-100", 10000, RedirectType.S3, 101},
                {false, "Range: bytes=0-100", 10000, RedirectType.CLOUD_FRONT, 101},
                {false, "Range: bytes=0-100", 10000, RedirectType.DIRECT, 101},

                // if range header invalid then full binary size logged
                {false, "Range: bytes=16384-z", 10000, RedirectType.S3, 10000},
                {false, "Range: bytes=16384-z", 10000, RedirectType.CLOUD_FRONT, 10000},
                {false, "Range: bytes=16384-z", 10000, RedirectType.DIRECT, 10000},

                // if range header out of binary size then full binary size logged
                {false, "Range: bytes=30000-40000", 10000, RedirectType.S3, 10000},
                {false, "Range: bytes=30000-40000", 10000, RedirectType.CLOUD_FRONT, 10000},
                {false, "Range: bytes=30000-40000", 10000, RedirectType.DIRECT, 10000},

                // multi range support
                {false, "Range: bytes=10-100, 150-200", 10000, RedirectType.S3, 10000},
                {false, "Range: bytes=10-100, 150-200", 10000, RedirectType.CLOUD_FRONT, 339},
                {false, "Range: bytes=10-100, 150-200", 10000, RedirectType.DIRECT, 339},

                // if no range header then full binary size logged
                {false, null, 10000, RedirectType.S3, 10000},
                {false, null, 10000, RedirectType.CLOUD_FRONT, 10000},
                {false, null, 10000, RedirectType.DIRECT, 10000},
        };
    }

}
