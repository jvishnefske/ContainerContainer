package org.artifactory.metadata.service.provider;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.Data;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.md.Properties;
import org.artifactory.repo.RepoPath;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

/**
 * @author Natalie Kirillov
 */

@Component
public class SwiftMetadataProvider extends AbstractMetadataProvider {
    private static final RepoType type = RepoType.Swift;
    private static final String LEAD_FILE_EXTENSION = "zip";

    SwiftMetadataProvider() {
        super(type);
        setTypeSpecificMetadataTemplate(new SwiftMetadataProvider.SwiftTypeSpecificMetadataTemplate());
    }

    @Override
    protected String getType(@Nonnull Properties properties) {
        return type.getPkgidPrefix();
    }

    @Data
    class SwiftTypeSpecificMetadataTemplate implements TypeSpecificMetadataTemplate {
        private Set<String> excludedUserProperties = Set.of(
                type.getType() + NAME_SUFFIX,
                type.getType() + VERSION_SUFFIX);

        @Nullable
        @Override
        public String getVersion(RepoPath repoPath, Properties properties) {
            return getVersionKey().map(versionKey -> valueForKey(properties, versionKey)).orElse(null);
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

        @Override
        public Optional<String> getNameKey() {
            return Optional.of(type.getType() + NAME_SUFFIX);
        }

        @Override
        public Optional<String> getVersionKey() {
            return Optional.of(type.getType() + VERSION_SUFFIX);
        }
    }
}
