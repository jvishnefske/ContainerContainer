package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.action.recalculateindex;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.terraform.TerraformAddon;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.profile.PackageProfile;
import org.artifactory.common.repo.PackageType;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.ph.PackageResourceService;
import org.artifactory.repo.service.InternalRepositoryService;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.ConfigurableEnvironment;

import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Linoyp
 */
public class TerraformIndexCalculatorTest {

    private static final String REPO_NAME = "repo";

    @Mock
    private ArtifactoryContext context;
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private TerraformAddon terraformAddon;
    @Mock
    private InternalRepositoryService repositoryService;
    @Mock
    private ConfigurableEnvironment configurableEnvironment;
    @Mock
    private CentralConfigDescriptor descriptor;
    @Mock
    private CentralConfigService service;
    @Mock
    private PackageResourceService packageResourceService;
    private AutoCloseable closeable;

    @BeforeEach
    void setup() {
        closeable = MockitoAnnotations.openMocks(this);
        ArtifactoryContextThreadBinder.bind(context);

        when(addonsManager.addonByType(TerraformAddon.class)).thenReturn(terraformAddon);
        when(context.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        when(context.getRepositoryService()).thenReturn(repositoryService);
        when(context.beanForType(ConfigurableEnvironment.class)).thenReturn(configurableEnvironment);
        when(context.beanForType(PackageResourceService.class)).thenReturn(packageResourceService);
    }

    @AfterEach
    void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        closeable.close();
    }

    @Test
    void calculateIndex_shouldCallTerraformAddonReindex_whenLegacyProfile() throws Exception {
        when(configurableEnvironment.getActiveProfiles()).thenReturn(new String[] {});
        FederatedRepoConfig federatedRepoConfig = Mockito.mock(FederatedRepoConfig.class, RETURNS_DEEP_STUBS);
        LocalRepoDescriptor localRepoDescriptor = mock(LocalRepoDescriptor.class);
        when(localRepoDescriptor.getType()).thenReturn(RepoType.Terraform);
        when(repositoryService.localOrFederatedRepoConfigByKey(REPO_NAME)).thenReturn(federatedRepoConfig);
        when(repositoryService.localFederatedOrCachedRepoDescriptorByKey(REPO_NAME)).thenReturn(localRepoDescriptor);
        when(federatedRepoConfig.getKey()).thenReturn(REPO_NAME);
        when(service.getDescriptor()).thenReturn(descriptor);
        when(context.getCentralConfig()).thenReturn(service);

        TerraformIndexCalculator indexCalculator = new TerraformIndexCalculator();
        indexCalculator.setRepoKey(REPO_NAME);
        indexCalculator.calculateIndex();

        verify(terraformAddon, times(1)).reindexTerraform(REPO_NAME);
    }

    @Test
    void calculateIndex_shouldCallPackageResourceServiceReindex_whenNewTerraformProfile() throws Exception {
        when(configurableEnvironment.getActiveProfiles()).thenReturn(new String[] {PackageProfile.NEW_TERRAFORM_IMPL});

        TerraformIndexCalculator indexCalculator = new TerraformIndexCalculator();
        indexCalculator.setRepoKey(REPO_NAME);
        indexCalculator.calculateIndex();

        verify(packageResourceService).reindex(PackageType.TERRAFORM, REPO_NAME, null);
    }
}
