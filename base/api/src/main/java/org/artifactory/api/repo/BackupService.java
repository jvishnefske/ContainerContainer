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

package org.artifactory.api.repo;

import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.config.ExportSettingsImpl;
import org.artifactory.descriptor.backup.BackupDescriptor;

import java.io.File;
import java.util.Set;
import java.util.function.Consumer;

/**
 * User: freds Date: Aug 5, 2008 Time: 9:25:33 PM
 */
public interface BackupService {
    void backupRepos(File backupDir, ExportSettingsImpl exportSettings);

    void scheduleImmediateSystemBackup(BackupDescriptor backupDescriptor, BasicStatusHolder statusHolder);

    Set<String> getBackedUpRepos(Set<String> excludeRepositories);

    boolean isEnoughFreeSpace(BackupDescriptor backupDescriptor);

    boolean isEnoughFreeSpace(ExportSettingsImpl settings);

    /**
     * Checks if there is enough space available to perform a specified backup. Takes a 'max used space threshold'
     * restrictions into account. If there is not enough space, details are provided using a specified consumer.
     */
    boolean isEnoughFreeSpace(BackupDescriptor backup, Consumer<String> detailsConsumer);
}
