package org.artifactory.rest.resource.upload;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.artifactory.api.repo.exception.FileExpectedException;
import org.artifactory.api.repo.exception.ItemNotFoundRuntimeException;
import org.artifactory.api.repo.exception.RepoRejectException;
import org.artifactory.api.request.InternalSystemFolderUploadRequest;
import org.artifactory.api.request.UploadService;
import org.artifactory.fs.FileInfo;
import org.artifactory.request.InternalArtifactoryResponse;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.RemoteRepo;
import org.artifactory.repo.Repo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.repo.system.SystemFolderUploadMarker;
import org.artifactory.repo.system.SystemFolders;
import org.artifactory.repo.system.SystemFolders.SystemFolder;
import org.artifactory.repo.virtual.VirtualRepo;
import org.artifactory.util.PathValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.file.InvalidPathException;

/**
 * Internal system-folder upload service backing {@link InternalUploadResource}.
 *
 * <p>Authorization is identical to the standard deploy API: the shared upload pipeline runs
 * {@code assertValidDeployPathAndPermissions}, so the caller must hold deploy permission on the
 * target repository path. This service performs only request-shape validation (path shape →
 * folder resolve → repo-type validation → empty body) before delegating the upload — wrapped in
 * a {@link SystemFolderUploadMarker} — to the standard upload service.
 *
 * <p>Delegates to {@link UploadService} rather than calling {@code saveFileInternal} directly.
 * {@link InternalSystemFolderUploadRequest} carries a type marker that
 * {@code UploadServiceUtils.getTargetRepository} detects to resolve remote repo keys to their
 * {@code -cache} half (instead of returning {@code null}), so remote uploads are admitted rather
 * than rejected. The request otherwise flows through the full pipeline — addon checks, plugins,
 * deploy-permission validation — exactly like a regular deployment.
 *
 * <p>The {@link SystemFolderUploadMarker} thread marker lets a folder's storage interceptor detect
 * this trusted internal flow and skip its own permission check. All other standard interceptors
 * (indexing, Xray, Workers events, federation) run exactly as they do for regular deployments.
 *
 * <p>Repo-type support: LOCAL, REMOTE (writes to the {@code -cache} half), and FEDERATED
 * (a {@link LocalRepo}, so the write goes to its local storage and federation replication
 * propagates the file to mirror members). VIRTUAL is rejected because virtuals have no storage.
 *
 * @author Fawaz Atwa
 */
@Slf4j
@Service
public class InternalUploadServiceImpl {

    private static final String PATH_SEPARATOR = "/";

    private final InternalRepositoryService internalRepositoryService;
    private final UploadService uploadService;

    @Autowired
    public InternalUploadServiceImpl(
            InternalRepositoryService internalRepositoryService,
            UploadService uploadService) {
        this.internalRepositoryService = internalRepositoryService;
        this.uploadService = uploadService;
    }

    /**
     * Uploads {@code content} to {@code repoKey} under a recognized system-folder path.
     *
     * @param repoKey  LOCAL, REMOTE (without the {@code -cache} suffix), or FEDERATED key;
     *                 400 if it does not exist or is VIRTUAL
     * @param filePath must start with a recognized system-folder prefix; 400 otherwise
     * @param content  non-empty; closed by the underlying save
     * @return details of the stored artifact (resolved repo path, size, checksums)
     */
    public InternalUploadResult upload(
            @NonNull String repoKey,
            @NonNull String filePath,
            @NonNull InputStream content) throws RepoRejectException, IOException {
        try {
            PathValidator.validate(filePath);
        } catch (InvalidPathException invalid) {
            throw new BadRequestException(
                    "Invalid file path '%s': %s".formatted(filePath, invalid.getReason()));
        }

        SystemFolder folder = resolveSystemFolder(filePath);
        validateFileWithinFolder(filePath, folder);

        Repo<?, ?> repo = validateRepoType(repoKey);
        InputStream nonEmptyContent = rejectEmptyBody(content);

        RepoPath requestPath = RepoPathFactory.create(repoKey, filePath);
        InternalSystemFolderUploadRequest request =
                new InternalSystemFolderUploadRequest(requestPath, nonEmptyContent, -1);
        InternalArtifactoryResponse response = new InternalArtifactoryResponse();

        SystemFolderUploadMarker.mark();
        try {
            log.debug("Uploading file under system folder '{}': {}/{}", folder.getFolderName(), repoKey, filePath);
            uploadService.upload(request, response);
        } finally {
            SystemFolderUploadMarker.unmark();
        }

        if (response.isError()) {
            throw new RepoRejectException(
                    "Upload failed for '%s/%s': %s".formatted(repoKey, filePath, response.getStatusMessage()),
                    response.getStatus());
        }

        return buildResult(repo, repoKey, filePath);
    }

    /**
     * Reads back the just-stored item to populate the response (resolved path, size, checksums).
     * For remote repos the file lands in the {@code -cache} half, so the stored path differs from
     * the request path. The read is best-effort: the upload has already succeeded, so a failure to
     * re-read the item must not turn a successful upload into an error — we fall back to a
     * path-only result.
     */
    private InternalUploadResult buildResult(Repo<?, ?> repo, String repoKey, String filePath) {
        RepoPath storedPath = resolveStoredPath(repo, repoKey, filePath);
        try {
            FileInfo fileInfo = internalRepositoryService.getFileInfo(storedPath);
            return new InternalUploadResult(
                    storedPath.toPath(),
                    fileInfo.getSize(),
                    fileInfo.getSha2(),
                    fileInfo.getSha1(),
                    fileInfo.getMd5());
        } catch (ItemNotFoundRuntimeException | FileExpectedException unavailable) {
            log.debug("Stored item info unavailable for '{}'; returning path-only result", storedPath, unavailable);
            return new InternalUploadResult(storedPath.toPath(), -1, null, null, null);
        }
    }

    private RepoPath resolveStoredPath(Repo<?, ?> repo, String repoKey, String filePath) {
        if (repo instanceof RemoteRepo<?, ?> remoteRepo && remoteRepo.getLocalCacheRepo() != null) {
            return RepoPathFactory.create(remoteRepo.getLocalCacheRepo().getKey(), filePath);
        }
        return RepoPathFactory.create(repoKey, filePath);
    }

    private InputStream rejectEmptyBody(InputStream content) throws IOException {
        PushbackInputStream pushback = new PushbackInputStream(content);
        int first;
        try {
            first = pushback.read();
        } catch (IOException readFailure) {
            IOUtils.closeQuietly(pushback);
            throw readFailure;
        }
        if (first == -1) {
            IOUtils.closeQuietly(pushback);
            throw new BadRequestException(
                    "Request body is empty: this endpoint writes signed artifact files and "
                            + "does not accept zero-byte uploads");
        }
        pushback.unread(first);
        return pushback;
    }

    private SystemFolder resolveSystemFolder(String filePath) {
        return SystemFolders.forPath(filePath)
                .orElseThrow(() -> new BadRequestException(
                        "Invalid file path '%s': must start with a recognized system-folder prefix"
                                .formatted(filePath)));
    }

    private void validateFileWithinFolder(String filePath, SystemFolder folder) {
        String folderName = folder.getFolderName();
        String normalized = filePath.startsWith(PATH_SEPARATOR) ? filePath.substring(1) : filePath;
        // A trailing slash is folder semantics by the RepoPath contract; this endpoint stores files.
        if (filePath.endsWith(PATH_SEPARATOR) || normalized.equals(folderName)) {
            throw new BadRequestException(
                    "Invalid file path '%s': must point to a file inside '%s', not the folder itself"
                            .formatted(filePath, folderName));
        }
    }

    /**
     * Validates the repository type for this endpoint:
     * <ul>
     *   <li>LOCAL  → allowed; {@link UploadService} writes directly.</li>
     *   <li>REMOTE → allowed; {@code UploadServiceUtils.getTargetRepository()} routes to the
     *       {@code -cache} half when it detects {@link InternalSystemFolderUploadRequest}.</li>
     *   <li>FEDERATED → allowed; it is a {@link LocalRepo}, so the write goes to its local
     *       storage and federation replication propagates the file to mirror members.</li>
     *   <li>VIRTUAL → rejected (virtuals have no storage; they aggregate other repos).</li>
     * </ul>
     *
     * @return the validated repository, for resolving the stored path of the uploaded item
     */
    private Repo<?, ?> validateRepoType(String repoKey) {
        Repo<?, ?> repo = internalRepositoryService.repositoryByKey(repoKey);
        if (repo == null) {
            throw new BadRequestException(
                    "Repository '%s' does not exist".formatted(repoKey));
        }
        if (repo instanceof VirtualRepo) {
            throw new BadRequestException(
                    "Repository '%s' is virtual and cannot accept uploads"
                            .formatted(repoKey));
        }
        if (repo instanceof RemoteRepo<?, ?> remoteRepo) {
            if (!remoteRepo.isStoreArtifactsLocally()) {
                throw new BadRequestException(
                        ("Repository '%s' is remote but not configured to store artifacts locally; "
                                + "enable 'Store Artifacts Locally' to use this endpoint")
                                .formatted(repoKey));
            }
            return repo;
        }
        if (repo instanceof LocalRepo) {
            return repo;
        }
        throw new BadRequestException(
                "Repository '%s' has an unsupported type for this endpoint".formatted(repoKey));
    }
}
