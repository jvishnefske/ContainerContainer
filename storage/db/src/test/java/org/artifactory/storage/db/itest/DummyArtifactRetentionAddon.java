package org.artifactory.storage.db.itest;

import org.artifactory.addon.retention.ArtifactRetentionAddon;

public class DummyArtifactRetentionAddon implements ArtifactRetentionAddon {

    @Override
    public boolean isDefault() {
        return false;
    }
}