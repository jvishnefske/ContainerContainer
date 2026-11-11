package org.artifactory.metadata.service.provider;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.apache.commons.lang3.Strings;
import org.artifactory.aql.api.domain.sensitive.AqlApiItem;
import org.artifactory.aql.api.internal.AqlBase;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.md.Properties;
import org.artifactory.repo.RepoPath;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.create;
import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.name;
import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.path;
import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.repo;
import static org.artifactory.aql.api.internal.AqlBase.and;

@Component
public class LuaRocksMetadataProvider extends AbstractMetadataProvider {

    private static final RepoType type = RepoType.LuaRocks;
    private static final String LEAD_FILE_EXTENSION = "rockspec";

    LuaRocksMetadataProvider() {
        super(type);
        setTypeSpecificMetadataTemplate(new LuaRocksTypeSpecificMetadataTemplate());
    }

    @Override
    public Optional<AqlApiItem> buildVersionFilesQuery(@Nonnull RepoPath leadArtifactRepoPath) {
        if (leadArtifactRepoPath.getParent() == null) {
            return Optional.empty();
        }
        String baseName = Strings.CS.removeEnd(leadArtifactRepoPath.getName(), "." + LEAD_FILE_EXTENSION);
        AqlBase.AndClause<AqlApiItem> repoPathAndNameCriteria = and(
                repo().equal(leadArtifactRepoPath.getRepoKey()),
                path().equal(leadArtifactRepoPath.getParent().getPath()),
                name().matches(baseName + "*")
        );
        return Optional.of(create().filter(repoPathAndNameCriteria));
    }

    private class LuaRocksTypeSpecificMetadataTemplate implements TypeSpecificMetadataTemplate {
        private static final String ARCH_SUFFIX = ".arch";
        private static final String SUMMARY_SUFFIX = ".summary";
        private final Set<String> excludedUserProperties = Set.of(type.getType() + NAME_SUFFIX,
                type.getType() + VERSION_SUFFIX);

        @Override
        public Optional<String> getNameKey() {
            return key(NAME_SUFFIX);
        }

        @Override
        public Optional<String> getVersionKey() {
            return key(VERSION_SUFFIX);
        }

        @Override
        public Optional<String> getArchitectureKey() {
            return key(ARCH_SUFFIX);
        }

        @Override
        public Optional<String> getDescriptionKey() {
            return key(SUMMARY_SUFFIX);
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
            return valueFromKey(getNameKey(), properties);
        }

        @Nullable
        @Override
        public String getVersion(RepoPath repoPath, Properties properties) {
            return valueFromKey(getVersionKey(), properties);
        }

        private Optional<String> key(String suffix) {
            return Optional.of(type.getType() + suffix);
        }

        private String valueFromKey(Optional<String> key, Properties properties) {
            return key.map(k -> valueForKey(properties, k)).orElse(null);
        }

    }
}
