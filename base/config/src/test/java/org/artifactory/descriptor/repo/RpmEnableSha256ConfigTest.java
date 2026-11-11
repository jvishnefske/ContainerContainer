package org.artifactory.descriptor.repo;

import org.junit.jupiter.api.Assertions;
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
class RpmEnableSha256ConfigTest {

    @Test
    void testDefaultConstructor() {
        RpmEnableSha256Config config = new RpmEnableSha256Config();
        assertTrue(config.isVisibleOnUi(), "Default visibleOnUi should be true");
        assertFalse(config.isEnabled(), "Default enabled should be false");
    }

    @Test
    void testParameterizedConstructor() {
        RpmEnableSha256Config config = new RpmEnableSha256Config(false, true);
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
        RpmEnableSha256Config config = new RpmEnableSha256Config(visibleOnUi, enabled);
        assertEquals(visibleOnUi, config.isVisibleOnUi(), "visibleOnUi should match constructor arg");
        assertEquals(enabled, config.isEnabled(), "enabled should match constructor arg");
    }

    @Test
    void testSettersAndGetters() {
        RpmEnableSha256Config config = new RpmEnableSha256Config();
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
        RpmEnableSha256Config config = new RpmEnableSha256Config();
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
        RpmEnableSha256Config cfg1 = new RpmEnableSha256Config(vis1, en1);
        RpmEnableSha256Config cfg2 = new RpmEnableSha256Config(vis2, en2);

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
        RpmEnableSha256Config config = new RpmEnableSha256Config();
        assertNotEquals(config, null, "Object should not equal null");
    }

    @Test
    void testEqualsWithDifferentClass() {
        RpmEnableSha256Config config = new RpmEnableSha256Config();
        String notAConfig = "Not a config";
        assertNotEquals(config, notAConfig, "Object should not equal different class instance");
    }

    @Test
    void testEqualsWithDifferentConfigType() {
        RpmEnableSha256Config sha256Config = new RpmEnableSha256Config(true, false);
        RpmEnableRecommendTagsConfig recommendConfig = new RpmEnableRecommendTagsConfig(true, false);

        assertNotEquals(sha256Config, recommendConfig, "Different config types should not be equal");
    }

    @Test
    void testEqualsAfterModification() {
        RpmEnableSha256Config config1 = new RpmEnableSha256Config();
        RpmEnableSha256Config config2 = new RpmEnableSha256Config();


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
        RpmEnableSha256Config config1 = new RpmEnableSha256Config(false, true);
        RpmEnableSha256Config config2 = new RpmEnableSha256Config(false, true);
        RpmEnableSha256Config config3 = new RpmEnableSha256Config(false, true);

        assertEquals(config1, config2, "config1 should equal config2");
        assertEquals(config2, config3, "config2 should equal config3");
        assertEquals(config1, config3, "config1 should equal config3 (transitivity)");
    }


    @Test
    void testIndependentInstances() {
        RpmEnableSha256Config config1 = new RpmEnableSha256Config();
        RpmEnableSha256Config config2 = new RpmEnableSha256Config();


        config1.setEnabled(true);
        config1.setVisibleOnUi(false);


        assertFalse(config2.isEnabled(), "config2 enabled should remain default");
        assertTrue(config2.isVisibleOnUi(), "config2 visibleOnUi should remain default");
    }

    @Test
    void testSha256SpecificScenarios() {

        RpmEnableSha256Config config = new RpmEnableSha256Config();


        assertFalse(config.isEnabled(), "SHA256 should be disabled by default for backward compatibility");


        assertTrue(config.isVisibleOnUi(), "SHA256 config should be visible in UI by default");


        config.setEnabled(true);
        assertTrue(config.isEnabled(), "SHA256 should be enabled when explicitly set");


        config.setVisibleOnUi(false);
        assertFalse(config.isVisibleOnUi(), "SHA256 config should be hideable from UI");
    }

    @ParameterizedTest(name = "cfg1(vis={0},en={1}) vs cfg2(vis={2},en={3}) => sameHash={4}")
    @CsvSource({
            // visible1, enabled1, visible2, enabled2, expectSameHash
            "true,false,true,false,true",
            "false,true,false,true,true",
            "false,false,false,false,true",
            "true,true,true,true,true",
            "true,false,true,true,false",
            "true,false,false,false,false",
            "true,false,false,true,false"
    })
    void testHashCodeEquality(boolean vis1, boolean en1, boolean vis2, boolean en2, boolean expectSame) {
        RpmEnableSha256Config c1 = new RpmEnableSha256Config(vis1, en1);
        RpmEnableSha256Config c2 = new RpmEnableSha256Config(vis2, en2);
        if (expectSame) {
            Assertions.assertEquals(c1.hashCode(), c2.hashCode(),
                    "HashCodes should match for logically equal objects");
        } else {
            Assertions.assertNotEquals(c1.hashCode(), c2.hashCode(),
                    "HashCodes should differ for non-equal objects");
        }
    }

    @Test
    void testHashCodeConsistency() {
        RpmEnableSha256Config cfg = new RpmEnableSha256Config(true, false);
        int initial = cfg.hashCode();
        cfg.setEnabled(true);
        Assertions.assertNotEquals(initial, cfg.hashCode(), "HashCode must change after state change (enabled)" );

        int afterEnabledChange = cfg.hashCode();
        cfg.setVisibleOnUi(false);
        Assertions.assertNotEquals(afterEnabledChange, cfg.hashCode(),
                "HashCode must change after state change (visibleOnUi)");
    }
} 