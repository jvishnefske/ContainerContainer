package org.artifactory.ui.rest.service.artifacts.browse.treebrowser.actions;

import org.artifactory.addon.AddonType;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.watch.ArtifactWatchAddon;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.exception.http.ForbiddenException;
import org.artifactory.fs.ItemInfo;
import org.artifactory.repo.RepoPath;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.action.WatchArtifact;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Amir Avidan
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WatchArtifactServiceTest {

    private static final String REPO_KEY = "repo-1";
    private static final String PATH = "path/a";
    private static final String CURRENT_USER = "current";

    @Mock
    private ArtifactoryContext context;

    @Mock
    private AddonsManager addonsManager;

    @Mock
    private ArtifactWatchAddon watchAddon;

    @Mock
    private RepositoryService repositoryService;

    @Mock
    private SecurityService securityService;

    @Mock
    private ArtifactoryRestRequest<WatchArtifact> request;

    @InjectMocks
    private WatchArtifactService service;

    @BeforeEach
    void bindContext() {
        ArtifactoryContextThreadBinder.bind(context);
        when(context.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        when(addonsManager.addonByType(ArtifactWatchAddon.class)).thenReturn(watchAddon);
        when(addonsManager.isAddonSupported(AddonType.WATCH)).thenReturn(true);
        when(securityService.currentUsername()).thenReturn(CURRENT_USER);

        WatchArtifact watchArtifact = new WatchArtifact();
        watchArtifact.setRepoKey(REPO_KEY);
        watchArtifact.setPath(PATH);
        watchArtifact.setParam("watch");
        when(request.getImodel()).thenReturn(watchArtifact);

        ItemInfo itemInfo = mock(ItemInfo.class);
        RepoPath repoPath = mock(RepoPath.class);
        when(repoPath.getPath()).thenReturn(PATH);
        when(itemInfo.getRepoPath()).thenReturn(repoPath);
        when(repositoryService.getItemInfo(any(RepoPath.class))).thenReturn(itemInfo);
    }

    @AfterEach
    void unbindContext() {
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void watch_addonThrowsForbidden_responseHasForbiddenStatusAndPropagatedMessage() {
        ArtifactoryRestResponse<Object> response = new ArtifactoryRestResponse<>();
        doThrow(new ForbiddenException("denied")).when(watchAddon).addWatcher(any(), any());

        service.execute(request, response);

        assertEquals(403, response.getResponseCode());
        String error = response.getFeedbackMsg().getError();
        assertNotNull(error);
        assertTrue(error.contains("denied"), "Expected propagated message, got: " + error);
    }

    @Test
    void watch_genericException_fallsBackToFailedToAddWatchMessage() {
        ArtifactoryRestResponse<Object> response = new ArtifactoryRestResponse<>();
        doThrow(new RuntimeException("boom")).when(watchAddon).addWatcher(any(), any());

        service.execute(request, response);

        String error = response.getFeedbackMsg().getError();
        assertNotNull(error);
        assertTrue(error.contains("Failed to add watch"),
                "Expected fallback error message, got: " + error);
    }

    @Test
    void watch_happyPath_callsAddWatcherWithCurrentUser() {
        ArtifactoryRestResponse<Object> response = new ArtifactoryRestResponse<>();

        service.execute(request, response);

        verify(watchAddon).addWatcher(any(RepoPath.class), eq(CURRENT_USER));
    }
}
