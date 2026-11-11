package org.artifactory.metadata.service.provider;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;
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

/**
 * @author Uriah Levy
 */
@Component
public class DebianMetadataProvider extends AbstractMetadataProvider {
    private static final RepoType type = RepoType.Debian;
    private static final Set<String> LEAD_FILE_EXTENSIONS = Set.of("deb", "ddeb", "dsc");
    private static final String DSC = "dsc";
    private static final String DSC_NAME_PROP = "dsc.name";

    DebianMetadataProvider() {
        super(type);
        setTypeSpecificMetadataTemplate(new DebianTypeSpecificMetadataTemplate());
    }

    @Override
    protected String getType(@Nonnull Properties properties) {
        String dscType = valueForKey(properties, DSC_NAME_PROP);
        if (!StringUtils.isBlank(dscType)) {
            return DSC;
        }
        return type.getPkgidPrefix();
    }

    @Override
    protected void resolvePkgId(MutableMetadataEntityBOM bom, Properties properties, FileInfo fileInfo) {
        String pkgType = getType(properties);
        if (!pkgType.equals(DSC)) {
            super.resolvePkgId(bom, properties, fileInfo);
            return;
        }

        String pkgidSuffix = typeSpecificMetadataTemplate.getPkgidSuffix(fileInfo.getRepoPath(), properties);
        if (StringUtils.isNotBlank(pkgidSuffix)) {
            bom.setPkgid(trimToSize(DSC + PACKAGE_ID_SEPARATOR + pkgidSuffix,
                    MAX_PKGID_VERSION_LENGTH));
        }
    }

    @Data
    class DebianTypeSpecificMetadataTemplate implements TypeSpecificMetadataTemplate {
        private static final String DEB = "deb";
        private static final String DSC = "dsc";
        private static final String DISTRIBUTION = ".distribution";
        private static final String ARCHITECTURE = ".architecture";
        private Set<String> excludedUserProperties =
                Set.of(DEB + NAME_SUFFIX, DEB + VERSION_SUFFIX, DSC + NAME_SUFFIX, DSC + VERSION_SUFFIX);

        @Override
        public Optional<String> getNameKey() {
            return Optional.of(DEB + NAME_SUFFIX);
        }

        @Override
        public Optional<String> getVersionKey() {
            return Optional.of(DEB + VERSION_SUFFIX);
        }

        @Override
        public Set<String> getExcludedUserProperties() {
            return excludedUserProperties;
        }

        @Override
        public Set<String> getLeadFileExtensions() {
            return LEAD_FILE_EXTENSIONS;
        }

        @Override
        public Optional<String> getArchitectureKey() {
            return Optional.of(DEB + ARCHITECTURE);
        }

        @Override
        public Optional<String> getDistributionKey() {
            return Optional.of(DEB + DISTRIBUTION);
        }

        @Override
        public String getName(Properties properties, RepoPath repoPath) {
            return getCommonNameOrPkgidSuffix(properties, repoPath);
        }

        @Nullable
        @Override
        public String getVersion(RepoPath repoPath, Properties properties) {
            if (repoPath.getPath().endsWith(DSC)) {
                String value = valueForKey(properties, DSC + VERSION_SUFFIX);
                return !value.isBlank() ? value : null;
            }

            return getVersionKey().map(versionKey -> valueForKey(properties, versionKey)).orElse(null);
        }

        @Nullable
        @Override
        public String getPkgidSuffix(RepoPath repoPath, Properties properties) {
            return getCommonNameOrPkgidSuffix(properties, repoPath);
        }

        private String getCommonNameOrPkgidSuffix(Properties properties, RepoPath repoPath) {
            if (repoPath.getPath().endsWith(DSC)) {
                String value = valueForKey(properties, DSC + NAME_SUFFIX);
                return !value.isBlank() ? value : null;
            }

            return getNameKey().map(nameKey -> valueForKey(properties, nameKey)).orElse(null);
        }
    }
}
