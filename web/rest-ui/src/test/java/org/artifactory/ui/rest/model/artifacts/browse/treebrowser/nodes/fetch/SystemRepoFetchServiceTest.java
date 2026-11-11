package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.fetch;

import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.model.ReleaseBundleRepoConfig;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.security.ArtifactoryPermission;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.TreeFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Set;

import static org.artifactory.common.ConstantValues.treeBrowserCheckOnlyReadPermissionOnRootLevel;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Amir Avidan
 */
class SystemRepoFetchServiceTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private TreeFilter treeFilter;
    @Mock
    private DescriptorFilter descriptorFilter;
    @Mock
    private RepositoryConfigService repositoryConfigService;
    @Mock
    private RepositoryService repositoryService;
    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private SecurityService securityService;
    @Mock
    private ArtifactoryContext context;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        when(context.getRepositoryService()).thenReturn(repositoryService);
        when(context.getRepositoryConfigService()).thenReturn(repositoryConfigService);
        when(context.getAuthorizationService()).thenReturn(authorizationService);
        when(context.beanForType(SecurityService.class)).thenReturn(securityService);
        ArtifactoryContextThreadBinder.bind(context);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
        ArtifactoryContextThreadBinder.unbind();
    }

    @ParameterizedTest
    @ValueSource(strings = {"TRUE", "FALSE"})
    void testAcceptGettingPermissionAccordingToConstantValue(String onlyReadPermission) {
        setStringSystemProperty(treeBrowserCheckOnlyReadPermissionOnRootLevel, onlyReadPermission);
        ReleaseBundleRepoConfig repoConfig = ReleaseBundleRepoConfig.create("test-release-bundle");
        when(repositoryConfigService.getRepoConfigsByType(RepoDetailsType.RELEASE_BUNDLE))
                .thenReturn(List.of(repoConfig));
        when(descriptorFilter.accept(repoConfig, Set.of(ArtifactoryPermission.READ)))
                .thenReturn(true);
        SystemRepoFetchService service = new SystemRepoFetchService(treeFilter, true, descriptorFilter);
        service.getReleaseBundlesRepoNodes(0, 10);
        if (onlyReadPermission.equals("TRUE")) {
            verify(descriptorFilter).accept(repoConfig, Set.of(ArtifactoryPermission.READ));
        } else {
            verify(descriptorFilter).accept(repoConfig, Set.of(ArtifactoryPermission.values()));
        }
    }
}
