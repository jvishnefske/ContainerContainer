package org.artifactory.repo.system;

import lombok.Getter;
import lombok.experimental.UtilityClass;
import org.artifactory.repo.RepoPath;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.artifactory.common.home.ArtifactoryHome.getBooleanFromSysConfig;
import static org.artifactory.util.evidence.EvidenceSystemYamlConstants.EVIDENCE_NAVIGATION_ENABLED;

/**
 * This class and enum are used to define system folders in Artifactory.
 * System folders are special directories, their name prefixed by ".", that are used internally by Artifactory for
 * various purposes, such as: Storing metadata files, evidence, and other system-related data.
 *
 * <p>The {@link SystemFolder} enum carries only the folder name. Every behavioral attribute
 * ({@code downloadAllowed}, {@code defaultShowInUI}, {@code backedUp}, {@code federated},
 * {@code billed}) lives in the internal {@link SystemFolderAttributes} record and is keyed by
 * folder in {@link #ATTRIBUTES_BY_FOLDER}. The attributes are intentionally not exposed on the
 * enum, so the only way to read them is through the static helpers on this class.
 * {@link #isShowInUI(SystemFolder)} in particular consults a runtime override
 * ({@code artifactory.evidence.navigation.enabled}) that bypasses {@code defaultShowInUI}.
 *
 * @author Mor Merhav
 */
@UtilityClass
public class SystemFolders {

    public final Set<SystemFolder> ALL_SYSTEM_FOLDERS = SystemFolder.toSet();

    private final Map<SystemFolder, SystemFolderAttributes> ATTRIBUTES_BY_FOLDER = buildAttributes();

    private Map<SystemFolder, SystemFolderAttributes> buildAttributes() {
        Map<SystemFolder, SystemFolderAttributes> attributes = new EnumMap<>(SystemFolder.class);
        attributes.put(SystemFolder.EVIDENCE, new SystemFolderAttributes(
                true,   // downloadAllowed
                false,  // defaultShowInUI
                true,   // backedUp
                true,   // federated
                true    // billed
        ));
        attributes.put(SystemFolder.JFROG, new SystemFolderAttributes(
                false,  // downloadAllowed — TBD
                false,  // defaultShowInUI — TBD
                true,   // backedUp — TBD
                false,  // federated — TBD
                false   // billed — TBD
        ));
        return Map.copyOf(attributes);
    }

    /**
     * Checks if the given repository path is a system folder.
     *
     * @param repoPath the repository path to check
     * @return true if the path is a system folder, false otherwise
     */
    public boolean isSystemFolder(RepoPath repoPath) {
        return ALL_SYSTEM_FOLDERS
                .stream()
                .anyMatch(
                        systemFolder -> repoPath.isFolder() && repoPath.getName().equals(systemFolder.getFolderName()));
    }

    /**
     * Checks if the given repository path is under a system folder.
     *
     * @param repoPath the repository path to check
     * @return true if the path is under a system folder (including if it points to the system folder itself), false
     * otherwise
     */
    public boolean isUnderSystemFolder(RepoPath repoPath) {
        if (repoPath == null || repoPath.isRoot()) {
            return false;
        }
        if (isSystemFolder(repoPath)) {
            return true;
        }
        return isUnderSystemFolder(repoPath.getParent());
    }

    /**
     * Returns the {@link SystemFolder} whose root folder contains {@code repoPath}, or
     * {@link Optional#empty()} if the path is not under any system folder.
     */
    public Optional<SystemFolder> forPath(RepoPath repoPath) {
        if (repoPath == null || repoPath.isRoot()) {
            return Optional.empty();
        }
        return forPath(repoPath.getPath());
    }

    /**
     * Path-string overload of {@link #forPath(RepoPath)}. An optional leading {@code /} is
     * stripped before matching; the leading repo key must NOT be part of {@code path}.
     */
    public Optional<SystemFolder> forPath(String path) {
        if (path == null || path.isBlank()) {
            return Optional.empty();
        }
        String normalized = path.startsWith("/") ? path.substring(1) : path;
        return ALL_SYSTEM_FOLDERS.stream()
                .filter(sf -> normalized.equals(sf.getFolderName())
                        || normalized.startsWith(sf.getFolderName() + "/"))
                .findFirst();
    }

    /**
     * UI visibility for {@code folder}. For {@link SystemFolder#EVIDENCE} this consults the
     * runtime override {@code artifactory.evidence.navigation.enabled} (default {@code false});
     * for everything else it returns the folder's {@code defaultShowInUI} attribute. Use this —
     * not the attribute directly — to respect the runtime toggle.
     */
    public boolean isShowInUI(SystemFolder folder) {
        if (folder == SystemFolder.EVIDENCE) {
            return getBooleanFromSysConfig(EVIDENCE_NAVIGATION_ENABLED, false);
        }
        return attributesOf(folder).defaultShowInUI();
    }

    /**
     * Returns {@code true} when {@code repoPath} is under a system folder that should be
     * included in trash restore and cold-storage archival ({@code backedUp = true}).
     */
    public boolean isBackedUp(RepoPath repoPath) {
        return forPath(repoPath).map(SystemFolders::isBackedUp).orElse(false);
    }

    /**
     * Returns {@code true} when {@code folder}'s files should be included in trash restore and
     * cold-storage archival ({@code backedUp = true}).
     */
    public boolean isBackedUp(SystemFolder folder) {
        return attributesOf(folder).backedUp();
    }

    /**
     * Returns {@code true} when {@code repoPath} is under a system folder whose files are
     * replicated to federation peers ({@code federated = true}).
     */
    public boolean isFederated(RepoPath repoPath) {
        return forPath(repoPath).map(SystemFolders::isFederated).orElse(false);
    }

    /**
     * Returns {@code true} when {@code folder}'s files are replicated to federation peers
     * ({@code federated = true}).
     */
    public boolean isFederated(SystemFolder folder) {
        return attributesOf(folder).federated();
    }

    /**
     * Returns {@code true} when {@code repoPath} is under a system folder whose
     * upload/download traffic counts toward billing ({@code billed = true}).
     */
    public boolean isBilled(RepoPath repoPath) {
        return forPath(repoPath).map(SystemFolders::isBilled).orElse(false);
    }

    /**
     * Returns {@code true} when {@code folder}'s upload/download traffic counts toward billing
     * ({@code billed = true}).
     */
    public boolean isBilled(SystemFolder folder) {
        return attributesOf(folder).billed();
    }

    /**
     * Returns {@code true} when {@code repoPath} is under a system folder that allows
     * direct download by users with repo-read permission ({@code downloadAllowed = true}).
     */
    public boolean isDownloadAllowed(RepoPath repoPath) {
        return forPath(repoPath).map(SystemFolders::isDownloadAllowed).orElse(false);
    }

    /**
     * Returns {@code true} when {@code folder} allows direct download by users with
     * repo-read permission ({@code downloadAllowed = true}).
     */
    public boolean isDownloadAllowed(SystemFolder folder) {
        return attributesOf(folder).downloadAllowed();
    }

    private SystemFolderAttributes attributesOf(SystemFolder folder) {
        SystemFolderAttributes attributes = ATTRIBUTES_BY_FOLDER.get(folder);
        if (attributes == null) {
            throw new IllegalStateException("No attributes registered for system folder " + folder);
        }
        return attributes;
    }

    /**
     * Behavioral attributes of a {@link SystemFolder}. Kept internal so attributes can only be
     * read through the static helpers on {@link SystemFolders}.
     */
    private record SystemFolderAttributes(
            boolean downloadAllowed,
            boolean defaultShowInUI,
            boolean backedUp,
            boolean federated,
            boolean billed) {
    }

    @Getter
    public enum SystemFolder {
        EVIDENCE(".evidence"),
        JFROG(".jfrog");

        private final String folderName;

        SystemFolder(String folderName) {
            this.folderName = folderName;
        }

        public static Set<SystemFolder> toSet() {
            return Set.of(SystemFolder.values());
        }
    }
}
