package org.artifactory.ui.rest.model.artifacts.browse.generic;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class NativeBrowserModel {

    private String repo;
    private String path;
    private String footer;
    private boolean root;
    private boolean folder;
    private List<NativeBrowserChild> children;
}
