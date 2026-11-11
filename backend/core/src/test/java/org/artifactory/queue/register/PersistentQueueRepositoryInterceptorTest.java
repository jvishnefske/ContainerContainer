package org.artifactory.queue.register;

import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.repo.RepoConfigChange;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_CREATE;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_DELETE;
import static org.artifactory.repo.RepoDetailsType.FEDERATED;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author Alexei Vainshtein
 */
public class PersistentQueueRepositoryInterceptorTest {

    @Test
    public void testOnAfterSave() {
        PersistentQueueRegistrationService queueRegistrationService = mock(PersistentQueueRegistrationService.class);
        PersistentQueueRepositoryInterceptor interceptor =
                new PersistentQueueRepositoryInterceptor(queueRegistrationService);
        interceptor.onAfterSave(
                Map.of(FEDERATED, List.of(new RepoConfigChange("key_old", REPO_DELETE, null,
                                FederatedRepoConfig.create("repoKey", RepoType.Generic)),
                        new RepoConfigChange("key", REPO_CREATE,
                                FederatedRepoConfig.create("repoKey", RepoType.Generic),
                                null))));
        verify(queueRegistrationService, times(1)).deRegisterRepository("key_old");
        verify(queueRegistrationService, times(1)).registerRepository("key");
    }
}