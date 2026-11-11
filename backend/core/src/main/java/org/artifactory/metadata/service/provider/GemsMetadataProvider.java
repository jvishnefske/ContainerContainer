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
 * @author Uriah Levy
 */
@Component
public class GemsMetadataProvider extends AbstractMetadataProvider {
    private static final RepoType type = RepoType.Gems;
    private static final String LEAD_FILE_EXTENSION = "gem";
    private static final String RUBY_GEMS = "rubygems";

    GemsMetadataProvider() {
        super(type);
        setTypeSpecificMetadataTemplate(new GemsTypeSpecificMetadataTemplate());
    }

    @Override
    protected String getType(@Nonnull Properties properties) {
        return RUBY_GEMS;
    }

    @Data
    class GemsTypeSpecificMetadataTemplate implements TypeSpecificMetadataTemplate {
        private static final String GEM = "gem";
        private Set<String> excludedUserProperties = Set.of(type.getType() + NAME_SUFFIX,
                type.getType() + VERSION_SUFFIX);

        @Nullable
        @Override
        public String getPkgidSuffix(RepoPath repoPath, Properties properties) {
            return getNameKey().map(nameKey -> valueForKey(properties, nameKey)).orElse(null);
        }

        @Nullable
        @Override
        public String getVersion(RepoPath repoPath, Properties properties) {
            return getVersionKey().map(versionKey -> valueForKey(properties, versionKey)).orElse(null);
        }

        @Override
        public Optional<String> getNameKey() {
            return Optional.of(GEM + NAME_SUFFIX);
        }

        @Override
        public Optional<String> getVersionKey() {
            return Optional.of(GEM + VERSION_SUFFIX);
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
    }
}

