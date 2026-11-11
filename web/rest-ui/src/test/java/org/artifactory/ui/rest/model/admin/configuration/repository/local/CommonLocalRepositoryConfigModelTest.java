package org.artifactory.ui.rest.model.admin.configuration.repository.local;

import org.artifactory.api.repo.RepositoryService;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.repo.ReverseProxyDescriptor;
import org.artifactory.model.RepoConfig;
import org.artifactory.rest.common.exception.RepoConfigException;
import org.artifactory.ui.rest.model.admin.configuration.repository.RepositoryConfigModel;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.CreateRepoConfigHelper;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.UpdateRepoConfigHelper;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.builder.RepoConfigDescriptorBuilder;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.builder.RepoConfigModelBuilder;
import org.testng.annotations.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;

/**
 * Coverage for the no-{@code fedAttrOptIn} overloads added under RTDEV-88032 and for the restored
 * {@code getReverseProxyDescriptor} delegation (the actual "revert issue" fix).
 */
public class CommonLocalRepositoryConfigModelTest {

    /**
     * Regression guard for the revert fix: getReverseProxyDescriptor must delegate to the builder (it was
     * temporarily broken to {@code return null}). Verifies it forwards the model's advanced config and the
     * repoConfig, and returns whatever the builder produces.
     */
    @Test
    public void getReverseProxyDescriptorDelegatesToBuilder() {
        CommonLocalRepositoryConfigModel model = new CommonLocalRepositoryConfigModel();
        LocalAdvancedRepositoryConfigModel advanced = new LocalAdvancedRepositoryConfigModel();
        model.setAdvanced(advanced);
        RepoConfigDescriptorBuilder builder = mock(RepoConfigDescriptorBuilder.class);
        RepoConfig repoConfig = mock(RepoConfig.class);
        ReverseProxyDescriptor descriptor = mock(ReverseProxyDescriptor.class);
        when(builder.buildReverseProxyDescriptor(advanced, repoConfig)).thenReturn(descriptor);

        ReverseProxyDescriptor result = model.getReverseProxyDescriptor(repoConfig, builder);

        assertSame(result, descriptor, "must return the builder-produced descriptor, not null");
        verify(builder).buildReverseProxyDescriptor(advanced, repoConfig);
    }

    @Test
    public void fromRepoConfigReturnsNull() {
        CommonLocalRepositoryConfigModel model = new CommonLocalRepositoryConfigModel();
        RepoConfigModelBuilder builder = mock(RepoConfigModelBuilder.class);
        RepoConfig repoConfig = mock(RepoConfig.class);

        RepositoryConfigModel<?, ?, ?> result = model.fromRepoConfig(builder, repoConfig);

        assertNull(result, "common base fromRepoConfig is a stub returning null");
    }

    @Test
    public void createRepoConfigReturnsNullWithoutTouchingCreator() throws RepoConfigException {
        CommonLocalRepositoryConfigModel model = new CommonLocalRepositoryConfigModel();
        CreateRepoConfigHelper creator = mock(CreateRepoConfigHelper.class);

        RepoConfig result = model.createRepoConfig(creator, mock(MutableCentralConfigDescriptor.class),
                mock(RepositoryService.class));

        assertNull(result, "common base createRepoConfig is a no-op returning null");
        verifyNoInteractions(creator);
    }

    @Test
    public void updateRepoIsNoopWithoutTouchingUpdater() throws RepoConfigException {
        CommonLocalRepositoryConfigModel model = new CommonLocalRepositoryConfigModel();
        UpdateRepoConfigHelper updater = mock(UpdateRepoConfigHelper.class);

        model.updateRepo(updater);

        verifyNoInteractions(updater);
    }

    @Test
    public void getFederationReturnsNull() {
        assertNull(new CommonLocalRepositoryConfigModel().getFederation());
    }
}
