package org.artifactory.io.checksum.policy;


import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ChecksumPolicyExceptionTest {

    private static final String policyExceptionMessage = "Policy is null. Checksum is unavailable.";

    @Test
    public void testChecksumPolicyExceptionThrownWhenPolicyNull() {
        Exception exception= assertThrows(ChecksumPolicyException.class, () -> {
            throw new ChecksumPolicyException(policyExceptionMessage);
        });
        assertEquals(policyExceptionMessage, exception.getMessage());
    }
}
