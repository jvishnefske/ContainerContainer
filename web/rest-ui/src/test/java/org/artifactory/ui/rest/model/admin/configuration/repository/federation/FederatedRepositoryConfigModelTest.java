package org.artifactory.ui.rest.model.admin.configuration.repository.federation;

import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.builder.RepoConfigModelBuilder;
import org.testng.annotations.Test;

import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

/**
 * @author Alexei Vainshtein
 */
public class FederatedRepositoryConfigModelTest {

    @Test
    public void toStringReturnsJson() {
        assertTrue(new FederatedRepositoryConfigModel().toString().startsWith("{"),
                "toString must return JSON, not Lombok default");
    }

    @Test
    public void testGetAdvanced() {
        FederatedRepositoryConfigModel model = new FederatedRepositoryConfigModel();
        FederatedRepoAdvancedRepositoryConfigModel advanced = new FederatedRepoAdvancedRepositoryConfigModel();
        advanced.setProxy("proxy");
        model.setAdvanced(advanced);
        FederatedRepoAdvancedRepositoryConfigModel returnedAdvanced = model.getAdvanced();
        assertNotNull(returnedAdvanced);
        assertEquals(returnedAdvanced.getProxy(), "proxy");
    }

    @Test
    public void fromRepoConfigReturnsThis() {
        FederatedRepositoryConfigModel model = new FederatedRepositoryConfigModel();
        RepoConfigModelBuilder builder = mock(RepoConfigModelBuilder.class);
        FederatedRepoConfig repoConfig = mock(FederatedRepoConfig.class);

        FederatedRepositoryConfigModel result = model.fromRepoConfig(builder, repoConfig);

        assertSame(result, model, "fromRepoConfig must return this");
    }
}