/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.api.config;

import org.apache.commons.lang3.reflect.MethodUtils;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the import/export allowlist is also resolvable from {@code system.yaml}, with the
 * yaml-then-properties precedence used by {@code ArtifactoryPropertySource}.
 *
 * <p>This test does NOT extend {@link org.artifactory.test.ArtifactoryHomeBoundJUnitTest} because
 * the yaml fixture must be loaded by {@link ArtifactoryHome}'s constructor (it caches SysConfig at
 * construction time and does not re-read).
 */
class PathValidatorUtilYamlTest {

    private ArtifactoryHome previouslyBoundHome;

    @BeforeEach
    void bindHomeFromYamlFixture() throws Exception {
        // remember whatever was bound before (typically nothing) so we can restore in @AfterEach
        try {
            previouslyBoundHome = ArtifactoryHome.get();
        } catch (Throwable ignored) {
            previouslyBoundHome = null;
        }
        URL homePath = PathValidatorUtilYamlTest.class.getResource("/home");
        if (homePath == null) {
            throw new IllegalStateException("Cannot find the test resources directory");
        }
        ArtifactoryHome home = new ArtifactoryHome(homePath.getPath());
        MethodUtils.invokeMethod(home, true, "initArtifactorySystemPropertiesInternal");
        ArtifactoryHome.bind(home);
    }

    @AfterEach
    void restoreHome() {
        ArtifactoryHome.unbind();
        if (previouslyBoundHome != null) {
            ArtifactoryHome.bind(previouslyBoundHome);
        }
    }

    @Test
    void resolveAllowedPaths_readsImportAllowlistFromSystemYaml() {
        List<String> allowed = PathValidatorUtil.resolveAllowedPaths(
                PathValidatorUtil.YAML_KEY_IMPORT_ALLOWED_PATHS, ConstantValues.importAllowedPaths);
        assertEquals(List.of("/tmp/yaml-import/", "/mnt/yaml-backups/"), allowed);
    }

    @Test
    void resolveAllowedPaths_readsExportAllowlistFromSystemYaml() {
        List<String> allowed = PathValidatorUtil.resolveAllowedPaths(
                PathValidatorUtil.YAML_KEY_EXPORT_ALLOWED_PATHS, ConstantValues.exportAllowedPaths);
        assertEquals(List.of("/tmp/yaml-export/", "/mnt/yaml-backups/"), allowed);
    }

    @Test
    void isImportPathInAllowList_acceptsPathInsideYamlAllowedDir() {
        assertTrue(PathValidatorUtil.isImportPathInAllowList("/tmp/yaml-import/job-1"));
        assertTrue(PathValidatorUtil.isImportPathInAllowList("/mnt/yaml-backups/snapshot"));
    }

    @Test
    void isImportPathInAllowList_rejectsPathOutsideYamlAllowedDir() {
        assertFalse(PathValidatorUtil.isImportPathInAllowList("/etc/passwd"));
        assertFalse(PathValidatorUtil.isImportPathInAllowList("/tmp/other"));
    }

    @Test
    void isExportPathInAllowList_acceptsPathInsideYamlAllowedDir() {
        assertTrue(PathValidatorUtil.isExportPathInAllowList("/tmp/yaml-export/job-1"));
    }

    @Test
    void isExportPathInAllowList_rejectsPathOutsideYamlAllowedDir() {
        assertFalse(PathValidatorUtil.isExportPathInAllowList("/etc/output"));
    }

    @Test
    void resolveAllowedPaths_readsBackupAllowlistFromSystemYaml() {
        List<String> allowed = PathValidatorUtil.resolveAllowedPaths(
                PathValidatorUtil.YAML_KEY_BACKUP_ALLOWED_PATHS, ConstantValues.backupAllowedPaths);
        assertEquals(List.of("/tmp/yaml-backup/", "/mnt/yaml-backups/"), allowed);
    }

    @Test
    void isBackupPathInAllowList_acceptsPathInsideYamlAllowedDir() {
        assertTrue(PathValidatorUtil.isBackupPathInAllowList("/tmp/yaml-backup/job-1"));
        assertTrue(PathValidatorUtil.isBackupPathInAllowList("/mnt/yaml-backups/snapshot"));
    }

    @Test
    void isBackupPathInAllowList_rejectsPathOutsideYamlAllowedDir() {
        assertFalse(PathValidatorUtil.isBackupPathInAllowList("/etc/passwd"));
        assertFalse(PathValidatorUtil.isBackupPathInAllowList("/tmp/yaml-export/something"));
    }

    @Test
    void resolveAllowedPaths_yamlBeatsConstantValues_whenBothSet() {
        // ConstantValues is set but yaml is also set -> yaml wins.
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.importAllowedPaths.getPropertyName(), "/props-only/");
        List<String> allowed = PathValidatorUtil.resolveAllowedPaths(
                PathValidatorUtil.YAML_KEY_IMPORT_ALLOWED_PATHS, ConstantValues.importAllowedPaths);
        assertEquals(List.of("/tmp/yaml-import/", "/mnt/yaml-backups/"), allowed);
    }

    @Test
    void resolveAllowedPaths_yamlBeatsConstantValues_forBackup() {
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.backupAllowedPaths.getPropertyName(), "/props-only/");
        List<String> allowed = PathValidatorUtil.resolveAllowedPaths(
                PathValidatorUtil.YAML_KEY_BACKUP_ALLOWED_PATHS, ConstantValues.backupAllowedPaths);
        assertEquals(List.of("/tmp/yaml-backup/", "/mnt/yaml-backups/"), allowed);
    }
}
