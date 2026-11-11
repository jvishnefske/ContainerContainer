package org.artifactory.repo.remote.interceptor;

import org.artifactory.api.repo.exception.RepoRejectException;
import org.artifactory.repo.RepoPath;
import org.artifactory.resource.RemoteRepoResource;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ThreadLocalCacheRemoteDownloadInterceptorTest {

    @Test
    void invalidateLocalThreadCache() throws RepoRejectException {
        RemoteRepoResource remoteRepoResource = mock(RemoteRepoResource.class);
        RepoPath repoPath = mock(RepoPath.class);
        when(remoteRepoResource.getRepoPath()).thenReturn(repoPath);
        when(repoPath.getRepoKey()).thenReturn("remove-repo-cache");
        new ThreadLocalCacheRemoteDownloadInterceptor().afterRemoteDownload(remoteRepoResource);
        verify(repoPath, times(1)).getRepoKey();
    }
}