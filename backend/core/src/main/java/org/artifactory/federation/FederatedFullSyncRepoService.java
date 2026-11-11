package org.artifactory.federation;

import org.artifactory.repo.RepoPath;

import jakarta.ws.rs.core.Response;
import java.io.InputStream;

/**
 * @author Shahar Palmor
 */
public interface FederatedFullSyncRepoService {

    void createFullSyncRepo();
    /**
     * Deploys content to the full sync system repo
     *
     * @param fileName    is the file name which includes the repository name
     * @param inputStream is the content of the file
     * @return the path of where the file was deployed
     * @throws FullSyncException upon exception
     */
    RepoPath deployFullSyncFile(String fileName, InputStream inputStream, long eventId) throws FullSyncException;

    /**
     * Deploys content to the full sync system repo
     *
     * @param fileName    is the file name which includes the repository name
     * @param inputStream is the content of the file
     * @param jpdId       is the jpd id
     * @param localRepoKey is the local repo key
     * @param remoteRepoKey is the remote repo key
     * @return the path of where the file was deployed
     * @throws FullSyncException upon exception
     */
    RepoPath deployDryFullSyncFile(String fileName, InputStream inputStream, String jpdId, String localRepoKey,
            String remoteRepoKey)throws FullSyncException;

    /**
     * Undeploy the file from the full sync system repo
     *
     * @param fileName is the file name which we wish to delete
     * @throws FullSyncException upon exception
     */
    void undeployFullSyncFile(String fileName) throws FullSyncException;


    Response downloadFullSyncFile(String fileName) throws FullSyncException;
    long getFullSyncFileProperty(String repoKey, String fileName) throws FullSyncException;

    void validateSystemRepo();
}
