package org.artifactory.addon.debian;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * @author Priyanshu Gupta
 */
@Getter
@Setter
public class DscInfo {

    private PackageInfo packageInfo;
    private List<FileChecksumInfo> associatedFilesAndChecksums;
    private AdditionalMetadata additionalMetadataLinks;
    private List<PackageListEntry> packageLists;
    private List<String> depends;
    private List<String> conflicts;

    @Getter
    @Setter
    public static class PackageInfo {
        private String source;
        private String version;
        private String architecture;
        private String format;
        private List<String> binary;
        private String maintainer;
        private String uploaders;
    }

    @Getter
    @Setter
    public static class FileChecksumInfo {
        private String fileName;
        private String checksumsSha256;
        private String checksumsSha1;
        private String size;
    }

    @Getter
    @Setter
    public static class AdditionalMetadata {
        private String homepage;
        private String standardsVersion;
        private String vcsBrowser;
        private String vcsGit;
    }

    @Getter
    @Setter
    public static class PackageListEntry {
        private String packageName;
        private String type;
        private String section;
        private String priority;
        private String architecture;
    }
}
