package org.artifactory.policy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @vovam
 */
class PolicyTypeTest {

    @ParameterizedTest
    @EnumSource(PolicyType.class)
    void findByCode(PolicyType policyType) {
        assertEquals(policyType, PolicyType.findByCode(policyType.getCode()));
    }

    @Test
    void findByInvalidCode() {
        assertThrows(NoSuchElementException.class, () -> PolicyType.findByCode("cleanup-1"));
    }
}