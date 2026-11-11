package org.artifactory.metadata.service.provider;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.md.Properties;
import org.artifactory.repo.RepoPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Uriah Levy
 */
@Component
public class RpmMetadataProvider extends AbstractMetadataProvider {
    private static final Logger log = LoggerFactory.getLogger(RpmMetadataProvider.class);
    private static final RepoType type = RepoType.YUM;
    private static final String RPM = "rpm";
    private static final String LEAD_FILE_EXTENSION = RPM;
    private static final Pattern EL_VERSION = Pattern.compile("(?i)(\\.el|\\.rhel)(?<enterpriseLinuxVersion>\\d+)");

    public RpmMetadataProvider() {
        super(type);
        setTypeSpecificMetadataTemplate(new RpmTypeSpecificMetadataTemplate());
    }

    @Override
    protected String getType(@Nonnull Properties properties) {
        return RPM;
    }

    @Data
    class RpmTypeSpecificMetadataTemplate implements TypeSpecificMetadataTemplate {
        private static final String METADATA_PREFIX = "rpm.metadata";
        private static final String DESCRIPTION_SUFFIX = ".summary";
        private static final String ARCH_SUFFIX = ".arch";
        private static final String EPOCH_SUFFIX = ".epoch";
        private static final String RELEASE_SUFFIX = ".release";
        private final Map<String, String> qualifierKeys = Map.of(METADATA_PREFIX + ARCH_SUFFIX,
                "rpm_architecture", METADATA_PREFIX + ".group", "rpm_group");
        private Set<String> excludedUserProperties = Set.of(METADATA_PREFIX + NAME_SUFFIX,
                METADATA_PREFIX + VERSION_SUFFIX, METADATA_PREFIX + DESCRIPTION_SUFFIX);

        @Nullable
        @Override
        public String getPkgidSuffix(RepoPath repoPath, Properties properties) {
            return getNameKey().map(nameKey -> {
                String packageName = valueForKey(properties, nameKey);
                if (StringUtils.isNotBlank(packageName)) {
                    String fullSuffix = "";
                    // Enterprise Linux version
                    Optional<String> enterpriseLinuxVersion = tryToGetEnterpriseLinuxVersion(
                            valueForKey(properties, METADATA_PREFIX + RELEASE_SUFFIX));
                    if (enterpriseLinuxVersion.isPresent()) {
                        fullSuffix = fullSuffix + enterpriseLinuxVersion.get() + COLON;
                    }
                    // Name
                    fullSuffix = fullSuffix + packageName;
                    return fullSuffix;
                }
                log.debug("Unable to resolve RPM package ID for '{}', package packageName is missing.", repoPath);
                return null;
            }).orElse(null);
        }

        public String getName(Properties properties, RepoPath repoPath) {
            return getNameKey().map(nameKey -> {
                String rpmName = valueForKey(properties, nameKey);
                Optional<String> linuxVersion = tryToGetEnterpriseLinuxVersion(
                        valueForKey(properties, METADATA_PREFIX + RELEASE_SUFFIX));
                return linuxVersion.map(version -> version + COLON + rpmName).orElse(rpmName);
            }).orElse(null);
        }

        @Nullable
        @Override
        public String getVersion(RepoPath repoPath, Properties properties) {
            String version = valueForKey(properties, METADATA_PREFIX + VERSION_SUFFIX);
            if (StringUtils.isNotBlank(version)) {
                String fullSuffix = "";
                // RPM Epoch
                String epoch = valueForKey(properties, METADATA_PREFIX + EPOCH_SUFFIX);
                if (StringUtils.isNotBlank(epoch)) {
                    fullSuffix = fullSuffix + epoch + COLON;
                }
                // Version
                fullSuffix = fullSuffix + version;
                // Release
                String release = valueForKey(properties, METADATA_PREFIX + RELEASE_SUFFIX);
                if (StringUtils.isNotBlank(release)) {
                    fullSuffix = fullSuffix + "-" + release;
                }
                return fullSuffix;
            }
            log.debug("Unable to resolve RPM Version Name for '{}', version property is missing.", repoPath);
            return null;
        }

        @Override
        public Optional<String> getNameKey() {
            return Optional.of(METADATA_PREFIX + NAME_SUFFIX);
        }

        @Override
        public Optional<String> getVersionKey() {
            return Optional.of(METADATA_PREFIX + VERSION_SUFFIX);
        }

        @Override
        public Optional<String> getDescriptionKey() {
            return Optional.of(METADATA_PREFIX + DESCRIPTION_SUFFIX);
        }

        @Override
        public Map<String, String> getQualifierKeysToMdsKeys() {
            return qualifierKeys;
        }

        @Override
        public Set<String> getLeadFileExtensions() {
            return Set.of(LEAD_FILE_EXTENSION);
        }

        private Optional<String> tryToGetEnterpriseLinuxVersion(String release) {
            if (StringUtils.isNotBlank(release)) {
                Matcher elVersionMatcher = EL_VERSION.matcher(release);
                if (StringUtils.isNotBlank(release) && elVersionMatcher.find()) {
                    return Optional.of(elVersionMatcher.group("enterpriseLinuxVersion"));
                }
            }
            return Optional.empty();
        }
    }
}
