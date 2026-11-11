package org.artifactory.repo.service;

import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.federated.FederatedMember;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.artifactory.descriptor.repo.RepoType.Generic;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link RepositoryGridConfigUtils}.
 */
class RepositoryGridConfigUtilsTest extends ArtifactoryHomeBoundJUnitTest {

    private void setGridEnabled(boolean enabled) {
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.gridEnabled.getPropertyName(), String.valueOf(enabled));
    }

    // ========================================================================================
    // validateNoGridTopologyIdWhenGridDisabled Tests
    // ========================================================================================

    @ParameterizedTest(name = "validateNoGridTopologyIdWhenGridDisabled: gridEnabled={0}, hasTopologyId={1}")
    @CsvSource({
            // Grid enabled - always passes regardless of gridTopologyId
            "true, true, true",
            "true, false, true",
            // Grid disabled - passes only if no gridTopologyId
            "false, false, true",
            // Grid disabled with gridTopologyId - should fail
            "false, true, false"
    })
    void validateNoGridTopologyIdWhenGridDisabled_shouldValidateBasedOnGridStateAndTopologyId(
            boolean gridEnabled, boolean hasTopologyId, boolean shouldPass) {
        // Arrange
        setGridEnabled(gridEnabled);
        LocalRepoConfig config = LocalRepoConfig.create("test-repo", Generic);
        if (hasTopologyId) {
            config.getBaseConfig().setGridTopologyId("topology-123");
        }

        // Act & Assert
        if (shouldPass) {
            assertDoesNotThrow(
                    () -> RepositoryGridConfigUtils.validateNoGridTopologyIdWhenGridDisabled(List.of(config)));
        } else {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> RepositoryGridConfigUtils.validateNoGridTopologyIdWhenGridDisabled(List.of(config)));
            assertTrue(exception.getMessage().contains("gridTopologyId"));
            assertTrue(exception.getMessage().contains("grid is disabled"));
        }
    }

    // ========================================================================================
    // hasFederationCapability Tests
    // ========================================================================================

    @ParameterizedTest(name = "hasFederationCapability gridEnabled={0}, hasTopologyId={1} → expected={2}")
    @CsvSource({
            // Grid enabled: topology ID determines capability
            "true, true, true",
            "true, false, false",
            // Grid disabled: topology ID doesn't matter (needs members instead)
            "false, true, false",
            "false, false, false"
    })
    void hasFederationCapability_shouldCheckTopologyId_basedOnGridState(
            boolean gridEnabled, boolean hasTopologyId, boolean expected) {
        setGridEnabled(gridEnabled);
        LocalRepoConfig config = LocalRepoConfig.create("test-repo", Generic);
        if (hasTopologyId) {
            config.getBaseConfig().setGridTopologyId("topology-123");
        }

        boolean result = RepositoryGridConfigUtils.hasFederationCapability(config);

        assertEquals(expected, result);
    }

    @Test
    void hasFederationCapability_shouldReturnTrue_whenGridDisabledAndHasMembers() {
        // Unique scenario: grid disabled but has federation members
        setGridEnabled(false);
        FederatedRepoConfig federatedConfig = FederatedRepoConfig.create("test-repo", Generic);
        FederatedMember member = new FederatedMember();
        member.setUrl("http://site1.example.com/artifactory/test-repo");
        federatedConfig.getRepoTypeConfig().setFederatedMembers(List.of(member));
        LocalRepoConfig config = LocalRepoConfig.fromFederated(federatedConfig);

        boolean result = RepositoryGridConfigUtils.hasFederationCapability(config);

        assertTrue(result);
    }

    @ParameterizedTest(name = "hasFederationCapability gridDisabled, invalidFederation case={0} → false")
    @MethodSource("invalidFederationProvider")
    void hasFederationCapability_shouldReturnFalse_whenGridDisabledAndInvalidFederation(
            String caseName, LocalRepoConfig config) {
        setGridEnabled(false);

        boolean result = RepositoryGridConfigUtils.hasFederationCapability(config);

        assertFalse(result);
    }

    private static Stream<Arguments> invalidFederationProvider() {
        return Stream.of(
                // No federation data at all
                Arguments.of("noFederation", LocalRepoConfig.create("test-repo", Generic)),
                // Null members
                Arguments.of("nullMembers", createLocalWithNullMembers()),
                // Empty members list
                Arguments.of("emptyMembers", createLocalWithEmptyMembers())
        );
    }

    private static LocalRepoConfig createLocalWithNullMembers() {
        FederatedRepoConfig config = FederatedRepoConfig.create("test-repo", Generic);
        config.getRepoTypeConfig().setFederatedMembers(null);
        return LocalRepoConfig.fromFederated(config);
    }

    private static LocalRepoConfig createLocalWithEmptyMembers() {
        FederatedRepoConfig config = FederatedRepoConfig.create("test-repo", Generic);
        config.getRepoTypeConfig().setFederatedMembers(List.of());
        return LocalRepoConfig.fromFederated(config);
    }

    @Test
    void hasFederationCapability_shouldReturnFalse_whenNonLocalRepoConfig() {
        // Unique scenario: remote config (not local) should always return false
        setGridEnabled(false);
        RemoteRepoConfig remoteConfig = RemoteRepoConfig.create("remote-repo", Generic);

        boolean result = RepositoryGridConfigUtils.hasFederationCapability(remoteConfig);

        assertFalse(result);
    }

    // ========================================================================================
    // detectLocalReposWithFederation Tests
    // ========================================================================================

    @ParameterizedTest(name = "detectLocalReposWithFederation: gridEnabled={0}, triggerType={1}")
    @CsvSource({
            // Grid enabled: detects by gridTopologyId
            "true, topologyId",
            // Grid disabled: detects by federation members
            "false, members"
    })
    void detectLocalReposWithFederation_shouldDetectBasedOnGridState(boolean gridEnabled, String triggerType) {
        setGridEnabled(gridEnabled);

        LocalRepoConfig localWithTrigger;
        if ("topologyId".equals(triggerType)) {
            localWithTrigger = LocalRepoConfig.create("local-with-trigger", Generic);
            localWithTrigger.getBaseConfig().setGridTopologyId("topology-123");
        } else {
            FederatedRepoConfig federatedConfig = FederatedRepoConfig.create("local-with-trigger", Generic);
            FederatedMember member = new FederatedMember();
            member.setUrl("http://site1.example.com/artifactory/local-with-trigger");
            federatedConfig.getRepoTypeConfig().setFederatedMembers(List.of(member));
            localWithTrigger = LocalRepoConfig.fromFederated(federatedConfig);
        }

        LocalRepoConfig localWithoutTrigger = LocalRepoConfig.create("local-without-trigger", Generic);
        FederatedRepoConfig federatedRepo = FederatedRepoConfig.create("federated-repo", Generic);

        ArrayList<RepoConfig> configs = new ArrayList<>();
        configs.add(localWithTrigger);
        configs.add(localWithoutTrigger);
        configs.add(federatedRepo);

        List<RepoConfig> result = RepositoryGridConfigUtils.detectLocalReposWithFederation(configs);

        assertEquals(1, result.size());
        assertEquals("local-with-trigger", result.get(0).getKey());
    }

    // ========================================================================================
    // shouldMigrateToFederated Tests
    // ========================================================================================

    @ParameterizedTest(name = "shouldMigrateToFederated: {0}")
    @MethodSource("shouldMigrateToFederatedProvider")
    void shouldMigrateToFederated_shouldReturnExpectedResult(
            String caseName, boolean gridEnabled, String gridTopologyId,
            LocalRepoConfig.FederationExportData federationData, boolean expected) {
        setGridEnabled(gridEnabled);

        boolean result =
                RepositoryGridConfigUtils.shouldMigrateToFederated(gridTopologyId, federationData, "test-repo");

        assertEquals(expected, result);
    }

    private static Stream<Arguments> shouldMigrateToFederatedProvider() {
        LocalRepoConfig.FederationExportData withMembers = createFederationDataWithMembers();
        LocalRepoConfig.FederationExportData emptyMembers = new LocalRepoConfig.FederationExportData();
        emptyMembers.setMembers(List.of());

        return Stream.of(
                // Grid enabled: gridTopologyId triggers migration
                Arguments.of("gridEnabled+topologyId", true, "topology-123", null, true),
                // Grid enabled: no topologyId - should not migrate
                Arguments.of("gridEnabled+noTopologyId", true, null, null, false),
                Arguments.of("gridEnabled+emptyTopologyId", true, "", null, false),
                // Grid disabled: federation members trigger migration
                Arguments.of("gridDisabled+members", false, null, withMembers, true),
                // Grid disabled: no members - should not migrate
                Arguments.of("gridDisabled+noMembers", false, null, null, false),
                Arguments.of("gridDisabled+emptyMembers", false, null, emptyMembers, false)
        );
    }

    private static LocalRepoConfig.FederationExportData createFederationDataWithMembers() {
        FederatedMember member = new FederatedMember();
        member.setUrl("http://site1.example.com/artifactory/test-repo");
        member.setEnabled(true);
        LocalRepoConfig.FederationExportData data = new LocalRepoConfig.FederationExportData();
        data.setMembers(List.of(member));
        return data;
    }
}
