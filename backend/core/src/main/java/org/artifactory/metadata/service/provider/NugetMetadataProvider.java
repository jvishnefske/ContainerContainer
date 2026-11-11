package org.artifactory.metadata.service.provider;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.md.Properties;
import org.artifactory.repo.RepoPath;
import org.springframework.stereotype.Component;

import jakarta.annotation.Nullable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Uriah Levy
 */
@Component
public class NugetMetadataProvider extends AbstractMetadataProvider {
    private static final RepoType type = RepoType.NuGet;
    private static final String LEAD_FILE_EXTENSION = "nupkg";

    NugetMetadataProvider() {
        super(type);
        setTypeSpecificMetadataTemplate(new NugetTypeSpecificMetadataTemplate());
    }

    @Data
    class NugetTypeSpecificMetadataTemplate implements TypeSpecificMetadataTemplate {
        private static final String NAME_SUFFIX = ".id";
        private static final String TAG_SEPARATOR = " ";
        private final String descriptionKey = type.getType() + DESCRIPTION_SUFFIX;
        private final String tagKey = type.getType() + ".tags";
        private Set<String> excludedUserProperties = Set.of(type.getType() + NAME_SUFFIX,
                type.getType() + VERSION_SUFFIX, descriptionKey,
                        type.getType() + ".digest", type.getType() + ".summary", type.getType() + ".tags",
                        type.getType() + ".dependency");

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
            return Optional.of(type.getType() + NAME_SUFFIX);
        }

        @Override
        public Optional<String> getVersionKey() {
            return Optional.of(type.getType() + VERSION_SUFFIX);
        }

        @Override
        public Optional<String> getDescriptionKey() {
            return Optional.of(descriptionKey);
        }

        @Override
        public Optional<String> getTagKey() {
            return Optional.of(tagKey);
        }

        @Override
        public Function<String, Set<String>> getTagValueResolver() {
            return tagValue -> {
                if (StringUtils.isNotBlank(tagValue)) {
                    String[] tags = tagValue.split(TAG_SEPARATOR);
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
