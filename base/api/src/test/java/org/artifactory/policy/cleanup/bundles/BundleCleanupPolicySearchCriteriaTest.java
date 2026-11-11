package org.artifactory.policy.cleanup.bundles;
import org.artifactory.policy.cleanup.bundle.BundleCleanupPolicySearchCriteria;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class BundleCleanupPolicySearchCriteriaTest {
    @Test
    void assertBothMonthsAndDaysExist() {
        BundleCleanupPolicySearchCriteria criteria = BundleCleanupPolicySearchCriteria.builder()
                .createdBeforeInDays(3)
                .createdBeforeInMonths(4)
                .build();

        Assertions.assertTrue(criteria.bothTimeBasedConditionsExist());
    }

    @Test
    void assetBothMonthsAndDaysNotExist() {
        BundleCleanupPolicySearchCriteria criteria = BundleCleanupPolicySearchCriteria.builder()
                .createdBeforeInDays(3)
                .build();

        BundleCleanupPolicySearchCriteria criteria2 = BundleCleanupPolicySearchCriteria.builder()
                .createdBeforeInMonths(3)
                .build();

        Assertions.assertFalse(criteria.bothTimeBasedConditionsExist());
        Assertions.assertFalse(criteria2.bothTimeBasedConditionsExist());
    }
}
