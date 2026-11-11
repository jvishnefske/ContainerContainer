package org.artifactory.federation;

import jakarta.annotation.Nonnull;
import jakarta.ws.rs.core.Response;
import org.artifactory.repo.RepoPath;
import org.springframework.stereotype.Service;

import java.io.InputStream;

/**
 * @author Shahar Palmor
 * this class is default sevice for FederatedFullSyncRepoService
 */
@Service
public class DefaultFederatedFullSyncRepoServiceImpl implements FederatedFullSyncRepoService {

    @Override
    public void createFullSyncRepo() {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public RepoPath deployFullSyncFile(String fileName, InputStream inputStream, long eventId)
            throws FullSyncException {
        return null;
    }

    @Override
    public RepoPath deployDryFullSyncFile(String fileName, InputStream inputStream, String jpdId, String localRepoKey,
            String remoteRepoKey) throws FullSyncException {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public void undeployFullSyncFile(String fileName) throws FullSyncException{
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public Response downloadFullSyncFile(String fileName) throws FullSyncException {
        return null;
    }

    @Override
    public long getFullSyncFileProperty(String repoKey, String fileName) throws FullSyncException {
        return 0;
    }

    @Override
    public void validateSystemRepo() {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Nonnull
    private String getUnsupportedMessage() {
        return "Unsupported operation: federated";
    }
}
