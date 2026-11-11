package org.artifactory.ui.rest.model.continuous.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.artifactory.repo.system.SystemRepos.BUILDINFO_REPO_KEY;
import static org.artifactory.ui.utils.RequestUtils.PROJECT_KEY_PARAM;
import static org.artifactory.ui.utils.RequestUtils.PROJECT_VALUE_ALL_PARAM;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ContinueBuildDto extends ContinuePageDto {

    String orderBy;
    String searchStr;
    String buildRepo;

    public ContinueBuildDto(ArtifactoryRestRequest request,
            ProjectKeyToBuildRepoConverter projectKeyToBuildRepoConverter) {
        super(request);
        Map<String, List<String>> params = request.getUriInfo().getQueryParameters();
        this.orderBy = getFirst(params, "orderBy");
        this.searchStr = getFirst(params, "searchStr");
        String projectKey = getFirst(params, PROJECT_KEY_PARAM);

        if (StringUtils.isBlank(projectKey)) {
            buildRepo = BUILDINFO_REPO_KEY;
            return;
        }
        if (Objects.equals(projectKey, PROJECT_VALUE_ALL_PARAM)) {
            buildRepo = StringUtils.EMPTY;
            return;
        }
        buildRepo = projectKeyToBuildRepoConverter.toBuildRepo(projectKey);
    }

    public ContinueBuildDto(ContinueBuildDto other) {
        super(other);
        this.orderBy = other.orderBy;
        this.searchStr = other.searchStr;
        this.buildRepo = other.buildRepo;
    }

    @FunctionalInterface
    public interface ProjectKeyToBuildRepoConverter {
        String toBuildRepo(String projectKey);
    }
}
