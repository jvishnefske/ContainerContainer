package org.artifactory.metrics.providers.features;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.plugin.PluginsAddon;
import org.artifactory.api.callhome.FeatureGroup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import org.springframework.test.util.ReflectionTestUtils;

public class PluginsFeatureTest {

    @Mock
    private AddonsManager addonsManager;

    @Mock
    private PluginsAddon pluginsAddon;

    private PluginsFeature pluginsFeature;

    private FeatureGroup featureGroup;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.initMocks(this);
        when(addonsManager.addonByType(PluginsAddon.class)).thenReturn(pluginsAddon);
        pluginsFeature = new PluginsFeature();
        ReflectionTestUtils.setField(pluginsFeature, "addonsManager", addonsManager);
        getFeatureGroupForPlugin(false);
    }

    @Test
    void testGetFeatureForEmptyPluginsDir() {
        getFeatureGroupForPlugin(true);
        assertEquals(0, featureGroup.getAttributes().get("number_of_plugins"));
        assertEquals(0, featureGroup.getAttributes().get("number_of_compliant_plugins"));
        assertEquals(0, featureGroup.getAttributes().get("number_of_non_compliant_plugins"));
        assertEquals(0, featureGroup.getAttributes().get("number_of_plugin_events_usage"));
    }

    @Test
    void testGetFeatureForNonEmptyPluginsDir() {
        assertEquals(2, featureGroup.getAttributes().get("number_of_plugins"));
        assertEquals(1, featureGroup.getAttributes().get("number_of_compliant_plugins"));
        assertEquals(1, featureGroup.getAttributes().get("number_of_non_compliant_plugins"));
        assertEquals(2, featureGroup.getAttributes().get("number_of_plugin_events_usage"));
    }

    @Test
    void testAddPluginsComplianceStatus() {
        // Verify that the FeatureGroup has the expected attributes
        assertEquals(1, featureGroup.getAttributes().get("number_of_compliant_plugins"));
        assertEquals(1, featureGroup.getAttributes().get("number_of_non_compliant_plugins"));

        // Verify that the FeatureGroup has the expected sub-features
        List<FeatureGroup> subFeatures = featureGroup.getFeatures();
        for (FeatureGroup subFeature : subFeatures) {
            if ("plugin1".equals(subFeature.toString())) {
                assertEquals(PluginsAddon.PLUGIN_COMPLIANT, subFeature.getAttributes().get("plugin_compliance_status"));
            } else if ("plugin2".equals(subFeature.toString())) {
                assertEquals(PluginsAddon.PLUGIN_NON_COMPLIANT, subFeature.getAttributes()
                        .get("plugin_compliance_status"));
            }
        }
    }

    @Test
    void testAddPluginsUsageStatus() {
        // Verify that the FeatureGroup has the expected attributes
        assertEquals(2, featureGroup.getAttributes().get("number_of_plugin_events_usage"));

        // Verify that the FeatureGroup has the expected sub-features
        List<FeatureGroup> subFeatures = featureGroup.getFeatures();
        for (FeatureGroup subFeature : subFeatures) {
            if ("storage.BeforeCreateAction".equals(subFeature.toString())) {
                assertEquals(1L, subFeature.getAttributes().get("storage.BeforeCreateAction"));
            } else if ("download.BeforeDownload".equals(subFeature.toString())) {
                assertEquals(2L, subFeature.getAttributes().get("download.BeforeDownload"));
            }
        }
    }

    // Helper method to get the FeatureGroup for the plugins
    private void getFeatureGroupForPlugin(boolean emptyPlugins) {
        Map<String, String> plugins = new HashMap<>();
        Map<String, String> pluginsCompliance = new HashMap<>();
        Map<String, Long> pluginUsageStatus = new HashMap<>();
        if (!emptyPlugins) {
            plugins.put("plugin1", "active");
            plugins.put("plugin2", "inactive");
            pluginsCompliance.put("plugin1", PluginsAddon.PLUGIN_COMPLIANT);
            pluginsCompliance.put("plugin2", PluginsAddon.PLUGIN_NON_COMPLIANT);
            pluginUsageStatus.put("storage.BeforeCreateAction", 1L);
            pluginUsageStatus.put("download.BeforeDownload", 2L);
        }
        when(pluginsAddon.getPluginsStatus()).thenReturn(plugins);
        when(pluginsAddon.getPluginsComplianceStatus()).thenReturn(pluginsCompliance);
        when(pluginsAddon.getPluginsUsageStatus()).thenReturn(pluginUsageStatus);
        featureGroup = pluginsFeature.getFeature();
    }
}
