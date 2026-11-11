package org.artifactory.metadata.service.provider;

import lombok.Data;
import org.artifactory.api.module.ModuleInfo;
import org.artifactory.aql.api.domain.sensitive.AqlApiItem;
import org.artifactory.aql.api.internal.AqlBase;
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
import java.util.Optional;
import java.util.Set;

import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.create;
import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.name;
import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.path;
import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.repo;
import static org.artifactory.aql.api.internal.AqlBase.and;
import static org.artifactory.metadata.service.provider.MavenGroupUtils.getModuleInfo;

/**
 * @author Uriah Levy
 */
@Component
public class GoMetadataProvider extends AbstractMetadataProvider {
    private static final Logger log = LoggerFactory.getLogger(GoMetadataProvider.class);
    private static final RepoType type = RepoType.Go;
    private static final String LEAD_FILE_EXTENSION = "mod";
    private final InternalRepositoryService repositoryService;

    @Autowired
    GoMetadataProvider(InternalRepositoryService repositoryService) {
        super(type);
        setTypeSpecificMetadataTemplate(new GoTypeSpecificMetadataTemplate());
        this.repositoryService = repositoryService;
    }

    @Override
    public Optional<AqlApiItem> buildVersionFilesQuery(@Nonnull RepoPath leadArtifactRepoPath) {
        if (leadArtifactRepoPath.getParent() == null) {
            // Root is not supported.
            return Optional.empty();
        }

        if (!isLeadArtifactMatchRepoLayout(repositoryService, leadArtifactRepoPath, RepoLayoutUtils.GO_DEFAULT_NAME)) {
            log.debug("Skipping version files collection for Go package {} - Repository Layout is not {}",
                    leadArtifactRepoPath, RepoLayoutUtils.GO_DEFAULT_NAME);
            return Optional.empty();
        }

        Optional<ModuleInfo> moduleInfo = getModuleInfo(leadArtifactRepoPath, repositoryService);
        if (moduleInfo.isPresent()) {
            String baseRevision = moduleInfo.get().getBaseRevision();
            AqlBase.AndClause<AqlApiItem> repoPathAndNameCriteria = and(
                    repo().equal(leadArtifactRepoPath.getRepoKey()),
                    path().equal(leadArtifactRepoPath.getParent().getPath())
            );
            if (moduleInfo.get().isIntegration()) {
                String fileIntegrationRevision = moduleInfo.get().getFileIntegrationRevision();
                repoPathAndNameCriteria
                        .append(name().matches("v" + baseRevision + "-" + fileIntegrationRevision + "*"));
            } else {
                repoPathAndNameCriteria.append(name().matches("v" + baseRevision + "*"));
            }
            return Optional.of(create().filter(repoPathAndNameCriteria));
        }
        log.warn("Module Info cannot be extracted from go-default layout - version files will not be resolved");
        return Optional.empty();
    }

    @Data
    class GoTypeSpecificMetadataTemplate implements TypeSpecificMetadataTemplate {
        private Set<String> excludedUserProperties = Set.of(type.getType() + NAME_SUFFIX,
                type.getType() + VERSION_SUFFIX);

        @Override
        public Optional<String> getNameKey() {
            return Optional.of(type.getType() + NAME_SUFFIX);
        }

        @Override
        public Optional<String> getVersionKey() {
            return Optional.of(type.getType() + VERSION_SUFFIX);
        }

        @Override
        public Set<String> getExcludedUserProperties() {
            return excludedUserProperties;
        }

        @Override
        public Set<String> getLeadFileExtensions() {
            return Set.of(LEAD_FILE_EXTENSION);
        }

        @Override
        public String getName(Properties properties, RepoPath repoPath) {
            return getNameKey().map(nameKey -> valueForKey(properties, nameKey)).orElse(null);
        }

        @Nullable
        @Override
        public String getVersion(RepoPath repoPath, Properties properties) {
            return getVersionKey().map(versionKey -> valueForKey(properties, versionKey)).orElse(null);
        }

        @Nullable
        @Override
        public String getPkgidSuffix(RepoPath repoPath, Properties properties) {
            return getNameKey().map(nameKey -> valueForKey(properties, nameKey)).orElse(null);
        }
    }
}
