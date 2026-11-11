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

import static org.artifactory.metadata.service.provider.TypeSpecificMetadataTemplate.DESCRIPTION_SUFFIX;
import static org.artifactory.metadata.service.provider.TypeSpecificMetadataTemplate.VERSION_SUFFIX;

/**
 * @author BarakH
 */
@Component
public class AnsibleMetadataProvider extends AbstractMetadataProvider {

    private static final RepoType type = RepoType.Ansible;
    private static final String FULLNAME_SUFFIX = ".fullname";
    private static final String TAGS_SUFFIX = ".tags";
    private static final String LEAD_FILE_EXTENSION = "tar.gz";
    private static final String COMMA = ",";
    private final Set<String> excludedUserProperties =
            Set.of(type.getType() + FULLNAME_SUFFIX, type.getType() + VERSION_SUFFIX,
                   type.getType() + DESCRIPTION_SUFFIX, type.getType() + TAGS_SUFFIX);

    AnsibleMetadataProvider() {
        super(type);
        setTypeSpecificMetadataTemplate(new AnsibleTypeSpecificMetadataTemplate());
    }

    @Data
    class AnsibleTypeSpecificMetadataTemplate implements TypeSpecificMetadataTemplate {

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
            return Optional.of(type.getType() + DESCRIPTION_SUFFIX);
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
            return getNameKey().map(nameKey -> valueForKey(properties, type.getType() + FULLNAME_SUFFIX)).orElse(null);
        }

        @Nullable
        @Override
        public String getVersion(RepoPath repoPath, Properties properties) {
            return getNameKey().map(nameKey -> valueForKey(properties, type.getType() + VERSION_SUFFIX)).orElse(null);
        }

        @Nullable
        @Override
        public String getPkgidSuffix(RepoPath repoPath, Properties properties) {
            return getName(properties, repoPath);
        }

        @Override
        public Optional<String> getTagKey() {
            return Optional.of(type.getType() + TAGS_SUFFIX);
        }

        @Override
        public Function<String, Set<String>> getTagValueResolver() {
            return tagValue -> {
                if (StringUtils.isNotBlank(tagValue)) {
                    String normalizedTags = tagValue.replaceAll("[\\[\\]\"]", "");
                    String[] tags = normalizedTags.split(COMMA);
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
    }
}
