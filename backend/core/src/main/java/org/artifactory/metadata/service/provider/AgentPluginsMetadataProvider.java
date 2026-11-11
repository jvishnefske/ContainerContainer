package org.artifactory.metadata.service.provider;

import jakarta.annotation.Nullable;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.md.Properties;
import org.artifactory.repo.RepoPath;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

/**
 * @author Barak Hacham
 */
@Component
public class AgentPluginsMetadataProvider extends AbstractMetadataProvider {

    private static final RepoType type = RepoType.AgentPlugins;
    private static final String AGENT_PLUGIN_PREFIX = "agentplugins";
    private static final String LEAD_FILE_EXTENSION = "zip";

    AgentPluginsMetadataProvider() {
        super(type);
        setTypeSpecificMetadataTemplate(new AgentPluginsTypeSpecificMetadataTemplate());
    }

    private class AgentPluginsTypeSpecificMetadataTemplate implements TypeSpecificMetadataTemplate {
        private final Set<String> excludedUserProperties = Set.of(AGENT_PLUGIN_PREFIX + NAME_SUFFIX,
                AGENT_PLUGIN_PREFIX + VERSION_SUFFIX, AGENT_PLUGIN_PREFIX + DESCRIPTION_SUFFIX);

        @Override
        public Optional<String> getNameKey() {
            return key(NAME_SUFFIX);
        }

        @Override
        public Optional<String> getVersionKey() {
            return key(VERSION_SUFFIX);
        }

        @Override
        public Optional<String> getDescriptionKey() {
            return key(DESCRIPTION_SUFFIX);
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
            return Optional.of(AGENT_PLUGIN_PREFIX + suffix);
        }

        private String valueFromKey(Optional<String> key, Properties properties) {
            return key.map(k -> valueForKey(properties, k)).orElse(null);
        }

    }
}
