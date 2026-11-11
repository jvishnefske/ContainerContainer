package org.artifactory.api.artifact;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * @author Shashank Taliwal
 */
@JsonTypeName("alpine")
@Getter
@Setter
@NoArgsConstructor
public class AlpineArtifactInfo implements UnitInfo {

    @Getter
    private static final String artifactType = "alpine";
    private String path;

    public AlpineArtifactInfo(String path) {
        // Using this StringBuilder to avoid Sonar issue
        this.path = new StringBuilder("/").append(path).toString();
    }

    @Override
    public boolean isMavenArtifact() {
        return false;
    }

    @Override
    public boolean isValid() {
        return true;
    }
}
