package com.jfrog.build.model.api;

import com.jfrog.build.acl.model.BuildCoordinate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DeleteBuildsModel {

    private List<BuildCoordinate> buildsCoordinates = new ArrayList<>();

    public void addBuildCoordinate(
            String buildName,
            String buildNumber,
            Long buildStarted,
            String buildRepo) {
        buildsCoordinates.add(new BuildCoordinate(buildName, buildNumber, buildStarted, buildRepo));
    }

    public void addBuildCoordinate(
            String buildName,
            String buildRepo) {
        buildsCoordinates.add(new BuildCoordinate(buildName, buildRepo));
    }
}
