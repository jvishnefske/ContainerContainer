package org.artifactory.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BasicHttpUtilsTest {

    @Test
    void testIsEncodedWithEncodedString() {
        String encoded = "Hello%20World";
        assertTrue(BasicHttpUtils.isEncoded(encoded));
    }

    @Test
    void testIsEncodedWithDecodedString() {
        String decoded = "Hello World";
        assertFalse(BasicHttpUtils.isEncoded(decoded));
    }

    @Test
    void testIsEncodedWithNullString() {
        String query = null;
        assertFalse(BasicHttpUtils.isEncoded(query));
    }

    @Test
    void testIsEncodedWithInvalidEncoding() {
        String invalid = "Hello%2GWorld";
        assertFalse(BasicHttpUtils.isEncoded(invalid));
    }

    @Test
    void testIsEncodedWithMixedEncoding() {
        String mixedEncoding = "Hello%2B%20World";
        assertTrue(BasicHttpUtils.isEncoded(mixedEncoding));
    }

    @Test
    void testIsEncodedWithUTF8Encoding() {
        String utf8 = "%C3%A9t%C3%A9";
        assertTrue(BasicHttpUtils.isEncoded(utf8));
    }

}