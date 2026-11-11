package org.artifactory.api.common;

import ch.qos.logback.classic.Level;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.common.JsonUtils;
import org.json.JSONException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;

import java.util.List;

import static org.apache.http.HttpStatus.SC_FORBIDDEN;
import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_MULTI_STATUS;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_OK;
import static org.artifactory.api.common.RepoRemovalStatusHolder.ARTIFACT_DELETE_FAILURES_EXAMPLES_MAX;
import static org.artifactory.api.common.RepoRemovalStatusHolder.ArtifactDeleteFailure;
import static org.artifactory.api.common.RepoRemovalStatusHolder.RemoveRepositoryFailureReport;
import static org.artifactory.api.common.RepoRemovalStatusHolder.RemoveRepositoryReport;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
class RepoRemovalStatusHolderTest extends ArtifactoryHomeBoundJUnitTest {

    private RepoRemovalStatusHolder statusHolder;

    @BeforeEach
    void setUp() {
        statusHolder = new RepoRemovalStatusHolder("repoKey");
    }

    @Test
    void testIncrementDeletedCount() {
        statusHolder.incrementDeletedCount();
        RemoveRepositoryReport report = statusHolder.getSuccessReport();
        assertEquals(SC_OK, statusHolder.getStatusCode());
        assertEquals(1, report.getDeletedArtifactsCount());
    }

    @Test
    void testAddArtifactDeleteFailure() {
        statusHolder.error("Not Found", SC_NOT_FOUND, log);
        RemoveRepositoryFailureReport report = statusHolder.getFailureReport();
        List<ArtifactDeleteFailure> artifactDeleteFailures = report.getErrors();
        assertEquals(1, artifactDeleteFailures.size());
        assertEquals(new ArtifactDeleteFailure(SC_NOT_FOUND, "Not Found"), artifactDeleteFailures.getFirst());
    }

    @Test
    void testAddMultipleDeleteFailures() {
        statusHolder = new RepoRemovalStatusHolder("repoKey");
        statusHolder.error("Not Found. Path: 'repoKey/path/to/artifact1", SC_NOT_FOUND, log);
        statusHolder.error("Not enough permissions to delete: repoKey/path/to/artifact2", SC_FORBIDDEN, log);
        RemoveRepositoryFailureReport report = statusHolder.getFailureReport();
        List<ArtifactDeleteFailure> artifactDeleteFailures = report.getErrors();
        assertEquals(2, artifactDeleteFailures.size());
        assertEquals(new ArtifactDeleteFailure(SC_NOT_FOUND, "Not Found. Path: 'repoKey/path/to/artifact1"),
                artifactDeleteFailures.getFirst());
        assertEquals(new ArtifactDeleteFailure(SC_FORBIDDEN,
                        "Not enough permissions to delete: repoKey/path/to/artifact2"),
                artifactDeleteFailures.get(1));
        assertEquals(2, report.getDeleteArtifactsFailureCount());
    }

    @Test
    void testAddMultipleDeleteFailures_AssertJson() throws JSONException {
        testAddMultipleDeleteFailures();
        RemoveRepositoryFailureReport report = statusHolder.getFailureReport();
        String reportJson = JsonUtils.getInstance().writeValueAsString(report);
        JSONAssert.assertEquals("""
                {
                  "repoKey" : "repoKey",
                  "statusMsg" : "Cannot delete repository: 'repoKey'. See error report for more info.",
                  "deletedArtifactsCount" : 0,
                  "errors" : [ {
                    "status" : 404,
                    "message" : "Not Found. Path: 'repoKey/path/to/artifact1"
                  }, {
                    "status" : 403,
                    "message" : "Not enough permissions to delete: repoKey/path/to/artifact2"
                  } ],
                  "deleteArtifactsFailureCount" : 2,
                  "success" : false
                }""", reportJson, true);
    }

    @Test
    void testGetFailureReportOneDeletedOneNotFound() {
        statusHolder.incrementDeletedCount();
        statusHolder.error("Not Found", SC_NOT_FOUND, log);
        RemoveRepositoryFailureReport report = statusHolder.getFailureReport();
        assertEquals(SC_MULTI_STATUS, statusHolder.getStatusCode());
        assertEquals(
                "Cannot delete repository: Aborting deletion of repository: 'repoKey' because some artifacts could " +
                        "not be deleted. Delete all artifacts from the repository and try again.",
                statusHolder.getStatusMsg());
        assertEquals(1, report.getDeletedArtifactsCount());
        assertEquals(1, report.getErrors().size());
        assertEquals(SC_NOT_FOUND, report.getErrors().getFirst().status());
        assertEquals("Not Found", report.getErrors().getFirst().message());
    }

    @Test
    void testAddMoreThanMaxFailureExamples() {
        for (int i = 0; i < ARTIFACT_DELETE_FAILURES_EXAMPLES_MAX + 1; i++) {
            statusHolder.error("Not Found", SC_NOT_FOUND, log);
        }
        assertEquals(ARTIFACT_DELETE_FAILURES_EXAMPLES_MAX,
                statusHolder.getFailureReport().getErrors().size());
        assertEquals(ARTIFACT_DELETE_FAILURES_EXAMPLES_MAX + 1,
                statusHolder.getFailureReport().getDeleteArtifactsFailureCount());
    }

    @Test
    void testMutateToRepoLevelStatus_NoErrorsOrWarnings() {
        statusHolder.mutateToRepoLevelStatus();
        assertEquals(SC_OK, statusHolder.getStatusCode());
        assertEquals("Repository 'repoKey' was removed successfully", statusHolder.getStatusMsg());
        assertTrue(statusHolder.hasWarnings());
        assertTrue(statusHolder.getWarnings().isEmpty());
        assertTrue(statusHolder.getErrors().isEmpty());
        assertEquals(0, statusHolder.getFailureReport().getDeleteArtifactsFailureCount());
        assertEquals(0, statusHolder.getFailureReport().getErrors().size());
    }

    @Test
    void testMutateToRepoLevelStatus_WithErrors() {
        statusHolder.error("Error message", SC_FORBIDDEN, log);
        statusHolder.mutateToRepoLevelStatus();
        assertEquals(SC_FORBIDDEN, statusHolder.getStatusCode());
        assertEquals("Error message", statusHolder.getStatusMsg());
        assertTrue(statusHolder.hasWarnings());
        assertTrue(statusHolder.getWarnings().isEmpty());
        assertTrue(statusHolder.getErrors().isEmpty());
        assertEquals(0, statusHolder.getFailureReport().getDeleteArtifactsFailureCount());
        assertEquals(0, statusHolder.getFailureReport().getErrors().size());

    }

    @Test
    void testMutateToRepoLevelStatus_WithWarnings() {
        statusHolder.warn("Warning message", SC_FORBIDDEN, log);
        statusHolder.mutateToRepoLevelStatus();
        assertEquals(SC_FORBIDDEN, statusHolder.getStatusCode());
        assertEquals("Warning message", statusHolder.getStatusMsg());
        assertTrue(statusHolder.hasWarnings());
        assertTrue(statusHolder.getWarnings().isEmpty());
        assertTrue(statusHolder.getErrors().isEmpty());
        assertEquals(0, statusHolder.getFailureReport().getDeleteArtifactsFailureCount());
        assertEquals(0, statusHolder.getFailureReport().getErrors().size());
    }

    //region Tests for Sonar
    @Test
    void testWarnWithThrowable() {
        statusHolder.warn("Warning message", new Exception("Test exception"), log);
        assertTrue(statusHolder.hasWarnings());
    }

    @Test
    void testWarnWithStatusCodeAndThrowable() {
        statusHolder.warn("Warning message", SC_INTERNAL_SERVER_ERROR, new Exception("Test exception"), log);
        assertTrue(statusHolder.hasWarnings());
    }

    @Test
    void testWarnWithLogger() {
        statusHolder.warn("Warning message", log);
        assertTrue(statusHolder.hasWarnings());
    }

    @Test
    void testWarnWithStatusCodeAndLogger() {
        statusHolder.warn("Warning message", SC_INTERNAL_SERVER_ERROR, log);
        assertTrue(statusHolder.hasWarnings());
    }

    @Test
    void testErrorWithThrowable() {
        statusHolder.error("Error message", SC_INTERNAL_SERVER_ERROR, new Exception("Test exception"), log);
        assertFalse(statusHolder.isSuccess());
    }

    @Test
    void testErrorWithMarker() {
        statusHolder.error("Error message", SC_INTERNAL_SERVER_ERROR, log, null);
        assertFalse(statusHolder.isSuccess());
    }

    @Test
    void testErrorWithThrowableAndMarker() {
        statusHolder.error("Error message", SC_INTERNAL_SERVER_ERROR, log, new Exception("Test exception"),
                null);
        assertFalse(statusHolder.isSuccess());
    }

    @Test
    void testErrorWithDifferentLogLevel() {
        statusHolder.errorWithDifferentLogLevel("Error message", SC_INTERNAL_SERVER_ERROR,
                new Exception("Test exception"), log, Level.ERROR);
        assertFalse(statusHolder.isSuccess());
    }

    @Test
    void testGetStatusMsg() {
        statusHolder.mainStatus("Main status message", log);
        assertEquals("Main status message", statusHolder.getStatusMsg());
    }

    @Test
    void testGetStatusCode() {
        statusHolder.mainWarning("Main warning message", SC_INTERNAL_SERVER_ERROR, log);
        assertEquals(SC_INTERNAL_SERVER_ERROR, statusHolder.getStatusCode());
    }

    @Test
    void testGetReport() {
        statusHolder.mainStatus("Main status message", log);
        assertNotNull(statusHolder.getReport());
    }

    @Test
    void testGetSuccessReport() {
        statusHolder.mainStatus("Main status message", log);
        assertNotNull(statusHolder.getSuccessReport());
    }

    @Test
    void testGetFailureReportForSonar() {
        statusHolder.error("Error message", SC_INTERNAL_SERVER_ERROR, log);
        assertNotNull(statusHolder.getFailureReport());
    }

    @Test
    void testIsSuccess() {
        statusHolder.mainStatus("Main status message", log);
        assertTrue(statusHolder.isSuccess());
    }

    @Test
    void testArtifactDeleteFailureToString() {
        RepoRemovalStatusHolder.ArtifactDeleteFailure failure =
                new RepoRemovalStatusHolder.ArtifactDeleteFailure(SC_NOT_FOUND, "Not Found");
        assertEquals("{ statusCode=404, reason=Not Found }", failure.toString());
    }

    @Test
    void testGetReportNoErrorsOrWarnings() {
        RemoveRepositoryReport report = statusHolder.getReport();
        assertTrue(report.isSuccess());
    }

    @Test
    void testGetReportWithErrorsOrWarnings() {
        statusHolder.error("Not Found", SC_NOT_FOUND, log);
        RemoveRepositoryReport report = statusHolder.getReport();
        assertFalse(report.isSuccess());
    }

    @Test
    void testGetFailureReportNoErrorsOrWarnings() {
        RemoveRepositoryFailureReport failureReport = statusHolder.getFailureReport();
        assertNull(failureReport);
    }

    @Test
    void testGetStatusCodeNoMainStatusCodeNoFailures() {
        assertEquals(SC_OK, statusHolder.getStatusCode());
    }

    @Test
    void testGetStatusSuccessMsg() {
        assertEquals("Repository 'repoKey' was removed successfully", statusHolder.getStatusMsg());
    }
    //endregion
}