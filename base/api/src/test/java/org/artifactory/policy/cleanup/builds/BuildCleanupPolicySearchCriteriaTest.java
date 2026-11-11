package org.artifactory.policy.cleanup.builds;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


public class BuildCleanupPolicySearchCriteriaTest {

    @Test
    void assertBothMonthsAndDaysExist() {
        BuildCleanupPolicySearchCriteria criteria = BuildCleanupPolicySearchCriteria.builder()
                .createdBeforeInDays(3)
                .createdBeforeInMonths(4)
                .build();

        Assertions.assertTrue(criteria.bothMonthsAndDaysExists());
    }

    @Test
    void assetBothMonthsAndDaysNotExist() {
        BuildCleanupPolicySearchCriteria criteria = BuildCleanupPolicySearchCriteria.builder()
                .createdBeforeInDays(3)
                .build();

        BuildCleanupPolicySearchCriteria criteria2 = BuildCleanupPolicySearchCriteria.builder()
                .createdBeforeInMonths(3)
                .build();

        Assertions.assertFalse(criteria.bothMonthsAndDaysExists());
        Assertions.assertFalse(criteria2.bothMonthsAndDaysExists());
    }
}
