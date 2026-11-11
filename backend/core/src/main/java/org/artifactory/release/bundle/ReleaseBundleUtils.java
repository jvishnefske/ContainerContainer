package org.artifactory.release.bundle;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.apache.commons.lang3.tuple.Pair;
import org.artifactory.api.rest.distribution.bundle.models.ArtifactsBundleModel;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.storage.db.bundle.model.BundleNode;
import org.artifactory.storage.db.bundle.model.DBArtifactsBundle;
import org.artifactory.storage.db.fs.util.NodeUtils;
import org.jfrog.client.util.PathUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.artifactory.repo.system.SystemRepos.IN_TRANSIT_REPO_KEY;

/**
 * @author Eli Skoran 09/09/2020
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ReleaseBundleUtils {

    @NonNull
    public static RepoPath buildExportFileRepoPath(
            @NonNull String bundleName,
            @NonNull String bundleVersion,
            @NonNull String storingRepo
    ) {
        String repoPath = String.format("%s/%s/%s-%s.zip", bundleName, bundleVersion, bundleName, bundleVersion);
        return RepoPathFactory.create(storingRepo, repoPath);
    }

    @NonNull
    public static ArtifactsBundleModel convertToArtifactsBundleModel(@NonNull DBArtifactsBundle artifactsBundle) {
        return new ArtifactsBundleModel(
                artifactsBundle.getId(),
                artifactsBundle.getName(),
                artifactsBundle.getVersion(),
                artifactsBundle.getStatus(),
                artifactsBundle.getType(),
                artifactsBundle.getStoringRepo()
        );
    }

    @NonNull
    public static String generateTransactionPath(@NonNull String bundleName, @NonNull String bundleVersion) {
        return String.join("/", IN_TRANSIT_REPO_KEY, bundleName, bundleVersion);
    }

    @NonNull
    public static RepoPath extractTargetPathFromIntransitPath(@NonNull RepoPath intransitRepoPath) {
        String pathSeparator = Character.toString(RepoPath.PATH_SEPARATOR);

        String[] pathParts = validateInTransitAndSplit(intransitRepoPath);
        String extractedPath = String.join(pathSeparator, Arrays.copyOfRange(pathParts, 3, pathParts.length));

        return RepoPathFactory.create(extractedPath);
    }

    @NonNull
    public static Pair<String, String> extractReleaseBundleNameAndVersion(@NonNull String transactionPath) {
        String[] transactionParts = transactionPath.split("/");
        if (transactionParts.length != 3) {
            throw new IllegalArgumentException(
                    "Unexpected Formatting of the transactionPath. expecting 3 parts separated by '/' but got: " +
                            transactionPath);
        }
        return Pair.of(transactionParts[1], transactionParts[2]);
    }

    /**
     * @param folderPaths Set of folder paths related to a release bundle
     * @return Map of key depth of folder paths, value set of folder paths with respective depth
     * depth 1 (repository level depth) is removed
     */
    @NonNull
    public static Map<Integer, Set<String>> getDepthFolderPathsMap(@NonNull Set<String> folderPaths) {
        Map<Integer, Set<String>> depthFolderPathsMap =
                new TreeMap<>((o1, o2) -> (o1.compareTo(o2) * -1)); //DECREASING depth order
        for (String path : folderPaths) {
            int depth = NodeUtils.getDepth(path);
            depthFolderPathsMap.computeIfAbsent(depth, k -> new HashSet<>())
                    .add(path);
        }
        depthFolderPathsMap.remove(1); //remove repository depth if exists
        return depthFolderPathsMap;
    }

    /**
     * @param bundleNodes List of BundleNodes related to the release bundle
     *                    (artifacts only, no folders as we don't save them in the DB)
     * @return Set of all folder paths (including all parents of folders) related to release bundle artifacts
     */
    @NonNull
    public static Set<String> getPotentialFolderPathsToDelete(@NonNull List<BundleNode> bundleNodes) {
        Set<String> potentialFolderPathsToDelete = new HashSet<>();
        bundleNodes.forEach(bundleNode -> {
            String folderPath = bundleNode.getRepoPath();
            if (!PathUtils.isDirectoryPath(folderPath)) {
                folderPath = PathUtils.getParent(folderPath);
            }
            if (!folderPath.isEmpty()) {
                potentialFolderPathsToDelete.addAll(getAllFolderPathsFromASingleFolderPath(folderPath));
            }
        });
        return potentialFolderPathsToDelete;
    }

    @NonNull
    private static Set<String> getAllFolderPathsFromASingleFolderPath(@NonNull String folderPath) {
        if (!folderPath.isEmpty() && !folderPath.endsWith("/")) {
            folderPath += "/"; //make sure to use convention of folders in Artifactory
        }
        RepoPath folderRepoPath = RepoPathFactory.create(folderPath);
        if (folderRepoPath.isRoot()) {
            return new HashSet<>();
        }
        Set<String> allFolderPaths = new HashSet<>();
        allFolderPaths.add(folderRepoPath.toPath());
        while (folderRepoPath != null && !folderRepoPath.isRoot()) {
            folderRepoPath = folderRepoPath.getParent();
            if (folderRepoPath != null && !folderRepoPath.isRoot()) {
                allFolderPaths.add(folderRepoPath.toPath());
            }
        }
        return allFolderPaths;
    }

    @NonNull
    public static Pair<String, String> extractReleaseBundleNameAndVersionFromIntransitPath(@NonNull RepoPath repoPath) {
        String[] pathParts = validateInTransitAndSplit(repoPath);

        return Pair.of(pathParts[1], pathParts[2]);
    }

    @NonNull
    private static String[] validateInTransitAndSplit(@NonNull RepoPath repoPath) {
        if (!repoPath.getRepoKey().equals(IN_TRANSIT_REPO_KEY)) {
            throw new IllegalArgumentException(
                    "Unexpected repo path. expected _intransit repo path, got: " + repoPath.getPath());
        }
        String pathSeparator = Character.toString(RepoPath.PATH_SEPARATOR);
        String[] pathParts = repoPath.toPath().split(pathSeparator);

        if (pathParts.length <= 3) {
            throw new IllegalArgumentException(
                    "repo path of _intransit is too short. expected format is " +
                            "_intransit/bundleName/bundleVersion/path. got: " +
                            repoPath.getPath());
        }
        return pathParts;
    }
}
