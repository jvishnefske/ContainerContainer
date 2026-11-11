package org.artifactory.metadata.service.provider;

import com.google.common.collect.ImmutableSet;
import jakarta.annotation.Nonnull;
import lombok.Data;
import org.artifactory.api.module.ModuleInfo;
import org.artifactory.aql.api.domain.sensitive.AqlApiItem;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.md.Properties;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.util.RepoLayoutUtils;
import org.jfrog.client.util.PathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.artifactory.metadata.service.provider.MavenGroupUtils.getIvyVersionFileQuery;
import static org.artifactory.metadata.service.provider.MavenGroupUtils.getModuleInfo;
import static org.artifactory.metadata.service.provider.MavenGroupUtils.getPomVersionFileQuery;

/**
 * @author Yuval Reches
 */
@Component
@Lazy
public class SbtMetadataProvider extends GAVCMetadataProvider {
    private static final Logger log = LoggerFactory.getLogger(SbtMetadataProvider.class);
    private static final RepoType type = RepoType.SBT;
    private static final String LEAD_FILE_EXTENSION = "xml";
    private static final String LEAD_FILE_EXTENSION_POM = "pom";
    private final InternalRepositoryService repositoryService;
    private static final Set<String> EXPECTED_LAYOUTS = Set.of(
            RepoLayoutUtils.MAVEN_2_DEFAULT_NAME,
            RepoLayoutUtils.SBT_DEFAULT_NAME,
            RepoLayoutUtils.IVY_DEFAULT_NAME,
            RepoLayoutUtils.SBT_IVY_DEFAULT_NAME
    );

    @Autowired
    SbtMetadataProvider(InternalRepositoryService repositoryService) {
        super(type);
        setTypeSpecificMetadataTemplate(new SbtTypeSpecificMetadataTemplate());
        this.repositoryService = repositoryService;
    }

    /**
     * Lead artifact for SBT can be either *.pom file, or ivy.xml file (according to SBT documentation that's the only
     * option for Ivy, while for Ivy itself the file's name can be many variations of ivy{.+}.xml)
     */
    @Override
    public boolean isLeadArtifact(RepoPath repoPath) {
        return isPomLead(repoPath) || isIvyLead(repoPath);
    }

    /**
     * Looking for version file list according to type of lead artifact.
     * See documentation under the {@link AbstractMetadataProvider#buildVersionFilesQuery} of the Ivy or Maven type.
     */
    @Override
    public Optional<AqlApiItem> buildVersionFilesQuery(@Nonnull RepoPath leadArtifactRepoPath) {
        if (!isLeadArtifactMatchRepoLayouts(repositoryService, leadArtifactRepoPath, EXPECTED_LAYOUTS)) {
            log.debug("Skipping version files collection for SBT package {} - Repository Layout is not one of {}",
                    leadArtifactRepoPath, String.join(", ", EXPECTED_LAYOUTS));
            return Optional.empty();
        }
        if (isPomLead(leadArtifactRepoPath)) {
            return getPomVersionFileQuery(leadArtifactRepoPath, repositoryService);
        } else if (isIvyLead(leadArtifactRepoPath)) {
            return getIvyVersionFileQuery(leadArtifactRepoPath);
        }
        log.debug("The lead artifact '{}' is not of type pom or ivy.xml, can't generate a file list",
                leadArtifactRepoPath);
        return Optional.empty();
    }

    private boolean isIvyLead(RepoPath repoPath) {
        return repoPath.getName().startsWith("ivy") && repoPath.getName().endsWith(".xml");
    }

    private boolean isPomLead(RepoPath repoPath) {
        String extension = PathUtils.getExtension(repoPath.getPath());
        return isPomLeadExtension(repoPath, extension, getLeadArtifactExtensions());
    }

    @Data
    class SbtTypeSpecificMetadataTemplate implements TypeSpecificMetadataTemplate {
        @Override
        public Set<String> getExcludedUserProperties() {
            return new HashSet<>();
        }

        @Override
        public Set<String> getLeadFileExtensions() {
            return ImmutableSet.of(LEAD_FILE_EXTENSION, LEAD_FILE_EXTENSION_POM);
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
            return getModuleInfo(repoPath, repositoryService)
                    .map(info -> getPkgIdSuffixFromLayout(info, repoPath)).orElse(null);
        }
    }
}
