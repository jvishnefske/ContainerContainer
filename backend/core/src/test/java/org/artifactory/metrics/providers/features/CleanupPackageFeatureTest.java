package org.artifactory.metrics.providers.features;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.cleanup.CleanupPolicyAddon;
import org.artifactory.addon.cleanup.statistics.CleanupPackageStats;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.ConstantValues;
import org.artifactory.entitlement.EntitlementType;
import org.artifactory.spring.InternalArtifactoryContext;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.artifactory.metrics.providers.features.CleanupPackageFeature.LAST_DAY_CLEANED_ARTIFACTS;
import static org.artifactory.metrics.providers.features.CleanupPackageFeature.LAST_DAY_CLEANED_PACKAGES;
import static org.artifactory.metrics.providers.features.CleanupPackageFeature.LAST_DAY_CLEANED_PACKAGES_PHYSICAL_SIZE;
import static org.artifactory.metrics.providers.features.CleanupPackageFeature.LAST_DAY_CLEANED_PACKAGES_VIRTUAL_SIZE;
import static org.artifactory.metrics.providers.features.CleanupPackageFeature.LAST_DAY_PACKAGES_FAILED;
import static org.artifactory.metrics.providers.features.CleanupPackageFeature.LAST_DAY_RUNS;
import static org.artifactory.metrics.providers.features.CleanupPackageFeature.TOTAL_CLEANUP_PACKAGES_POLICIES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Oleksandr Legenkiy
 */
class CleanupPackageFeatureTest extends ArtifactoryHomeBoundJUnitTest {

    private CleanupPolicyAddon cleanupPolicyAddonMock;
    private CleanupPackageFeature feature;
    private AddonsManager addonsManagerMock;

    @BeforeEach
    void setup() {
        cleanupPolicyAddonMock = mock(CleanupPolicyAddon.class);
        addonsManagerMock = mock(AddonsManager.class);
        when(addonsManagerMock.addonByType(CleanupPolicyAddon.class)).thenReturn(cleanupPolicyAddonMock);
        InternalArtifactoryContext context = mock(InternalArtifactoryContext.class);
        ArtifactoryContextThreadBinder.bind(context);
        when(context.beanForType(AddonsManager.class)).thenReturn(addonsManagerMock);
        feature = new CleanupPackageFeature();
        getBound().setProperty(ConstantValues.policyCleanupPackageEnabled, "true");
    }

    @AfterEach
    void unbindContext() {
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void testGetFeatureSuccess() {
        when(addonsManagerMock.isEntitled(EntitlementType.CLEANUP_POLICY)).thenReturn(true);
        when(cleanupPolicyAddonMock.getCleanupPackageStats()).thenReturn(createStats());
        var featureGroup = feature.getFeature();
        assertNotNull(featureGroup);
        assertNull(featureGroup.getFeatures());
        assertEquals(10L, featureGroup.getAttributes().get(TOTAL_CLEANUP_PACKAGES_POLICIES));
        assertEquals(5L, featureGroup.getAttributes().get(LAST_DAY_RUNS));
        assertEquals(10_000L, featureGroup.getAttributes().get(LAST_DAY_CLEANED_PACKAGES));
        assertEquals(50_000L, featureGroup.getAttributes().get(LAST_DAY_CLEANED_ARTIFACTS));
        assertEquals(4L, featureGroup.getAttributes().get(LAST_DAY_PACKAGES_FAILED));
        assertEquals(1_000_000_000L, featureGroup.getAttributes().get(LAST_DAY_CLEANED_PACKAGES_VIRTUAL_SIZE));
        assertEquals(200_000_000L, featureGroup.getAttributes().get(LAST_DAY_CLEANED_PACKAGES_PHYSICAL_SIZE));
    }

    @Test
    void testGetFeatureWhenDisabled() {
        getBound().setProperty(ConstantValues.policyCleanupPackageEnabled, "false");
        assertNull(feature.getFeature());
    }

    @Test
    void testGetFeatureWhenExceptionThrown() {
        when(addonsManagerMock.isEntitled(EntitlementType.CLEANUP_POLICY)).thenReturn(true);
        when(cleanupPolicyAddonMock.getCleanupPackageStats()).thenThrow(new RuntimeException("test"));
        assertNull(feature.getFeature());
    }

    @Test
    void testGetFeatureNotEntitled() {
        when(addonsManagerMock.isEntitled(EntitlementType.CLEANUP_POLICY)).thenReturn(false);
        assertNull(feature.getFeature());
    }

    private CleanupPackageStats createStats() {
        var stats = new CleanupPackageStats();
        stats.setPoliciesTotal(10);
        stats.setRunsTotal(5);
        stats.setPackagesCleaned(10_000);
        stats.setArtifactsCleaned(50_000);
        stats.setPackagesFailed(4);
        stats.setCleanedPackagesVirtualSize(1_000_000_000);
        stats.setCleanedPackagesPhysicalSize(200_000_000);
        return stats;
    }
}