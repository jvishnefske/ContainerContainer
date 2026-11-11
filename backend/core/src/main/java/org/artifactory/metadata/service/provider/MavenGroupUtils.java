package org.artifactory.metadata.service.provider;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.artifactory.api.module.ModuleInfo;
import org.artifactory.aql.api.domain.sensitive.AqlApiItem;
import org.artifactory.aql.util.AqlUtils;
import org.artifactory.repo.Repo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.service.InternalRepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.Nonnull;
import java.util.Optional;

import static org.artifactory.aql.api.internal.AqlBase.and;

/**
 * Common utils for the Maven group and the ModuleInfo class
 *
 * @author Yuval Reches
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public abstract class MavenGroupUtils {

    private static final Logger log = LoggerFactory.getLogger(MavenGroupUtils.class);

    public static Optional<ModuleInfo> getModuleInfo(RepoPath repoPath,
            @Nonnull InternalRepositoryService repositoryService) {
        Repo repo = repositoryService.repositoryByKey(repoPath.getRepoKey());
        if (repo != null) {
            try {
                ModuleInfo artifactModuleInfo = repo.getItemModuleInfo(repoPath.getPath());
                if (artifactModuleInfo.isValid()) {
                    return Optional.of(artifactModuleInfo);
                }
            } catch (Exception e) {
                log.error("Unable to extract Module Info from path '{}': {}", repoPath, e.getMessage(), e);
            }
        }
        return Optional.empty();
    }

    static Optional<AqlApiItem> getPomVersionFileQuery(@Nonnull RepoPath repoPath,
            @Nonnull InternalRepositoryService repositoryService) {
        if (repoPath.getParent() == null) {
            // Root is not supported.
            return Optional.empty();
        }
        return getModuleInfo(repoPath, repositoryService)
                .flatMap(info -> AqlUtils.getDirectChildrenByModuleAndVersionQuery(repoPath, info));
    }

    static Optional<AqlApiItem> getIvyVersionFileQuery(@Nonnull RepoPath repoPath) {
        if (repoPath.getParent() == null || repoPath.getParent().getParent() == null) {
            // Root is not supported.
            return Optional.empty();
        }
        RepoPath parentPath = repoPath.getParent().getParent();
        return Optional.ofNullable(AqlApiItem.create().filter(
                and(
                        AqlApiItem.repo().equal(parentPath.getRepoKey()),
                        AqlApiItem.path().matches(parentPath.getPath() + "*")
                )));
    }
}
