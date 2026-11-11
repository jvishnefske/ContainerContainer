package org.artifactory.util;

import org.apache.commons.io.input.CountingInputStream;
import org.artifactory.api.repo.exception.RepoRejectException;
import org.artifactory.api.request.ArtifactoryResponse;
import org.artifactory.api.request.InternalArtifactoryRequest;
import org.artifactory.api.request.InternalSystemFolderUploadRequest;
import org.artifactory.api.request.ReplicationChecksumDeployRequest;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.fs.RepoResource;
import org.artifactory.md.Properties;
import org.artifactory.model.fs.FileInfoImpl;
import org.artifactory.properties.PropertiesImpl;
import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.SaveResourceContext;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.request.ArtifactoryRequest;
import org.artifactory.resource.MutableRepoResourceInfo;
import org.jfrog.storage.binstore.common.ReaderTrackingInputStream;
import org.jfrog.storage.binstore.ifc.ArtificialInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

import static org.artifactory.util.UploadServiceUtils.getInputStream;
import static org.artifactory.util.UploadServiceUtils.getTargetRepository;
import static org.artifactory.util.UploadServiceUtils.getTransferredSize;
import static org.artifactory.util.UploadServiceUtils.isSuccess;
import static org.artifactory.util.UploadServiceUtils.populateAndValidateItemPropertiesFromRequest;
import static org.artifactory.util.UploadServiceUtils.validateCanAnnotate;
import static org.artifactory.util.UploadServiceUtils.validateProperties;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.AssertJUnit.assertFalse;

/**
 * @author dudim
 */
class UploadServiceUtilsTest {

    @Test
    void testPopulateAndValidateItemPropertiesFromRequestChecksumDeployWithNoAnnotatePermission()
            throws RepoRejectException, UnsupportedEncodingException {
        ArtifactoryRequest requestMock = mock(ArtifactoryRequest.class);
        PropertiesImpl requestProps = new PropertiesImpl();
        requestProps.put("propKey", "propValue");
        when(requestMock.getProperties()).thenReturn(requestProps);
        when(requestMock.getHeader(ArtifactoryRequest.CHECKSUM_DEPLOY)).thenReturn("true");
        when(requestMock.getHeader(ArtifactoryRequest.CHECKSUM_SHA256))
                .thenReturn("15e470ec647ae0e6734ec2a397d8be444063aebab70fb6775c1a1e5f042b0ae6");
        RepoPath repoPath = RepoPathFactory.create("repoKey", "path/to/uploaded/binary.tgz");
        AuthorizationService authServiceMock = mock(AuthorizationService.class);
        when(authServiceMock.canAnnotate(repoPath)).thenReturn(false);
        ReaderTrackingInputStream readerTrackingInputStream =
                new ReaderTrackingInputStream(new StringInputStream("file body"));
        SecurityService securityServiceMock = mock(SecurityService.class);
        Properties properties =
                populateAndValidateItemPropertiesFromRequest(requestMock, readerTrackingInputStream, repoPath,
                        authServiceMock, securityServiceMock);
        assertNotNull(properties);
        assertEquals(properties.size(), 1);
        assertEquals(properties.getFirst("sha256"), "15e470ec647ae0e6734ec2a397d8be444063aebab70fb6775c1a1e5f042b0ae6");
        verify(authServiceMock, times(1)).canAnnotate(repoPath);
    }

    @Test
    void testPopulateAndValidateItemPropertiesFromRequestChecksumDeployValidScenario()
            throws RepoRejectException, UnsupportedEncodingException {
        ArtifactoryRequest requestMock = mock(ArtifactoryRequest.class);
        PropertiesImpl requestProps = new PropertiesImpl();
        requestProps.put("propKey", "propValue");
        when(requestMock.getProperties()).thenReturn(requestProps);
        when(requestMock.getHeader(ArtifactoryRequest.CHECKSUM_DEPLOY)).thenReturn("true");
        when(requestMock.getHeader(ArtifactoryRequest.CHECKSUM_SHA256))
                .thenReturn("15e470ec647ae0e6734ec2a397d8be444063aebab70fb6775c1a1e5f042b0ae6");
        RepoPath repoPath = RepoPathFactory.create("repoKey", "path/to/uploaded/binary.tgz");
        AuthorizationService authServiceMock = mock(AuthorizationService.class);
        when(authServiceMock.canAnnotate(repoPath)).thenReturn(true);
        ReaderTrackingInputStream readerTrackingInputStream =
                new ReaderTrackingInputStream(new StringInputStream("file body"));
        SecurityService securityServiceMock = mock(SecurityService.class);
        Properties properties =
                populateAndValidateItemPropertiesFromRequest(requestMock, readerTrackingInputStream, repoPath,
                        authServiceMock, securityServiceMock);
        assertNotNull(properties);
        assertEquals(properties.size(), 2);
        assertEquals(properties.getFirst("sha256"), "15e470ec647ae0e6734ec2a397d8be444063aebab70fb6775c1a1e5f042b0ae6");
        assertEquals(properties.getFirst("propKey"), "propValue");
        verify(authServiceMock, times(1)).canAnnotate(repoPath);
    }

    @Test
    void testPopulateAndValidateItemPropertiesFromRequestNoAnnotatePermission() throws RepoRejectException {
        ArtifactoryRequest requestMock = mock(ArtifactoryRequest.class);
        PropertiesImpl requestProps = new PropertiesImpl();
        requestProps.put("propKey", "propValue");
        when(requestMock.getProperties()).thenReturn(requestProps);

        RepoPath repoPath = RepoPathFactory.create("repoKey", "path/to/uploaded/binary.tgz");

        AuthorizationService authServiceMock = mock(AuthorizationService.class);
        when(authServiceMock.canAnnotate(repoPath)).thenReturn(false);
        SecurityService securityServiceMock = mock(SecurityService.class);
        Properties properties =
                populateAndValidateItemPropertiesFromRequest(requestMock, null, repoPath, authServiceMock,
                        securityServiceMock);
        assertNull(properties);
        verify(authServiceMock, times(1)).canAnnotate(repoPath);
    }

    @Test
    void testPopulateAndValidateItemPropertiesFromRequestWithNullProperties() throws RepoRejectException {
        ArtifactoryRequest requestMock = mock(ArtifactoryRequest.class);
        when(requestMock.getProperties()).thenReturn(null);

        AuthorizationService authServiceMock = mock(AuthorizationService.class);
        SecurityService securityServiceMock = mock(SecurityService.class);

        Properties properties = populateAndValidateItemPropertiesFromRequest(requestMock, null, null, authServiceMock,
                securityServiceMock);
        assertNull(properties);
        verify(authServiceMock, times(0)).canAnnotate(any());
    }

    @Test
    void testPopulateAndValidateItemPropertiesFromRequestWithEmptyProperties() throws RepoRejectException {
        ArtifactoryRequest requestMock = mock(ArtifactoryRequest.class);
        when(requestMock.getProperties()).thenReturn(new PropertiesImpl());

        AuthorizationService authServiceMock = mock(AuthorizationService.class);
        SecurityService securityServiceMock = mock(SecurityService.class);

        Properties properties = populateAndValidateItemPropertiesFromRequest(requestMock, null, null, authServiceMock,
                securityServiceMock);
        assertNull(properties);
        verify(authServiceMock, times(0)).canAnnotate(any());
    }

    @Test
    public void testPopulateAndValidateItemPropertiesFromRequestWithUnvalidProperties() throws RepoRejectException {
        ArtifactoryRequest requestMock = mock(ArtifactoryRequest.class);
        PropertiesImpl requestProps = new PropertiesImpl();
        requestProps.put("555propKey", "propValue");
        when(requestMock.getProperties()).thenReturn(requestProps);

        RepoPath repoPath = RepoPathFactory.create("repoKey", "path/to/uploaded/binary.tgz");

        AuthorizationService authServiceMock = mock(AuthorizationService.class);
        when(authServiceMock.canAnnotate(repoPath)).thenReturn(true);
        SecurityService securityServiceMock = mock(SecurityService.class);

        assertThrows(RepoRejectException.class,
                () -> populateAndValidateItemPropertiesFromRequest(requestMock, null, repoPath, authServiceMock,
                        securityServiceMock), "Expected RepoRejectException to throw, but it didn't");
    }

    @Test
    void testPopulateAndValidateItemPropertiesFromRequestValidScenario() throws RepoRejectException {
        ArtifactoryRequest requestMock = mock(ArtifactoryRequest.class);
        PropertiesImpl requestProps = new PropertiesImpl();
        requestProps.put("propKey", "propValue");
        when(requestMock.getProperties()).thenReturn(requestProps);

        RepoPath repoPath = RepoPathFactory.create("repoKey", "path/to/uploaded/binary.tgz");

        AuthorizationService authServiceMock = mock(AuthorizationService.class);
        when(authServiceMock.canAnnotate(repoPath)).thenReturn(true);
        SecurityService securityServiceMock = mock(SecurityService.class);
        Properties properties = populateAndValidateItemPropertiesFromRequest(requestMock, null, repoPath,
                authServiceMock, securityServiceMock);

        assertNotNull(properties);
        assertEquals(properties.get("propKey").size(), 1);
        assertTrue(properties.get("propKey").contains("propValue"));
    }

    @Test
    void testValidateCanAnnotateNoAnnotatePermission() throws RepoRejectException {
        RepoPath repoPath = RepoPathFactory.create("repoKey", "path/to/uploaded/binary.tgz");
        AuthorizationService authServiceMock = mock(AuthorizationService.class);
        when(authServiceMock.canAnnotate(repoPath)).thenReturn(false);
        SecurityService securityServiceMock = mock(SecurityService.class);
        assertFalse(validateCanAnnotate(repoPath, authServiceMock, securityServiceMock));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testPopulateItemInfoFromHeaders(boolean isAdmin) {
        ArtifactoryRequest request = mock(ArtifactoryRequest.class);
        RepoResource res = mock(RepoResource.class);
        InputStream inputStream = mock(InputStream.class);
        SaveResourceContext.Builder contextBuilder = new SaveResourceContext.Builder(res, inputStream);
        SecurityService securityService = mock(SecurityService.class);
        when(securityService.isAdmin()).thenReturn(isAdmin);
        when(request.getHeader(ArtifactoryRequest.LAST_MODIFIED)).thenReturn("5");
        MutableRepoResourceInfo mutableRepoResourceInfo = new FileInfoImpl(); //mock(MutableRepoResourceInfo.class);
        when(res.getInfo()).thenReturn(mutableRepoResourceInfo);
        assertEquals(0, mutableRepoResourceInfo.getLastModified());
        UploadServiceUtils.populateItemInfoFromHeaders(request, res, contextBuilder, securityService);
        if (isAdmin) {
            verify(request, times(1)).getHeader(ArtifactoryRequest.LAST_MODIFIED);
            assertEquals(5, mutableRepoResourceInfo.getLastModified());
        } else {
            verify(request, never()).getHeader(ArtifactoryRequest.LAST_MODIFIED);
            verify(request, never()).getHeader(ArtifactoryRequest.CREATED);
            verify(request, never()).getHeader(ArtifactoryRequest.CREATED_BY);
            verify(request, never()).getHeader(ArtifactoryRequest.MODIFIED_BY);
            assertEquals(mutableRepoResourceInfo.getCreated(), mutableRepoResourceInfo.getLastModified());
        }
    }

    @Test
    void testValidateCanAnnotateValidScenario() throws RepoRejectException {
        RepoPath repoPath = RepoPathFactory.create("repoKey", "path/to/uploaded/binary.tgz");
        AuthorizationService authServiceMock = mock(AuthorizationService.class);
        when(authServiceMock.canAnnotate(repoPath)).thenReturn(true);
        SecurityService securityServiceMock = mock(SecurityService.class);
        assertTrue(validateCanAnnotate(repoPath, authServiceMock, securityServiceMock));
    }

    @Test
    void testValidatePropertiesUnvalidProps() throws RepoRejectException {
        PropertiesImpl properties = new PropertiesImpl();
        properties.put("555propKey", "value");
        assertThrows(RepoRejectException.class, () -> validateProperties(properties));
    }

    @Test
    void testValidatePropertiesValidProps() throws RepoRejectException {
        PropertiesImpl properties = new PropertiesImpl();
        properties.put("propKey", "value");
        validateProperties(properties);
    }

    @Test
    void testGetTransferredSize() {
        ArtificialInputStream inputStream = new ArtificialInputStream("", "", "", 25L, "repo", null);
        assertEquals(getTransferredSize(inputStream), 25L);

        CountingInputStream countingInputStream = new CountingInputStream(inputStream);
        assertEquals(getTransferredSize(countingInputStream), 0L);
    }

    @Test
    void testGetInputStream() throws IOException {
        ArtifactoryRequest request = mock(ArtifactoryRequest.class);
        ArtificialInputStream inputStream = new ArtificialInputStream("", "", "", 25L, "repo", null);
        when(request.getInputStream()).thenReturn(inputStream);
        assertEquals(getInputStream(request, null), inputStream);

        reset(request);
        InputStream dummy = mock(InputStream.class);
        when(request.getInputStream()).thenReturn(dummy);
        assertTrue(getInputStream(request, CountingInputStream::new) instanceof CountingInputStream);
    }

    @Test
    void testShouldLogTrafficUponFailure() {
        assertFalse(isSuccess(null));

        ArtifactoryResponse response = mock(ArtifactoryResponse.class);
        when(response.getStatus()).thenReturn(400);
        assertFalse(isSuccess(response));

        reset(response);
        when(response.getStatus()).thenReturn(200);
        assertTrue(isSuccess(response));

        reset(response);
        when(response.getStatus()).thenReturn(201);
        assertTrue(isSuccess(response));
    }

    @Test
    void testGetTargetRepositoryReplicationChecksumDeployRequest() {
        LocalRepo<?, ?> localRepo = mock(LocalRepo.class);
        when(localRepo.getKey()).thenReturn("cool-remote-cache");
        InternalRepositoryService internalRepositoryService = mock(InternalRepositoryService.class);
        when(internalRepositoryService.localFederatedOrCachedRepositoryByKey(any())).thenReturn(localRepo);
        RepoPath cacheRepoPath = InternalRepoPathFactory.create("cool-remote-cache", "cool.file");
        ReplicationChecksumDeployRequest replicationChecksumDeployRequest =
                new ReplicationChecksumDeployRequest(cacheRepoPath, true);
        LocalRepo<?, ?> targetRepository =
                getTargetRepository(replicationChecksumDeployRequest, internalRepositoryService);
        assertNotNull(targetRepository);
        assertEquals(targetRepository.getKey(), "cool-remote-cache");
        InternalArtifactoryRequest internalArtifactoryRequest = new InternalArtifactoryRequest(cacheRepoPath, true);
        targetRepository = getTargetRepository(internalArtifactoryRequest, internalRepositoryService);
        assertNull(targetRepository);
    }

    /**
     * Verifies that {@link InternalSystemFolderUploadRequest} is routed via
     * {@code localFederatedOrCachedRepositoryByKey} so remote repo keys resolve to their
     * {@code -cache} half — the same behaviour as {@link ReplicationChecksumDeployRequest}.
     * A plain {@link InternalArtifactoryRequest} with the same repo key returns {@code null}
     * (i.e. rejected), proving the routing is type-specific.
     */
    @Test
    void getTargetRepository_internalSystemFolderRequest_resolvesCacheRepo() {
        LocalRepo<?, ?> cacheRepo = mock(LocalRepo.class);
        when(cacheRepo.getKey()).thenReturn("my-remote-cache");
        InternalRepositoryService repoService = mock(InternalRepositoryService.class);
        when(repoService.localFederatedOrCachedRepositoryByKey("my-remote")).thenReturn(cacheRepo);

        RepoPath repoPath = RepoPathFactory.create("my-remote", ".evidence/sha256:abc/sig.json");
        InternalSystemFolderUploadRequest request =
                new InternalSystemFolderUploadRequest(repoPath, InputStream.nullInputStream(), -1);

        LocalRepo<?, ?> result = getTargetRepository(request, repoService);

        assertNotNull(result);
        assertEquals(result.getKey(), "my-remote-cache");
        verify(repoService, never()).localOrFederatedRepositoryByKey(any());
    }

    @ParameterizedTest
    @CsvSource({
            "10, 10",
            "124763434, 124763434",
            "10.4, -1",
            "0, 0",
            "0.123, -1",
            "-0.123, -1",
            "-1, -1",
            "abc, -1",
            ", -1"
    })
    void testGetChecksumBinarySize(String header, long expectedResult) {
        ArtifactoryRequest requestMock = mock(ArtifactoryRequest.class);
        when(requestMock.getHeader(eq(ArtifactoryRequest.BINARY_SIZE))).thenReturn(header);
        long result = UploadServiceUtils.getBinarySize(requestMock);
        assertEquals(expectedResult, result);
    }

}