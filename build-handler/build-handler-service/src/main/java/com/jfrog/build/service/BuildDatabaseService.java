package com.jfrog.build.service;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.jfrog.build.acl.bridge.BuildBridgeToArtifactory;
import com.jfrog.build.acl.model.BuildChecksumType;
import com.jfrog.build.acl.model.BuildFileInfo;
import com.jfrog.build.acl.model.BuildId;
import com.jfrog.build.entity.BuildEntityRecord;
import com.jfrog.build.model.BuildIdImpl;
import com.jfrog.build.acl.model.BuildNameAndRepo;
import com.jfrog.build.acl.model.BuildNode;
import com.jfrog.build.model.BuildNodeRecord;
import com.jfrog.build.model.BuildNumberRecord;
import com.jfrog.build.model.BuildProps;
import com.jfrog.build.acl.model.BuildRun;
import com.jfrog.build.acl.model.BuildRunImpl;
import com.jfrog.build.acl.model.BuildSearchCriteria;
import com.jfrog.build.acl.model.BuildWrapper;
import com.jfrog.build.acl.model.BuildsSearchFilter;
import com.jfrog.build.acl.model.GeneralBuild;
import com.jfrog.build.model.api.ContinueBuildFilter;
import com.jfrog.build.model.api.ModuleArtifact;
import com.jfrog.build.model.api.ModuleDependency;
import com.jfrog.build.model.api.PublishedModule;
import com.jfrog.build.model.api.diff.BuildParams;
import com.jfrog.build.dao.BuildArtifactsDao;
import com.jfrog.build.dao.BuildDependenciesDao;
import com.jfrog.build.dao.BuildModulesDao;
import com.jfrog.build.dao.BuildsDao;
import com.jfrog.build.entity.BuildArtifact;
import com.jfrog.build.entity.BuildDependency;
import com.jfrog.build.entity.BuildEntity;
import com.jfrog.build.entity.BuildIdEntity;
import com.jfrog.build.entity.BuildModule;
import com.jfrog.build.entity.BuildPromotionStatus;
import com.jfrog.build.entity.BuildProperty;
import com.jfrog.build.entity.ModuleProperty;
import jakarta.annotation.Nullable;
import lombok.Data;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jfrog.build.api.Artifact;
import org.jfrog.build.api.Build;
import org.jfrog.build.api.BuildFileBean;
import org.jfrog.build.api.Dependency;
import org.jfrog.build.api.Module;
import org.jfrog.build.api.release.PromotionStatus;
import org.jfrog.common.StreamSupportUtils;
import org.jfrog.storage.binstore.ifc.model.BinaryElement;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;

import static com.jfrog.build.acl.model.BuildChecksumType.MD5;
import static com.jfrog.build.acl.model.BuildChecksumType.SHA1;
import static com.jfrog.build.acl.model.BuildChecksumType.SHA256;
import static com.jfrog.build.acl.util.BuildInfoUtils.formatBuildTime;
import static com.jfrog.build.acl.util.BuildInfoUtils.parseBuildTime;
import static com.jfrog.build.util.BuildPerformanceMonitor.logOperationStats;
import static java.lang.String.valueOf;
import static java.lang.System.currentTimeMillis;
import static java.lang.System.nanoTime;
import static java.util.Objects.isNull;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.artifactory.exception.factory.ExceptionFactory.internalServerException;

@Slf4j
@RequiredArgsConstructor
public class BuildDatabaseService {

    private final BuildBridgeToArtifactory bridgeToArtifactory;

    private final BuildsDao buildsDao;
    private final BuildModulesDao buildModulesDao;
    private final BuildArtifactsDao buildArtifactsDao;
    private final BuildDependenciesDao buildDependenciesDao;

    public static Date parseStringToDate(String dateString) {
        return new Date(parseBuildTime(dateString));
    }

    public void addBuild(@NonNull BuildWrapper build) {
        try {
            String buildStarted = build.getStarted();
            Date parsedDate = parseStringToDate(buildStarted);
            BuildEntity dbBuild = new BuildEntity(
                    bridgeToArtifactory.getNextDatabaseId(),
                    build.getName(),
                    build.getNumber(),
                    parsedDate.getTime(),
                    build.getBuild().getUrl(),
                    currentTimeMillis(),
                    build.getBuild().getArtifactoryPrincipal(),
                    0L,
                    null,
                    build.getBuildRepo()
            );
            long buildId = dbBuild.getBuildId();
            dbBuild.setProperties(createProperties(buildId, build.getBuild()));
            dbBuild.setPromotions(createPromotions(buildId, build.getBuild()));
            long opStart = nanoTime();
            buildsDao.createBuild(dbBuild);
            logOperationStats("SQL insertion", opStart);
            insertModules(buildId, build);
        } catch (SQLException e) {
            throw internalServerException("Could not insert build %s", e, build);
        }
    }

    private ArrayList<BuildPromotionStatus> createPromotions(long buildId, Build build) {
        List<PromotionStatus> statuses = build.getStatuses();
        ArrayList<BuildPromotionStatus> buildPromotions;
        if (statuses != null && !statuses.isEmpty()) {
            buildPromotions = new ArrayList<>(statuses.size());
            for (PromotionStatus status : statuses) {
                buildPromotions.add(convertPromotionStatus(buildId, status));
            }
        } else {
            buildPromotions = new ArrayList<>(1);
        }
        return buildPromotions;
    }

    private BuildPromotionStatus convertPromotionStatus(long buildId, PromotionStatus status) {
        return new BuildPromotionStatus(buildId,
                status.getTimestampDate().getTime(),
                status.getUser(),
                status.getStatus(),
                status.getRepository(),
                status.getComment(),
                status.getCiUser());
    }

    private Set<BuildProperty> createProperties(long buildId, Build build) {
        Properties properties = build.getProperties();
        Set<BuildProperty> buildProperties;
        if (properties != null && !properties.isEmpty()) {
            buildProperties = new HashSet<>(properties.size());
            for (Map.Entry<Object, Object> entry : properties.entrySet()) {
                buildProperties.add(
                        new BuildProperty(bridgeToArtifactory.getNextDatabaseId(), buildId, entry.getKey().toString(),
                                entry.getValue().toString())
                );
            }
        } else {
            buildProperties = new HashSet<>(1);
        }
        return buildProperties;
    }

    private void insertModules(long buildId, BuildWrapper buildWrapper) throws SQLException {
        List<Module> modules = buildWrapper.getBuild().getModules();
        if (modules == null || modules.isEmpty()) {
            return;
        }
        List<BuildModule> buildModules = new ArrayList<>();
        List<ModuleProperty> buildModulesProperties = new ArrayList<>();
        List<BuildArtifact> buildArtifacts = new ArrayList<>();
        List<BuildDependency> buildDependencies = new ArrayList<>();
        for (Module module : modules) {
            BuildModule buildModule = getBuildModule(buildId, module);
            buildModules.add(buildModule);
            buildModulesProperties.addAll(buildModule.getProperties());
            List<Artifact> artifacts = module.getArtifacts();
            buildArtifacts.addAll(calculateBuildArtifacts(buildModule, artifacts));
            buildDependencies.addAll(prepareBuildDependencies(module, buildModule.getModuleId()));
        }
        long opStart = nanoTime();
        buildModulesDao.createBuildModules(buildModules);
        logOperationStats("SQL insertion for modules", opStart);
        opStart = nanoTime();
        buildModulesDao.createBuildModuleProperties(buildModulesProperties);
        logOperationStats("SQL insertion for module properties", opStart);
        opStart = nanoTime();
        buildArtifactsDao.createBuildArtifacts(buildArtifacts);
        logOperationStats("SQL insertion for artifacts", opStart);
        opStart = nanoTime();
        buildDependenciesDao.createBuildDependencies(buildDependencies);
        logOperationStats("SQL insertion for dependencies", opStart);
    }

    private BuildModule getBuildModule(long buildId, Module module) {
        BuildModule buildModule = new BuildModule(
                bridgeToArtifactory.getNextDatabaseId(), buildId, module.getId(), module.getType());
        buildModule.setProperties(calculateModuleProperties(buildModule, module.getProperties()));
        return buildModule;
    }

    private List<BuildDependency> prepareBuildDependencies(Module module,
            long moduleId) {
        List<Dependency> dependencies = module.getDependencies();
        if (dependencies == null || dependencies.isEmpty()) {
            return List.of();
        }
        return dependencies.stream()
                .map(dependency -> new BuildDependency(
                        bridgeToArtifactory.getNextDatabaseId(),
                        moduleId,
                        dependency.getId(), dependency.getScopes(), dependency.getType(),
                        dependency.getSha1(), dependency.getMd5(), dependency.getSha256()))
                .toList();
    }

    @NonNull
    private List<BuildArtifact> calculateBuildArtifacts(@NonNull BuildModule dbModule, List<Artifact> artifacts) {
        List<BuildArtifact> dbArtifacts;
        if (artifacts != null && !artifacts.isEmpty()) {
            dbArtifacts = Lists.newArrayListWithExpectedSize(artifacts.size());
            for (Artifact artifact : artifacts) {
                // Artifact properties are not inserted in DB
                dbArtifacts.add(new BuildArtifact(bridgeToArtifactory.getNextDatabaseId(), dbModule.getModuleId(),
                        artifact.getName(), artifact.getType(), artifact.getSha1(), artifact.getMd5(),
                        artifact.getSha256()));
            }
        } else {
            dbArtifacts = Lists.newArrayListWithExpectedSize(1);
        }
        return dbArtifacts;
    }

    @NonNull
    private Set<ModuleProperty> calculateModuleProperties(@NonNull BuildModule dbModule, Properties properties) {
        Set<ModuleProperty> moduleProperties;
        if (properties != null && !properties.isEmpty()) {
            moduleProperties = Sets.newHashSetWithExpectedSize(properties.size());
            for (Map.Entry<Object, Object> entry : properties.entrySet()) {
                moduleProperties.add(
                        new ModuleProperty(bridgeToArtifactory.getNextDatabaseId(), dbModule.getModuleId(),
                                entry.getKey().toString(),
                                entry.getValue().toString())
                );
            }
        } else {
            moduleProperties = Sets.newHashSetWithExpectedSize(1);
        }
        return moduleProperties;
    }

    public void populateMissingChecksums(Build build) {
        List<Module> modules = build.getModules();
        if (modules != null && !modules.isEmpty()) {
            for (Module module : modules) {
                handleBeanPopulation(module.getArtifacts());
                handleBeanPopulation(module.getDependencies());
            }
        }
    }

    public BuildRun getBuildRun(String buildName, String buildNumber, String buildStarted) {
        Date parsedDate = parseStringToDate(buildStarted);
        return getBuildRun(buildName, buildNumber, parsedDate.getTime());
    }

    public BuildRun getBuildRun(String buildName, String buildNumber, long buildStarted) {
        try {
            BuildEntity entity = buildsDao.findBuild(buildName, buildNumber, buildStarted);
            if (entity != null) {
                return getBuildRun(entity);
            }
        } catch (SQLException e) {
            throw internalServerException(
                    "Could not execute find build for build name='%s' number='%s' start='%s'", e,
                    buildName, buildNumber, buildStarted);
        }
        return null;
    }

    public void addPromotionStatus(
            @NonNull BuildWrapper build,
            @NonNull PromotionStatus promotion,
            @NonNull String currentUser) {
        log.debug("Adding build promotion status for build: '{}'", build);
        BuildRun buildRun = getBuildRun(build.getName(), build.getNumber(), build.getStarted());
        if (isNull(buildRun)) {
            throw internalServerException("Could not add promotion %s to non existent build %s", promotion, build);
        }
        try {
            long buildId = findIdFromBuildRun(buildRun);
            BuildPromotionStatus promotionStatus = convertPromotionStatus(buildId, promotion);
            buildsDao.addPromotionStatus(buildId, promotionStatus, currentUser, currentTimeMillis());
        } catch (SQLException e) {
            throw internalServerException("Could not add promotion %s for build %s", e, promotion, buildRun);
        }
    }

    public boolean exists(BuildWrapper build) {
        String name = build.getName();
        String number = build.getNumber();
        String started = build.getStarted();
        try {
            return buildsDao.findBuildId(name, number, parseBuildTime(started)) > 0;
        } catch (SQLException e) {
            throw internalServerException(
                    "Error encountered while verifying build existence for build %s:%s-%s", e, name, number, started);
        }
    }

    public BuildRun getLatestBuildRun(String buildName, String buildNumber, String repoKey) {
        String fullBuildDate = "not found";
        try {
            return buildsDao.findLatestBuild(buildName, buildNumber, repoKey);
        } catch (SQLException e) {
            throw internalServerException(
                    "Could not find build JSON for latest build name='%s' number='%s' latest date found='%s'", e,
                    buildName, buildNumber, fullBuildDate);
        }
    }

    private void deleteBuilds(Collection<Long> buildIds) throws SQLException {
        for (Long buildId : buildIds) {
            List<Long> moduleIds = buildModulesDao.findModuleIdsForBuild(buildId);
            if (!moduleIds.isEmpty()) {
                buildArtifactsDao.deleteBuildArtifacts(moduleIds);
                buildDependenciesDao.deleteBuildDependencies(moduleIds);
            }
            buildModulesDao.deleteBuildModules(moduleIds, buildId);
            buildsDao.deleteBuild(buildId);
        }
    }

    public void deleteBuild(String buildName, String buildNumber, String buildStarted, String repo) {
        BuildRunImpl buildRun = new BuildRunImpl(buildName, buildNumber, buildStarted, repo);
        deleteBuildFromBuildRun(buildRun);
    }

    public void deleteBuildFromBuildRun(BuildRun buildRun) {
        log.debug("Deleting Build " + buildRun);
        try {
            long buildId = findIdFromBuildRun(buildRun);
            if (buildId > 0L) {
                deleteBuilds(List.of(buildId));
            } else {
                log.info("Build " + buildRun + " already deleted!");
            }
        } catch (SQLException e) {
            throw internalServerException("Could not delete build %s", e, buildRun);
        }
    }

    public void deleteAllBuilds() {
        try {
            buildArtifactsDao.deleteAllBuildArtifacts();
            buildDependenciesDao.deleteAllBuildDependencies();
            buildModulesDao.deleteAllBuildModules();
            buildsDao.deleteAllBuilds();
        } catch (SQLException e) {
            throw internalServerException("Could not delete all builds", e);
        }
    }

    public Set<BuildRun> findBuildsForChecksum(
            BuildSearchCriteria criteria,
            BuildChecksumType type,
            String checksum,
            @NonNull String buildRepo) {
        if (!type.isValid(checksum)) {
            log.info("Looking for invalid checksum {} '{}'", type.name(), checksum);
        }
        try {
            Set<BuildRun> results = Sets.newHashSet();
            if (criteria.searchInDependencies()) {
                Collection<BuildEntity> buildEntities =
                        buildsDao.findBuildsForDependencyChecksum(type, checksum, buildRepo);
                for (BuildEntity buildEntity : buildEntities) {
                    results.add(getBuildRun(buildEntity));
                }
            }
            if (criteria.searchInArtifacts()) {
                Collection<BuildEntity> buildEntities =
                        buildsDao.findBuildsForArtifactChecksum(type, checksum, buildRepo);
                for (BuildEntity buildEntity : buildEntities) {
                    results.add(getBuildRun(buildEntity));
                }
            }
            return results;
        } catch (SQLException e) {
            throw internalServerException("Could not find builds for %s '%s'", e, type.name(), checksum);
        }
    }

    public List<PublishedModule> getPublishedModules(
            String buildName,
            String buildNumber,
            String date,
            String buildRepo) {
        List<PublishedModule> buildModule = null;
        try {
            buildModule = buildsDao.getBuildModule(buildName, buildNumber, date, buildRepo);
        } catch (Exception e) {
            log.error(e.toString());
        }
        return buildModule;
    }

    public List<ModuleArtifact> getModuleArtifact(String buildName, String buildNumber, String moduleId, String date,
            String buildRepo, boolean onlyDefaultBuildRepoExists) {
        List<ModuleArtifact> moduleArtifactList = null;
        try {
            moduleArtifactList = buildsDao
                    .getModuleArtifact(buildName, buildNumber, date, moduleId, buildRepo, onlyDefaultBuildRepoExists);
        } catch (Exception e) {
            log.error(e.toString());
        }
        return moduleArtifactList;
    }

    @NonNull
    public List<BuildNodeRecord> getBuildArtifacts(String buildName, String buildNumber, String buildRepo) {
        List<BuildNodeRecord> artifacts = new ArrayList<>();
        try {
            artifacts = buildArtifactsDao.getBuildArtifacts(buildName, buildNumber, buildRepo);
        } catch (Exception e) {
            log.error("Error while query build artifacts : ", e);
        }
        return artifacts;
    }

    @NonNull
    public List<BuildNodeRecord> getBuildArtifacts(
            String buildName,
            String buildNumber,
            String buildRepo,
            long publishedEpoch) {
        List<BuildNodeRecord> artifacts = new ArrayList<>();
        try {
            artifacts = buildArtifactsDao.getBuildArtifacts(buildName, buildNumber, buildRepo, publishedEpoch);
        } catch (Exception e) {
            log.error("Error while query build artifacts : ", e);
        }
        return artifacts;
    }

    @Nullable
    public List<ModuleDependency> getModuleDependency(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull String moduleId,
            @NonNull String buildStarted,
            @NonNull String buildRepo) {
        try {
            return buildsDao.selectModuleDependencies(buildName, buildNumber, buildStarted, buildRepo, moduleId);
        } catch (Exception e) {
            log.error("Failed to select build dependencies for module {} of build {}:{}:{}:{}",
                    moduleId, buildRepo, buildName, buildNumber, buildStarted, e);
            return null;
        }
    }

    @NonNull
    public List<BuildFileInfo> getBuildDependencies(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull String buildRepo) {
        try {
            return buildDependenciesDao.selectBuildDependencies(buildName, buildNumber, buildRepo);
        } catch (Exception e) {
            log.error("Failed to select build dependencies for build {}:{}:{}", buildRepo, buildName, buildNumber, e);
            throw internalServerException(EMPTY, e);
        }
    }

    @NonNull
    public List<BuildFileInfo> getBuildArtifacts(
            @NonNull BuildWrapper buildWrapper,
            @Nullable List<String> sourceRepos,
            @Nullable List<String> excludedRepos) {
        try {
            return buildArtifactsDao.selectBuildArtifacts(buildWrapper, sourceRepos, excludedRepos);
        } catch (Exception e) {
            log.error("Failed to select unmatched artifacts non-strict for build {}:{}:{}", buildWrapper.getBuildRepo(),
                    buildWrapper.getName(), buildWrapper.getNumber(), e);
            throw internalServerException(EMPTY, e);
        }
    }

    public List<ModuleArtifact> getModuleArtifactsForDiffWithPaging(BuildParams buildParams,
            boolean onlyDefaultBuildRepoExists) {
        return buildsDao.getModuleArtifactsForDiffWithPaging(buildParams, onlyDefaultBuildRepoExists);
    }

    public List<GeneralBuild> getPrevBuildsList(String buildName, String buildDate, String buildRepo) {
        return buildsDao.getPrevBuildsList(buildName, buildDate, buildRepo);
    }

    public List<BuildProps> getBuildPropsData(BuildParams buildParams) {
        List<BuildProps> buildPropsList = buildsDao.getBuildPropsList(buildParams);
        return distinctBuildProps(buildPropsList);
    }

    public static List<BuildProps> distinctBuildProps(List<BuildProps> buildPropertyList) {
        List<BuildPropertyAsKeyValue> distinct = buildPropertyList.stream()
                .map(BuildPropertyAsKeyValue::new).distinct().toList();
        return distinct.stream().map(it -> new BuildProps(it.propKey, it.propValue, null)).toList();
    }

    public List<ModuleDependency> getModuleDependencyForDiffWithPaging(BuildParams buildParams) {
        return buildsDao.getModuleDependencyForDiffWithPaging(buildParams);
    }

    @NonNull
    public Set<BuildRun> getLatestBuildsByName(@Nullable String buildRepo) {
        try {
            List<BuildNameAndRepo> allBuildsInfo = buildsDao.getAllBuildNames(buildRepo);
            LinkedHashSet<BuildRun> results = new LinkedHashSet<>(allBuildsInfo.size());
            for (BuildNameAndRepo buildInfo : allBuildsInfo) {
                BuildEntity buildEntity = buildsDao.getLatestBuild(buildInfo.getName(), buildInfo.getRepo());
                if (buildEntity != null) {
                    results.add(getBuildRun(buildEntity));
                }
            }
            return results;
        } catch (SQLException e) {
            throw internalServerException("Could not list all builds by name and latest build date", e);
        }
    }

    public List<BuildNameAndRepo> getBuildNames(@NonNull List<String> buildRepos, @Nullable String buildName,
            long offset, long limit) {
        try {
            return buildsDao.getBuildNames(buildRepos, buildName, offset, limit);
        } catch (SQLException e) {
            throw internalServerException(
                    "Failed to search for build names; repos: %s, name param: %s, offset: %s, limit: %s", e,
                    buildRepos, buildName, offset, limit);
        }
    }

    @NonNull
    public List<BuildNumberRecord> getBuildNumbers(@NonNull String repoKey, @NonNull String name, long startedBefore,
            boolean excludeInReleaseBundles, long startedAfter, long limit, long bundlesLimit) {
        try {
            return buildsDao.getBuildNumbers(repoKey, name, startedBefore, excludeInReleaseBundles,
                    startedAfter, limit, bundlesLimit);
        } catch (SQLException e) {
            throw internalServerException(
                    "Failed to search for build numbers; repoKey: %s, name: %s, startedBefore: %s, "
                            + "excludeInReleaseBundles: %s, startedAfter: %s, limit: %s", e,
                    repoKey, name, startedBefore, excludeInReleaseBundles, startedAfter, limit);
        }
    }

    public List<BuildId> getLatestBuildIDsPaging(ContinueBuildFilter continueBuildFilter) {
        List<BuildIdEntity> allBuildNames = buildsDao.getLatestBuildIds(continueBuildFilter);
        return StreamSupportUtils.stream(allBuildNames)
                .map(this::buildIdFromEntity)
                .collect(toList());
    }

    public List<String> getDistinctBuildNamesPaging(ContinueBuildFilter continueBuildFilter) {
        return buildsDao.getDistinctBuildNames(continueBuildFilter);
    }

    public int getDistinctBuildNamesCount(ContinueBuildFilter continueBuildFilter) {
        return buildsDao.getDistinctBuildNamesCount(continueBuildFilter);
    }

    @NonNull
    public List<BuildId> getLatestBuildIDsByNameCaseInsensitive(@NonNull BuildsSearchFilter filter) {
        try {
            return buildsDao.selectLatestBuildIdsByNameCaseInsensitive(filter).stream()
                    .map(this::buildIdFromEntity)
                    .collect(toList());
        } catch (SQLException e) {
            throw internalServerException("Failed to get all builds by name and latest build date", e);
        }
    }

    @NonNull
    public List<GeneralBuild> getBuildNumbersByBuildName(@NonNull BuildsSearchFilter filter) {
        try {
            return buildsDao.selectBuildNumbersByName(filter).stream().map(this::buildGeneralFromEntity).toList();
        } catch (SQLException e) {
            throw internalServerException("Failed to get build numbers by name", e);
        }
    }

    @NonNull
    public Map<Long, String> getLatestPromotionStatusForBuildIds(@NonNull List<Long> buildsIds) {
        try {
            return buildsDao.selectLatestPromotionStatusForBuildIds(buildsIds);
        } catch (SQLException e) {
            throw internalServerException("Failed to get latest build promotion statuses", e);
        }
    }

    public void populateReleaseBundleNames(@NonNull List<BuildId> buildIds) {
        try {
            buildsDao.populateReleaseBundleNames(buildIds);
        } catch (SQLException e) {
            throw internalServerException("Could not populate Release Bundle names for builds", e);
        }
    }

    @NonNull
    @VisibleForTesting
    BuildIdImpl buildIdFromEntity(@NonNull BuildIdEntity entity) {
        return BuildIdImpl.builder()
                .buildId(entity.getBuildId())
                .name(entity.getBuildName())
                .number(entity.getBuildNumber())
                .started(entity.getBuildDate())
                .buildRepo(entity.getBuildRepo())
                .build();
    }

    @NonNull
    private GeneralBuild buildGeneralFromEntity(@NonNull BuildEntityRecord entity) {
        return GeneralBuild.builder()
                .buildId(entity.getBuildId())
                .buildName(entity.getBuildName())
                .buildNumber(entity.getBuildNumber())
                .buildDate(entity.getBuildDate())
                .buildRepo(entity.getRepo())
                .ciUrl(entity.getCiUrl())
                .build();
    }

    public Set<BuildRun> findBuildsByName(
            @Nullable String buildName,
            @Nullable String buildRepo) {
        Set<BuildRun> results = Sets.newHashSet();
        try {
            List<Long> buildIds = buildsDao.findBuildIds(buildName, buildRepo);
            for (Long buildId : buildIds) {
                getBuildRun(buildId).ifPresent(results::add);
            }
        } catch (SQLException e) {
            throw internalServerException("Could not search for builds with name='%s'", e, valueOf(buildName));
        }
        return results;
    }

    public Set<BuildRun> findBuildsByName(@Nullable String buildName) {
        Set<BuildRun> results = Sets.newHashSet();
        try {
            List<Long> buildIds = buildsDao.findBuildIds(buildName);
            for (Long buildId : buildIds) {
                getBuildRun(buildId).ifPresent(results::add);
            }
        } catch (SQLException e) {
            throw internalServerException("Could not search for builds with name='%s'", e, buildName);
        }
        return results;
    }

    public boolean buildNameExists(@NonNull String buildName, @NonNull String buildRepo) {
        try {
            return buildsDao.existsBuildByName(buildName, buildRepo);
        } catch (SQLException e) {
            throw internalServerException("Could not check existence of build name='%s'", e, buildName);
        }
    }

    public Set<BuildRun> findBuildsByNameAndNumber(String buildName, String buildNumber, @NonNull String buildRepo) {
        Set<BuildRun> results = Sets.newHashSet();
        try {
            List<Long> buildIds = buildsDao.findBuildIds(buildName, buildNumber, buildRepo);
            for (Long buildId : buildIds) {
                getBuildRun(buildId).ifPresent(results::add);
            }
        } catch (SQLException e) {
            throw internalServerException(
                    "Could not search for builds with name='%s' and number='%s'", e, buildName, buildNumber);
        }
        return results;
    }

    @NonNull
    public Set<BuildRun> findBuildsByNameAndNumbers(
            @NonNull String buildName,
            @NonNull Collection<String> buildNumbers,
            @NonNull String buildRepo) {
        try {
            return new LinkedHashSet<>(buildsDao.findBuildsByNameAndNumbers(buildName, buildNumbers, buildRepo));
        } catch (SQLException e) {
            throw internalServerException(
                    "Could not search for builds with name='%s' and numbers='%s'", e, buildName, buildNumbers);
        }
    }

    public List<BuildNameAndRepo> getAllBuildsInfo(@Nullable String buildRepo) {
        try {
            return buildsDao.getAllBuildNames(buildRepo);
        } catch (SQLException e) {
            throw internalServerException("Could not retrieve the list of build names", e);
        }
    }

    private long findIdFromBuildRun(BuildRun buildRun) throws SQLException {
        long buildId = 0L;
        if (buildRun instanceof BuildRunImpl) {
            buildId = ((BuildRunImpl) buildRun).getBuildId();
        }
        if (buildId <= 0L) {
            buildId = buildsDao
                    .findBuildId(buildRun.getName(), buildRun.getNumber(), buildRun.getStartedDate().getTime());
        }
        return buildId;
    }

    @NonNull
    private Optional<BuildRun> getBuildRun(Long buildId) throws SQLException {
        BuildEntity buildEntity = buildsDao.getBuild(buildId);
        if (buildEntity == null) {
            return empty();
        }
        return of(getBuildRun(buildEntity));
    }

    public List<GeneralBuild> getBuildForName(String buildName, String buildRepo,
            ContinueBuildFilter continueBuildFilter) {
        try {
            return buildsDao.getBuildForName(buildName, buildRepo, continueBuildFilter);
        } catch (SQLException e) {
            throw internalServerException("Could not get builds for name '%s'.", e, buildName);
        }
    }

    @NonNull
    private BuildRun getBuildRun(@NonNull BuildEntity build) {
        Optional<BuildPromotionStatus> latestPromotion = getLatestPromotion(build);
        return new BuildRunImpl(
                build.getBuildId(),
                build.getBuildName(),
                build.getBuildNumber(),
                formatBuildTime(build.getBuildDate()),
                build.getCiUrl(),
                latestPromotion.map(BuildPromotionStatus::getStatus).orElse(null),
                latestPromotion.map(BuildPromotionStatus::getRepository).orElse(EMPTY),
                build.getRepo());
    }

    @NonNull
    private Optional<BuildPromotionStatus> getLatestPromotion(@NonNull BuildEntity build) {
        return build.getPromotions().isEmpty() ? empty() : of(build.getPromotions().last());
    }

    /**
     * Locates and fills in missing checksums of a build file bean
     *
     * @param buildFiles List of build files to populate
     */
    private void handleBeanPopulation(List<? extends BuildFileBean> buildFiles) {
        if (isEmpty(buildFiles)) {
            return;
        }
        Set<String> checksums = getMissingChecksums(buildFiles);

        Set<BinaryElement> binaryInfos = bridgeToArtifactory.findBinaries(checksums);
        Map<String, BinaryElement> sha1ToInfo = Maps.newHashMap();
        Map<String, BinaryElement> sha2ToInfo = Maps.newHashMap();
        Map<String, BinaryElement> md5ToInfo = Maps.newHashMap();

        binaryInfos.forEach(binaryInfo -> {
            sha1ToInfo.put(binaryInfo.getSha1(), binaryInfo);
            sha2ToInfo.put(binaryInfo.getSha2(), binaryInfo);
            md5ToInfo.put(binaryInfo.getMd5(), binaryInfo);
        });
        populateMissingChecksums(buildFiles, sha1ToInfo, sha2ToInfo, md5ToInfo);
    }

    /**
     * Collects one available checksum for each {@link BuildFileBean} in {@param buildFiles} that has one or more
     * missing checksums from its build info.
     * If all checksums are missing from the build info or if the bean has all, it is skipped.
     */
    private Set<String> getMissingChecksums(List<? extends BuildFileBean> buildFiles) {
        Set<String> checksums = Sets.newHashSet();
        for (BuildFileBean buildFile : buildFiles) {
            ShaExtractor shaExtractor = new ShaExtractor(buildFile);
            if (shaExtractor.skipProcessing()) {
                continue;
            }

            //This bean is missing one or more checksums - need to search for the others based on the first one it has.
            if (shaExtractor.isSha1Exists()) {
                checksums.add(buildFile.getSha1());
            } else if (shaExtractor.isSha2Exists()) {
                checksums.add(buildFile.getSha256());
            } else {
                checksums.add(buildFile.getMd5());
            }
        }
        return checksums;
    }

    private void populateMissingChecksums(List<? extends BuildFileBean> buildFiles,
            Map<String, BinaryElement> sha1ToInfo, Map<String, BinaryElement> sha2ToInfo,
            Map<String, BinaryElement> md5ToInfo) {

        for (BuildFileBean buildFile : buildFiles) {
            setMissingChecksums(sha1ToInfo, sha2ToInfo, md5ToInfo, buildFile);
        }
    }

    private void setMissingChecksums(Map<String, BinaryElement> sha1ToInfo, Map<String, BinaryElement> sha2ToInfo,
            Map<String, BinaryElement> md5ToInfo, BuildFileBean buildFile) {
        ShaExtractor shaExtractor = new ShaExtractor(buildFile);
        //If the bean has all or none of the checksums, skip it
        if (shaExtractor.skipProcessing()) {
            return;
        }
        if (shaExtractor.isSha1Exists()) {
            BinaryElement info = sha1ToInfo.get(buildFile.getSha1());
            if (info == null) {
                return;
            }
            if (!shaExtractor.isSha2Exists()) {
                setNewChecksum(SHA256, info.getSha2(), buildFile::setSha256);
            }
            if (!shaExtractor.isMd5Exists()) {
                setNewChecksum(MD5, info.getMd5(), buildFile::setMd5);
            }
        } else if (shaExtractor.isSha2Exists()) {
            BinaryElement info = sha2ToInfo.get(buildFile.getSha256());
            if (info == null) {
                return;
            }
            //there's no sha1 here for sure
            setNewChecksum(SHA1, info.getSha1(), buildFile::setSha1);
            if (!shaExtractor.isMd5Exists()) {
                setNewChecksum(MD5, info.getMd5(), buildFile::setMd5);
            }
        } else { //md5
            BinaryElement info = md5ToInfo.get(buildFile.getMd5());
            if (info == null) {
                return;
            }
            //there's no sha1 and no sha2 here for sure
            setNewChecksum(SHA256, info.getSha2(), buildFile::setSha256);
            setNewChecksum(SHA1, info.getSha1(), buildFile::setSha1);
        }
    }

    private void setNewChecksum(BuildChecksumType checksumType, String newChecksum, Consumer<String> checksumSetter) {
        if (checksumType.isValid(newChecksum)) {
            checksumSetter.accept(newChecksum);
        }
    }

    public String getBuildStoringRepo(@NonNull String name, @NonNull String number, @NonNull String started) {
        try {
            Date parsedDate = parseStringToDate(started);
            return buildsDao.getBuildStoringRepo(name, number, parsedDate.getTime());
        } catch (SQLException e) {
            throw internalServerException("Could not find build repo by name and number", e);
        }
    }

    public int getBuildsCountFromSpecificRepo(@Nullable String buildInfoRepoKey) {
        try {
            return buildsDao.getBuildsCountFromSpecificRepo(buildInfoRepoKey);
        } catch (SQLException e) {
            throw internalServerException("Could not count the total builds amount from repository %s", e,
                    buildInfoRepoKey);
        }
    }

    public int getUniqueBuildsCountFromSpecificRepo(String buildInfoRepoKey) {
        try {
            return buildsDao.getUniqueBuildsCountFromSpecificRepo(buildInfoRepoKey);
        } catch (SQLException e) {
            throw internalServerException("Could not count the total builds amount from repository %s", e,
                    buildInfoRepoKey);
        }
    }

    public int getTotalBuildsCount() {
        try {
            return buildsDao.getTotalBuildsCount();
        } catch (SQLException e) {
            throw internalServerException("Could not count the total builds amount", e);
        }
    }

    @NonNull
    public Map<String, List<BuildNode>> selectDependencyNodesBySha1(@NonNull List<String> sha1s) {
        try {
            return buildsDao.selectDependencyNodesBySha1(sha1s);
        } catch (SQLException e) {
            throw internalServerException("Failed to get build dependencies by SHA-1", e);
        }
    }

    @NonNull
    public List<String> getBuildNumbersNotInRepo(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull String buildRepo) {
        try {
            return buildsDao.selectBuildNumbersNotInRepo(buildName, buildNumber, buildRepo);
        } catch (SQLException e) {
            throw internalServerException(
                    "Failed to get build numbers %s,%s which are not in repo %s", e, buildName, buildNumber, buildRepo);
        }
    }

    @Getter
    private static class ShaExtractor {
        private final boolean sha1Exists;
        private final boolean sha2Exists;
        private final boolean md5Exists;

        ShaExtractor(BuildFileBean buildFile) {
            sha1Exists = StringUtils.isNotBlank(buildFile.getSha1());
            sha2Exists = StringUtils.isNotBlank(buildFile.getSha256());
            md5Exists = StringUtils.isNotBlank(buildFile.getMd5());
        }

        boolean skipProcessing() {
            return ((sha1Exists && sha2Exists && md5Exists) || (!sha1Exists && !sha2Exists && !md5Exists));
        }
    }

    /**
     * Used for doing programmatically distinct
     * Must be package level class (not inner) when we use lombok
     *
     * @author Saffi Hartal
     */
    @Data
    static class BuildPropertyAsKeyValue {
        final String propKey;
        final String propValue;

        @Override
        public String toString() {
            return "BuildPropertyAsKeyValue{" +
                    "propKey='" + propKey + '\'' +
                    ", propValue='" + propValue + '\'' +
                    '}';
        }

        BuildPropertyAsKeyValue(BuildProps bp) {
            this(bp.getKey(), bp.getValue());
        }


        BuildPropertyAsKeyValue(String propKey, String propValue) {
            this.propKey = propKey;
            this.propValue = propValue;
        }
    }

}
