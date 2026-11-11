package org.artifactory.metadata.service.provider;

import lombok.Data;
import org.artifactory.api.module.ModuleInfo;
import org.artifactory.aql.api.domain.sensitive.AqlApiItem;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.ivy.IvyNaming;
import org.artifactory.md.Properties;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.util.RepoLayoutUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.Nonnull;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.artifactory.metadata.service.provider.MavenGroupUtils.getIvyVersionFileQuery;
import static org.artifactory.metadata.service.provider.MavenGroupUtils.getModuleInfo;

/**
 * @author Yuval Reches
 */
@Component
public class IvyMetadataProvider extends AbstractMetadataProvider {
    private static final Logger log = LoggerFactory.getLogger(IvyMetadataProvider.class);
    private static final RepoType type = RepoType.Ivy;
    private final InternalRepositoryService repositoryService;

    @Autowired
    IvyMetadataProvider(InternalRepositoryService repositoryService) {
        super(type);
        setTypeSpecificMetadataTemplate(new IvyTypeSpecificMetadataTemplate());
        this.repositoryService = repositoryService;
    }

    /**
     * Looking for any file that starts with "ivy-" and ends with ".xml", or "ivy.xml"
     */
    @Override
    public boolean isLeadArtifact(RepoPath repoPath) {
        return IvyNaming.isIvyFileName(repoPath.getName());
    }

    /**
     * Looking for any file under the grand parent of ivy-*.xml file
     * As the structure of Ivy is:
     * org/module/version/
     * |
     * | - ivys
     * | ----- ivy-version.xml
     * | - jars
     * | ----- module-version.jar
     *
     * So we look for any file under /version/ folder.
     */
    @Override
    public Optional<AqlApiItem> buildVersionFilesQuery(@Nonnull RepoPath leadArtifactRepoPath) {
        if (!isLeadArtifactMatchRepoLayout(repositoryService, leadArtifactRepoPath, RepoLayoutUtils.IVY_DEFAULT_NAME)) {
            log.debug("Skipping version files collection for Ivy package {} - Repository Layout is not {}",
                    leadArtifactRepoPath, RepoLayoutUtils.IVY_DEFAULT_NAME);
            return Optional.empty();
        }

        return getIvyVersionFileQuery(leadArtifactRepoPath);
    }

    @Data
    class IvyTypeSpecificMetadataTemplate implements TypeSpecificMetadataTemplate {
        @Override
        public Set<String> getExcludedUserProperties() {
            return new HashSet<>();
        }

        @Override
        public Set<String> getLeadFileExtensions() {
            return Set.of();
        }

        //The Name and the PkgidSuffix are the same
        @Override
        public String getName(Properties properties, RepoPath repoPath) {
            return getPkgidSuffix(repoPath, properties);
        }

        @Override
        public String getVersion(RepoPath repoPath, Properties properties) {
            return getModuleInfo(repoPath, repositoryService).map(ModuleInfo::getBaseRevision).orElse(null);
        }

        @Override
        public String getPkgidSuffix(RepoPath repoPath, Properties properties) {
            return getModuleInfo(repoPath, repositoryService).map(info -> getPkgIdSuffixFromLayout(info, repoPath))
                    .orElse(null);
        }
    }
}
