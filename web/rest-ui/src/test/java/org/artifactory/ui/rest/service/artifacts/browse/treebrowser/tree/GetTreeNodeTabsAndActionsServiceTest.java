package org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tree;

import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.actions.TabsAndActions;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.actions.TreeNodeActionsPopulator;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.actions.TreeNodeTabsPopulator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Spy;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * @author Hussein Alshqerat
 */
class GetTreeNodeTabsAndActionsServiceTest {
    private final GetTreeNodeTabsAndActionsService GetTreeNodeTabsAndActionsService =
            new GetTreeNodeTabsAndActionsService();
    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private SecurityService securityService;
    @Mock
    private TreeNodeTabsPopulator treeNodeTabsPopulator;
    @Spy
    private TreeNodeActionsPopulator treeNodeActionsPopulator;
    @Mock
    private ArtifactoryRestRequest request;
    private AutoCloseable autoCloseable;
    private ArtifactoryRestResponse<?> response;

    @BeforeEach
    void setup() {
        autoCloseable = openMocks(this);
        response = new ArtifactoryRestResponse<>();
        GetTreeNodeTabsAndActionsService.setSecurityService(securityService);
        GetTreeNodeTabsAndActionsService.setActionsPopulator(treeNodeActionsPopulator);
        GetTreeNodeTabsAndActionsService.setTabsPopulator(treeNodeTabsPopulator);
    }

    @AfterEach
    void tearDown() throws Exception {
        autoCloseable.close();
    }

    @Test
    void testNullTypeReturnsError() {
        final TabsAndActions tabsAndActions = mock(TabsAndActions.class);
        when(tabsAndActions.getType()).thenReturn(null);
        when(request.getImodel()).thenReturn(tabsAndActions);

        GetTreeNodeTabsAndActionsService.execute(request, response);

        verify(treeNodeActionsPopulator, times(0)).populateForRepository(tabsAndActions);
        verify(treeNodeTabsPopulator, times(0)).populateForRepository(tabsAndActions);
        assertEquals("TabsAndActions type is null, cannot populate tabs and actions.",
                response.getFeedbackMsg().getError());
        assertEquals(400, response.getResponseCode());
    }

    @Test
    void testAccessTrashcanWithAnonymousUser() {
        TabsAndActions tabsAndActions = mock(TabsAndActions.class);
        when(securityService.isAnonymous()).thenReturn(true);
        when(tabsAndActions.getType()).thenReturn("repository");
        when(request.getImodel()).thenReturn(tabsAndActions);
        when(tabsAndActions.getRepoType()).thenReturn("trash");
        GetTreeNodeTabsAndActionsService.execute(request, response);
        assertEquals(Collections.emptyList(), ((TabsAndActions) response.getIModel()).getActions());
    }

    @Test
    void testAccessTrashcanWithAuthorizedUser() {
        treeNodeActionsPopulator.setAuthService(authorizationService);
        treeNodeActionsPopulator.setSecurityService(securityService);
        TabsAndActions tabsAndActions = mock(TabsAndActions.class);
        when(securityService.isAnonymous()).thenReturn(false);
        when(authorizationService.canAccessTrashcan()).thenReturn(true);
        when(tabsAndActions.getType()).thenReturn("repository");
        when(request.getImodel()).thenReturn(tabsAndActions);
        when(tabsAndActions.getRepoType()).thenReturn("trash");
        GetTreeNodeTabsAndActionsService.execute(request, response);
        verify(treeNodeActionsPopulator, times(1)).populateForRepository(tabsAndActions);
        verify(treeNodeTabsPopulator, times(1)).populateForRepository(tabsAndActions);
    }
}