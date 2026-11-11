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

package org.artifactory.repo.interceptor;

import ch.qos.logback.classic.Level;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;
import org.artifactory.api.module.ModuleInfo;
import org.artifactory.api.properties.PropertiesService;
import org.artifactory.api.repo.exception.maven.BadPomException;
import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.exception.CancelException;
import org.artifactory.fs.ItemInfo;
import org.artifactory.maven.PomTargetPathValidator;
import org.artifactory.md.Properties;
import org.artifactory.metadata.service.indexer.MetadataServiceIndexHandler;
import org.artifactory.mime.MavenNaming;
import org.artifactory.mime.NamingUtils;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.StoringRepo;
import org.artifactory.repo.interceptor.storage.StorageInterceptorAdapter;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.sapi.fs.VfsFile;
import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.sapi.interceptor.CopyMoveCheckResult;
import org.artifactory.sapi.interceptor.ImportInterceptor;
import org.artifactory.sapi.interceptor.context.InterceptorCreateContext;
import org.artifactory.sapi.interceptor.context.InterceptorMoveCopyContext;
import org.artifactory.storage.fs.MutableVfsFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

import static org.artifactory.sapi.interceptor.CopyMoveCheckResult.ALLOW;
import static org.artifactory.sapi.interceptor.CopyMoveCheckResult.REJECT;

/**
 * An interceptor that checks the validity of POM files and if it represents a maven plugin.
 *
 * @author Yossi Shaul
 */
public class MavenPomInterceptor extends StorageInterceptorAdapter implements ImportInterceptor {
    private static final Logger log = LoggerFactory.getLogger(MavenPomInterceptor.class);

    private InternalRepositoryService repositoryService;
    private MetadataServiceIndexHandler metadataServiceIndexHandler;

    @Autowired
    public void setRepositoryService(InternalRepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    @Autowired
    public void setMetadataServiceIndexHandler(MetadataServiceIndexHandler metadataServiceIndexHandler) {
        this.metadataServiceIndexHandler = metadataServiceIndexHandler;
    }

    @Override
    public void afterCreate(VfsItem fsItem, MutableStatusHolder statusHolder, InterceptorCreateContext ctx) {
        if (!fsItem.isFile()) {
            return;
        }
        VfsFile fsFile = (VfsFile) fsItem;
        if (MavenNaming.isPom(fsItem.getName())) {
            String repoKey = fsItem.getRepoKey();
            StoringRepo storingRepo = repositoryService.storingRepositoryByKey(repoKey);
            LocalRepoDescriptor descriptor = repositoryService.localFederatedCachedRepoDescriptorByKey(repoKey);
            if (!isStoringRepoReal(storingRepo) || !repoIsMaven(descriptor)) {
                return;
            }

            boolean suppressPomConsistencyChecks = descriptor.isSuppressPomConsistencyChecks();
            ModuleInfo moduleInfo = storingRepo.getItemModuleInfo(fsItem.getPath());
            PomTargetPathValidator pomValidator = new PomTargetPathValidator(fsItem.getPath(), moduleInfo);
            InputStream is = fsFile.getStream();
            try {
                pomValidator.validate(is, suppressPomConsistencyChecks);
            } catch (BadPomException e) {
                throw new RuntimeException("Failed to validate pom file: " + e.getMessage(), e);
            } catch (EOFException e) {
                throw new CancelException(
                        String.format("Failed to parse pom file: %s. The file might be corrupted or empty: %s",
                                fsItem.getRepoPath(), e.getMessage()),
                        e, HttpStatus.SC_BAD_REQUEST);
            } catch (IOException e) {
                throw new RuntimeException("Failed to parse pom file: " + e.getMessage(), e);
            } finally {
                IOUtils.closeQuietly(is);
            }

            if (pomValidator.isMavenPlugin()) {
                log.debug("Marking {} as maven plugin", fsItem.getRepoPath());
                MutableVfsFile mutableFile = storingRepo.getMutableFile(fsItem.getRepoPath());
                Properties properties = mutableFile.getProperties();
                properties.put(PropertiesService.MAVEN_PLUGIN_PROPERTY_NAME, Boolean.toString(true));
                mutableFile.setProperties(properties);
            }
            // valid pom created - index it
            metadataServiceIndexHandler.indexPackageAsync(fsFile.getInfo(), fsFile.getProperties());
        }
    }

    @Override
    public CopyMoveCheckResult checkCopyMove(VfsItem sourceItem, RepoPath targetRepoPath, MutableStatusHolder status) {
        String targetRepoKey = targetRepoPath.getRepoKey();
        StoringRepo targetRepo = repositoryService.storingRepositoryByKey(targetRepoKey);
        if (targetRepo == null) {
            log.debug("Could not find storing repository named '{}'", targetRepoKey);
            return REJECT;
        }
        String targetPath = targetRepoPath.getPath();
        if ((targetRepo instanceof LocalRepo) &&
                shouldValidatePomTransfer(sourceItem, (LocalRepo) targetRepo, targetPath)) {
            ModuleInfo moduleInfo = targetRepo.getItemModuleInfo(targetPath);
            try (InputStream resourceStream = ((VfsFile) sourceItem).getStream()) {
                new PomTargetPathValidator(targetPath, moduleInfo).validate(resourceStream, false);
            } catch (BadPomException e) {
                status.errorWithStatusEntryWithDifferentLogLevel("Failed to validate pom: " + e.getMessage(),
                        HttpStatus.SC_BAD_REQUEST, e, log, Level.WARN);
                return REJECT;
            } catch (Exception e) {
                status.errorWithDifferentLogLevel("Failed to validate target path of pom: " + targetPath,
                        HttpStatus.SC_BAD_REQUEST, e, log, Level.WARN);
                return REJECT;
            }
        }
        return ALLOW;
    }

    @Override
    public void afterMove(VfsItem<? extends ItemInfo> sourceItem, VfsItem<? extends ItemInfo> targetItem,
                          MutableStatusHolder statusHolder, Properties properties, InterceptorMoveCopyContext ctx) {
        indexAsPackageCreatedIfNeeded(targetItem);
    }

    @Override
    public void afterCopy(VfsItem<? extends ItemInfo> sourceItem, VfsItem<? extends ItemInfo> targetItem,
                          MutableStatusHolder statusHolder, Properties properties, InterceptorMoveCopyContext ctx) {
        indexAsPackageCreatedIfNeeded(targetItem);
    }

    private void indexAsPackageCreatedIfNeeded(VfsItem<? extends ItemInfo> targetItem) {
        if (MavenNaming.isPom(targetItem.getName())) {
            String repoKey = targetItem.getRepoKey();
            StoringRepo storingRepo = repositoryService.storingRepositoryByKey(repoKey);
            LocalRepoDescriptor descriptor = repositoryService.localFederatedCachedRepoDescriptorByKey(repoKey);
            if (isStoringRepoReal(storingRepo) && repoIsMaven(descriptor)) {
                // valid pom copied or moved - index it
                metadataServiceIndexHandler.indexPackageAsync(targetItem.getInfo(), targetItem.getProperties());
            }
        }
    }

    @Override
    public void afterImport(VfsItem fsItem, MutableStatusHolder statusHolder) {
        afterCreate(fsItem, statusHolder, new InterceptorCreateContext());
    }

    private boolean shouldValidatePomTransfer(VfsItem sourceItem, LocalRepo targetRepo, String targetPath) {
        if (targetRepo != null) {
            LocalRepoDescriptor targetDescriptor = (LocalRepoDescriptor) targetRepo.getDescriptor();
            return repoIsMaven(targetDescriptor) && sourceItem.isFile()
                    && NamingUtils.isPom(sourceItem.getRepoPath().getPath()) && NamingUtils.isPom(targetPath)
                    && !targetDescriptor.isSuppressPomConsistencyChecks();
        }
        return false;
    }

    private boolean isStoringRepoReal(StoringRepo storingRepo) {
        return storingRepo != null && storingRepo.isReal();
    }

    private boolean repoIsMaven(LocalRepoDescriptor descriptor) {
        return descriptor != null && descriptor.getType().isMavenGroup();
    }
}
