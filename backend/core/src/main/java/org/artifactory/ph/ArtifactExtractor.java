package org.artifactory.ph;

import com.jfrog.ph.acl.exception.PackageException;
import com.jfrog.ph.acl.exception.PackageInternalServerError;
import com.jfrog.ph.acl.exception.PackageNotFoundException;
import com.jfrog.ph.acl.exception.PackageRepoNotFoundException;
import jakarta.validation.constraints.NotNull;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.repo.exception.BlackedOutException;
import org.artifactory.api.repo.exception.RepoRejectException;
import org.artifactory.api.repo.exception.SnapshotPolicyException;
import org.artifactory.fs.RepoResource;
import org.artifactory.ph.exception.PackageResourceExceptionUtil;
import org.artifactory.repo.Repo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.StoringRepo;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.request.DownloadRequestContext;
import org.artifactory.request.RemoteRequestException;
import org.artifactory.resource.ExpiredRepoResource;
import org.artifactory.resource.RemoteRepoResource;
import org.artifactory.resource.ResourceStreamHandle;
import org.artifactory.sapi.fs.VfsFile;

import java.io.IOException;

import static org.apache.http.HttpStatus.SC_CONFLICT;
import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.artifactory.ph.exception.PackageResourceExceptionUtil.isValidResource;

@UtilityClass
public class ArtifactExtractor {

    @NotNull
    public static VfsFile<?> locateFile(DownloadRequestContext context,
            InternalRepositoryService repositoryService, boolean throwIfExpire) throws PackageException {
        Repo<?, ?> repo = repositoryService.repositoryByKey(context.getRepoKey());
        if (repo == null) {
            throw repoNotFoundException(context.getRepoKey());
        }
        RepoResource info = repo.getInfo(context);
        return locateAndGetByInfo(info, context, throwIfExpire, repositoryService);
    }

    /**
     * This function return a stream from the given resource, and saving it in the cache repo (if exists)
     */
    public static ResourceStreamHandle getResourceStreamHandle(RepoResource info, DownloadRequestContext context,
            Repo<?, ?> repo, InternalRepositoryService repositoryService) throws PackageException {
        try {
            return repositoryService.getResourceStreamHandle(context, repo, info);
        } catch (SnapshotPolicyException e) {
            throw conflictException(info.getRepoKey(), info.getPath(), e.getMessage());
        } catch (BlackedOutException e) {
            throw blackedOutFoundException(info.getRepoKey(), info.getPath());
        } catch (RepoRejectException | RemoteRequestException e) {
            throw rejectException(info.getRepoKey(), info.getPath(), e.getMessage());
        } catch (IOException e) {
            throw internalError(info.getRepoKey(), info.getPath(), e);
        }
    }

    @NotNull
    public static VfsFile<?> locateAndGetByInfo(RepoResource resource, DownloadRequestContext context,
            boolean throwIfExpire, InternalRepositoryService repositoryService) throws PackageException {
        if (isValidResource(resource, throwIfExpire)) {
            if (resource instanceof RemoteRepoResource || resource instanceof ExpiredRepoResource) {
                String key = StringUtils.removeEnd(resource.getRepoKey(), RepoPath.REMOTE_CACHE_SUFFIX);
                Repo<?, ?> repo = repositoryService.remoteRepositoryByKey(key);
                if (repo == null) {
                    throw repoNotFoundException(key);
                }
                getResourceStreamHandle(resource, context, repo, repositoryService);
            }
            VfsFile<?> file = locateFileInternal(resource, repositoryService);
            if (file == null) {
                file = locateFileInternal(resource, repositoryService);
                if (file == null) {
                    throw internalErrorArtifactExpected(resource.getRepoKey(), resource.getPath());
                }
            }
            return file;
        }
        throw PackageResourceExceptionUtil.createResourceException(resource);
    }


    private static VfsFile<?> locateFileInternal(RepoResource info, InternalRepositoryService repositoryService) {
        StoringRepo<?, ?> storingRepo = repositoryService.storingRepositoryByKey(info.getRepoKey());
        if (storingRepo != null) {
            return storingRepo.getImmutableFile(RepoPathFactory.create(storingRepo.getKey(), info.getPath()));
        }
        return null;
    }

    private static PackageException repoNotFoundException(String repoKey) {
        String message = String.format("Failed to find repository:%s", repoKey);
        return new PackageRepoNotFoundException(message);
    }

    private static PackageException blackedOutFoundException(String repoKey, String path) {
        String message = String.format("Failed to find artifact in repo:%s, path:%s, cause: repo is blacked out",
                repoKey, path);
        return new PackageNotFoundException(message);
    }

    private static PackageException conflictException(String repoKey, String path, String cause) {
        String message = String.format("Failed to find artifact in repo:%s, path:%s, cause:%s", repoKey, path, cause);
        return new PackageException(SC_CONFLICT, "Conflict", message);
    }

    private static PackageException rejectException(String repoKey, String path, String cause) {
        String message = String.format("Failed to find artifact in repo:%s, path:%s, cause:%s", repoKey, path, cause);
        return new PackageNotFoundException(message);
    }

    private static PackageException internalErrorArtifactExpected(String repoKey, String path) {
        String message = String.format("Failed to find artifact, repo:%s, path:%s, " +
                "cause: artifact should not be null (resource found)", repoKey, path);
        return new PackageInternalServerError(message);
    }

    private static PackageException internalError(String repoKey, String path, Exception e) {
        String message = String.format("Failed to find artifact, repo:%s, path:%s, cause:%s", repoKey, path, e);
        return new PackageException(SC_INTERNAL_SERVER_ERROR, "Internal server error", message, e);
    }
}
