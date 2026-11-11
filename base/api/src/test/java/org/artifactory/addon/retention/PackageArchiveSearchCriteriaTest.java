package org.artifactory.addon.retention;

import org.artifactory.descriptor.repo.RepoType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import jakarta.ws.rs.BadRequestException;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Oleksandr Legenkiy
 */
class PackageArchiveSearchCriteriaTest {

    private static final String REPO_NAME = "someRepo";

    @Test
    void testValidationExceptions() {
        PackageArchiveSearchCriteria criteria = PackageArchiveSearchCriteria.builder().build();
        assertBadRequest(criteria, "Package type cannot be blank");

        criteria.setRepositories(List.of(REPO_NAME, " "));
        assertBadRequest(criteria, "Repositories cannot contain blank values");

        criteria.setRepositories(List.of(REPO_NAME));
        assertBadRequest(criteria, "Package type cannot be blank");

        criteria.setPackageType("invalidPackageType");
        assertBadRequest(criteria, "Invalid packageType value: 'invalidPackageType'");

        criteria.setPackageType(RepoType.Docker.getType());
        criteria.setArchiveTimeFrom(-1L);
        assertBadRequest(criteria, "Archive time cannot be negative");
        criteria.setArchiveTimeTo(-1L);
        assertBadRequest(criteria, "Archive time cannot be negative");

        criteria.setArchiveTimeFrom(10L);
        criteria.setArchiveTimeTo(9L);
        assertBadRequest(criteria, "Param 'archiveTimeTo' should be greater or equal to 'archiveTimeFrom'");
    }

    @Test
    void testSuccessfulValidationFullParams() {
        PackageArchiveSearchCriteria criteria = PackageArchiveSearchCriteria.builder()
                .repositories(List.of(REPO_NAME))
                .packageType(RepoType.Docker.getType())
                .name("name*")
                .version("1.1.1")
                .archiveTimeFrom(10L)
                .archiveTimeTo(11L)
                .build();
        criteria.validate();
    }

    @Test
    void testSuccessfulValidationMinRequiredParams() {
        PackageArchiveSearchCriteria criteria = PackageArchiveSearchCriteria.builder()
                .repositories(List.of(REPO_NAME))
                .packageType("Docker") // making sure not only lowercase values are accepted
                .name("name*")
                .build();
        criteria.validate();
    }

    @ParameterizedTest
    @MethodSource("provideValidationHasWildcard")
    void testValidationNameHasWildcard(String name, boolean expected) {
        PackageArchiveSearchCriteria criteria = PackageArchiveSearchCriteria.builder()
                .name(name)
                .build();
        assertEquals(expected, criteria.nameContainsWildcard());
    }

    @ParameterizedTest
    @MethodSource("provideValidationHasWildcard")
    void testValidationVersionHasWildcard(String version, boolean expected) {
        PackageArchiveSearchCriteria criteria = PackageArchiveSearchCriteria.builder()
                .version(version)
                .build();
        assertEquals(expected, criteria.versionContainsWildcard());
    }

    @ParameterizedTest
    @MethodSource("provideValidationHasOnlyWildcard")
    void testValidationNameHasOnlyWildcard(String name, boolean expected) {
        PackageArchiveSearchCriteria criteria = PackageArchiveSearchCriteria.builder()
                .name(name)
                .build();
        assertEquals(expected, criteria.nameContainsWildcardOnly());
    }

    @ParameterizedTest
    @MethodSource("provideValidationHasOnlyWildcard")
    void testValidationVersionHasOnlyWildcard(String version, boolean expected) {
        PackageArchiveSearchCriteria criteria = PackageArchiveSearchCriteria.builder()
                .version(version)
                .build();
        assertEquals(expected, criteria.versionContainsWildcardOnly());
    }

    private void assertBadRequest(PackageArchiveSearchCriteria criteria, String message) {
        BadRequestException thrown = assertThrows(BadRequestException.class, criteria::validate);
        assertEquals(message, thrown.getMessage());
    }

    private static Stream<Arguments> provideValidationHasWildcard() {
        return Stream.of(
                Arguments.of(null, false),
                Arguments.of("", false),
                Arguments.of("  ", false),
                Arguments.of("not blank", false),
                Arguments.of("*name", true),
                Arguments.of("name*", true),
                Arguments.of("*name*", true),
                Arguments.of("**", true)
        );
    }

    private static Stream<Arguments> provideValidationHasOnlyWildcard() {
        return Stream.of(
                Arguments.of(null, false),
                Arguments.of("", false),
                Arguments.of("  ", false),
                Arguments.of("not blank", false),
                Arguments.of("*name", false),
                Arguments.of("name*", false),
                Arguments.of("*name*", false),
                Arguments.of("**", false),
                Arguments.of("*", true)
        );
    }
}