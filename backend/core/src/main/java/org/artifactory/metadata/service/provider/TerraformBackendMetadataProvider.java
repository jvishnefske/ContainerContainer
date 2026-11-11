package org.artifactory.metadata.service.provider;

import jakarta.annotation.Nullable;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.md.Properties;
import org.artifactory.repo.RepoPath;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Asafz
 */
@Component
public class TerraformBackendMetadataProvider extends AbstractMetadataProvider {
    private static final RepoType type = RepoType.TerraformBackend;
    private static final String TERRAFORM = "terraform";
    private static final String JSON = "json";
    private static final String DOT_JSON = ".json";

    private static final String STATE = "state";
    private static final String STATE_DOT = "state.";
    private static final Pattern versionPattern = Pattern.compile("^state\\.(\\d+)\\.json$");


    TerraformBackendMetadataProvider() {
        super(type);
        setTypeSpecificMetadataTemplate(new TerraformBackendTypeSpecificMetadataTemplate());
    }

    @Override
    public boolean isLeadArtifact(RepoPath repoPath) {
        String fileName = repoPath.getName();
        return fileName.startsWith(STATE) && fileName.endsWith(DOT_JSON) && (getVersionFromRepoPath(repoPath) != null);
    }

    private static String getVersionFromRepoPath(RepoPath repoPath) {
        if ("state.latest.json".equals(repoPath.getName())) {
            return "latest";
        }
        Matcher matcher = versionPattern.matcher(repoPath.getName());
        if (matcher.matches()) {
            return matcher.group(1);
        }
        return null;
    }

    @Data
    class TerraformBackendTypeSpecificMetadataTemplate implements TypeSpecificMetadataTemplate {
        private static final String PATH_SEPARATOR = "/";
        private static final Set<String> LEAD_FILE_EXTENSION = Set.of(JSON);
        private Set<String> excludedUserProperties = Set.of(TERRAFORM + ".creator", TERRAFORM + ".time.created");

        /**
         * Return the workspace name
         */
        @Override
        public String getName(Properties properties, RepoPath repoPath) {
            if (!isLeadArtifact(repoPath)) {
                return null;
            }
            return StringUtils.substringBetween(repoPath.getPath(), "workspaces/", "/" + STATE_DOT);
        }

        @Nullable
        @Override
        public String getVersion(RepoPath repoPath, Properties properties) {
            return getVersionFromRepoPath(repoPath);
        }

        @Override
        public Set<String> getExcludedUserProperties() {
            return excludedUserProperties;
        }

        @Override
        public Set<String> getLeadFileExtensions() {
            return LEAD_FILE_EXTENSION;
        }
    }
}
