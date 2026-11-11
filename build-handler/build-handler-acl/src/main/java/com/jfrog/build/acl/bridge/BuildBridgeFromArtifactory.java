package com.jfrog.build.acl.bridge;

public interface BuildBridgeFromArtifactory {

    void init();

    void reload();

    void onRepoConfigReload();
}
