package org.artifactory.metadata.service.provider;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.fs.FileInfo;
import org.artifactory.md.Properties;
import org.artifactory.repo.RepoPath;
import org.artifactory.storage.db.event.service.metadata.model.MutableMetadataEntityBOM;
import org.springframework.stereotype.Component;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Optional;
import java.util.Set;

import static org.artifactory.descriptor.repo.RepoType.MachineLearning;

/**
 * @author avivbr
 */
@Component
@Slf4j
public class MachineLearningMetadataProvider extends AbstractMetadataProvider {

    private static final RepoType type = RepoType.MachineLearning;
    static final String MODEL_LEAD_FILE_NAME = "model-manifest.json";
    static final String DATASET_LEAD_FILE_NAME = "dataset-manifest.json";
    static final String PROP_ENTITY_TYPE = ".entity.type";
    static final String MACHINELEARNING_DATASET_TYPE = "machinelearningdataset";
    static final String ARTIFACTS = "artifacts";
    static final String MODEL = "model";
    static final String DATASET = "dataset";

    MachineLearningMetadataProvider() {
        super(RepoType.MachineLearning);
        setTypeSpecificMetadataTemplate(new MachineLearningTypeSpecificMetadataTemplate());
    }

    @Override
    public boolean isLeadArtifact(RepoPath repoPath) {
        return repoPath.getPath().endsWith(MODEL_LEAD_FILE_NAME) || repoPath.getPath().endsWith(DATASET_LEAD_FILE_NAME);
    }

    @Override
    public boolean isPackageConsumptionLead(RepoPath repoPath) {
        return  isLeadArtifact(repoPath) || isAuxiliaryFile(repoPath);
    }

    private boolean isAuxiliaryFile(RepoPath repoPath) {
        String path = repoPath.getPath();
        int artifactsIndex = path.indexOf("/"+ ARTIFACTS +"/");
        if (artifactsIndex != -1) {
            int modelIndex = path.lastIndexOf("/" + MODEL+ "/", artifactsIndex);
            return modelIndex == -1;
        }
        return false;
    }

    @Override
    protected String getType(@Nonnull Properties properties) {
        String entityType = valueForKey(properties, getRepoType().getType() + PROP_ENTITY_TYPE);
        if (entityType != null && entityType.equalsIgnoreCase(DATASET)) {
            return MACHINELEARNING_DATASET_TYPE;
        }
        return type.getType();
    }

    @Override
    protected void resolvePkgId(MutableMetadataEntityBOM bom, Properties properties, FileInfo fileInfo) {
        super.resolvePkgId(bom, properties, fileInfo);
        String pkgType = getType(properties);
        if (!pkgType.equals(MACHINELEARNING_DATASET_TYPE)) {
            return;
        }
        String currentPkgId = bom.getPkgid();
        String newPkgId = currentPkgId.replaceFirst(MachineLearning.getType(), MACHINELEARNING_DATASET_TYPE);
        log.debug("MachineLearning PkgId: {} for pkgName: {} pkgVersion: {} and pkgType: {}", newPkgId, bom.getName(),
                bom.getVersion(), pkgType);
        bom.setPkgid(newPkgId);
    }

    @Data
    class MachineLearningTypeSpecificMetadataTemplate implements TypeSpecificMetadataTemplate {
        private static final String CREATE_DATE_SUFFIX = ".create.date";
        private Set<String> excludedUserProperties =
                Set.of(getRepoType().getType() + NAME_SUFFIX, getRepoType().getType() + VERSION_SUFFIX,
                        getRepoType().getType() + CREATE_DATE_SUFFIX, getRepoType().getType() + PROP_ENTITY_TYPE);

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
            return Optional.of(getRepoType().getType() + NAME_SUFFIX);
        }

        @Override
        public Optional<String> getVersionKey() {
            return Optional.of(getRepoType().getType() + VERSION_SUFFIX);
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
