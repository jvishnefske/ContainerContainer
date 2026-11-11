package org.artifactory.federation;

import org.artifactory.repo.RepoPath;

import jakarta.ws.rs.core.Response;
import org.jfrog.rtfs.dto.sync.FileListPageResponse;

/**
 * @author Shahar Palmor
 */
public interface FileListTaskService {
    /**
     * Gets the path of where the file list will be saved
     * @param repoKey - The repository key of which the file list is being prepared
     * @return The path of the file list result
     */
    RepoPath getFileListPath(String repoKey);

    /**
     * Downloads the file list result by the provided path to it
     * @param repoPath - The repo path of where the file list result is saved
     * @return The response
     */
    Response downloadFileList(RepoPath repoPath);

    /**
     * Activates new file list tasks and updates existing ones
     */
    void activateTasks();

    /**
     * Returns one page of file list entries for the given repository.
     *
     * @param repoKey      the repository key
     * @param created      ISO-8601 {@code created} of the last entry from the previous page; {@code null} on the first
     *                     page
     * @param pageSize     maximum number of entries to return (see {@link FileListPaginationLimits#MAX_PAGE_SIZE})
     * @return a page of entries with a cursor for the next page
     */
    FileListPageResponse getFileListPage(String repoKey, String created, int pageSize);
}
