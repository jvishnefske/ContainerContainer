package org.artifactory.policy;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertTrue;

/**
 * @author punyap
 */

public class PackagePolicySearchCriteriaTest {
    @Test
    void testGetTimeBasedConditions() {
        PackagePolicySearchCriteria criteria = PackagePolicySearchCriteria.builder()
                .createdBeforeInMonths(1)
                .lastDownloadedBeforeInMonths(2)
                .build();
        assertEquals(List.of(1, 2), criteria.getTimeBasedConditions());

        criteria = criteria.toBuilder().lastDownloadedBeforeInMonths(null).build();
        assertEquals(List.of(1), criteria.getTimeBasedConditions());
    }

    @Test
    void testDaysTimeBasedCondition() {
        PackagePolicySearchCriteria criteria = PackagePolicySearchCriteria.builder()
                .createdBeforeInDays(4)
                .lastDownloadedBeforeInDays(5)
                .build();
        assertEquals(List.of(4, 5), criteria.getTimeBasedConditions());

        criteria = criteria.toBuilder().lastDownloadedBeforeInDays(null).build();
        assertEquals(List.of(4), criteria.getTimeBasedConditions());
    }

    @Test
    void testMonthsAndDays() {
        PackagePolicySearchCriteria criteria = PackagePolicySearchCriteria.builder()
                .createdBeforeInDays(3)
                .lastDownloadedBeforeInDays(4)
                .build();

        PackagePolicySearchCriteria criteria2 = PackagePolicySearchCriteria.builder()
                .createdBeforeInDays(3)
                .lastDownloadedBeforeInDays(4)
                .createdBeforeInMonths(2)
                .lastDownloadedBeforeInMonths(3)
                .build();

        assertFalse(criteria.bothMonthsAndDaysExists());
        assertTrue(criteria2.bothMonthsAndDaysExists());
    }

    @Test
    void testGetVersionsBasedConditions() {
        PackagePolicySearchCriteria criteria = PackagePolicySearchCriteria.builder()
                .keepLastNVersions(1)
                .build();
        assertEquals(List.of(1), criteria.getVersionsBasedConditions());

        criteria = criteria.toBuilder().keepLastNVersions(null).build();
        assertEquals(List.of(), criteria.getVersionsBasedConditions());
    }

    @Test
    void hasInclusionBasedPropertyConditions_WhenNull() {
        PackagePolicySearchCriteria criteria = PackagePolicySearchCriteria.builder()
                .includedProperties(null)
                .build();
        assertFalse(criteria.hasInclusionBasedPropertyConditions());
    }

    @Test
    void hasInclusionBasedPropertyConditions_WhenEmpty() {
        PackagePolicySearchCriteria criteria = PackagePolicySearchCriteria.builder()
                .includedProperties(Collections.emptyMap())
                .build();
        assertFalse(criteria.hasInclusionBasedPropertyConditions());
    }

    @Test
    void hasInclusionBasedPropertyConditions_WithEmptyValues() {
        PackagePolicySearchCriteria criteria = PackagePolicySearchCriteria.builder()
                .includedProperties(Map.of(
                        "env", Collections.emptyList(),
                        "arch", List.of()
                ))
                .build();
        assertFalse(criteria.hasInclusionBasedPropertyConditions());
    }

    @Test
    void hasInclusionBasedPropertyConditions_WithValidValues() {
        PackagePolicySearchCriteria criteria = PackagePolicySearchCriteria.builder()
                .includedProperties(Map.of(
                        "env", List.of("prod"),
                        "team", List.of("backend")
                ))
                .build();
        assertTrue(criteria.hasInclusionBasedPropertyConditions());
    }

    @Test
    void hasExclusionBasedPropertyConditions_WhenNull() {
        PackagePolicySearchCriteria criteria = PackagePolicySearchCriteria.builder()
                .excludedProperties(null)
                .build();
        assertFalse(criteria.hasExclusionBasedPropertyConditions());
    }

    @Test
    void hasExclusionBasedPropertyConditions_WhenEmpty() {
        PackagePolicySearchCriteria criteria = PackagePolicySearchCriteria.builder()
                .excludedProperties(Collections.emptyMap())
                .build();
        assertFalse(criteria.hasExclusionBasedPropertyConditions());
    }

    @Test
    void hasExclusionBasedPropertyConditions_WithMixedValues() {
        Map<String, List<String>> excludedProps = new HashMap<>();
        excludedProps.put("tested", List.of("true"));
        excludedProps.put("arch", Collections.emptyList());
        excludedProps.put("os", null);

        PackagePolicySearchCriteria criteria = PackagePolicySearchCriteria.builder()
                .excludedProperties(excludedProps)
                .build();

        assertTrue(criteria.hasExclusionBasedPropertyConditions());
    }

    @Test
    void hasExclusionBasedPropertyConditions_WithNullValues() {
        Map<String, List<String>> excludedProps = new HashMap<>();
        excludedProps.put("env", null);
        excludedProps.put("stage", null);

        PackagePolicySearchCriteria criteria = PackagePolicySearchCriteria.builder()
                .excludedProperties(excludedProps)
                .build();

        assertFalse(criteria.hasExclusionBasedPropertyConditions());
    }
}
