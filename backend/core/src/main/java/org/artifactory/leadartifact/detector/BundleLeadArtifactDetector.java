package org.artifactory.leadartifact.detector;

import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.repo.RepoPath;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Pattern;

import static org.artifactory.util.bundles.BundleConfigurationUtil.BUNDLE_MANIFEST_FILE_NAME;
import static org.artifactory.util.bundles.BundleConfigurationUtil.isReleaseBundlesV2BaseRepoKey;

@Component
public class BundleLeadArtifactDetector implements LeadArtifactDetector {

    private static final Set<String> LEAD_EXTENSIONS = Set.of("evd");

    private static final String FILE_NAME_REGEX = "[^/]+";
    /**
     * Matches "RB-Name/RB-Version/release-bundles-json.evd".
     */
    private static final Pattern BUNDLE_CREATION_EVIDENCE_PATH_PATTERN =
            Pattern.compile(FILE_NAME_REGEX + "/" + FILE_NAME_REGEX + "/" + BUNDLE_MANIFEST_FILE_NAME);

    /**
     * Matches "RB-Name/RB-Version/extensions/<anything which ends with .evd>".
     */
    private static final Pattern BUNDLE_EXTENSION_EVIDENCE_PATH_PATTERN =
            Pattern.compile(FILE_NAME_REGEX + "/" + FILE_NAME_REGEX + "/extensions/" +
                    "(" + FILE_NAME_REGEX + "/)*+" + FILE_NAME_REGEX + "\\.evd$");

    @Override
    public boolean isLeadArtifact(RepoPath repoPath) {
        if (!isReleaseBundlesV2BaseRepoKey(repoPath.getRepoKey())) {
            return false;
        }
        String path = repoPath.getPath();
        return isCreationEvidence(path) || isExtensionEvidence(path);
    }

    @Override
    public Set<String> getLeadArtifactExtensions() {
        return LEAD_EXTENSIONS;
    }

    @Override
    public RepoType getRepoType() {
        return RepoType.ReleaseBundles;
    }

    private static boolean isCreationEvidence(String path) {
        return BUNDLE_CREATION_EVIDENCE_PATH_PATTERN.matcher(path).matches();
    }

    private boolean isExtensionEvidence(String path) {
        return BUNDLE_EXTENSION_EVIDENCE_PATH_PATTERN.matcher(path).matches();
    }
}
