package org.artifactory.repo.vcs.git.release;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class GithubLatestRelease {
    private String url;
    private String assetsUrl;
    private String tarballUrl;
    private String zipballUrl;
    private int id;
    private String nodeId;
    private String tagName;
    private String name;
    private boolean prerelease;
}
