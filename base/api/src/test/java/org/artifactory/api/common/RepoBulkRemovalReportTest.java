package org.artifactory.api.common;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_FORBIDDEN;
import static org.apache.http.HttpStatus.SC_MULTI_STATUS;
import static org.apache.http.HttpStatus.SC_OK;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
class RepoBulkRemovalReportTest extends ArtifactoryHomeBoundJUnitTest {

    @Test
    void testConstructor_AllOK() {
        RepoRemovalStatusHolder statusHolder = new RepoRemovalStatusHolder();

        RepoBulkRemovalReport report = new RepoBulkRemovalReport(Set.of(statusHolder));

        assertEquals(SC_OK, report.getStatusCode());
        assertEquals("All repositories were removed successfully", report.getStatusMessage());
        assertEquals(1, report.getReports().size());
    }

    @Test
    void testConstructor_AllFailed() {
        RepoRemovalStatusHolder statusHolder = new RepoRemovalStatusHolder();
        statusHolder.error("error", SC_BAD_REQUEST, log);

        RepoBulkRemovalReport report = new RepoBulkRemovalReport(Set.of(statusHolder));

        assertEquals(SC_BAD_REQUEST, report.getStatusCode());
        assertEquals("Repositories batch removal failed", report.getStatusMessage());
        assertEquals(1, report.getReports().size());
    }

    @Test
    void testConstructor_SomeFailed() {
        RepoRemovalStatusHolder statusHolder1 = new RepoRemovalStatusHolder();
        RepoRemovalStatusHolder statusHolder2 = new RepoRemovalStatusHolder();
        statusHolder2.error("error", SC_FORBIDDEN, log);

        RepoBulkRemovalReport report = new RepoBulkRemovalReport(Set.of(statusHolder1, statusHolder2));

        assertEquals(SC_MULTI_STATUS, report.getStatusCode());
        assertEquals("Some repositories failed to be removed", report.getStatusMessage());
        assertEquals(2, report.getReports().size());
    }
}