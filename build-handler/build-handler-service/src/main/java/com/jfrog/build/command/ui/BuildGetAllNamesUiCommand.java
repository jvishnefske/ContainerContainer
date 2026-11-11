package com.jfrog.build.command.ui;

import com.jfrog.build.service.BuildReadService;
import com.jfrog.build.service.BuildRepoService;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import static org.artifactory.exception.factory.ExceptionFactory.internalServerException;

@Slf4j
@RequiredArgsConstructor
public class BuildGetAllNamesUiCommand {

    private final BuildReadService readService;
    private final BuildRepoService repoService;

    public List<String> getAllBuildNames(@Nullable String buildRepo) {
        buildRepo = repoService.normaliseBuildRepoName(buildRepo);
        try {
            return readService.getBuildNames(buildRepo);
        } catch (Exception e) {
            log.debug("Failed to get build names", e);
            throw internalServerException("Failed to retrieve all build names from build repo %s. "
                    + "Reason: %s", buildRepo, e.getMessage());
        }
    }
}
