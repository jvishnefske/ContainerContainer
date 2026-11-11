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

package org.artifactory.repo.service;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import jakarta.annotation.Nullable;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.release.bundle.ReleaseBundleAddon;
import org.artifactory.api.config.RepositoryImportSettingsImpl;
import org.artifactory.backup.BackupJob;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.maven.index.MavenIndexerJob;
import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.cleanup.ArtifactCleanupJob;
import org.artifactory.repo.cleanup.ArtifactCleanupJobUnexclusive;
import org.artifactory.repo.cleanup.IntegrationCleanupJob;
import org.artifactory.repo.db.importexport.DbRepoImportExportBase;
import org.artifactory.repo.db.importexport.DbRepoImportHandler;
import org.artifactory.repo.replication.PushReplicationJob;
import org.artifactory.repo.replication.RemoteReplicationJob;
import org.artifactory.repo.replication.RemoteReplicationJobUnexclusive;
import org.artifactory.repo.virtual.VirtualRepo;
import org.artifactory.schedule.CachedThreadPoolTaskExecutor;
import org.artifactory.schedule.JobCommand;
import org.artifactory.schedule.JobName;
import org.artifactory.schedule.QuartzCommand;
import org.artifactory.schedule.StopCommand;
import org.artifactory.schedule.StopStrategy;
import org.artifactory.schedule.Task;
import org.artifactory.schedule.TaskCallback;
import org.artifactory.schedule.TaskUser;
import org.artifactory.spring.InternalContextHelper;
import org.artifactory.storage.StorageService;
import org.artifactory.storage.jobs.CalculateReposStorageSummaryJob;
import org.artifactory.storage.jobs.SyncStorageUsageJob;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.AsyncTaskExecutor;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

import static org.artifactory.repo.db.importexport.DbRepoImportExportBase.RENAMED_BY_EXPORT;
import static org.artifactory.repo.db.importexport.SafePathUtils.convertFromSafeName;
import static org.artifactory.repo.system.SystemRepos.EDGE_UPLOADS_REPO_KEY;
import static org.artifactory.schedule.StopStrategy.STOP;

/**
 * This job triggers the actual import process of either single or multiple repositories.
 *
 * @author freds
 * @author Yoav Luft
 */
@JobCommand(
        jobName = JobName.IMPORT,
        description = "Import Repositories",
        manualUser = TaskUser.CURRENT,
        keyAttributes = {Task.REPO_KEY},
        commandsToStop = {
                @StopCommand(command = ExportJob.class, strategy = StopStrategy.IMPOSSIBLE),
                @StopCommand(command = BackupJob.class, strategy = StopStrategy.IMPOSSIBLE),
                @StopCommand(command = MavenIndexerJob.class, strategy = StopStrategy.STOP),
                @StopCommand(command = ArtifactCleanupJob.class, strategy = StopStrategy.STOP),
                @StopCommand(command = ArtifactCleanupJobUnexclusive.class, strategy = StopStrategy.STOP),
                @StopCommand(command = IntegrationCleanupJob.class, strategy = StopStrategy.STOP),
                @StopCommand(command = PushReplicationJob.class, strategy = StopStrategy.STOP),
                @StopCommand(command = RemoteReplicationJob.class, strategy = StopStrategy.STOP),
                @StopCommand(command = RemoteReplicationJobUnexclusive.class, strategy = StopStrategy.STOP),
                @StopCommand(command = CalculateReposStorageSummaryJob.class, strategy = STOP),
                @StopCommand(command = SyncStorageUsageJob.class, strategy = STOP)},
        clusterSingleton = false,
        allowedAffinity = false)
public class ImportJob extends QuartzCommand {
    private static final Logger log = LoggerFactory.getLogger(ImportJob.class);

    public final static String THREAD_NAME_PREFIX = "art-import-";
    public static final String REMOTE_CLIENT_IP = "remoteClientIp";

    private RepositoryImportSettingsImpl importSettings;
    private String remoteClientIp;
    private MutableStatusHolder statusHolder;
    private final InternalRepositoryService repositoryService;
    private final StorageService storageService;
    private final ImportHandlerCallablesFactory callablesFactory;
    private final Semaphore parallelImportsGate;
    private final AsyncTaskExecutor taskExecutor;
    private final ReleaseBundleAddon releaseBundleAddon;

    public ImportJob() {
        int maxParallelImports = Math.max(ConstantValues.importMaxParallelRepos.getInt(), 1);
        parallelImportsGate = new Semaphore(maxParallelImports);
        callablesFactory = new ImportHandlerCallablesFactory(parallelImportsGate, TaskCallback.currentTaskId());
        taskExecutor = new CachedThreadPoolTaskExecutor(THREAD_NAME_PREFIX);
        repositoryService = InternalContextHelper.get().beanForType(InternalRepositoryService.class);
        storageService = InternalContextHelper.get().beanForType(StorageService.class);
        releaseBundleAddon = InternalContextHelper.get().beanForType(AddonsManager.class)
                .addonByType(ReleaseBundleAddon.class);
    }

    /**
     * Constructor for use by tests
     */
    ImportJob(AsyncTaskExecutor executor,
            ImportHandlerCallablesFactory callablesFactory,
            Semaphore gate,
            InternalRepositoryService repositoryService,
            StorageService storageService,
            ReleaseBundleAddon releaseBundleAddon
    ) {
        parallelImportsGate = gate;
        this.callablesFactory = callablesFactory;
        this.taskExecutor = executor;
        this.repositoryService = repositoryService;
        this.storageService = storageService;
        this.releaseBundleAddon = releaseBundleAddon;
    }

    @Override
    protected void onExecute(JobExecutionContext callbackContext) {
        try {
            initializeFromContext(callbackContext);
            deleteArtifactBundles();
            deleteRepositories();
            cleanVirtualCache();
            List<Callable<DbRepoImportHandler>> importers = prepareImportHandlerCallables();
            List<Future<DbRepoImportHandler>> handlerFutures = new ArrayList<>(importers.size());
            for (Callable<DbRepoImportHandler> importer : importers) {
                // we acquire an import permit here and each importer is responsible to release it when it ends
                parallelImportsGate.acquire();
                handlerFutures.add(taskExecutor.submit(importer));
            }
            log.debug("All import threads were submitted");
            for (Future<DbRepoImportHandler> handlerFuture : handlerFutures) {
                // serially call finalize for each repo which will trigger another retry phase if required
                // this has to be serial to avoid further concurrent import conflicts
                finalizeImport(handlerFuture);
            }
            log.info("Import of {} repositories completed", importSettings.getRepositories().size());
            checkForUnusedSubdirectories();

            log.info("Refresh storage summary cache async");
            storageService.calculateStorageSummaryAsync(); // Import should succeed even if this fails
        } catch (Exception e) {
            if (statusHolder != null) {
                statusHolder.error("Error occurred during import: " + e.getMessage(), e, log);
            } else {
                log.error("Error occurred during import", e);
            }
        } finally {
            if (taskExecutor instanceof CachedThreadPoolTaskExecutor) {
                ((CachedThreadPoolTaskExecutor) taskExecutor).destroy();
            }
        }
    }

    @Override
    protected List<String> getLockKeyAttributes() {
        return List.of(Task.REPO_KEY);
    }

    private void deleteArtifactBundles() {
        releaseBundleAddon.deleteAllBundles();
    }

    private void finalizeImport(Future<DbRepoImportHandler> handlerFuture) {
        try {
            DbRepoImportHandler importHandler = handlerFuture.get();
            importHandler.finalizeImport();
        } catch (InterruptedException e) {
            log.info("Import was interrupted");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            log.error("Import had failed due to {}: {}", cause.getClass().getName(), cause.getMessage());
            log.debug("Import had failed due to {}: {}",
                    cause.getClass().getName(), cause.getMessage(), cause);
            if (statusHolder != null) {
                statusHolder.error("Error occurred during import: " + cause.getMessage(), cause, log);
            }
        } catch (RuntimeException e) {
            // Extract cause
            if (statusHolder != null) {
                statusHolder.error("Error occurred during import: " + e.getMessage(), e, log);
            } else {
                log.error("Error occurred during import", e);
            }
        }
    }

    private List<Callable<DbRepoImportHandler>> prepareImportHandlerCallables() {
        List<String> repoKeysToImport = importSettings.getRepositories();
        List<Callable<DbRepoImportHandler>> importers = new ArrayList<>(repoKeysToImport.size());
        for (String repoKey : repoKeysToImport) {
            try {
                Callable<DbRepoImportHandler> handlerCallable = createHandlerCallable(repoKey);
                importers.add(handlerCallable);
            } catch (FileNotFoundException e) {
                // Ignore it for EDGE_UPLOADS_REPO_KEY, as it may have been created automatically internally
                if (!EDGE_UPLOADS_REPO_KEY.equals(repoKey)) {
                    if (importSettings.isFailIfEmpty()) {
                        throw new RuntimeException(e);
                    } else {
                        statusHolder.warn(e.getMessage(), log);
                    }
                }
            }
        }
        return importers;
    }

    private Callable<DbRepoImportHandler> createHandlerCallable(String repoKey) throws FileNotFoundException {
        File repoRoot;
        if (importSettings.isSingleRepoImport()) {
            repoRoot = importSettings.getBaseDir();
        } else {
            // base dir is the root repositories dir (e.g., the specific repo is under 'repositories/repoKey')
            repoRoot = getRepoRootDir(repoKey, importSettings.getBaseDir());
        }
        return callablesFactory.create(repoKey, repoRoot);
    }

    private void deleteRepositories() {
        for (String repoKey : importSettings.getRepositoriesToDelete()) {
            deleteExistingRepository(repoKey);
        }
    }

    private void deleteExistingRepository(String repoKey) {
        statusHolder.status("Fully removing repository '" + repoKey + "'.", log);
        RepoPath deleteRepoPath = InternalRepoPathFactory.repoRootPath(repoKey);
        try {
            repositoryService.undeploy(deleteRepoPath);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        statusHolder.status("Repository '" + repoKey + "' fully deleted.", log);
    }

    private void cleanVirtualCache() {
        for (VirtualRepo virtualRepo : repositoryService.getVirtualRepositories()) {
            repositoryService.undeploy(RepoPathFactory.create(virtualRepo.getKey()));
        }
    }

    private void initializeFromContext(JobExecutionContext callbackContext) {
        JobDataMap jobDataMap = callbackContext.getJobDetail().getJobDataMap();
        importSettings = (RepositoryImportSettingsImpl) jobDataMap.get(RepositoryImportSettingsImpl.class.getName());
        callablesFactory.setBaseImportSettings(importSettings);
        remoteClientIp = jobDataMap.getString(REMOTE_CLIENT_IP);
        callablesFactory.setRemoteClientIp(remoteClientIp);
        statusHolder = importSettings.getStatusHolder();
    }

    private void checkForUnusedSubdirectories() {
        if (importSettings.isSingleRepoImport()) {
            return;
        }
        Collection<String> directoryNames = collectSubdirectoriesNames();
        if (importSettings.isFailIfEmpty()
                && Collections.disjoint(importSettings.getRepositories(), directoryNames)) {
            statusHolder.error("The selected directory did not contain any repositories.", log);
        } else {
            for (String subDir : directoryNames) {
                boolean isMetadata = subDir.contains("metadata");
                boolean isIndex = subDir.contains("index");
                String repoName = getOriginalName(subDir);
                if (!isMetadata && !isIndex && !importSettings.getRepositories().contains(repoName)) {
                    statusHolder.warn("The directory " + subDir + " does not match any repository key.", log);
                }
            }
        }
    }

    private Collection<String> collectSubdirectoriesNames() {
        File[] subDirectories = importSettings.getBaseDir().listFiles(
                (java.io.FileFilter) DirectoryFileFilter.DIRECTORY);
        return Collections2.transform(Arrays.asList(subDirectories), new Function<File, String>() {
            @Nullable
            @Override
            public String apply(File file) {
                return file.getName();
            }
        });
    }

    private File getRepoRootDir(String repoKey, File baseDir) throws FileNotFoundException {
        String importDirectory = findImportDirectory(repoKey);
        if (!importDirectory.isEmpty()) {
            File repoBaseDir = new File(baseDir, importDirectory);
            if (repoBaseDir.canRead() && repoBaseDir.isDirectory()) {
                log.info("Repository {} will be imported from directory {}", repoKey, repoBaseDir);
                return repoBaseDir;
            }
        }
        throw new FileNotFoundException("No directory for repository " + repoKey + " found at " + baseDir);
    }

    public String getOriginalName(String subDir) {
        File renameMarker = new File(DbRepoImportExportBase.getMetadataContainerFolder(
                new File(importSettings.getBaseDir(), subDir)), RENAMED_BY_EXPORT);
        return renameMarker.exists() ? convertFromSafeName(subDir) : subDir;
    }

    private String findImportDirectory(String repoKey) {
        Collection<String> directories = collectSubdirectoriesNames();
        for (String directory : directories) {
            String originalName = getOriginalName(directory);
            boolean renamed = !directory.equals(originalName);
            if (renamed && directories.contains(originalName)) {
                throw new IllegalStateException("Duplicate repository directories found: " + originalName);
            }
            if (repoKey.equals(originalName)) {
                return directory;
            }
        }
        return "";
    }
}