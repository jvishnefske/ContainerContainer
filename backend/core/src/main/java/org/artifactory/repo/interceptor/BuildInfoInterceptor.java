package org.artifactory.repo.interceptor;

import ch.qos.logback.classic.Level;
import com.jfrog.build.acl.bridge.BuildFacade;
import com.jfrog.build.acl.model.BuildCoordinate;
import com.jfrog.build.acl.model.BuildRepoPath;
import com.jfrog.build.acl.model.BuildRun;
import com.jfrog.build.acl.model.BuildWrapper;
import jakarta.annotation.Nullable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.addon.AddonsManager;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.exception.CancelException;
import org.artifactory.exception.http.NotFoundException;
import org.artifactory.fs.ItemInfo;
import org.artifactory.md.Properties;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.sapi.interceptor.CopyMoveCheckResult;
import org.artifactory.sapi.interceptor.ImportInterceptor;
import org.artifactory.sapi.interceptor.context.BulkCopyMoveContext;
import org.artifactory.sapi.interceptor.context.DeleteContext;
import org.artifactory.sapi.interceptor.context.InterceptorCreateContext;
import org.artifactory.sapi.interceptor.context.InterceptorMoveCopyContext;
import org.artifactory.storage.fs.MutableVfsFile;
import org.jfrog.build.api.Build;
import org.jfrog.storage.binstore.exceptions.BinaryNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.Objects;
import java.util.Set;

import static com.jfrog.build.acl.util.BuildInfoUtils.extractCoordinatesFromPath;
import static com.jfrog.build.acl.util.BuildInfoUtils.generateBuildJsonRepoPath;
import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_CONFLICT;
import static org.apache.http.HttpStatus.SC_FORBIDDEN;
import static org.artifactory.common.ConstantValues.buildsDbForceOverrideEnabled;
import static org.artifactory.sapi.interceptor.CopyMoveCheckResult.ALLOW;
import static org.artifactory.sapi.interceptor.CopyMoveCheckResult.REJECT;

/**
 * Intercept actions performed on Build Info repository
 *
 * @author Yuval Reches
 */
@Slf4j
@Getter(AccessLevel.PROTECTED)
public class BuildInfoInterceptor extends AbstractBuildInfoInterceptor implements ImportInterceptor {

    private static final String ERR_INNER_COPY_MOVE =
            "Copy and Move operations are not allowed within the Build Info repository";

    private static final String ERR_BAD_PATH =
            "Trying to position build '%s:%s' under wrong path '%s' is not allowed, " +
                    "the expected path for this build-info json is '%s'";

    private final AuthorizationService authService;
    private final SecurityService securityService;
    private final AddonsManager addonsManager;
    private final BuildFacade buildFacade;

    @Autowired
    public BuildInfoInterceptor(
            @NonNull RepositoryService repositoryService,
            @NonNull BuildFacade buildFacade,
            @NonNull AuthorizationService authService,
            @NonNull SecurityService securityService,
            @NonNull AddonsManager addonsManager) {
        super(repositoryService);
        this.authService = authService;
        this.securityService = securityService;
        this.addonsManager = addonsManager;
        this.buildFacade = buildFacade;
    }

    @Override
    public void beforeCreate(
            @NonNull VfsItem<? extends ItemInfo> fsItem,
            @NonNull MutableStatusHolder statusHolder) {
        BuildRepoPath path = new BuildRepoPath(fsItem.getRepoKey(), fsItem.getPath());
        if (isActionWithNonJsonFileInBuildInfoRepo(fsItem, path)) {
            //Reporting the CancelException in the status holder will fail the deployment.
            String err = "The '" + path.getRepoKey() + "' repository rejected the deployment of '" +
                    path.getRelativePath() + "'. Only valid Build Info .json files are supported.";
            statusHolder.error(err, SC_CONFLICT, new CancelException(err, SC_CONFLICT), log);
        }
    }

    @Override
    public void afterCreate(
            @NonNull VfsItem<? extends ItemInfo> fsItem,
            @NonNull MutableStatusHolder statusHolder,
            @Nullable InterceptorCreateContext ctx) {
        if (shouldTakeAction(fsItem)) {
            doAction(fsItem, statusHolder, ctx);
        }
    }

    @Override
    public void afterImport(
            @NonNull VfsItem<? extends ItemInfo> fsItem,
            @NonNull MutableStatusHolder statusHolder) {
        if (shouldTakeAction(fsItem)) {
            doAction(fsItem, statusHolder, null);
        }
    }

    private void doAction(
            @NonNull VfsItem<? extends ItemInfo> fsItem,
            @NonNull MutableStatusHolder statusHolder,
            @Nullable InterceptorCreateContext ctx) {
        BuildRepoPath deploymentPath = new BuildRepoPath(fsItem.getRepoKey(), fsItem.getPath());
        String fullPath = deploymentPath.toString();
        log.debug("Build Info json deployed to '{}'", fullPath);
        BuildWrapper buildWrapper = buildFacade.getBuildModelFromFile(deploymentPath);
        Build build = buildWrapper.getBuild();
        //This is the path the json should actually go to
        BuildRepoPath targetPath = generateBuildJsonRepoPath(build, buildWrapper.getBuildRepo());
        // We don't process builds under wrong path, it will be deleted by caller
        if (isBuildUnderWrongPath(build, deploymentPath, buildWrapper.getBuildRepo())) {
            log.debug("Build deployed to wrong path, ignoring it");
            return;
        }
        if (fsItem instanceof MutableVfsFile && !((MutableVfsFile) fsItem).isOriginallyNew() ||
                (buildsDbForceOverrideEnabled.getBoolean() && buildFacade.isBuildExistsInDb(buildWrapper))) {
            //If this is an override we just need to kick the build out of the db before re-adding it.
            log.info("Override of build at path '{}' detected.", fullPath);
            log.debug("Removing build that matches path '{}' from db", fullPath);
            buildFacade.deleteBuildInternal(build, buildWrapper.getBuildRepo(), statusHolder);
        }
        // In case of error - exception is thrown and deployment of .json file fails
        buildFacade.addBuildInternal(buildWrapper);
        // Update the response with the right coordinates of the artifact after it has been moved
        if (ctx != null) {
            ctx.setAlternateRepoPath(RepoPathFactory.create(targetPath));
        }
    }

    /**
     * In case {@link DeleteContext#isAvoidBuildDeleteInterceptor()} is on, it means we wish to delete the entire
     * repo and not trigger build delete per build number / build name.
     * <p>
     * Upon delete of a folder this interceptor is called upon every file inside.
     */
    @Override
    public void afterDelete(
            @NonNull VfsItem<? extends ItemInfo> fsItem,
            @NonNull MutableStatusHolder statusHolder,
            @NonNull DeleteContext ctx) {
        if (!ctx.isAvoidBuildDeleteInterceptor() && shouldTakeAction(fsItem)) {
            BuildRepoPath buildJsonPath = new BuildRepoPath(fsItem.getRepoKey(), fsItem.getPath());
            if (log.isDebugEnabled()) {
                log.debug("Trying to delete build according to file '{}'", fsItem.getRepoPath().toPath());
            }
            try {
                BuildWrapper buildFromFile = buildFacade.getBuildModelFromFile(buildJsonPath);
                // Validating path here in order to assume the path in the buildFacade later
                if (isBuildUnderWrongPath(buildFromFile.getBuild(), buildJsonPath, buildFromFile.getBuildRepo())) {
                    // The build-info JSON sits under a path whose timestamp doesn't match the manifest's build
                    // time (a known legacy Jenkins-plugin build.timestamp mismatch). Deleting only the file and
                    // relying on the layout path would leave the DB build record behind, so build retention keeps
                    // re-finding it in the DB and re-deleting it on every run. Purge the DB record by
                    // the coordinates encoded in the path so the row retention found is actually removed.
                    if (log.isDebugEnabled()) {
                        log.debug("Build {}:{} path doesn't match the layout: {}. Purging its DB record by path " +
                                        "coordinates instead of deleting from repo only.",
                                buildFromFile.getBuild().getName(), buildFromFile.getBuild().getNumber(),
                                buildJsonPath);
                    }
                    deleteBuildRecordByPathCoordinates(fsItem, statusHolder);
                    return;
                }
                // By default, does not remove artifacts (since interceptor might also be triggered by undeploy)
                // REST calls do the build artifact deletion manually
                buildFacade.deleteBuildInternal(buildFromFile.getBuild(), buildFromFile.getBuildRepo(), statusHolder);
            } catch (BinaryNotFoundException | NotFoundException e) {
                //This catch is for if the binary for the build info json is missing
                log.warn("Could not extract and parse build in afterDelete build interceptor for {} due to: {}. " +
                        "Continuing to delete via fallback", fsItem.getRepoPath().toPath(), e.getMessage());
                deleteBuildRecordByPathCoordinates(fsItem, statusHolder);
            }
        }
    }

    /**
     * Deletes the DB build record identified by the coordinates encoded in the build-info JSON path (name, number
     * and start time). Used as a fallback both when the build-info binary is missing (cannot be parsed) and when the
     * file is under a path that doesn't match the manifest layout, where the row must still be purged so build
     * retention converges. The start-time equality check guarantees only the record matching the deleted path is
     * removed, so builds sharing name+number but a different start time are left untouched.
     */
    private void deleteBuildRecordByPathCoordinates(
            @NonNull VfsItem<? extends ItemInfo> fsItem,
            @NonNull MutableStatusHolder statusHolder) {
        BuildCoordinate buildCoords = extractCoordinatesFromPath(fsItem.getPath(), fsItem.getRepoKey());
        if (buildCoords == null) {
            log.warn("Could not extract build coordinates from path '{}'. Skipping DB record deletion.",
                    fsItem.getRepoPath().toPath());
            return;
        }
        Set<BuildRun> builds = buildFacade.searchBuildsByNameAndNumberInternal(
                buildCoords.getBuildName(), buildCoords.getBuildNumber(), buildCoords.getBuildRepo());
        // Compares buildJsonPath to buildRun date to ensure we're deleting correct build
        for (BuildRun buildRun : builds) {
            Date buildCoordsDate = new Date(buildCoords.getDate());
            if (buildCoordsDate.equals(buildRun.getStartedDate())) {
                buildFacade.deleteBuild(buildRun, fsItem.getRepoKey(), statusHolder);
            }
        }
    }

    /**
     * We allow move/copy into the repo to support copy/move from REST and trashcan restore (which is move).
     * Copy and Move within the repo makes no sense since the deployment path is calculated based on the content of the
     * json and obviously since the content doesn't change when you copy/move then the json will end in the same path.
     */
    @Override
    public CopyMoveCheckResult checkCopyMove(
            @NonNull VfsItem<? extends ItemInfo> sourceItem,
            @NonNull RepoPath repoPath,
            @NonNull MutableStatusHolder status) {
        BuildRepoPath targetPath = new BuildRepoPath(repoPath.getRepoKey(), repoPath.getPath());
        if (shouldTakeAction(sourceItem, targetPath)) {
            BuildRepoPath sourcePath = new BuildRepoPath(sourceItem.getRepoKey(), sourceItem.getPath());
            if (isBuildRepo(sourcePath) && !isBuildRepo(targetPath)) {
                status.errorWithDifferentLogLevel(ERR_INNER_COPY_MOVE, SC_BAD_REQUEST,
                        new CancelException(ERR_INNER_COPY_MOVE, SC_BAD_REQUEST), log, Level.WARN);
                return REJECT;
            } else {
                try {
                    BuildWrapper buildToAdd = buildFacade.getBuildModelFromFile(sourcePath);
                    BuildRepoPath expectedPath =
                            generateBuildJsonRepoPath(buildToAdd.getBuild(), targetPath.getRepoKey());
                    if (!expectedPath.equals(targetPath)) {
                        String err = badPathError(buildToAdd.getBuild(), expectedPath, targetPath);
                        status.errorWithDifferentLogLevel(err, SC_BAD_REQUEST, new CancelException(err, SC_BAD_REQUEST),
                                log, Level.WARN);
                        return REJECT;
                    }
                } catch (Exception e) {
                    log.debug("", e);
                    String err =
                            "Failed to parse Build Info from source path '" + sourcePath + "'. Operation is blocked: " +
                                    e.getMessage();
                    status.errorWithDifferentLogLevel(err, SC_BAD_REQUEST, new CancelException(err, SC_BAD_REQUEST),
                            log, Level.WARN);
                    return REJECT;
                }
            }
        }
        return ALLOW;
    }

    @Override
    public void beforeCopyFiles(@NonNull BulkCopyMoveContext context) {
        if (context.getSourceItems().getFirst() == null) {
            return;
        }
        for (int i = 0; i < context.getSourceItems().size(); i++) {
            checkCopyMove(
                    context.getSourceItems().get(i),
                    context.getTargetRepoPaths().get(i),
                    context.getStatusHolder()
            );
        }
    }

    @Override
    public void beforeMove(
            @NonNull VfsItem<? extends ItemInfo> sourceItem,
            @NonNull RepoPath targetRepoPath,
            @NonNull MutableStatusHolder statusHolder,
            @Nullable Properties properties) {
        checkCopyMove(sourceItem, targetRepoPath, statusHolder);
    }

    @Override
    public void afterMove(
            @Nullable VfsItem<? extends ItemInfo> sourceItem,
            @NonNull VfsItem<? extends ItemInfo> targetItem,
            @NonNull MutableStatusHolder statusHolder,
            @Nullable Properties properties,
            @Nullable InterceptorMoveCopyContext ctx) {
        afterMoveCopy(targetItem, statusHolder);
    }

    @Override
    public void afterCopy(
            @Nullable VfsItem<? extends ItemInfo> sourceItem,
            @NonNull VfsItem<? extends ItemInfo> targetItem,
            @NonNull MutableStatusHolder statusHolder,
            @Nullable Properties properties,
            @Nullable InterceptorMoveCopyContext ctx) {
        afterMoveCopy(targetItem, statusHolder);
    }

    /**
     * {@link #checkCopyMove} limits the scope of this method to run only on copy/move operations *into* the
     * build info repo.
     */
    private void afterMoveCopy(
            @NonNull VfsItem<? extends ItemInfo> targetItem,
            @NonNull MutableStatusHolder status) {
        BuildRepoPath targetPath = new BuildRepoPath(targetItem.getRepoKey(), targetItem.getPath());
        if (shouldTakeAction(targetItem)) {
            BuildWrapper buildToAdd = buildFacade.getBuildModelFromFile(targetPath);
            BuildRepoPath expectedPath =
                    generateBuildJsonRepoPath(buildToAdd.getBuild(), targetItem.getRepoKey());
            if (!expectedPath.equals(targetPath)) {
                //Shouldn't really reach here since the 'before' and 'allowed' triggers prevent this but what the heck
                String err = badPathError(buildToAdd.getBuild(), expectedPath, targetPath);
                status.errorWithDifferentLogLevel(err, SC_BAD_REQUEST, new CancelException(err, SC_BAD_REQUEST), log,
                        Level.WARN);
                return;
            }

            buildFacade.addBuildInternal(buildToAdd);
        }
    }

    @Override
    public void assertDeleteRepoAllowed(
            @NonNull String repoKey,
            @NonNull MutableStatusHolder status) {
        String defaultBuildRepo = buildFacade.getBuildInfoRepoForDefaultProject();
        if (Objects.equals(defaultBuildRepo, repoKey)) {
            status.error("Deletion of Build Info repository " + repoKey + " is not allowed.", SC_FORBIDDEN, log);
        } else if (buildFacade.isBuildInfoRepo(repoKey) && !securityService.isAdmin()) {
            status.error("Deletion of Build-Info repository '" + repoKey + "' is restricted only for super admin.",
                    SC_FORBIDDEN, log);
        }
    }

    private boolean isActionWithNonJsonFileInBuildInfoRepo(
            @NonNull VfsItem<? extends ItemInfo> vfsItem,
            @NonNull BuildRepoPath targetPath) {
        return vfsItem.isFile() && !isJsonFile(targetPath) && isBuildRepo(targetPath);
    }

    @NonNull
    private String badPathError(
            @NonNull Build build,
            @NonNull BuildRepoPath expectedPath,
            @NonNull BuildRepoPath targetPath) {
        return String.format(
                ERR_BAD_PATH,
                build.getName(),
                build.getNumber(),
                targetPath,
                expectedPath
        );
    }

    private static boolean isBuildUnderWrongPath(
            @NonNull Build build,
            @NonNull BuildRepoPath buildJsonPath,
            @NonNull String repoKey) {
        BuildRepoPath expectedPath = generateBuildJsonRepoPath(
                build.getName(),
                build.getNumber(),
                build.getStarted(),
                repoKey
        );
        return !buildJsonPath.equals(expectedPath);
    }
}