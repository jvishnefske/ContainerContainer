package org.artifactory.ui.rest.model.artifacts.browse.generic;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class NativeBrowserChild {

    private String name;
    private long lastModified;
    private long size;
    private boolean folder;
    private boolean remote;
}
