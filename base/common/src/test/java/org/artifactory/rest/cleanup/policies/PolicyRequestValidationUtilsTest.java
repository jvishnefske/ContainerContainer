package org.artifactory.rest.cleanup.policies;

import org.artifactory.exception.http.BadRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Oleksandr Legenkiy
 */
class PolicyRequestValidationUtilsTest {

    @Test
    void testValidatePolicyKeys() {
        PolicyRequestValidationUtils.validatePolicyKeys("key", "key");
    }

    @Test
    void testValidatePolicyInconsistentPolicyKeys() {
        BadRequestException badRequestException = assertThrows(BadRequestException.class,
                () -> PolicyRequestValidationUtils.validatePolicyKeys("key2", "key1"));
        assertEquals("Incorrect policy key value", badRequestException.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    void testValidatePolicyBlankPolicyKey(final String key) {
        BadRequestException badRequestException = assertThrows(BadRequestException.class,
                () -> PolicyRequestValidationUtils.validatePolicyKeys("key", key));
        assertEquals("Incorrect policy key value", badRequestException.getMessage());
        badRequestException = assertThrows(BadRequestException.class,
                () -> PolicyRequestValidationUtils.validatePolicyKeys(" ", "key"));
        assertEquals("Incorrect policy key value", badRequestException.getMessage());
    }

    @Test
    void validateItemTypes() {
        PolicyRequestValidationUtils.validateItemTypes(Set.of("artifact", "package"));
    }

    @ParameterizedTest
    @MethodSource("validateItemTypesParams")
    void validateItemTypesError(String error, Set<String> itemTypes) {
        BadRequestException badRequestException = assertThrows(BadRequestException.class,
                () -> PolicyRequestValidationUtils.validateItemTypes(itemTypes));
        assertEquals(error, badRequestException.getMessage());
    }

    private static Stream<Arguments> validateItemTypesParams() {
        return Stream.of(
                Arguments.of("itemType is mandatory", null),
                Arguments.of("Wrong item type, must be one of [package, artifact, build, releaseBundle]",
                        Set.of("test"))
        );
    }
}