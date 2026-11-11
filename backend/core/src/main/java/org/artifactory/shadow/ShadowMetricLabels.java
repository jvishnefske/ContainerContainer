package org.artifactory.shadow;

public enum ShadowMetricLabels {
    PACKAGE_TYPE("packageType"), RESULT;
    final String displayName;

    ShadowMetricLabels(String displayName) {
        this.displayName = displayName;
    }

    ShadowMetricLabels() {
        this.displayName = this.name().toLowerCase();
    }

    public enum ShadowMetricResultLabelType {
        ERROR, STATUS("status-diff"), HEADERS("headers-diff"), MATCH;
        final String displayName;

        ShadowMetricResultLabelType(String displayName) {
            this.displayName = displayName;
        }

        ShadowMetricResultLabelType() {
            this.displayName = this.name().toLowerCase();
        }
    }
}
