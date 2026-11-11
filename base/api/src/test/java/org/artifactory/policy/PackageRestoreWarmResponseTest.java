package org.artifactory.policy;

import org.artifactory.policy.restore.packages.PackageRestoreWarmResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PackageRestoreWarmResponseTest {
    @Test
    void packageRestoreColdResponseShouldReturnCorrectRunSummaryId() {
        PackageRestoreWarmResponse response = PackageRestoreWarmResponse.builder()
                .runSummaryId(123)
                .responseMessage("Successful restore")
                .build();
        assertEquals(123, response.runSummaryId());
        assertEquals("Successful restore", response.responseMessage());
    }
}
