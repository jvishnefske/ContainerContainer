package org.artifactory.webapp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.IOUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.ConstantValues;
import org.artifactory.io.checksum.ChecksumUtils;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.artifactory.entitlement.EntitlementType.AI_CATALOG;
import static org.artifactory.entitlement.EntitlementType.PACKAGES_IDE_EXTENSION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Dvir Twina
 */
class FeatureFlagServiceImplTest extends ArtifactoryHomeBoundJUnitTest {

    private final FeatureFlagServiceImpl featureFlagService = new FeatureFlagServiceImpl();
    private final ObjectMapper objectMapper = new ObjectMapper();

    AddonsManager addonsManager;

    @BeforeEach
    void setup() {
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        addonsManager = mock(AddonsManager.class);
        lenient().when(context.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        ArtifactoryContextThreadBinder.bind(context);
        when(addonsManager.isEntitled(PACKAGES_IDE_EXTENSION)).thenReturn(true);
        when(addonsManager.isEntitled(AI_CATALOG)).thenReturn(true);
    }

    @AfterEach
    void cleanup() {
        ArtifactoryContextThreadBinder.unbind();
    }

    public static Stream<Arguments> constantValuesProvider() {
        return Stream.of(
                Arguments.of(ConstantValues.treeBrowserStreamEnabled, false, "artifactoryTreebrowserStreamEnabled"),
                Arguments.of(ConstantValues.treeBrowserStreamEnabled, true, "artifactoryTreebrowserStreamEnabled"),
                Arguments.of(ConstantValues.nimModelSetMeUpRepositoryEnabled, true,
                        "artifactorySet_me_upSupported_packagesNimmodel"),
                Arguments.of(ConstantValues.nimModelSetMeUpRepositoryEnabled,
                        false, "artifactorySet_me_upSupported_packagesNimmodel"),
                Arguments.of(ConstantValues.nimModelRepositoryEnabled, true,
                        "artifactoryRepositoriesPackagesNim"),
                Arguments.of(ConstantValues.nimModelRepositoryEnabled,
                        false, "artifactoryRepositoriesPackagesNim"),
                Arguments.of(ConstantValues.hexRepositoryEnabled, true,
                        "artifactoryRepositoriesPackagesHex"),
                Arguments.of(ConstantValues.hexRepositoryEnabled,
                        false, "artifactoryRepositoriesPackagesHex"),
                Arguments.of(ConstantValues.packagesPackagesListNewUi,
                        false, "artifactoryPackagesPackages_list_new_ui"),
                Arguments.of(ConstantValues.jemRepositoryEnabled, true,
                        "artifactoryJemRepositoryEnabled"),
                Arguments.of(ConstantValues.jemRepositoryEnabled, false,
                        "artifactoryJemRepositoryEnabled"),
                Arguments.of(ConstantValues.packagesPackagesDetailsNewUi, false,
                        "artifactoryPackagesPackages_details_new_ui"),
                Arguments.of(ConstantValues.packagesOnemodel, false,
                        "artifactoryPackagesOnemodel"),
                Arguments.of(ConstantValues.huggingFaceMLVirtualEnabled, false,
                        "artifactoryRepositoriesVirtualHuggingfaceml"),
                Arguments.of(ConstantValues.jetbrainsPluginsRepositoryEnabled, true,
                        "artifactoryRepositoriesPackagesJetbrainsplugins"),
                Arguments.of(ConstantValues.jetbrainsPluginsSetMeUpRepositoryEnabled, true,
                        "artifactorySet_me_upSupported_packagesJetbrainsplugins"),
                Arguments.of(ConstantValues.aieditorExtensionsRepositoryEnabled, true,
                        "artifactoryRepositoriesPackagesAieditorextensions"),
                Arguments.of(ConstantValues.aieditorExtensionsSetMeUpRepositoryEnabled, true,
                        "artifactorySet_me_upSupported_packagesAieditorextensions"),
                Arguments.of(ConstantValues.bazelModulesSetMeUpRepositoryEnabled, true,
                        "artifactorySet_me_upSupported_packagesBazelmodules"),
                Arguments.of(ConstantValues.bazelModulesRepositoryEnabled, true,
                        "artifactoryRepositoriesPackagesBazelmodules"),
                Arguments.of(ConstantValues.debianDscLocalRepoSupportEnabled, true,
                        "artifactoryDebianDscLocalRepoSupportEnabled"),
                Arguments.of(ConstantValues.nugetPackageHandlerSupport, false,
                        "artifactoryPackageHandlerNuget"),
                Arguments.of(ConstantValues.nixRepositoryPackagesEnabled, true,
                        "artifactoryRepositoriesPackagesNix"),
                Arguments.of(ConstantValues.nixRepositoryPackagesEnabled, false,
                        "artifactoryRepositoriesPackagesNix"),
                Arguments.of(ConstantValues.nixSetMeUpRepositoryEnabled, true,
                        "artifactorySet_me_upSupported_packagesNix"),
                Arguments.of(ConstantValues.nixSetMeUpRepositoryEnabled, false,
                        "artifactorySet_me_upSupported_packagesNix"),
                Arguments.of(ConstantValues.luaRocksRepositoryPackagesEnabled, true,
                        "artifactoryRepositoriesPackagesLuarocks"),
                Arguments.of(ConstantValues.luaRocksRepositoryPackagesEnabled, false,
                        "artifactoryRepositoriesPackagesLuarocks"),
                Arguments.of(ConstantValues.luaRocksSetMeUpRepositoryEnabled, true,
                        "artifactorySet_me_upSupported_packagesLuarocks"),
                Arguments.of(ConstantValues.luaRocksSetMeUpRepositoryEnabled, false,
                        "artifactorySet_me_upSupported_packagesLuarocks"),
                Arguments.of(ConstantValues.skillsRepositoryPackagesEnabled, true,
                        "artifactoryRepositoriesPackagesSkills"),
                Arguments.of(ConstantValues.skillsRepositoryPackagesEnabled, false,
                        "artifactoryRepositoriesPackagesSkills"),
                Arguments.of(ConstantValues.skillsSetMeUpRepositoryEnabled, true,
                        "artifactorySet_me_upSupported_packagesSkills"),
                Arguments.of(ConstantValues.skillsSetMeUpRepositoryEnabled, false,
                        "artifactorySet_me_upSupported_packagesSkills"),
                Arguments.of(ConstantValues.skillsUIXrayIntegration, true,
                        "artifactoryPackagesSkillsXrayIntegrationEnabled"),
                Arguments.of(ConstantValues.condaShardsEnabled, true,
                        "artifactoryPackagesCondaShardsEnabled"),
                Arguments.of(ConstantValues.condaShardsEnabled, false,
                        "artifactoryPackagesCondaShardsEnabled"),
                Arguments.of(ConstantValues.nugetPackageHandlerSupport, false,
                        "artifactoryPackageHandlerNuget"),
                Arguments.of(ConstantValues.repositoryMigrationToolEnabled, false,
                             "artifactoryRepositoryMigrationToolEnabled"),
                Arguments.of(ConstantValues.agentPluginsRepositoryPackagesEnabled, false,
                             "artifactoryRepositoriesPackagesAgentplugins"),
                Arguments.of(ConstantValues.agentPluginsRepositoryPackagesEnabled, true,
                             "artifactoryRepositoriesPackagesAgentplugins"),
                Arguments.of(ConstantValues.agentPluginsSetMeUpRepositoryEnabled, false,
                             "artifactorySet_me_upSupported_packagesAgentplugins"),
                Arguments.of(ConstantValues.agentPluginsSetMeUpRepositoryEnabled, true,
                             "artifactorySet_me_upSupported_packagesAgentplugins"),
                Arguments.of(ConstantValues.agentPackagesRepositoryPackagesEnabled, false,
                             "artifactoryRepositoriesPackagesAgentpackages"),
                Arguments.of(ConstantValues.agentPackagesRepositoryPackagesEnabled, true,
                             "artifactoryRepositoriesPackagesAgentpackages"),
                Arguments.of(ConstantValues.curationFailOpenUiEnabled, true,
                             "artifactoryCurationFailopenuiEnabled"),
                Arguments.of(ConstantValues.curationFailOpenUiEnabled, false,
                             "artifactoryCurationFailopenuiEnabled"),
                Arguments.of(ConstantValues.rtuiMfeControlledRolloutEnabled, true,
                             "artifactoryRtuiMfeControlledRolloutEnabled"),
                Arguments.of(ConstantValues.rtuiMfeControlledRolloutEnabled, false,
                             "artifactoryRtuiMfeControlledRolloutEnabled"));
    }

    @ParameterizedTest
    @MethodSource("constantValuesProvider")
    void testGetAllFeatureFlags(ConstantValues featureFlag, boolean value, String jsonName)
            throws JsonProcessingException {
        getOrCreateArtifactoryHomeStub().setProperty(featureFlag, String.valueOf(value));

        featureFlagService.init();
        String allFeatureFlags = featureFlagService.getAllFeatureFlags();

        Map<String, Boolean> map = objectMapper.readValue(allFeatureFlags, Map.class);
        Assertions.assertNotNull(map.get(jsonName));
        assertEquals(value, map.get(jsonName));
    }

    @Test
    void testAiCatalogEntitlementGatesSkillsXrayFlag()
            throws JsonProcessingException {
        String flagKey = "artifactoryPackagesSkillsXrayIntegrationEnabled";

        when(addonsManager.isEntitled(AI_CATALOG)).thenReturn(true);
        featureFlagService.init();
        String flags = featureFlagService.getAllFeatureFlags();
        Map<String, Boolean> map = objectMapper.readValue(flags, Map.class);
        assertEquals(true, map.get(flagKey));

        when(addonsManager.isEntitled(AI_CATALOG)).thenReturn(false);
        flags = featureFlagService.getAllFeatureFlags();
        map = objectMapper.readValue(flags, Map.class);
        assertEquals(false, map.get(flagKey));

        when(addonsManager.isEntitled(AI_CATALOG)).thenReturn(true);
        flags = featureFlagService.getAllFeatureFlags();
        map = objectMapper.readValue(flags, Map.class);
        assertEquals(true, map.get(flagKey));
    }

    @Test
    void testAiCatalogEntitlementHashRecalculation() {
        when(addonsManager.isEntitled(AI_CATALOG)).thenReturn(true);
        featureFlagService.init();
        String entitledFlags = featureFlagService.getAllFeatureFlags();
        String entitledHash = featureFlagService.featureFlagsHash();

        when(addonsManager.isEntitled(AI_CATALOG)).thenReturn(false);
        String unentitledFlags = featureFlagService.getAllFeatureFlags();
        String unentitledHash = featureFlagService.featureFlagsHash();

        assertNotEquals(entitledFlags, unentitledFlags);
        assertNotEquals(entitledHash, unentitledHash);
        assertEquals(entitledHash, ChecksumUtils.calculateMD5(
                IOUtils.toInputStream(entitledFlags, StandardCharsets.UTF_8)));
        assertEquals(unentitledHash, ChecksumUtils.calculateMD5(
                IOUtils.toInputStream(unentitledFlags, StandardCharsets.UTF_8)));
    }

    @Test
    void controlledRolloutMfeFlagChangesFeatureFlagsHash() {
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.rtuiMfeControlledRolloutEnabled, "true");
        featureFlagService.init();
        String hashWhenTrue = featureFlagService.featureFlagsHash();

        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.rtuiMfeControlledRolloutEnabled, "false");
        featureFlagService.init();
        String hashWhenFalse = featureFlagService.featureFlagsHash();

        assertNotEquals(hashWhenTrue, hashWhenFalse);
    }

    @Test
    void testGetAllFeatureFlagsWithMultipleFeatureFlags()
            throws NoSuchFieldException, IllegalAccessException, JsonProcessingException {

        Field f = Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        Unsafe unsafe = (Unsafe) f.get(null);
        Field field = FeatureFlagServiceImpl.class.getDeclaredField("FEATURE_FLAGS");
        long fieldOffset = unsafe.staticFieldOffset(field);
        Object originalFlags = unsafe.getObject(FeatureFlagServiceImpl.class, fieldOffset);
        try {
            Set<ConstantValues> featureFlags = EnumSet.of(
                    ConstantValues.jetbrainsPluginsRepositoryEnabled,
                    ConstantValues.aieditorExtensionsSetMeUpRepositoryEnabled,
                    ConstantValues.skillsUIXrayIntegration);
            unsafe.putObject(FeatureFlagServiceImpl.class, fieldOffset, featureFlags);
            String allFeatureFlags = "";
            String featureFlagsHash = "";

            List<Boolean> expectedValues = List.of(false, true, false, true);
            featureFlagService.init();
            int count = 0;
            for (boolean entitled : expectedValues) {
                when(addonsManager.isEntitled(PACKAGES_IDE_EXTENSION)).thenReturn(entitled);
                when(addonsManager.isEntitled(AI_CATALOG)).thenReturn(entitled);
                if (count < 2) {
                    allFeatureFlags = featureFlagService.getAllFeatureFlags();
                    featureFlagsHash = featureFlagService.featureFlagsHash();
                    count++;
                } else {
                    featureFlagsHash = featureFlagService.featureFlagsHash();
                    allFeatureFlags = featureFlagService.getAllFeatureFlags();
                }

                Assertions.assertEquals(featureFlagsHash, ChecksumUtils.calculateMD5(
                        IOUtils.toInputStream(allFeatureFlags, StandardCharsets.UTF_8)));

                Map<String, Boolean> map = objectMapper.readValue(allFeatureFlags, Map.class);
                Assertions.assertEquals(entitled,
                        map.get("artifactoryRepositoriesPackagesJetbrainsplugins"));
                Assertions.assertEquals(entitled,
                        map.get("artifactorySet_me_upSupported_packagesAieditorextensions"));
                Assertions.assertEquals(entitled,
                        map.get("artifactoryPackagesSkillsXrayIntegrationEnabled"));
            }
        } finally {
            unsafe.putObject(FeatureFlagServiceImpl.class, fieldOffset, originalFlags);
        }
    }
}