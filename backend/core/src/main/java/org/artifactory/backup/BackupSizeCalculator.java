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

package org.artifactory.backup;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import jakarta.annotation.Nonnull;
import org.artifactory.api.config.ExportSettingsImpl;
import org.artifactory.api.repo.BackupService;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.backup.BackupDescriptor;
import org.artifactory.fs.ItemInfo;
import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.repo.RepoPath;
import org.artifactory.storage.fs.service.ConfigsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Estimate disk space of a backup descriptor
 *
 * @author nadavy
 */
public class BackupSizeCalculator {

    private static final long METADATA_AVG_SIZE = 512;
    private static final int ROUND_UP_SIZE = 4096;

    private static final Logger log = LoggerFactory.getLogger(BackupSizeCalculator.class);

    private final ConfigsService configsService;
    private final RepositoryService repositoryService;
    private final BackupService backupService;
    private final File dataDir;
    private final File etcDir;

    public BackupSizeCalculator(
            RepositoryService repositoryService,
            ConfigsService configsService,
            BackupService backupService,
            File dataDir,
            File etcDir) {
        this.repositoryService = repositoryService;
        this.configsService = configsService;
        this.backupService = backupService;
        this.dataDir = dataDir;
        this.etcDir = etcDir;
    }

    /**
     * Checks if current amount of used space exceeds a threshold set by
     * <code>ConstantValues.maxUsedSpaceAfterBackupPercent<code/>.<br>
     * The formula is: <code>(totalSpaceAvailable - freeSpace) / totalSpaceAvailable >= threshold</code>
     */
    public boolean isMaxUsedSpaceExceeded(@Nonnull BackupDescriptor backup) {
        ExportSettingsImpl settings = getExportSettings(backup);
        settings.setOutputFile(backup.getDir());
        try {
            FileStore fileStore = getFileStore(settings);
            double totalSpace = fileStore.getTotalSpace();
            double freeSpace = fileStore.getUnallocatedSpace();
            double threshold = ConstantValues.backupMaxUsedSpacePercent.getInt() / 100.0;
            // Check if the threshold is already exceeded
            return (totalSpace - freeSpace) / totalSpace >= threshold;
        } catch (IOException e) {
            log.error("Can't estimate free space in backup target location path. Skipping a check for " +
                    "'max used space before backup' threshold.", e);
            return false;
        }
    }

    /**
     * Checks if amount of used space is going exceed the threshold set by
     * <code>ConstantValues.maxUsedSpaceAfterBackupPercent</code> after the backup is performed.<br>
     * The formula is: <code>(totalSpaceAvailable - freeSpace + backupSize) / totalSpaceAvailable >= threshold</code>
     */
    public boolean willExceedMaxUsedSpaceAfterBackup(@Nonnull BackupDescriptor backup) {
        ExportSettingsImpl settings = getExportSettings(backup);
        settings.setOutputFile(backup.getDir());
        try {
            FileStore fileStore = getFileStore(settings);
            double totalSpace = fileStore.getTotalSpace();
            double freeSpace = fileStore.getUnallocatedSpace();
            double threshold = ConstantValues.backupMaxUsedSpacePercent.getInt() / 100.0;
            // Check if the threshold is going to be exceeded after the backup
            double backupSize = calculateBackupSize(settings);
            return (totalSpace - freeSpace + backupSize) / totalSpace >= threshold;
        } catch (IOException e) {
            log.error("Can't estimate free space in backup target location path. Skipping a check for " +
                    "'max used space after backup' threshold.", e);
            return false;
        }
    }

    boolean isEnoughFreeSpace(@Nonnull ExportSettingsImpl settings) {
        if (!settings.isPrecalculate()) {
            return true;
        }
        long freeDiskSpace;
        try {
            freeDiskSpace = getFileStore(settings).getUnallocatedSpace();
            log.info("Free space available for backup: {}", freeDiskSpace);
        } catch (Exception e) {
            log.error("Can't estimate free space in backup target location path. starting backup without space check",
                    e);
            return true;
        }
        return calculateBackupSize(settings) < freeDiskSpace;
    }

    private long calculateBackupSize(@Nonnull ExportSettingsImpl settings) {
        long backupSize = 0;
        List<String> repoKeys = settings.getRepositories();
        // calculate and add repository size
        for (String repoKey : repoKeys) {
            RepoPath repoPath = InternalRepoPathFactory.create(repoKey, ".");
            backupSize += calculateRepoPathSize(repositoryService.getItemInfo(repoPath));
        }
        // calculate and add config files size
        backupSize += getBackupFilesSize();
        // calculate and add license file size
        if (configsService.getConfig("licenses.xml") != null) {
            backupSize += roundSizeUpTo(configsService.getConfig("licenses.xml").length());
        }
        log.debug("Estimated backup size: {}", backupSize);
        return backupSize;
    }

    @VisibleForTesting
    protected FileStore getFileStore(ExportSettingsImpl settings) throws IOException {
        return settings.getOutputFile() == null ? Files.getFileStore(dataDir.toPath()) :
                Files.getFileStore(settings.getOutputFile().toPath());
    }

    private long calculateRepoPathSize(ItemInfo itemInfo) {
        if (itemInfo.isFolder()) {
            return METADATA_AVG_SIZE + repositoryService.getChildren(itemInfo.getRepoPath())
                    .stream()
                    .mapToLong(this::calculateRepoPathSize)
                    .sum();
        } else {
            return roundSizeUpTo(
                    repositoryService.getFileInfo(itemInfo.getRepoPath()).getSize() + METADATA_AVG_SIZE
            );
        }
    }

    private long getBackupFilesSize() {
        File[] etcDirFiles = etcDir.listFiles();

        List<File> configFiles = Lists.newArrayList();
        if (etcDirFiles != null) {
            configFiles.addAll(Arrays.asList(etcDirFiles));
        }
        File[] files = dataDir.listFiles();
        if (files != null) {
            File artifactoryProperties = findFile(files, "artifactory.properties");
            if (artifactoryProperties != null) {
                configFiles.add(artifactoryProperties);
            }
        }
        if (etcDirFiles != null) {
            File artifactoryConfigXml = findFile(etcDirFiles, "artifactory.config.xml");
            if (artifactoryConfigXml != null) {
                configFiles.add(artifactoryConfigXml);
            }
        }
        return configFiles.stream().mapToLong(file -> roundSizeUpTo(file.length())).sum();
    }

    private File findFile(File[] files, String fileName) {
        for (File file : files) {
            if (file.getName().equals(fileName)) {
                return file;
            }
        }
        return null;
    }

    /**
     * Round up x to the closest multiple multiple
     */
    private long roundSizeUpTo(long x) {
        return (x + (long) BackupSizeCalculator.ROUND_UP_SIZE - 1L) / (long) BackupSizeCalculator.ROUND_UP_SIZE *
                (long) BackupSizeCalculator.ROUND_UP_SIZE;
    }

    private ExportSettingsImpl getExportSettings(@Nonnull BackupDescriptor backup) {
        Set<String> excludeRepositories = new HashSet<>(backup.getExcludedRepositories());
        Set<String> backedUpRepos = backupService.getBackedUpRepos(excludeRepositories);
        boolean createArchive = backup.isCreateArchive();
        boolean incremental = backup.isIncremental();
        ExportSettingsImpl settings = new ExportSettingsImpl(new File(""));
        settings.setRepositories(new ArrayList<>(backedUpRepos));
        settings.setCreateArchive(createArchive);
        settings.setIncremental(incremental);
        settings.addCallback(new SystemBackupPauseCallback());
        settings.setPrecalculate(backup.isPrecalculate());
        return settings;
    }
}
