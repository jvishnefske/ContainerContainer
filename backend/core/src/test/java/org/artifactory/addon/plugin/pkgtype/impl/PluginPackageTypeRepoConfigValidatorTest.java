/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.addon.plugin.pkgtype.impl;

import org.artifactory.addon.plugin.pkgtype.PluginPackageType;
import org.artifactory.config.ConfigurationException;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.interceptor.RepoConfigChanges;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.artifactory.addon.plugin.pkgtype.impl.PluginTypeTestRepos.config;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** R2: creates/updates naming an unregistered type fail with 400; R6: imports and kept names do not. */
class PluginPackageTypeRepoConfigValidatorTest {

    private final PluginPackageTypeRegistryImpl registry = new PluginPackageTypeRegistryImpl();
    private final PluginPackageTypeRepoConfigValidator validator = new PluginPackageTypeRepoConfigValidator(registry);

    private static RepoConfigChange change(RepoConfigChange.ConfigAction action, RepoConfig newConfig,
            RepoConfig oldConfig) {
        RepoConfigChange change = mock(RepoConfigChange.class);
        when(change.getConfigAction()).thenReturn(action);
        when(change.isCreateOrUpdate()).thenReturn(action != RepoConfigChange.ConfigAction.REPO_DELETE);
        when(change.getNewRepoConfig()).thenReturn(newConfig);
        when(change.getOldRepoConfig()).thenReturn(oldConfig);
        return change;
    }

    private static RepoConfigChanges changes(boolean isImport, RepoConfigChange change) {
        return new RepoConfigChanges(Map.of(RepoDetailsType.LOCAL, List.of(change)), isImport);
    }

    @Test
    void createWithUnknownTypeRejectedWith400NamingType() {
        RepoConfigChange create = change(RepoConfigChange.ConfigAction.REPO_CREATE,
                config("r", RepoType.Generic, "nosuch"), null);
        ConfigurationException e = assertThrows(ConfigurationException.class,
                () -> validator.validate(changes(false, create)));
        assertEquals(400, e.getStatusCode());
        assertTrue(e.getMessage().contains("'nosuch'"), e.getMessage());
    }

    @Test
    void createWithRegisteredTypeAccepted() {
        registry.replacePluginTypes("p", List.of(PluginPackageType.builder("simple", "p").build()));
        RepoConfigChange create = change(RepoConfigChange.ConfigAction.REPO_CREATE,
                config("r", RepoType.Generic, "simple"), null);
        assertDoesNotThrow(() -> validator.validate(changes(false, create)));
    }

    @Test
    void untypedAndNonGenericReposAccepted() {
        assertDoesNotThrow(() -> validator.validate(changes(false, change(RepoConfigChange.ConfigAction.REPO_CREATE,
                config("g", RepoType.Generic, null), null))));
        assertDoesNotThrow(() -> validator.validate(changes(false, change(RepoConfigChange.ConfigAction.REPO_CREATE,
                config("m", RepoType.Maven, "nosuch"), null))));
    }

    @Test
    void updateChangingToUnknownTypeRejected() {
        RepoConfigChange update = change(RepoConfigChange.ConfigAction.REPO_UPDATE,
                config("r", RepoType.Generic, "nosuch"), config("r", RepoType.Generic, null));
        assertThrows(ConfigurationException.class, () -> validator.validate(changes(false, update)));
    }

    @Test
    void updateKeepingTypeOfRemovedPluginAccepted() {
        RepoConfigChange update = change(RepoConfigChange.ConfigAction.REPO_UPDATE,
                config("r", RepoType.Generic, "gone"), config("r", RepoType.Generic, "gone"));
        assertDoesNotThrow(() -> validator.validate(changes(false, update)));
    }

    @Test
    void importOfRepoWithUnknownTypeDoesNotFail() {
        RepoConfigChange create = change(RepoConfigChange.ConfigAction.REPO_CREATE,
                config("r", RepoType.Generic, "gone"), null);
        assertDoesNotThrow(() -> validator.validate(changes(true, create)));
    }
}
