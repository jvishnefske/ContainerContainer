package org.artifactory.repo.service.flexible;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.service.flexible.context.MoveCopyContext;
import org.artifactory.util.RepoPathUtils;
import org.jfrog.client.util.PathUtils;

import java.util.regex.Pattern;

public class MoveCopyTargetPathResolver {

    public static RepoPath resolveTargetPath(MoveCopyContext context, RepoPath currentSourcePath) {
        // Clean DB dots (".") from repo path
        String path = ".".equals(currentSourcePath.getPath()) ? "" : currentSourcePath.getPath();
        RepoPath sourceRepoPath = RepoPathFactory.create(currentSourcePath.getRepoKey(), path);
        // Clean base target path from context
        String contextTargetPath = context.getTargetPath();
        String baseTargetPath = StringUtils.isBlank(contextTargetPath) ? "/" : contextTargetPath;
        // Identify base target path type (is folder/file)
        boolean folder = baseTargetPath.endsWith("/");
        // Clean slashes to avoid duplicate slashes
        baseTargetPath = PathUtils.trimSlashes(baseTargetPath);
        String diffPath;
        if (folder && !context.isCopyContent()) {
            diffPath = getDiffPath(context.getSourceRepoPath(), sourceRepoPath, true);
        } else {
            diffPath = getDiffPath(context.getSourceRepoPath(), sourceRepoPath, false);
        }
        // Create target path
        String targetPath = baseTargetPath + "/" + diffPath + (currentSourcePath.isFolder() ? "/" : "");
        // Make sure to remove duplicate slashes
        targetPath = PathUtils.removeDuplicateSlashes(targetPath);
        String targetRepo =
                StringUtils.isNotBlank(context.getTargetKey()) ? context.getTargetKey() : sourceRepoPath.getRepoKey();
        return InternalRepoPathFactory.create(targetRepo, targetPath);
    }

    /**
     * The method resolves the relative sub path of the source in relation to the root folder of the copy
     */
    @VisibleForTesting
    static String getDiffPath(RepoPath rootSource, RepoPath currentSource, boolean skipName) {
        if (rootSource == null) {
            return currentSource.getPath();
        }
        if (isTrash(rootSource)) {
            rootSource = cleanRepoKeyFromPath(rootSource);
        }
        if (isTrash(currentSource)) {
            currentSource = cleanRepoKeyFromPath(currentSource);
        }
        String[] rootSourceElements = PathUtils.getPathElements(rootSource.getPath());
        String[] currentSourceElements = PathUtils.getPathElements(currentSource.getPath());
        int maxIndex = Math.min(rootSourceElements.length, currentSourceElements.length);
        int index = 0;
        String commonPath = "";
        int skip = skipName ? 1 : 0;
        while (index < maxIndex - skip && rootSourceElements[index].equals(currentSourceElements[index])) {
            commonPath = StringUtils.isBlank(commonPath) ? rootSourceElements[index] :
                    commonPath + "/" + rootSourceElements[index];
            index++;
        }
        return PathUtils.trimSlashes(currentSource.getPath().replaceFirst(Pattern.quote(commonPath), ""));
    }

    /**
     * Returns true if the repoPath represent trash repo path
     */
    private static boolean isTrash(RepoPath pathToMove) {
        return RepoPathUtils.isTrash(pathToMove);
    }

    /**
     * In case of trash can the method removes the repo key from the path
     */
    private static RepoPath cleanRepoKeyFromPath(RepoPath pathToMove) {
        String ancestor = PathUtils.stripFirstPathElement(pathToMove.getPath());
        String repoKey = PathUtils.getFirstPathElement(pathToMove.getPath());
        return RepoPathFactory.create(repoKey, ancestor);
    }

}
