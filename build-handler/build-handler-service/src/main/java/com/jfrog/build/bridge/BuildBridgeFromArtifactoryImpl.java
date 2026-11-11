package com.jfrog.build.bridge;

import com.jfrog.build.acl.bridge.BuildBridgeFromArtifactory;
import com.jfrog.build.service.BuildImportService;
import com.jfrog.build.service.BuildRepoService;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class BuildBridgeFromArtifactoryImpl implements BuildBridgeFromArtifactory {

    private final BuildRepoService repoService;
    private final BuildImportService buildImportService;

    @Override
    public void init() {
        repoService.refreshAllBuildsRepositories();
        buildImportService.reload();
    }

    @Override
    public void reload() {
        repoService.clearProjectRepoMapping();
        buildImportService.reload();
    }

    @Override
    public void onRepoConfigReload() {
        repoService.refreshAllBuildsRepositories();
    }
}
