package com.jfrog.build.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jfrog.build.acl.bridge.BuildBridgeToArtifactory;
import com.jfrog.build.acl.model.ArtifactoryBuildArtifact;
import com.jfrog.build.acl.model.BuildFileInfo;
import com.jfrog.build.acl.model.BuildRepoDescriptor;
import com.jfrog.build.acl.model.BuildRepoPath;
import com.jfrog.build.acl.model.BuildRun;
import com.jfrog.build.acl.model.BuildWrapper;
import com.jfrog.build.model.api.PromotionResult;
import com.jfrog.build.tx.BuildTxRunner;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.common.status.StatusEntry;
import org.jfrog.build.api.Build;
import org.jfrog.build.api.BuildFileBean;
import org.jfrog.build.api.Dependency;
import org.jfrog.build.api.builder.PromotionStatusBuilder;
import org.jfrog.build.api.release.Promotion;
import org.jfrog.build.api.release.PromotionStatus;
import org.joda.time.format.ISODateTimeFormat;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static com.jfrog.build.acl.model.BuildAccessLogType.BUILD_PROMOTE;
import static com.jfrog.build.acl.util.BuildInfoUtils.generateBuildJsonRepoPath;
import static java.lang.String.format;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.artifactory.exception.factory.ExceptionFactory.badRequestException;
import static org.artifactory.exception.factory.ExceptionFactory.contentTooLargeException;
import static org.artifactory.exception.factory.ExceptionFactory.notFoundException;

@Slf4j
@RequiredArgsConstructor
public class BuildPromotionService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().setDefaultPropertyInclusion(NON_NULL);

    private final BuildBridgeToArtifactory bridgeToArtifactory;

    private final BuildTxRunner txRunner;
    private final BuildArtifactService artifactService;
    private final BuildCreationService creationService;
    private final BuildDatabaseService databaseService;
    private final BuildDeletionService deletionService;
    private final BuildDependencyService dependencyService;
    private final BuildPermissionService permissionService;
    private final BuildReadService readService;
    private final BuildSysConfigService sysConfigService;

    public PromotionResult promoteBuild(
            @NonNull BuildRun build,
            @NonNull Promotion promotion) {
        sysConfigService.assertNotArtifactoryColdInstance();
        permissionService.assertUploadPermissions(
                build.getName(), build.getNumber(), build.getStarted(), build.getBuildRepo());

        handleVirtualRepo(promotion);
        BuildWrapper buildWrapper;
        try {
            buildWrapper = getBuild(build);
            if (buildWrapper == null || !buildWrapper.isBuildExist()) {
                throw new IllegalArgumentException("Build not found");
            }
        } catch (IllegalArgumentException e) {
            throw notFoundException("Unable to find build '%s' #%s.", e, build.getName(), build.getNumber());
        }
        PromotionResult promotionResult = new PromotionResult();

        String targetRepo = promotion.getTargetRepo();
        MutableStatusHolder status = bridgeToArtifactory.newStatusHolder();

        Set<BuildFileInfo> itemsToMove = null;

        if (isBlank(targetRepo)) {
            status.status("Skipping build item relocation: no target repository selected.", log);
        } else {
            assertRepoExists(targetRepo);
            itemsToMove = collectItems(buildWrapper, promotion, status);
            promoteBuildItems(promotion, status, toRepoPath(itemsToMove));
        }
        // A list of properties to attach to the build's artifacts (regardless if "targetRepo" is used).
        Map<String, Collection<String>> properties = promotion.getProperties();
        if (nonNull(properties) && !properties.isEmpty()) {
            Set<BuildRepoPath> modifiedItems = collectModifiedItems(
                    promotion, buildWrapper, targetRepo, status, toRepoPath(itemsToMove));
            if (!modifiedItems.isEmpty()) {
                tagBuildItemsWithProperties(
                        modifiedItems, properties, promotion.isFailFast(), promotion.isDryRun(), status);
            }
        }

        performPromotionIfNeeded(status, buildWrapper, promotion);
        appendMessages(promotionResult, status);
        return promotionResult;
    }

    private void tagBuildItemsWithProperties(
            Set<BuildRepoPath> repoPaths,
            Map<String, Collection<String>> properties,
            boolean failFast,
            boolean dryRun,
            MutableStatusHolder status) {
        for (BuildRepoPath repoPath : repoPaths) {
            if (permissionService.canAnnotate(repoPath.toString())) {
                if (!dryRun) {
                    bridgeToArtifactory.addProperties(repoPath.toString(), properties);
                }
            } else {
                status.warn("User doesn't have permissions to annotate '" + repoPath + "'", log);
                if (failFast) {
                    return;
                }
            }
        }
    }

    private BuildWrapper getBuild(BuildRun buildRun) {
        BuildWrapper buildWrapper = readService.getBuildInternally(buildRun);
        if (buildWrapper != null && buildWrapper.isBuildExist()) {
            return buildWrapper;
        }
        return null;
    }

    private void assertRepoExists(String targetRepoKey) {
        Optional<BuildRepoDescriptor> repoDesc = bridgeToArtifactory.getRepoDescriptor(targetRepoKey);
        if (repoDesc.isEmpty() || !repoDesc.get().isLocalOrFederated() && !repoDesc.get().isCache()) {
            throw notFoundException("Cannot find target repository by the key '%s'", targetRepoKey);
        }
    }

    private Set<BuildFileInfo> collectItems(
            BuildWrapper buildWrapper,
            Promotion promotion,
            MutableStatusHolder status) {
        Build build = buildWrapper.getBuild();
        Set<BuildFileInfo> itemsToMove = Sets.newHashSet();
        if (isNull(build) || isNull(build.getModules())) {
            return itemsToMove;
        }
        //Artifacts should be collected
        if (promotion.isArtifacts()) {
            Set<ArtifactoryBuildArtifact> buildArtifactsInfo =
                    getBuildArtifactsAndVerifyInfo(buildWrapper, promotion, status);
            for (ArtifactoryBuildArtifact artifact : buildArtifactsInfo) {
                itemsToMove.add(artifact.getFileInfo());
            }
        }
        //Build dependencies should be collected
        if (promotion.isDependencies()) {
            Map<Dependency, BuildFileInfo> buildDependenciesInfo =
                    dependencyService.getBuildDependenciesFileInfos(buildWrapper);
            verifyAllDependencyInfosExistInMap(build, status, buildDependenciesInfo);
            for (Map.Entry<Dependency, BuildFileInfo> entry : buildDependenciesInfo.entrySet()) {
                Dependency dependency = entry.getKey();
                if (dependency == null) {
                    continue;
                }
                Set<String> dependencyScopes = dependency.getScopes();
                //Scopes of dependencies to collect
                if (CollectionUtils.isEmpty(
                        promotion.getScopes()) || (dependencyScopes != null &&
                        CollectionUtils.containsAny(dependencyScopes, promotion.getScopes()))) {
                    itemsToMove.add(entry.getValue());
                }
            }
        }
        return itemsToMove;
    }

    private Set<ArtifactoryBuildArtifact> getBuildArtifactsAndVerifyInfo(
            BuildWrapper buildWrapper,
            Promotion promotion,
            MutableStatusHolder status) {
        ArrayList<String> sourceRepositories = Lists.newArrayList();
        if (promotion.getSourceRepo() != null) {
            sourceRepositories.add(promotion.getSourceRepo());
        }
        Set<ArtifactoryBuildArtifact> buildArtifactsInfo = artifactService.getBuildArtifactsFileInfos(
                buildWrapper, sourceRepositories, null, false);
        verifyAllArtifactInfosExistInSet(buildWrapper.getBuild(), status, buildArtifactsInfo);
        if (status.isWarning() && promotion.isFailFast()) {
            throw badRequestException(
                    "Unable to find artifacts of build '%s' #%s from %s repo: aborting promotion.",
                    buildWrapper.getName(), buildWrapper.getNumber(), buildWrapper.getBuildRepo());
        }
        return buildArtifactsInfo;
    }

    private void handleVirtualRepo(Promotion promotion) {
        if (nonNull(promotion)) {
            String promotionTargetRepo = promotion.getTargetRepo();
            bridgeToArtifactory.getDefaultDeploymentRepoKey(promotionTargetRepo)
                    .ifPresent(promotion::setTargetRepo);
        }
    }

    private Set<BuildRepoPath> collectModifiedItems(
            Promotion promotion,
            BuildWrapper build,
            String targetRepo,
            MutableStatusHolder statusHolder,
            Set<BuildRepoPath> itemsToMove) {
        //Collect artifacts only from the target repository
        Set<BuildRepoPath> modifiedItems = Sets.newHashSet();
        if (!isBlank(targetRepo)) {
            for (BuildRepoPath item : itemsToMove) {
                modifiedItems.add(new BuildRepoPath(targetRepo, item.getRelativePath()));
            }
        } else {
            //In case the target repository is not defined, collect the items form the source.
            Set<BuildFileInfo> result = collectItems(build, promotion, statusHolder);
            modifiedItems = toRepoPath(result);
        }
        return modifiedItems;
    }

    private void performPromotionIfNeeded(
            MutableStatusHolder statusHolder,
            BuildWrapper buildWrapper,
            Promotion promotion) {
        Build build = buildWrapper.getBuild();
        String status = promotion.getStatus();

        if (promotion.isFailFast() && (statusHolder.isError() || statusHolder.isWarning())) {
            statusHolder.status("Skipping promotion status update: item promotion was completed with errors " +
                    "and warnings.", log);
            return;
        }

        if (isBlank(status)) {
            statusHolder.status("Skipping promotion status update: no status received.", log);
            return;
        }

        PromotionStatusBuilder statusBuilder = new PromotionStatusBuilder(status).
                user(bridgeToArtifactory.getCurrentUsername()).repository(promotion.getTargetRepo()).
                comment(promotion.getComment()).ciUser(promotion.getCiUser());

        String timestamp = promotion.getTimestamp();

        if (StringUtils.isNotBlank(timestamp)) {
            try {
                ISODateTimeFormat.dateTime().parseMillis(timestamp);
            } catch (Exception e) {
                statusHolder.error("Skipping promotion status update: invalid\\unparsable timestamp " + timestamp +
                        ".", null, log);
                return;
            }
            statusBuilder.timestamp(timestamp);
        } else {
            statusBuilder.timestampDate(new Date());
        }

        if (promotion.isDryRun()) {
            return;
        }
        txRunner.runTx(() -> addPromotionStatus(buildWrapper, statusBuilder.build()));
        String withErrors = (statusHolder.isError() || statusHolder.isWarning()) ? "with errors" : "successfully";
        try {
            String jsonPromotion = OBJECT_MAPPER.writeValueAsString(promotion);
            log.info("Promotion completed {} for build name '{}' and number '{}' under repo '{}' with status of '{}'," +
                            " promotion details: {}",
                    withErrors,
                    build.getName(), build.getNumber(), buildWrapper.getBuildRepo(), status, jsonPromotion);
        } catch (JsonProcessingException e) {
            log.warn("Could not parse promotion details: {}", promotion, e);
            log.info("Promotion completed {} for build name '{}' and number '{}' under repo '{}' with status of '{}'",
                    withErrors,
                    build.getName(), build.getNumber(), buildWrapper.getBuildRepo(), status);
        } finally {
            bridgeToArtifactory.logAccessEvent(BUILD_PROMOTE, build.getName());
            bridgeToArtifactory.runBuildPromotionInterceptor(promotion, build, buildWrapper.getBuildRepo());
        }
    }

    private void promoteBuildItems(
            Promotion promotion,
            MutableStatusHolder status,
            Set<BuildRepoPath> itemsToMove) {
        String targetRepo = promotion.getTargetRepo();
        boolean dryRun = promotion.isDryRun();
        boolean failFast = promotion.isFailFast();
        if (isNotEmpty(itemsToMove)) {
            Set<String> repoPaths = itemsToMove.stream().map(BuildRepoPath::toString).collect(toSet());
            if (promotion.isCopy()) {
                try {
                    bridgeToArtifactory.copy(repoPaths, targetRepo, status, failFast, dryRun);
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                    status.error("Error occurred while copying: " + e.getMessage(), e, log);
                }
            } else {
                try {
                    bridgeToArtifactory.move(repoPaths, targetRepo, status, failFast, dryRun);
                } catch (Exception e) {
                    status.error("Error occurred while moving: " + e.getMessage(), e, log);
                }
            }
        }
    }

    private void verifyAllArtifactInfosExistInSet(
            Build build,
            MutableStatusHolder statusHolder,
            Set<ArtifactoryBuildArtifact> buildArtifactsInfos) {
        String artifactNames = buildArtifactsInfos.stream()
                .filter(artifact -> artifact.getFileInfo() == null)
                .map(artifact -> artifact.getArtifact().getName())
                .collect(Collectors.joining(", "));
        if (isNotBlank(artifactNames)) {
            String message = format("Unable to find the following artifacts of build '%s' #%s: %s",
                    build.getName(), build.getNumber(), artifactNames);
            statusHolder.warn(message, log);
        }
        buildArtifactsInfos.removeIf(artifact -> artifact.getFileInfo() == null);
    }

    /**
     * Verifies all dependencies from the build exist in the map and writes appropriate entries to the StatusHolder
     * based on the chosen log level.
     * NOTE: Relies on missing dependency to have a null mapping.
     *
     * @param build                 Build to verify
     * @param statusHolder          StatusHolder that entries will be written into
     * @param buildDependenciesInfo Mapping of Dependencies to FileInfos
     */
    private void verifyAllDependencyInfosExistInMap(
            Build build,
            MutableStatusHolder statusHolder,
            Map<Dependency, BuildFileInfo> buildDependenciesInfo) {
        List<BuildFileBean> keysToRemove = Lists.newArrayList();
        for (Map.Entry<Dependency, BuildFileInfo> entry : buildDependenciesInfo.entrySet()) {
            if (entry.getValue() == null) {
                String message = "Unable to find dependency '" + entry.getKey().getId() + "' of build '"
                        + build.getName() + "' #" + build.getNumber();
                keysToRemove.add(entry.getKey());
                statusHolder.warn(message, log);
            }
        }
        keysToRemove.stream()
                .filter(Objects::nonNull)
                .forEach(buildDependenciesInfo::remove);
    }

    private void appendMessages(
            PromotionResult promotionResult,
            MutableStatusHolder statusHolder) {
        for (StatusEntry statusEntry : statusHolder.getEntries()) {
            promotionResult.messages.add(new PromotionResult.PromotionResultMessages(
                    statusEntry.getLevel().name(), statusEntry.getMessage()));
        }
    }

    private Set<BuildRepoPath> toRepoPath(Set<BuildFileInfo> artifacts) {
        return ofNullable(artifacts)
                .orElse(Sets.newHashSet())
                .stream()
                .filter(Objects::nonNull)
                .map(BuildFileInfo::getRepoPath)
                .filter(Objects::nonNull)
                .collect(toSet());
    }

    public void addPromotionStatus(
            BuildWrapper buildWrapper,
            PromotionStatus promotion) {
        sysConfigService.assertNotArtifactoryColdInstance();
        Build build = buildWrapper.getBuild();
        String name = build.getName();
        String number = build.getNumber();
        String started = build.getStarted();
        String buildRepo = buildWrapper.getBuildRepo();
        String currentUser = bridgeToArtifactory.getCurrentUsername();
        if (sysConfigService.isEvidenceEnabled() && readService.isImmutableBuild(name, number, started)) {
            databaseService.addPromotionStatus(buildWrapper, promotion, currentUser);
            bridgeToArtifactory.createEvidenceForBuildPromotion(buildRepo, name, number, started, promotion);
            return;
        }
        permissionService.assertUploadPermissions(name, number, started, buildRepo);
        log.debug("Overwriting the .json content of '{}:{}' with new build model including promotion", name, number);
        build.addStatus(promotion);

        BuildRepoPath repoPath = generateBuildJsonRepoPath(build, buildWrapper.getBuildRepo());
        BuildFileInfo fileInfo = bridgeToArtifactory.getFileInfo(repoPath.toString());
        if (!bridgeToArtifactory.assertStorageQuota(buildRepo, fileInfo.getSize())) {
            String message = format("Promotion status update for build %s:%s:%s "
                            + "failed due to storage quota exceeded; build info size: %d",
                    build.getName(), build.getNumber(), build.getStarted(), fileInfo.getSize());
            log.warn(message);
            throw contentTooLargeException(message);
        }
        deletePreviousBuildAsSystem(build);
        creationService.createBuild(build, buildRepo);
    }

    private void deletePreviousBuildAsSystem(Build build) {
        MutableStatusHolder status = bridgeToArtifactory.newStatusHolder();
        bridgeToArtifactory.doAsSystem(() -> {
            BuildRun buildRun = readService.getBuildRunInternally(build.getName(), build.getNumber(), build.getStarted());
            log.debug("Invoking Build info deletion");
            txRunner.runTxNew(() -> deletionService.deleteBuild(buildRun, status, false));
        });
        StatusEntry lastError = status.getLastError();
        if (nonNull(lastError)) {
            log.warn("Couldn't delete build as part of promotion: {}.", lastError.getMessage());
        }
    }
}
