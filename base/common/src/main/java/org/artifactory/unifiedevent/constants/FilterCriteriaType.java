package org.artifactory.unifiedevent.constants;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum FilterCriteriaType {
    REPO_KEY("repoKey"),
    REPO_TYPE("repoType"),
    PROJECT_KEY("projectKey");

    private String name;
}
