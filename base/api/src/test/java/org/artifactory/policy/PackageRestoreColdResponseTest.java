package org.artifactory.policy;

import org.artifactory.policy.restore.packages.PackageRestoreColdResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PackageRestoreColdResponseTest {
    @Test
    void packageRestoreColdResponseShouldReturnCorrectRunSummaryId() {
        PackageRestoreColdResponse response = PackageRestoreColdResponse.builder()
                .runSummaryId(123)
                .build();
        assertEquals(123, response.runSummaryId());
    }
}
