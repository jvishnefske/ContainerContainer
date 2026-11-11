package org.artifactory.ui.rest.service.artifacts.search.syntax;

import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.actions.TabOrAction;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.actions.TabsAndActions;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.actions.TreeNodeActionsPopulator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

/**
 * @author Igor Usenko
 */
@ExtendWith(MockitoExtension.class)
class ItemActionsPopulatorImplTest {

    @Mock
    private TreeNodeActionsPopulator actionsPopulator;

    @InjectMocks
    private ItemActionsPopulatorImpl actionsPopulatorImpl;

    @Test
    void populateForRepository() {
        doAnswer(invocation -> {
            TabsAndActions tabsAndActions = invocation.getArgument(0);
            tabsAndActions.setActions(List.of(new TabOrAction("Refresh"), new TabOrAction("Download")));
            return null;
        }).when(actionsPopulator).populateForRepository(any(TabsAndActions.class));

        List<String> result = actionsPopulatorImpl.populateForRepository("local", "maven", "my-repo/path");

        verify(actionsPopulator).populateForRepository(any(TabsAndActions.class));
        assertEquals(List.of("Refresh", "Download"), result);
    }

    @Test
    void populateForFolder() {
        doAnswer(invocation -> {
            TabsAndActions tabsAndActions = invocation.getArgument(0);
            tabsAndActions.setActions(List.of(new TabOrAction("Copy"), new TabOrAction("Move")));
            return null;
        }).when(actionsPopulator).populateForFolder(any(TabsAndActions.class));

        List<String> result = actionsPopulatorImpl.populateForFolder("local", "docker", "docker-repo/library/nginx");

        verify(actionsPopulator).populateForFolder(any(TabsAndActions.class));
        assertEquals(List.of("Copy", "Move"), result);
    }

    @Test
    void populateForFile() {
        doAnswer(invocation -> {
            TabsAndActions tabsAndActions = invocation.getArgument(0);
            tabsAndActions.setActions(List.of(new TabOrAction("Download"), new TabOrAction("Delete")));
            return null;
        }).when(actionsPopulator).populateForFile(any(TabsAndActions.class));

        List<String> result = actionsPopulatorImpl.populateForFile("local", "pypi", "pypi-repo/package/dist.tar.gz");

        verify(actionsPopulator).populateForFile(any(TabsAndActions.class));
        assertEquals(List.of("Download", "Delete"), result);
    }

}
