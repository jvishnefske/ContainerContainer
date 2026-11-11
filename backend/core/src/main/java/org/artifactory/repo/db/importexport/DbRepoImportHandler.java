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

package org.artifactory.repo.db.importexport;

import com.google.common.collect.Sets;
import org.apache.commons.io.input.CountingInputStream;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.cargo.CargoAddon;
import org.artifactory.addon.yum.YumAddon;
import org.artifactory.api.config.ImportSettingsImpl;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.maven.MavenMetadataService;
import org.artifactory.api.maven.MavenMetadataWorkItem;
import org.artifactory.api.security.SecurityService;
import org.artifactory.checksum.ChecksumType;
import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.fs.FileInfo;
import org.artifactory.fs.FolderInfo;
import org.artifactory.fs.MetadataEntryInfo;
import org.artifactory.fs.MutableStatsInfo;
import org.artifactory.fs.StatsInfo;
import org.artifactory.fs.WatcherInfo;
import org.artifactory.fs.WatchersInfo;
import org.artifactory.io.checksum.ChecksumUtils;
import org.artifactory.md.MetadataDefinition;
import org.artifactory.md.MetadataDefinitionService;
import org.artifactory.md.PropertiesInfo;
import org.artifactory.model.LocalConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.properties.PropertiesImpl;
import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.interceptor.ImportInterceptors;
import org.artifactory.repo.interceptor.StorageAggregationInterceptors;
import org.artifactory.repo.local.ValidDeployPathContext;
import org.artifactory.repo.packagetype.configuration.YumConfiguration;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.sapi.common.ImportSettings;
import org.artifactory.sapi.fs.MetadataReader;
import org.artifactory.schedule.Task;
import org.artifactory.schedule.TaskService;
import org.artifactory.security.AccessLogger;
import org.artifactory.spring.InternalContextHelper;
import org.artifactory.storage.BinaryInsertRetryException;
import org.artifactory.storage.fs.MutableVfsFile;
import org.artifactory.storage.fs.MutableVfsFolder;
import org.artifactory.storage.fs.MutableVfsItem;
import org.artifactory.storage.fs.service.StatsService;
import org.artifactory.storage.spring.ArtifactoryStorageContext;
import org.artifactory.storage.spring.StorageContextHelper;
import org.artifactory.traffic.TrafficService;
import org.artifactory.traffic.TrafficEntry;
import org.artifactory.update.md.MetadataReaderImpl;
import org.artifactory.util.CollectionUtils;
import org.artifactory.util.GlobalExcludes;
import org.artifactory.util.StreamUtils;
import org.artifactory.util.YumPackageTypeConfigMapper;
import org.jfrog.client.util.PathUtils;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static org.artifactory.repo.db.importexport.ImportExportAccumulator.ProgressAccumulatorType.IMPORT;
import static org.artifactory.repo.db.importexport.SafePathUtils.convertFromSafeName;

/**
 * Imports a single repository from the file system while managing transactions.
 * This handler is used in two phases: import and finalize. Import does the actual import, after which there might be
 * recoverable failures that can be retried. This breakdown allows the import to be externally synchronized.
 *
 * @author Yossi Shaul
 */
public class DbRepoImportHandler extends DbRepoImportExportBase {
    private static final Logger log = LoggerFactory.getLogger(DbRepoImportHandler.class);
    private static final int MAX_ITEMS_PER_TRANSACTION = 1000;
    private static final Duration MAX_TIME_PER_TRANSACTION = Duration.standardMinutes(3);
    private final LocalRepo<? extends LocalRepoDescriptor, ? extends LocalConfig> repo;
    private final ImportSettings settings;
    private final String parentTaskToken;
    private ImportExportAccumulator progressAccumulator;
    private MutableStatusHolder status;
    private TransactionStatus transactionStatus;
    private Set<ImportItem> itemsToRetry = Sets.newHashSet();
    private DateTime transactionStartTime;
    private File fileSystemBaseDir;
    private Function<InputStream, CountingInputStream> streamFactory = CountingInputStream::new;
    private String remoteClientIp;
    private final InternalRepositoryService repositoryService;
    private final StatsService statsService;

    public DbRepoImportHandler(LocalRepo<? extends LocalRepoDescriptor,
            ? extends LocalConfig> repo, ImportSettings settings,
            String parentTaskToken, String remoteClientIp) {
        this.repo = repo;
        this.settings = settings;
        status = settings.getStatusHolder();
        this.parentTaskToken = parentTaskToken;
        this.remoteClientIp = remoteClientIp;
        this.repositoryService = ContextHelper.get().beanForType(InternalRepositoryService.class);
        this.statsService = ContextHelper.get().beanForType(StatsService.class);
    }

    public void executeImport() {
        try {
            fileSystemBaseDir = settings.getBaseDir();
            status.status(String.format("%s import started %s", repo.getKey(), fileSystemBaseDir), log);
            if (fileSystemBaseDir == null || !fileSystemBaseDir.isDirectory()) {
                status.error("Error Import: Cannot import null, non existent folder or non directory file '"
                        + fileSystemBaseDir + "'.", log);
                return;
            }

            progressAccumulator = new ImportExportAccumulator(repo.getKey(), IMPORT);
            executeRecursiveImport(fileSystemBaseDir);
            if (hasRetries()) {
                importFromRetryList();
            }
        } finally {
            if (!hasRetries()) {
                internalFinalizeImport();
            }
        }
    }

    public void finalizeImport() {
        if (hasRetries()) {
            importFromRetryList();
            internalFinalizeImport();
        } else {
            log.debug("Import was already finalized");
        }
        if (RepoType.Cargo.equals(repo.getDescriptor().getType())) {
            CargoAddon cargoAddon = ContextHelper.get().beanForType(AddonsManager.class).addonByType(CargoAddon.class);
            cargoAddon.reindex(repo.getKey());
        }
    }

    private boolean hasRetries() {
        return !itemsToRetry.isEmpty();
    }

    private void internalFinalizeImport() {
        progressAccumulator.finished();
        RepoPath rootRepoPath = InternalRepoPathFactory.repoRootPath(repo.getKey());
        runPostImportCalculations(rootRepoPath);
        reportEndOfImport(fileSystemBaseDir);
    }

    private void executeRecursiveImport(File fileSystemBaseDir) {
        RepoPath rootRepoPath = InternalRepoPathFactory.repoRootPath(repo.getKey());
        startTransaction();
        try {
            importRecursive(fileSystemBaseDir, rootRepoPath, "");
        } finally {
            commitTransaction(transactionStatus);
        }
    }

    private void reportEndOfImport(File fileSystemBaseDir) {
        status.status(String.format("%s import finished with: %s Items imported: (%s files %s folders). " +
                                "Duration: %s IPS: %s Target: '%s'",
                        repo.getKey(), progressAccumulator.getSuccessfulItemsCount(),
                        progressAccumulator.getSuccessfulFilesCount(),
                        progressAccumulator.getSuccessfulFoldersCount(),
                        progressAccumulator.getDurationString(), progressAccumulator.getItemsPerSecond(),
                        fileSystemBaseDir
                ),
                log
        );
    }

    private void runPostImportCalculations(RepoPath rootRepoPath) {
        if (!repo.isCache()) {
            ContextHelper.get().beanForType(MavenMetadataService.class)
                    .calculateMavenMetadataAsync(new MavenMetadataWorkItem(rootRepoPath, true));
            ContextHelper.get().beanForType(StorageAggregationInterceptors.class).afterRepoImport(
                    rootRepoPath, progressAccumulator.getSuccessfulItemsCount(), status);

            RepoConfig repoConfig = repo.getRepoConfig();
            YumConfiguration yumConfiguration =
                    YumPackageTypeConfigMapper.INSTANCE.map(repoConfig.getPackageTypeConfig());
            if (RepoType.YUM.equals(repoConfig.getPackageType())
                    && yumConfiguration.isCalculateYumMetadata()) {
                // We will get the passphrase later from the conf if it exists; a null value won't fail the calc
                AddonsManager addonsManager = StorageContextHelper.get().beanForType(AddonsManager.class);
                addonsManager.addonByType(YumAddon.class).requestRpmMetadataCalculation(repoConfig, null, true);
            }
        }
    }

    private void importFromRetryList() {
        log.info("{}: Retrying import of {} items", repo.getKey(), itemsToRetry.size());
        for (ImportItem toRetry : itemsToRetry) {
            itemsToRetry.remove(toRetry);
            startTransaction();
            try {
                importFile(toRetry.source(), toRetry.parent(), toRetry.child());
            } finally {
                commitTransaction(transactionStatus);
            }
        }
    }

    private void importRecursive(final File fileToImport, final RepoPath parent, String child) {
        RepoPathImpl target = getOriginalName(fileToImport, parent, child);
        if (shouldPauseOrBreak()) {
            status.error("Import of " + repo.getKey() + " was stopped", log);
            return;
        }

        if (shouldStartNewTransaction()) {
            commitTransaction(transactionStatus);
            startTransaction();
        }

        if (!fileToImport.exists()) {
            // skeleton import? looks for file metadata
            File fileInfoMetadata = new File(
                    fileToImport.getAbsolutePath() + METADATA_FOLDER + "/" + FileInfo.ROOT + ".xml");
            if (fileInfoMetadata.exists() && isStorableFile(fileToImport.getName())) {
                importFile(fileToImport, parent, child);
            } else {
                status.warn("File/metadata not found: " + fileToImport.getAbsolutePath(), log);
            }
        } else if (fileToImport.isFile() && isStorableFile(fileToImport.getName())) {
            importFile(fileToImport, parent, child);
        } else if (isStorableFolder(fileToImport.getName())) {
            boolean folderExistAfterImport = importFolderMetaData(fileToImport, target);
            if (!folderExistAfterImport) {
                log.debug("Folder '{}' doesn't exist after import. Skipping import children of '{}'", target,
                        fileToImport);
                return;
            }
            File[] filesToImport = fileToImport.listFiles();
            if (filesToImport != null && filesToImport.length > 0) {
                Set<String> fileNames = collectFileNamesForImport(filesToImport);
                for (String fileName : fileNames) {
                    importRecursive(new File(fileToImport, fileName), target, fileName);
                }
            }
        }
    }

    private static RepoPathImpl getOriginalName(File fileToImport, RepoPath parent, String child) {
        File renameMarker = new File(getMetadataContainerFolder(fileToImport), RENAMED_BY_EXPORT);
        return new RepoPathImpl(parent, renameMarker.exists() ? convertFromSafeName(child) : child);
    }

    private boolean shouldPauseOrBreak() {
        if (StringUtils.isBlank(parentTaskToken)) {
            return false;
        }
        TaskService taskService = InternalContextHelper.get().getTaskService();
        Task activeTask = taskService.getActiveTask(parentTaskToken, false);
        if (activeTask != null) {
            return activeTask.blockIfPausedAndShouldBreak();
        } else {
            return taskService.pauseOrBreak();
        }
    }

    private boolean shouldStartNewTransaction() {
        boolean reachedMaxFiles = progressAccumulator.getSuccessfulItemsCount() > 0 &&
                progressAccumulator.getSuccessfulItemsCount() % MAX_ITEMS_PER_TRANSACTION == 0;
        boolean transactionTimeMaxed = transactionStartTime.plus(MAX_TIME_PER_TRANSACTION).isBeforeNow();
        return reachedMaxFiles || transactionTimeMaxed;
    }

    private Set<String> collectFileNamesForImport(File[] filesToImport) {
        Set<String> fileNames = Sets.newHashSetWithExpectedSize(filesToImport.length / 2);
        for (File childFile : filesToImport) {
            String name = childFile.getName();
            if (settings.isIncludeMetadata() && name.endsWith(METADATA_FOLDER)) {
                fileNames.add(name.substring(0, name.length() - METADATA_FOLDER.length()));
            } else if (isStorableFolder(name) && isStorableFile(name) &&
                    !GlobalExcludes.isInGlobalExcludes(childFile)) {
                fileNames.add(name);
            }
        }
        return fileNames;
    }

    private void importFile(final File fileToImport, final RepoPath parent, String child) {
        RepoPathImpl target = getOriginalName(fileToImport, parent, child);
        log.debug("Importing '{}'.", target);
        if (!settings.isIncludeMetadata() && !fileToImport.exists()) {
            addErrorMessage(fileToImport, target, "Cannot import non existent file (metadata is excluded): " +
                    fileToImport.getAbsolutePath());
            return;
        }
        if (isDeployPathValid(fileToImport, target)) {
            deployImportedFile(fileToImport, target);
        }
    }

    private boolean isDeployPathValid(File fileToImport, RepoPath target) {
        try {
            long length = -1L;
            if (fileToImport.exists()) {
                length = fileToImport.length();
            }
            InternalRepositoryService repositoryService = StorageContextHelper.get().beanForType(
                    InternalRepositoryService.class);
            repositoryService.assertValidDeployPathAndPermissions(
                    new ValidDeployPathContext.Builder(repo, target).contentLength(length).build());
        } catch (Exception e) {
            addErrorMessage(fileToImport, target, "Artifact rejected: " + e.getMessage());
            log.debug("Import of {} as {} rejected, reason: {}", fileToImport, target, e.getMessage(), e);
            progressAccumulator.accumulateSkippedFile();
            return false;
        }
        return true;
    }

    private void deployImportedFile(File fileToImport, RepoPath target) {
        ArtifactoryStorageContext context = StorageContextHelper.get();
        MutableVfsFile mutableFile = null;
        try {
            mutableFile = repo.createOrGetFile(target);
            importFileFrom(fileToImport, mutableFile);

            context.beanForType(ImportInterceptors.class).afterImport(mutableFile, status);
            log.debug("Imported '{}'.", target);
            AccessLogger.deployed(target);
            progressAccumulator.accumulateSuccessfulFile();
        } catch (BinaryInsertRetryException e) {
            log.info("Import of {} will be retried", target);
            log.debug("Import of " + target + " will be retried", e);
            RepoPath parent = target.getParent();
            String child = target.getName();
            itemsToRetry.add(new ImportItem(fileToImport, parent, child));
        } catch (Exception e) {
            addErrorMessage(fileToImport, target, "Could not import file '" + fileToImport.getAbsolutePath() +
                    " into " + target + ".", e);
            // mark the mutable item in error and let the session manager handle it
            if (mutableFile != null) {
                mutableFile.markError();
            }
            progressAccumulator.accumulateSkippedFile();
        }
    }

    private void addErrorMessage(File from, RepoPath to, String message) {
        addErrorMessage(from, to, message, null);
    }

    private void addErrorMessage(File from, RepoPath to, String message, Exception e) {
        String msg = String.format("Import error: from: %s to %s reason: %s", from, to, message);
        if (e == null) {
            status.error(msg, log);
        } else {
            status.error(msg, e, log);
        }
    }

    private void importFileFrom(File sourceFile, MutableVfsFile mutableFile) throws IOException {
        updateMutableFileBasicData(sourceFile, mutableFile);
        FileInfo importedFileInfo = null;
        RepoPath targetRepoPath = mutableFile.getRepoPath();
        if (settings.isIncludeMetadata()) {
            importedFileInfo = importFileMetadata(sourceFile, mutableFile, targetRepoPath);
        }
        if (importedFileInfo == null && !sourceFile.exists()) {
            throw new FileNotFoundException("Cannot import non existent file " + sourceFile.getAbsolutePath()
                    + " since metadata information was not found!");
        }
        if (importedFileInfo == null) {
            importMetadataFromSourceFile(sourceFile, mutableFile);
        }
        boolean binaryInfoExists = false;
        /*there's a big fat lie here, the 'expected' methods actually return the actual checksums
        it creates a big hole in the import validation but its out of scope and has been working
        like this forever.*/
        String expectedSha1 = getExpectedSha1(importedFileInfo, mutableFile);
        String expectedSha2 = getExpectedSha2(importedFileInfo, mutableFile);
        String expectedMd5 = getExpectedMd5(importedFileInfo, mutableFile);
        //[sha2]: change to sha2 when filestore switches
        if (ChecksumType.sha1.isValid(expectedSha1)) {
            binaryInfoExists = tryUsingExistingBinary(sourceFile, mutableFile, importedFileInfo);
        }
        if (!binaryInfoExists) {
            fillBinaryDataFromFile(sourceFile, mutableFile);
        }
        if (PathUtils.hasText(expectedSha1) && !mutableFile.getSha1().equals(expectedSha1)) {
            status.warn("Received file " + targetRepoPath + " with Checksum error on SHA1 " +
                    "actual=" + mutableFile.getSha1() + " expected=" + expectedSha1, log);
        }
        if (PathUtils.hasText(expectedSha2) && !mutableFile.getSha2().equals(expectedSha2)) {
            status.warn("Received file " + targetRepoPath + " with Checksum error on SHA256 " +
                    "actual=" + mutableFile.getSha2() + " expected=" + expectedSha2, log);
        }
        if (PathUtils.hasText(expectedMd5) && !mutableFile.getMd5().equals(expectedMd5)) {
            status.warn("Received file " + targetRepoPath + " with Checksum error on MD5 " +
                    "actual=" + mutableFile.getMd5() + " expected=" + expectedMd5, log);
        }
    }

    private FileInfo importFileMetadata(File sourceFile, MutableVfsFile mutableFile, RepoPath targetRepoPath) {
        FileInfo importedFileInfo;
        List<MetadataEntryInfo> metadataEntries = getMetadataEntryInfos(sourceFile);
        String sourcePath = sourceFile.getAbsolutePath();
        importedFileInfo = (FileInfo) readItemInfoMetadata(FileInfo.ROOT, sourcePath, targetRepoPath, metadataEntries);
        if (importedFileInfo != null) {
            mutableFile.fillInfo(importedFileInfo);
        }
        importPropertiesMetaData(sourcePath, mutableFile, metadataEntries);
        importWatchesMetaData(sourcePath, mutableFile, metadataEntries);
        importStatsMetaData(sourcePath, mutableFile, metadataEntries);
        return importedFileInfo;
    }

    private void importMetadataFromSourceFile(File sourceFile, MutableVfsFile mutableFile) throws IOException {
        String sha1FileValue = getOriginalChecksumFromFile(sourceFile, ChecksumType.sha1);
        if (StringUtils.isNotBlank(sha1FileValue)) {
            mutableFile.setClientSha1(sha1FileValue);
        }
        String sha2FileValue = getOriginalChecksumFromFile(sourceFile, ChecksumType.sha256);
        if (StringUtils.isNotBlank(sha2FileValue)) {
            mutableFile.setClientSha2(sha2FileValue);
        }
        String md5FileValue = getOriginalChecksumFromFile(sourceFile, ChecksumType.md5);
        if (StringUtils.isNotBlank(md5FileValue)) {
            mutableFile.setClientMd5(md5FileValue);
        }
    }

    private boolean tryUsingExistingBinary(File sourceFile, MutableVfsFile mutableFile, FileInfo importedFileInfo) {
        // Found file info in metadata : Try deploy by checksum
        status.debug("Using metadata import for " + sourceFile, log);
        String expectedSha1 = getExpectedSha1(importedFileInfo, mutableFile);
        String expectedSha2 = getExpectedSha2(importedFileInfo, mutableFile);
        String expectedMd5 = getExpectedMd5(importedFileInfo, mutableFile);
        long expectedLength = getExpectedLength(importedFileInfo, mutableFile);
        boolean binaryInfoExists =
                mutableFile.tryUsingExistingBinary(expectedSha1, expectedSha2, expectedMd5, expectedLength);
        if (binaryInfoExists) {
            status.debug("Found existing binary record for SHA1: " + expectedSha1, log);
        }
        return binaryInfoExists;
    }

    void setStreamFactory(Function<InputStream, CountingInputStream> streamFactory) {
        this.streamFactory = streamFactory;
    }

    private void fillBinaryDataFromFile(File sourceFile, MutableVfsFile mutableFile) throws IOException {
        if (!sourceFile.exists()) {
            throw new FileNotFoundException(
                    sourceFile.getAbsolutePath() + ": File doesn't exist. Either a matching binary is missing " +
                            "or you need to verify your export includes content. Otherwise, check the 'Exclude " +
                            "Content' checkbox");
        }
        try (InputStream stream = new BufferedInputStream(new FileInputStream(sourceFile))) {
            fillBinaryDataFromStream(stream, mutableFile);
        }
    }

    void fillBinaryDataFromStream(InputStream stream, MutableVfsFile mutableFile) {
        CountingInputStream cis = null;
        try {
            cis = streamFactory.apply(stream);
            mutableFile.fillBinaryData(cis);
        } finally {
            long transferred = 0;
            if (cis != null) {
                transferred = cis.getByteCount();
                StreamUtils.close(cis);
            }
            logTraffic(mutableFile, transferred);
        }
    }

    private void logTraffic(MutableVfsFile mutableFile, long transferred) {
        TrafficEntry entry = TrafficEntry.builder()
                .action(TrafficEntry.TrafficAction.UPLOAD)
                .actor(TrafficEntry.TrafficActor.USER)
                .redirectTarget(TrafficEntry.TrafficTarget.DIRECT)
                .repoPath(mutableFile.getRepoPath().getId())
                .userAddress(remoteClientIp)
                .traceId(MDC.get("uber-trace-id"))
                .requestedSize(mutableFile.length())
                .size(mutableFile.length())
                .transferredSize(transferred)
                .build();
        ContextHelper.get().beanForType(TrafficService.class).handleTrafficEntry(entry);
    }

    private String getExpectedSha1(FileInfo fileInfo, MutableVfsFile mutableFile) {
        if (fileInfo != null) {
            return fileInfo.getSha1();
        } else {
            return mutableFile.getSha1();
        }
    }

    private String getExpectedSha2(FileInfo fileInfo, MutableVfsFile mutableFile) {
        if (fileInfo != null) {
            return fileInfo.getSha2();
        } else {
            return mutableFile.getSha2();
        }
    }

    private String getExpectedMd5(FileInfo importedFileInfo, MutableVfsFile mutableFile) {
        if (importedFileInfo != null) {
            return importedFileInfo.getMd5();
        } else {
            return mutableFile.getMd5();
        }
    }

    private long getExpectedLength(FileInfo importedFileInfo, MutableVfsFile mutableFile) {
        if (importedFileInfo != null) {
            return importedFileInfo.getSize();
        } else {
            return mutableFile.length();
        }
    }

    private List<MetadataEntryInfo> getMetadataEntryInfos(File sourceFile) {
        File metadataFolder = getMetadataContainerFolder(sourceFile);
        if (!metadataFolder.exists()) {
            return null;
        }

        MetadataReader metadataReader = findBestMatchMetadataReader(settings, metadataFolder);
        return metadataReader.getMetadataEntries(metadataFolder, status);
    }

    //TORE: [by YS] requires refactoring
    private Object readItemInfoMetadata(String metadataName, String source, RepoPath target,
            List<MetadataEntryInfo> metadataEntries) {
        if (CollectionUtils.isNullOrEmpty(metadataEntries)) {
            if (!target.isRoot()) {
                status.debug("No Metadata entries found for " + source, log);
            }
            return null;
        }
        try {
            for (MetadataEntryInfo entry : metadataEntries) {
                if (metadataName.equals(entry.getMetadataName())) {
                    MetadataDefinitionService metadataDefinitionService = getMetadataDefinitionService();
                    MetadataDefinition definition = metadataDefinitionService.getMetadataDefinition(metadataName, true);
                    return definition.getXmlProvider().fromXml(entry.getXmlContent());
                }
            }
        } catch (Exception e) {
            String msg = "Failed to import metadata of " + source + " into '" + target + "'.";
            status.error(msg, e, log);
        }

        return null;
    }

    private void updateMutableFileBasicData(File sourceFile, MutableVfsFile mutableFile) {
        String currentUser = getCurrentUsername();
        mutableFile.setCreatedBy(currentUser);
        mutableFile.setModifiedBy(currentUser);
        if (sourceFile.exists()) {
            mutableFile.setModified(sourceFile.lastModified());
        }
        mutableFile.setUpdated(System.currentTimeMillis());
    }

    private String getOriginalChecksumFromFile(File artifactFile, ChecksumType checksumType) throws IOException {
        //TORE: [by YS] check for file size and use a util method
        File checksumFile = new File(artifactFile.getParent(), artifactFile.getName() + checksumType.ext());
        try (InputStream is = new BufferedInputStream(new FileInputStream(checksumFile))) {
            return ChecksumUtils.checksumStringFromStream(is);
        } catch (FileNotFoundException e) {
            log.debug("Couldn't find '{}' checksum file and Artifactory metadata doesn't exist.",
                    checksumFile.getName());
        }
        return null;
    }

    private boolean importFolderMetaData(File sourceFolder, RepoPath target) {
        boolean folderExistAfterImport = false;
        if (!GlobalExcludes.isInGlobalExcludes(sourceFolder)) {
            // Create the folder and import its the metadata
            MutableVfsFolder mutableFolder = null;
            String sourcePath = sourceFolder.getAbsolutePath();
            try {
                // First create the folder
                mutableFolder = repo.createOrGetFolder(target);
                //Read metadata into the node
                if (settings.isIncludeMetadata()) {
                    List<MetadataEntryInfo> metadataEntries =
                            getMetadataEntryInfos(sourceFolder);
                    FolderInfo folderInfoToImport = (FolderInfo) readItemInfoMetadata(
                            FolderInfo.ROOT, sourcePath, target, metadataEntries);
                    if (folderInfoToImport != null) {
                        mutableFolder.fillInfo(folderInfoToImport);
                    }

                    importPropertiesMetaData(sourcePath, mutableFolder, metadataEntries);
                    importWatchesMetaData(sourcePath, mutableFolder, metadataEntries);
                }
                folderExistAfterImport = true;
                progressAccumulator.accumulateSuccessfulFolder();
            } catch (Exception e) {
                // Just log an error and continue - will not import children
                String msg = "Failed to import folder " + sourcePath + " into '" + target + "'.";
                status.error(msg, e, log);
                if (mutableFolder != null) {
                    mutableFolder.markError();
                }
                progressAccumulator.accumulateSkippedFolder();
            }
        } else {
            progressAccumulator.accumulateSkippedFolder();
        }
        return folderExistAfterImport;
    }

    private void importPropertiesMetaData(String sourceFile, MutableVfsItem mutableItem,
            List<MetadataEntryInfo> metadataEntries) {
        PropertiesInfo propertiesInfo = (PropertiesInfo) readItemInfoMetadata(
                PropertiesInfo.ROOT, sourceFile, mutableItem.getRepoPath(), metadataEntries);
        if (propertiesInfo != null) {
            mutableItem.setProperties(new PropertiesImpl(propertiesInfo));
        }
    }

    private void importWatchesMetaData(String sourceFile, MutableVfsItem mutableItem,
            List<MetadataEntryInfo> metadataEntries) {
        WatchersInfo watchersInfo = (WatchersInfo) readItemInfoMetadata(
                WatchersInfo.ROOT, sourceFile, mutableItem.getRepoPath(), metadataEntries);
        if (watchersInfo != null) {
            for (WatcherInfo watcherInfo : watchersInfo.getWatchers()) {
                mutableItem.addWatch(watcherInfo);
            }
        }
    }

    private void importStatsMetaData(String sourceFile, MutableVfsFile mutableFile,
            List<MetadataEntryInfo> metadataEntries) {
        MutableStatsInfo statsInfo = (MutableStatsInfo) readItemInfoMetadata(
                StatsInfo.ROOT, sourceFile, mutableFile.getRepoPath(), metadataEntries);
        if (statsInfo != null) {
            StatsInfo existingStats = statsService.getStats(mutableFile.getRepoPath());
            statsInfo.merge(existingStats);
            mutableFile.forceUpdateStats();
            mutableFile.setStats(statsInfo);
        }
    }

    private MetadataReader findBestMatchMetadataReader(ImportSettings importSettings, File metadataFolder) {
        ImportSettingsImpl settings = (ImportSettingsImpl) importSettings;
        MetadataReader metadataReader = settings.getMetadataReader();
        if (metadataReader == null) {
            metadataReader = new MetadataReaderImpl();
            settings.setMetadataReader(metadataReader);
        }
        return metadataReader;
    }

    private String getCurrentUsername() {
        return InternalContextHelper.get().beanForType(SecurityService.class).currentUsername();
    }

    private void startTransaction() {
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        AbstractPlatformTransactionManager txManager = getTransactionManager();
        transactionStartTime = DateTime.now();
        this.transactionStatus = txManager.getTransaction(def);
    }

    private void commitTransaction(TransactionStatus status) {
        log.debug("{}: Committing transaction artifacts count: {} after {} seconds", repo.getKey(),
                progressAccumulator.getSuccessfulItemsCount(),
                new Duration(transactionStartTime, DateTime.now()).getStandardSeconds());
        getTransactionManager().commit(status);
    }

    private AbstractPlatformTransactionManager getTransactionManager() {
        return ContextHelper.get().beanForType("artifactoryTransactionManager",
                AbstractPlatformTransactionManager.class);
    }

    /**
     * Represents an importable item of source (file) and destination (repo path)
     */
    private record ImportItem(File source, RepoPath parent, String child) {
    }

}
