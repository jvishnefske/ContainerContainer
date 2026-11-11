package org.artifactory.descriptor.repo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Shashank Taliwal
 */
class RpmEnableRecommendTagsConfigTest {

    @Test
    void testDefaultConstructor() {
        RpmEnableRecommendTagsConfig config = new RpmEnableRecommendTagsConfig();
        assertTrue(config.isVisibleOnUi(), "Default visibleOnUi should be true");
        assertFalse(config.isEnabled(), "Default enabled should be false");
    }

    @Test
    void testParameterizedConstructor() {
        RpmEnableRecommendTagsConfig config = new RpmEnableRecommendTagsConfig(false, true);
        assertFalse(config.isVisibleOnUi(), "visibleOnUi should be false as set");
        assertTrue(config.isEnabled(), "enabled should be true as set");
    }

    @ParameterizedTest(name = "visibleOnUi={0}, enabled={1}")
    @CsvSource({
            "true, true",
            "true, false",
            "false, true",
            "false, false"
    })
    void testParameterizedConstructorCombinations(boolean visibleOnUi, boolean enabled) {
        RpmEnableRecommendTagsConfig config = new RpmEnableRecommendTagsConfig(visibleOnUi, enabled);
        assertEquals(visibleOnUi, config.isVisibleOnUi(), "visibleOnUi should match constructor arg");
        assertEquals(enabled, config.isEnabled(), "enabled should match constructor arg");
    }

    @Test
    void testSettersAndGetters() {
        RpmEnableRecommendTagsConfig config = new RpmEnableRecommendTagsConfig();
        config.setVisibleOnUi(false);
        assertFalse(config.isVisibleOnUi(), "visibleOnUi should be false after setting");

        config.setVisibleOnUi(true);
        assertTrue(config.isVisibleOnUi(), "visibleOnUi should be true after setting");


        config.setEnabled(true);
        assertTrue(config.isEnabled(), "enabled should be true after setting");

        config.setEnabled(false);
        assertFalse(config.isEnabled(), "enabled should be false after setting");
    }

    @Test
    void testEqualsWithSameObject() {
        RpmEnableRecommendTagsConfig config = new RpmEnableRecommendTagsConfig();
        assertEquals(config, config, "Object should equal itself");
    }

    @ParameterizedTest(name = "expectedEqual={4} for cfg1(vis={0},en={1}) vs cfg2(vis={2},en={3})")
    @CsvSource({
            // visible1, enabled1, visible2, enabled2, expectedEqual
            "true,false,true,false,true",
            "false,true,false,true,true",
            "false,false,false,false,true",
            "true,true,true,true,true",
            "true,false,true,true,false",
            "true,false,false,false,false",
            "true,false,false,true,false"
    })
    void testEqualsParameterized(boolean vis1, boolean en1, boolean vis2, boolean en2, boolean expectedEqual) {
        RpmEnableRecommendTagsConfig cfg1 = new RpmEnableRecommendTagsConfig(vis1, en1);
        RpmEnableRecommendTagsConfig cfg2 = new RpmEnableRecommendTagsConfig(vis2, en2);

        if (expectedEqual) {
            assertEquals(cfg1, cfg2);
            assertEquals(cfg2, cfg1);
        } else {
            assertNotEquals(cfg1, cfg2);
            assertNotEquals(cfg2, cfg1);
        }
    }

    @Test
    void testEqualsWithNull() {
        RpmEnableRecommendTagsConfig config = new RpmEnableRecommendTagsConfig();
        assertNotEquals(config, null, "Object should not equal null");
    }

    @Test
    void testEqualsWithDifferentClass() {
        RpmEnableRecommendTagsConfig config = new RpmEnableRecommendTagsConfig();
        String notAConfig = "Not a config";
        assertNotEquals(config, notAConfig, "Object should not equal different class instance");
    }

    @Test
    void testEqualsWithDifferentConfigType() {
        RpmEnableRecommendTagsConfig recommendConfig = new RpmEnableRecommendTagsConfig(true, false);
        RpmEnableSha256Config sha256Config = new RpmEnableSha256Config(true, false);

        assertNotEquals(recommendConfig, sha256Config, "Different config types should not be equal");
    }

    @Test
    void testEqualsAfterModification() {
        RpmEnableRecommendTagsConfig config1 = new RpmEnableRecommendTagsConfig();
        RpmEnableRecommendTagsConfig config2 = new RpmEnableRecommendTagsConfig();

        assertEquals(config1, config2, "Default configs should be equal");


        config1.setEnabled(true);
        assertNotEquals(config1, config2, "Configs should not be equal after modification");


        config2.setEnabled(true);
        assertEquals(config1, config2, "Configs should be equal after same modification");


        config1.setVisibleOnUi(false);
        assertNotEquals(config1, config2, "Configs should not be equal after UI visibility change");

        config2.setVisibleOnUi(false);
        assertEquals(config1, config2, "Configs should be equal after same UI visibility change");
    }

    @Test
    void testEqualsTransitivity() {
        RpmEnableRecommendTagsConfig config1 = new RpmEnableRecommendTagsConfig(false, true);
        RpmEnableRecommendTagsConfig config2 = new RpmEnableRecommendTagsConfig(false, true);
        RpmEnableRecommendTagsConfig config3 = new RpmEnableRecommendTagsConfig(false, true);

        assertEquals(config1, config2, "config1 should equal config2");
        assertEquals(config2, config3, "config2 should equal config3");
        assertEquals(config1, config3, "config1 should equal config3 (transitivity)");
    }

    @Test
    void testIndependentInstances() {
        RpmEnableRecommendTagsConfig config1 = new RpmEnableRecommendTagsConfig();
        RpmEnableRecommendTagsConfig config2 = new RpmEnableRecommendTagsConfig();

        config1.setEnabled(true);
        config1.setVisibleOnUi(false);

        assertFalse(config2.isEnabled(), "config2 enabled should remain default");
        assertTrue(config2.isVisibleOnUi(), "config2 visibleOnUi should remain default");
    }

    @Test
    void testRecommendTagsSpecificScenarios() {
        RpmEnableRecommendTagsConfig config = new RpmEnableRecommendTagsConfig();

        assertFalse(config.isEnabled(),
                "Recommend tags should be disabled by default for backward compatibility");

        assertTrue(config.isVisibleOnUi(), "Recommend tags config should be visible in UI by default");

        config.setEnabled(true);
        assertTrue(config.isEnabled(), "Recommend tags should be enabled when explicitly set");

        config.setVisibleOnUi(false);
        assertFalse(config.isVisibleOnUi(), "Recommend tags config should be hideable from UI");
    }

    @ParameterizedTest(name = "cfg1(vis={0},en={1}) hashCode() vs cfg2(vis={2},en={3}) hashCode() should be {4}")
    @CsvSource({
            // visible1, enabled1, visible2, enabled2, sameHash
            "true,false,true,false,true",
            "false,true,false,true,true",
            "false,false,false,false,true",
            "true,true,true,true,true",
            "true,false,true,true,false",
            "true,false,false,false,false",
            "true,false,false,true,false"
    })
    void testHashCodeParameterized(boolean vis1, boolean en1, boolean vis2, boolean en2, boolean sameHash) {
        RpmEnableRecommendTagsConfig cfg1 = new RpmEnableRecommendTagsConfig(vis1, en1);
        RpmEnableRecommendTagsConfig cfg2 = new RpmEnableRecommendTagsConfig(vis2, en2);

        if (sameHash) {
            assertEquals(cfg1.hashCode(), cfg2.hashCode(), "HashCodes should match for equal objects");
        } else {
            assertNotEquals(cfg1.hashCode(), cfg2.hashCode(), "HashCodes should not match for unequal objects");
        }
    }

    @Test
    void testHashCodeConsistency() {
        RpmEnableRecommendTagsConfig config = new RpmEnableRecommendTagsConfig(true, false);
        int initialHashCode = config.hashCode();

        config.setEnabled(true);
        int finalHashCode = config.hashCode();
        assertNotEquals(initialHashCode, finalHashCode, "HashCode should change after modifying enabled");

        config.setVisibleOnUi(false);

        int changedHashCode = config.hashCode();
        assertNotEquals(initialHashCode, changedHashCode, "HashCode should change after modifying visibleOnUi");
    }


    @Test
    void testHashCodeIndependence() {
        RpmEnableRecommendTagsConfig config1 = new RpmEnableRecommendTagsConfig();
        RpmEnableRecommendTagsConfig config2 = new RpmEnableRecommendTagsConfig();

        config1.setEnabled(true);
        config1.setVisibleOnUi(false);

        assertNotEquals(config1.hashCode(), config2.hashCode(), "HashCodes should differ for independent objects");
    }
}