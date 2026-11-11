package org.artifactory.metadata.service.provider;

import jakarta.annotation.Nullable;
import lombok.Data;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.md.Properties;
import org.artifactory.repo.RepoPath;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

/**
 * @author Tomer Nir
 */
@Component
public class AIEditorExtensionsMetadataProvider extends AbstractMetadataProvider {
    private static final RepoType type = RepoType.AIEditorExtensions;
    private static final String VSIX_PACKAGE_MARKETPLACE = "Microsoft.VisualStudio.Services.VSIXPackage";
    private static final String VSIX_PACKAGE_EXTENSION = ".vsix";


    AIEditorExtensionsMetadataProvider() {
        super(type);
        setTypeSpecificMetadataTemplate(new VSCodeTypeSpecificMetadataTemplate());
    }

    @Override
    public boolean isLeadArtifact(RepoPath repoPath) {
        String fileName = repoPath.getName();
        return fileName.startsWith(VSIX_PACKAGE_MARKETPLACE) || fileName.endsWith(VSIX_PACKAGE_EXTENSION);
    }


    @Data
    class VSCodeTypeSpecificMetadataTemplate implements TypeSpecificMetadataTemplate {
        private Set<String> excludedUserProperties =
                Set.of(type.getType() + EXTENSION + NAME_SUFFIX, type.getType() + EXTENSION + VERSION_SUFFIX);
        private static final String EXTENSION = ".extension";

        @Override
        public Optional<String> getNameKey() {
            return Optional.of(type.getType() + EXTENSION + NAME_SUFFIX);
        }

        @Override
        public Optional<String> getVersionKey() {
            return Optional.of(type.getType() + EXTENSION + VERSION_SUFFIX);
        }

        @Nullable
        @Override
        public String getVersion(RepoPath repoPath, Properties properties) {
            if (!isLeadArtifact(repoPath)) {
                return null;
            }
            return getVersionKey().map(versionKey -> valueForKey(properties, versionKey)).orElse(null);
        }

        @Override
        public Set<String> getExcludedUserProperties() {
            return excludedUserProperties;
        }

        @Override
        public Set<String> getLeadFileExtensions() {
            return Set.of(VSIX_PACKAGE_MARKETPLACE + "*");
        }

        @Override
        public String getName(Properties properties, RepoPath repoPath) {
            if (!isLeadArtifact(repoPath)) {
                return null;
            }
            return getNameKey().map(nameKey -> valueForKey(properties, nameKey)).orElse(null);
        }
    }
}
