package org.artifactory.rest.resource.localgenerated;

import java.util.List;
public record LocalGeneratedPathsModel(String repoKey, List<String> paths) {

}
