package org.artifactory.metadata.service.provider;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.md.Properties;
import org.artifactory.repo.RepoPath;
import org.springframework.stereotype.Component;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Uriah Levy
 */
@Component
class NpmMetadataProvider extends AbstractMetadataProvider {

    private static final RepoType type = RepoType.Npm;
    private static final String LEAD_FILE_EXTENSION = "tgz";

    NpmMetadataProvider() {
        super(type);
        setTypeSpecificMetadataTemplate(new NpmTypeSpecificMetadataTemplate());
    }

    @Data
    class NpmTypeSpecificMetadataTemplate implements TypeSpecificMetadataTemplate {
        private final String descriptionKey = type.getType() + DESCRIPTION_SUFFIX;
        private Function<String, String> qualifierValueResolver;
        private Map<String, String> qualifierKeysToMdsKeys = Map.of(type.getType() + NAME_SUFFIX, "npm_scope");
        private String tagKey = type.getType() + KEYWORDS_SUFFIX;
        private static final String TAG_SEPARATOR = ",";
        private Set<String> excludedUserProperties = Set.of(type.getType() + NAME_SUFFIX,
                type.getType() + VERSION_SUFFIX, descriptionKey, type.getType() + KEYWORDS_SUFFIX);
        private static final String EMPTY_TAG_OR_DESCRIPTION = "no_content";

        NpmTypeSpecificMetadataTemplate() {
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
        public Map<String, String> getQualifierKeysToMdsKeys() {
            return qualifierKeysToMdsKeys;
        }

        @Override
        public Optional<String> getTagKey() {
            return Optional.ofNullable(tagKey);
        }

        @Override
        public Function<String, String> getQualifierValueResolver() {
            return rawValue -> {
                if (StringUtils.isNotBlank(rawValue) && rawValue.startsWith("@")) {
                    // "@scope/pkg" -> "scope"
                    return StringUtils.substringBefore(rawValue, "/").substring(1);
                }
                return "";
            };
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
                                .filter(tag -> !tag.equals(EMPTY_TAG_OR_DESCRIPTION))
                                .collect(Collectors.toSet());
                    }
                }
                return new HashSet<>();
            };
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
        public boolean acceptsDescription(@Nonnull String description) {
            return !EMPTY_TAG_OR_DESCRIPTION.equals(description);
        }

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
    }
}
