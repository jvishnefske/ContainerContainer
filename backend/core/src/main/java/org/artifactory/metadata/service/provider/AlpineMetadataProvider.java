package org.artifactory.metadata.service.provider;

import lombok.Data;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.md.Properties;
import org.artifactory.repo.RepoPath;
import org.springframework.stereotype.Component;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Optional;
import java.util.Set;

/**
 * @author Yuval Reches
 */
@Component
public class AlpineMetadataProvider extends AbstractMetadataProvider {
    private static final RepoType type = RepoType.Alpine;
    private static final String LEAD_FILE_EXTENSION = "apk";

    AlpineMetadataProvider() {
        super(type);
        setTypeSpecificMetadataTemplate(new AlpineTypeSpecificMetadataTemplate());
    }

    @Override
    protected String getType(@Nonnull Properties properties) {
        return type.getPkgidPrefix();
    }

    @Data
    class AlpineTypeSpecificMetadataTemplate implements TypeSpecificMetadataTemplate {
        private static final String ALPINE = "alpine";
        private static final String DISTRIBUTION = ".branch";
        private static final String ARCHITECTURE = ".architecture";
        private Set<String> excludedUserProperties = Set.of(ALPINE + NAME_SUFFIX, ALPINE + VERSION_SUFFIX);

        @Override
        public Optional<String> getNameKey() {
            return Optional.of(ALPINE + NAME_SUFFIX);
        }

        @Override
        public Optional<String> getVersionKey() {
            return Optional.of(ALPINE + VERSION_SUFFIX);
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
        public Optional<String> getArchitectureKey() {
            return Optional.of(ALPINE + ARCHITECTURE);
        }

        @Override
        public Optional<String> getDistributionKey() {
            return Optional.of(ALPINE + DISTRIBUTION);
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
