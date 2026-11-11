package org.artifactory.metadata.service.provider;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.md.Properties;
import org.artifactory.repo.RepoPath;
import org.springframework.stereotype.Component;

import jakarta.annotation.Nullable;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * @author Uriah Levy
 */
@Component
public class CondaMetadataProvider extends AbstractMetadataProvider {
    private static final RepoType type = RepoType.Conda;
    private static final String LEAD_FILE_EXTENSION = "tar.bz2";
    private static final String CONDA_LEAD_FILE_EXTENSION = "conda";

    CondaMetadataProvider() {
        super(type);
        setTypeSpecificMetadataTemplate(new CondaTypeSpecificMetadataTemplate());
    }

    @Override
    public boolean isLeadArtifact(RepoPath repoPath) {
        String fileName = repoPath.getName();
        return StringUtils.isNotBlank(fileName) &&
                (fileName.endsWith(LEAD_FILE_EXTENSION) || fileName.endsWith(CONDA_LEAD_FILE_EXTENSION));
    }

    @Data
    class CondaTypeSpecificMetadataTemplate implements TypeSpecificMetadataTemplate {
        private static final String ARCH = ".arch";
        private static final String PLATFORM = ".platform";
        private Map<String, String> qualifierKeysToMdsKeys = Map.of(type.getType() + ARCH, "arch",
                type.getType() + PLATFORM, "platform");
        private Set<String> excludedUserProperties = Set.of(type.getType() + NAME_SUFFIX,
                type.getType() + VERSION_SUFFIX, type.getType() + DESCRIPTION_SUFFIX);

        @Override
        public Optional<String> getNameKey() {
            return Optional.of(type.getType() + NAME_SUFFIX);
        }

        @Override
        public Optional<String> getVersionKey() {
            return Optional.of(type.getType() + VERSION_SUFFIX);
        }

        @Override
        public Map<String, String> getQualifierKeysToMdsKeys() {
            return qualifierKeysToMdsKeys;
        }

        @Override
        public Optional<String> getDescriptionKey() {
            return Optional.of(type.getType() + DESCRIPTION_SUFFIX);
        }

        @Override
        public Set<String> getLeadFileExtensions() {
            return Set.of(LEAD_FILE_EXTENSION, CONDA_LEAD_FILE_EXTENSION);
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
