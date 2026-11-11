package org.artifactory.metadata.service.provider;

import jakarta.annotation.Nullable;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.md.Properties;
import org.artifactory.repo.RepoPath;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Base for package-type metadata providers whose name, version, and description are stored as
 * {@code <prefix>.<suffix>} artifact properties and whose lead artifact is identified by a single
 * file extension.
 * <p>
 * Subclasses only need to supply the property prefix, the name key (which may deviate from the
 * standard {@code <prefix>.name} convention), the lead-file extension, and any extra property
 * keys that must be excluded from the free-form user-properties map.
 *
 * @author Igor Gumush
 */
public abstract class AbstractSimplePrefixMetadataProvider extends AbstractMetadataProvider {

    protected AbstractSimplePrefixMetadataProvider(RepoType type, String prefix, String nameKey,
            String leadFileExtension) {
        this(type, prefix, nameKey, leadFileExtension, Set.of());
    }

    protected AbstractSimplePrefixMetadataProvider(RepoType type, String prefix, String nameKey,
            String leadFileExtension, Set<String> additionalExcludedKeys) {
        super(type);
        setTypeSpecificMetadataTemplate(
                new SimplePrefixTemplate(prefix, nameKey, leadFileExtension, additionalExcludedKeys));
    }

    private class SimplePrefixTemplate implements TypeSpecificMetadataTemplate {

        private final String prefix;
        private final String nameKey;
        private final String leadFileExtension;
        private final Set<String> excludedUserProperties;

        SimplePrefixTemplate(String prefix, String nameKey, String leadFileExtension,
                Set<String> additionalExcludedKeys) {
            this.prefix = prefix;
            this.nameKey = nameKey;
            this.leadFileExtension = leadFileExtension;
            Set<String> excluded = new HashSet<>();
            excluded.add(nameKey);
            excluded.add(prefix + VERSION_SUFFIX);
            excluded.add(prefix + DESCRIPTION_SUFFIX);
            excluded.addAll(additionalExcludedKeys);
            this.excludedUserProperties = Set.copyOf(excluded);
        }

        @Override
        public Optional<String> getNameKey() {
            return Optional.of(nameKey);
        }

        @Override
        public Optional<String> getVersionKey() {
            return Optional.of(prefix + VERSION_SUFFIX);
        }

        @Override
        public Optional<String> getDescriptionKey() {
            return Optional.of(prefix + DESCRIPTION_SUFFIX);
        }

        @Override
        public Set<String> getExcludedUserProperties() {
            return excludedUserProperties;
        }

        @Override
        public Set<String> getLeadFileExtensions() {
            return Set.of(leadFileExtension);
        }

        @Override
        public String getName(Properties properties, RepoPath repoPath) {
            return valueFromKey(getNameKey(), properties);
        }

        @Nullable
        @Override
        public String getVersion(RepoPath repoPath, Properties properties) {
            return valueFromKey(getVersionKey(), properties);
        }

        private String valueFromKey(Optional<String> key, Properties properties) {
            return key.map(k -> valueForKey(properties, k)).orElse(null);
        }
    }
}
