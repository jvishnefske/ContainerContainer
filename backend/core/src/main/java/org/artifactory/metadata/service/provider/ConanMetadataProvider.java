package org.artifactory.metadata.service.provider;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.aql.api.domain.sensitive.AqlApiItem;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.md.Properties;
import org.artifactory.repo.RepoPath;
import org.springframework.stereotype.Component;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Optional;
import java.util.Set;

/**
 * @author Yuval Reches
 */
@Component
public class ConanMetadataProvider extends AbstractMetadataProvider {
    private static final RepoType type = RepoType.Conan;
    private static final String LEAD_FILE_EXTENSION = "py";

    ConanMetadataProvider() {
        super(type);
        setTypeSpecificMetadataTemplate(new ConanTypeSpecificMetadataTemplate());
    }

    @Override
    public boolean isLeadArtifact(RepoPath repoPath) {
        return repoPath.getName().equals("conanfile.py");
    }

    @Override
    public boolean isPackageConsumptionLead(RepoPath repoPath) {
        return repoPath.getName().equals("conan_package.tgz");
    }

    @Override
    public Optional<AqlApiItem> buildVersionFilesQuery(@Nonnull RepoPath leadArtifactRepoPath) {
        return getLeadArtifactSiblingsQuery(leadArtifactRepoPath);
    }

    @Data
    class ConanTypeSpecificMetadataTemplate implements TypeSpecificMetadataTemplate {
        private static final String CONAN = "conan.package";
        private static final String VENDOR_SUFFIX = ".vendor";
        private Set<String> excludedUserProperties = Set.of(CONAN + NAME_SUFFIX, CONAN + VERSION_SUFFIX);

        @Override
        public Optional<String> getNameKey() {
            return Optional.of(CONAN + NAME_SUFFIX);
        }

        @Override
        public Optional<String> getVersionKey() {
            return Optional.of(CONAN + VERSION_SUFFIX);
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
            return getNameKey().map(nameKey -> getConanPackageName(properties, nameKey)).orElse(null);
        }

        @Nullable
        @Override
        public String getVersion(RepoPath repoPath, Properties properties) {
            return getVersionKey().map(versionKey -> valueForKey(properties, versionKey)).orElse(null);
        }

        @Nullable
        @Override
        public String getPkgidSuffix(RepoPath repoPath, Properties properties) {
            return getNameKey().map(nameKey -> getConanPackageName(properties, nameKey)).orElse(null);
        }

        private String getConanPackageName(Properties properties, String nameKey) {
            String packageName = valueForKey(properties, nameKey);
            String vendorName = valueForKey(properties, CONAN + VENDOR_SUFFIX);
            if (StringUtils.isNotBlank(vendorName)) {
                packageName = vendorName + COLON + packageName;
            }
            return packageName;
        }
    }
}
