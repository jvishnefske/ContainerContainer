package org.artifactory.repo.cache.expirable;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.rest.constant.RestApiGroup;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.mime.MavenNaming;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class InternalMetadataChecker implements CacheExpiryChecker {

    public static final String INTERNAL_METADATA_FOLDER = ".jfrog/";
    private static final String INTERNAL_METADATA_FOLDER_NAME = ".jfrog";

    /**
     * Indicates whether the specified path is internal metadata path
     * and should be hidden from standard outputs like UI or AQL.
     * <p>
     * The path is considered internal metadata when its first element is the
     * {@code .jfrog} folder, i.e. the path equals {@code .jfrog} or starts with
     * {@code .jfrog/}. This is a plain string check (no NIO {@link java.nio.file.Path}
     * parsing/allocation) since it runs on hot request paths (download, upload,
     * authorization, search, browse).
     *
     * @param path the path to check
     * @return true if the path is metadata, false otherwise
     */
    public static boolean isInternalMetadata(String path) {
        return path != null
                && (path.equals(INTERNAL_METADATA_FOLDER_NAME) || path.startsWith(INTERNAL_METADATA_FOLDER));
    }

    @Override
    public RepoType[] getTypes() {
        return RepoType.values();
    }

    @Override
    public boolean isMetadata(RepoType repoType, String repoKey, String path) {
        return isInternalMetadata(path);
    }

    @Override
    public String getMetadataArtifactType(RepoType repoType, String repoKey, String path) {
        if (isInternalMetadata(path) && MavenNaming.isMavenMetadata(path)) {
            return RestApiGroup.PACKAGE_INFO;
        }
        return null;
    }

    @Override
    public boolean isExpirable(RepoType repoType, String repoKey, String path) {
        return false;
    }

    @Override
    public boolean isLocalGenerated(RepoType repoType, String repoKey, String path) {
        return isInternalMetadata(path);
    }
}
