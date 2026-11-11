package org.artifactory.metadata.service.provider;

import jakarta.annotation.Nullable;
import lombok.Data;
import org.artifactory.md.Properties;
import org.artifactory.repo.RepoPath;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

import static org.artifactory.descriptor.repo.RepoType.BazelModules;

/**
 * Metadata provider for Bazel Modules repositories.
 * Handles metadata extraction for Bazel modules with support for various archive formats.
 *
 * @author Volodymyr Shulga
 */
@Component
public class BazelModulesMetadataProvider extends AbstractMetadataProvider {
    private static final Set<String> BAZEL_ARCHIVE_EXTENSIONS = Set.of(
            "zip", "jar", "war", "aar", "nupkg", "whl", "tar", "tar.gz", "tgz", "tar.xz", "txz",
            "tar.zst", "tzst", "tar.bz2", "tbz", "ar", "deb"
    );

    BazelModulesMetadataProvider() {
        super(BazelModules);
        setTypeSpecificMetadataTemplate(new BazelModulesTypeSpecificMetadataTemplate());
    }

    @Data
    class BazelModulesTypeSpecificMetadataTemplate implements TypeSpecificMetadataTemplate {

        public static final String BAZEL_MODULE_NAME_PROPS = createPropKey("module.name");
        public static final String BAZEL_MODULE_VERSION_PROPS = createPropKey("module.version");
        public static final String BAZEL_MODULE_COMPATIBILITY_LEVEL_PROPS = createPropKey("module.compatibility_level");
        public static final String BAZEL_MODULE_BAZEL_COMPATIBILITY_PROPS = createPropKey("module.bazel_compatibility");
        public static final String BAZEL_MODULE_YANKED_VERSIONS_PROPS = createPropKey("module.yanked_versions");

        private static final Set<String> excludedUserProperties =
                Set.of(BAZEL_MODULE_NAME_PROPS, BAZEL_MODULE_VERSION_PROPS);

        @Nullable
        @Override
        public String getPkgidSuffix(RepoPath repoPath, Properties properties) {
            return getName(properties, repoPath);
        }

        @Nullable
        @Override
        public String getVersion(RepoPath repoPath, Properties properties) {
            return getBazelModulesVersion(properties);
        }

        @Override
        public Optional<String> getNameKey() {
            return Optional.of(BAZEL_MODULE_NAME_PROPS);
        }

        @Override
        public Optional<String> getVersionKey() {
            return Optional.of(BAZEL_MODULE_VERSION_PROPS);
        }

        @Override
        public Set<String> getExcludedUserProperties() {
            return excludedUserProperties;
        }

        @Override
        public Set<String> getLeadFileExtensions() {
            return BAZEL_ARCHIVE_EXTENSIONS;
        }

        @Override
        public String getName(Properties properties, RepoPath repoPath) {
            return getNameKey().map(nameKey -> valueForKey(properties, nameKey)).orElse(null);
        }

        private String getBazelModulesVersion(Properties properties) {
            return getVersionKey().map(versionKey -> valueForKey(properties, versionKey)).orElse(null);
        }

        private static String createPropKey(String key) {
            return String.join(".", BazelModules.getType(), key);
        }
    }
}