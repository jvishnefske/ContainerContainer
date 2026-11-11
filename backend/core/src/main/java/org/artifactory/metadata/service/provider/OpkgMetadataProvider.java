package org.artifactory.metadata.service.provider;

import lombok.Data;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.md.Properties;
import org.artifactory.repo.RepoPath;
import org.springframework.stereotype.Component;

import jakarta.annotation.Nullable;
import java.util.Optional;
import java.util.Set;

/**
 * @author Yuval Reches
 */
@Component
public class OpkgMetadataProvider extends AbstractMetadataProvider {
    private static final RepoType type = RepoType.Opkg;
    private static final String LEAD_FILE_EXTENSION = "ipk";

    OpkgMetadataProvider() {
        super(type);
        setTypeSpecificMetadataTemplate(new OpkgTypeSpecificMetadataTemplate());
    }

    @Data
    class OpkgTypeSpecificMetadataTemplate implements TypeSpecificMetadataTemplate {
        private Set<String> excludedUserProperties =
                Set.of(type.getType() + NAME_SUFFIX, type.getType() + VERSION_SUFFIX);

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
