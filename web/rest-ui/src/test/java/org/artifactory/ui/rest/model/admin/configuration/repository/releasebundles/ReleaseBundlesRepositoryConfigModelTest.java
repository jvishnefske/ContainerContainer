package org.artifactory.ui.rest.model.admin.configuration.repository.releasebundles;

import org.artifactory.api.repo.RepositoryService;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.model.RepoConfig;
import org.artifactory.rest.common.exception.RepoConfigException;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.CreateRepoConfigHelper;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.UpdateRepoConfigHelper;
import org.testng.annotations.Test;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

public class ReleaseBundlesRepositoryConfigModelTest {

    @Test
    public void toStringReturnsJson() {
        assertTrue(new ReleaseBundlesRepositoryConfigModel().toString().startsWith("{"),
                "toString must return JSON, not Lombok default");
    }

    @Test
    public void createRepoConfigDelegatesWithFedAttrOptInFalse() throws RepoConfigException {
        ReleaseBundlesRepositoryConfigModel model = spy(new ReleaseBundlesRepositoryConfigModel());
        CreateRepoConfigHelper creator = mock(CreateRepoConfigHelper.class);
        MutableCentralConfigDescriptor descriptor = mock(MutableCentralConfigDescriptor.class);
        RepositoryService repoService = mock(RepositoryService.class);
        RepoConfig expected = mock(RepoConfig.class);
        doReturn(expected).when(model).createRepoConfig(creator, descriptor, repoService, false);

        RepoConfig result = model.createRepoConfig(creator, descriptor, repoService);

        verify(model).createRepoConfig(creator, descriptor, repoService, false);
        assertSame(result, expected, "must return the delegate's result");
    }

    @Test
    public void updateRepoDelegatesWithFedAttrOptInFalse() throws Exception {
        ReleaseBundlesRepositoryConfigModel model = spy(new ReleaseBundlesRepositoryConfigModel());
        UpdateRepoConfigHelper updater = mock(UpdateRepoConfigHelper.class);
        doNothing().when(model).updateRepo(updater, false);

        model.updateRepo(updater);

        verify(model).updateRepo(updater, false);
    }
}
