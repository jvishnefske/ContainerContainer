package org.artifactory.metadata.service.provider;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.md.Properties;
import org.artifactory.repo.RepoPath;
import org.springframework.stereotype.Component;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Einas Ashkar
 */

@Component
@Slf4j
public class NimModelMetadataProvider extends AbstractMetadataProvider {
    static final RepoType type = RepoType.NimModel;
    static final String MODEL_LEAD_FILE_NAME = ".jfrog_nim_model_info.json";

    NimModelMetadataProvider() {
        super(type);
        setTypeSpecificMetadataTemplate(new NimModelTypeSpecificMetadataTemplate());
    }

    @Override
    public boolean isLeadArtifact(RepoPath repoPath) {
        return repoPath.getPath().endsWith(MODEL_LEAD_FILE_NAME);
    }

    @Override
    public boolean isPackageConsumptionLead(RepoPath repoPath) {
        return isLeadArtifact(repoPath);
    }

    @Data
    class NimModelTypeSpecificMetadataTemplate implements TypeSpecificMetadataTemplate {

        private static final String NAME_SUFFIX = ".name";
        private static final String VERSION_SUFFIX = ".versionId";
        private static final String TAG_SEPARATOR = ",";
        private final String descriptionKey = type.getType() + ".shortDescription";
        private final String tagKey = type.getType() + ".labels";
        private Set<String> excludedUserProperties = Set.of(type.getType() + NAME_SUFFIX,
                type.getType() + VERSION_SUFFIX, descriptionKey, tagKey);

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
            return Set.of(MODEL_LEAD_FILE_NAME);
        }

        @Override
        public String getName(Properties properties, RepoPath repoPath) {
            return getNameKey().map(nameKey -> valueForKey(properties, nameKey)).orElse(null);

        }

    }
}
