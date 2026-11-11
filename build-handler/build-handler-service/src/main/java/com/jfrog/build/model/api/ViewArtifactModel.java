package com.jfrog.build.model.api;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ViewArtifactModel {

    private String fileContent;
}
