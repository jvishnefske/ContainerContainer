package org.artifactory.api.config;

import com.google.common.annotations.VisibleForTesting;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * @author Hezi Cohen
 */
@Slf4j
@UtilityClass
public class PathValidatorUtil {

    /**
     * SysConfig key for the import allowlist (set in {@code system.yaml} under
     * {@code artifactory.security.import.allowedPaths} as a comma-separated scalar).
     */
    public static final String YAML_KEY_IMPORT_ALLOWED_PATHS = "artifactory.security.import.allowedPaths";

    /**
     * SysConfig key for the export allowlist (set in {@code system.yaml} under
     * {@code artifactory.security.export.allowedPaths} as a comma-separated scalar).
     */
    public static final String YAML_KEY_EXPORT_ALLOWED_PATHS = "artifactory.security.export.allowedPaths";

    /**
     * SysConfig key for the backup-destination allowlist (set in {@code system.yaml} under
     * {@code artifactory.security.backup.allowedPaths} as a comma-separated scalar). Used by
     * {@code BackupServiceImpl} to gate manual and scheduled backups in addition to the
     * existing Artifactory-home rejection.
     */
    public static final String YAML_KEY_BACKUP_ALLOWED_PATHS = "artifactory.security.backup.allowedPaths";

    public static boolean isValidPath(String path, boolean allowImportZipFileFromHomePath) {
        return !path.startsWith("$")
                && !path.startsWith("\\\\")
                && !path.startsWith("//")
                && !path.contains("/..")
                && !path.contains("\\..")
                && (allowImportZipFileFromHomePath || !isArtifactoryHomePath(new File(path)));
    }

    /**
     * insecure paths defined as described in RTFACT-20118 and RTDEV-31305
     */
    public static boolean isExportImportValidPath(String path) {
        return isExportImportValidPath(path, false);
    }

    public static boolean isExportImportValidPath(String path, boolean allowImportZipFileFromHomePath) {
        return ConstantValues.allowExportImportInsecurePaths.getBoolean()
                || isValidPath(path, allowImportZipFileFromHomePath);
    }

    /**
     * Stricter variant of {@link #isExportImportValidPath(String)} for IMPORT flows
     * (RTDEV-83087): the path must additionally not be an ancestor of the Artifactory
     * home tree, since importing from such a path would still descend into
     * home/var/data/etc and expose sensitive files
     */
    public static boolean isImportValidPath(String path) {
        return isImportValidPath(path, false);
    }

    public static boolean isImportValidPath(String path, boolean allowImportZipFileFromHomePath) {
        return isExportImportValidPath(path, allowImportZipFileFromHomePath)
                && (allowImportZipFileFromHomePath
                        || ConstantValues.allowExportImportInsecurePaths.getBoolean()
                        || !isOrContainsArtifactoryHomePath(new File(path)));
    }

    /**
     * Returns true when {@code path} is the Artifactory home directory or any directory
     * inside the home tree (home, var, data or etc). Used by write flows (backup, export)
     * to prevent overwriting/polluting the Artifactory installation.
     */
    public static boolean isArtifactoryHomePath(File path) {
        return matchesArtifactoryHome(path, false);
    }

    /**
     * Stricter variant of {@link #isArtifactoryHomePath(File)} used by import flows.
     * Returns true when the path is the home tree, is inside it, OR is an ancestor of it
     */
    @VisibleForTesting
    static boolean isOrContainsArtifactoryHomePath(File path) {
        return matchesArtifactoryHome(path, true);
    }

    private static boolean matchesArtifactoryHome(File path, boolean alsoRejectAncestors) {
        if (path == null) {
            return false;
        }
        try {
            ArtifactoryHome home = ArtifactoryHome.get();
            String pathAbsolute = path.getAbsolutePath();
            String pathCanonical = path.getCanonicalPath();
            return matchesProtectedDir(pathAbsolute, pathCanonical, home.getHomeDir(), alsoRejectAncestors)
                    || matchesProtectedDir(pathAbsolute, pathCanonical, home.getVarDir(), alsoRejectAncestors)
                    || matchesProtectedDir(pathAbsolute, pathCanonical, home.getDataDir(), alsoRejectAncestors)
                    || matchesProtectedDir(pathAbsolute, pathCanonical, home.getEtcDir(), alsoRejectAncestors);
        } catch (IOException e) {
            log.warn("Failed to resolve canonical path for '{}', rejecting as potentially unsafe: {}",
                    path, e.getMessage());
            log.debug("Canonical path resolution error details:", e);
            return true;
        }
    }

    private static boolean matchesProtectedDir(String pathAbsolute, String pathCanonical,
            File protectedDir, boolean alsoRejectAncestors) throws IOException {
        String absolute = protectedDir.getAbsolutePath();
        String canonical = protectedDir.getCanonicalPath();
        if (isSubPath(pathAbsolute, absolute) || isSubPath(pathCanonical, canonical)) {
            return true;
        }
        return alsoRejectAncestors
                && (isSubPath(absolute, pathAbsolute) || isSubPath(canonical, pathCanonical));
    }

    private static boolean isSubPath(String path, String parent) {
        if (path.equals(parent)) {
            return true;
        }
        String boundary = parent.endsWith(File.separator) ? parent : parent + File.separator;
        return path.startsWith(boundary);
    }

    public static boolean isPathForArchiveIsValid(String path) {
        return path != null
                && !path.contains("../")
                && !path.startsWith("$")
                && !path.startsWith("\\\\")
                && !path.startsWith("//");
    }

    /**
     * Opt-in allowlist check for import. Returns {@code true} when the import allowlist is unset
     * in all sources (legacy back-compat), the kill switch is on, or the upload-zip flag is set.
     * When the allowlist is configured (in {@code system.yaml} OR
     * {@code artifactory.system.properties} OR via {@code -Dartifactory.import.allowed.paths=...}),
     * the path must resolve to a location equal to or inside one of the listed directories
     * (canonical-path resolution defeats {@code ..} and symlink traversal).
     */
    public static boolean isImportPathInAllowList(String path) {
        return isImportPathInAllowList(path, false);
    }

    public static boolean isImportPathInAllowList(String path, boolean allowImportZipFileFromHomePath) {
        if (allowImportZipFileFromHomePath
                || ConstantValues.allowExportImportInsecurePaths.getBoolean()) {
            return true;
        }
        return isInsideAllowedPaths(path,
                resolveAllowedPaths(YAML_KEY_IMPORT_ALLOWED_PATHS, ConstantValues.importAllowedPaths));
    }

    /**
     * Opt-in allowlist check for export. Returns {@code true} when the export allowlist is unset
     * in all sources (legacy back-compat) or the kill switch is on. When configured, the path
     * must resolve to a location equal to or inside one of the listed directories.
     */
    public static boolean isExportPathInAllowList(String path) {
        if (ConstantValues.allowExportImportInsecurePaths.getBoolean()) {
            return true;
        }
        return isInsideAllowedPaths(path,
                resolveAllowedPaths(YAML_KEY_EXPORT_ALLOWED_PATHS, ConstantValues.exportAllowedPaths));
    }

    /**
     * Opt-in allowlist check for backup destinations. Reads the dedicated backup keys
     * ({@link #YAML_KEY_BACKUP_ALLOWED_PATHS} / {@link ConstantValues#backupAllowedPaths}).
     * Returns {@code true} when the backup allowlist is unset in all sources (legacy
     * back-compat); when configured, the path must resolve to a location equal to or inside
     * one of the listed directories.
     * <p>
     * Unlike {@link #isImportPathInAllowList} / {@link #isExportPathInAllowList}, the
     * {@code allow.export.import.insecure.paths} kill switch deliberately does NOT bypass this
     * check: that switch was historically scoped to import/export (its name says so) and
     * backups have never had a bypass for {@link #isArtifactoryHomePath}. A misconfigured
     * backup writes large amounts of data repeatedly to disk, so making this gate
     * non-bypassable is appropriate; once the allowlist is configured, the only way to write
     * elsewhere is to explicitly add that destination to the backup allowlist.
     */
    public static boolean isBackupPathInAllowList(String path) {
        return isInsideAllowedPaths(path,
                resolveAllowedPaths(YAML_KEY_BACKUP_ALLOWED_PATHS, ConstantValues.backupAllowedPaths));
    }

    /**
     * Resolves the allowed-paths list. {@code system.yaml} wins over
     * {@code artifactory.system.properties} (and {@code -Dartifactory.*} JVM args, which are folded
     * into the properties layer). Matches the precedence used by
     * {@code ArtifactoryPropertySource} (yaml > properties/-D > default).
     */
    public static List<String> resolveAllowedPaths(String sysConfigKey, ConstantValues constantValuesKey) {
        try {
            Optional<String> fromYaml = ArtifactoryHome.get().getSysConfig().get(sysConfigKey);
            if (fromYaml.isPresent() && !fromYaml.get().isBlank()) {
                return Arrays.stream(fromYaml.get().split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList();
            }
        } catch (Exception e) {
            log.warn("Failed to read '{}' from system.yaml, falling back to properties: {}",
                    sysConfigKey, e.getMessage());
        }
        return constantValuesKey.getList();
    }

    /**
     * Returns true when {@code path} is equal to, or inside, any of the {@code allowedPaths} directories.
     * Empty {@code allowedPaths} list returns true (no allowlist configured -> legacy behavior preserved).
     * <p>
     * Only the canonical form of both sides is compared, so {@code /tmp/../etc} is rejected when the
     * allowlist is {@code /tmp/} (canonical resolution defeats {@code ..} traversal and symlink
     * escapes).
     */
    public static boolean isInsideAllowedPaths(String path, List<String> allowedPaths) {
        if (allowedPaths == null || allowedPaths.isEmpty() || allowedPaths.stream().allMatch(StringUtils::isBlank)) {
            return true;
        }
        try {
            String candidateCanonicalPath = new File(path).getCanonicalPath();
            return allowedPaths.stream()
                    .filter(StringUtils::isNotBlank)
                    .map(String::trim)
                    .flatMap(allowedPath -> {
                        try {
                            return Stream.of(new File(allowedPath).getCanonicalPath());
                        } catch (IOException e) {
                            log.warn("Failed to resolve canonical path for system configured allowed " +
                                    "path '{}': {}", allowedPath, e.getMessage());
                            return Stream.empty();
                        }
                    })
                    .anyMatch(allowedCanonicalPath -> isSubPath(candidateCanonicalPath, allowedCanonicalPath));
        } catch (IOException e) {
            log.warn("Failed to resolve canonical path for '{}', rejecting as potentially unsafe.", path, e);
        }
        return false;
    }
}
