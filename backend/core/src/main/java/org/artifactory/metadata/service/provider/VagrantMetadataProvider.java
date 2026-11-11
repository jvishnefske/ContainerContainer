package org.artifactory.metadata.service.provider;

import com.google.common.collect.ImmutableSet;
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
public class VagrantMetadataProvider extends AbstractMetadataProvider {
    private static final RepoType type = RepoType.Vagrant;
    private static final String LEAD_FILE_EXTENSION = "box";

    VagrantMetadataProvider() {
        super(type);
        setTypeSpecificMetadataTemplate(new VagrantTypeSpecificMetadataTemplate());
    }

    @Data
    class VagrantTypeSpecificMetadataTemplate implements TypeSpecificMetadataTemplate {
        private static final String BOX = "box";
        private static final String NAME_SUFFIX = "_name";
        private static final String VERSION_SUFFIX = "_version";
        private Set<String> excludedUserProperties = ImmutableSet
                .of(BOX + NAME_SUFFIX, BOX + VERSION_SUFFIX);

        @Override
        public Optional<String> getNameKey() {
            return Optional.of(BOX + NAME_SUFFIX);
        }

        @Override
        public Optional<String> getVersionKey() {
            return Optional.of(BOX + VERSION_SUFFIX);
        }

        @Override
        public Set<String> getExcludedUserProperties() {
            return excludedUserProperties;
        }

        @Override
        public Set<String> getLeadFileExtensions() {
            return ImmutableSet.of(LEAD_FILE_EXTENSION);
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
