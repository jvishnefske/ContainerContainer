package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.action.recalculateindex;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.swift.SwiftAddon;
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
import org.springframework.core.env.ConfigurableEnvironment;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import jakarta.ws.rs.core.Response;

import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Natalie Berni
 */
public class SwiftIndexCalculatorTest {

    private static final String REPO_NAME = "repo";

    @Mock
    private ArtifactoryContext context;
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private SwiftAddon swiftAddon;
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

    @BeforeMethod
    public void setup() {
        closeable = MockitoAnnotations.openMocks(this);
        ArtifactoryContextThreadBinder.bind(context);

        when(addonsManager.addonByType(SwiftAddon.class)).thenReturn(swiftAddon);
        when(context.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        when(context.getRepositoryService()).thenReturn(repositoryService);
        when(context.beanForType(ConfigurableEnvironment.class)).thenReturn(configurableEnvironment);
        when(context.beanForType(PackageResourceService.class)).thenReturn(packageResourceService);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        closeable.close();
    }

    @Test
    public void testLegacyCalculateIndex() throws Exception {
        ArtifactoryContextThreadBinder.bind(context);
        when(configurableEnvironment.getActiveProfiles()).thenReturn(new String[] {});
        FederatedRepoConfig federatedRepoConfig = Mockito.mock(FederatedRepoConfig.class, RETURNS_DEEP_STUBS);
        LocalRepoDescriptor localRepoDescriptor = mock(LocalRepoDescriptor.class);
        when(localRepoDescriptor.getType()).thenReturn(RepoType.Swift);
        when(repositoryService.localOrFederatedRepoConfigByKey(REPO_NAME)).thenReturn(federatedRepoConfig);
        when(repositoryService.localFederatedOrCachedRepoDescriptorByKey("repo")).thenReturn(localRepoDescriptor);
        when(federatedRepoConfig.getKey()).thenReturn(REPO_NAME);

        when(service.getDescriptor()).thenReturn(descriptor);
        when(context.getCentralConfig()).thenReturn(service);

        SwiftIndexCalculator indexCalculator = new SwiftIndexCalculator();
        indexCalculator.setRepoKey(REPO_NAME);
        indexCalculator.calculateIndex();
        verify(swiftAddon, times(1)).reindexSwift(REPO_NAME);
    }

    @Test
    public void testSwiftPackageHandlerCalculateIndex() throws Exception {
        when(configurableEnvironment.getActiveProfiles()).thenReturn(new String[] {PackageProfile.NEW_SWIFT_IMPL});
        when(packageResourceService.reindex(PackageType.SWIFT, "repo", true)).thenReturn(mock(Response.class));

        SwiftIndexCalculator indexCalculator = new SwiftIndexCalculator();
        indexCalculator.setRepoKey(REPO_NAME);
        indexCalculator.calculateIndex();
        verify(packageResourceService).reindex(PackageType.SWIFT, REPO_NAME, true);
    }
}