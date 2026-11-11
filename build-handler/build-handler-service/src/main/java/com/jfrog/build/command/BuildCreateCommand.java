package com.jfrog.build.command;

import com.jfrog.build.service.BuildCreationService;
import com.jfrog.build.service.BuildRepoService;
import com.jfrog.build.service.BuildSysConfigService;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jfrog.build.api.Artifact;
import org.jfrog.build.api.Build;
import org.jfrog.build.api.Module;

import java.util.HashMap;
import java.util.Map;

import static java.lang.String.format;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;
import static org.artifactory.exception.factory.ExceptionFactory.badRequestException;

@Slf4j
@RequiredArgsConstructor
public class BuildCreateCommand {

    private final BuildRepoService repoService;
    private final BuildCreationService creationService;
    private final BuildSysConfigService sysConfigService;

    @NonNull
    public String createBuild(
            @NonNull Build build,
            @Nullable String buildRepoKey,
            @Nullable String projectKey) {
        if (sysConfigService.isBuildBlockDuplicateEntries()) {
            validateNoDuplicatedBuildArtifacts(build);
        }
        buildRepoKey = repoService.getPreferredBuildRepo(buildRepoKey, projectKey);
        return creationService.createBuild(build, buildRepoKey);
    }

    private void validateNoDuplicatedBuildArtifacts(@NonNull Build build) {
        Map<Artifact, Integer> duplicates = findBuildArtifactDuplicates(build);
        if (!duplicates.isEmpty()) {
            String message = "Artifact(s) duplicates: " + formatDuplicatedArtifacts(duplicates);
            log.warn(message);
            throw badRequestException(message);
        }
    }

    @NonNull
    private Map<Artifact, Integer> findBuildArtifactDuplicates(@NonNull Build build) {
        return isNull(build.getModules()) ? Map.of()
                : countBuildArtifacts(build).entrySet().stream()
                  .filter(entry -> entry.getValue() > 1)
                  .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @NonNull
    private Map<Artifact, Integer> countBuildArtifacts(@NonNull Build build) {
        Map<Artifact, Integer> countedArtifacts = new HashMap<>();
        for (Module module : build.getModules()) {
            if (nonNull(module.getArtifacts())) {
                for (Artifact artifact : module.getArtifacts()) {
                    countedArtifacts.merge(artifact, 1, Integer::sum);
                }
            }
        }
        return countedArtifacts;
    }

    @NonNull
    private String formatDuplicatedArtifacts(@NonNull Map<Artifact, Integer> duplicates) {
        return duplicates.entrySet().stream()
                .map(this::formatDuplicatedArtifact)
                .collect(joining(", "));
    }

    @NonNull
    private String formatDuplicatedArtifact(@NonNull Map.Entry<Artifact, Integer> duplicate) {
        Artifact artifact = duplicate.getKey();
        return format("%s(%s) : %s time(s)", artifact.getName(), artifact.getSha1(), duplicate.getValue());
    }
}
