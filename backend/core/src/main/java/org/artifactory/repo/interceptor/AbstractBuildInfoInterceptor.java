package org.artifactory.repo.interceptor;

import com.jfrog.build.acl.model.BuildRepoPath;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.fs.ItemInfo;
import org.artifactory.model.LocalConfig;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.interceptor.storage.StorageInterceptorAdapter;
import org.artifactory.sapi.fs.VfsItem;

import static java.util.Objects.nonNull;
import static org.artifactory.descriptor.repo.RepoType.BuildInfo;
import static org.artifactory.util.evidence.EvidenceConfigurationUtil.isEvidencePath;
import static org.jfrog.client.util.PathUtils.getExtension;

/**
 * This class introduced as an intermediate layer and aimed to contain only logic deciding if method should be triggered
 * or not. Don't add here implementations that triggers on event. Used by interceptor interested in this logic only.
 *
 * @author Volodymyr Shulga
 */
@Slf4j
@AllArgsConstructor
public abstract class AbstractBuildInfoInterceptor extends StorageInterceptorAdapter {

    protected final RepositoryService repositoryService;

    protected boolean shouldTakeAction(@NonNull VfsItem<? extends ItemInfo> item) {
        BuildRepoPath targetPath = new BuildRepoPath(item.getRepoKey(), item.getPath());
        return shouldTakeAction(item, targetPath);
    }

    protected boolean shouldTakeAction(
            @NonNull VfsItem<? extends ItemInfo> item,
            @NonNull BuildRepoPath targetPath) {
        return item.isFile()
                && isJsonFile(targetPath)
                && isBuildRepo(targetPath)
                && !isEvidencePath(RepoPathFactory.create(targetPath));
    }

    protected boolean isBuildRepo(@NonNull BuildRepoPath path) {
        log.debug("Checking if '{}' is a potential for build processing", path.getRelativePath());
        LocalConfig descriptor = repositoryService.localOrFederatedRepoConfigByKey(path.getRepoKey());
        return nonNull(descriptor) && BuildInfo.equals(descriptor.getPackageType());
    }

    protected boolean isJsonFile(@NonNull BuildRepoPath path) {
        String extension = getExtension(path.getName());
        return "json".equals(extension);
    }
}
