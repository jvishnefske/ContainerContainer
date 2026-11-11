package org.artifactory.pipelines;

import org.artifactory.descriptor.repo.RepoType;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author Alexei Vainshtein
 */
public interface InternalPipelinesService {

    /**
     * Used for creating a project related pipe-nfo repository
     */
    String PIPE_INFO_REPO_SUFFIX = "-pipe-info";

    /**
     * Saves the provided input stream to the given path
     *
     * @param filePath    - The path that the file will be created
     * @param pipeRepoKey - The pipe info repository name if provided
     * @param projectKey  - The project key if provided
     * @param inputStream - The file content
     */
    void saveContent(String filePath, String pipeRepoKey, String projectKey, InputStream inputStream)
            throws IOException;

    /**
     * @param repoName - The repository name to check if of type {@link RepoType#PipeInfo}
     * @return true if the provided repo name is of type {@link RepoType#PipeInfo}, false otherwise.
     */
    boolean isPipeInfoRepo(String repoName);
}
