package com.jfrog.build.converter;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.descriptor.backup.BackupDescriptor;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RealRepoConfig;

import java.io.File;
import java.io.IOException;

import static java.nio.charset.StandardCharsets.UTF_8;

@Slf4j
public class BuildBackupConverter {

    private CentralConfigService centralConfigService;
    private RepositoryService repositoryService;
    private SecurityService securityService;

    public BuildBackupConverter(
            CentralConfigService centralConfigService,
            RepositoryService repositoryService,
            SecurityService securityService) {
        this.centralConfigService = centralConfigService;
        this.repositoryService = repositoryService;
        this.securityService = securityService;
    }

    public void convert() {
        log.info("Starting backup configuration builds conversion");
        File createBackupExcludedBuildNames = ArtifactoryHome.get().getCreateBackupExcludedBuildNames();
        File backupFile;
        try {
            String[] backupKeys = getBackupKeyFromMarkerFile(createBackupExcludedBuildNames);
            RealRepoConfig buildInfoRepo = null;
            for (RealRepoConfig repo : repositoryService.getLocalFederatedAndRemoteRepoConfigs()) {
                if (repo.getPackageType() == RepoType.BuildInfo) {
                    buildInfoRepo = repo;
                    break;
                }
            }
            if (buildInfoRepo == null) {
                log.error("Couldn't find build-info repo");
                return;
            }
            MutableCentralConfigDescriptor mutableDescriptor = centralConfigService.getMutableDescriptor();
            if (CollectionUtils.isEmpty(mutableDescriptor.getBackups())) {
                return;
            }
            for (String key : backupKeys) {
                BackupDescriptor backup = mutableDescriptor.getBackup(key);
                if (backup == null) {
                    return;
                }
                // Before adding new excluded repo we need to allow exclude new repos
                boolean excludeNewRepositories = backup.isExcludeNewRepositories();
                backup.setExcludeNewRepositories(true);
                backup.addExcludedRepository(buildInfoRepo.getKey());
                backup.setExcludeNewRepositories(excludeNewRepositories);
            }
            // we must change/delete the file before saving the configuration.
            // If we don't we will have a loop and then save operation will fail.
            backupFile = backupMarkerFile(createBackupExcludedBuildNames);
            securityService.doAsSystem(() -> centralConfigService
                    .saveEditedDescriptorAndReload(mutableDescriptor));
        } catch (IOException e) {
            log.error("Failed to read backups marker file", e);
            return;
        }
        log.info("Deleting backup file {}", backupFile.getAbsolutePath());
        FileUtils.deleteQuietly(backupFile);
    }

    private String[] getBackupKeyFromMarkerFile(File createBackupExcludedBuildNames) throws IOException {
        String content = FileUtils.readFileToString(createBackupExcludedBuildNames, UTF_8);
        String replace = content.replace("]", "").replace("[", "");
        return replace.split(",");
    }

    private File backupMarkerFile(File createBackupExcludedBuildNames) throws IOException {
        File backupFile = new File(createBackupExcludedBuildNames.getAbsolutePath()
                .replace(createBackupExcludedBuildNames.getName(),
                        createBackupExcludedBuildNames.getName() + ".bck"));
        FileUtils.moveFile(createBackupExcludedBuildNames, backupFile);
        return backupFile;
    }
}
