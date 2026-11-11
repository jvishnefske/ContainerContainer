package com.jfrog.build.command;

import com.jfrog.build.acl.model.BuildWrapper;
import com.jfrog.build.service.BuildCreationService;
import com.jfrog.build.service.BuildReadService;
import com.jfrog.build.service.BuildRepoService;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jfrog.build.api.Build;
import org.jfrog.build.api.Module;

import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.artifactory.exception.factory.ExceptionFactory.notFoundException;

@Slf4j
@RequiredArgsConstructor
public class BuildAddModulesCommand {

    private final BuildRepoService repoService;
    private final BuildReadService readService;
    private final BuildCreationService creationService;

    public void addModules(
            @NonNull List<Module> newModules,
            @NonNull String buildName,
            @NonNull String buildNumber,
            @Nullable String buildStarted,
            @Nullable String buildRepo,
            @Nullable String projectKey) {
        log.info("Adding new modules to the build {}:{}", buildName, buildNumber);
        buildRepo = repoService.getPreferredBuildRepo(buildRepo, projectKey);
        BuildWrapper buildWrapper = readService.getBuild(buildName, buildNumber, buildStarted, buildRepo);
        if (isNull(buildWrapper) || !buildWrapper.isBuildExist()) {
            throw notFoundException("The build %s:%s is not found", buildName, buildNumber);
        }
        Build build = buildWrapper.getBuild();
        List<Module> existingModules = build.getModules();
        List<Module> combinedModules = new ArrayList<>();
        if (nonNull(existingModules)) {
            combinedModules.addAll(existingModules);
        }
        combinedModules.addAll(newModules);
        build.setModules(combinedModules);
        creationService.updateBuild(buildWrapper);
    }
}

