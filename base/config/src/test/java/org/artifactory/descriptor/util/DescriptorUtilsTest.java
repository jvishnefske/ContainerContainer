package org.artifactory.descriptor.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DescriptorUtilsTest {

    @Test
    void testAssertNoSpecialCharactersAndValidXmlNameWithValidKey() {
        DescriptorUtils.assertNoSpecialCharactersAndValidXmlName("validStr", "key");
    }

    @ParameterizedTest
    @MethodSource("invalidNamesProvider")
    void testAssertNoSpecialCharactersAndValidXmlNameWithInvalidKey(String invalidStr, String expectedMessage) {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            DescriptorUtils.assertNoSpecialCharactersAndValidXmlName(invalidStr, "key");
        });
        assertTrue(thrown.getMessage().contains(expectedMessage));
    }

    private static Stream<Arguments> invalidNamesProvider() {
        return Stream.of(
                Arguments.of(null, "Name cannot be blank"),
                Arguments.of("", "Name cannot be blank"),
                Arguments.of("  ", "Name cannot be blank"), //whitespace
                Arguments.of("1startingDigit", "XML names cannot begin with the character \"1\""),
                Arguments.of("invalid$symbol", "XML names cannot contain the character \"$\""),
                Arguments.of("co:lon", "'/,\\,:,|,?,<,>,*,\"' is not allowed")
        );
    }
}