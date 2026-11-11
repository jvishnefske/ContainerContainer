package com.jfrog.build.model.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.Objects;

import static com.jfrog.build.acl.util.BuildConstants.BUILDINFO_REPO_KEY;
import static org.apache.commons.lang3.StringUtils.EMPTY;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ContinueBuildDto extends ContinuePageDto {

    String orderBy;
    String searchStr;
    String buildRepo;

    public ContinueBuildDto(
            Map<String, String[]> params,
            ProjectKeyToBuildRepoConverter projectKeyToBuildRepoConverter) {
        super(params);
        this.orderBy = getFirst(params, "orderBy");
        this.searchStr = getFirst(params, "searchStr");
        String projectKey = getFirst(params, "projectKey");

        if (StringUtils.isBlank(projectKey)) {
            buildRepo = BUILDINFO_REPO_KEY;
            return;
        }
        if (Objects.equals(projectKey, "all")) {
            buildRepo = EMPTY;
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
