package org.artifactory.release.bundle;

import lombok.NonNull;
import org.artifactory.api.rest.distribution.bundle.models.ArtifactsBundleModel;
import org.artifactory.bundle.BundleTransactionStatus;
import org.artifactory.bundle.BundleType;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.spring.InternalContextHelper;
import org.artifactory.storage.db.bundle.dao.ArtifactBundlesDao;
import org.artifactory.storage.db.bundle.dao.BundleBlobsDao;
import org.artifactory.storage.db.bundle.model.BundleNode;
import org.artifactory.storage.fs.session.StorageSessionHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.artifactory.repo.system.SystemRepos.IN_TRANSIT_REPO_KEY;

/**
 * @author Inbar Tal
 */
@Service
public class InternalReleaseBundleServiceImpl implements InternalReleaseBundleService {
    private static final Logger log = LoggerFactory.getLogger(InternalReleaseBundleServiceImpl.class);
    private final ArtifactBundlesDao bundlesDao;
    private final BundleBlobsDao blobsDao;
    private final InternalRepositoryService repoService;
    ReleaseBundleDeleteTemplate releaseBundleDeleteTemplate;

    @Autowired
    public InternalReleaseBundleServiceImpl(
            @NonNull ArtifactBundlesDao bundlesDao,
            @NonNull BundleBlobsDao blobsDao,
            @NonNull InternalRepositoryService repoService) {
        this.bundlesDao = bundlesDao;
        this.blobsDao = blobsDao;
        this.repoService = repoService;
        this.releaseBundleDeleteTemplate =
                ReleaseBundleDeleteTemplateFactory.getReleaseBundleDeleteTemplate(bundlesDao, this,
                        ArtifactoryHome.get().getDBProperties());
    }

    @Override
    public void deleteReleaseBundleInternal(ArtifactsBundleModel bundleModel,
            List<BundleNode> bundleNodes,
            Set<Long> nodeIdsWithArtifactsToDelete) throws SQLException {
        log.debug("Deleting Release Bundle with id '{}'", bundleModel.getId());
        deleteBundleContent(bundleModel, bundleNodes, nodeIdsWithArtifactsToDelete);
        deleteExportedBundleFile(bundleModel);
        blobsDao.deleteBlob(bundleModel.getId());
        bundlesDao.deleteArtifactsBundle(bundleModel.getId());

        saveSession(); // save db tx changes for removal of children to reveal empty folders
        if (ConstantValues.releaseBundleDeleteEmptyFolders.getBoolean()) {
            cleanupReleaseBundleRepository(bundleModel);
        } else {
            log.info("{} is set to false, hence deleting release bundle will not include deletion of empty folders",
                    ConstantValues.releaseBundleDeleteEmptyFolders.getPropertyName());
        }
    }

    private void deleteExportedBundleFile(@NonNull ArtifactsBundleModel bundleModel) {
        RepoPath filePath = ReleaseBundleUtils.buildExportFileRepoPath(
                bundleModel.getName(),
                bundleModel.getVersion(),
                bundleModel.getStoringRepo()
        );
        if (repoService.exists(filePath)) {
            log.info("Deleting exported bundle file for {}/{} from {}",
                    bundleModel.getName(), bundleModel.getVersion(), filePath);
            repoService.undeploy(filePath);
        }
    }

    protected void deleteBundleContent(ArtifactsBundleModel artifactsBundle, List<BundleNode> bundleNodes,
            Set<Long> nodeIdsWithArtifactsToDelete) throws SQLException {
        log.info("Deleting a release bundle version {}/{} of type {} with status {}",
                artifactsBundle.getName(), artifactsBundle.getVersion(),
                artifactsBundle.getType(), artifactsBundle.getStatus());
        if (BundleTransactionStatus.INPROGRESS.equals(artifactsBundle.getStatus())) {
            deleteInProgressReleaseBundleContent(artifactsBundle);
        } else {
            releaseBundleDeleteTemplate.deleteBundle(artifactsBundle, bundleNodes, nodeIdsWithArtifactsToDelete);
        }
    }

    private void deleteInProgressReleaseBundleContent(ArtifactsBundleModel artifactsBundle) throws SQLException {
        bundlesDao.deleteBundleNodes(artifactsBundle.getId());
        InternalContextHelper.get().getSecurityService().doAsSystem(() ->
                deleteArtifactsFromIntransitRepo(artifactsBundle.getName(), artifactsBundle.getVersion()));
    }

    @Override
    public void undeployIfNeeded(@NonNull ArtifactsBundleModel artifactsBundle, @NonNull List<BundleNode> bundleNodes,
            @NonNull Set<Long> nodeIdsWithArtifactsToDelete) {

        List<BundleNode> sortedBundleNodes = bundleNodes.stream()
                .filter(bundleNode -> nodeIdsWithArtifactsToDelete.contains(bundleNode.getNodeId()))
                .sorted(Comparator.comparing(BundleNode::getRepoPath))
                .toList();
        boolean needToTryToDeleteEmptyFolders = false;
        for (BundleNode bundleNode : sortedBundleNodes) {
            log.debug("Deleting repoPath: {}. bundle name: {} ", bundleNode.getRepoPath(), artifactsBundle.getName());
            if (repoService.exists(RepoPathFactory.create(bundleNode.getRepoPath()))) {
                repoService.undeploy(RepoPathFactory.create(bundleNode.getRepoPath()), true, true,
                        artifactsBundle.getType().equals(BundleType.TARGET));
                //we try to delete at least one file, so we will try to delete empty folders
                needToTryToDeleteEmptyFolders = true;
            }
        }

        if (needToTryToDeleteEmptyFolders && ConstantValues.releaseBundleDeleteEmptyFolders.getBoolean()) {
            saveSession(); // save changes in db tx before trying to delete empty folders
            cleanupTargetRepositories(bundleNodes);
        }
    }

    /**
     * Delete the temp folder and all of its content
     */
    private void deleteArtifactsFromIntransitRepo(String bundleName, String bundleVersion) {
        String txPath = String.join("/", IN_TRANSIT_REPO_KEY, bundleName, bundleVersion);
        repoService.undeploy(RepoPathFactory.create(txPath));
    }

    private void cleanupReleaseBundleRepository(@NonNull ArtifactsBundleModel bundleModel) {
        String releaseBundleVersion = bundleModel.getName() + "/" + bundleModel.getVersion();
        RepoPath releaseBundleVersionFolderRepoPath =
                RepoPathFactory.create(bundleModel.getStoringRepo(), releaseBundleVersion);
        log.info("Removing Release Bundle {} directory from {} repository", releaseBundleVersion,
                bundleModel.getStoringRepo());
        undeployEmptyFolder(releaseBundleVersionFolderRepoPath);
    }

    private void cleanupTargetRepositories(@NonNull List<BundleNode> bundleNodes) {
        Set<String> potentialFolderPathsToDelete =
                ReleaseBundleUtils.getPotentialFolderPathsToDelete(bundleNodes);
        Map<Integer, Set<String>> depthFolderPathsMap =
                ReleaseBundleUtils.getDepthFolderPathsMap(potentialFolderPathsToDelete);
        depthFolderPathsMap.keySet().forEach(depth -> {
            for (String folderPath : depthFolderPathsMap.get(depth)) {
                RepoPath folderRepoPath = RepoPathFactory.create(folderPath);
                log.debug("Deleting empty folder {}", folderRepoPath.getPath());
                undeployEmptyFolder(folderRepoPath);
            }
            saveSession(); // save changes in db tx after every depth of empty folders
        });
    }

    private void undeployEmptyFolder(@NonNull RepoPath folderPathToDelete) {
        if (repoService.exists(folderPathToDelete) && !repoService.hasChildren(folderPathToDelete)) {
            repoService.undeploy(folderPathToDelete, true, true, true);
        }
    }

    private void saveSession() {
        if (StorageSessionHolder.getSession() != null) {
            StorageSessionHolder.getSession().save();
        } else {
            log.warn("StorageSessionHolder.getSession() is null, " +
                    "release bundle empty folders might not be deleted properly.");
        }
    }
}
