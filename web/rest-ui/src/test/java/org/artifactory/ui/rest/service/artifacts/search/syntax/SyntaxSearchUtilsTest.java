package org.artifactory.ui.rest.service.artifacts.search.syntax;

import org.artifactory.ui.rest.model.artifacts.search.syntax.Filters;
import org.artifactory.ui.rest.model.artifacts.search.syntax.Query;
import org.artifactory.ui.rest.model.artifacts.search.syntax.Type;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Set;
import java.util.stream.Stream;

import static org.artifactory.ui.rest.model.artifacts.search.syntax.Type.FILE;
import static org.artifactory.ui.rest.model.artifacts.search.syntax.Type.FOLDER;
import static org.artifactory.ui.rest.model.artifacts.search.syntax.Type.REPO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SyntaxSearchUtilsTest {

    @ParameterizedTest
    @MethodSource("searchTypesTestCases")
    void getSearchTypes(Query query, Set<Type> expectedTypes, String testDescription) {
        Set<Type> actualTypes = SyntaxSearchUtils.getSearchTypes(query, null);
        assertEquals(expectedTypes, actualTypes, testDescription);
    }

    static Stream<Arguments> searchTypesTestCases() {
        return Stream.of(
                Arguments.of(
                        new Query(null, null, null, "abc123"),
                        Set.of(FILE),
                        "Only sha provided should return FILE type"
                ),
                Arguments.of(
                        new Query(null, null, "/some/path", null),
                        Set.of(FOLDER),
                        "Only path provided should return FOLDER type"
                ),
                Arguments.of(
                        new Query(null, "my-repo", null, null),
                        Set.of(REPO),
                        "Only repo provided should return REPO type"
                ),
                Arguments.of(
                        new Query(null, "my-repo", "/some/path", null),
                        Set.of(FOLDER),
                        "Repo and path provided, path takes precedence"
                ),
                Arguments.of(
                        new Query(null, "my-repo", null, "abc123"),
                        Set.of(FILE),
                        "Repo and sha provided, sha takes precedence"
                ),
                Arguments.of(
                        new Query(null, null, "/some/path", "abc123"),
                        Set.of(FILE),
                        "Path and sha provided, sha takes precedence"
                ),
                Arguments.of(
                        new Query(null, "my-repo", "/some/path", "abc123"),
                        Set.of(FILE),
                        "All fields provided, sha takes precedence"
                ),

                Arguments.of(
                        new Query("search text", null, null, null),
                        Set.of(REPO, FOLDER, FILE),
                        "Only text provided should return all types"
                ),
                Arguments.of(
                        new Query("search text", null, null, "abc123"),
                        Set.of(FILE),
                        "Text and sha provided should return FILE type"
                ),
                Arguments.of(
                        new Query("search text", null, "/some/path", null),
                        Set.of(FOLDER, FILE),
                        "Text and path provided should return FOLDER and FILE types"
                ),
                Arguments.of(
                        new Query("search text", "my-repo", null, null),
                        Set.of(FOLDER, FILE),
                        "Text and repo provided should return FOLDER and FILE types"
                ),
                Arguments.of(
                        new Query("search text", "my-repo", "/some/path", null),
                        Set.of(FOLDER, FILE),
                        "Text, repo and path provided should return FOLDER and FILE types"
                ),
                Arguments.of(
                        new Query("search text", "my-repo", null, "abc123"),
                        Set.of(FILE),
                        "Text, repo and sha provided should return FILE type"
                ),
                Arguments.of(
                        new Query("search text", null, "/some/path", "abc123"),
                        Set.of(FILE),
                        "Text, path and sha provided should return FILE type"
                ),
                Arguments.of(
                        new Query("search text", "my-repo", "/some/path", "abc123"),
                        Set.of(FILE),
                        "All fields provided should return FILE type"
                ),

                Arguments.of(
                        new Query("", null, null, null),
                        Set.of(REPO, FOLDER, FILE),
                        "Empty text should be treated as non-null and return all types"
                ),
                Arguments.of(
                        new Query("", null, null, ""),
                        Set.of(FILE),
                        "Empty text and empty sha should return FILE type"
                ),
                Arguments.of(
                        new Query("", null, "", null),
                        Set.of(FOLDER, FILE),
                        "Empty text and empty path should return FOLDER and FILE types"
                ),
                Arguments.of(
                        new Query("", "", null, null),
                        Set.of(FOLDER, FILE),
                        "Empty text and empty repo should return FOLDER and FILE types"
                ),

                Arguments.of(
                        new Query("   ", null, null, null),
                        Set.of(REPO, FOLDER, FILE),
                        "Whitespace text should be treated as non-null and return all types"
                ),
                Arguments.of(
                        new Query("   ", null, null, "   "),
                        Set.of(FILE),
                        "Whitespace text and sha should return FILE type"
                )
        );
    }

    @ParameterizedTest
    @CsvSource({
            "my-repo, com/example, artifact.jar, my-repo/com/example/artifact.jar",
            "libs-release, org/jfrog, test-1.0.0.jar, libs-release/org/jfrog/test-1.0.0.jar",

            "my-repo, ., file.txt, my-repo/file.txt",
            "repo, ., artifact, repo/artifact",

            "my-repo, '', file.jar, my-repo/file.jar",
            "libs-release, '', artifact-1.0.0.jar, libs-release/artifact-1.0.0.jar",

            "my-repo, com/example, '', my-repo/com/example/",
            "repo, path/to, '', repo/path/to/",

            "my-repo, '', '', my-repo",
            "libs-release, ., '', libs-release",
            "repo, '', '', repo",

            "my-repo, com, file.jar, my-repo/com/file.jar",
    })
    void getFullPath(String repo, String path, String name, String expected) {
        String actual = SyntaxSearchUtils.getFullPath(repo, path, name);
        assertEquals(expected, actual);
    }

    @ParameterizedTest
    @MethodSource("getSearchTypesWithFiltersTestCases")
    void getSearchTypesWithFilters_shouldReturnExpectedTypes_whenFiltersProvided(
            Query query, Filters filters, Set<Type> expectedTypes, String testDescription) {
        Set<Type> actualTypes = SyntaxSearchUtils.getSearchTypes(query, filters);
        assertEquals(expectedTypes, actualTypes, testDescription);
    }

    private static Stream<Arguments> getSearchTypesWithFiltersTestCases() {
        return Stream.of(
                Arguments.of(
                        new Query(null, "my-repo", null, null),
                        new Filters(Set.of(REPO, FOLDER, FILE), null, null, null, null),
                        Set.of(FOLDER, FILE),
                        "Filters with REPO+PATH+FILE and repo specified should remove REPO"
                ),
                Arguments.of(
                        new Query(null, "my-repo", null, null),
                        new Filters(Set.of(REPO, FOLDER), null, null, null, null),
                        Set.of(FOLDER),
                        "Filters with REPO+PATH and repo specified should remove REPO"
                ),
                Arguments.of(
                        new Query(null, "my-repo", null, null),
                        new Filters(Set.of(REPO, FILE), null, null, null, null),
                        Set.of(FILE),
                        "Filters with REPO+FILE and repo specified should remove REPO"
                ),
                Arguments.of(
                        new Query(null, "my-repo", null, null),
                        new Filters(Set.of(REPO), null, null, null, null),
                        Set.of(),
                        "Filters with only REPO and repo specified should return empty set"
                ),
                Arguments.of(
                        new Query(null, "my-repo", null, null),
                        new Filters(Set.of(FOLDER, FILE), null, null, null, null),
                        Set.of(FOLDER, FILE),
                        "Filters with PATH+FILE and repo specified should remain unchanged"
                ),
                Arguments.of(
                        new Query(null, "my-repo", null, null),
                        new Filters(Set.of(FOLDER), null, null, null, null),
                        Set.of(FOLDER),
                        "Filters with only PATH and repo specified should remain unchanged"
                ),
                Arguments.of(
                        new Query(null, "my-repo", null, null),
                        new Filters(Set.of(FILE), null, null, null, null),
                        Set.of(FILE),
                        "Filters with only FILE and repo specified should remain unchanged"
                ),
                Arguments.of(
                        new Query(null, null, null, null),
                        new Filters(Set.of(REPO, FOLDER, FILE), null, null, null, null),
                        Set.of(REPO, FOLDER, FILE),
                        "Filters with REPO+PATH+FILE and no repo specified should keep REPO"
                ),
                Arguments.of(
                        new Query(null, null, "/some/path", null),
                        new Filters(Set.of(REPO, FOLDER), null, null, null, null),
                        Set.of(REPO, FOLDER),
                        "Filters with REPO+PATH and path specified should keep REPO"
                ),
                Arguments.of(
                        new Query("search text", null, null, null),
                        new Filters(Set.of(REPO), null, null, null, null),
                        Set.of(REPO),
                        "Filters with only REPO and text specified should keep REPO"
                ),
                Arguments.of(
                        new Query(null, "", null, null),
                        new Filters(Set.of(REPO, FOLDER), null, null, null, null),
                        Set.of(FOLDER),
                        "Filters with REPO+PATH and empty repo should remove REPO"
                ),
                Arguments.of(
                        new Query(null, "my-repo", null, null),
                        new Filters(null, null, null, null, null),
                        Set.of(REPO),
                        "Filters with null itemTypes should derive types from query"
                ),
                Arguments.of(
                        new Query(null, "my-repo", "/path", null),
                        new Filters(Set.of(), null, null, null, null),
                        Set.of(FOLDER),
                        "Filters with empty itemTypes should derive types from query"
                )
        );
    }

    @Test
    void getSearchTypesWithFilters_shouldThrowException_whenQueryProducesEmptyTypes() {
        Query invalidQuery = new Query(null, null, null, null);
        Filters emptyFilters = new Filters(null, null, null, null, null);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> SyntaxSearchUtils.getSearchTypes(invalidQuery, emptyFilters)
        );
        assertEquals("Unable to determine search types from query", exception.getMessage());
    }

    @Test
    void getSearchTypesWithFilters_shouldThrowException_whenQueryProducesEmptyTypesWithEmptyItemTypes() {
        Query invalidQuery = new Query(null, null, null, null);
        Filters emptyFilters = new Filters(Set.of(), null, null, null, null);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> SyntaxSearchUtils.getSearchTypes(invalidQuery, emptyFilters)
        );
        assertEquals("Unable to determine search types from query", exception.getMessage());
    }

}
