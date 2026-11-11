package org.artifactory.metadata.service.provider;

import jakarta.annotation.Nullable;
import lombok.Data;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.md.Properties;
import org.artifactory.repo.RepoPath;
import org.artifactory.util.RepoPathUtils;
import org.springframework.stereotype.Component;

import java.util.Set;


@Component
public class JemMetadataProvider extends AbstractMetadataProvider {

    private static final RepoType type = RepoType.Jem;
    private static final String LEAD_FILE_EXTENSION = "json";
    private static final String LEAD_FILE_NAME = "version.json";

    JemMetadataProvider() {
        super(type);
        setTypeSpecificMetadataTemplate(new JemTypeSpecificMetadataTemplate());
    }

    /**
     * Lead artifact layout is
     * <repo></repo>/<name>/<version>/version.json
     */
    @Override
    public boolean isLeadArtifact(RepoPath repoPath) {
        return repoPath.getName().equals(LEAD_FILE_NAME) && RepoPathUtils.getPathDeeps(repoPath) == 4;
    }

    @Data
    class JemTypeSpecificMetadataTemplate implements TypeSpecificMetadataTemplate {

        @Override
        public Set<String> getExcludedUserProperties() {
            return Set.of();
        }

        @Override
        public Set<String> getLeadFileExtensions() {
            return Set.of(LEAD_FILE_EXTENSION);
        }

        @Override
        public String getName(Properties properties, RepoPath repoPath) {
            if (!isLeadArtifact(repoPath)) {
                return null;
            }
            RepoPath namePath = RepoPathUtils.getAncestor(repoPath, 2);
            if (namePath != null) {
                return namePath.getName();
            }
            return null;
        }

        @Nullable
        @Override
        public String getVersion(RepoPath repoPath, Properties properties) {
            if (!isLeadArtifact(repoPath)) {
                return null;
            }
            RepoPath parent = repoPath.getParent();
            if (parent != null) {
                return parent.getName();
            }
            return null;
        }
    }
}