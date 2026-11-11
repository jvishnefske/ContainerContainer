package org.artifactory.ui.rest.model.artifacts.search.packagesearch.search;

import java.util.List;

/**
 * @author Oleksandr Legenkiy
 */
public class AqlUiSearchControls {

    private List<AqlUISearchModel> searchModels;
    private boolean searchShouldBeSkipped;
    private boolean limitSearchResultsForProjects;

    public AqlUiSearchControls(List<AqlUISearchModel> searchModels, boolean searchShouldBeSkipped,
            boolean limitSearchResultsForProjects) {
        this.searchModels = searchModels;
        this.searchShouldBeSkipped = searchShouldBeSkipped;
        this.limitSearchResultsForProjects = limitSearchResultsForProjects;
    }

    public List<AqlUISearchModel> getSearchModels() {
        return searchModels;
    }

    public void setSearchModels(List<AqlUISearchModel> searchModels) {
        this.searchModels = searchModels;
    }

    public boolean isSearchShouldBeSkipped() {
        return searchShouldBeSkipped;
    }

    public void setSearchShouldBeSkipped(boolean searchShouldBeSkipped) {
        this.searchShouldBeSkipped = searchShouldBeSkipped;
    }

    public boolean isLimitSearchResultsForProjects() {
        return limitSearchResultsForProjects;
    }

    public void setLimitSearchResultsForProjects(boolean limitSearchResultsForProjects) {
        this.limitSearchResultsForProjects = limitSearchResultsForProjects;
    }
}
