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

package org.artifactory.ui.rest.service.admin.services.backups;

import lombok.experimental.UtilityClass;
import org.artifactory.api.config.PathValidatorUtil;
import org.artifactory.descriptor.backup.BackupDescriptor;

import java.io.File;
import java.util.Optional;

/**
 * Save-time validator for {@link BackupDescriptor#getDir() backup destinations}. Mirrors the
 * runtime gates in {@code BackupServiceImpl#backupSystem} / {@code scheduleImmediateSystemBackup}
 * so that a misconfiguration is rejected when the operator clicks Save (HTTP 400) instead of
 * silently surviving until the next cron tick and only failing in the backup status holder.
 */
@UtilityClass
class BackupDescriptorValidator {

    /**
     * Validates the descriptor's destination directory against the same gates the runtime
     * applies. Returns the operator-facing error message when the destination violates a gate,
     * or empty when the descriptor is acceptable to save.
     * <ul>
     *   <li>{@code null} dir — accepted (legacy behavior; runtime treats it as "no override")</li>
     *   <li>dir inside {@code <ART_HOME>}/{@code var}/{@code data}/{@code etc} — rejected</li>
     *   <li>backup allowlist configured AND dir outside it — rejected</li>
     * </ul>
     */
    static Optional<String> validate(BackupDescriptor backupDescriptor) {
        File dir = backupDescriptor == null ? null : backupDescriptor.getDir();
        if (dir == null) {
            return Optional.empty();
        }
        if (PathValidatorUtil.isArtifactoryHomePath(dir)) {
            return Optional.of("Creating a backup inside '" + dir + "' is not allowed.");
        }
        if (!PathValidatorUtil.isBackupPathInAllowList(dir.getAbsolutePath())) {
            return Optional.of(BackupDescriptor.BACKUP_PATH_NOT_IN_ALLOWLIST + ": '" + dir + "'.");
        }
        return Optional.empty();
    }
}
