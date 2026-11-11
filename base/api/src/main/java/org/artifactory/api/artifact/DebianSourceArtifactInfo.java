package org.artifactory.api.artifact;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * @author Shashank Taliwal
 */
@Getter
@NoArgsConstructor
@JsonTypeName("debianSource")
public class DebianSourceArtifactInfo implements UnitInfo {

    private static final String artifactType = "debianSource";
    private String path;

    public DebianSourceArtifactInfo(String path) {
        this.path = path;
    }

    @Override
    public boolean isMavenArtifact() {
        return false;
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public void setPath(String path) {
        this.path = path;
    }

    public String getArtifactType() {
        return artifactType;
    }
}
