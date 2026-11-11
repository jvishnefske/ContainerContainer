package com.jfrog.build.model.api;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.jfrog.build.acl.util.BuildConstants.BUILDINFO_REPO_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ContinueBuildDtoTest {

    @Test
    void constructor_shouldUseDefaultBuildRepo_whenProjectKeyMissing() {
        // Act
        ContinueBuildDto continueBuildDto = new ContinueBuildDto(Map.of(), projectKey -> "unused");

        // Assert
        assertEquals(BUILDINFO_REPO_KEY, continueBuildDto.getBuildRepo());
    }

    @Test
    void constructor_shouldUseEmptyBuildRepo_whenProjectKeyIsAll() {
        // Act
        ContinueBuildDto continueBuildDto = new ContinueBuildDto(
                Map.of("projectKey", new String[] {"all"}),
                projectKey -> "unused");

        // Assert
        assertEquals(StringUtils.EMPTY, continueBuildDto.getBuildRepo());
    }

    @Test
    void constructor_shouldResolveBuildRepoFromConverter_whenCustomProjectKeyProvided() {
        // Arrange
        ContinueBuildDto.ProjectKeyToBuildRepoConverter converter =
                mock(ContinueBuildDto.ProjectKeyToBuildRepoConverter.class);
        when(converter.toBuildRepo("phoenix")).thenReturn("phoenix-build-info");

        // Act
        ContinueBuildDto continueBuildDto = new ContinueBuildDto(
                Map.of("projectKey", new String[] {"phoenix"}),
                converter);

        // Assert
        assertEquals("phoenix-build-info", continueBuildDto.getBuildRepo());
    }

    @Test
    void constructor_shouldParseBuildSpecificParams() {
        // Act
        ContinueBuildDto continueBuildDto = new ContinueBuildDto(
                Map.of(
                        "orderBy", new String[] {"build_name"},
                        "searchStr", new String[] {"some-prefix"},
                        "limit", new String[] {"50"}),
                projectKey -> BUILDINFO_REPO_KEY);

        // Assert
        assertEquals("build_name", continueBuildDto.getOrderBy());
        assertEquals("some-prefix", continueBuildDto.getSearchStr());
        assertEquals(50L, continueBuildDto.getLimit());
    }

    @Test
    void copyConstructor_shouldCopyAllFields() {
        // Arrange
        ContinueBuildDto original = new ContinueBuildDto(
                Map.of(
                        "orderBy", new String[] {"build_date"},
                        "searchStr", new String[] {"query"},
                        "projectKey", new String[] {"phoenix"},
                        "limit", new String[] {"10"}),
                projectKey -> "phoenix-build-info");

        // Act
        ContinueBuildDto copy = new ContinueBuildDto(original);

        // Assert
        assertEquals(original.getOrderBy(), copy.getOrderBy());
        assertEquals(original.getSearchStr(), copy.getSearchStr());
        assertEquals(original.getBuildRepo(), copy.getBuildRepo());
        assertEquals(original.getLimit(), copy.getLimit());
    }
}
