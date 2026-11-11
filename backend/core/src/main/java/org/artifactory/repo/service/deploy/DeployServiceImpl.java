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

package org.artifactory.repo.service.deploy;

import ch.qos.logback.classic.Level;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.jfrog.build.acl.bridge.BuildFacade;
import com.jfrog.build.acl.model.BuildRepoPath;
import jakarta.annotation.Nonnull;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.AbstractFileFilter;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.http.HttpHeaders;
import org.artifactory.api.artifact.ArtifactInfo;
import org.artifactory.api.artifact.UnitInfo;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.maven.MavenArtifactInfo;
import org.artifactory.api.maven.MavenService;
import org.artifactory.api.module.ModuleInfo;
import org.artifactory.api.repo.DeployService;
import org.artifactory.api.repo.exception.RepoRejectException;
import org.artifactory.api.request.ArtifactoryResponse;
import org.artifactory.api.request.UploadService;
import org.artifactory.api.rest.artifact.RestFileInfo;
import org.artifactory.common.ConstantValues;
import org.artifactory.concurrent.ArtifactoryExecutor;
import org.artifactory.concurrent.ArtifactoryExecutorFactory;
import org.artifactory.descriptor.repo.RealRepoDescriptor;
import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.descriptor.repo.VirtualRepoDescriptor;
import org.artifactory.maven.MavenModelUtils;
import org.artifactory.md.Properties;
import org.artifactory.mime.MavenNaming;
import org.artifactory.mime.NamingUtils;
import org.artifactory.model.RealRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.Repo;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.packagetype.configuration.MavenConfiguration;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.repo.virtual.VirtualRepo;
import org.artifactory.request.ArtifactoryRequest;
import org.artifactory.request.BuildArtifactoryRequest;
import org.artifactory.request.InternalCapturingResponse;
import org.artifactory.sapi.common.RepositoryRuntimeException;
import org.artifactory.spring.InternalContextHelper;
import org.artifactory.util.GlobalExcludes;
import org.artifactory.util.MavenPackageTypeConfigMapper;
import org.artifactory.util.ZipUtils;
import org.jfrog.build.api.Build;
import org.jfrog.client.util.PathUtils;
import org.jfrog.common.JsonParsingException;
import org.jfrog.common.JsonUtils;
import org.jfrog.storage.binstore.exceptions.BinaryRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.artifactory.common.ConstantValues.explodeArchiveThreads;
import static org.artifactory.exception.factory.ExceptionFactory.badRequestException;
import static org.artifactory.exception.factory.ExceptionFactory.conflictException;
import static com.jfrog.build.acl.util.BuildInfoUtils.generateBuildJsonRepoPath;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.jfrog.client.util.PathUtils.normalizeSlashes;

/**
 * Provides artifacts deploy services from the UI.
 *
 * @author Yossi Shaul
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeployServiceImpl implements DeployService {

    private final InternalRepositoryService repositoryService;
    private final UploadService uploadService;
    private final MavenService mavenService;

    @Override
    public void deploy(RepoDescriptor targetRepo, UnitInfo artifactInfo, File file, Properties properties)
            throws RepoRejectException {
        String pomString = mavenService.getPomModelString(file);
        deploy(targetRepo, artifactInfo, file, pomString, false, false, properties);
    }

    @Override
    public void deploy(RepoDescriptor targetRepo, UnitInfo artifactInfo, File fileToDeploy, String pomString,
            boolean forceDeployPom, boolean partOfBundleDeploy, Properties properties) throws RepoRejectException {
        String path = artifactInfo.getPath();
        if (!artifactInfo.isValid()) {
            throw new IllegalArgumentException("Invalid unit info for '" + path + "'.");
        }

        //Sanity check
        if (targetRepo == null) {
            throw new IllegalArgumentException("No target repository selected for deployment.");
        }

        String targetRepoKey = targetRepo.getKey();
        if (targetRepo instanceof VirtualRepoDescriptor) {
            final VirtualRepo virtualRepo = repositoryService.virtualRepositoryByKey(targetRepoKey);
            if (virtualRepo == null || virtualRepo.getDescriptor().getDefaultDeploymentRepo() == null) {
                throw new IllegalArgumentException("No target repository found for deployment.");
            }
        } else {
            final LocalRepo localRepo = repositoryService.localOrFederatedRepositoryByKey(targetRepoKey);
            if (localRepo == null) {
                throw new IllegalArgumentException("No target repository found for deployment.");
            }
        }

        RepoPath repoPath = InternalRepoPathFactory.create(targetRepoKey, normalizeSlashes(path).toString());

        // upload the main file
        InternalCapturingResponse response = new InternalCapturingResponse();
        boolean isBuildDeployRequest = RepoType.BuildInfo.equals(targetRepo.getType());
        try {
            ArtifactoryDeployRequest request = new ArtifactoryDeployRequestBuilder(repoPath)
                    .fileToDeploy(fileToDeploy)
                    .properties(properties)
                    .trustServerChecksums(true)
                    .isBuildDeployRequest(isBuildDeployRequest)
                    .build();
            if (isBuildDeployRequest) {
                handleBuildUploadRedirect((BuildArtifactoryRequest) request, response, targetRepoKey);
            } else {
                request.setSkipJarIndexing(partOfBundleDeploy);
                uploadService.upload(request, response);
            }
            validateResponseAndAlterPathIfNeeded(fileToDeploy.getName(), response, artifactInfo);
        } catch (IOException e) {
            handleIoException(e, fileToDeploy.getName());
        }

        //Handle extra pom deployment - add the metadata with the generated pom file to the artifact
        if (forceDeployPom && artifactInfo.isMavenArtifact() && StringUtils.isNotBlank(pomString)) {
            MavenArtifactInfo mavenArtifactInfo = (MavenArtifactInfo) artifactInfo;
            String pomFileName = MavenModelUtils.getPomFileName(mavenArtifactInfo);
            RepoPath pomPath = InternalRepoPathFactory.create(repoPath.getParent(), pomFileName);
            RepoPath uploadPomPath = InternalRepoPathFactory.create(targetRepoKey, pomPath.getPath());
            try {
                ArtifactoryDeployRequest pomRequest = new ArtifactoryDeployRequestBuilder(uploadPomPath)
                        .inputStream(IOUtils.toInputStream(pomString, Charsets.UTF_8.name()))
                        .contentLength(pomString.getBytes().length)
                        .lastModified(fileToDeploy.lastModified())
                        .properties(properties)
                        .trustServerChecksums(true)
                        .build();
                InternalCapturingResponse pomResponse = new InternalCapturingResponse();
                // upload the POM if needed
                uploadService.upload(pomRequest, pomResponse);
                validateResponseAndAlterPathIfNeeded(fileToDeploy.getName(), pomResponse, artifactInfo);
            } catch (IOException e) {
                handleIoException(e, pomPath.getName());
            }
        }
    }

    @Override
    public void deploy(RepoConfig targetRepoConfig, UnitInfo artifactInfo, File fileToDeploy, String pomString,
            boolean forceDeployPom, boolean partOfBundleDeploy, Properties properties) throws RepoRejectException {
        String path = artifactInfo.getPath();
        if (!artifactInfo.isValid()) {
            throw new IllegalArgumentException("Invalid unit info for '" + path + "'.");
        }

        //Sanity check
        if (targetRepoConfig == null) {
            throw new IllegalArgumentException("No target repository selected for deployment.");
        }

        String targetRepoKey = targetRepoConfig.getKey();
        if (RepoDetailsType.VIRTUAL.equals(targetRepoConfig.getRepoType())) {
            final VirtualRepo virtualRepo = repositoryService.virtualRepositoryByKey(targetRepoKey);
            if (virtualRepo == null) {
                throw new IllegalArgumentException("No target repository found for deployment.");
            } else {
                String defaultDeploymentRepoRef =
                        virtualRepo.getRepoConfig().getRepoTypeConfig().getDefaultDeploymentRepoRef();
                if (repositoryService.getRepositoryConfigByKey(defaultDeploymentRepoRef) == null) {
                    if (defaultDeploymentRepoRef != null) {
                        log.debug("Default deployment repository reference {} in repository {} doesn't exist",
                                defaultDeploymentRepoRef, virtualRepo.getKey());
                    }
                    throw new IllegalArgumentException("No target repository found for deployment.");
                }
            }
        } else {
            final LocalRepo localRepo = repositoryService.localOrFederatedRepositoryByKey(targetRepoKey);
            if (localRepo == null) {
                throw new IllegalArgumentException("No target repository found for deployment.");
            }
        }

        RepoPath repoPath = InternalRepoPathFactory.create(targetRepoKey, path);

        // upload the main file
        InternalCapturingResponse response = new InternalCapturingResponse();
        boolean isBuildDeployRequest = RepoType.BuildInfo.equals(targetRepoConfig.getPackageType());
        try {
            ArtifactoryDeployRequest request = new ArtifactoryDeployRequestBuilder(repoPath)
                    .fileToDeploy(fileToDeploy)
                    .properties(properties)
                    .trustServerChecksums(true)
                    .isBuildDeployRequest(isBuildDeployRequest)
                    .build();
            if (isBuildDeployRequest) {
                handleBuildUploadRedirect((BuildArtifactoryRequest) request, response, targetRepoKey);
            } else {
                request.setSkipJarIndexing(partOfBundleDeploy);
                uploadService.upload(request, response);
            }
            validateResponseAndAlterPathIfNeeded(fileToDeploy.getName(), response, artifactInfo);
        } catch (IOException e) {
            handleIoException(e, fileToDeploy.getName());
        }

        //Handle extra pom deployment - add the metadata with the generated pom file to the artifact
        if (forceDeployPom && artifactInfo.isMavenArtifact() && StringUtils.isNotBlank(pomString)) {
            MavenArtifactInfo mavenArtifactInfo = (MavenArtifactInfo) artifactInfo;
            String pomFileName = MavenModelUtils.getPomFileName(mavenArtifactInfo);
            RepoPath pomPath = InternalRepoPathFactory.create(repoPath.getParent(), pomFileName);
            RepoPath uploadPomPath = InternalRepoPathFactory.create(targetRepoKey, pomPath.getPath());
            try {
                ArtifactoryDeployRequest pomRequest = new ArtifactoryDeployRequestBuilder(uploadPomPath)
                        .inputStream(IOUtils.toInputStream(pomString, StandardCharsets.UTF_8))
                        .contentLength(pomString.getBytes().length)
                        .lastModified(fileToDeploy.lastModified())
                        .properties(properties)
                        .trustServerChecksums(true)
                        .build();
                InternalCapturingResponse pomResponse = new InternalCapturingResponse();
                // upload the POM if needed
                uploadService.upload(pomRequest, pomResponse);
                validateResponseAndAlterPathIfNeeded(fileToDeploy.getName(), pomResponse, artifactInfo);
            } catch (IOException e) {
                handleIoException(e, pomPath.getName());
            }
        }
    }

    private void handleIoException(IOException e, String name) {
        String msg = String.format("Cannot deploy file %s", name);
        log.debug(msg, e);
        throw new RepositoryRuntimeException(msg, e);
    }

    private void validateResponseAndAlterPathIfNeeded(String deployedFileName, InternalCapturingResponse response,
            UnitInfo artifactInfo) throws RepoRejectException {
        if (!response.isSuccessful()) {
            StringBuilder errorMessageBuilder = new StringBuilder("Cannot deploy file '").append(deployedFileName).
                    append("'. ");
            String statusMessage = response.getStatusMessage();
            if (StringUtils.isNotBlank(statusMessage)) {
                errorMessageBuilder.append(statusMessage);
                if (!Strings.CS.endsWith(statusMessage, ".")) {
                    errorMessageBuilder.append(".");
                }
            } else {
                errorMessageBuilder.append("Please view the logs for further information.");
            }
            throw new RepoRejectException(errorMessageBuilder.toString());
        } else {
            alterResponsePath(response, artifactInfo);
        }
    }

    /**
     * Checks for deployment final path change (due to move during the deploy interceptors)
     * and update the response path accordingly.
     *
     * @param response     of the deployment
     * @param artifactInfo the model we return to the client, with altered path if needed
     */
    private void alterResponsePath(InternalCapturingResponse response, UnitInfo artifactInfo) {
        String resultAsString = response.getResultAsString();
        if (resultAsString == null) {
            log.debug("Deployment response is empty, cannot check for alternate path");
            return;
        }
        try {
            RestFileInfo responseStorageInfo = JsonUtils.getInstance().readValue(resultAsString, RestFileInfo.class);
            String newPath = responseStorageInfo != null ? PathUtils.trimLeadingSlashes(responseStorageInfo.path) : "";
            if (StringUtils.isNotBlank(newPath)
                    && !StringUtils.equals(newPath, artifactInfo.getPath())) {
                artifactInfo.setPath(newPath);
                log.debug("Changed deployment path to alternate path {}", newPath);
            }
        } catch (JsonParsingException e) {
            log.error("Couldn't parse deployment response, cannot check for alternate path", e);
        }
    }

    @Override
    public void deployBundle(File bundle, RealRepoDescriptor targetRepo, BasicStatusHolder status, boolean failFast) {
        deployBundle(bundle, targetRepo, status, failFast, "", null);
    }

    @Override
    public void deployBundle(File bundle, RealRepoDescriptor targetRepo, final BasicStatusHolder status,
            boolean failFast, @Nonnull String prefix, Properties properties) {
        deployBundle(bundle, (RealRepoConfig) targetRepo.toRepositoryConfig(), status, failFast, prefix, properties);
    }

    @Override
    public void deployBundleAtomic(File bundle, RealRepoDescriptor targetRepo, BasicStatusHolder status,
            boolean failFast, @Nonnull String prefix, Properties properties) {
        deployBundle(bundle, (RealRepoConfig) targetRepo.toRepositoryConfig(), status, failFast, prefix, properties);
    }

    @Override
    public void deployBundleAtomic(File bundle, RealRepoConfig targetRepo, final BasicStatusHolder status,
            boolean failFast, @Nonnull String prefix, Properties properties) {
        deployBundle(bundle, targetRepo, status, failFast, prefix, properties);
    }

    @Override
    public void deployBundle(File bundle, RealRepoConfig targetRepo, BasicStatusHolder status,
            boolean failFast, @Nonnull String prefix, Properties properties) {
        long start = System.currentTimeMillis();
        if (!bundle.exists()) {
            String message = "Specified location '" + bundle + "' does not exist. Deployment aborted.";
            status.errorWithDifferentLogLevel(message, SC_BAD_REQUEST, null, log, Level.WARN);
            return;
        }
        File extractFolder = getExtractedArchiveFolder(bundle, status);
        if (extractFolder == null) {
            //We have errors
            return;
        }
        try {
            List<File> archiveContent = getDeployableFiles(status, extractFolder);
            Repo repo = repositoryService.repositoryByKey(targetRepo.getKey());
            if (repo == null) {
                log.error("No such repo {} to deploy to.", targetRepo.getKey());
                return;
            }
            if (explodeArchiveThreads.getInt() > 1) {
                doParallelDeploy(targetRepo, status, failFast, prefix, properties, archiveContent, extractFolder, repo);
            } else {
                for (File file : archiveContent) {
                    String relPath = getFileRelativePath(prefix, extractFolder, file);
                    if (isModuleValid(repo, file, relPath, targetRepo, status)) {
                        deployFile(targetRepo, status, properties, file, relPath);
                    }
                    if (status.hasErrors() && failFast) {
                        return;
                    }
                }
            }
            logAction(bundle, status, start, archiveContent);
        } catch (Exception e) {
            status.error(e.getMessage(), e, log);
        } finally {
            FileUtils.deleteQuietly(extractFolder);
        }
    }

    private void doParallelDeploy(RealRepoConfig targetRepo, BasicStatusHolder status, boolean failFast,
            @NonNull String prefix, Properties properties, List<File> archiveContent, File extractFolder, Repo repo) {
        ExecutorService executor = createExplodeExecutor();
        try {
            List<Callable<Void>> checksums = new ArrayList<>();
            List<Callable<Void>> nonChecksums = new ArrayList<>();

            for (File file : archiveContent) {
                String relPath = getFileRelativePath(prefix, extractFolder, file);
                if (isModuleValid(repo, file, relPath, targetRepo, status)) {
                    populateInvocationLists(targetRepo, status, failFast, properties, file, relPath, checksums,
                            nonChecksums);
                }
            }
            List<Future<Void>> nonChecksumFutures = executor.invokeAll(nonChecksums);
            unwrapCallablesExceptions(nonChecksumFutures);
            log.debug("Finished deploying {} non-checksum bundle parts", nonChecksums.size());
            List<Future<Void>> checksumFutures = executor.invokeAll(checksums);
            unwrapCallablesExceptions(checksumFutures);
            log.debug("Finished deploying {} checksum bundle parts", checksums.size());

            executor.shutdown();
            if (!executor.awaitTermination(ConstantValues.explodeArchiveTimeoutMinutes.getInt(), TimeUnit.MINUTES)) {
                status.error("Timeout uploading bundle parts", log);
            }
        } catch (InterruptedException e) {
            status.error("Interrupted while uploading bundle parts", e, log);
            Thread.currentThread().interrupt();
        } finally {
            if (!executor.isShutdown()) {
                executor.shutdown();
            }
        }
    }

    private static void unwrapCallablesExceptions(List<Future<Void>> futures) {
        futures.forEach(f -> {
            try {
                f.get();
            } catch (InterruptedException e) {
                log.error("Interrupted while checking output of completed bundle parts. Shouldn't happen", e);
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                if (e.getCause() instanceof RuntimeException re) {
                    throw re;
                }
            }
        });
    }

    @NonNull
    @VisibleForTesting
    protected ArtifactoryExecutor createExplodeExecutor() {
        return ArtifactoryExecutorFactory.newArtifactoryExecutor(
                Executors.newFixedThreadPool(explodeArchiveThreads.getInt(),
                        new ThreadFactoryBuilder().setNameFormat("explode-bundle-%s").build()),
                ContextHelper.get(), true);
    }

    private void populateInvocationLists(RealRepoConfig targetRepo, BasicStatusHolder status, boolean failFast,
            Properties properties,
            File file, String relPath, List<Callable<Void>> checksums,
            List<Callable<Void>> nonChecksums) {
        Callable<Void> callable = () -> {
            if (status.hasErrors() && failFast) {
                log.debug("Error after deploying {} to repo {}", relPath, targetRepo);
                return null;
            }
            deployFile(targetRepo, status, properties, file, relPath);
            return null;
        };
        if (NamingUtils.isChecksum(relPath)) {
            checksums.add(callable);
        } else {
            nonChecksums.add(callable);
        }
    }

    private String getFileRelativePath(@Nonnull String prefix, File extractFolder, File file) {
        String parentPath = extractFolder.getAbsolutePath();
        String filePath = file.getAbsolutePath();
        return PathUtils.trimSlashes(prefix + "/" + PathUtils.getRelativePath(parentPath, filePath));
    }

    private File getExtractedArchiveFolder(File bundle, BasicStatusHolder status) {
        try {
            return extractArchive(status, bundle);
        } catch (Exception e) {
            status.error(e.getLocalizedMessage(), e, log);
            throw new RestClientException(e.getMessage(), e);
        }
    }

    private List<File> getDeployableFiles(final BasicStatusHolder status, File extractFolder) {
        IOFileFilter deployableFilesFilter = new AbstractFileFilter() {
            @Override
            public boolean accept(File file) {
                if (NamingUtils.isSystem(file.getAbsolutePath()) || GlobalExcludes.isInGlobalExcludes(file) ||
                        file.getName().contains(MavenNaming.MAVEN_METADATA_NAME)) {
                    status.debug("Excluding '" + file.getAbsolutePath() + "' from bundle deployment.", log);
                    return false;
                }

                return true;
            }
        };
        List<File> archiveContent = Lists.newArrayList(FileUtils.listFiles(extractFolder, deployableFilesFilter,
                DirectoryFileFilter.DIRECTORY));
        Collections.sort(archiveContent);
        return archiveContent;
    }

    private boolean isModuleValid(Repo repo, File file, String relPath, RealRepoConfig targetRepo,
            BasicStatusHolder status) {
        ModuleInfo moduleInfo = repo.getItemModuleInfo(relPath);
        if (MavenNaming.isPom(file.getName())) {
            try {
                MavenConfiguration mavenConfiguration =
                        MavenPackageTypeConfigMapper.INSTANCE.map(targetRepo.getPackageTypeConfig());
                mavenService.validatePomFile(file, relPath, moduleInfo,
                        mavenConfiguration.isSuppressPomConsistencyChecks());
            } catch (Exception e) {
                String msg = "The pom: " + file.getName() + " could not be validated, and thus was not deployed.";
                status.error(msg, e, log);
                return false;
            }
        }
        return true;
    }

    private void deployFile(RealRepoConfig targetRepo, BasicStatusHolder status, Properties properties, File file,
            String relPath) {
        try {
            getTransactionalMe().deploy(targetRepo, new ArtifactInfo(relPath), file, null, false, true, properties);
        } catch (IllegalArgumentException iae) {
            status.error(iae.getMessage(), iae, log);
        } catch (Exception e) {
            status.error("Error during deployment: " + e.getMessage(), e, log);
        }
    }

    private void logAction(File bundle, BasicStatusHolder status, long start, List<File> archiveContent) {
        String bundleName = bundle.getName();
        String timeTaken = DurationFormatUtils.formatPeriod(start, System.currentTimeMillis(), "s");
        int archiveContentSize = archiveContent.size();
        String msg;
        if (status.hasErrors()) {
            msg = "Deployment of archive " + bundleName + " finished with errors.";
        } else if (status.hasWarnings()) {
            msg = "Deployment of archive " + bundleName + " finished with warnings, " + archiveContentSize
                    + " artifacts were deployed (" + timeTaken + " seconds).";
        } else {
            msg = "Successfully deployed " + archiveContentSize + " artifacts from archive: " + bundleName
                    + " (" + timeTaken + " seconds).";
        }
        status.status(msg, log);
    }

    private File extractArchive(BasicStatusHolder status, File archive) throws Exception {
        String archiveName = archive.getName();
        String fixedArchiveName = new String(archiveName.getBytes(Charsets.UTF_8.name()), Charsets.UTF_8);
        File fixedArchive = new File(archive.getParentFile(), fixedArchiveName);
        try {
            if (!fixedArchive.exists()) {
                FileUtils.moveFile(archive, fixedArchive);
            }
        } catch (IOException e) {
            throw new Exception("Could not encode archive name to UTF-8.", e);
        }
        File extractFolder = new File(ContextHelper.get().getArtifactoryHome().getTempUploadDir(),
                fixedArchive.getName() + "_extracted_" + System.currentTimeMillis());
        if (extractFolder.exists()) {
            //Clean up any existing folder
            try {
                FileUtils.deleteDirectory(extractFolder);
            } catch (IOException e) {
                status.error("Could not delete existing extracted archive folder: " +
                        extractFolder.getAbsolutePath() + ".", e, log);
                return null;
            }
        }
        try {
            FileUtils.forceMkdir(extractFolder);
        } catch (IOException e) {
            log.error("Could not created the extracted archive folder: " +
                    extractFolder.getAbsolutePath() + ".", log);
            return null;
        }

        try {
            ZipUtils.extract(fixedArchive, extractFolder);
        } catch (Exception e) {
            FileUtils.deleteQuietly(extractFolder);
            if (e.getMessage() == null) {
                String errorMessage;
                if (e instanceof IllegalArgumentException) {
                    errorMessage =
                            "Please make sure the textual values in the archive are encoded in UTF-8.";
                } else {
                    errorMessage = "Please ensure the integrity of the selected archive";
                }
                throw new Exception(errorMessage, e);
            }
            throw e;
        }
        return extractFolder;
    }

    @Override
    public void handleBuildUploadRedirect(
            @Nonnull ArtifactoryRequest request,
            @Nonnull ArtifactoryResponse response,
            @Nonnull String targetRepoKey) throws IOException {
        RepoPath repoPath = RepoPathFactory.create(targetRepoKey);
        try {
            modifyRequestForProperDeployment((BuildArtifactoryRequest) request, repoPath);
            uploadService.upload(request, response);
        } catch (IOException e) {
            if (!response.isCommitted()) {
                response.sendError(SC_BAD_REQUEST, e.getMessage(), log);
            } else {
                throw badRequestException(e.getMessage(), e);
            }
        } catch (RepoRejectException | BinaryRejectedException rej) {
            log.error("Deployment of build info failed: {}", rej.getMessage());
            log.debug("Deployment of build info failed", rej);
            if (!response.isCommitted()) {
                response.sendError(rej.getErrorCode(), rej.getMessage(), log);
            } else {
                throw conflictException(rej.getMessage());
            }
        }
    }

    private void modifyRequestForProperDeployment(
            @Nonnull BuildArtifactoryRequest request,
            @Nonnull RepoPath deploymentPath) {
        if (isReplicationRequest(request)) {
            String path = request.getRepoPath().getPath();
            // RepoFilter changes the request in case of replication we should handle the original request.
            String originalRequest = StringUtils.substringAfter(path, "build/buildUploadRedirect/");
            RepoPath repoPath = RepoPathFactory.create(originalRequest);
            request.setRepoPath(repoPath);
        } else {
            Build build = getBuildModelFrom(request);
            BuildFacade buildFacade = ContextHelper.get().beanForType(BuildFacade.class);
            buildFacade.preAddBuild(build, deploymentPath.getRepoKey());
            request.replaceContent(JsonUtils.getInstance().valueToByteArray(build));
            BuildRepoPath correctUploadPath = generateBuildJsonRepoPath(build, deploymentPath.getRepoKey());
            if (!correctUploadPath.toString().equals(request.getRepoPath().toPath())) {
                if (log.isDebugEnabled()) {
                    log.debug("Wrong path given for deployment of build {}:{}:{}, adjusting to path '{}'",
                            build.getName(), build.getNumber(), deploymentPath.getRepoKey(), correctUploadPath);
                }
                request.setRepoPath(RepoPathFactory.create(correctUploadPath.toString()));
            }
        }
    }

    private boolean isReplicationRequest(@Nonnull BuildArtifactoryRequest request) {
        Enumeration<String> headerValues = request.getHeaderValues(HttpHeaders.USER_AGENT);
        String userAgent = headerValues.hasMoreElements() ? headerValues.nextElement() : null;
        // In case the userAgent is Artifactory/ we can assume that the request is valid and not need to do modification
        return isNotBlank(userAgent) && Strings.CS.startsWith(userAgent, "Artifactory/");
    }

    private Build getBuildModelFrom(@Nonnull BuildArtifactoryRequest request) {
        try (InputStream in = request.getInputStream()) {
            return JsonUtils.getInstance().readValue(in, Build.class);
        } catch (Exception e) {
            String message = request.getRepoPath() + " could not be deployed. " +
                    "Build Info repositories only supports build-info.json files.";
            throw badRequestException(message, e);
        }
    }

    private static DeployService getTransactionalMe() {
        return InternalContextHelper.get().beanForType(DeployService.class);
    }
}
