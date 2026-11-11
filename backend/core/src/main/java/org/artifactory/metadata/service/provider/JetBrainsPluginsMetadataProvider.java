package org.artifactory.metadata.service.provider;

import io.micrometer.common.util.StringUtils;
import jakarta.annotation.Nullable;
import lombok.Data;
import org.artifactory.md.Properties;
import org.artifactory.repo.RepoPath;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.artifactory.descriptor.repo.RepoType.JetBrainsPlugins;

/**
 * @author shahafg
 */
@Component
public class JetBrainsPluginsMetadataProvider extends AbstractMetadataProvider {
    private static final String LEAD_FILE_EXTENSION = ".zip";

    JetBrainsPluginsMetadataProvider() {
        super(JetBrainsPlugins);
        setTypeSpecificMetadataTemplate(new JetBrainsPluginsTypeSpecificMetadataTemplate());
    }

    @Override
    public boolean isLeadArtifact(RepoPath repoPath) {
        return repoPath.getPath().endsWith(LEAD_FILE_EXTENSION);
    }

    @Override
    public boolean isPackageConsumptionLead(RepoPath repoPath) {
        return repoPath.getPath().endsWith(LEAD_FILE_EXTENSION);
    }

    @Data
    class JetBrainsPluginsTypeSpecificMetadataTemplate implements TypeSpecificMetadataTemplate {

        private static final String TAGS_PROPS = createPropKey("tags");
        private static final String TAG_SEPARATOR = ",";
        private static final Set<String> excludedUserProperties =
                Set.of(createPropKey("name"), createPropKey("version"), TAGS_PROPS);


        @Nullable
        @Override
        public String getPkgidSuffix(RepoPath repoPath, Properties properties) {
            return getJetBrainsPluginsPkgidSuffix(properties);
        }

        @Nullable
        @Override
        public String getVersion(RepoPath repoPath, Properties properties) {
            return getJetBrainsPluginsVersion(properties);
        }

        @Override
        public Optional<String> getNameKey() {
            return Optional.of(JetBrainsPlugins.getType() + NAME_SUFFIX);
        }

        @Override
        public Optional<String> getVersionKey() {
            return Optional.of(JetBrainsPlugins.getType() + VERSION_SUFFIX);
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
        public Optional<String> getTagKey() {
            return Optional.of(TAGS_PROPS);
        }

        @Override
        public Function<String, Set<String>> getTagValueResolver() {
            return tagValue -> {
                if (StringUtils.isNotBlank(tagValue)) {
                    String normalizedTags = tagValue.replaceAll("[\\[\\]\"]", "");
                    String[] tags = normalizedTags.split(TAG_SEPARATOR);
                    if (tags.length > 0) {
                        return Arrays.stream(tags)
                                .map(String::trim)
                                .filter(tag -> !tag.isEmpty())
                                .collect(Collectors.toSet());
                    }
                }
                return new HashSet<>();
            };
        }

        private String getJetBrainsPluginsPkgidSuffix(Properties properties) {
            return getNameKey().map(nameKey -> valueForKey(properties, nameKey)).orElse(null);
        }

        private String getJetBrainsPluginsVersion(Properties properties) {
            return getVersionKey().map(versionKey -> valueForKey(properties, versionKey)).orElse(null);
        }

        private static String createPropKey(String key) {
            return String.join(".", JetBrainsPlugins.getType(), key);
        }
    }
}
