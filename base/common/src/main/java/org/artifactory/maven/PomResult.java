package org.artifactory.maven;

import org.apache.maven.model.Model;

public class PomResult {

    private final String pomAsString;
    private final Model pomAsModel;

    public PomResult(Model pomAsModel, String pomAsString) {
        this.pomAsString = pomAsString;
        this.pomAsModel = pomAsModel;
    }

    public Model getPomAsModel() {
        return pomAsModel;
    }

    public String getPomAsString() {
        return pomAsString;
    }
}
