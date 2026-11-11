package org.artifactory.addon.plugin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;


class PluginsAddonDefaultImplTest {

    private final PluginsAddonDefaultImpl pluginsAddonDefault = new PluginsAddonDefaultImpl();

    @Test
    void getInstalledPluginsStatus_ThrowException() {
        assertThrows(UnsupportedOperationException.class, pluginsAddonDefault::getInstalledPluginsStatus);
    }

    @Test
    void deletePlugin_ThrowException() {
        assertThrows(UnsupportedOperationException.class,
                () -> pluginsAddonDefault.deletePlugin("pluginName"));
    }
}