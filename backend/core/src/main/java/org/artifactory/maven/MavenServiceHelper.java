package org.artifactory.maven;

import com.google.common.base.Charsets;
import org.apache.commons.io.IOUtils;
import org.artifactory.api.maven.MavenArtifactInfo;
import org.artifactory.api.request.InternalArtifactoryRequest;
import org.artifactory.api.request.InternalRequestFactory;
import org.artifactory.common.status.StatusHolder;
import org.artifactory.fs.RepoResource;
import org.artifactory.mime.NamingUtils;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.request.DownloadRequestContext;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.exception.http.NotFoundException;
import org.artifactory.rest.exception.RestException;
import org.artifactory.sapi.fs.VfsFile;
import org.artifactory.storage.io.InputStreamSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.UnsupportedCharsetException;

@Component
public class MavenServiceHelper {

    private static final Logger log = LoggerFactory.getLogger(MavenServiceHelper.class);

    private final InternalRepositoryService repositoryService;

    @Autowired
    public MavenServiceHelper(InternalRepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    public void generateAndDeployPom(String repoKey, String pathInRepo) {
        LocalRepo localRepo = getLocalRepo(repoKey);
        RepoPath jarRepoPath = getJarRepoPath(repoKey, pathInRepo);
        RepoResource repoResource = getRepoResource(repoKey, pathInRepo, localRepo, jarRepoPath);
        RepoPath uniqueRepoPath = repoResource
                .getRepoPath(); // unique even in case of multiple SNAPSHOT jars for same version
        VfsFile jarVfsFile = getJarVfsFile(repoKey, pathInRepo, localRepo, uniqueRepoPath);
        generateAndDeployPomFromJar(jarVfsFile);
    }

    private VfsFile getJarVfsFile(String repoKey, String pathInRepo, LocalRepo localRepo, RepoPath uniqueRepoPath) {
        VfsFile jarVfsFile = localRepo.getImmutableFile(uniqueRepoPath);
        if (jarVfsFile == null) {
            throw new NotFoundException(
                    "Unable to find file '" + pathInRepo + "' in local repository '" + repoKey + "'");
        }
        return jarVfsFile;
    }

    private RepoResource getRepoResource(String repoKey, String pathInRepo, LocalRepo localRepo, RepoPath jarRepoPath) {
        RepoResource repoResource = getRepoResource(jarRepoPath, localRepo);
        if (!repoResource.isFound()) {
            throw new NotFoundException(
                    "Unable to find file '" + pathInRepo + "' in local repository '" + repoKey + "'");
        }
        if (repoResource.isBlocked()) {
            throw new BadRequestException("'" + pathInRepo + "' of repo '" + repoKey + "' is blocked");
        }
        return repoResource;
    }

    private RepoPath getJarRepoPath(String repoKey, String pathInRepo) {
        if (!NamingUtils.isJarVariant(pathInRepo)) {
            throw new BadRequestException("'" + pathInRepo + "' is not a JAR, WAR or EAR file");
        }
        return new RepoPathImpl(repoKey, pathInRepo);
    }

    private LocalRepo getLocalRepo(String repoKey) {
        LocalRepo localRepo = repositoryService.localOrFederatedRepositoryByKey(repoKey);
        if (localRepo == null) {
            throw new NotFoundException("Unable to find local repository '" + repoKey + "'");
        }
        if (!localRepo.getDescriptor().getType().isMavenGroup()) {
            throw new BadRequestException("Repository '" + repoKey + "' is not a maven repository");
        }
        return localRepo;
    }

    private RepoResource getRepoResource(RepoPath repoPath, LocalRepo localRepo) {
        InternalArtifactoryRequest artifactoryRequest = InternalRequestFactory
                .createInternalRequestDisableRedirect(repoPath);
        DownloadRequestContext requestContext = new DownloadRequestContext(artifactoryRequest);
        return localRepo.getInfo(requestContext);
    }

    private void generateAndDeployPomFromJar(VfsFile jarVfsFile) {
        InputStreamSupplier inputStreamSupplier = InputStreamSupplier.of(jarVfsFile);
        PomResult pomResult = MavenModelUtils.getOrGeneratePomModelFromJarVariant(inputStreamSupplier);
        MavenArtifactInfo mavenArtifactInfo = MavenModelUtils.mavenModelToArtifactInfo(pomResult.getPomAsModel());
        RepoPath jarRepoPath = jarVfsFile.getRepoPath();
        RepoPath pomPath = InternalRepoPathFactory
                .create(jarRepoPath.getParent(), MavenModelUtils.getPomFileName(mavenArtifactInfo));
        InputStream inputStream;
        try {
            inputStream = IOUtils.toInputStream(pomResult.getPomAsString(), Charsets.UTF_8.name());
        } catch (UnsupportedCharsetException e) {
            String message =
                    String.format("Failed to generate pom for '%s' in local repository '%s'", jarRepoPath.getPath(),
                            jarRepoPath.getRepoKey());
            log.debug(message, e);
            throw new RestException(message);
        }
        StatusHolder statusHolder = repositoryService.deploy(pomPath, inputStream);
        throwIfError(statusHolder);
    }

    public static void throwIfError(StatusHolder statusHolder) {
        if (statusHolder.isError()) {
            throw new RestException(statusHolder.getStatusCode(), statusHolder.getStatusMsg());
        }
    }
}
