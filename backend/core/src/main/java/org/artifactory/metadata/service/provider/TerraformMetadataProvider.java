package org.artifactory.metadata.service.provider;

import lombok.Data;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.md.Properties;
import org.artifactory.repo.RepoPath;
import org.springframework.stereotype.Component;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Optional;
import java.util.Set;

/**
 * @author BarakH
 */
@Component
public class TerraformMetadataProvider extends AbstractMetadataProvider {
    private static final RepoType type = RepoType.Terraform;
    private static final String LEAD_FILE_EXTENSION = "zip";
    private static final String TERRAFORM = "terraform";
    private static final String TYPE_SUFFIX = ".type";
    private static final String TYPE_MODULE = "module";
    private static final String TYPE_PROVIDER = "provider";

    TerraformMetadataProvider() {
        super(type);
        setTypeSpecificMetadataTemplate(new TerraformTypeSpecificMetadataTemplate());
    }

    @Override
    protected String getType(@Nonnull Properties properties) {
        String terraformType = valueForKey(properties, TERRAFORM + TYPE_SUFFIX);
        if (TYPE_MODULE.equalsIgnoreCase(terraformType)) {
            return TERRAFORM + TYPE_MODULE;
        } else if (TYPE_PROVIDER.equalsIgnoreCase(terraformType)) {
            return TERRAFORM + TYPE_PROVIDER;
        }
        return type.getPkgidPrefix();
    }

    @Data
    class TerraformTypeSpecificMetadataTemplate implements TypeSpecificMetadataTemplate {
        private static final String PATH_SEPARATOR = "/";
        private static final String PROVIDER_SUFFIX = ".provider";
        private static final String FLAVOR_SUFFIX = ".flavor";
        private static final String NAMESPACE_SUFFIX = ".namespace";
        private Set<String> excludedUserProperties = Set.of(TERRAFORM + NAME_SUFFIX, TERRAFORM + VERSION_SUFFIX);

        @Override
        public Optional<String> getNameKey() {
            return Optional.of(TERRAFORM + NAME_SUFFIX);
        }

        @Override
        public Optional<String> getArchitectureKey() {
            return Optional.of(TERRAFORM + FLAVOR_SUFFIX);
        }

        @Override
        public Optional<String> getVersionKey() {
            return Optional.of(TERRAFORM + VERSION_SUFFIX);
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
            return getNameKey().map(nameKey -> {
                String name = null;
                String artifactType = valueForKey(properties, TERRAFORM + TYPE_SUFFIX);
                String namespace = valueForKey(properties, TERRAFORM + NAMESPACE_SUFFIX);
                if (TYPE_MODULE.equalsIgnoreCase(artifactType)) {
                    String module = valueForKey(properties, nameKey);
                    String provider = valueForKey(properties, TERRAFORM + PROVIDER_SUFFIX);
                    name = String.join(PATH_SEPARATOR, namespace, module, provider);
                } else if (TYPE_PROVIDER.equalsIgnoreCase(artifactType)) {
                    name = String.join(PATH_SEPARATOR, namespace, valueForKey(properties, nameKey));
                }
                return name;
            }).orElse(null);
        }

        @Nullable
        @Override
        public String getVersion(RepoPath repoPath, Properties properties) {
            return getVersionKey().map(versionKey -> valueForKey(properties, versionKey)).orElse(null);
        }

        @Nullable
        @Override
        public String getPkgidSuffix(RepoPath repoPath, Properties properties) {
            return getName(properties, repoPath);
        }
    }
}
