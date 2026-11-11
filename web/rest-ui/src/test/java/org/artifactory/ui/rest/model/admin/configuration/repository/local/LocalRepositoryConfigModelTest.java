package org.artifactory.ui.rest.model.admin.configuration.repository.local;

import org.artifactory.model.LocalConfig;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.builder.RepoConfigModelBuilder;
import org.testng.annotations.Test;

import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertSame;

public class LocalRepositoryConfigModelTest {

    @Test
    public void fromRepoConfigReturnsThis() {
        LocalRepositoryConfigModel model = new LocalRepositoryConfigModel();
        RepoConfigModelBuilder builder = mock(RepoConfigModelBuilder.class);
        LocalConfig repoConfig = mock(LocalConfig.class);

        LocalRepositoryConfigModel result = model.fromRepoConfig(builder, repoConfig);

        assertSame(result, model, "fromRepoConfig must return this");
    }
}
