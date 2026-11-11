package org.artifactory.metadata.service.provider;

import lombok.Data;
import org.artifactory.api.module.ModuleInfo;
import org.artifactory.aql.api.domain.sensitive.AqlApiItem;
import org.artifactory.aql.util.AqlUtils;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.md.Properties;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.util.RepoLayoutUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.artifactory.metadata.service.provider.MavenGroupUtils.getModuleInfo;

/**
 * A supported lead artifact path (based on the gradle-default layout), is:
 * quartetfs.util/scheduled-tasks/3.3/scheduled-tasks-3.3.pom
 * which differs from Maven only in the dot being used as a separator between the Group ID elements, instead of slashes
 *
 * @author Uriah Levy
 */
@Component
public class GradleMetadataProvider extends GAVCMetadataProvider {
    private static final Logger log = LoggerFactory.getLogger(GradleMetadataProvider.class);
    private static final RepoType type = RepoType.Gradle;
    private static final String LEAD_FILE_EXTENSION = "pom";
    private final InternalRepositoryService repositoryService;

    @Autowired
    GradleMetadataProvider(InternalRepositoryService repositoryService) {
        super(type);
        this.repositoryService = repositoryService;
        setTypeSpecificMetadataTemplate(new GradleTypeSpecificMetadataTemplate());
    }

    @Override
    public Optional<AqlApiItem> buildVersionFilesQuery(@Nonnull RepoPath leadArtifactRepoPath) {
        if (leadArtifactRepoPath.getParent() == null) {
            // Root is not supported.
            return Optional.empty();
        }

        if (!isLeadArtifactMatchRepoLayout(repositoryService,
                leadArtifactRepoPath, RepoLayoutUtils.MAVEN_2_DEFAULT_NAME)) {
            log.debug("Skipping version files collection for Gradle package {} - Repository Layout is not {}",
                    leadArtifactRepoPath, RepoLayoutUtils.MAVEN_2_DEFAULT_NAME);
            return Optional.empty();
        }

        return getModuleInfo(leadArtifactRepoPath, repositoryService)
                .flatMap(info -> AqlUtils.getDirectChildrenByModuleAndVersionQuery(leadArtifactRepoPath, info));
    }

    @Data
    class GradleTypeSpecificMetadataTemplate implements TypeSpecificMetadataTemplate {
        @Nullable
        @Override
        public String getPkgidSuffix(RepoPath repoPath, Properties properties) {
            return getModuleInfo(repoPath, repositoryService).map(info -> getPkgIdSuffixFromLayout(info, repoPath))
                    .orElse(null);
        }

        @Nullable
        @Override
        public String getVersion(RepoPath repoPath, Properties properties) {
            return getModuleInfo(repoPath, repositoryService).map(ModuleInfo::getBaseRevision).orElse(null);
        }

        @Override
        public Set<String> getExcludedUserProperties() {
            return new HashSet<>();
        }

        @Override
        public Set<String> getLeadFileExtensions() {
            return Set.of(LEAD_FILE_EXTENSION);
        }

        //The Name and the PkgidSuffix are the same
        @Override
        public String getName(Properties properties, RepoPath repoPath) {
            return getPkgidSuffix(repoPath, properties);
        }
    }
}
