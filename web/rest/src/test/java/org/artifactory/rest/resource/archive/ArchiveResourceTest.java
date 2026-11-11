package org.artifactory.rest.resource.archive;

import com.jfrog.build.acl.bridge.BuildFacade;
import com.jfrog.build.acl.model.api.BuildArtifactsMapping;
import com.jfrog.build.acl.model.api.BuildArtifactsRequest;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import org.apache.http.HttpStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.rest.RestAddon;
import org.artifactory.api.repo.exception.BlackedOutException;
import org.artifactory.api.security.SecurityService;
import org.artifactory.download.FolderDownloadException;
import org.artifactory.model.RepoConfig;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.exception.http.NotFoundException;
import org.artifactory.rest.util.ErrorMessagesConstants;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.traffic.TrafficEntry;
import org.artifactory.traffic.TrafficService;
import org.glassfish.jersey.server.CloseableService;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

public class ArchiveResourceTest extends ArtifactoryHomeBoundTest {

    @Test
    public void smoke() throws IOException {
        AddonsManager addonsManager = mock(AddonsManager.class);
        RestAddon restAddon = mock(RestAddon.class);
        File file = mock(File.class);
        when(file.length()).thenReturn(48L);
        when(restAddon.getBuildArtifactsArchive(any())).thenReturn(file);
        when(addonsManager.addonByType(RestAddon.class)).thenReturn(restAddon);
        CloseableService closeableService = mock(CloseableService.class);
        ArchiveResource resource = new ArchiveResourceForTest();
        BuildFacade buildFacade = mock(BuildFacade.class);
        when(buildFacade.getPreferredBuildRepo(any(), any())).thenReturn("build-repo");
        TrafficService trafficService = mock(TrafficService.class);
        resource.setAddonsManager(addonsManager);
        resource.setCloseableService(closeableService);
        resource.setBuildFacade(buildFacade);
        resource.setTrafficService(trafficService);

        BuildArtifactsRequest request = new BuildArtifactsRequest();
        request.setBuildName("build-name");
        request.setBuildNumber("build-number");
        request.setArchiveType("zip");

        Response response = resource.getBuildArtifactsArchive(request);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ((StreamingOutput) response.getEntity()).write(baos);

        ArgumentCaptor<TrafficEntry> trafficEntryV2Captor = ArgumentCaptor.forClass(TrafficEntry.class);
        verify(trafficService, times(1)).handleTrafficEntry(trafficEntryV2Captor.capture());
        TrafficEntry captured = trafficEntryV2Captor.getValue();

        assertEquals(captured.getAction(), TrafficEntry.TrafficAction.DOWNLOAD);
        assertEquals(captured.getActor(), TrafficEntry.TrafficActor.USER);
        assertEquals(captured.getRedirectTarget(), TrafficEntry.TrafficTarget.DIRECT);
        assertEquals(captured.getUserAddress(), "");
        assertEquals(captured.getRepoPath(), "build-repo:build-name");
        assertEquals(captured.getSize(), 48);
        assertEquals(captured.getRequestedSize(), 48);
        assertEquals(captured.getTransferredSize(), 9);
    }


    @Test(
            expectedExceptions = BadRequestException.class,
            expectedExceptionsMessageRegExp = ErrorMessagesConstants.BLANK_BUILD_NAME_MESSAGE
    )
    public void buildArchiveShouldFailIfBuildNameIsEmpty() throws IOException {
        ArchiveResource resource = new ArchiveResourceForTest();
        BuildArtifactsRequest request = new BuildArtifactsRequest();

        resource.getBuildArtifactsArchive(request);
    }

    @Test(
            expectedExceptions = BadRequestException.class,
            expectedExceptionsMessageRegExp = ErrorMessagesConstants.BLANK_BUILD_NUMBER_AND_STATUS_MESSAGE
    )
    public void buildArchiveShouldFailIfBuildNumberAndStatusAreEmpty() throws IOException {
        ArchiveResource resource = new ArchiveResourceForTest();
        BuildArtifactsRequest request = new BuildArtifactsRequest();
        request.setBuildName("build-name");

        resource.getBuildArtifactsArchive(request);
    }

    @Test(
            expectedExceptions = BadRequestException.class,
            expectedExceptionsMessageRegExp = ErrorMessagesConstants.BUILD_NUMBER_AND_STATUS_BOTH_PROVIDED_MESSAGE
    )
    public void buildArchiveShouldFailIfBuildNumberAndStatusAreBothProvided() throws IOException {
        ArchiveResource resource = new ArchiveResourceForTest();
        BuildArtifactsRequest request = new BuildArtifactsRequest();
        request.setBuildName("build-name");
        request.setBuildNumber("build-number");
        request.setBuildStatus("build-status");

        resource.getBuildArtifactsArchive(request);
    }

    @Test(
            expectedExceptions = BadRequestException.class,
            expectedExceptionsMessageRegExp = ErrorMessagesConstants.ARCHIVE_TYPE_EMPTY_MESSAGE
    )
    public void buildArchiveShouldFailIfArchiveTypeIsEmpty() throws IOException {
        ArchiveResource resource = new ArchiveResourceForTest();
        BuildArtifactsRequest request = new BuildArtifactsRequest();
        request.setBuildName("build-name");
        request.setBuildNumber("build-number");

        resource.getBuildArtifactsArchive(request);
    }

    @Test(
            expectedExceptions = BadRequestException.class,
            expectedExceptionsMessageRegExp = ErrorMessagesConstants.INVALID_MAPPING_OUTPUT_MESSAGE
    )
    public void buildArchiveShouldFailIfMappingsIsInvalid() throws IOException {
        ArchiveResource resource = new ArchiveResourceForTest();

        BuildArtifactsRequest request = new BuildArtifactsRequest();
        request.setBuildName("build-name");
        request.setBuildNumber("build-number");
        request.setArchiveType("zip");
        List<BuildArtifactsMapping> mappings =
                Collections.singletonList(BuildArtifactsMapping.builder()
                        .input(".*")
                        .output("../path")
                        .build());
        request.setMappings(mappings);

        resource.getBuildArtifactsArchive(request);
    }

    @Test(
            expectedExceptions = NotFoundException.class,
            expectedExceptionsMessageRegExp =
                    "Could not find any build artifacts for build .*"

    )
    public void buildArchiveShouldFailIfArchiveFileIsNull() throws IOException {
        ArchiveResource resource = new ArchiveResourceForTest();

        BuildArtifactsRequest request = new BuildArtifactsRequest();
        request.setBuildName("build-name");
        request.setBuildNumber("build-number");
        request.setArchiveType("zip");

        AddonsManager addonsManager = mock(AddonsManager.class);
        RestAddon restAddon = mock(RestAddon.class);
        when(restAddon.getBuildArtifactsArchive(any())).thenReturn(null);
        when(addonsManager.addonByType(RestAddon.class)).thenReturn(restAddon);
        resource.setAddonsManager(addonsManager);

        resource.getBuildArtifactsArchive(request);
    }

    @Test(
            expectedExceptions = NotFoundException.class,
            expectedExceptionsMessageRegExp = ErrorMessagesConstants.BUILD_ARTIFACTS_ARCHIVE_FAILED

    )
    public void buildArchiveShouldThrowNotFoundExceptionWhenIOExceptionIsCaught() throws IOException {
        ArchiveResource resource = new ArchiveResourceForTest();

        BuildArtifactsRequest request = new BuildArtifactsRequest();
        request.setBuildName("build-name");
        request.setBuildNumber("build-number");
        request.setArchiveType("zip");

        AddonsManager addonsManager = mock(AddonsManager.class);
        RestAddon restAddon = mock(RestAddon.class);
        when(restAddon.getBuildArtifactsArchive(any())).thenThrow(new IOException("Archive has failed."));
        when(addonsManager.addonByType(RestAddon.class)).thenReturn(restAddon);
        resource.setAddonsManager(addonsManager);

        resource.getBuildArtifactsArchive(request);
    }

    @Test
    public void testDownloadFolderOrRepoBlackedOutExceptionHandle() throws BlackedOutException, IOException {
        ArchiveResource resource = new ArchiveResourceForTest();
        resource.setSecurityService(mock(SecurityService.class));

        AddonsManager addonsManager = mock(AddonsManager.class);
        RestAddon restAddon = mock(RestAddon.class);

        RepoConfig repoConfig = mock(RepoConfig.class);
        when(repoConfig.getKey()).thenReturn("repoKey");
        when(restAddon.downloadFolderOrRepo(any(), any(), anyBoolean())).thenThrow(
                new BlackedOutException(repoConfig, null));
        when(addonsManager.addonByType(RestAddon.class)).thenReturn(restAddon);
        resource.setAddonsManager(addonsManager);

        Response response = resource.downloadFolder("repoKey", "path", "zip", false);
        assertEquals(response.getStatus(), HttpStatus.SC_NOT_FOUND);
    }

    @Test
    public void testDownloadFolderOrRepoFolderDownloadExceptionHandle() throws BlackedOutException, IOException {
        ArchiveResource resource = new ArchiveResourceForTest();
        resource.setSecurityService(mock(SecurityService.class));

        AddonsManager addonsManager = mock(AddonsManager.class);
        RestAddon restAddon = mock(RestAddon.class);

        RepoConfig repoConfig = mock(RepoConfig.class);
        when(repoConfig.getKey()).thenReturn("repoKey");
        when(restAddon.downloadFolderOrRepo(any(), any(), anyBoolean())).thenThrow(
                new FolderDownloadException("message", HttpStatus.SC_BAD_REQUEST));
        when(addonsManager.addonByType(RestAddon.class)).thenReturn(restAddon);
        resource.setAddonsManager(addonsManager);

        Response response = resource.downloadFolder("repoKey", "path", "zip", false);
        assertEquals(response.getStatus(), HttpStatus.SC_BAD_REQUEST);
    }

    @Test
    public void testDownloadFolderOrRepoExceptionHandle() throws BlackedOutException, IOException {
        ArchiveResource resource = new ArchiveResourceForTest();
        resource.setSecurityService(mock(SecurityService.class));

        AddonsManager addonsManager = mock(AddonsManager.class);
        RestAddon restAddon = mock(RestAddon.class);

        RepoConfig repoConfig = mock(RepoConfig.class);
        when(repoConfig.getKey()).thenReturn("repoKey");
        when(restAddon.downloadFolderOrRepo(any(), any(), anyBoolean()))
                .thenThrow(new NullPointerException("message"));
        when(addonsManager.addonByType(RestAddon.class)).thenReturn(restAddon);
        resource.setAddonsManager(addonsManager);

        Response response = resource.downloadFolder("repoKey", "path", "zip", false);
        assertEquals(response.getStatus(), HttpStatus.SC_INTERNAL_SERVER_ERROR);
    }

    private static class ArchiveResourceForTest extends ArchiveResource {
        @Override
        InputStream getArchiveStream(File buildArtifactsArchive) throws FileNotFoundException {
            return new ByteArrayInputStream("test-data".getBytes());
        }
    }
}