package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.artifactory.descriptor.repo.RepoType;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.jfrog.commons.continues.ContinueIndexPage;

import java.util.List;


/**
 * @author Omri Ziv
 */
@Data
@EqualsAndHashCode(callSuper=false)
@AllArgsConstructor
@JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
public class TreeFilter extends ContinueIndexPage {

    List<RepoType> packageTypes;
    List<RepositoryType> repositoryTypes;
    List<String> repositoryKeys;
    SortBy sortBy = SortBy.REPO_TYPE;
    String byRepoKey;
    String projectKey;

    public TreeFilter() {
        super();
    }

    public TreeFilter(TreeFilter orig) {
        super(orig);
        this.packageTypes = orig.packageTypes;
        this.repositoryTypes = orig.repositoryTypes;
        this.repositoryKeys = orig.repositoryKeys;
        this.sortBy = orig.sortBy;
        this.byRepoKey = orig.byRepoKey;
        this.projectKey = orig.projectKey;
    }

    public TreeFilter(ContinueIndexPage orig) {
        super(orig);
    }

    public enum SortBy {
        REPO_TYPE, PACKAGE_TYPE, REPO_KEY
    }

}
