package org.artifactory.ui.rest.service.artifacts.search.syntax;

import lombok.RequiredArgsConstructor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.actions.TabOrAction;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.actions.TabsAndActions;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.actions.TreeNodeActionsPopulator;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * @author Igor Usenko
 */
@Component
@RequiredArgsConstructor
public class ItemActionsPopulatorImpl implements ItemActionsPopulator {

    private final TreeNodeActionsPopulator actionsPopulator;

    @Override
    public List<String> populateForRepository(String repoType, String repoPkgType, String repoPath) {
        return populate(repoType, repoPkgType, repoPath, actionsPopulator::populateForRepository);
    }

    @Override
    public List<String> populateForFolder(String repoType, String repoPkgType, String repoPath) {
        return populate(repoType, repoPkgType, repoPath, actionsPopulator::populateForFolder);
    }

    @Override
    public List<String> populateForFile(String repoType, String repoPkgType, String repoPath) {
        return populate(repoType, repoPkgType, repoPath, actionsPopulator::populateForFile);
    }

    private List<String> populate(String repoType, String repoPkgType, String repoPath,
                                  Consumer<TabsAndActions> populationStrategy) {
        TabsAndActions tabsAndActions = new TabsAndActions();
        tabsAndActions.setRepoType(repoType);
        tabsAndActions.setRepoPkgType(RepoType.fromString(repoPkgType));
        tabsAndActions.setRepoPath(repoPath);
        populationStrategy.accept(tabsAndActions);
        return tabsAndActions.getActions().stream().map(TabOrAction::getName).toList();
    }
}
