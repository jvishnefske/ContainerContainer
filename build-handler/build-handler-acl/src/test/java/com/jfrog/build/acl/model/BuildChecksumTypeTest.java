package com.jfrog.build.acl.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildChecksumTypeTest {

    @ParameterizedTest
    @EnumSource(BuildChecksumType.class)
    void getAlg_getExt_getLength_shouldReturnExpectedValues_forEachEnum(BuildChecksumType type) {
        switch (type) {
            case SHA1:
                assertEquals("SHA-1", type.getAlg());
                assertEquals(".sha1", type.getExt());
                assertEquals(40, type.getLength());
                break;
            case MD5:
                assertEquals("MD5", type.getAlg());
                assertEquals(".md5", type.getExt());
                assertEquals(32, type.getLength());
                break;
            case SHA256:
                assertEquals("SHA-256", type.getAlg());
                assertEquals(".sha256", type.getExt());
                assertEquals(64, type.getLength());
                break;
            default:
                break;
        }
    }

    @Test
    void isValid_shouldReturnFalse_whenChecksumNull() {
        assertFalse(BuildChecksumType.SHA1.isValid(null));
        assertFalse(BuildChecksumType.MD5.isValid(null));
        assertFalse(BuildChecksumType.SHA256.isValid(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void isValid_shouldReturnFalse_whenChecksumEmptyOrBlank(String checksum) {
        assertFalse(BuildChecksumType.SHA1.isValid(checksum));
    }

    @Test
    void isValid_shouldReturnFalse_whenLengthMismatch() {
        assertFalse(BuildChecksumType.SHA1.isValid("a".repeat(39)));
        assertFalse(BuildChecksumType.SHA1.isValid("a".repeat(41)));
        assertFalse(BuildChecksumType.MD5.isValid("a".repeat(31)));
        assertFalse(BuildChecksumType.SHA256.isValid("a".repeat(63)));
    }

    @Test
    void isValid_shouldReturnFalse_whenContainsNonHexCharacter() {
        assertFalse(BuildChecksumType.SHA1.isValid("g" + "a".repeat(39)));
        assertFalse(BuildChecksumType.MD5.isValid("a".repeat(31) + "z"));
        assertFalse(BuildChecksumType.SHA256.isValid("a".repeat(63) + " "));
    }

    @Test
    void isValid_shouldReturnTrue_whenSha1Is40HexCharacters() {
        assertTrue(BuildChecksumType.SHA1.isValid("a".repeat(40)));
        assertTrue(BuildChecksumType.SHA1.isValid("0123456789abcdef0123456789abcdef01234567"));
        assertTrue(BuildChecksumType.SHA1.isValid("ABCDEF0123456789ABCDEF0123456789ABCDEF01"));
    }

    @Test
    void isValid_shouldReturnTrue_whenMd5Is32HexCharacters() {
        assertTrue(BuildChecksumType.MD5.isValid("a".repeat(32)));
        assertTrue(BuildChecksumType.MD5.isValid("0123456789abcdef0123456789abcdef"));
    }

    @Test
    void isValid_shouldReturnTrue_whenSha256Is64HexCharacters() {
        assertTrue(BuildChecksumType.SHA256.isValid("a".repeat(64)));
        assertFalse(BuildChecksumType.SHA256.isValid(
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123"));
    }

    @Test
    void isValid_shouldReturnFalse_whenCorrectLengthButInvalidHexCharacter() {
        assertFalse(BuildChecksumType.SHA1.isValid("x" + "a".repeat(39)));
        assertFalse(BuildChecksumType.MD5.isValid("x" + "a".repeat(31)));
        assertFalse(BuildChecksumType.SHA256.isValid("x" + "a".repeat(63)));
    }
}
