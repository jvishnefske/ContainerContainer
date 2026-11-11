package com.jfrog.build.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jfrog.build.acl.model.BuildId;
import com.jfrog.build.model.api.ContinueBuildFilter;
import com.jfrog.build.acl.util.BuildInfoUtils;
import com.jfrog.build.model.api.ContinueBuildDto;
import com.jfrog.build.model.api.GeneralBuildInfo;
import org.jfrog.commons.continues.util.Direction;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static com.jfrog.build.acl.util.BuildConstants.BUILDINFO_REPO_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class BuildContinueTranslatorTest {

    private static final long NOW = 1704067200000L;

    @Test
    void buildIdToBase64_shouldEncodeBuildFields_whenGeneralBuildInfoProvided() throws IOException {
        // Arrange
        GeneralBuildInfo generalBuildInfo = GeneralBuildInfo.builder()
                .buildName("some-build-name")
                .buildNumber("1")
                .time(NOW)
                .build();

        // Act
        String encoded = BuildContinueTranslator.buildIdToBase64(generalBuildInfo);

        // Assert
        JsonNode jsonNode = new ObjectMapper().readTree(Base64.getDecoder().decode(encoded));
        assertEquals("some-build-name", jsonNode.get("buildName").asText());
        assertEquals("1", jsonNode.get("buildNumber").asText());
    }

    @Test
    void base64ToBuildId_shouldRestoreBuildId_whenValidPayloadProvided() {
        // Arrange
        String json = "{\"buildName\":\"some-build-name\", \"buildNumber\":\"1\", \"time\": " + NOW + "}";
        String encoded = Base64.getEncoder().encodeToString(json.getBytes());

        // Act
        BuildId buildId = BuildContinueTranslator.base64ToBuildId(encoded);

        // Assert
        assertNotNull(buildId);
        assertEquals("some-build-name", buildId.getName());
        assertEquals("1", buildId.getNumber());
        assertEquals(NOW, buildId.getStartedDate().getTime());
    }

    @Test
    void buildIdToBase64AndBack_shouldRoundTripBuildInfo() {
        // Arrange
        GeneralBuildInfo original = GeneralBuildInfo.builder()
                .buildName("some-build-name")
                .buildNumber("2")
                .lastBuildTime(BuildInfoUtils.formatBuildTime(NOW))
                .time(NOW)
                .buildRepo(BUILDINFO_REPO_KEY)
                .build();

        // Act
        String encoded = BuildContinueTranslator.buildIdToBase64(original);
        GeneralBuildInfo restored = BuildContinueTranslator.base64ToGeneralBuildInfo(encoded);

        // Assert
        assertNotNull(restored);
        assertEquals(original.getBuildName(), restored.getBuildName());
        assertEquals(original.getBuildNumber(), restored.getBuildNumber());
        assertEquals(original.getTime(), restored.getTime());
        assertEquals(original.getBuildRepo(), restored.getBuildRepo());
    }

    @Test
    void base64ToGeneralBuildInfo_shouldReturnNull_whenInputIsNull() {
        assertNull(BuildContinueTranslator.base64ToGeneralBuildInfo(null));
    }

    @Test
    void base64ToBuildId_shouldReturnNull_whenInputIsNull() {
        assertNull(BuildContinueTranslator.base64ToBuildId(null));
    }

    @Test
    void buildIdToBase64_shouldReturnNull_whenInputIsNull() {
        assertNull(BuildContinueTranslator.buildIdToBase64(null));
    }

    @Test
    void toBuildFilter_shouldDefaultDirectionToDesc_whenOrderByBuildDateAndDirectionMissing() {
        // Arrange
        ContinueBuildDto continueBuildDto = new ContinueBuildDto(
                Map.of("orderBy", new String[] {"build_date"}),
                projectKey -> BUILDINFO_REPO_KEY);

        // Act
        ContinueBuildFilter filter = BuildContinueTranslator.toBuildFilter(continueBuildDto);

        // Assert
        assertEquals(ContinueBuildFilter.OrderBy.BUILD_DATE, filter.getOrderBy());
        assertEquals(Direction.DESC, filter.getDirection());
    }

    @Test
    void toBuildFilter_shouldSetContinueBuildId_whenContinueStateProvided() {
        // Arrange
        GeneralBuildInfo continueBuild = GeneralBuildInfo.builder()
                .buildName("some-build-name")
                .buildNumber("1")
                .time(NOW)
                .buildRepo(BUILDINFO_REPO_KEY)
                .build();
        String continueState = BuildContinueTranslator.buildIdToBase64(continueBuild);
        Map<String, String[]> params = new HashMap<>();
        params.put("continueState", new String[] {continueState});
        ContinueBuildDto continueBuildDto = new ContinueBuildDto(params, projectKey -> BUILDINFO_REPO_KEY);

        // Act
        ContinueBuildFilter filter = BuildContinueTranslator.toBuildFilter(continueBuildDto);

        // Assert
        assertNotNull(filter.getContinueBuildId());
        assertEquals("some-build-name", filter.getContinueBuildId().getName());
        assertEquals("1", filter.getContinueBuildId().getNumber());
    }

    @Test
    void toBuildFilter_shouldSetBuildRepo_whenContinueStateMissingAndBuildRepoProvided() {
        // Arrange
        ContinueBuildDto continueBuildDto = new ContinueBuildDto(
                Map.of("projectKey", new String[] {"phoenix"}),
                projectKey -> "phoenix-build-info");

        // Act
        ContinueBuildFilter filter = BuildContinueTranslator.toBuildFilter(continueBuildDto);

        // Assert
        assertNull(filter.getContinueBuildId());
        assertEquals("phoenix-build-info", filter.getBuildRepo());
    }
}
