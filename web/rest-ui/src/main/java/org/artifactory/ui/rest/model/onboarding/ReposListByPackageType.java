package org.artifactory.ui.rest.model.onboarding;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.artifactory.repo.RepoDetailsType;

import java.util.List;
import java.util.Map;

/**
 * A UI purpose model that holds all the system repositories names by their type (LOCAL, FEDERATED, REMOTE, VIRTUAL)
 * The hideNestedReposSupported flag informing the UI if they can filter out on demand real repositories that are
 * already aggregated in Virtual repositories or not (We would like to avoid this filter on large scale environments).
 *
 * @author BarakH
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReposListByPackageType {

    private boolean hideNestedReposSupported;
    private Map<RepoDetailsType, List<String>> repoTypeToReposKeysMap;
}
