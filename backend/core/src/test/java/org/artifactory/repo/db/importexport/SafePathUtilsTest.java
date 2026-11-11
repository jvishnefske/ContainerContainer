package org.artifactory.repo.db.importexport;

import org.apache.commons.codec.CodecPolicy;
import org.apache.commons.codec.binary.Base32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Stream.of;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SafePathUtilsTest {

    @ParameterizedTest
    @MethodSource("namesStartedWitRt")
    void whenNameStartsWithRt(String original, String expected) {
        String actual = SafePathUtils.convertToSafeName(original, Collections.emptySet(), true);
        assertEquals(expected, actual);
        assertEquals(original, SafePathUtils.convertFromSafeName(actual));
    }

    @ParameterizedTest
    @MethodSource("names")
    void conversion(String original, String expected) {
        String actual = SafePathUtils.convertToSafeName(original, Collections.emptySet(), true);
        assertEquals(expected, actual);
        assertEquals(original, SafePathUtils.convertFromSafeName(actual));
    }

    @Test
    void whenDiffCaseSiblingsAreNotExists() {
        String name1 = "name1";
        Set<String> siblings = Set.of(name1, "name2");
        String actual = SafePathUtils.convertToSafeName(name1, siblings, false);
        assertEquals(name1, actual);
        actual = SafePathUtils.convertToSafeName(name1, siblings, true);
        assertEquals(name1, actual);
    }

    @Test
    void whenDiffCaseSiblingsAreExists() {
        String name1 = "name1";
        Set<String> siblings = Set.of(name1, "NaMe1");
        String actual = SafePathUtils.convertToSafeName(name1, siblings, false);
        assertEquals(c(name1), actual);
        actual = SafePathUtils.convertToSafeName(name1, siblings, true);
        assertEquals(c(name1), actual);
    }

    @Test
    void whenFailToFindSafeName() {
        String name1 = "name1";
        Set<String> siblings = new HashSet<>();
        siblings.add(name1);
        siblings.add(c(name1));
        siblings.add("NaMe1");
        IntStream.range(1, 4000).forEach(i -> siblings.add(c("name1") + "-" + i));
        assertThrows(
                IllegalStateException.class,
                () -> SafePathUtils.convertToSafeName(name1, siblings, false)
        );
    }

    private static Stream<Arguments> names() {
        return of(
                p("", ""),
                p("a", "a"),
                p("di:r1", c("di:r1")),
                p("dir1 ", c("dir1 ")),
                p("dir1.", c("dir1.")),
                p("com1", c("com1"))
        );
    }

    private static Stream<Arguments> namesStartedWitRt() {
        return of(
                p("rtname", c("rtname")),
                p("Rtname", c("Rtname")),
                p("rTname", c("rTname")),
                p("RTname", c("RTname"))
        );
    }

    private static Arguments p(String a, String b) {
        return Arguments.of(a, b);
    }

    private static String c(String s) {
        Base32 base32 = new Base32(0, null, false, (byte) 61, CodecPolicy.STRICT);
        return "RT" + base32.encodeAsString(s.getBytes());
    }

}