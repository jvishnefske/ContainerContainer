package org.artifactory.ui.rest.model.admin.configuration.repository.virtual;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.artifactory.ph.RepoListType;

import java.util.List;
import java.util.Map;

/**
 * Request model for collectively handling virtualConfigModel and repoLists.
 *
 * @author Aishwarya Singh
 */
@Data
@NoArgsConstructor
public class VirtualAllowedReposModel {
    private VirtualRepositoryConfigModel virtualConfigModel;
    private Map<RepoListType, List<String>> repoLists;
}
