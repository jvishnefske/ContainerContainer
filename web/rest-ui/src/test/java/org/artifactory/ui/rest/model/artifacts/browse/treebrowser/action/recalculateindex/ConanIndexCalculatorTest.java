package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.action.recalculateindex;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.conan.ConanAddon;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Shahaf Golan
 */
class ConanIndexCalculatorTest {

    private static final String REPO_KEY = "conan-local";

    private AutoCloseable autoCloseable;
    private ArtifactoryContext context;
    private AddonsManager addonsManager;

    @Mock
    private ConanAddon conanAddon;

    @BeforeEach
    void setup() {
        autoCloseable = MockitoAnnotations.openMocks(this);
        context = mock(ArtifactoryContext.class);
        ArtifactoryContextThreadBinder.bind(context);
        addonsManager = mock(AddonsManager.class);

        when(addonsManager.addonByType(ConanAddon.class)).thenReturn(conanAddon);
        when(context.beanForType(AddonsManager.class)).thenReturn(addonsManager);

        RepositoryService repositoryService = mock(RepositoryService.class);
        LocalRepoDescriptor descriptor = mock(LocalRepoDescriptor.class);
        when(descriptor.getType()).thenReturn(RepoType.Conan);
        when(repositoryService.localFederatedOrCachedRepoDescriptorByKey(REPO_KEY)).thenReturn(descriptor);
        when(context.getRepositoryService()).thenReturn(repositoryService);
    }

    @AfterEach
    void cleanup() throws Exception {
        autoCloseable.close();
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void testCalculateIndexWithNoPathCallsReindexRepo() throws Exception {
        ConanIndexCalculator calculator = new ConanIndexCalculator();
        calculator.setRepoKey(REPO_KEY);

        calculator.calculateIndex();

        verify(conanAddon, times(1)).reindexRepo(REPO_KEY);
        verify(conanAddon, never()).reindexPath(REPO_KEY, null);
    }

    @Test
    void testCalculateIndexWithPathCallsReindexPath() throws Exception {
        String path = "user/simple/1.0/channel";

        ConanIndexCalculator calculator = new ConanIndexCalculator();
        calculator.setRepoKey(REPO_KEY);
        calculator.setPath(path);

        calculator.calculateIndex();

        verify(conanAddon, never()).reindexRepo(REPO_KEY);
        verify(conanAddon, times(1)).reindexPath(REPO_KEY, path);
    }

    @Test
    void testCalculateIndexWithBlankPathCallsReindexRepo() throws Exception {
        ConanIndexCalculator calculator = new ConanIndexCalculator();
        calculator.setRepoKey(REPO_KEY);
        calculator.setPath("   ");

        calculator.calculateIndex();

        verify(conanAddon, times(1)).reindexRepo(REPO_KEY);
        verify(conanAddon, never()).reindexPath(REPO_KEY, "   ");
    }
}
