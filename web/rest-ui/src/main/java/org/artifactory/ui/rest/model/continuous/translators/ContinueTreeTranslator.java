package org.artifactory.ui.rest.model.continuous.translators;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.projects.ProjectsService;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.TreeFilter;
import org.artifactory.ui.rest.model.continuous.dtos.ContinueTreeDto;
import org.artifactory.util.CollectionUtils;
import org.jfrog.commons.continues.ContinueIndexPage;

import java.util.Optional;

public class ContinueTreeTranslator {


    public static TreeFilter toContinueTreeFilter(ContinueTreeDto continueTreeDto) {
        ContinueIndexPage continueIndexPage = ContinuePageTranslator.toContinueIndex(continueTreeDto);
        TreeFilter treeFilter = new TreeFilter(continueIndexPage);
        if (StringUtils.isNotBlank(continueTreeDto.getByRepoKey())) {
            treeFilter.setByRepoKey(continueTreeDto.getByRepoKey());
        }
        if (CollectionUtils.notNullOrEmpty(continueTreeDto.getPackageTypes())) {
            treeFilter.setPackageTypes(continueTreeDto.getPackageTypes());
        }
        if (CollectionUtils.notNullOrEmpty(continueTreeDto.getRepositoryKeys())) {
            treeFilter.setRepositoryKeys(continueTreeDto.getRepositoryKeys());
        }
        if (CollectionUtils.notNullOrEmpty(continueTreeDto.getRepositoryTypes())) {
            treeFilter.setRepositoryTypes(continueTreeDto.getRepositoryTypes());
        }
        if (continueTreeDto.getSortBy() != null) {
            treeFilter.setSortBy(continueTreeDto.getSortBy());
        }
        treeFilter.setProjectKey(Optional
                .ofNullable(continueTreeDto.getProjectKey())
                .filter(StringUtils::isNotEmpty)
                .orElse(ProjectsService.DEFAULT_PROJECT));
        return treeFilter;
    }

}
