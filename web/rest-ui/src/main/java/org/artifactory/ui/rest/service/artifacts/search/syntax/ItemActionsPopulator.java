package org.artifactory.ui.rest.service.artifacts.search.syntax;

import java.util.List;

/**
 * @author Igor Usenko
 */
public interface ItemActionsPopulator {

    List<String> populateForRepository(String repoType, String repoPkgType, String repoPath);

    List<String> populateForFolder(String repoType, String repoPkgType, String repoPath);

    List<String> populateForFile(String repoType, String repoPkgType, String repoPath);

}
