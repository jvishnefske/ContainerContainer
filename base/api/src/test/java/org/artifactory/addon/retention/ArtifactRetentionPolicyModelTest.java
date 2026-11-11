package org.artifactory.addon.retention;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * @author Oleksandr Legenkiy
 */
class ArtifactRetentionPolicyModelTest {

    @Test
    void testEqualsComparesFieldsFromBaseClass() {
        ArtifactRetentionExecutionStatus status = new ArtifactRetentionExecutionStatus();
        ArtifactRetentionPolicyModel o1 = ArtifactRetentionPolicyModel.builder()
                .lastRunStatus(status)
                .nextExecutionTime(123L)
                .cronExp("exp1") // this field from base class is the only difference
                .build();
        ArtifactRetentionPolicyModel o2 = ArtifactRetentionPolicyModel.builder()
                .lastRunStatus(status)
                .nextExecutionTime(123L)
                .cronExp("exp2") // this field from base class is the only difference
                .build();
        assertNotEquals(o1, o2);
    }
}