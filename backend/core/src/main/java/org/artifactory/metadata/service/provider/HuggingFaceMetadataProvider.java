package org.artifactory.metadata.service.provider;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.fs.FileInfo;
import org.artifactory.md.Properties;
import org.artifactory.repo.RepoPath;
import org.artifactory.storage.db.event.service.metadata.model.MutableMetadataEntityBOM;
import org.springframework.stereotype.Component;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.artifactory.descriptor.repo.RepoType.HuggingFaceML;

/**
 * @author Volodymyr Shulga
 */
@Component
@Slf4j
public class HuggingFaceMetadataProvider extends AbstractMetadataProvider {
    private static final RepoType type = HuggingFaceML;
    static final String MODEL_LEAD_FILE_NAME = ".jfrog_huggingface_model_info.json";
    static final String DATASET_LEAD_FILE_NAME = ".jfrog_huggingface_dataset_info.json";
    static final String HF_ENTITY_TYPE = "huggingfaceml.entity.type";
    static final String HFDATASET = "hfdataset";

    HuggingFaceMetadataProvider() {
        super(type);
        setTypeSpecificMetadataTemplate(new HuggingFaceTypeSpecificMetadataTemplate());
    }

    @Override
    public boolean isLeadArtifact(RepoPath repoPath) {
        return repoPath.getPath().endsWith(MODEL_LEAD_FILE_NAME) || repoPath.getPath().endsWith(DATASET_LEAD_FILE_NAME);
    }

    @Override
    public boolean isPackageConsumptionLead(RepoPath repoPath) {
        return !isLeadArtifact(repoPath);
    }

    @Override
    protected String getType(@Nonnull Properties properties) {
        String entityType = valueForKey(properties, HF_ENTITY_TYPE);
        if (entityType != null && entityType.equalsIgnoreCase("dataset")) {
            return HFDATASET;
        }
        return type.getType();
    }

    @Override
    protected void resolvePkgId(MutableMetadataEntityBOM bom, Properties properties, FileInfo fileInfo) {
        super.resolvePkgId(bom, properties, fileInfo);
        String pkgType = getType(properties);
        if (!pkgType.equals(HFDATASET)) {
            return;
        }
        String currentPkgId = bom.getPkgid();
        String newPkgId = currentPkgId.replaceFirst(HuggingFaceML.getType(), HFDATASET);
        log.debug("HuggingFace PkgId: {} for pkgName: {} pkgVersion: {} and pkgType: {}", newPkgId, bom.getName(),
                bom.getVersion(), pkgType);
        bom.setPkgid(newPkgId);
    }

    @Data
    class HuggingFaceTypeSpecificMetadataTemplate implements TypeSpecificMetadataTemplate {
        private static final String NAME_SUFFIX = ".id";
        private static final String TAG_SEPARATOR = ",";
        private final String descriptionKey = type.getType() + DESCRIPTION_SUFFIX;
        private final String tagKey = type.getType() + ".tags";
        private Set<String> excludedUserProperties = Set.of(type.getType() + NAME_SUFFIX,
                type.getType() + VERSION_SUFFIX, descriptionKey, type.getType() + ".tags");

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
            return Set.of(MODEL_LEAD_FILE_NAME, DATASET_LEAD_FILE_NAME);
        }

        @Override
        public String getName(Properties properties, RepoPath repoPath) {
            return getNameKey().map(nameKey -> valueForKey(properties, nameKey)).orElse(null);
        }
    }
}
