package org.artifactory.addon;

import com.google.common.collect.ImmutableSet;
import org.artifactory.entitlement.EntitlementType;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Set;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * @author Shay Bagants
 */
@Test
public class OssAddonsManagerTest {

    private final Set<EntitlementType> supportedEntitlements =
            ImmutableSet.of(EntitlementType.OAUTH, EntitlementType.CROWD,
                    EntitlementType.SAML, EntitlementType.LDAP);

    @Test(dataProvider = "provideAddonsState")
    public void testIsAddonSupported(boolean isConan, boolean isJcr) {
        OssAddonsManager ossAddonsManagerSpy = spy(new OssAddonsManager());
        doReturn(isConan).when(ossAddonsManagerSpy).isConanCEVersion();
        doReturn(isJcr).when(ossAddonsManagerSpy).isJcrVersion();

        Arrays.stream(AddonType.values())
                .filter(addon -> filterExpectedSupportedAddons(isConan, isJcr, addon))
                .forEach(addonType -> {
                    System.out.println("Conan: " + isConan + " Jcr: " + isJcr + " Testing addon: " + addonType);
                    boolean addonSupported = ossAddonsManagerSpy.isAddonSupported(addonType);
                    assertTrue(addonSupported);
                });

        Arrays.stream(AddonType.values())
                .filter(addon -> !filterExpectedSupportedAddons(isConan, isJcr, addon))
                .forEach(addonType -> {
                    System.out.println("Conan: " + isConan + " Jcr: " + isJcr + " Testing addon: " + addonType);
                    boolean addonSupported = ossAddonsManagerSpy.isAddonSupported(addonType);
                    assertFalse(addonSupported);
                });
    }

    public void isEntitled() {
        OssAddonsManager ossAddonsManager = new OssAddonsManager();
        for (EntitlementType entitlementType : EntitlementType.values()) {
            assertEquals(ossAddonsManager.isEntitled(entitlementType), isSupportedEntitlement(entitlementType));
        }
    }

    public void isEntitledForced() {
        OssAddonsManager ossAddonsManager = new OssAddonsManager();
        for (EntitlementType entitlementType : EntitlementType.values()) {
            assertEquals(ossAddonsManager.isEntitledForced(entitlementType), isSupportedEntitlement(entitlementType));
        }
    }

    private boolean filterExpectedSupportedAddons(boolean isConan, boolean isJcr, AddonType addon) {
        if (isConan) {
            return addon == AddonType.CONAN;
        } else if (isJcr) {
            return addon == AddonType.DOCKER || addon == AddonType.PROPERTIES
                    || addon == AddonType.SMART_REPO || addon == AddonType.S3 || addon == AddonType.DISTRIBUTION
                    || addon == AddonType.AQL || addon == AddonType.AOL;
        }
        return false;
    }

    private boolean isSupportedEntitlement(EntitlementType entitlementType) {
        return supportedEntitlements.contains(entitlementType);
    }

    @DataProvider
    public static Object[][] provideAddonsState() {
        return new Object[][] {
                // isConan, isJcr
                {true, false},
                {false, true},
                {false, false}
        };
    }
}