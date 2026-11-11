package org.artifactory.version;

import org.apache.commons.lang3.StringUtils;

/**
 * @author dudim
 */
public interface ArtifactoryServiceVersion {

    String getVersion();

    long getRevision();

    boolean isCurrent();

    boolean before(ArtifactoryServiceVersion otherVersion);

    boolean after(ArtifactoryServiceVersion otherVersion);

    boolean beforeOrEqual(ArtifactoryServiceVersion otherVersion);

    boolean afterOrEqual(ArtifactoryServiceVersion otherVersion);

    static ArtifactoryServiceVersion[] values() {
        return ValuesHolder.VALUES.clone();
    }

    static ArtifactoryServiceVersion getCurrent() {
        return CurrentVersionLoader.getInstance().getCurrent();
    }

    static boolean isCurrentVersion(String versionString, String revisionString) {
        if (StringUtils.isBlank(versionString)) {
            throw new IllegalArgumentException("Version value is empty!");
        }
        // Dev version
        if (getCurrent().getVersion().equals(convertToMavenVersion(versionString))
                || versionString.endsWith("-SNAPSHOT")
                || versionString.contains(".x-DOWN-")
                || versionString.contains(".x-DOWN.")
                || versionString.startsWith("${") // TODO: [by fsi] why we keep the old issue
        ) {
            return true;
        }
        // No revision, from version it is not latest => Probably an error here?
        if (revisionString == null) {
            return false;
        }
        // Dev revision
        if (revisionString.startsWith("dev") || revisionString.startsWith("${")) {
            return true;
        }
        // Stork dev branch version
        return (versionString.contains(".x.") || versionString.contains(".x_")) &&
                StringUtils.isNumeric(revisionString);

    }

    static ArtifactoryServiceVersion valueOf(String versionOverride) {
        return ArtifactoryServiceVersionProvider.get(versionOverride);
    }

    static String convertToMavenVersion(String version) {
        if (version.contains("_m") || version.contains("_p")) {
            version = version.replace("_m", "-m");
            version = version.replace("_p", "-p");
        }
        return version;
    }

    ArtifactoryServiceVersionProvider getProvider();

    final class ValuesHolder {
        private static final ArtifactoryServiceVersion[] VALUES = buildValues();

        private ValuesHolder() {
        }

        private static ArtifactoryServiceVersion[] buildValues() {
            ArtifactoryServiceVersionProvider[] providers = ArtifactoryServiceVersionProvider.values();
            ArtifactoryServiceVersion[] artifactoryServiceVersions = new ArtifactoryServiceVersion[providers.length];
            for (int i = 0; i < providers.length; i++) {
                artifactoryServiceVersions[i] = providers[i].get();
            }
            return artifactoryServiceVersions;
        }
    }
}