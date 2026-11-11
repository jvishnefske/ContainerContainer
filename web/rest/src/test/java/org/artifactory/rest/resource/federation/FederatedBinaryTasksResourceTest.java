package org.artifactory.rest.resource.federation;

import ch.qos.logback.classic.Level;
import com.jfrog.test.log.MemoryAppender;
import com.jfrog.test.log.MemoryAppenderUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Response;
import lombok.Cleanup;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.federation.FederatedBinaryTasksService;
import org.artifactory.federation.FederatedRepoService;
import org.artifactory.federation.FederationServiceProxy;
import org.artifactory.repo.PersistedRemoteTaskInfo;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.storage.StorageException;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.storage.binstore.providers.base.BinariesTasksInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static ch.qos.logback.classic.Level.INFO;
import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;


/**
 * @author annakv
 */
@ExtendWith(MockitoExtension.class)
class FederatedBinaryTasksResourceTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    FederatedBinaryTasksService federatedBinaryTasksService;

    @Mock
    FederationServiceProxy<FederatedBinaryTasksService> federatedBinariesTasksServiceProxy;

    @Mock
    FederatedRepoService federatedRepoService;

    @Mock
    HttpServletRequest httpRequest;
    @Mock
    ArtifactoryHome home;
    @Mock
    private ArtifactorySystemProperties artifactorySystemProperties;


    // Resource under test
    @InjectMocks
    FederatedBinaryTasksResource resource;

    @BeforeEach
    public void setup() {
        lenient().when(federatedBinariesTasksServiceProxy.getService()).thenReturn(federatedBinaryTasksService);
        lenient().when(httpRequest.getRequestURI()).thenReturn("http://localhost:8080/artifactory/api/federation/binaries");
        resource.httpRequest = httpRequest;
        lenient().when(home.getArtifactoryProperties()).thenReturn(artifactorySystemProperties);
        lenient().when(artifactorySystemProperties.getBooleanProperty(
                ConstantValues.federatedForceLoadFederatedProviderV2ForTestDefault)).thenReturn(false);
        ArtifactoryHome.bind(home);
    }

    @AfterEach
    public void tearDown() {
        ArtifactoryHome.unbind();
    }

    @Test
    void testGetList() {
        List<PersistedRemoteTaskInfo> tasks = List.of(new PersistedRemoteTaskInfo("", "", 0, 10));
        when(federatedBinaryTasksService.getFailingTasks(anyString(), any(), any()))
                .thenReturn(tasks);
        try (Response response = resource.list("repo", null, null)) {
            assertEquals(SC_OK, response.getStatus());
            assertEquals(tasks, response.getEntity());
        }
    }

    @Test
    void testGetListFails() {
        when(federatedBinaryTasksService.getFailingTasks(anyString(), any(), any()))
                .thenThrow(StorageException.class);
        try (Response response = resource.list("repo", null, null)) {
            assertEquals(SC_INTERNAL_SERVER_ERROR, response.getStatus());
        }
    }

    @Test
    void testGetListNoRepo() {
        try (Response response = resource.list(null, null, null)) {
            assertEquals(SC_BAD_REQUEST, response.getStatus());
        }
    }

    @Test
    void testReplay() {
        when(federatedBinaryTasksService.replayTasks(anyString(), anyString()))
                .thenReturn(1);
        try (Response response = resource.replayTasks("repo", "sha1")) {
            assertEquals(SC_OK, response.getStatus());
            FederatedBinaryTasksResource.FetchFailuresReplayResult result =
                    (FederatedBinaryTasksResource.FetchFailuresReplayResult) response.getEntity();
            assertEquals(1, result.replayTasks());

        }
    }

    @Test
    void testReplayFail() {
        when(federatedBinaryTasksService.replayTasks(anyString(), anyString()))
                .thenThrow(StorageException.class);
        try (Response response = resource.replayTasks("repo", "sha1")) {
            assertEquals(SC_INTERNAL_SERVER_ERROR, response.getStatus());
        }
    }

    @Test
    void testReplayNoRepo() {
        try (Response response = resource.replayTasks(null, "sha1")) {
            assertEquals(SC_BAD_REQUEST, response.getStatus());
        }
    }

    @Test
    void testReplayWrongRepo() {
        when(federatedBinaryTasksService.replayTasks(anyString(), anyString()))
                .thenThrow(BadRequestException.class);
        try (Response response = resource.replayTasks("repo", "sha1")) {
            assertEquals(SC_BAD_REQUEST, response.getStatus());
        }
    }

    @Test
    void testDelete() {
        when(federatedBinaryTasksService.deleteRepoFailedTasksByBinary(anyString(), anyString()))
                .thenReturn(1);
        try (Response response = resource.deleteTasks("repo", "sha1")) {
            assertEquals(SC_OK, response.getStatus());
            FederatedBinaryTasksResource.FetchFailuresDeleteResult result =
                    (FederatedBinaryTasksResource.FetchFailuresDeleteResult) response.getEntity();
            assertEquals(1, result.deletedTasks());
        }
    }

    @Test
    void testDeleteFail() {
        when(federatedBinaryTasksService.deleteRepoFailedTasksByBinary(anyString(), anyString()))
                .thenThrow(StorageException.class);
        try (Response response = resource.deleteTasks("repo", "sha1")) {
            assertEquals(SC_INTERNAL_SERVER_ERROR, response.getStatus());
        }
    }

    @Test
    void testDeleteNoRepo() {
        try (Response response = resource.deleteTasks(null, "sha1")) {
            assertEquals(SC_BAD_REQUEST, response.getStatus());
        }
    }

    @Test
    void testGetCount() {
        BinariesTasksInfo info = new BinariesTasksInfo(10L, 15L);
        when(federatedBinaryTasksService.getBinariesTasksInfo(anyString())).thenReturn(info);
        try (Response response = resource.tasksInfo("repo")) {
            assertEquals(SC_OK, response.getStatus());
            assertEquals(info, response.getEntity());
        }
    }

    @Test
    void testGetCountFails() {
        when(federatedBinaryTasksService.getBinariesTasksInfo(anyString()))
                .thenThrow(StorageException.class);
        try (Response response = resource.tasksInfo("repo")) {
            assertEquals(SC_INTERNAL_SERVER_ERROR, response.getStatus());
        }
    }

    @Test
    void testGetCountNoRepo() {
        try (Response response = resource.tasksInfo(null)) {
            assertEquals(SC_BAD_REQUEST, response.getStatus());
        }
    }

    @Test
    void testGetCountWrongRepo() {
        when(federatedBinaryTasksService.getBinariesTasksInfo(anyString()))
                .thenThrow(BadRequestException.class);
        try (Response response = resource.tasksInfo("repo")) {
            assertEquals(SC_BAD_REQUEST, response.getStatus());
        }
    }

    @Test
    void testIsRtfsBinaryTasksEnabled() {
        when(federatedRepoService.isBinaryTasksEnabled()).thenReturn(true);
        @Cleanup Response response = resource.isRtfsBinaryTasksEnabled();
        assertEquals(SC_OK, response.getStatus());
        FederatedBinaryTasksResource.FeatureStateResponse result =
                (FederatedBinaryTasksResource.FeatureStateResponse) response.getEntity();
        assertTrue(result.enabled());
        assertFalse(result.active());
    }

    @Test
    void testIsRtfsBinaryTasksEnabledFails() {
        when(federatedRepoService.isBinaryTasksEnabled()).thenThrow(StorageException.class);
        @Cleanup Response response = resource.isRtfsBinaryTasksEnabled();
        assertEquals(SC_INTERNAL_SERVER_ERROR, response.getStatus());
    }

    @Test
    void testSwitchRtfsBinaryTasksEnableButNotActive() {
        MemoryAppender logAppender = MemoryAppenderUtil.createMemoryAppender(FederatedBinaryTasksResource.class, INFO);
        when(federatedRepoService.isMigrationOver()).thenReturn(true);
        @Cleanup Response response = resource.switchRtfsBinaryTasks(true);
        assertEquals(SC_OK, response.getStatus());
        FederatedBinaryTasksResource.FeatureStateResponse result =
                (FederatedBinaryTasksResource.FeatureStateResponse) response.getEntity();
        assertTrue(result.enabled());
        assertFalse(result.active());
        assertTrue(logAppender.contains("Binary tasks is set to use RTFS", Level.INFO));
        assertTrue(logAppender.contains("Federated binary tasks provider V2 is inactive, restart to activate!",
                Level.INFO));
    }

    @Test
    void testSwitchRtfsBinaryTasksEnableAlreadyActive() {
        MemoryAppender logAppender = MemoryAppenderUtil.createMemoryAppender(FederatedBinaryTasksResource.class, INFO);
        when(federatedRepoService.isMigrationOver()).thenReturn(true);
        when(federatedRepoService.isFederatedBinaryProviderV2Active()).thenReturn(true);
        @Cleanup Response response = resource.switchRtfsBinaryTasks(true);
        assertEquals(SC_OK, response.getStatus());
        FederatedBinaryTasksResource.FeatureStateResponse result =
                (FederatedBinaryTasksResource.FeatureStateResponse) response.getEntity();
        assertTrue(result.enabled());
        assertTrue(result.active());
        assertTrue(logAppender.contains("Binary tasks is set to use RTFS", Level.INFO));
        assertTrue(logAppender.contains("Federated binary tasks provider V2 is active", Level.INFO));
    }


    @Test
    void testSwitchRtfsBinaryTasksDisableActive() {
        MemoryAppender logAppender = MemoryAppenderUtil.createMemoryAppender(FederatedBinaryTasksResource.class, INFO);
        when(federatedRepoService.isFederatedBinaryProviderV2Active()).thenReturn(true);
        @Cleanup Response response = resource.switchRtfsBinaryTasks(false);
        assertEquals(SC_OK, response.getStatus());
        FederatedBinaryTasksResource.FeatureStateResponse result =
                (FederatedBinaryTasksResource.FeatureStateResponse) response.getEntity();
        assertFalse(result.enabled());
        assertTrue(result.active());
        assertTrue(logAppender.contains("Binary tasks is no longer set to use RTFS", Level.INFO));
        assertTrue(logAppender.contains("Federated binary tasks provider V2 is active, restart to deactivate!",
                Level.INFO));
    }

    @Test
    void testSwitchRtfsBinaryTasksDisableInActive() {
        MemoryAppender logAppender = MemoryAppenderUtil.createMemoryAppender(FederatedBinaryTasksResource.class, INFO);
        when(federatedRepoService.isFederatedBinaryProviderV2Active()).thenReturn(false);
        @Cleanup Response response = resource.switchRtfsBinaryTasks(false);
        assertEquals(SC_OK, response.getStatus());
        FederatedBinaryTasksResource.FeatureStateResponse result =
                (FederatedBinaryTasksResource.FeatureStateResponse) response.getEntity();
        assertFalse(result.enabled());
        assertFalse(result.active());
        assertTrue(logAppender.contains("Binary tasks is no longer set to use RTFS", Level.INFO));
        assertTrue(logAppender.contains("Federated binary tasks provider V2 is inactive",
                Level.INFO));
    }

    @Test
    void testSwitchRtfsBinaryTasksFails() {
        when(federatedRepoService.isMigrationOver()).thenReturn(true);
        doThrow(StorageException.class).when(federatedRepoService).updateBinaryTasksFeatureStatus(anyBoolean());
        @Cleanup Response response = resource.switchRtfsBinaryTasks(true);
        assertEquals(SC_INTERNAL_SERVER_ERROR, response.getStatus());
    }

    @Test
    void testSwitchRtfsBinaryTasksEnableMigrationNotOver() {
        MemoryAppender logAppender = MemoryAppenderUtil.createMemoryAppender(FederatedBinaryTasksResource.class, INFO);
        when(federatedRepoService.isMigrationOver()).thenReturn(false);
        @Cleanup Response response = resource.switchRtfsBinaryTasks(true);
        assertEquals(SC_BAD_REQUEST, response.getStatus());
        FederatedBinaryTasksResource.ErrorResult entity =
                (FederatedBinaryTasksResource.ErrorResult) response.getEntity();
        assertEquals("Failed enabling Binary Tasks v2", entity.message());
        assertEquals("Migration to RTFS is not over yet", entity.reason());
        assertTrue(logAppender
                .contains("An attempt to enable Binary Tasks v2 failed since migration to RTFS is not over yet",
                Level.ERROR));
    }

}