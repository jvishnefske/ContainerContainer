package org.artifactory.descriptor.repo;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Shashank Taliwal
 */
class RpmGeneralConfigTest {

    private RpmGeneralConfig config;

    @BeforeEach
    void setUp() {
        config = new RpmGeneralConfig();
    }

    @Test
    void testDefaultInitialization() {
        assertNotNull(config.getEnableRecommendTags(), "enableRecommendTags should be initialized by default");
        assertNotNull(config.getEnableSha256(), "enableSha256 should be initialized by default");
    }

    @Test
    void testDefaultNestedConfigValues() {
        RpmEnableRecommendTagsConfig recommendTags = config.getEnableRecommendTags();
        assertTrue(recommendTags.isVisibleOnUi(), "Default recommend tags visibleOnUi should be true");
        assertFalse(recommendTags.isEnabled(), "Default recommend tags enabled should be false");
        
        RpmEnableSha256Config sha256 = config.getEnableSha256();
        assertTrue(sha256.isVisibleOnUi(), "Default SHA256 visibleOnUi should be true");
        assertFalse(sha256.isEnabled(), "Default SHA256 enabled should be false");
    }

    @Test
    void testSettersAndGetters() {
        RpmEnableRecommendTagsConfig customRecommendTags = new RpmEnableRecommendTagsConfig(false,
                true);
        RpmEnableSha256Config customSha256 = new RpmEnableSha256Config(false, true);

        config.setEnableRecommendTags(customRecommendTags);
        config.setEnableSha256(customSha256);

        assertEquals(customRecommendTags, config.getEnableRecommendTags(), 
                "enableRecommendTags should be set correctly");
        assertEquals(customSha256, config.getEnableSha256(), 
                "enableSha256 should be set correctly");

        assertFalse(config.getEnableRecommendTags().isVisibleOnUi(), 
                "Custom recommend tags visibleOnUi should be false");
        assertTrue(config.getEnableRecommendTags().isEnabled(), 
                "Custom recommend tags enabled should be true");
        assertFalse(config.getEnableSha256().isVisibleOnUi(), 
                "Custom SHA256 visibleOnUi should be false");
        assertTrue(config.getEnableSha256().isEnabled(), 
                "Custom SHA256 enabled should be true");
    }

    @Test
    void testNestedConfigIndependence() {
        config.getEnableRecommendTags().setEnabled(true);
        config.getEnableRecommendTags().setVisibleOnUi(false);
        

        assertTrue(config.getEnableRecommendTags().isEnabled(), "recommend tags enabled should be true");
        assertFalse(config.getEnableRecommendTags().isVisibleOnUi(), "recommend tags visibleOnUi should be false");
        

        assertTrue(config.getEnableSha256().isVisibleOnUi(), "SHA256 visibleOnUi should remain default");
        assertFalse(config.getEnableSha256().isEnabled(), "SHA256 enabled should remain default");
        

        config.getEnableSha256().setEnabled(true);
        config.getEnableSha256().setVisibleOnUi(false);

        assertTrue(config.getEnableSha256().isEnabled(), "SHA256 enabled should be true");
        assertFalse(config.getEnableSha256().isVisibleOnUi(), "SHA256 visibleOnUi should be false");

        assertTrue(config.getEnableRecommendTags().isEnabled(), "recommend tags enabled should still be true");
        assertFalse(config.getEnableRecommendTags().isVisibleOnUi(),
                "recommend tags visibleOnUi should still be false");
    }

    @Test
    void testEqualsWithSameObject() {
        assertEquals(config, config, "Object should equal itself");
    }

    @ParameterizedTest(name = "expectedEqual={8}")
    @CsvSource({
            // recEn1, recVis1, shaEn1, shaVis1, recEn2, recVis2, shaEn2, shaVis2, expectedEqual
            "false,true,false,true,false,true,false,true,true",   // both default
            "true,true,false,true,false,true,false,true,false",  // diff recommend enabled
            "true,true,false,true,true,true,false,true,true",    // same recommend enabled
            "false,true,true,true,false,true,false,true,false",  // diff sha enabled
            "false,true,true,true,false,true,true,true,true"     // same sha enabled
    })
    void testEqualsParameterized(boolean recEn1, boolean recVis1, boolean shaEn1, boolean shaVis1,
                                 boolean recEn2, boolean recVis2, boolean shaEn2, boolean shaVis2,
                                 boolean expectedEqual) {
        RpmGeneralConfig cfg1 = new RpmGeneralConfig();
        cfg1.getEnableRecommendTags().setEnabled(recEn1);
        cfg1.getEnableRecommendTags().setVisibleOnUi(recVis1);
        cfg1.getEnableSha256().setEnabled(shaEn1);
        cfg1.getEnableSha256().setVisibleOnUi(shaVis1);

        RpmGeneralConfig cfg2 = new RpmGeneralConfig();
        cfg2.getEnableRecommendTags().setEnabled(recEn2);
        cfg2.getEnableRecommendTags().setVisibleOnUi(recVis2);
        cfg2.getEnableSha256().setEnabled(shaEn2);
        cfg2.getEnableSha256().setVisibleOnUi(shaVis2);

        if (expectedEqual) {
            assertEquals(cfg1, cfg2);
            assertEquals(cfg2, cfg1);
        } else {
            assertNotEquals(cfg1, cfg2);
            assertNotEquals(cfg2, cfg1);
        }
    }

    @Test
    void testEqualsWithNullNestedConfigs() {
        RpmGeneralConfig config1 = new RpmGeneralConfig();
        RpmGeneralConfig config2 = new RpmGeneralConfig();
        

        config1.setEnableRecommendTags(null);
        
        assertNotEquals(config1, config2, "Config with null nested config should not equal normal config");
        

        config2.setEnableRecommendTags(null);
        assertEquals(config1, config2, "Configs with same null nested configs should be equal");

        config1.setEnableSha256(null);
        assertNotEquals(config1, config2, "Config with additional null nested config should not be equal");
        
        config2.setEnableSha256(null);
        assertEquals(config1, config2, "Configs with all same null nested configs should be equal");
    }

    @Test
    void testEqualsWithNull() {
        assertNotEquals(config, null, "Config should not equal null");
    }

    @Test
    void testEqualsWithDifferentClass() {
        String notAConfig = "Not a config";
        assertNotEquals(config, notAConfig, "Config should not equal different class instance");
    }

    @Test
    void testEqualsComplexScenario() {
        RpmGeneralConfig config1 = new RpmGeneralConfig();
        RpmGeneralConfig config2 = new RpmGeneralConfig();
        

        config1.getEnableRecommendTags().setEnabled(true);
        config1.getEnableRecommendTags().setVisibleOnUi(false);
        config1.getEnableSha256().setEnabled(false);
        config1.getEnableSha256().setVisibleOnUi(true);
        
        config2.getEnableRecommendTags().setEnabled(true);
        config2.getEnableRecommendTags().setVisibleOnUi(false);
        config2.getEnableSha256().setEnabled(false);
        config2.getEnableSha256().setVisibleOnUi(true);
        
        assertEquals(config1, config2, "Complex equal configs should be equal");
        

        config2.getEnableSha256().setEnabled(true);
        assertNotEquals(config1, config2, "Small change should make configs unequal");
    }

    @Test
    void testEqualsTransitivity() {
        RpmGeneralConfig config1 = new RpmGeneralConfig();
        RpmGeneralConfig config2 = new RpmGeneralConfig();
        RpmGeneralConfig config3 = new RpmGeneralConfig();
        

        for (RpmGeneralConfig config : new RpmGeneralConfig[]{config1, config2, config3}) {
            config.getEnableRecommendTags().setEnabled(true);
            config.getEnableSha256().setVisibleOnUi(false);
        }
        
        assertEquals(config1, config2, "config1 should equal config2");
        assertEquals(config2, config3, "config2 should equal config3");
        assertEquals(config1, config3, "config1 should equal config3 (transitivity)");
    }

    @Test
    void testObjectReferences() {

        RpmEnableRecommendTagsConfig originalRecommendTags = config.getEnableRecommendTags();
        RpmEnableSha256Config originalSha256 = config.getEnableSha256();
        

        assertSame(originalRecommendTags, config.getEnableRecommendTags(), 
                "Multiple calls should return same recommend tags reference");
        assertSame(originalSha256, config.getEnableSha256(), 
                "Multiple calls should return same SHA256 reference");
        

        RpmEnableRecommendTagsConfig newRecommendTags = new RpmEnableRecommendTagsConfig();
        RpmEnableSha256Config newSha256 = new RpmEnableSha256Config();
        
        config.setEnableRecommendTags(newRecommendTags);
        config.setEnableSha256(newSha256);
        

        assertSame(newRecommendTags, config.getEnableRecommendTags(), 
                "Should get new recommend tags reference after setting");
        assertSame(newSha256, config.getEnableSha256(), 
                "Should get new SHA256 reference after setting");
        

        assertNotSame(originalRecommendTags, config.getEnableRecommendTags(), 
                "Should not get original recommend tags reference");
        assertNotSame(originalSha256, config.getEnableSha256(), 
                "Should not get original SHA256 reference");
    }

    @Test
    void testDescriptorImplementation() {
        assertTrue(config instanceof org.artifactory.descriptor.Descriptor, 
                "RpmGeneralConfig should implement Descriptor interface");
    }

    @Test
    void testCustomEqualsImplementation() {

        RpmGeneralConfig config1 = new RpmGeneralConfig();
        RpmGeneralConfig config2 = new RpmGeneralConfig();
        

        assertEquals(config1, config2, "Default configs should be equal");
        

        config1.getEnableRecommendTags().setEnabled(true);
        assertNotEquals(config1, config2, "Modified config should not equal default");
        

        config2.getEnableRecommendTags().setEnabled(true);
        assertEquals(config1, config2, "Configs with same modifications should be equal");

        config1.getEnableSha256().setVisibleOnUi(false);
        assertNotEquals(config1, config2, "Different SHA256 config should make configs unequal");
        
        config2.getEnableSha256().setVisibleOnUi(false);
        assertEquals(config1, config2, "Same SHA256 modifications should make configs equal again");
    }



    @ParameterizedTest(name = "shaEnabled={0}, recEnabled={1}, sameHash={2}")
    @CsvSource({
            // shaEnabled1 recEnabled1 shaEnabled2 recEnabled2 expectSame
            "true,true,true,true,true",
            "false,false,false,false,true",
            "true,false,true,false,true",
            "false,true,false,true,true",
            "true,false,false,true,false",
            "true,true,false,true,false",
            "true,true,true,false,false"
    })
    void testHashCodeEquality(boolean sha1, boolean rec1, boolean sha2, boolean rec2, boolean expectSame) {
        RpmGeneralConfig cfg1 = createConfig(sha1, rec1);
        RpmGeneralConfig cfg2 = createConfig(sha2, rec2);
        if (expectSame) {
            Assertions.assertEquals(cfg1.hashCode(), cfg2.hashCode(),
                    "HashCodes should match for equal top-level configs");
        } else {
            Assertions.assertNotEquals(cfg1.hashCode(), cfg2.hashCode(),
                    "HashCodes should differ for non-equal configs");
        }
    }

    @ParameterizedTest(name = "toggle field {0}")
    @CsvSource({"sha","rec"})
    void testHashCodeChangesWhenNestedFieldChanges(String field) {
        RpmGeneralConfig cfg = createConfig(false, false);
        int initial = cfg.hashCode();
        if ("sha".equals(field)) {
            cfg.getEnableSha256().setEnabled(true);
        } else {
            cfg.getEnableRecommendTags().setEnabled(true);
        }
        Assertions.assertNotEquals(initial, cfg.hashCode(),
                "Hash code must change when nested config changes");
    }

    @ParameterizedTest(name = "tag={0}, setToNull={1}")
    @CsvSource({
            // tag, setToNull
            "rec,false",
            "sha,false",
            "rec,true",
            "sha,true"
    })
    void testInequalityWhenSingleTagDiffers(String tag, boolean setToNull) {
        RpmGeneralConfig cfg1 = new RpmGeneralConfig();
        RpmGeneralConfig cfg2 = new RpmGeneralConfig();

        // Modify one tag in cfg1 according to parameters
        if (setToNull) {
            if ("rec".equals(tag)) {
                cfg1.setEnableRecommendTags(null);
            } else {
                cfg1.setEnableSha256(null);
            }
        } else {
            if ("rec".equals(tag)) {
                cfg1.getEnableRecommendTags().setEnabled(true);
            } else {
                cfg1.getEnableSha256().setEnabled(true);
            }
        }

        // The two configs should now be different
        assertNotEquals(cfg1, cfg2, "Configs should be unequal when one tag differs or is null");
        assertNotEquals(cfg2, cfg1, "Inequality should be symmetric");
    }

    private RpmGeneralConfig createConfig(boolean shaEnabled, boolean recEnabled) {
        RpmGeneralConfig cfg = new RpmGeneralConfig();
        cfg.setEnableSha256(new RpmEnableSha256Config(true, shaEnabled));
        cfg.setEnableRecommendTags(new RpmEnableRecommendTagsConfig(true, recEnabled));
        return cfg;
    }
} 