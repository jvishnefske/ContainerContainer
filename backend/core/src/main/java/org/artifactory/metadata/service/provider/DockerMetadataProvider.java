package org.artifactory.metadata.service.provider;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.artifactory.aql.api.domain.sensitive.AqlApiItem;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.md.Properties;
import org.artifactory.repo.RepoPath;
import org.springframework.stereotype.Component;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.artifactory.mime.DockerNaming.MANIFEST_FILENAME;
import static org.artifactory.mime.DockerNaming.MANIFEST_LIST_FILENAME;

/**
 * @author Uriah Levy
 */
@Slf4j
@Component
public class DockerMetadataProvider extends AbstractMetadataProvider {

    public static final String MANIFEST_TYPE_PROP = "docker.manifest.type";
    public static final String MANIFEST_ARTIFACT_TYPE_PROP = "oci.artifact.type";
    private static final RepoType type = RepoType.Docker;
    private static final String LEAD_FILE_EXTENSION = "json";

    public DockerMetadataProvider() {
        this(type);
    }

    protected DockerMetadataProvider(RepoType type) {
        super(type);
        setTypeSpecificMetadataTemplate(new DockerTypeSpecificMetadataTemplate());
    }

    @Override
    public boolean isLeadArtifact(RepoPath repoPath) {
        return repoPath.getName().equals(MANIFEST_FILENAME) || repoPath.getName().equals(MANIFEST_LIST_FILENAME);
    }

    @Override
    public boolean isPackageConsumptionLead(RepoPath repoPath) {
        return repoPath.getName().equals(MANIFEST_FILENAME);
    }

    @Override
    public Optional<AqlApiItem> buildVersionFilesQuery(@Nonnull RepoPath leadArtifactRepoPath) {
        return getLeadArtifactSiblingsQuery(leadArtifactRepoPath).map(aqlApiItem -> aqlApiItem.distinct(false));
    }

    @Override
    protected String getType(@Nonnull Properties properties) {
        String manifestType = valueForKey(properties, MANIFEST_TYPE_PROP);
        if (manifestType != null && manifestType.startsWith("application/vnd.oci.image.")) {
            return "oci";
        }
        return type.getType();
    }

    @Data
    class DockerTypeSpecificMetadataTemplate implements TypeSpecificMetadataTemplate {
        static final String VERSION_SUFFIX = ".manifest";
        private static final String NAME_SUFFIX = ".repoName";
        private static final String SHA256_PREFIX = "sha256";
        private Map<String, String> qualifierKeys =
                Map.of(MANIFEST_TYPE_PROP, "manifest_version", "docker.manifest.digest", "manifest_digest");
        private Set<String> excludedUserProperties =
                Set.of(type.getType() + NAME_SUFFIX, type.getType() + VERSION_SUFFIX);

        DockerTypeSpecificMetadataTemplate() {
        }

        @Nullable
        @Override
        public String getPkgidSuffix(RepoPath repoPath, Properties properties) {
            return getName(properties, repoPath);
        }

        @Nullable
        @Override
        public String getVersion(RepoPath repoPath, Properties properties) {
            return getVersionKey().map(versionKey -> {
                String rawVersionValue = valueForKey(properties, versionKey);
                if (Strings.isBlank(rawVersionValue)) {
                    rawVersionValue = Optional.ofNullable(
                            repoPath.getParent()).map(RepoPath::getName).orElse("");
                }
                if (rawVersionValue.startsWith(SHA256_PREFIX)) {
                    // Manifests referenced by a fat manifest will have a certain sha256 value for a version.
                    // Replace the colon with double underscore to be aligned with Xray
                    return rawVersionValue.replace(COLON, "__");
                }
                return rawVersionValue;
            }).orElse(null);
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
        public Map<String, String> getQualifierKeysToMdsKeys() {
            return qualifierKeys;
        }

        @Override
        public Set<String> getLeadFileExtensions() {
            return Set.of(LEAD_FILE_EXTENSION);
        }

        @Override
        public String getName(Properties properties, RepoPath repoPath) {
            String name = getNameKey().map(nameKey -> valueForKey(properties, nameKey)).orElse(null);
            return Strings.isBlank(name) ?
                    // If the name property is not found, use the repo name as the artifact name
                    getNameKeyFromPath(repoPath) : name;
        }

        private String getNameKeyFromPath(RepoPath repoPath) {
            log.debug("Fallback to repo path parsing for artifact name form repo path: {}", repoPath);
            return Optional.ofNullable(repoPath)
                    .map(RepoPath::getParent)
                    .map(RepoPath::getParent)
                    .map(RepoPath::getPath)
                    .orElse("");
        }
    }
}
