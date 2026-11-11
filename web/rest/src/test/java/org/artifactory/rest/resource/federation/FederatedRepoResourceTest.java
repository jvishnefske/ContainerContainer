package org.artifactory.rest.resource.federation;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import lombok.Cleanup;
import lombok.SneakyThrows;
import org.apache.http.HttpStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.request.ArtifactoryResponse;
import org.artifactory.api.request.DownloadService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.federation.FederatedRequestCustomContext;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.entitlement.EntitlementType;
import org.artifactory.exceptions.HaFederationPropagationException;
import org.artifactory.federation.DryFullSyncException;
import org.artifactory.federation.FederatedFullSyncRepoService;
import org.artifactory.federation.FederatedRepoService;
import org.artifactory.federation.FederationRoutingService;
import org.artifactory.federation.FullSyncException;
import org.artifactory.federation.exceptions.BinariesTaskException;
import org.artifactory.federation.exceptions.FederatedRemoteMembersUpdateException;
import org.artifactory.federation.model.RepoStatus;
import org.artifactory.federation.model.State;
import org.artifactory.model.QueuePosition;
import org.artifactory.repo.ArtifactoryFederationServiceException;
import org.artifactory.repo.FederationMigrationStatus;
import org.artifactory.repo.FederationPriorityUpdateRtfsResponse;
import org.artifactory.repo.FullSyncRtfsResponse;
import org.artifactory.repo.RepoDetails;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPriority;
import org.artifactory.repo.UrlReposDetails;
import org.artifactory.request.ArtifactoryRequest;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.exception.http.ConflictException;
import org.artifactory.rest.exception.RestException;
import org.artifactory.security.SecurityServiceImpl;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.fs.service.FileService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.common.JsonUtils;
import org.jfrog.rtfs.dto.member.ExtendedMirrorMemberInfo;
import org.jfrog.rtfs.dto.member.FederatedConfigInfo;
import org.jfrog.rtfs.dto.member.MemberQueueState;
import org.jfrog.rtfs.dto.member.MirrorMemberInfo;
import org.jfrog.rtfs.dto.migration.MigrationVerboseStatus;
import org.jfrog.rtfs.dto.properties.Properties;
import org.jfrog.rtfs.dto.queue.QueueState;
import org.jfrog.rtfs.dto.tasks.TaskRequest;
import org.jfrog.rtfs.dto.tenant.snapshot.TenantMoveStatus;
import org.jfrog.rtfs.dto.traffic.ExtendedInfo;
import org.jfrog.rtfs.module.member.MemberRemovalResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.verification.VerificationMode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.apache.http.HttpStatus.SC_ACCEPTED;
import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_CREATED;
import static org.apache.http.HttpStatus.SC_FAILED_DEPENDENCY;
import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_METHOD_NOT_ALLOWED;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_NO_CONTENT;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_TOO_MANY_REQUESTS;
import static org.artifactory.rest.resource.federation.FederatedRepoResource.MIGRATION_TOOL_USER_AGENT;
import static org.artifactory.rest.resource.federation.FederatedRepoResource.MIN_SUPPORTED_MIGRATION_TOOL_VERSION;
import static org.artifactory.util.bundles.BundleConfigurationUtil.DEFAULT_RBV2_REPO;
import static org.artifactory.model.GridTopology.TopologyType.MIRROR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;


/**
 * @author Alexei Vainshtein
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class FederatedRepoResourceTest extends ArtifactoryHomeBoundJUnitTest {

    private static final String GRID_TOPOLOGY_CHANGE_PROCESSED_SUCCESSFULLY =
            "Grid topology change processed successfully";

    @Mock
    AddonsManager addonsManager;;

    @Mock
    SecurityServiceImpl securityService;

    @Mock
    DownloadService downloadService;

    @Mock
    FileService fileService;

    @Mock
    private FederatedRepoService federatedRepoService;

    @Mock
    private FederationRoutingService federationRoutingService;

    @Mock
    private FederatedFullSyncRepoService federatedFullSyncRepoService;

    @Mock
    private RepoPath repoPath;

    @Mock
    private FederatedRequestCustomContext customContext;

    @InjectMocks
    FederatedRepoResource resource;

    MockedStatic<ContextHelper> contextHelperStatic = mockStatic(ContextHelper.class);

    @BeforeEach
    public void setup() {
        customContext.clear();
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        when(context.getContextPath()).thenReturn("");
        contextHelperStatic.when(ContextHelper::get).thenReturn(context);
        lenient().when(ContextHelper.get().beanForType(SecurityService.class)).thenReturn(securityService);
        lenient().when(ContextHelper.get().beanForType(AddonsManager.class)).thenReturn(addonsManager);
        when(addonsManager.isEntitled(EntitlementType.FEDERATED_REPOSITORY)).thenReturn(true);
        ArtifactoryContextThreadBinder.bind(context);
    }

    @AfterEach
    public void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        contextHelperStatic.close();
    }

    @ParameterizedTest
    @MethodSource("responseProvider")
    void testValidate(String responseFromService, int responseCode, String reason, boolean isImport) {

        createRequest(resource);
        if (responseCode != SC_OK) {
            doThrow(new BadRequestException(responseFromService)).when(federatedRepoService)
                    .validate("repo", "http://localhost:8080/artifactory", 0, null, null, isImport, false, "project1");
        }
        if (responseCode == 409) {
            doThrow(new ConflictException(responseFromService)).when(federatedRepoService)
                    .validate("repo", "http://localhost:8080/artifactory", 0, null, null, isImport, false, "project1");
        }
        try (Response response = resource.validate("repo", 0, null, null, isImport, "project1")) {
            assertEquals(response.getStatus(), responseCode);
            assertEquals(response.getEntity(), reason);
        }
    }

    @Test
    void testValidateWithRBv2Key() {
        createRequest(resource);
        doNothing().when(federatedRepoService)
                .validate(eq(DEFAULT_RBV2_REPO), anyString(), anyLong(), eq(RepoType.ReleaseBundles.getType()),
                        anyString(), anyBoolean(), anyBoolean(), anyString());
        try (Response response = resource.validate(DEFAULT_RBV2_REPO, 0,
                RepoType.ReleaseBundles.getType(), DEFAULT_RBV2_REPO, false, "project1")) {
            assertEquals(SC_CREATED, response.getStatus());
        }
    }

    @Test
    public void testRepos() {
        resource.setService(federatedRepoService);
        List<RepoDetails> repoDetails = new ArrayList<>();
        repoDetails.add(new RepoDetails("key", "", RepoDetailsType.FEDERATED, "url", "", RepoType.Docker));
        repoDetails.add(new RepoDetails("key2", "", RepoDetailsType.FEDERATED, "url", "", RepoType.Npm));
        when(federatedRepoService.getRepos()).thenReturn(repoDetails);
        @Cleanup Response repos = resource.repos();
        assertEquals(SC_OK, repos.getStatus());
        assertEquals(repos.getEntity(), repoDetails);
    }

    @Test
    public void testBaseUrlAndRepos() {

        resource.setService(federatedRepoService);
        List<RepoDetails> repoDetails = new ArrayList<>();
        repoDetails.add(new RepoDetails("key", "", RepoDetailsType.FEDERATED, "url", "", RepoType.Docker));
        repoDetails.add(new RepoDetails("key2", "", RepoDetailsType.FEDERATED, "url", "", RepoType.Npm));
        UrlReposDetails urlReposDetails =
                UrlReposDetails.builder().reposDetailsList(repoDetails).baseUrl("url").build();
        when(federatedRepoService.getBaseUrlAndRepos()).thenReturn(urlReposDetails);
        @Cleanup Response repos = resource.baseUrlAndRepos();
        assertEquals(SC_OK, repos.getStatus());
        assertEquals(urlReposDetails, repos.getEntity());
    }

    @Test
    public void testReposFails() {
        resource.setService(federatedRepoService);
        doThrow(new BadRequestException("bad")).when(federatedRepoService).getRepos();
        @Cleanup Response repos = resource.repos();

        assertEquals(SC_BAD_REQUEST, repos.getStatus());
        assertEquals("Retrieval of federated repositories failed: bad", repos.getEntity());
    }

    @Test
    public void testBaseUrlAndReposFails() {

        resource.setService(federatedRepoService);
        doThrow(new BadRequestException("bad")).when(federatedRepoService).getBaseUrlAndRepos();
        @Cleanup Response repos = resource.baseUrlAndRepos();

        assertEquals(SC_BAD_REQUEST, repos.getStatus());
        assertEquals("Retrieval of federated repositories failed: bad", repos.getEntity());
    }

    @Test
    public void testMigrate() {
        resource.setService(federatedRepoService);
        doAnswer(invocationOnMock -> {
            assertEquals(("repo"), invocationOnMock.getArgument(0, String.class));
            return null;
        }).when(federatedRepoService).migrate("repo");
        @Cleanup Response repos = resource.migrate("repo");
        assertEquals(SC_OK, repos.getStatus());
        assertEquals("Migration finished successfully", repos.getEntity());
    }

    @Test
    public void testMigrateFails() {
        doThrow(new BadRequestException("should throw exception test")).when(federatedRepoService).migrate("repo");
        @Cleanup Response repos = resource.migrate("repo");
        assertEquals(SC_BAD_REQUEST, repos.getStatus());
        assertEquals("Failed to migrate repository repo due to: should throw exception test", repos.getEntity());
    }

    @Test
    public void testConvertToLocalSuccess() {
        String repoName = "repo";
        HashMap<String, String> errors = new HashMap<>();
        resource.setService(federatedRepoService);
        when(federatedRepoService.convertFederatedToLocal(any())).thenReturn(errors);
        @Cleanup Response repos = resource.convertToLocal(List.of(repoName));
        assertEquals(SC_OK, repos.getStatus());
        assertEquals("Successfully converted from federated to local repositories: %s"
                .formatted(List.of(repoName)), repos.getEntity());
    }

    @Test
    public void testConvertToLocalPArtialSuccess() {
        String repoName = "repo";
        HashMap<String, String> errors = new HashMap<>();
        errors.put(repoName, "error");
        resource.setService(federatedRepoService);
        when(federatedRepoService.convertFederatedToLocal(any())).thenReturn(errors);
        @Cleanup Response repos = resource.convertToLocal(List.of(repoName, "repo2"));
        assertEquals(SC_OK, repos.getStatus());
        assertEquals(Map.of("failedConversionRepositories", errors), repos.getEntity());
    }

    @Test
    public void testConvertToLocalFails() {
        String repoName = "repo";
        HashMap<String, String> errors = new HashMap<>();
        errors.put(repoName, "error");
        when(federatedRepoService.convertFederatedToLocal(any())).thenReturn(errors);
        @Cleanup Response repos = resource.convertToLocal(List.of(repoName));
        assertEquals(SC_BAD_REQUEST, repos.getStatus());
        assertEquals(Map.of("failedConversionRepositories", errors), repos.getEntity());
    }

    @Test
    public void testFullSync() {

        doReturn(new FullSyncRtfsResponse("Full sync triggered successfully")).when(
                federationRoutingService).triggerFullSync("repo", "", false, false);
        createRequest(resource);
        @Cleanup Response resp = resource.fullSync("repo", "", false);
        assertEquals(SC_OK, resp.getStatus());
        FullSyncRtfsResponse parsedResp = JsonUtils.getInstance().readValue(resp.getEntity().toString(),
                FullSyncRtfsResponse.class);
        assertEquals("Full sync triggered successfully", parsedResp.message());
        assertEquals(List.of(), parsedResp.resultPaths());
    }


    @Test
    public void testFullSyncError() {

        doThrow(new BadRequestException("bad request")).when(federationRoutingService).triggerFullSync("repo",
                "", false, false);
        createRequest(resource);
        @Cleanup Response repo = resource.fullSync("repo", "", false);
        assertEquals(SC_BAD_REQUEST, repo.getStatus());
        assertEquals("Failed trigger full sync for repository repo due to: bad request", repo.getEntity());
    }

    @Test
    public void testDryFullSyncWithNoResponseFiles() {

        doReturn(new FullSyncRtfsResponse("success", List.of())).when(
                federationRoutingService).triggerFullSync("repo", "", false, true);
        createRequest(resource);
        @Cleanup Response resp = resource.fullSync("repo", "", true);
        assertEquals(SC_OK, resp.getStatus());
        FullSyncRtfsResponse parsedResp = JsonUtils.getInstance().readValue(resp.getEntity().toString(),
                FullSyncRtfsResponse.class);
        assertEquals(List.of(), parsedResp.resultPaths());
    }

    @Test
    public void testDryFullSyncWithNoResponse() {

        doReturn(new FullSyncRtfsResponse("success", List.of(
                "file_1",
                "file_2"
        ))).when(
                federationRoutingService).triggerFullSync("repo", "", false, true);
        createRequest(resource);
        @Cleanup Response resp = resource.fullSync("repo", "", true);
        assertEquals(SC_OK, resp.getStatus());
        FullSyncRtfsResponse parsedResp = JsonUtils.getInstance().readValue(resp.getEntity().toString(),
                FullSyncRtfsResponse.class);
        assertEquals(List.of(
                "http://localhost:8080/artifactory/api/federation/compareResult/file_1",
                "http://localhost:8080/artifactory/api/federation/compareResult/file_2"
        ), parsedResp.resultPaths());
    }

    @Test
    public void testFullSyncExcludedRepo() {

        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.federatedFullSyncExcludeRepos, "repo");
        createRequest(resource);
        @Cleanup Response repo = resource.fullSync("repo", "", false);
        verifyNoInteractions(federatedRepoService);
        assertEquals(SC_METHOD_NOT_ALLOWED, repo.getStatus());
        assertEquals("Full sync trigger attempt on excluded repo", repo.getEntity());
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.federatedFullSyncExcludeRepos, "");
    }

    @Test
    public void testForceFullsync() {

        doReturn(new FullSyncRtfsResponse("Full sync triggered successfully")).when(
                federationRoutingService).triggerFullSync("repo", "", true, false);

        @Cleanup Response resp = resource.forceFullSync("repo", "");
        assertEquals(SC_OK, resp.getStatus());
        FullSyncRtfsResponse parsedResp = JsonUtils.getInstance().readValue(resp.getEntity().toString(),
                FullSyncRtfsResponse.class);

        assertEquals("Full sync triggered successfully", parsedResp.message());
        assertEquals(List.of(), parsedResp.resultPaths());
    }

    @Test
    public void testGetDryFullSyncComparisonHappyFlow() throws Exception {

        resource.setDownloadService(downloadService);
        resource.setFileService(fileService);
        when(securityService.callAsSystem(any())).thenCallRealMethod();
        when(fileService.exists(any())).thenReturn(true);
        doNothing().when(downloadService).process(any(ArtifactoryRequest.class), any(ArtifactoryResponse.class));
        @Cleanup Response resp = resource.getDryFullSyncComparisonFile("test-compare-result.json");
        assertEquals(SC_OK, resp.getStatus());
    }

    @Test
    public void testGetDryFullSyncComparisonInvalidRequest() throws Exception {

        resource.setDownloadService(downloadService);
        resource.setFileService(fileService);
        doNothing().when(downloadService).process(any(ArtifactoryRequest.class), any(ArtifactoryResponse.class));
        @Cleanup Response resp = resource.getDryFullSyncComparisonFile("test-invalid-end.json");
        assertEquals(SC_BAD_REQUEST, resp.getStatus());
    }

    @Test
    public void testGetDryFullSyncComparisonFileDoesntExist() throws Exception {

        resource.setDownloadService(downloadService);
        resource.setFileService(fileService);
        when(fileService.exists(any())).thenReturn(false);
        doNothing().when(downloadService).process(any(ArtifactoryRequest.class), any(ArtifactoryResponse.class));
        @Cleanup Response resp = resource.getDryFullSyncComparisonFile("test-compare-result.json");
        assertEquals(SC_BAD_REQUEST, resp.getStatus());
    }

    @Test
    public void testGetDryFullSyncComparisonInternalError() throws Exception {

        resource.setDownloadService(downloadService);
        resource.setFileService(fileService);
        when(securityService.callAsSystem(any())).thenThrow(new Exception("test"));
        when(fileService.exists(any())).thenReturn(true);
        @Cleanup Response resp = resource.getDryFullSyncComparisonFile("test-compare-result.json");
        assertEquals(SC_INTERNAL_SERVER_ERROR, resp.getStatus());
    }

    @Test
    public void testGetDryFullSyncComparisonInternalErrorDuringProcessing() throws Exception {

        resource.setDownloadService(downloadService);
        resource.setFileService(fileService);
        when(securityService.callAsSystem(any())).thenCallRealMethod();
        when(fileService.exists(any())).thenReturn(true);
        doAnswer(invocationOnMock -> {
            invocationOnMock.getArgument(1, ArtifactoryResponse.class).setStatus(500);
            return null;
        }).when(downloadService).process(any(ArtifactoryRequest.class), any(ArtifactoryResponse.class));
        @Cleanup Response resp = resource.getDryFullSyncComparisonFile("test-compare-result.json");
        assertEquals(SC_INTERNAL_SERVER_ERROR, resp.getStatus());
    }

    @Test
    public void testConfigSync() {

        doNothing().when(federationRoutingService).configSync("repo");
        @Cleanup Response repo = resource.configSync("repo");
        verify(federationRoutingService, times(1)).configSync("repo");
        assertEquals(SC_OK, repo.getStatus());
        assertEquals("Configuration sync executed successfully", repo.getEntity());
    }

    @Test
    public void testConfigSyncBadRequest() {
        doThrow(new BadRequestException("bad request")).when(federationRoutingService).configSync("repo");
        @Cleanup Response repo = resource.configSync("repo");
        verify(federationRoutingService, times(1)).configSync("repo");
        assertEquals(SC_BAD_REQUEST, repo.getStatus());
    }

    @Test
    void testConfigSyncFederatedRemoteMembersUpdateException() {
        doThrow(new FederatedRemoteMembersUpdateException(List.of("https://..."), "error"))
                .when(federationRoutingService)
                .configSync("repo");
        @Cleanup Response response = resource.configSync("repo");
        assertEquals(SC_BAD_REQUEST, response.getStatus());
        String error = (String) response.getEntity();
        assertEquals("error", error);
    }

    @Test
    void testIsReady() {
        when(federatedRepoService.isActive(anyString())).thenReturn(true);
        @Cleanup Response repo = resource.isReady("repo");
        assertEquals(SC_OK, repo.getStatus());
        assertTrue((Boolean) repo.getEntity());
    }

    @Test
    void testMetadata() {
        String value = "{my special json}";
        List<String> list = List.of("maven");
        when(federatedRepoService.getMetadata(list)).thenReturn(value);
        try (Response response = resource.
                metadata(list)) {
            assertEquals(SC_OK, response.getStatus());
            assertEquals(value, response.getEntity());
        }
    }

    @Test
    void testUpdateMetadata() {

        String metadata = "{maven:{}, gems:{}}";
        String url = "http://localhost:8082";

        //NOSONAR
        try (Response response = resource.updateMetadata(url, true, metadata)) {
            assertEquals(SC_CREATED, response.getStatus());
            assertEquals("Metadata received by the member", response.getEntity());
        }
    }

    @Test
    void testEnableRepos() {

        resource.setService(federatedRepoService);
        List<String> list = List.of("maven");
        try (Response response = resource.enableRepos(list)) {
            assertEquals(SC_OK, response.getStatus());
            assertEquals("Repos of type [maven] enabled.", response.getEntity());
            verify(federatedRepoService).enableRepos(list);
        }
    }

    @Test
    @SneakyThrows
    void testDisableNegotiation() {

        try (Response response = resource.disableNegotiation("alpine")) {
            assertEquals(SC_OK, response.getStatus());
            assertEquals("Negotiation is permanently disabled for all types", response.getEntity());
        }

        try (Response response = resource.disableNegotiation("*")) {
            assertEquals(SC_OK, response.getStatus());
            assertEquals("Negotiation is permanently disabled for all types", response.getEntity());
        }
    }

    @Test
    void testCreateFullSyncFile() {
        when(repoPath.getName()).thenReturn("folder/file");
        when(federatedFullSyncRepoService.deployFullSyncFile(any(), any(), any(Long.class))).thenReturn(repoPath);
        FederatedRepoResource federatedRepoResource = spy(new FederatedRepoResource());
        federatedRepoResource.setFederatedFullSyncRepoService(federatedFullSyncRepoService);
        Response response = federatedRepoResource.createFullSyncFile(any(), any(), any(Long.class));
        assertEquals(SC_OK, response.getStatus());
    }

    @Test
    void testCreateFullSyncFileFails() {
        String fileName = "fileExample";
        when(federatedFullSyncRepoService.deployFullSyncFile(fileName, null, 1L)).thenThrow(FullSyncException.class);
        FederatedRepoResource federatedRepoResource = spy(new FederatedRepoResource());
        federatedRepoResource.setFederatedFullSyncRepoService(federatedFullSyncRepoService);
        Response response = federatedRepoResource.createFullSyncFile(fileName, null, 1L);
        assertEquals(SC_INTERNAL_SERVER_ERROR, response.getStatus());
    }

    @Test
    void testCreateDryFullSyncFile() {
        when(repoPath.getName()).thenReturn("folder/file");
        when(federatedFullSyncRepoService.deployDryFullSyncFile(any(), any(),
                anyString(), anyString(), anyString())).thenReturn(repoPath);
        FederatedRepoResource federatedRepoResource = spy(new FederatedRepoResource());
        federatedRepoResource.setFederatedFullSyncRepoService(federatedFullSyncRepoService);
        Response response = federatedRepoResource.createDryFullSyncFile(any(), any(),
                anyString(), anyString(), anyString());
        assertEquals(SC_OK, response.getStatus());
    }

    @Test
    void testCreateDryFullSyncFileFails() {
        String fileName = "fileExample";
        when(federatedFullSyncRepoService.deployDryFullSyncFile(fileName, null, "jpd",
                "local","remote")).thenThrow(DryFullSyncException.class);
        FederatedRepoResource federatedRepoResource = spy(new FederatedRepoResource());
        federatedRepoResource.setFederatedFullSyncRepoService(federatedFullSyncRepoService);
        Response response = federatedRepoResource.createDryFullSyncFile(fileName, null, "jpd",
                "local","remote");
        assertEquals(SC_INTERNAL_SERVER_ERROR, response.getStatus());
    }

    @Test
    void testDeleteFullSyncFile() {
        when(repoPath.getName()).thenReturn("folder/file");
        doNothing().when(federatedFullSyncRepoService).undeployFullSyncFile(any());
        FederatedRepoResource federatedRepoResource = spy(new FederatedRepoResource());
        federatedRepoResource.setFederatedFullSyncRepoService(federatedFullSyncRepoService);
        Response response = federatedRepoResource.deleteFullSyncFile(any());
        assertEquals(SC_NO_CONTENT, response.getStatus());
    }

    @Test
    void testDeleteFullSyncFileFails() {
        String fileName = "fileExample";
        doThrow(FullSyncException.class).when(federatedFullSyncRepoService).undeployFullSyncFile(any());
        FederatedRepoResource federatedRepoResource = spy(new FederatedRepoResource());
        federatedRepoResource.setFederatedFullSyncRepoService(federatedFullSyncRepoService);
        Response response = federatedRepoResource.deleteFullSyncFile(fileName);
        assertEquals(SC_INTERNAL_SERVER_ERROR, response.getStatus());
    }

    @Test
    void testCreateFullSyncSystemRepo() {
        doNothing().when(federatedFullSyncRepoService).createFullSyncRepo();
        FederatedRepoResource federatedRepoResource = spy(new FederatedRepoResource());
        federatedRepoResource.setFederatedFullSyncRepoService(federatedFullSyncRepoService);
        Response response = federatedRepoResource.createFullSyncFilesRepo();
        assertEquals(SC_OK, response.getStatus());
    }

    @Test
    void testCreateFullSyncSystemRepoFails() {
        when(repoPath.getName()).thenReturn("folder/file");
        doThrow(FullSyncException.class).when(federatedFullSyncRepoService).createFullSyncRepo();
        FederatedRepoResource federatedRepoResource = spy(new FederatedRepoResource());
        federatedRepoResource.setFederatedFullSyncRepoService(federatedFullSyncRepoService);
        Response response = federatedRepoResource.createFullSyncFilesRepo();
        assertEquals(SC_INTERNAL_SERVER_ERROR, response.getStatus());
    }

    @Test
    void testGetPropertyForFullSyncFile() {
        when(federatedFullSyncRepoService.getFullSyncFileProperty(any(), any())).thenReturn(1L);
        FederatedRepoResource federatedRepoResource = spy(new FederatedRepoResource());
        federatedRepoResource.setFederatedFullSyncRepoService(federatedFullSyncRepoService);
        Response response = federatedRepoResource.fullSyncProperty("repo", "repo123");
        assertEquals(SC_OK, response.getStatus());
    }

    @Test
    void testGetPropertyForFullSyncFileFails() {
        when(federatedFullSyncRepoService.getFullSyncFileProperty(any(), any())).thenThrow(FullSyncException.class);
        FederatedRepoResource federatedRepoResource = spy(new FederatedRepoResource());
        federatedRepoResource.setFederatedFullSyncRepoService(federatedFullSyncRepoService);
        Response response = federatedRepoResource.fullSyncProperty("repo", "repo123");
        assertEquals(SC_INTERNAL_SERVER_ERROR, response.getStatus());
    }

    @Test
    void testDownloadFullSyncFile() {
        when(repoPath.getName()).thenReturn("folder/file");
        when(federatedFullSyncRepoService.downloadFullSyncFile(any())).thenReturn(mock(Response.class));
        FederatedRepoResource federatedRepoResource = spy(new FederatedRepoResource());
        federatedRepoResource.setFederatedFullSyncRepoService(federatedFullSyncRepoService);
        Response response = federatedRepoResource.downloadFullSyncFile(any());
        assertNotEquals(SC_INTERNAL_SERVER_ERROR, response.getStatus());
    }

    @Test
    void testDownloadFullSyncFileFails() {
        String fileName = "fileExample";
        when(federatedFullSyncRepoService.downloadFullSyncFile(any())).thenThrow(
                new FullSyncException("File not found", new RestException(404, "File not found")));
        FederatedRepoResource federatedRepoResource = spy(new FederatedRepoResource());
        federatedRepoResource.setFederatedFullSyncRepoService(federatedFullSyncRepoService);
        Response response = federatedRepoResource.downloadFullSyncFile(fileName);
        assertEquals(SC_NOT_FOUND, response.getStatus());
        assertEquals(
                "An error has occurred when streaming the response from system repo in local artifactory, File not " +
                        "found: fileExample", response.getEntity().toString());
    }

    @Test
    void testGetState() {
        Set<State> state = Set.of(new State("localKey", RepoStatus.DISABLED, List.of()));
        when(federatedRepoService.getMirrorStates()).thenReturn(state);
        @Cleanup Response response = resource.state();
        assertEquals(SC_OK, response.getStatus());
        assertEquals(state, response.getEntity());
    }

    @Test
    void testGetStateFails() {
        when(federatedRepoService.getMirrorStates()).thenThrow(HaFederationPropagationException.class);
        @Cleanup Response response = resource.state();
        assertEquals(SC_INTERNAL_SERVER_ERROR, response.getStatus());
    }

    @Test
    void testGetStateIsBlockedByRtfsService() {
        when(federatedRepoService.shouldProcessEventsToRTFS()).thenReturn(true);
        @Cleanup Response response = resource.state();
        assertEquals(SC_METHOD_NOT_ALLOWED, response.getStatus());
    }

    @Test
    void testGetProperties() {
        Properties props = new Properties(List.of());
        when(federatedRepoService.getProperties(any(), any())).thenReturn(props);
        @Cleanup Response response = resource.properties(List.of(), "key");
        assertEquals(SC_OK, response.getStatus());
        assertEquals(props, response.getEntity());
    }

    @Test
    void testGetPropertiesFails() {
        when(federatedRepoService.getProperties(any(), any())).thenThrow(StorageException.class);
        @Cleanup Response response = resource.properties(List.of(), "key");
        assertEquals(SC_INTERNAL_SERVER_ERROR, response.getStatus());
        assertTrue(
                ((String) response.getEntity()).contains("Retrieval of properties for federated repo key has failed"));
    }

    @Test
    void testGetExtendedInfo() {
        ExtendedInfo extendedInfo = new ExtendedInfo("pjkey", "user", "repo", "package");
        when(federatedRepoService.getExtendedInfo(any())).thenReturn(extendedInfo);
        @Cleanup Response response = resource.extendedInfo("repo");
        assertEquals(SC_OK, response.getStatus());
        assertEquals(extendedInfo, response.getEntity());
    }

    @Test
    void testRecoveryForRepo() {
        @Cleanup Response response = resource.recovery("repo");
        verify(federatedRepoService).triggerManualRecoveryForRepository("repo");
        assertEquals(SC_OK, response.getStatus());
        assertEquals("Recovery triggered successfully", response.getEntity());
    }

    @Test
    void testRecovery() {
        @Cleanup Response response = resource.recovery("");
        verify(federatedRepoService).triggerRecovery(true);
        assertEquals(SC_OK, response.getStatus());
        assertEquals("Recovery triggered successfully", response.getEntity());
    }

    @Test
    void testRecoveryBadRequest() {
        doThrow(new BadRequestException("bad request")).when(federatedRepoService)
                .triggerManualRecoveryForRepository("repo");
        @Cleanup Response repo = resource.recovery("repo");
        verify(federatedRepoService, times(1)).triggerManualRecoveryForRepository("repo");
        assertEquals(SC_BAD_REQUEST, repo.getStatus());
    }


    @Test
    void testForceRecover() {
        @Cleanup Response response = resource.recovery("");
        verify(federatedRepoService).triggerRecovery(true);
        assertEquals(SC_OK, response.getStatus());
        assertEquals("Recovery triggered successfully", response.getEntity());
    }

    @Test
    void testEventsService() {
        when(federatedRepoService.lastIdInTheSystem()).thenReturn(30L);
        try (Response response = resource.lastIdInSystem()) {
            assertEquals(30L, response.getEntity());
            assertEquals(HttpStatus.SC_OK, response.getStatus());
        }
    }

    @Test
    void testRtfsStatusReady() {
        createRequest(resource);
        when(federatedRepoService.isMigrationOver()).thenReturn(true);
        try (Response response = resource.rtfsStatus()) {
            assertEquals(Boolean.TRUE, response.getEntity());
            assertEquals(HttpStatus.SC_OK, response.getStatus());
        }
    }

    @Test
    void testRtfsStatusNotReady() {
        createRequest(resource);

        when(federatedRepoService.isMigrationOver()).thenReturn(false);
        try (Response response = resource.rtfsStatus()) {
            assertEquals(Boolean.FALSE, response.getEntity());
            assertEquals(HttpStatus.SC_OK, response.getStatus());
        }
    }

    @Test
    public void testFederatedRepoConfigInfo() {
        FederatedConfigInfo configInfo =
                FederatedConfigInfo.builder().repoKey("repo").version(1L).enabled(true)
                        .members(List.of(ExtendedMirrorMemberInfo.builder().build())).build();
        List<FederatedConfigInfo> configInfos = List.of(configInfo);
        when(federatedRepoService.getFederatedRepoConfigInfo()).thenReturn(configInfos);
        try (Response response = resource.federatedRepoConfigInfo()) {
            assertEquals(configInfos, response.getEntity());
            assertEquals(HttpStatus.SC_OK, response.getStatus());
        }

    }

    @Test
    public void testFederatedConfigInfoByRepoKey() {
        FederatedConfigInfo configInfo =
                FederatedConfigInfo.builder().repoKey("repo").version(1L).enabled(true)
                        .members(List.of(ExtendedMirrorMemberInfo.builder().build())).build();
        when(federatedRepoService.getFederatedRepoConfigInfo("repo")).thenReturn(configInfo);
        try (Response response = resource.federatedConfigInfo("repo")) {
            assertEquals(configInfo, response.getEntity());
            assertEquals(HttpStatus.SC_OK, response.getStatus());
        }
    }

    @Test
    public void testFederatedConfigInfoByRepoKeyNotFound() {
        when(federatedRepoService.getFederatedRepoConfigInfo("missing")).thenReturn(null);
        try (Response response = resource.federatedConfigInfo("missing")) {
            assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatus());
        }
    }

    @Test
    void disableRemoteMemberFederatedRepoTest() throws ArtifactoryFederationServiceException {
        MirrorMemberInfo mirrorMemberInfo = new MirrorMemberInfo("remoteKey1", "url1");
        String localRepoKey = "localRepoKey1";

        String expectedMessage = "Member %s disabled for local repo %s"
                .formatted(mirrorMemberInfo.getRemoteUrlWithRepoKey(), localRepoKey);
        when(federatedRepoService.updateMemberStatus(localRepoKey, mirrorMemberInfo, false, 1L))
                .thenReturn(Response.ok().entity(expectedMessage).build());
        try (Response response = resource.disableRemoteMemberFederatedRepo(localRepoKey, mirrorMemberInfo, 1L)) {
            assertEquals(SC_OK, response.getStatus());
            assertEquals(expectedMessage, response.getEntity());
        }
    }

    @Test
    void disableRemoteMemberFederatedRepoTestBadRequest() throws ArtifactoryFederationServiceException {
        MirrorMemberInfo mirrorMemberInfo = new MirrorMemberInfo("remoteKey1", "url1");
        String localRepoKey = "localRepoKey1";
        String expectedMessage = "bad request";
        doThrow(new BadRequestException(expectedMessage))
                .when(federatedRepoService).updateMemberStatus(any(), any(), eq(false), eq(2L));

        try (Response response = resource.disableRemoteMemberFederatedRepo(localRepoKey, mirrorMemberInfo, 2L)) {
            assertEquals(SC_BAD_REQUEST, response.getStatus());
            assertEquals("Failed to disable remote member due to " + expectedMessage, response.getEntity());
        }
    }

    @Test
    void disableRemoteMemberWithNullVersion() throws ArtifactoryFederationServiceException {
        MirrorMemberInfo mirrorMemberInfo = new MirrorMemberInfo("remoteKey1", "url1");
        String localRepoKey = "localRepoKey1";

        String expectedMessage = "Member %s disabled for local repo %s"
                .formatted(mirrorMemberInfo.getRemoteUrlWithRepoKey(), localRepoKey);
        when(federatedRepoService.updateMemberStatus(localRepoKey, mirrorMemberInfo, false, null))
                .thenReturn(Response.ok().entity(expectedMessage).build());
        try (Response response = resource.disableRemoteMemberFederatedRepo(localRepoKey, mirrorMemberInfo, null)) {
            assertEquals(SC_OK, response.getStatus());
            assertEquals(expectedMessage, response.getEntity());
        }
    }

    @Test
    void enableRemoteMemberFederatedRepoTest() throws ArtifactoryFederationServiceException {
        MirrorMemberInfo mirrorMemberInfo = new MirrorMemberInfo("remoteKey1", "url1");
        String localRepoKey = "localRepoKey1";

        String expectedMessage = "Member %s enabled for local repo %s"
                .formatted(mirrorMemberInfo.getRemoteUrlWithRepoKey(), localRepoKey);
        when(federatedRepoService.updateMemberStatus(localRepoKey, mirrorMemberInfo, true, 3L))
                .thenReturn(Response.ok().entity(expectedMessage).build());
        try (Response response = resource.enableRemoteMemberFederatedRepo(localRepoKey, mirrorMemberInfo, 3L)) {
            assertEquals(SC_OK, response.getStatus());
            assertEquals(expectedMessage, response.getEntity());
        }
    }

    @Test
    void enableRemoteMemberFederatedRepoTestBadRequest() throws ArtifactoryFederationServiceException {
        MirrorMemberInfo mirrorMemberInfo = new MirrorMemberInfo("remoteKey1", "url1");
        String localRepoKey = "localRepoKey1";
        String expectedMessage = "bad request";
        doThrow(new BadRequestException(expectedMessage))
                .when(federatedRepoService).updateMemberStatus(any(), any(), eq(true), eq(4L));

        try (Response response = resource.enableRemoteMemberFederatedRepo(localRepoKey, mirrorMemberInfo, 4L)) {
            assertEquals(SC_BAD_REQUEST, response.getStatus());
            assertEquals("Failed to enable remote member due to " + expectedMessage, response.getEntity());
        }
    }

    @Test
    void enableRemoteMemberWithNullVersion() throws ArtifactoryFederationServiceException {
        MirrorMemberInfo mirrorMemberInfo = new MirrorMemberInfo("remoteKey1", "url1");
        String localRepoKey = "localRepoKey1";

        String expectedMessage = "Member %s enabled for local repo %s"
                .formatted(mirrorMemberInfo.getRemoteUrlWithRepoKey(), localRepoKey);
        when(federatedRepoService.updateMemberStatus(localRepoKey, mirrorMemberInfo, true, null))
                .thenReturn(Response.ok().entity(expectedMessage).build());
        try (Response response = resource.enableRemoteMemberFederatedRepo(localRepoKey, mirrorMemberInfo, null)) {
            assertEquals(SC_OK, response.getStatus());
            assertEquals(expectedMessage, response.getEntity());
        }
    }

    @Test
    public void testRemainingEventsInQueue() {
        QueuePosition toReach = new QueuePosition("remote", "repo", "local", 2L);
        when(federatedRepoService.remainingEventsInQueue(toReach)).thenReturn(5L);
        try (Response response = resource.queueRemainingEventsToProcess(toReach)) {
            assertEquals(5L, response.getEntity());
            assertEquals(HttpStatus.SC_OK, response.getStatus());
        }
    }

    @Test
    public void testQueueStop() {
        try (Response response = resource.queueStop("remote", "remoteKey", "repo")) {
            assertEquals(HttpStatus.SC_ACCEPTED, response.getStatus());
            assertEquals("Queue for member %s/%s and repo %s stopped".formatted("remote", "remoteKey", "repo"),
                    response.getEntity());
        }
    }

    @Test
    public void testQueueDeregister() {
        try (Response response = resource.deregisterQueue("remote", "remoteKey", "repo")) {
            verify(federatedRepoService).deregisterQueue("repo", "remote", "remoteKey");
            assertEquals(HttpStatus.SC_ACCEPTED, response.getStatus());
            assertEquals("Queue for member %s/%s and repo %s removed".formatted("remote", "remoteKey", "repo"),
                    response.getEntity());
        }
    }

    @Test
    public void testQueueStopWithException() {
        doThrow(new BadRequestException("error")).when(federatedRepoService)
                .queueStop("remote", "remoteKey", "repo");
        try (Response response = resource.queueStop("remote", "remoteKey", "repo")) {
            assertEquals(SC_BAD_REQUEST, response.getStatus());
            assertEquals("Queue stop for repo %s failed due to: %s".formatted("repo", "error"),
                    response.getEntity());
        }
    }

    @Test
    public void testQueueDeregisterWithException() {
        doThrow(new BadRequestException("error")).when(federatedRepoService)
                .deregisterQueue("repo", "remote", "remoteKey");
        try (Response response = resource.deregisterQueue("remote", "remoteKey", "repo")) {
            assertEquals(SC_BAD_REQUEST, response.getStatus());
            assertEquals("Queue de-register for repo %s failed due to: %s".formatted("repo", "error"),
                    response.getEntity());
        }
    }

    @Test
    public void testQueueReset() {
        QueueState queuePosition = new QueueState("remote", "remoteKey",
                "repo", MemberQueueState.HEALTHY);
        try (Response response = resource.queueReset(1L, false, queuePosition)) {
            assertEquals(HttpStatus.SC_ACCEPTED, response.getStatus());
            assertEquals("Queue for member %s/%s and repo %s started".formatted("remote", "remoteKey", "repo"),
                    response.getEntity());
            verify(federatedRepoService).queueResetTo(queuePosition, false, 1L);
        }
    }

    @Test
    public void testQueueResetWithException() {
        QueueState queuePosition = new QueueState("remote", "remoteKey",
                "repo", MemberQueueState.HEALTHY);
        doThrow(new BadRequestException("error")).when(federatedRepoService).queueResetTo(queuePosition, false, 1L);
        try (Response response = resource.queueReset(1L, false, queuePosition)) {
            assertEquals(SC_BAD_REQUEST, response.getStatus());
            assertEquals("Failed to reset queue %s due to: %s".formatted(queuePosition.getRepoKey(), "error"),
                    response.getEntity());
            verify(federatedRepoService).queueResetTo(queuePosition, false, 1L);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    public void testMigrationStatus(boolean internal) {
        mockMigrationToolHeaders();
        try (Response response = resource.migrationStatus(FederationMigrationStatus.MIGRATION_COMPLETED, internal)) {
            assertEquals(SC_CREATED, response.getStatus());
            assertEquals("Status %s was set".formatted(FederationMigrationStatus.MIGRATION_COMPLETED),
                    response.getEntity());
        }
        verify(federatedRepoService).updateFederationStatus(FederationMigrationStatus.MIGRATION_COMPLETED, internal);
    }

    @Test
    public void testMigrationStatusFails() {
        mockMigrationToolHeaders();
        doThrow(new BadRequestException("error")).when(federatedRepoService)
                .updateFederationStatus(FederationMigrationStatus.MIGRATION_COMPLETED, false);
        try (Response response = resource.migrationStatus(FederationMigrationStatus.MIGRATION_COMPLETED, false)) {
            assertEquals(SC_BAD_REQUEST, response.getStatus());
            assertEquals("error", response.getEntity());
        }
        verify(federatedRepoService).updateFederationStatus(FederationMigrationStatus.MIGRATION_COMPLETED, false);
    }

    @Test
    public void testGetMigrationStatus() {
        try (Response response = resource.migrationStatus()) {
            assertEquals(SC_OK, response.getStatus());
            assertEquals("Status not configured", response.getEntity());
        }
        verify(federatedRepoService).getCurrentFederationStatus();
    }

    @Test
    public void testGetMigrationStatusWithStatus() {
        when(federatedRepoService.getCurrentFederationStatus()).thenReturn(
                FederationMigrationStatus.MIGRATION_COMPLETED);
        try (Response response = resource.migrationStatus()) {
            assertEquals(SC_OK, response.getStatus());
            assertEquals(FederationMigrationStatus.MIGRATION_COMPLETED.name(), response.getEntity());
        }
        verify(federatedRepoService).getCurrentFederationStatus();
    }

    @Test
    public void testGetMigrationStatusWithException() {
        doThrow(new BadRequestException("error")).when(federatedRepoService).getCurrentFederationStatus();
        try (Response response = resource.migrationStatus()) {
            assertEquals(SC_BAD_REQUEST, response.getStatus());
            assertEquals("error", response.getEntity());
        }
        verify(federatedRepoService).getCurrentFederationStatus();
    }

    @Test
    public void testGetVerboseMigrationStatus() {
        MigrationVerboseStatus migrationVerboseStatus = new MigrationVerboseStatus(List.of());
        when(federatedRepoService.getMigrationVerboseStatus()).thenReturn(migrationVerboseStatus);
        try (Response response = resource.getMigrationVerboseStatus()) {
            assertEquals(SC_OK, response.getStatus());
            assertEquals(migrationVerboseStatus, response.getEntity());
        }
        verify(federatedRepoService).getMigrationVerboseStatus();
    }

    @Test
    public void testGetVerboseMigrationStatusWithException() {
        doThrow(new BadRequestException("error")).when(federatedRepoService).getMigrationVerboseStatus();
        try (Response response = resource.getMigrationVerboseStatus()) {
            assertEquals(SC_INTERNAL_SERVER_ERROR, response.getStatus());
            assertEquals("error", response.getEntity());
        }
        verify(federatedRepoService).getMigrationVerboseStatus();
    }

    @Test
    public void testSetVerboseMigrationStatus() {
        MigrationVerboseStatus migrationVerboseStatus = new MigrationVerboseStatus(List.of());
        try (Response response = resource.setMigrationVerboseStatus(migrationVerboseStatus)) {
            assertEquals(SC_CREATED, response.getStatus());
            assertEquals("Migration verbose status was set", response.getEntity());
        }
        verify(federatedRepoService).setMigrationVerboseStatus(migrationVerboseStatus);
    }

    @Test
    public void testSetMigrationStatusWithException() {
        MigrationVerboseStatus migrationVerboseStatus = new MigrationVerboseStatus(List.of());
        doThrow(new BadRequestException("error")).when(federatedRepoService)
                .setMigrationVerboseStatus(migrationVerboseStatus);
        try (Response response = resource.setMigrationVerboseStatus(migrationVerboseStatus)) {
            assertEquals(SC_INTERNAL_SERVER_ERROR, response.getStatus());
            assertEquals("error", response.getEntity());
        }
        verify(federatedRepoService).setMigrationVerboseStatus(migrationVerboseStatus);
    }

    @Test
    public void testSetMigrationStatusWithNoBody() {
        try (Response response = resource.setMigrationVerboseStatus(null)) {
            assertEquals(SC_BAD_REQUEST, response.getStatus());
            assertEquals("Verbose migration status must not be null", response.getEntity());
        }
        verify(federatedRepoService, never()).setMigrationVerboseStatus(any());
    }

    @ParameterizedTest
    @MethodSource("tenantMoveStatuses")
    public void testGetTenantMoveStatus(TenantMoveStatus tenantMoveStatus) {
        when(federatedRepoService.getTenantMoveStatus()).thenReturn(tenantMoveStatus);
        try (Response response = resource.tenantMoveStatus()) {
            assertEquals(SC_OK, response.getStatus());
            assertEquals(tenantMoveStatus.name(), response.getEntity());
        }
        verify(federatedRepoService).getTenantMoveStatus();
    }

    @Test
    public void testGetTenantMoveStatusThrowsException() {
        when(federatedRepoService.getTenantMoveStatus()).thenThrow(new BadRequestException("error"));
        try (Response response = resource.tenantMoveStatus()) {
            assertEquals(SC_BAD_REQUEST, response.getStatus());
            assertEquals("error", response.getEntity());
        }
        verify(federatedRepoService).getTenantMoveStatus();
    }

    @Test
    public void testEnableRTFSWithException() throws ArtifactoryFederationServiceException {
        mockMigrationToolHeaders();
        doThrow(new BadRequestException("error")).when(federatedRepoService).initFederationClient(true, false);
        try (Response response = resource.enableRTFS(true, false)) {
            assertEquals(SC_BAD_REQUEST, response.getStatus());
            assertEquals("error", response.getEntity());
        }
        verify(federatedRepoService).initFederationClient(true, false);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    public void testEnableRTFS(boolean internal) throws ArtifactoryFederationServiceException {
        mockMigrationToolHeaders();
        when(federatedRepoService.isRtfsClientConnected()).thenReturn(false);
        try (Response response = resource.enableRTFS(true, internal)) {
            assertEquals(SC_CREATED, response.getStatus());
            assertEquals("Federation service enabled", response.getEntity());
        }
        verify(federatedRepoService).initFederationClient(true, internal);
    }

    @Test
    public void testUpdatePriority() throws ArtifactoryFederationServiceException {
        when(federatedRepoService.updateFederationPriority("repo", RepoPriority.HIGH)).thenReturn(
                FederationPriorityUpdateRtfsResponse.builder().message("Priority updated successfully").build());
        try (Response response = resource.updateFederationPriority("repo", "high")) {
            assertEquals(SC_OK, response.getStatus());
            assertEquals("Priority updated successfully", response.getEntity());
        }
    }

    @Test
    public void testUpdatePrioritySuccess() throws ArtifactoryFederationServiceException {
        when(federatedRepoService.updateFederationPriority("repo", RepoPriority.STANDARD)).thenReturn(
                FederationPriorityUpdateRtfsResponse.builder()
                        .message("Priority updated successfully").build());
        try (Response response = resource.updateFederationPriority("repo", "standard")) {
            assertEquals(SC_OK, response.getStatus());
            assertEquals("Priority updated successfully", response.getEntity());
        }
    }

    @Test
    public void testUpdatePriorityFailureUnknownPriority() throws ArtifactoryFederationServiceException {
        when(federatedRepoService.updateFederationPriority("repo", RepoPriority.UNKNOWN)).thenThrow(
                new BadRequestException("Unknown priority: unknown"));

        try (Response response = resource.updateFederationPriority("repo", "unknown")) {
            assertEquals(SC_BAD_REQUEST, response.getStatus());
            assertEquals("Unknown priority: unknown", response.getEntity());
        }
    }

    @Test
    public void testUpdatePriorityFailure() throws ArtifactoryFederationServiceException {
        when(federatedRepoService.updateFederationPriority("repo", RepoPriority.HIGH)).thenThrow(
                new ArtifactoryFederationServiceException("Priority update failed"));
        try (Response response = resource.updateFederationPriority("repo", "high")) {
            assertEquals(SC_BAD_REQUEST, response.getStatus());
            assertEquals("Priority update failed", response.getEntity());
        }
    }

    @Test
    public void testReconnectRtfs() throws ArtifactoryFederationServiceException {
        doNothing().when(federatedRepoService).reconnectFederationClient(false);
        when(federatedRepoService.isRtfsClientConnected()).thenReturn(false);
        try (Response response = resource.reconnectToClient(false)) {
            assertEquals(SC_OK, response.getStatus());
            assertEquals(true, response.getEntity());
        }
        verify(federatedRepoService).reconnectFederationClient(false);
    }

    @Test
    public void testReconnectRtfsTimeout() throws ArtifactoryFederationServiceException {
        doNothing().when(federatedRepoService).reconnectFederationClient(false);
        try (Response response = resource.reconnectToClient(false)) {
            assertEquals(SC_OK, response.getStatus());
        }
        verify(federatedRepoService).reconnectFederationClient(false);
    }

    @Test
    public void testReconnectRtfsException() throws ArtifactoryFederationServiceException {
        doThrow(ArtifactoryFederationServiceException.class).when(federatedRepoService)
                .reconnectFederationClient(false);
        try (Response response = resource.reconnectToClient(false)) {
            assertEquals(SC_FAILED_DEPENDENCY, response.getStatus());
        }
        verify(federatedRepoService).reconnectFederationClient(false);
    }

    @Test
    public void testReconnectRtfsPassesInternalParamToService() throws ArtifactoryFederationServiceException {
        doNothing().when(federatedRepoService).reconnectFederationClient(true);
        try (Response response = resource.reconnectToClient(true)) {
            assertEquals(SC_OK, response.getStatus());
        }
        verify(federatedRepoService).reconnectFederationClient(true);
    }

    @Test
    public void testInternalQueueStatus() {
        when(federatedRepoService.getInternalQueueState()).thenReturn(MemberQueueState.HEALTHY);
        try (Response response = resource.internalQueueStatus()) {
            assertEquals(SC_OK, response.getStatus());
            assertEquals(MemberQueueState.HEALTHY, response.getEntity());
        }
    }

    @Test
    public void testInternalQueueStatusWithException() {
        when(federatedRepoService.getInternalQueueState()).thenThrow(new BadRequestException("Queue error"));
        try (Response response = resource.internalQueueStatus()) {
            assertEquals(SC_BAD_REQUEST, response.getStatus());
            assertEquals("Queue error", response.getEntity());
        }
    }

    @Test
    public void testUpdateQueueState() {
        try (Response response = resource.updateQueueState(MemberQueueState.HEALTHY, 1L)) {
            assertEquals(SC_NO_CONTENT, response.getStatus());
            verify(federatedRepoService).updateCursor(MemberQueueState.HEALTHY, 1L);
        }
    }

    @Test
    public void testUpdateQueueStateWithException() {
        doThrow(new BadRequestException("Queue error")).when(federatedRepoService)
                .updateCursor(MemberQueueState.HEALTHY, 1L);
        try (Response response = resource.updateQueueState(MemberQueueState.HEALTHY, 1L)) {
            assertEquals(SC_BAD_REQUEST, response.getStatus());
            verify(federatedRepoService).updateCursor(MemberQueueState.HEALTHY, 1L);
            assertEquals("Queue error", response.getEntity());
        }
    }

    @Test
    public void testUpdatePropagateCreatedTimeProperty() throws ArtifactoryFederationServiceException {
        try (Response response = resource.updateShouldPropagateCreatedProperty(true)) {
            assertEquals(SC_OK, response.getStatus());
            verify(federatedRepoService).updatePropagateCreatedTimeProperty(true);
        }
    }

    @Test
    public void testUpdatePropagateCreatedTimePropertyWithException() throws ArtifactoryFederationServiceException {
        doThrow(new ArtifactoryFederationServiceException("error")).when(federatedRepoService)
                .updatePropagateCreatedTimeProperty(true);
        try (Response response = resource.updateShouldPropagateCreatedProperty(true)) {
            assertEquals(SC_INTERNAL_SERVER_ERROR, response.getStatus());
            verify(federatedRepoService).updatePropagateCreatedTimeProperty(true);
            assertEquals("error", response.getEntity());
        }
    }

    @Test
    public void testUpdatePropagateCreatedTimePropertyWithoutQueryParam() throws ArtifactoryFederationServiceException {
        try (Response response = resource.updateShouldPropagateCreatedProperty(null)) {
            assertEquals(SC_BAD_REQUEST, response.getStatus());
            verify(federatedRepoService, never()).updatePropagateCreatedTimeProperty(anyBoolean());
            assertEquals("Query param 'enabled' is required for updating the property", response.getEntity());
        }
    }

    @Test
    public void testSubmitTaskSuccessfully() {
        TaskRequest taskRequest = new TaskRequest(1, "repo", "1w2w3e4e", "http://localhost:8082/");
        try (Response response = resource.submitTask(taskRequest)) {
            assertEquals(SC_ACCEPTED, response.getStatus());
            assertEquals("Task was submitted successfully", response.getEntity());
            verify(federatedRepoService).submitTask(taskRequest);
        }
    }

    @Test
    public void testSubmitTaskFails() {
        TaskRequest taskRequest = new TaskRequest(1, "repo", "1w2w3e4e", "http://localhost:8082/");
        doThrow(new BinariesTaskException("error", new Throwable(), SC_TOO_MANY_REQUESTS)).when(federatedRepoService)
                .submitTask(taskRequest);

        try (Response response = resource.submitTask(taskRequest)) {
            assertEquals(SC_TOO_MANY_REQUESTS, response.getStatus());
            assertEquals("error", response.getEntity());
            verify(federatedRepoService).submitTask(taskRequest);
        }
    }

    @Test
    void testFederatedBinaryProviderV2Check() {
        try (Response response = resource.federatedBinaryProviderV2Check()) {
            verify(federatedRepoService).isFederatedBinaryProviderV2Active();
            assertEquals(HttpStatus.SC_OK, response.getStatus());
        }
    }

    @Test
    public void testFederatedBinaryProviderV2CheckFails() {
        doThrow(new BinariesTaskException("error", new Throwable(), SC_INTERNAL_SERVER_ERROR)).when(
                        federatedRepoService)
                .isFederatedBinaryProviderV2Active();

        try (Response response = resource.federatedBinaryProviderV2Check()) {
            assertEquals(SC_INTERNAL_SERVER_ERROR, response.getStatus());
            assertEquals("error", response.getEntity());
            verify(federatedRepoService).isFederatedBinaryProviderV2Active();
        }
    }

    @Test
    public void testGetInconsistentRepos() {
        Set<String> inconsistentRepos = Set.of("repo1", "repo2");
        when(federatedRepoService.getInconsistentRepos()).thenReturn(inconsistentRepos);
        try (Response response = resource.getInconsistentRepositories()) {
            assertEquals(SC_OK, response.getStatus());
            assertEquals(inconsistentRepos, response.getEntity());
        }
    }

    @Test
    public void testGetInconsistentReposFails() {
        when(federatedRepoService.getInconsistentRepos()).thenThrow(new BadRequestException("error"));
        try (Response response = resource.getInconsistentRepositories()) {
            assertEquals(SC_BAD_REQUEST, response.getStatus());
            assertEquals("Failed to get inconsistent repositories due to: error", response.getEntity());
        }
    }

    @Test
    public void testForceRemoveMember() throws ArtifactoryFederationServiceException {
        String url = "http://artifactory.com/artifactory";
        when(federatedRepoService.forceRemoveMember(url)).thenReturn(MemberRemovalResponse.newBuilder()
                .setSuccess(true).setMessage("Member %s/%s removed".formatted("remote", "remoteKey")).build());
        Response response = resource.forceRemoveMember(url);
        assertEquals(SC_OK, response.getStatus());
        assertEquals("Member %s/%s removed".formatted("remote", "remoteKey"), response.getEntity());
        verify(federatedRepoService).forceRemoveMember(url);
    }

    @Test
    public void testForceRemoveMemberFails() throws ArtifactoryFederationServiceException {
        String url = "http://artifactory.com/artifactory";
        when(federatedRepoService.forceRemoveMember(url)).thenThrow(new ArtifactoryFederationServiceException("error"));
        Response response = resource.forceRemoveMember(url);
        assertEquals(SC_BAD_REQUEST, response.getStatus());
        assertEquals("error", response.getEntity());
        verify(federatedRepoService).forceRemoveMember(url);
    }

    @Test
    public void testTriggerFederatedReposStatesSnapshot() throws ArtifactoryFederationServiceException {
        String message = "Federated repos states snapshot triggered successfully";
        when(federatedRepoService.snapshotFederatedRepos()).thenReturn(Response.ok().entity(message).build());
        try (Response response = resource.triggerFederatedReposStateSnapshot()) {
            assertEquals(SC_OK, response.getStatus());
            assertEquals(message, response.getEntity());
        }
        verify(federatedRepoService).snapshotFederatedRepos();
    }

    @Test
    public void testTriggerFederatedReposStatesFails() throws ArtifactoryFederationServiceException {
        doThrow(new ArtifactoryFederationServiceException("Failed to take snapshot."))
                .when(federatedRepoService).snapshotFederatedRepos();
        try (Response response = resource.triggerFederatedReposStateSnapshot()) {
            assertEquals(SC_INTERNAL_SERVER_ERROR, response.getStatus());
            assertEquals("Failed to take snapshot.", response.getEntity());
        }

    }

    @ParameterizedTest
    @MethodSource("migrationToolArgs")
    public void testRtfsMigrationEndpointExposedOnlyToMigrationTool(String userAgent, String version,
            int statusCode) {
        mockMigrationToolHeaders(userAgent, version);

        FederationMigrationStatus status = FederationMigrationStatus.MIGRATION_STARTED;
        try (Response response = resource.migrationStatus(status, false)) {
            assertEquals(statusCode, response.getStatus());
        }
        VerificationMode timesOrNever = statusCode == SC_CREATED ? times(1) : never();
        verify(federatedRepoService, timesOrNever).updateFederationStatus(status, false);
    }

    public static Stream<Arguments> responseProvider() {
        return Stream.of(
                Arguments.of(null, 200, null, false),
                Arguments.of("error bad request", 400,
                        "Validation of federated repository failed: error bad request",
                        false),
                Arguments.of("error conflict", 409,
                        "Validation of federated repository failed: error conflict", true),
                Arguments.of("error bad request", 400,
                        "Validation of federated repository failed: error bad request",
                        true),
                Arguments.of("error conflict", 409,
                        "Validation of federated repository failed: error conflict", false)
        );
    }

    private void createRequest(FederatedRepoResource resource) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        lenient().when(request.getHeader(ArtifactoryRequest.JFROG_OVERRIDE_BASE_URL))
                .thenReturn("http://localhost:8080/artifactory");
        lenient().when(request.getHeader(ArtifactoryRequest.ORIGINAL_CLIENT_URI))
                .thenReturn("http://localhost:8080/artifactory");
        resource.httpRequest = request;
    }

    @ParameterizedTest
    @MethodSource("gridTopologyChangeSuccessScenarios")
    public void testNotifyGridTopologyChange_success(org.artifactory.federation.GridTopologyChangeType changeType,
            org.artifactory.model.GridTopology gridTopology) {
        GridTopologyChangeEvent event = GridTopologyChangeEvent.builder()
                .gridTopologyId("grid-topology-123")
                .changeType(changeType)
                .gridTopology(gridTopology)
                .build();

        doNothing().when(federatedRepoService).handleGridTopologyChange(gridTopology, changeType,
                "grid-topology-123");
        try (Response response = resource.notifyGridTopologyChange(event)) {
            assertEquals(SC_OK, response.getStatus());
            assertEquals(GRID_TOPOLOGY_CHANGE_PROCESSED_SUCCESSFULLY, response.getEntity());
        }
        verify(federatedRepoService).handleGridTopologyChange(gridTopology, changeType,
                "grid-topology-123");
    }

    public static Stream<Arguments> gridTopologyChangeSuccessScenarios() {
        org.artifactory.model.GridTopology gridTopology1 = org.artifactory.model.GridTopology.builder()
                .gridTopologyId("grid-topology-123")
                .topologyType(MIRROR)
                .additionalSites(List.of("https://jpd1.example.com/artifactory"))
                .build();
        org.artifactory.model.GridTopology gridTopology2 = org.artifactory.model.GridTopology.builder()
                .gridTopologyId("grid-topology-123")
                .topologyType(MIRROR)
                .additionalSites(List.of("https://jpd1.example.com/artifactory",
                        "https://jpd2.example.com/artifactory"))
                .build();
        return Stream.of(
                Arguments.of(org.artifactory.federation.GridTopologyChangeType.CREATED, gridTopology1),
                Arguments.of(org.artifactory.federation.GridTopologyChangeType.UPDATED, gridTopology2),
                Arguments.of(org.artifactory.federation.GridTopologyChangeType.DELETED, null)
        );
    }

    @ParameterizedTest
    @MethodSource("gridTopologyIdValidationScenarios")
    public void testNotifyGridTopologyChange_invalidGridTopologyId(String gridTopologyId) {
        GridTopologyChangeEvent event = GridTopologyChangeEvent.builder()
                .gridTopologyId(gridTopologyId)
                .changeType(org.artifactory.federation.GridTopologyChangeType.CREATED)
                .gridTopology(org.artifactory.model.GridTopology.builder().build())
                .build();

        try (Response response = resource.notifyGridTopologyChange(event)) {
            assertEquals(SC_BAD_REQUEST, response.getStatus());
            assertEquals("Grid topology change event must include a gridTopologyId", response.getEntity());
        }
        verify(federatedRepoService, never()).handleGridTopologyChange(any(), any(), anyString());
    }

    public static Stream<Arguments> gridTopologyIdValidationScenarios() {
        return Stream.of(
                Arguments.of((String) null),
                Arguments.of("")
        );
    }

    @ParameterizedTest
    @MethodSource("missingGridTopologyScenarios")
    public void testNotifyGridTopologyChange_missingGridTopology(
            org.artifactory.federation.GridTopologyChangeType changeType) {
        GridTopologyChangeEvent event = GridTopologyChangeEvent.builder()
                .gridTopologyId("grid-topology-123")
                .changeType(changeType)
                .gridTopology(null)
                .build();

        try (Response response = resource.notifyGridTopologyChange(event)) {
            assertEquals(SC_BAD_REQUEST, response.getStatus());
            assertEquals("Grid topology data is required for CREATED and UPDATED events", response.getEntity());
        }
        verify(federatedRepoService, never()).handleGridTopologyChange(any(), any(), anyString());
    }

    public static Stream<Arguments> missingGridTopologyScenarios() {
        return Stream.of(
                Arguments.of(org.artifactory.federation.GridTopologyChangeType.CREATED),
                Arguments.of(org.artifactory.federation.GridTopologyChangeType.UPDATED)
        );
    }

    @Test
    public void testNotifyGridTopologyChange_nullEvent_shouldReturnBadRequest() {
        try (Response response = resource.notifyGridTopologyChange(null)) {
            assertEquals(SC_BAD_REQUEST, response.getStatus());
            assertEquals("Grid topology change event is required", response.getEntity());
        }
        verify(federatedRepoService, never()).handleGridTopologyChange(any(), any(), anyString());
    }

    @Test
    public void testNotifyGridTopologyChange_exception() {
        org.artifactory.model.GridTopology gridTopology = org.artifactory.model.GridTopology.builder()
                .gridTopologyId("grid-topology-123")
                .topologyType(MIRROR)
                .additionalSites(List.of("https://jpd1.example.com/artifactory"))
                .build();
        GridTopologyChangeEvent event = GridTopologyChangeEvent.builder()
                .gridTopologyId("grid-topology-123")
                .changeType(org.artifactory.federation.GridTopologyChangeType.CREATED)
                .gridTopology(gridTopology)
                .build();

        doThrow(new RuntimeException("Database error")).when(federatedRepoService)
                .handleGridTopologyChange(gridTopology,
                        org.artifactory.federation.GridTopologyChangeType.CREATED, "grid-topology-123");
        try (Response response = resource.notifyGridTopologyChange(event)) {
            assertEquals(SC_INTERNAL_SERVER_ERROR, response.getStatus());
            assertTrue(((String) response.getEntity()).contains("Failed to process grid topology change notification"));
        }
        verify(federatedRepoService).handleGridTopologyChange(gridTopology,
                org.artifactory.federation.GridTopologyChangeType.CREATED, "grid-topology-123");
    }

    private static Stream<Arguments> tenantMoveStatuses() {
        return Stream.of(Arguments.of(TenantMoveStatus.NOT_STARTED),
                Arguments.of(TenantMoveStatus.IMPORT_IN_PROGRESS),
                Arguments.of(TenantMoveStatus.IMPORT_COMPLETED),
                Arguments.of(TenantMoveStatus.IMPORT_FAILED));
    }

    private static Stream<Arguments> migrationToolArgs() {
        return Stream.of(
                Arguments.of(MIGRATION_TOOL_USER_AGENT,
                        MIN_SUPPORTED_MIGRATION_TOOL_VERSION.toString(), SC_CREATED),
                Arguments.of(MIGRATION_TOOL_USER_AGENT, "1.0", SC_BAD_REQUEST),
                Arguments.of(null,
                        MIN_SUPPORTED_MIGRATION_TOOL_VERSION.toString(), SC_BAD_REQUEST),
                Arguments.of(null, null, SC_BAD_REQUEST)
        );
    }

    private void mockMigrationToolHeaders() {
        mockMigrationToolHeaders(MIGRATION_TOOL_USER_AGENT, MIN_SUPPORTED_MIGRATION_TOOL_VERSION.toString());
    }

    private void mockMigrationToolHeaders(String userAgent, String version) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        lenient().when(request.getHeader(HttpHeaders.USER_AGENT)).thenReturn(userAgent);
        lenient().when(request.getHeader("Version")).thenReturn(version);
        resource.httpRequest = request;
    }
}