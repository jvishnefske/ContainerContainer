package org.artifactory.ui.rest.service.artifacts.search.syntax;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.artifactory.ui.rest.service.artifacts.search.syntax.SyntaxSearchQueryFilesTest.bind;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Igor Usenko
 */
class SyntaxSearchQueryUtilsCreatePathsConditionTest {

    @ParameterizedTest
    @MethodSource("leadingSlashTestCases")
    void shouldHandleLeadingSlashes(String input, String expected) {
        String result = createPathCondition(input);
        assertEquals(expected, result,
                String.format("Input '%s' should produce the expected SQL condition", input));
    }

    public static String getString(SqlFragment fragment) {
        String bound = bind(fragment.template(), fragment.params());
        return bound.substring(0, bound.length() - 1);
    }

    static Stream<Arguments> leadingSlashTestCases() {
        return Stream.of(
                Arguments.of("/path", "and (node_path || '/' || node_name ilike '%path%') "),
                Arguments.of("//path", "and (node_path || '/' || node_name ilike '%path%') "),
                Arguments.of("///path", "and (node_path || '/' || node_name ilike '%path%') "),
                Arguments.of("/com/example",
                        "and node_ts_vector @@ to_tsquery('simple', 'com & example:*') and node_path || '/' || " +
                                "node_name ilike '%com/example%' "),
                Arguments.of("//com/example",
                        "and node_ts_vector @@ to_tsquery('simple', 'com & example:*') and node_path || '/' || " +
                                "node_name ilike '%com/example%' ")
        );
    }

    @ParameterizedTest
    @MethodSource("trailingSlashTestCases")
    void shouldHandleTrailingSlashes(String input, String expected) {
        String result = createPathCondition(input);
        assertEquals(expected, result,
                String.format("Input '%s' should produce the expected SQL condition", input));
    }

    static Stream<Arguments> trailingSlashTestCases() {
        return Stream.of(
                Arguments.of("path/", "and (node_path || '/' || node_name ilike '%path/%') "),
                Arguments.of("path//", "and (node_path || '/' || node_name ilike '%path/%') "),
                Arguments.of("path///", "and (node_path || '/' || node_name ilike '%path/%') "),
                Arguments.of("com/example/",
                        "and node_ts_vector @@ to_tsquery('simple', 'com & example:*') and node_path || '/' || " +
                                "node_name ilike '%com/example/%' "),
                Arguments.of("com/example//",
                        "and node_ts_vector @@ to_tsquery('simple', 'com & example:*') and node_path || '/' || " +
                                "node_name ilike '%com/example/%' ")
        );
    }

    @ParameterizedTest
    @MethodSource("bothEndsSlashTestCases")
    void shouldHandleBothEndsSlashes(String input, String expected) {
        String result = createPathCondition(input);
        assertEquals(expected, result,
                String.format("Input '%s' should produce the expected SQL condition", input));
    }

    static Stream<Arguments> bothEndsSlashTestCases() {
        return Stream.of(
                Arguments.of("/path/", "and (node_path || '/' || node_name ilike '%path/%') "),
                Arguments.of("//path//", "and (node_path || '/' || node_name ilike '%path/%') "),
                Arguments.of("///path///", "and (node_path || '/' || node_name ilike '%path/%') "),
                Arguments.of("/com/example/",
                        "and node_ts_vector @@ to_tsquery('simple', 'com & example:*') and node_path || '/' || " +
                                "node_name ilike '%com/example/%' "),
                Arguments.of("//com/example//",
                        "and node_ts_vector @@ to_tsquery('simple', 'com & example:*') and node_path || '/' || " +
                                "node_name ilike '%com/example/%' ")
        );
    }


    @ParameterizedTest
    @MethodSource("simplePathTestCases")
    void shouldCreateNodeNameConditionForSimplePaths(String input, String expected) {
        String result = createPathCondition(input);
        assertEquals(expected, result,
                String.format("Simple path '%s' should create node_name condition", input));
    }

    static Stream<Arguments> simplePathTestCases() {
        return Stream.of(
                Arguments.of("artifact", "and (node_path || '/' || node_name ilike '%artifact%') "),
                Arguments.of("myfile", "and (node_path || '/' || node_name ilike '%myfile%') "),
                Arguments.of("package", "and (node_path || '/' || node_name ilike '%package%') "),
                Arguments.of("lib", "and (node_path || '/' || node_name ilike '%lib%') "),
                Arguments.of("utils", "and (node_path || '/' || node_name ilike '%utils%') ")
        );
    }

    @ParameterizedTest
    @MethodSource("complexPathTestCases")
    void shouldCreateFtsConditionForComplexPaths(String input, String expected) {
        String result = createPathCondition(input);
        assertEquals(expected, result,
                String.format("Complex path '%s' should create FTS condition", input));
    }

    private static String createPathCondition(String input) {
        return getString(SyntaxSearchQueryUtils.createPathsCondition(input, ""));
    }

    static Stream<Arguments> complexPathTestCases() {
        return Stream.of(
                Arguments.of("com/example",
                        "and node_ts_vector @@ to_tsquery('simple', 'com & example:*') and node_path || '/' || " +
                                "node_name ilike '%com/example%' "),
                Arguments.of("org/artifactory/ui",
                        "and node_ts_vector @@ to_tsquery('simple', 'org & artifactory & ui:*') and node_path || '/' " +
                                "|| node_name ilike '%org/artifactory/ui%' "),
                Arguments.of("java/lang/String",
                        "and node_ts_vector @@ to_tsquery('simple', 'java & lang & String:*') and node_path || '/' ||" +
                                " " +
                                "node_name ilike '%java/lang/String%' "),
                Arguments.of("a/b",
                        "and node_ts_vector @@ to_tsquery('simple', 'a & b:*') and node_path || '/' || " +
                                "node_name ilike '%a/b%' "),
                Arguments.of("com/example/myapp/utils",
                        "and node_ts_vector @@ to_tsquery('simple', 'com & example & myapp & utils:*') and node_path " +
                                "||" +
                                " '/' || node_name ilike '%com/example/myapp/utils%' ")
        );
    }

    @ParameterizedTest
    @MethodSource("pathsWithSeparatorsTestCases")
    void shouldHandlePathsWithSeparators(String input, String expected) {
        String result = createPathCondition(input);
        assertEquals(expected, result,
                String.format("Path with separators '%s' should produce the expected condition", input));
    }

    static Stream<Arguments> pathsWithSeparatorsTestCases() {
        return Stream.of(
                Arguments.of("my-artifact",
                        "and (node_path || '/' || node_name ilike '%my-artifact%') "),
                Arguments.of("artifact.version",
                        "and (node_path || '/' || node_name ilike '%artifact.version%') "),
                Arguments.of("com/example-api/utils",
                        "and node_ts_vector @@ to_tsquery('simple', 'com & example & api & utils:*') and node_path ||" +
                                " " +
                                "'/' || node_name ilike '%com/example-api/utils%' "),
                Arguments.of("org.example/myapp",
                        "and node_ts_vector @@ to_tsquery('simple', 'org.example & myapp:*') and node_path || '/' || " +
                                "node_name ilike '%org.example/myapp%' ")
        );
    }

    @ParameterizedTest
    @MethodSource("nestedPathTestCases")
    void shouldHandleNestedPaths(String input, String expected) {
        String result = createPathCondition(input);
        assertEquals(expected, result,
                String.format("Nested path '%s' should produce the expected condition", input));
    }

    static Stream<Arguments> nestedPathTestCases() {
        return Stream.of(
                Arguments.of("a/b/c",
                        "and node_ts_vector @@ to_tsquery('simple', 'a & b & c:*') and node_path || '/' || " +
                                "node_name ilike '%a/b/c%' "),
                Arguments.of("com/example/utils/helper",
                        "and node_ts_vector @@ to_tsquery('simple', 'com & example & utils & helper:*') and node_path" +
                                " " +
                                "|| '/' || node_name ilike '%com/example/utils/helper%' "),
                Arguments.of("org/springframework/web/mvc",
                        "and node_ts_vector @@ to_tsquery('simple', 'org & springframework & web & mvc:*') and " +
                                "node_path || '/' || node_name ilike '%org/springframework/web/mvc%' ")
        );
    }

    @ParameterizedTest
    @MethodSource("specialCharacterTestCases")
    void shouldHandleSpecialCharacters(String input, String expected) {
        String result = createPathCondition(input);
        assertEquals(expected, result,
                String.format("Path with special characters '%s' should produce the expected condition", input));
    }

    static Stream<Arguments> specialCharacterTestCases() {
        return Stream.of(
                Arguments.of("artifact_v2",
                        "and (node_path || '/' || node_name ilike '%artifact^_v2%' ESCAPE '^') "),
                Arguments.of("my.file.tar.gz",
                        "and (node_path || '/' || node_name ilike '%my.file.tar.gz%') "),
                Arguments.of("com/example_v1/utils",
                        "and node_ts_vector @@ to_tsquery('simple', 'com & example & v1 & utils:*') and node_path || " +
                                "'/' || node_name ilike '%com/example^_v1/utils%' ESCAPE '^' "),
                Arguments.of("project_name/version-1.0",
                        "and node_ts_vector @@ to_tsquery('simple', 'project & name & version & 1.0:*') and node_path" +
                                " " +
                                "|| '/' || node_name ilike '%project^_name/version-1.0%' ESCAPE '^' ")
        );
    }

    @ParameterizedTest
    @MethodSource("prefixedPathTestCases")
    void shouldApplyTablePrefixWhenProvided(String input, String expected) {
        String result = getString(SyntaxSearchQueryUtils.createPathsCondition(input, "n."));
        assertEquals(expected, result,
                String.format("Path '%s' with prefix 'n.' should produce prefixed condition", input));
    }

    static Stream<Arguments> prefixedPathTestCases() {
        return Stream.of(
                Arguments.of("artifact",
                        "and (n.node_path || '/' || n.node_name ilike '%artifact%') "),
                Arguments.of("com/example",
                        "and n.node_ts_vector @@ to_tsquery('simple', 'com & example:*') and n.node_path || '/' || n" +
                                ".node_name ilike '%com/example%' ")
        );
    }

}

