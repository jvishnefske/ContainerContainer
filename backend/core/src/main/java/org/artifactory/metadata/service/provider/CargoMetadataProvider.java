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
 * @author Rotem Kfir
 */
@Component
public class CargoMetadataProvider extends AbstractMetadataProvider {
    private static final String LEAD_FILE_EXTENSION = "crate";

    CargoMetadataProvider() {
        super(RepoType.Cargo);
        setTypeSpecificMetadataTemplate(new CargoTypeSpecificMetadataTemplate());
    }

    @Data
    class CargoTypeSpecificMetadataTemplate implements TypeSpecificMetadataTemplate {
        private static final String PROPERTY_PREFIX = "crate";
        private Set<String> excludedUserProperties = Set.of(PROPERTY_PREFIX + NAME_SUFFIX,
                PROPERTY_PREFIX + VERSION_SUFFIX, PROPERTY_PREFIX + DESCRIPTION_SUFFIX);

        @Override
        public Optional<String> getNameKey() {
            return Optional.of(PROPERTY_PREFIX + NAME_SUFFIX);
        }

        @Override
        public Optional<String> getVersionKey() {
            return Optional.of(PROPERTY_PREFIX + VERSION_SUFFIX);
        }

        @Override
        public Optional<String> getDescriptionKey() {
            return Optional.of(PROPERTY_PREFIX + DESCRIPTION_SUFFIX);
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
    }
}
