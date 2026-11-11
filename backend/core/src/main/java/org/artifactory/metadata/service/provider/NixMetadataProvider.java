package org.artifactory.metadata.service.provider;

import lombok.Data;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.md.Properties;
import org.artifactory.repo.RepoPath;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

/**
 * @author Linoy Levi
 */
@Component
public class NixMetadataProvider extends AbstractMetadataProvider {

    private static final RepoType type = RepoType.Nix;
    private static final String LEAD_FILE_EXTENSION = "narinfo";

    NixMetadataProvider() {
        super(type);
        setTypeSpecificMetadataTemplate(new NixTypeSpecificMetadataTemplate());
    }

    @Data
    class NixTypeSpecificMetadataTemplate implements TypeSpecificMetadataTemplate {
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
        public String getName(Properties properties, RepoPath repoPath) {
            return getNameKey().map(nameKey -> valueForKey(properties, nameKey)).orElse(null);
        }

        @Override
        @Nullable
        public String getVersion(RepoPath repoPath, Properties properties) {
            return getVersionKey().map(versionKey -> valueForKey(properties, versionKey)).orElse(null);
        }

        @Override
        public Set<String> getLeadFileExtensions() {
            return Set.of(LEAD_FILE_EXTENSION);
        }
    }
}
