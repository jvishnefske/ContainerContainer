package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.service;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.repo.BaseBrowsableItem;
import org.artifactory.api.repo.RemoteBrowsableItem;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.fs.FolderInfo;
import org.artifactory.fs.ItemInfo;
import org.artifactory.mime.MavenNaming;
import org.artifactory.mime.MimeType;
import org.artifactory.mime.NamingUtils;
import org.artifactory.repo.ArtifactoryStandardUrlResolver;
import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.repo.RemoteRepo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.remote.browse.RemoteItem;
import org.artifactory.repo.virtual.VirtualRepo;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.BaseNode;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.FileNode;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.FolderNode;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.VirtualRemoteFileNode;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.VirtualRemoteFolderNode;
import org.jfrog.client.util.PathUtils;

import jakarta.annotation.Nonnull;

import static org.apache.commons.lang3.StringUtils.removeEnd;
import static org.apache.commons.lang3.StringUtils.removeStart;

/**
 * @author Hussein Alshqerat
 */
@Slf4j
@UtilityClass
public class TreeBrowserUtils {

    @Nonnull
    public static BaseNode buildVirtualItem(BaseNode node, String virtualRepoKey) {
        final String path = node.getRepoPath().getPath();
        node.setPath(virtualRepoKey + ":" + path);
        node.setRepoKey(virtualRepoKey);
        if (!node.isFolder()) {
            VirtualRemoteFileNode virtualNode =
                    new VirtualRemoteFileNode(node, node.getText(), "virtual", node.getRepoPkgType(), false);
            virtualNode.setRepoKey(virtualRepoKey);
            return virtualNode;
        } else {
            final RepoPath virtualRepoPath = InternalRepoPathFactory.create(virtualRepoKey, path, true);
            VirtualRemoteFolderNode virtualFolder =
                    new VirtualRemoteFolderNode(virtualRepoPath, isCached(node), node.getText(), "virtual");
            virtualFolder.setRepoKey(virtualRepoKey);
            return virtualFolder;
        }
    }

    private static boolean isCached(BaseNode node) {
        if (node instanceof VirtualRemoteFolderNode virtualRemoteFolderNode) {
            return virtualRemoteFolderNode.isLocal() || virtualRemoteFolderNode.isCached();
        }
        return node.isLocal();
    }

    @Nonnull
    public static RemoteBrowsableItem buildRemoteItem(RemoteItem remoteItem, RemoteRepo<?, ?> remoteRepo,
            String relativePath) {
        RepoPath repoPath = remoteRepo.getRepoPath(relativePath);
        String path = removeStart(remoteItem.getUrl(), removeBaseUrl(remoteRepo.getUrl()));
        RepoPath remoteRepoPath = InternalRepoPathFactory.create(repoPath.getRepoKey(), path, remoteItem.isDirectory());
        RemoteBrowsableItem browsableItem = new RemoteBrowsableItem(remoteItem, remoteRepoPath);
        if (remoteItem.getEffectiveUrl() != null) {
            log.debug("Remote Browsable item effective URL: '{}'", remoteItem.getEffectiveUrl());
            browsableItem.setEffectiveUrl(remoteItem.getEffectiveUrl());
        }
        return browsableItem;
    }

    @Nonnull
    public static String removeBaseUrl(String originalUrl) {
        String remoteUrl = PathUtils.trimTrailingSlashes(originalUrl);
        ArtifactoryStandardUrlResolver artifactoryStandardUrlResolver = new ArtifactoryStandardUrlResolver(remoteUrl);
        StringBuilder baseUrlBuilder = new StringBuilder(artifactoryStandardUrlResolver.getBaseUrl()).append("/")
                .append(artifactoryStandardUrlResolver.getRepoKey());
        if (!remoteUrl.endsWith("/")) {
            baseUrlBuilder.append("/");
        }
        return baseUrlBuilder.toString();
    }

    public static boolean virtualRepoAccepts(VirtualRepo virtualRepo, RepoPath repoPath) {
        String path = repoPath.getPath();
        if (repoPath.isFolder()) {
            path += "/";
        }
        //If the path is not accepted, return immediately
        if (!virtualRepo.accepts(repoPath)) {
            log.debug("Virtual repo '{}' did not accept path '{}'", virtualRepo, repoPath);
            return false;
        }
        if ((path.contains(MavenNaming.NEXUS_INDEX_DIR) || MavenNaming.isIndex(path)) &&
                virtualRepo.getRepoConfig().getPackageType().isMavenGroup()) {
            log.debug("Path '{}' is not an index", path);
            return false;
        }
        return true;
    }

    @Nonnull
    public static BaseNode getChildItem(ItemInfo itemInfo, String relativePath, String repoType, String repoPkgType,
            RepoPath repoPath) {
        BaseNode child;
        if (itemInfo.isFolder()) {
            child = new FolderNode(((FolderInfo) itemInfo), itemInfo.getName(), repoType);
        } else {
            MimeType mimeType = NamingUtils.getMimeType(relativePath);
            child = new FileNode(repoPath, itemInfo.getName(), repoType, RepoType.fromType(repoPkgType),
                    mimeType.isArchive());
        }
        return child;
    }

    public static BaseNode getRemoteChildItem(BaseBrowsableItem pathItem, String repoType, String repoPkgType) {
        BaseNode child = null;
        if (pathItem.isFolder()) {
            if (repoType.equals("virtual")) {
                child = createVirtualFolderNode(pathItem, repoType);
            } else if (repoType.equals("remote")) {
                child = createRemoteFolderNode(pathItem, repoType);
            }
        } else {
            MimeType mimeType = NamingUtils.getMimeType(pathItem.getRelativePath());
            child = new VirtualRemoteFileNode(pathItem, pathItem.getName(), repoType, RepoType.fromType(repoPkgType),
                    mimeType.isArchive());
        }
        return child;
    }

    @Nonnull
    public static BaseNode createVirtualFolderNode(BaseBrowsableItem pathItem, String repoType) {
        return new VirtualRemoteFolderNode(pathItem.getRepoPath(), pathItem, pathItem.getName(), repoType);
    }

    @Nonnull
    public static BaseNode createRemoteFolderNode(BaseBrowsableItem pathItem, String repoType) {
        String repoKey = removeEnd(pathItem.getRepoKey(), "-cache");
        RepoPath repositoryPath = InternalRepoPathFactory.create(repoKey, pathItem.getRepoPath().getPath(), true);
        return new VirtualRemoteFolderNode(repositoryPath, pathItem, pathItem.getName(), repoType);
    }

    @Nonnull
    public static BaseNode createRemoteFolderNode(ItemInfo itemInfo, String repoType) {
        String repoKey = removeEnd(itemInfo.getRepoKey(), "-cache");
        RepoPath repositoryPath = InternalRepoPathFactory.create(repoKey, itemInfo.getRepoPath().getPath(), true);
        return new VirtualRemoteFolderNode(repositoryPath, true, itemInfo.getName(), repoType);
    }
}
