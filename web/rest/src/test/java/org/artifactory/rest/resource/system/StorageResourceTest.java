package org.artifactory.rest.resource.system;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.core.Response;
import org.apache.http.HttpStatus;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.backup.InternalBackupService;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.schedule.TaskService;
import org.artifactory.sh.acl.ArtifactoryBinaryService;
import org.artifactory.storage.StorageService;
import org.artifactory.storage.fs.service.ConfigsService;
import org.artifactory.storage.fs.service.ConfigsServiceKey;
import org.artifactory.util.AolUtils;
import org.jfrog.storage.common.prune.PruneRequestModel;
import org.jfrog.storage.common.prune.PruneResponseModel;
import org.jfrog.storage.common.prune.PruneStatusReport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StorageResourceTest {
    private StorageResource storageResource;
    @Mock
    private StorageService storageService;
    @Mock
    private InternalBackupService backupService;
    @Mock
    private ArtifactoryBinaryService binaryStore;
    @Mock
    private TaskService taskService;
    @Mock
    private ConfigsService configsService;
    @Mock
    private HttpServletResponse httpResponse;

    private AutoCloseable autoCloseable;


    @BeforeEach
    public void setup() {
        this.autoCloseable = MockitoAnnotations.openMocks(this);
        this.storageResource =
                spy(new StorageResource(storageService, backupService, binaryStore, taskService, configsService,
                        httpResponse));
    }

    @AfterEach
    public void cleanup() throws Exception {
        this.autoCloseable.close();
    }

    @Test
    void unauthorizedAccess() {
        // Mock static method AolUtils.assertDashboardUser to throw an exception
        try (MockedStatic<AolUtils> aolUtils = Mockito.mockStatic(AolUtils.class)) {
            aolUtils.when(AolUtils::assertDashboardUser).thenThrow(new BadRequestException("TEST"));

            try (Response response = storageResource.pruneStart(any(PruneRequestModel.class))) {
                assertEquals(HttpStatus.SC_FORBIDDEN, response.getStatus());
                assertEquals("TEST", ((PruneResponseModel) response.getEntity()).info());
            }
        }
    }

    @Test
    void activateGc() {
        try (Response response = storageResource.activateGc()) {
            assertEquals(HttpStatus.SC_OK, response.getStatus());
        }
    }

    @Test
    void activateGcWithError() throws IOException {
        when(httpResponse.getOutputStream()).thenReturn(new ServletOutputStream() {
            @Override
            public void write(int b) {
            }

            @Override
            public boolean isReady() {
                return false;
            }

            @Override
            public void setWriteListener(WriteListener writeListener) {
            }
        });
        doAnswer(invocation -> {
            BasicStatusHolder statusHolder = invocation.getArgument(0);
            statusHolder.error("TEST", mock(Logger.class));
            return null;
        }).when(storageService).callManualGarbageCollect(any(BasicStatusHolder.class));
        try (Response response = storageResource.activateGc()) {
            assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, response.getStatus());
        }
    }

    @ParameterizedTest
    @MethodSource("pruneStatusSupplier")
    void pruneStatus(PruneStatusReport report, int expected) {
        when(configsService.getConfig(any(ConfigsServiceKey.class), eq(PruneStatusReport.class))).thenReturn(report);
        try (Response response = storageResource.pruneStatus()) {
            assertEquals(expected, response.getStatus());
        }
    }

    private static Stream<Arguments> pruneStatusSupplier() {
        PruneStatusReport report = new PruneStatusReport();
        report.setLastHandledDirectory(new PruneStatusReport.LastHandledDirectory("SomeDir"));
        return Stream.of(
                Arguments.of(null, HttpStatus.SC_PRECONDITION_FAILED),
                Arguments.of(new PruneStatusReport(), HttpStatus.SC_OK)
        );
    }

    @ParameterizedTest
    @MethodSource("pruneStartSupplier")
    void pruneStart(String startFromDir, String expected, int expectedCode,
            Exception throwException, boolean taskCreationFailed) {
        // Mock static method AolUtils.assertDashboardUser
        try (MockedStatic<AolUtils> aolUtils = Mockito.mockStatic(AolUtils.class)) {
            aolUtils.when(AolUtils::assertDashboardUser).thenAnswer((Answer<Void>) invocation -> null);

            // Mock createAndRunPruneTask method
            if (throwException == null) {
                BasicStatusHolder statusHolder = mock(BasicStatusHolder.class);
                when(statusHolder.isError()).thenReturn(taskCreationFailed);
                doReturn(statusHolder).when(storageResource).createAndRunPruneTask(any(PruneRequestModel.class));
            } else {
                doThrow(throwException).when(storageResource).createAndRunPruneTask(any(PruneRequestModel.class));
            }

            when(configsService.getConfig(any(ConfigsServiceKey.class), eq(PruneStatusReport.class))).thenReturn(
                    new PruneStatusReport());

            PruneRequestModel pruneRequestModel = new PruneRequestModel(true, startFromDir, 0);
            try (Response response = storageResource.pruneStart(pruneRequestModel)) {
                assertEquals(expectedCode, response.getStatus());
                assertEquals(expected, ((PruneResponseModel) response.getEntity()).info());
            }
        }
    }

    @Test
    void testOptimize() {
        try (Response response = storageResource.optimize()) {
            assertEquals(202, response.getStatus());
            verify(storageService, times(1)).runShardingBalancer(any());
        }
    }

    @Test
    void testOptimizeErrorConflict() {
        doAnswer(invocation -> {
            BasicStatusHolder statusHolder = invocation.getArgument(0);
            statusHolder.error("Conflict error", HttpStatus.SC_CONFLICT, new RuntimeException("Test exception"),
                    mock(Logger.class));
            return null;
        }).when(storageService).runShardingBalancer(any(BasicStatusHolder.class));

        try (Response response = storageResource.optimize()) {
            assertEquals(HttpStatus.SC_CONFLICT, response.getStatus());
        }
    }

    @Test
    void testOptimizeErrorGeneral() {
        doAnswer(invocation -> {
            BasicStatusHolder statusHolder = invocation.getArgument(0);
            statusHolder.error("Error message", new RuntimeException("Test exception"),
                    mock(Logger.class));
            return null;
        }).when(storageService).runShardingBalancer(any(BasicStatusHolder.class));

        try (Response response = storageResource.optimize()) {
            assertEquals(HttpStatus.SC_PRECONDITION_FAILED, response.getStatus());
        }
    }

    private static Stream<Arguments> pruneStartSupplier() {
        PruneStatusReport report = new PruneStatusReport();
        report.setLastHandledDirectory(new PruneStatusReport.LastHandledDirectory("SomeDir"));
        return Stream.of(
                Arguments.of(null, "Pruning Unreferenced Data task has been submitted",
                        HttpStatus.SC_ACCEPTED, null, false),
                Arguments.of("SomeDir", "Prune Unreferenced Data task resumes from directory SomeDir",
                        HttpStatus.SC_ACCEPTED, null, false),
                Arguments.of("SomeDir", "TEST",
                        HttpStatus.SC_FORBIDDEN, new BadRequestException("TEST"), false),
                Arguments.of("SomeDir", "Cannot start Pruning Unreferenced Data task: TEST",
                        HttpStatus.SC_INTERNAL_SERVER_ERROR, new RuntimeException("TEST"), false),
                Arguments.of("SomeDir", "Pruning Unreferenced Data task cannot be started",
                        HttpStatus.SC_PRECONDITION_FAILED, null, true)
        );
    }

    @ParameterizedTest
    @MethodSource("pruneStopSupplier")
    void pruneStop(PruneStatusReport report, int expectedCode, String expectedMessage, Exception throwException) {
        // Mock static method AolUtils.assertDashboardUser
        try (MockedStatic<AolUtils> aolUtils = Mockito.mockStatic(AolUtils.class)) {
            if (throwException instanceof BadRequestException) {
                aolUtils.when(AolUtils::assertDashboardUser).thenThrow(throwException);
            } else {
                aolUtils.when(AolUtils::assertDashboardUser).thenAnswer((Answer<Void>) invocation -> null);
            }

            when(configsService.getConfig(any(ConfigsServiceKey.class), eq(PruneStatusReport.class)))
                    .thenReturn(report);
            if (throwException == null) {
                when(configsService.addOrUpdateConfig(any(ConfigsServiceKey.class), eq(true), anyLong()))
                        .thenReturn(true);
            } else {
                when(configsService.addOrUpdateConfig(any(ConfigsServiceKey.class), eq(true), anyLong()))
                        .thenThrow(throwException);
            }

            try (Response response = storageResource.pruneStop()) {
                assertEquals(expectedCode, response.getStatus());
                assertEquals(expectedMessage, ((PruneResponseModel) response.getEntity()).info());
            }
        }
    }

    private static Stream<Arguments> pruneStopSupplier() {
        PruneStatusReport report = new PruneStatusReport();
        report.setLastHandledDirectory(new PruneStatusReport.LastHandledDirectory("SomeDir"));
        return Stream.of(
                Arguments.of(null, HttpStatus.SC_PRECONDITION_FAILED, "No running Prune task found",
                        null),
                Arguments.of(new PruneStatusReport(), HttpStatus.SC_ACCEPTED, "Prune task stop request submitted",
                        null),
                Arguments.of(report, HttpStatus.SC_ACCEPTED, "Prune task stop request submitted",
                        null),
                Arguments.of(new PruneStatusReport(), HttpStatus.SC_INTERNAL_SERVER_ERROR,
                        "Could not stop Prune task due to TEST", new RuntimeException("TEST")),
                Arguments.of(new PruneStatusReport(), HttpStatus.SC_FORBIDDEN, "TEST", new BadRequestException("TEST"))
        );
    }
}