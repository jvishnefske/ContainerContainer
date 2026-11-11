package org.artifactory.addon.rest;

import org.artifactory.rest.exception.MissingRestAddonException;
import org.jfrog.common.StringList;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

class RestAddonDefaultImplTest {

    private final RestAddonDefaultImpl restAddonDefault = new RestAddonDefaultImpl();

    @Test
    void hasRepositoriesByProjectAndTypes_ThrowException() {
        assertThrows(MissingRestAddonException.class,
                () -> restAddonDefault.hasRepositoriesByProjectAndTypes("project", null));
    }

    @Test
    void getAllRepositoryConfigurationsThrowException() {
        assertThrows(MissingRestAddonException.class,
                () -> restAddonDefault.getAllRepositoryConfigurations(new StringList("maven"), null));
    }

    @Test
    void updateRepositories_ThrowException() {
        assertThrows(MissingRestAddonException.class,
                () -> restAddonDefault.updateRepositories(List.of(), null));
    }

    @Test
    void getPluginStatus_ThrowException() {
        assertThrows(MissingRestAddonException.class,
                restAddonDefault::getPluginStatus);
    }

    @Test
    void deletePlugin_ThrowException() {
        assertThrows(MissingRestAddonException.class,
                () -> restAddonDefault.deletePlugin("pluginName"));
    }
}