package org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.watchers;

import org.artifactory.addon.AddonType;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.watch.ArtifactWatchAddon;
import org.artifactory.addon.watch.WatchStatus;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.repo.RepoPath;
import org.artifactory.rest.common.model.artifact.BaseArtifact;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Amir Avidan
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WatchStatusServiceTest {

    private static final String REPO_KEY = "repo-1";
    private static final String PATH = "path/a";

    @Mock
    private ArtifactoryContext context;

    @Mock
    private AddonsManager addonsManager;

    @Mock
    private ArtifactWatchAddon watchAddon;

    @Mock
    private RepositoryService repositoryService;

    @Mock
    private ArtifactoryRestRequest<?> request;

    @Mock
    private RestResponse<BaseArtifact> response;

    @InjectMocks
    private WatchStatusService service;

    @BeforeEach
    void bindContext() {
        ArtifactoryContextThreadBinder.bind(context);
        when(context.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        when(addonsManager.addonByType(ArtifactWatchAddon.class)).thenReturn(watchAddon);
        when(addonsManager.isAddonSupported(AddonType.WATCH)).thenReturn(true);
        when(repositoryService.localFederatedOrCachedRepoDescriptorByKey(REPO_KEY))
                .thenReturn(mock(LocalRepoDescriptor.class));
        when(request.getQueryParamByKey("path")).thenReturn(PATH);
        when(request.getQueryParamByKey("repoKey")).thenReturn(REPO_KEY);
    }

    @AfterEach
    void unbindContext() {
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void execute_watching_returnsUnwatchAction() {
        when(watchAddon.getWatchStatus(any(RepoPath.class))).thenReturn(WatchStatus.WATCHING);

        service.execute(request, response);

        BaseArtifact captured = captureIModel();
        assertEquals("Unwatch", captured.getName());
    }

    @Test
    void execute_notWatching_returnsWatchAction() {
        when(watchAddon.getWatchStatus(any(RepoPath.class))).thenReturn(WatchStatus.NOT_WATCHING);

        service.execute(request, response);

        BaseArtifact captured = captureIModel();
        assertEquals("Watch", captured.getName());
    }

    @Test
    void execute_notAllowed_returnsNullAction() {
        when(watchAddon.getWatchStatus(any(RepoPath.class))).thenReturn(WatchStatus.NOT_ALLOWED);

        service.execute(request, response);

        BaseArtifact captured = captureIModel();
        assertNull(captured);
    }

    @Test
    void execute_addonNotSupported_skipsStatusLookup() {
        when(addonsManager.isAddonSupported(AddonType.WATCH)).thenReturn(false);

        service.execute(request, response);

        verify(watchAddon, never()).getWatchStatus(any());
        verify(response, never()).iModel(any());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private BaseArtifact captureIModel() {
        ArgumentCaptor<BaseArtifact> captor = ArgumentCaptor.forClass(BaseArtifact.class);
        verify(response).iModel((BaseArtifact) captor.capture());
        return captor.getValue();
    }
}
