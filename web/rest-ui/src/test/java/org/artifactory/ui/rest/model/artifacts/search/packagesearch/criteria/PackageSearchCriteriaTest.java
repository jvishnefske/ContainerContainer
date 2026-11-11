package org.artifactory.ui.rest.model.artifacts.search.packagesearch.criteria;

import org.artifactory.ui.rest.service.artifacts.search.packagesearch.strategy.AqlUISearchStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Katrin Shor
 */
class PackageSearchCriteriaTest {

    @Test
    void testGetStrategyByFieldIdWithNullIdThrowsIllegalArgumentException() {
        final IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> PackageSearchCriteria.getStrategyByFieldId(null));

        assertTrue(exception.getMessage().contains("Field ID cannot be null or empty"));
    }

    @Test
    void testGetStrategyByFieldIdWithEmptyIdThrowsIllegalArgumentException() {
        final IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> PackageSearchCriteria.getStrategyByFieldId(""));

        assertTrue(exception.getMessage().contains("Field ID cannot be null or empty"));
    }

    @Test
    void testGetStrategyByFieldIdWithInvalidIdThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> PackageSearchCriteria.getStrategyByFieldId("invalidField"));
    }

    @Test
    void testGetStrategyByFieldIdWithValidIdReturnsStrategy() {
        final AqlUISearchStrategy strategy = PackageSearchCriteria.getStrategyByFieldId("npmName");
        assertNotNull(strategy);
    }

    @Test
    void testGetPackageTypeByFieldIdWithNullIdReturnsNull() {
        final PackageSearchCriteria.PackageSearchType result = PackageSearchCriteria.getPackageTypeByFieldId(null);
        assertNull(result);
    }

    @Test
    void testGetPackageTypeByFieldIdWithEmptyIdReturnsNull() {
        final PackageSearchCriteria.PackageSearchType result = PackageSearchCriteria.getPackageTypeByFieldId("");
        assertNull(result);
    }

    @Test
    void testGetPackageTypeByFieldIdWithInvalidIdReturnsNull() {
        final PackageSearchCriteria.PackageSearchType result =
                PackageSearchCriteria.getPackageTypeByFieldId("invalidFieldId");
        assertNull(result);
    }

    @Test
    void testGetPackageTypeByFieldIdWithValidIdReturnsPackageType() {
        final PackageSearchCriteria.PackageSearchType result = PackageSearchCriteria.getPackageTypeByFieldId("npmName");
        assertEquals(result.getId(), PackageSearchCriteria.PackageSearchType.npm.getId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"nixName", "nixVersion", "nixHash", "nixSystem"})
    void getStrategyByFieldId_shouldReturnStrategy_whenGivenNixFieldId(String fieldId) {
        AqlUISearchStrategy strategy = PackageSearchCriteria.getStrategyByFieldId(fieldId);
        assertNotNull(strategy);
    }

    @ParameterizedTest
    @ValueSource(strings = {"nixName", "nixVersion", "nixHash", "nixSystem"})
    void getPackageTypeByFieldId_shouldReturnNixType_whenGivenNixFieldId(String fieldId) {
        PackageSearchCriteria.PackageSearchType result = PackageSearchCriteria.getPackageTypeByFieldId(fieldId);
        assertEquals(PackageSearchCriteria.PackageSearchType.nix, result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"nix.name", "nix.version", "nix.store.path", "nix.system"})
    void getCriterionByAqlFieldOrPropName_shouldReturnCriterion_whenGivenNixAqlName(String aqlName) {
        PackageSearchCriteria.PackageSearchCriterion criterion =
                PackageSearchCriteria.getCriterionByAqlFieldOrPropName(aqlName);
        assertNotNull(criterion);
        assertEquals(PackageSearchCriteria.PackageSearchType.nix, criterion.getType());
    }

    @Test
    void getCriterionByAqlFieldOrPropName_shouldReturnNull_whenGivenInvalidNixAqlName() {
        PackageSearchCriteria.PackageSearchCriterion criterion =
                PackageSearchCriteria.getCriterionByAqlFieldOrPropName("nix.invalid");
        assertNull(criterion);
    }

    @Test
    void getCriteriaByPackage_shouldReturnAllNixCriteria_whenGivenNixPackageType() {
        List<PackageSearchCriteria.PackageSearchCriterion> criteria =
                PackageSearchCriteria.getCriteriaByPackage(PackageSearchCriteria.PackageSearchType.nix);
        assertEquals(4, criteria.size());
        assertTrue(criteria.stream().allMatch(c -> c.getType() == PackageSearchCriteria.PackageSearchType.nix));
    }

    @Test
    void getCriteriaByPackage_shouldReturnAllNixCriteria_whenGivenNixPackageTypeString() {
        List<PackageSearchCriteria.PackageSearchCriterion> criteria =
                PackageSearchCriteria.getCriteriaByPackage("nix");
        assertEquals(4, criteria.size());
        assertTrue(criteria.stream().allMatch(c -> c.getType() == PackageSearchCriteria.PackageSearchType.nix));
    }

    @ParameterizedTest
    @ValueSource(strings = {"luaRocksName", "luaRocksVersion"})
    void getPackageTypeByFieldId_shouldReturnLuaRocksType_whenGivenLuaRocksFieldId(String fieldId) {
        PackageSearchCriteria.PackageSearchType result = PackageSearchCriteria.getPackageTypeByFieldId(fieldId);
        assertEquals(PackageSearchCriteria.PackageSearchType.luarocks, result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"luarocks.name", "luarocks.version"})
    void getCriterionByAqlFieldOrPropName_shouldReturnCriterion_whenGivenLuaRocksAqlName(String aqlName) {
        PackageSearchCriteria.PackageSearchCriterion criterion =
                PackageSearchCriteria.getCriterionByAqlFieldOrPropName(aqlName);
        assertNotNull(criterion);
        assertEquals(PackageSearchCriteria.PackageSearchType.luarocks, criterion.getType());
    }

    @Test
    void getCriterionByAqlFieldOrPropName_shouldReturnNull_whenGivenInvalidLuaRocksAqlName() {
        PackageSearchCriteria.PackageSearchCriterion criterion =
                PackageSearchCriteria.getCriterionByAqlFieldOrPropName("luarocks.invalid");
        assertNull(criterion);
    }

    @Test
    void getCriteriaByPackage_shouldReturnAllLuaRocksCriteria_whenGivenLuaRocksPackageType() {
        List<PackageSearchCriteria.PackageSearchCriterion> criteria =
                PackageSearchCriteria.getCriteriaByPackage(PackageSearchCriteria.PackageSearchType.luarocks);
        assertEquals(2, criteria.size());
        assertTrue(criteria.stream().allMatch(c -> c.getType() == PackageSearchCriteria.PackageSearchType.luarocks));
    }

    @Test
    void getCriteriaByPackage_shouldReturnAllLuaRocksCriteria_whenGivenLuaRocksPackageTypeString() {
        List<PackageSearchCriteria.PackageSearchCriterion> criteria =
                PackageSearchCriteria.getCriteriaByPackage("luarocks");
        assertEquals(2, criteria.size());
        assertTrue(criteria.stream().allMatch(c -> c.getType() == PackageSearchCriteria.PackageSearchType.luarocks));
    }

    @ParameterizedTest
    @ValueSource(strings = {"skillsName", "skillsVersion"})
    void getPackageTypeByFieldId_shouldReturnSkillsType_whenGivenSkillsFieldId(String fieldId) {
        PackageSearchCriteria.PackageSearchType result =
                PackageSearchCriteria.getPackageTypeByFieldId(fieldId);
        assertEquals(PackageSearchCriteria.PackageSearchType.skills, result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"skill.name", "skill.version"})
    void getCriterionByAqlFieldOrPropName_shouldReturnCriterion_whenGivenSkillsAqlName(
            String aqlName) {
        PackageSearchCriteria.PackageSearchCriterion criterion =
                PackageSearchCriteria.getCriterionByAqlFieldOrPropName(aqlName);
        assertNotNull(criterion);
        assertEquals(PackageSearchCriteria.PackageSearchType.skills, criterion.getType());
    }

    @Test
    void getCriterionByAqlFieldOrPropName_shouldReturnNull_whenGivenInvalidSkillsAqlName() {
        PackageSearchCriteria.PackageSearchCriterion criterion =
                PackageSearchCriteria.getCriterionByAqlFieldOrPropName("skill.invalid");
        assertNull(criterion);
    }

    @Test
    void getCriteriaByPackage_shouldReturnAllSkillsCriteria_whenGivenSkillsPackageType() {
        List<PackageSearchCriteria.PackageSearchCriterion> criteria =
                PackageSearchCriteria.getCriteriaByPackage(
                        PackageSearchCriteria.PackageSearchType.skills);
        assertEquals(2, criteria.size());
        assertTrue(criteria.stream()
                .allMatch(c -> c.getType()
                        == PackageSearchCriteria.PackageSearchType.skills));
    }

    @Test
    void getCriteriaByPackage_shouldReturnAllSkillsCriteria_whenGivenSkillsPackageTypeString() {
        List<PackageSearchCriteria.PackageSearchCriterion> criteria =
                PackageSearchCriteria.getCriteriaByPackage("skills");
        assertEquals(2, criteria.size());
        assertTrue(criteria.stream()
                .allMatch(c -> c.getType()
                        == PackageSearchCriteria.PackageSearchType.skills));
    }

    @ParameterizedTest
    @ValueSource(strings = {"agentPluginsName", "agentPluginsVersion"})
    void getPackageTypeByFieldId_shouldReturnAgentPluginsType_whenGivenAgentPluginsFieldId(String fieldId) {
        PackageSearchCriteria.PackageSearchType result =
                PackageSearchCriteria.getPackageTypeByFieldId(fieldId);
        assertEquals(PackageSearchCriteria.PackageSearchType.agentplugins, result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"agentPluginsName", "agentPluginsVersion"})
    void getStrategyByFieldId_shouldReturnStrategy_whenGivenAgentPluginsFieldId(String fieldId) {
        AqlUISearchStrategy strategy = PackageSearchCriteria.getStrategyByFieldId(fieldId);
        assertNotNull(strategy);
    }

    @ParameterizedTest
    @ValueSource(strings = {"agentplugins.name", "agentplugins.version"})
    void getCriterionByAqlFieldOrPropName_shouldReturnCriterion_whenGivenAgentPluginsAqlName(String aqlName) {
        PackageSearchCriteria.PackageSearchCriterion criterion =
                PackageSearchCriteria.getCriterionByAqlFieldOrPropName(aqlName);
        assertNotNull(criterion);
        assertEquals(PackageSearchCriteria.PackageSearchType.agentplugins, criterion.getType());
    }

    @Test
    void getCriterionByAqlFieldOrPropName_shouldReturnNull_whenGivenInvalidAgentPluginsAqlName() {
        PackageSearchCriteria.PackageSearchCriterion criterion =
                PackageSearchCriteria.getCriterionByAqlFieldOrPropName("agentplugins.invalid");
        assertNull(criterion);
    }

    @Test
    void getCriteriaByPackage_shouldReturnAllAgentPluginsCriteria_whenGivenAgentPluginsPackageType() {
        List<PackageSearchCriteria.PackageSearchCriterion> criteria =
                PackageSearchCriteria.getCriteriaByPackage(
                        PackageSearchCriteria.PackageSearchType.agentplugins);
        assertEquals(2, criteria.size());
        assertTrue(criteria.stream()
                .allMatch(c -> c.getType()
                        == PackageSearchCriteria.PackageSearchType.agentplugins));
    }

    @Test
    void getCriteriaByPackage_shouldReturnAllAgentPluginsCriteria_whenGivenAgentPluginsPackageTypeString() {
        List<PackageSearchCriteria.PackageSearchCriterion> criteria =
                PackageSearchCriteria.getCriteriaByPackage("agentplugins");
        assertEquals(2, criteria.size());
        assertTrue(criteria.stream()
                .allMatch(c -> c.getType()
                        == PackageSearchCriteria.PackageSearchType.agentplugins));
    }

    @ParameterizedTest
    @ValueSource(strings = {"agentPackagesName", "agentPackagesVersion"})
    void getPackageTypeByFieldId_shouldReturnAgentPackagesType_whenGivenAgentPackagesFieldId(String fieldId) {
        PackageSearchCriteria.PackageSearchType result =
                PackageSearchCriteria.getPackageTypeByFieldId(fieldId);
        assertEquals(PackageSearchCriteria.PackageSearchType.agentpackages, result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"agentPackagesName", "agentPackagesVersion"})
    void getStrategyByFieldId_shouldReturnStrategy_whenGivenAgentPackagesFieldId(String fieldId) {
        AqlUISearchStrategy strategy = PackageSearchCriteria.getStrategyByFieldId(fieldId);
        assertNotNull(strategy);
    }

    @ParameterizedTest
    @ValueSource(strings = {"agentpackages.gitrepo", "agentpackages.version"})
    void getCriterionByAqlFieldOrPropName_shouldReturnCriterion_whenGivenAgentPackagesAqlName(String aqlName) {
        PackageSearchCriteria.PackageSearchCriterion criterion =
                PackageSearchCriteria.getCriterionByAqlFieldOrPropName(aqlName);
        assertNotNull(criterion);
        assertEquals(PackageSearchCriteria.PackageSearchType.agentpackages, criterion.getType());
    }

    @Test
    void getCriterionByAqlFieldOrPropName_shouldReturnNull_whenGivenInvalidAgentPackagesAqlName() {
        PackageSearchCriteria.PackageSearchCriterion criterion =
                PackageSearchCriteria.getCriterionByAqlFieldOrPropName("agentpackages.invalid");
        assertNull(criterion);
    }

    @Test
    void getCriteriaByPackage_shouldReturnAllAgentPackagesCriteria_whenGivenAgentPackagesPackageType() {
        List<PackageSearchCriteria.PackageSearchCriterion> criteria =
                PackageSearchCriteria.getCriteriaByPackage(
                        PackageSearchCriteria.PackageSearchType.agentpackages);
        assertEquals(2, criteria.size());
        assertTrue(criteria.stream()
                .allMatch(c -> c.getType()
                        == PackageSearchCriteria.PackageSearchType.agentpackages));
    }

    @Test
    void getCriteriaByPackage_shouldReturnAllAgentPackagesCriteria_whenGivenAgentPackagesPackageTypeString() {
        List<PackageSearchCriteria.PackageSearchCriterion> criteria =
                PackageSearchCriteria.getCriteriaByPackage("agentpackages");
        assertEquals(2, criteria.size());
        assertTrue(criteria.stream()
                .allMatch(c -> c.getType()
                        == PackageSearchCriteria.PackageSearchType.agentpackages));
    }

}
