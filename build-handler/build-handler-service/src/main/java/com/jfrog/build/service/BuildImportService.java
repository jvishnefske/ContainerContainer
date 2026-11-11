package com.jfrog.build.service;

import com.google.common.annotations.VisibleForTesting;
import com.jfrog.build.acl.bridge.BuildBridgeToArtifactory;
import com.jfrog.build.acl.model.BuildImportSettings;
import com.jfrog.build.acl.util.BuildConstants;
import com.jfrog.build.api.ImportableExportableBuild;
import com.jfrog.build.tx.BuildTxRunner;
import com.jfrog.build.xml.BuildJaxbAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.AbstractFileFilter;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.common.status.StatusEntry;
import org.jfrog.build.api.Build;
import org.jfrog.common.JsonUtils;

import java.io.File;
import java.io.FileInputStream;
import java.util.Collection;

import static java.lang.String.format;
import static java.util.Objects.nonNull;
import static org.apache.commons.io.filefilter.DirectoryFileFilter.DIRECTORY;
import static org.artifactory.exception.factory.ExceptionFactory.internalServerException;

@Slf4j
@RequiredArgsConstructor
public class BuildImportService {

    private final BuildBridgeToArtifactory bridgeToArtifactory;

    private final BuildTxRunner txRunner;
    private final BuildCreationService creationService;
    private final BuildDatabaseService databaseService;
    private final BuildRepoService repoService;

    private BuildJaxbAdapter jaxbAdapter;

    public void reload() {
        jaxbAdapter = new BuildJaxbAdapter();
    }

    public void importFrom(BuildImportSettings settings) {
        settings.getStatus().status("Starting build info import", log);
        repoService.clearCaches();
        if (settings.isHybridMigration()) {
            log.debug("Import kind is Hybrid Migration - builds an repositories will not be deleted");
        } else {
            log.debug("Import kind is Standard - builds and repositories will be deleted");
            deleteBuildsAndRepositories(settings);
        }
        importBuildFiles(settings);
    }

    @VisibleForTesting
    void deleteBuildsAndRepositories(BuildImportSettings settings) {
        txRunner.runTxNew(() -> {
            try {
                deleteAllBuildRepos();
                databaseService.deleteAllBuilds();
            } catch (Exception e) {
                settings.getStatus().error("Failed to delete builds root node", e, log);
            }
        });
    }

    /**
     * Removes the entire Build Info repo files, while avoiding the storage interceptors
     */
    private void deleteAllBuildRepos() {
        bridgeToArtifactory.doAsSystem(() -> {
            for (String buildRepo : repoService.getAllBuildInfoRepos()) {
                MutableStatusHolder status = bridgeToArtifactory.newStatusHolder();
                bridgeToArtifactory.undeploy(buildRepo, status, false, true, false, true, false);
                if (nonNull(status.getLastError())) {
                    StatusEntry statusEntry = status.getLastError();
                    throw internalServerException("Could not delete build json from %s: %s",
                            statusEntry.getException(), buildRepo, statusEntry.getMessage());
                }
            }
        });
    }

    @VisibleForTesting
    void importBuildFiles(BuildImportSettings settings) {
        File buildsFolder = new File(settings.getBaseDir(), BuildConstants.BUILDS_EXPORT_DIR);
        String buildsFolderPath = buildsFolder.getPath();
        if (!buildsFolder.exists()) {
            settings.getStatus().debug("'" + buildsFolderPath + "' folder is either non-existent "
                    + "or not a directory. Old-style build info import was not performed", log);
            return;
        }
        IOFileFilter buildExportFileFilter = new AbstractFileFilter() {

            @Override
            public boolean accept(File file) {
                String fileName = file.getName();
                return fileName.startsWith("build") && fileName.endsWith(".xml");
            }
        };
        Collection<File> buildExportFiles = FileUtils.listFiles(buildsFolder, buildExportFileFilter, DIRECTORY);
        if (buildExportFiles.isEmpty()) {
            settings.getStatus().status("'" + buildsFolderPath + "' folder does not contain build export files. "
                    + "Build info import was not performed", log);
            return;
        }
        importBuildFiles(settings, buildExportFiles);
        settings.getStatus().warn("Old-style import builds witnessed at location '"
                + buildsFolder.getParent() + File.separator + buildsFolder.getName()
                + "'.  If a build-info repository content export exists as well in this import it will be disregarded "
                + "for backwards compatibility.", log);
        settings.getStatus().warn("The correct way to import and export builds is to enable content export and let "
                + "Artifactory handle the build info repo import/export", log);
        settings.setExcludeBuildInfoRepo(true);
        settings.getStatus().status("Finished build info import", log);
    }

    private void importBuildFiles(BuildImportSettings settings, Collection<File> buildExportFiles) {
        for (File buildExportFile : buildExportFiles) {
            try (FileInputStream inputStream = new FileInputStream(buildExportFile)) {
                ImportableExportableBuild importableBuild = jaxbAdapter.fromXML(inputStream);
                // import each build in a separate transaction
                txRunner.runTx(() -> importBuild(settings, importableBuild));
            } catch (Exception e) {
                settings.getStatus().error("Error occurred during build info import", e, log);
                if (settings.isFailFast()) {
                    break;
                }
            }
        }
    }

    @VisibleForTesting
    public void importBuild(BuildImportSettings settings, ImportableExportableBuild importableBuild) {
        String buildName = importableBuild.getBuildName();
        String buildNumber = importableBuild.getBuildNumber();
        String buildStarted = importableBuild.getBuildStarted();
        String buildRepo = repoService.normaliseBuildRepoName(importableBuild.getBuildRepo());
        try {
            settings.getStatus().debug(format("Beginning import of build: %s:%s:%s from %s repo",
                    buildName, buildNumber, buildStarted, buildRepo), log);
            Build build = JsonUtils.getInstance().readValue(importableBuild.getJson(), Build.class);
            // Needs to be taken care of when adding support for projects related builds import\export
            creationService.createBuild(build, buildRepo);
        } catch (Exception e) {
            String msg = format("Could not import build %s:%s:%s from %s repo",
                    buildName, buildNumber, buildStarted, buildRepo);
            // Print stack trace in debug
            log.debug(msg, e);
            settings.getStatus().error(msg, e, log);
        }
        settings.getStatus().debug(format("Finished import of build: %s:%s:%s from %s repo",
                buildName, buildNumber, buildStarted, buildRepo), log);
    }
}
