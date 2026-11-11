package com.jfrog.build.dao;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.UnmodifiableIterator;
import com.jfrog.build.acl.model.BuildFileInfo;
import com.jfrog.build.acl.model.BuildId;
import com.jfrog.build.model.BuildIdImpl;
import com.jfrog.build.acl.model.BuildNameAndRepo;
import com.jfrog.build.acl.model.BuildNode;
import com.jfrog.build.acl.model.BuildRun;
import com.jfrog.build.model.BuildNumberBundleRecord;
import com.jfrog.build.model.BuildNumberRecord;
import com.jfrog.build.model.BuildProps;
import com.jfrog.build.acl.model.BuildWrapper;
import com.jfrog.build.acl.model.BuildsSearchFilter;
import com.jfrog.build.acl.model.GeneralBuild;
import com.jfrog.build.model.api.ContinueBuildFilter;
import com.jfrog.build.model.api.ModuleArtifact;
import com.jfrog.build.model.api.ModuleDependency;
import com.jfrog.build.model.api.PublishedModule;
import com.jfrog.build.model.api.diff.BuildParams;
import com.jfrog.build.entity.BuildEntity;
import com.jfrog.build.entity.BuildIdEntity;
import com.jfrog.build.entity.BuildPromotionStatus;
import com.jfrog.build.entity.BuildProperty;
import com.jfrog.build.test.database.base.DatabaseTestBase;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import org.jfrog.build.api.Build;
import org.jfrog.storage.DbType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.jfrog.build.acl.model.BuildChecksumType.MD5;
import static com.jfrog.build.acl.model.BuildChecksumType.SHA1;
import static com.jfrog.build.acl.util.BuildConstants.BUILDINFO_REPO_KEY;
import static com.jfrog.build.acl.util.BuildConstants.DEFAULT_RBV2_REPO;
import static com.jfrog.build.dao.util.BuildsDaoUtils.getDbIndexedValueMaxSize;
import static com.jfrog.build.test.database.TestDatabaseProvider.getDatabaseType;
import static com.jfrog.build.test.util.TestBuildsUtils.createBuild103;
import static com.jfrog.build.test.util.TestBuildsUtils.createBuild11;
import static com.jfrog.build.test.util.TestBuildsUtils.createBuild12;
import static com.jfrog.build.test.util.TestBuildsUtils.createBuild13;
import static com.jfrog.build.test.util.TestBuildsUtils.createBuild15;
import static com.jfrog.build.test.util.TestBuildsUtils.createBuild16;
import static com.jfrog.build.test.util.TestBuildsUtils.createBuildEntity;
import static java.lang.System.nanoTime;
import static java.time.temporal.ChronoUnit.MINUTES;
import static org.jfrog.build.api.release.PromotionStatus.RELEASED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class BuildsDaoComponentTest extends DatabaseTestBase {

    private static final String ALTERNATIVE_BUILD_INFO_REPO = "project-build-info";
    private static final String NOT_EXISTING_BUILD_REPO = "not_existing_build_repo";

    @Autowired
    private BuildsDao buildsDao;

    @Autowired
    private BuildArtifactsDao buildArtifactsDao;

    @BeforeEach
    void setup() {
        importSql("/sql/dml/builds-in-two-repos.sql");
    }

    @Test
    void getBuild() throws SQLException {
        for (long i = 1L; i <= 6L; i++) {
            assertAnyBuild(buildsDao.getBuild(i));
        }
    }

    @Test
    void selectLatestBuildIdsByNameCaseInsensitive_Success() throws SQLException {
        Instant buildDate = Instant.now().minus(1, ChronoUnit.HOURS);
        BuildEntity buildA1 = createBuildEntity(nanoTime(), "AAA", "1", buildDate.toEpochMilli());
        buildA1.setProperties(new HashSet<>());
        buildA1.setPromotions(new HashSet<>());
        buildsDao.createBuild(buildA1);

        BuildEntity buildB1 = createBuildEntity(nanoTime(), "aaa", "1", buildDate.toEpochMilli());
        buildB1.setProperties(new HashSet<>());
        buildB1.setPromotions(new HashSet<>());
        buildsDao.createBuild(buildB1);

        BuildEntity buildB2 = createBuildEntity(nanoTime(), "aaa", "2", buildDate.plus(2, MINUTES).toEpochMilli());
        buildB2.setProperties(new HashSet<>());
        buildB2.setPromotions(new HashSet<>());
        buildsDao.createBuild(buildB2);

        BuildsSearchFilter filter = BuildsSearchFilter.builder()
                .name("a")
                .buildRepo(null)
                .orderBy("date")
                .sortOrder("desc")
                .limit(10)
                .offset(0L)
                .build();

        List<BuildIdEntity> latestBuildIds = buildsDao.selectLatestBuildIdsByNameCaseInsensitive(filter);
        assertEquals(2, latestBuildIds.size());
        assertEquals(buildB2.getBuildName(), latestBuildIds.get(0).getBuildName());
        assertEquals(buildB2.getBuildNumber(), latestBuildIds.get(0).getBuildNumber());
        assertEquals(buildA1.getBuildName(), latestBuildIds.get(1).getBuildName());

        filter = BuildsSearchFilter.builder()
                .name("A")
                .buildRepo(null)
                .orderBy("date")
                .sortOrder("desc")
                .limit(10)
                .offset(1L)
                .build();
        latestBuildIds = buildsDao.selectLatestBuildIdsByNameCaseInsensitive(filter);
        assertEquals(1, latestBuildIds.size());
        assertEquals("AAA", latestBuildIds.getFirst().getBuildName());
    }

    @Test
    void findBuildIds_ByNameAndRepo() throws SQLException {
        List<Long> buildIds = buildsDao.findBuildIds("ba", BUILDINFO_REPO_KEY);
        assertEquals(3, buildIds.size());
        for (Long buildId : buildIds) {
            assertTrue(buildId == 1L || buildId == 3L || buildId == 5L, "Build id " + buildId + " should not be here!");
        }
        buildIds = buildsDao.findBuildIds("bb", ALTERNATIVE_BUILD_INFO_REPO);
        assertEquals(2, buildIds.size());
        for (Long buildId : buildIds) {
            assertTrue(buildId == 2L || buildId == 4L, "Build id " + buildId + " should not be here!");
        }
        buildIds = buildsDao.findBuildIds("bb", BUILDINFO_REPO_KEY);
        assertEquals(1, buildIds.size());
    }

    @Test
    void findBuildIds_ByNameGlobally() throws SQLException {
        List<Long> buildIds = buildsDao.findBuildIds("ba");
        assertEquals(3, buildIds.size());
        for (Long buildId : buildIds) {
            assertTrue(buildId == 1L || buildId == 3L || buildId == 5L, "Build id " + buildId + " should not be here!");
        }
        buildIds = buildsDao.findBuildIds("bb");
        assertEquals(3, buildIds.size());
        for (Long buildId : buildIds) {
            assertTrue(buildId == 2L || buildId == 4L || buildId == 6L, "Build id " + buildId + " should not be here!");
        }
    }

    @Test
    void findBuildIds_ByNameNumberRepo() throws SQLException {
        List<Long> buildIds = buildsDao.findBuildIds("ba", "1", BUILDINFO_REPO_KEY);
        assertEquals(1, buildIds.size());
        buildIds = buildsDao.findBuildIds("bb", "1", ALTERNATIVE_BUILD_INFO_REPO);
        assertEquals(1, buildIds.size());
        buildIds = buildsDao.findBuildIds("bb", "1", BUILDINFO_REPO_KEY);
        assertEquals(0, buildIds.size());
    }

    @Test
    void existsBuildByName() throws SQLException {
        assertTrue(buildsDao.existsBuildByName("ba", BUILDINFO_REPO_KEY));
        assertTrue(buildsDao.existsBuildByName("bb", BUILDINFO_REPO_KEY));
        assertTrue(buildsDao.existsBuildByName("bb", ALTERNATIVE_BUILD_INFO_REPO));
        // Name exists but not in this repo.
        assertFalse(buildsDao.existsBuildByName("ba", ALTERNATIVE_BUILD_INFO_REPO));
        // Name does not exist at all.
        assertFalse(buildsDao.existsBuildByName("does-not-exist", BUILDINFO_REPO_KEY));
        assertFalse(buildsDao.existsBuildByName("bb", NOT_EXISTING_BUILD_REPO));
    }

    @Test
    void findBuildsByNameAndNumbers() throws SQLException {
        List<BuildRun> builds = buildsDao.findBuildsByNameAndNumbers("ba", List.of("1", "3"), BUILDINFO_REPO_KEY);
        assertEquals(2, builds.size());
        Set<String> numbers = builds.stream().map(BuildRun::getNumber).collect(Collectors.toSet());
        assertEquals(Set.of("1", "3"), numbers);
        for (BuildRun build : builds) {
            assertEquals("ba", build.getName());
            assertEquals(BUILDINFO_REPO_KEY, build.getBuildRepo());
            // The delete path needs 'started' to resolve the build.json path; it must be populated.
            assertNotNull(build.getStarted());
        }
        // A requested number that only exists in another repo is not returned.
        assertTrue(buildsDao.findBuildsByNameAndNumbers("bb", List.of("1"), BUILDINFO_REPO_KEY).isEmpty());
        // Unknown number returns empty.
        assertTrue(buildsDao.findBuildsByNameAndNumbers("ba", List.of("999"), BUILDINFO_REPO_KEY).isEmpty());
        // Empty numbers short-circuits without querying.
        assertTrue(buildsDao.findBuildsByNameAndNumbers("ba", List.of(), BUILDINFO_REPO_KEY).isEmpty());
    }

    @Test
    void getAllBuildNames() throws SQLException {
        createBuilds();
        List<BuildNameAndRepo> buildNames = buildsDao.getAllBuildNames(null);
        // 16 baseline + 3 new RTDEV-71186 fixtures (test-same-sha1-same-name-build, test-multi-module-symmetric,
        // test-multi-module-asymmetric) -- all in artifactory-build-info.
        assertEquals(19, buildNames.size());
        buildNames = buildsDao.getAllBuildNames(BUILDINFO_REPO_KEY);
        assertEquals(11, buildNames.size());
        buildNames = buildsDao.getAllBuildNames(ALTERNATIVE_BUILD_INFO_REPO);
        assertEquals(2, buildNames.size());
    }

    @Test
    void getLatestBuildIds() {
        ContinueBuildFilter continueBuildFilter = new ContinueBuildFilter();
        List<BuildIdEntity> latestBuildIds = buildsDao.getLatestBuildIds(continueBuildFilter);
        // 13 baseline + 3 new RTDEV-71186 fixtures.
        assertEquals(16, latestBuildIds.size());
        Map<String, BuildIdEntity> buildsIdsByName = new HashMap<>();
        latestBuildIds.forEach(buildId -> buildsIdsByName.computeIfAbsent(buildId.getBuildName(), key -> buildId));

        assertEquals(4, buildsIdsByName.get("bb").getBuildId());
        assertEquals("2", buildsIdsByName.get("bb").getBuildNumber());
        assertEquals(1349003000000L, buildsIdsByName.get("bb").getBuildDate());

        assertEquals(5, buildsIdsByName.get("ba").getBuildId());
        assertEquals("3", buildsIdsByName.get("ba").getBuildNumber());
        assertEquals(1349004000000L, buildsIdsByName.get("ba").getBuildDate());
    }

    @Test
    void getLatestBuildIds_ByBuildRepoOnFilter() throws SQLException {
        createBuilds();
        ContinueBuildFilter continueBuildFilter = new ContinueBuildFilter();
        continueBuildFilter.setBuildRepo("project-build-info");
        List<BuildIdEntity> latestBuildIds = buildsDao.getLatestBuildIds(continueBuildFilter);
        assertEquals(2, latestBuildIds.size());
        Map<String, BuildIdEntity> builds = latestBuildIds.stream()
                .collect(Collectors.toMap(BuildIdEntity::getBuildName, build -> build));

        assertEquals(4, builds.get("bb").getBuildId());
        assertEquals("2", builds.get("bb").getBuildNumber());
        assertEquals(1349003000000L, builds.get("bb").getBuildDate());

        assertEquals(8, builds.get("cc").getBuildId());
        assertEquals("1", builds.get("cc").getBuildNumber());
        assertEquals(1349006000000L, builds.get("cc").getBuildDate());
    }

    @Test
    void getLatestBuildIds_ByBuildRepoOnBuildId() throws SQLException {
        createBuilds();
        ContinueBuildFilter continueBuildFilter = new ContinueBuildFilter();
        BuildId buildId = new BuildIdImpl(2L, "bb", "1", 1349001000000L, "project-build-info");
        continueBuildFilter.setContinueBuildId(buildId);
        List<BuildIdEntity> latestBuildIds = buildsDao.getLatestBuildIds(continueBuildFilter);
        assertEquals(2, latestBuildIds.size());
        Map<String, BuildIdEntity> builds = latestBuildIds.stream()
                .collect(Collectors.toMap(BuildIdEntity::getBuildName, build -> build));
        assertEquals(4, builds.get("bb").getBuildId());
        assertEquals("2", builds.get("bb").getBuildNumber());
        assertEquals(1349003000000L, builds.get("bb").getBuildDate());

        assertEquals(8, builds.get("cc").getBuildId());
        assertEquals("1", builds.get("cc").getBuildNumber());
        assertEquals(1349006000000L, builds.get("cc").getBuildDate());
    }

    @Test
    void findBuild_ByFullPath() throws SQLException {
        assertBuild1(buildsDao.findBuild("ba", "1", 1349000000000L));
        assertBuild2(buildsDao.findBuild("bb", "1", 1349001000000L));
        assertBuild3(buildsDao.findBuild("ba", "2", 1349002000000L));
        assertBuild4(buildsDao.findBuild("bb", "2", 1349003000000L));
        assertBuild5(buildsDao.findBuild("ba", "3", 1349004000000L));
    }

    @Test
    void getBuild_WithLongModuleProps() throws SQLException {
        BuildEntity c5 = createBuild15();
        buildsDao.createBuild(c5);
        BuildEntity build = buildsDao.getBuild(15L);
        build.getProperties().forEach(buildProperty ->
                assertTrue(buildProperty.getPropValue().length() <= getDbIndexedValueMaxSize(getDatabaseType())));
    }

    @Test
    void findLatestBuild() throws SQLException {
        createBuilds();
        assertEquals(1349000000000L,
                buildsDao.findLatestBuild("ba", "1", BUILDINFO_REPO_KEY).getStartedDate().getTime());
        assertEquals(1349001000000L,
                buildsDao.findLatestBuild("bb", "1", ALTERNATIVE_BUILD_INFO_REPO).getStartedDate().getTime());
        assertEquals(1349111111111L,
                buildsDao.findLatestBuild("ba", "2", BUILDINFO_REPO_KEY).getStartedDate().getTime());
        assertEquals(1349003000000L,
                buildsDao.findLatestBuild("bb", "2", ALTERNATIVE_BUILD_INFO_REPO).getStartedDate().getTime());
        assertEquals(1349004000000L,
                buildsDao.findLatestBuild("ba", "3", BUILDINFO_REPO_KEY).getStartedDate().getTime());
        assertEquals(BUILDINFO_REPO_KEY,
                buildsDao.findLatestBuild("ba", "3", BUILDINFO_REPO_KEY).getBuildRepo());
        assertEquals(ALTERNATIVE_BUILD_INFO_REPO,
                buildsDao.findLatestBuild("bb", "2", ALTERNATIVE_BUILD_INFO_REPO).getBuildRepo());
        assertNotNull(
                buildsDao.findLatestBuild("bb", "3", BUILDINFO_REPO_KEY));
    }

    @Test
    void selectLatestBuildIdsByNameCaseInsensitive_ByStoringRepo() throws SQLException {
        createBuilds();
        // When repo param is specified we expect to get just the build names from that specific build-info repo
        BuildsSearchFilter searchFilter = BuildsSearchFilter.builder()
                .name("bb")
                .buildRepo(ALTERNATIVE_BUILD_INFO_REPO)
                .orderBy("date")
                .sortOrder("desc")
                .limit(10)
                .offset(0L)
                .build();

        List<BuildIdEntity> latestBuildIds = buildsDao.selectLatestBuildIdsByNameCaseInsensitive(searchFilter);
        assertEquals(1, latestBuildIds.size());
        assertEquals("bb", latestBuildIds.getFirst().getBuildName());
        assertEquals("2", latestBuildIds.getFirst().getBuildNumber());

        searchFilter = BuildsSearchFilter.builder()
                .name("bb")
                .buildRepo(BUILDINFO_REPO_KEY)
                .orderBy("date")
                .sortOrder("desc")
                .limit(10)
                .offset(0L)
                .build();
        latestBuildIds = buildsDao.selectLatestBuildIdsByNameCaseInsensitive(searchFilter);
        assertEquals(1, latestBuildIds.size());
        assertEquals("bb", latestBuildIds.getFirst().getBuildName());
        assertEquals("3", latestBuildIds.getFirst().getBuildNumber());

        // When repo param is null we expect to get all builds - like before the repo column was added
        searchFilter = BuildsSearchFilter.builder()
                .name("bb")
                .buildRepo(null)
                .orderBy("date")
                .sortOrder("desc")
                .limit(10)
                .offset(0L)
                .build();

        latestBuildIds = buildsDao.selectLatestBuildIdsByNameCaseInsensitive(searchFilter);
        assertEquals(2, latestBuildIds.size());
        assertEquals("bb", latestBuildIds.get(0).getBuildName());
        assertEquals("3", latestBuildIds.get(0).getBuildNumber());
        assertEquals("bb", latestBuildIds.get(1).getBuildName());
        assertEquals("2", latestBuildIds.get(1).getBuildNumber());
    }

    @ParameterizedTest
    @MethodSource("selectLatestBuildIdsByNameCaseInsensitive_FilterDirectionAndOrderBy_Provider")
    void selectLatestBuildIdsByNameCaseInsensitive_FilterDirectionAndOrderBy(
            @NonNull String name,
            @NonNull String orderBy,
            @NonNull String sortOrder,
            @NonNull String firstBuildNumber,
            @NonNull String secondBuildNumber) throws SQLException {
        BuildsSearchFilter filter = BuildsSearchFilter.builder()
                .name(name)
                .orderBy(orderBy)
                .sortOrder(sortOrder)
                .limit(10)
                .offset(0L)
                .build();
        List<BuildIdEntity> filteredArtifactsBundles = buildsDao.selectLatestBuildIdsByNameCaseInsensitive(filter);
        assertEquals(2, filteredArtifactsBundles.size());
        assertEquals(firstBuildNumber, filteredArtifactsBundles.get(0).getBuildNumber());
        assertEquals(secondBuildNumber, filteredArtifactsBundles.get(1).getBuildNumber());
    }

    @Test
    void getBuildsCountFromSpecificRepo() throws SQLException {
        createBuilds();
        assertEquals(3, buildsDao.getBuildsCountFromSpecificRepo(ALTERNATIVE_BUILD_INFO_REPO));
        // 16 baseline + 3 new RTDEV-71186 fixtures, all in artifactory-build-info.
        assertEquals(19, buildsDao.getBuildsCountFromSpecificRepo(BUILDINFO_REPO_KEY));
    }

    @Test
    void getUniqueBuildsCountFromSpecificRepo() throws SQLException {
        createBuilds();
        assertEquals(2, buildsDao.getUniqueBuildsCountFromSpecificRepo(ALTERNATIVE_BUILD_INFO_REPO));
        // 8 baseline + 3 new RTDEV-71186 fixtures (each with a unique build name).
        assertEquals(11, buildsDao.getUniqueBuildsCountFromSpecificRepo(BUILDINFO_REPO_KEY));
    }

    @Test
    void getTotalBuildsCount() throws SQLException {
        createBuilds();
        // 25 baseline + 3 new RTDEV-71186 fixtures.
        assertEquals(28, buildsDao.getTotalBuildsCount());
    }

    @ParameterizedTest
    @MethodSource("getBuildNames_Provider")
    void getBuildNames(
            @NonNull List<String> repoKeys,
            @Nullable String buildName,
            long offset,
            long limit,
            @NonNull List<BuildNameAndRepo> expectedBuildNames) throws SQLException {
        buildsDao.createBuild(createBuild12());
        buildsDao.createBuild(createBuild13());
        buildsDao.createBuild(createBuild15());
        createBuild103();
        List<BuildNameAndRepo> result = buildsDao.getBuildNames(repoKeys, buildName, offset, limit);
        assertEquals(expectedBuildNames, result);
    }

    @ParameterizedTest
    @MethodSource("getBuildNumbers_Provider")
    void getBuildNumbers(
            long startedBefore,
            long startedAfter,
            long limit,
            long bundlesLimit,
            @NonNull List<BuildNumberRecord> expectedBuildNumbers) throws SQLException {
        createBuilds();
        List<BuildNumberRecord> result = buildsDao.getBuildNumbers(
                BUILDINFO_REPO_KEY, "ba", startedBefore, false, startedAfter, limit, bundlesLimit);
        assertEquals(expectedBuildNumbers, result);
    }

    @ParameterizedTest
    @MethodSource("getBuildNumbers_ExcludeInReleaseBundle_Provider")
    void getBuildNumbers_ExcludeInReleaseBundles(
            long startedBefore,
            long startedAfter,
            long limit,
            @NonNull List<BuildNumberRecord> expectedBuildNumbers) throws SQLException {
        createBuilds();
        List<BuildNumberRecord> result = buildsDao.getBuildNumbers(
                BUILDINFO_REPO_KEY, "ba", startedBefore, true, startedAfter, limit, 10);
        assertEquals(expectedBuildNumbers, result);
    }

    @ParameterizedTest
    @MethodSource("getBuildNumbers_ExcludeInReleaseBundleWIthProjects_Provider")
    void getBuildNumbers_ExcludeInReleaseBundlesB(
            @NonNull String repoKey,
            long startedBefore,
            long startedAfter,
            boolean excludeInReleaseBundles,
            long limit,
            @NonNull List<BuildNumberRecord> expectedBuildNumbers) throws SQLException {
        createBuilds();
        List<BuildNumberRecord> result = buildsDao.getBuildNumbers(
                repoKey, "bb", startedBefore, excludeInReleaseBundles, startedAfter, limit, 10);
        assertEquals(expectedBuildNumbers, result);
    }

    @Test
    void getBuildNumbers_Pagination() throws SQLException {
        createBuilds();
        List<BuildNumberRecord> result = buildsDao.getBuildNumbers(
                BUILDINFO_REPO_KEY, "dd", 1349020000001L, false, 0, 2, 3);
        List<BuildNumberRecord> expected = List.of(
                new BuildNumberRecord(BUILDINFO_REPO_KEY, "dd", "1", 1349007000000L, "me", List.of()),
                new BuildNumberRecord(BUILDINFO_REPO_KEY, "dd", "2", 1349008000000L, "me",
                        List.of(
                                new BuildNumberBundleRecord("proj-release-bundles-v2", "rb1", "2.0.0"),
                                new BuildNumberBundleRecord("proj-release-bundles-v2", "rb2", "1.0.0"),
                                new BuildNumberBundleRecord(DEFAULT_RBV2_REPO, "rb2", "1.0.0")
                        )));
        assertEquals(expected, result);
        result = buildsDao.getBuildNumbers(BUILDINFO_REPO_KEY, "dd", 1349020000001L, false, 1349008000000L, 2, 3);
        expected = List.of(
                new BuildNumberRecord(BUILDINFO_REPO_KEY, "dd", "3", 1349009000000L, "me",
                        List.of(
                                new BuildNumberBundleRecord("proj-release-bundles-v2", "rb2", "3.0.0"),
                                new BuildNumberBundleRecord(DEFAULT_RBV2_REPO, "rb2", "3.0.0")
                        )),
                new BuildNumberRecord(BUILDINFO_REPO_KEY, "dd", "3", 1349010000000L, "me",
                        List.of(
                                new BuildNumberBundleRecord(DEFAULT_RBV2_REPO, "rb2", "1.0.0")
                        )));
        assertEquals(expected, result);
        result = buildsDao.getBuildNumbers(BUILDINFO_REPO_KEY, "dd", 1349020000001L, false, 1349010000000L, 2, 3);
        expected = List.of(
                new BuildNumberRecord(BUILDINFO_REPO_KEY, "dd", "4", 1349020000000L, "me",
                        List.of(
                                new BuildNumberBundleRecord("proj-release-bundles-v2", "rb2", "2.0.0"),
                                new BuildNumberBundleRecord(DEFAULT_RBV2_REPO, "rb2", "1.0.0"),
                                new BuildNumberBundleRecord(DEFAULT_RBV2_REPO, "rb2", "2.0.0")
                        )));
        assertEquals(expected, result);
        result = buildsDao.getBuildNumbers(BUILDINFO_REPO_KEY, "dd", 1349020000001L, false, 1349020000000L, 2, 3);
        expected = List.of();
        assertEquals(expected, result);
    }

    @ParameterizedTest
    @MethodSource("selectBuildNumbersNotInRepo_Provider")
    void selectBuildNumbersNotInRepo(
            String name,
            String number,
            String repoKey,
            List<String> expectedRepoKeys) throws SQLException {
        createBuilds();
        List<String> result = buildsDao.selectBuildNumbersNotInRepo(name, number, repoKey);
        assertEquals(expectedRepoKeys, result);
    }

    @Test
    void getBuildStoringRepo() throws SQLException {
        createBuilds();
        assertEquals(BUILDINFO_REPO_KEY, buildsDao.getBuildStoringRepo("ba", "1", 1349000000000L));
        assertEquals(ALTERNATIVE_BUILD_INFO_REPO, buildsDao.getBuildStoringRepo("bb", "1", 1349001000000L));
        assertNull(buildsDao.getBuildStoringRepo("not_exist", "1", 1349001000000L));
    }

    @Test
    void getBuildModule() throws SQLException {
        createBuilds();
        List<PublishedModule> publishedModules = buildsDao.getBuildModule(
                "ba", "1", String.valueOf(1349000000000L), BUILDINFO_REPO_KEY);
        assertEquals(2, publishedModules.size());
        if (publishedModules.get(0).getType() != null) {
            Collections.swap(publishedModules, 0, 1);
        }
        PublishedModule buildTypeModule = publishedModules.get(1);
        assertEquals("build", buildTypeModule.getType());
        assertEquals("b", buildTypeModule.getBuildId());
        assertEquals("1", buildTypeModule.getBuildNumber());
        PublishedModule module = publishedModules.getFirst();
        assertNull(module.getType());
        assertNull(module.getBuildId());
        assertNull(module.getBuildNumber());
        assertEquals("2", module.getNumOfArtifact());
        assertEquals("1", module.getNumOfDependencies());
        assertEquals("ba:moda1", module.getId());
        publishedModules = buildsDao.getBuildModule(
                "ba", "1", String.valueOf(1349000000000L), ALTERNATIVE_BUILD_INFO_REPO);
        assertEquals(0, publishedModules.size());
    }

    @Test
    void getModuleArtifact() throws SQLException {
        createBuilds();
        List<ModuleArtifact> moduleArtifacts = buildsDao.getModuleArtifact(
                "ba", "1", String.valueOf(1349000000000L), "ba:moda1", BUILDINFO_REPO_KEY, true);
        assertEquals(2, moduleArtifacts.size());
        for (ModuleArtifact moduleArtifact : moduleArtifacts) {
            if ("ba1mod1-art1".equals(moduleArtifact.getName())) {
                assertEquals("acab88fc2a043c2479a6de676a2f8179e9ea2167", moduleArtifact.getSha1());
                assertEquals("npm-local", moduleArtifact.getRepoKey());
                assertEquals("test_pack1/-/test_pack-1.0.0.tgz", moduleArtifact.getPath());
            } else {
                assertEquals("ba1mod1-art2", moduleArtifact.getName());
            }
        }
        moduleArtifacts = buildsDao.getModuleArtifact(
                "ba", "1", String.valueOf(1349000000000L), "ba:moda1", ALTERNATIVE_BUILD_INFO_REPO, true);
        assertEquals(0, moduleArtifacts.size());
    }

    @Test
    void getModuleArtifact_WithRepoKey() throws SQLException {
        createBuilds();
        List<ModuleArtifact> moduleArtifacts = buildsDao.getModuleArtifact(
                "cc", "1", String.valueOf(1349005000000L), "cc:moda1", BUILDINFO_REPO_KEY, false);
        assertEquals(1, moduleArtifacts.size());
        ModuleArtifact moduleArtifact = moduleArtifacts.getFirst();
        assertEquals("cc2mod4-art1", moduleArtifact.getName());
        assertEquals("acab88fc2a043c2479a6de676a2f8179e9ea2167", moduleArtifact.getSha1());
        assertEquals("npm-local", moduleArtifact.getRepoKey());
        assertEquals("test_pack5/-/test_pack-1.0.0.tgz", moduleArtifact.getPath());
        moduleArtifacts = buildsDao.getModuleArtifact(
                "cc", "1", String.valueOf(1349006000000L), "cc:moda1", ALTERNATIVE_BUILD_INFO_REPO, false);
        assertEquals(1, moduleArtifacts.size());
        moduleArtifact = moduleArtifacts.getFirst();
        assertEquals("cc2mod4-art1", moduleArtifact.getName());
        assertEquals("acab88fc2a043c2479a6de676a2f8179e9ea2167", moduleArtifact.getSha1());
        assertEquals("npm-local", moduleArtifact.getRepoKey());
        assertEquals("test_pack4/-/test_pack-1.0.0.tgz", moduleArtifact.getPath());
    }

    @Test
    void selectModuleDependencies() throws SQLException {
        createBuilds();
        List<ModuleDependency> moduleDependencies = buildsDao.selectModuleDependencies(
                "ba", "1", String.valueOf(1349000000000L), BUILDINFO_REPO_KEY, "ba:moda1");
        assertEquals(1, moduleDependencies.size());
        ModuleDependency dependency = moduleDependencies.getFirst();
        assertEquals("bb1mod1-art1", dependency.getName());
        assertEquals("ccab88fc2a043c2479a6de676a2f8179e9ea2167", dependency.getSha1());
        moduleDependencies = buildsDao.selectModuleDependencies(
                "ba", "1", String.valueOf(1349000000000L), ALTERNATIVE_BUILD_INFO_REPO, "ba:moda1");
        assertEquals(0, moduleDependencies.size());
    }

    @Test
    void getModuleDependencyForDiffWithPaging() throws SQLException {
        createBuilds();
        BuildParams buildParams = new BuildParams("ba:moda1", "1", "2",
                String.valueOf(1349002000000L), String.valueOf(1349000000000L), "ba", BUILDINFO_REPO_KEY);
        List<ModuleDependency> moduleDependencies = buildsDao.getModuleDependencyForDiffWithPaging(buildParams);
        assertEquals(2, moduleDependencies.size());
        ModuleDependency dependency1 = moduleDependencies.getFirst();
        assertEquals("ba2mod4-art1", dependency1.getName());
        assertEquals("dcab88fc2a043c2479a6de676a2f8179e9ea2167", dependency1.getSha1());
        ModuleDependency dependency2 = moduleDependencies.get(1);
        assertEquals("bb1mod1-art1", dependency2.getName());
        assertEquals("ccab88fc2a043c2479a6de676a2f8179e9ea2167", dependency2.getSha1());
        buildParams.setBuildRepo(ALTERNATIVE_BUILD_INFO_REPO);
        moduleDependencies = buildsDao.getModuleDependencyForDiffWithPaging(buildParams);
        assertEquals(0, moduleDependencies.size());
    }

    @Test
    void getModuleArtifactsForDiffWithPaging() throws SQLException {
        createBuilds();
        BuildParams buildParams = new BuildParams("ba:moda1", "1", "2",
                String.valueOf(1349002000000L), String.valueOf(1349000000000L), "ba", BUILDINFO_REPO_KEY);
        List<ModuleArtifact> moduleArtifacts = buildsDao.getModuleArtifactsForDiffWithPaging(buildParams, true);
        assertEquals(6, moduleArtifacts.size());
        buildParams.setBuildRepo(ALTERNATIVE_BUILD_INFO_REPO);
        moduleArtifacts = buildsDao.getModuleArtifactsForDiffWithPaging(buildParams, false);
        assertEquals(0, moduleArtifacts.size());
    }

    @Test
    void getPrevBuildsList() throws SQLException {
        createBuilds();
        List<GeneralBuild> buildList =
                buildsDao.getPrevBuildsList("ba", String.valueOf(1349002000000L), BUILDINFO_REPO_KEY);
        assertEquals(1, buildList.size());
        assertEquals(1349000000000L, buildList.getFirst().getBuildDate());
        buildList = buildsDao.getPrevBuildsList("ba", String.valueOf(1349002000000L), ALTERNATIVE_BUILD_INFO_REPO);
        assertEquals(0, buildList.size());
        buildList = buildsDao.getPrevBuildsList("ba", String.valueOf(1349000000000L), BUILDINFO_REPO_KEY);
        assertEquals(0, buildList.size());
    }

    @Test
    void getLatestBuild() throws SQLException {
        createBuilds();
        BuildEntity buildEntity = buildsDao.getLatestBuild("ba", BUILDINFO_REPO_KEY);
        assertEquals("ba", buildEntity.getBuildName());
        assertEquals("2", buildEntity.getBuildNumber());
        assertEquals(1349111111111L, buildEntity.getBuildDate());
        assertEquals(BUILDINFO_REPO_KEY, buildEntity.getRepo());
        assertEquals(0, buildEntity.getPromotions().size());
        assertEquals(0, buildEntity.getProperties().size());
        buildEntity = buildsDao.getLatestBuild("ba", ALTERNATIVE_BUILD_INFO_REPO);
        assertNull(buildEntity);
    }

    @Test
    void isImmutable() throws SQLException {
        BuildEntity newBuild = createBuild16();
        buildsDao.createBuild(newBuild);
        assertFalse(buildsDao.isImmutable(newBuild.getBuildName(), newBuild.getBuildNumber(), newBuild.getBuildDate()));
    }

    @Test
    void deleteBuild() throws SQLException {
        BuildEntity build11 = createBuild11();
        buildsDao.createBuild(build11);
        assertTrue(build11.isIdentical(buildsDao.getBuild(11L)));
        doDelete();
    }

    @Test
    void createBuild_SameId() {
        BuildEntity badBuild = new BuildEntity(1L, "bad", "1", 2L, null, 1234L, "me", 0L, null, null);
        badBuild.setProperties(new HashSet<>());
        badBuild.setPromotions(new HashSet<>());
        assertThrows(SQLException.class, () -> buildsDao.createBuild(badBuild));
    }

    @Test
    void findBuildsForArtifactChecksum_WithBuildRepoKey() throws SQLException {
        Collection<BuildEntity> builds = buildsDao.findBuildsForArtifactChecksum(
                SHA1, "acab88fc2a043c2479a6de676a2f8179e9ea2167", ALTERNATIVE_BUILD_INFO_REPO);
        assertEquals(2, builds.size());
        builds = buildsDao.findBuildsForArtifactChecksum(
                SHA1, "acab88fc2a043c2479a6de676a2f8179e9ea2167", NOT_EXISTING_BUILD_REPO);
        assertEquals(0, builds.size());
    }

    @Test
    void findBuildsForArtifactChecksum_Sha1() throws SQLException {
        Collection<BuildEntity> builds = buildsDao.findBuildsForArtifactChecksum(SHA1,
                "acab88fc2a043c2479a6de676a2f8179e9ea2167", BUILDINFO_REPO_KEY);
        assertEquals(3, builds.size());
        for (BuildEntity build : builds) {
            assertTrue(Set.of(1L, 3L, 7L).contains(build.getBuildId()));
        }
    }

    @Test
    void findBuildsForArtifactChecksum_Md5() throws SQLException {
        Collection<BuildEntity> builds = buildsDao.findBuildsForArtifactChecksum(
                MD5, "b02a360ecad98a34b59863c1e65bcf71", BUILDINFO_REPO_KEY);
        assertEquals(3, builds.size());
        for (BuildEntity build : builds) {
            assertTrue(Set.of(1L, 3L, 7L).contains(build.getBuildId()));
        }
    }

    @Test
    void findBuildsForDependencyChecksum_sha1() throws SQLException {
        Collection<BuildEntity> builds = buildsDao.findBuildsForDependencyChecksum(
                SHA1, "ccab88fc2a043c2479a6de676a2f8179e9ea2167", BUILDINFO_REPO_KEY);
        assertEquals(1, builds.size());
        for (BuildEntity build : builds) {
            assertTrue(Set.of(1L, 2L).contains(build.getBuildId()));
        }
    }

    @Test
    void findBuildsForDependencyChecksum_WithBuildRepoKey() throws SQLException {
        Collection<BuildEntity> builds = buildsDao.findBuildsForDependencyChecksum(
                SHA1, "ccab88fc2a043c2479a6de676a2f8179e9ea2167", ALTERNATIVE_BUILD_INFO_REPO);
        assertEquals(1, builds.size());
        builds = buildsDao.findBuildsForDependencyChecksum(SHA1,
                "ccab88fc2a043c2479a6de676a2f8179e9ea2167", NOT_EXISTING_BUILD_REPO);
        assertEquals(0, builds.size());
    }

    @Test
    void findBuildsForDependencyChecksum_Md5() throws SQLException {
        Collection<BuildEntity> builds = buildsDao.findBuildsForDependencyChecksum(
                MD5, "d02a360ecad98a34b59863c1e65bcf71", BUILDINFO_REPO_KEY);
        assertEquals(2, builds.size());
        for (BuildEntity build : builds) {
            assertTrue(Set.of(3L, 7L).contains(build.getBuildId()));
        }
    }

    @Test
    void getModuleArtifact_WithSameSha1DifferentNames() throws SQLException {
        // Get the module artifacts for our test build
        List<ModuleArtifact> moduleArtifacts = buildsDao.getModuleArtifact(
                "test-same-sha1-build", "1", String.valueOf(1349100000000L), "test-module", BUILDINFO_REPO_KEY, false);

        // Verify we have two artifacts in the result
        assertEquals(2, moduleArtifacts.size(), "Should have found two artifacts with the same SHA1");

        // Create a map of artifacts by name for easier verification
        Map<String, ModuleArtifact> artifactsByName = moduleArtifacts.stream()
                .collect(Collectors.toMap(ModuleArtifact::getName, artifact -> artifact));

        // Verify the first artifact has the correct path
        ModuleArtifact libA = artifactsByName.get("lib-a-1.0.jar");
        assertNotNull(libA, "Should have found the lib-a-1.0.jar artifact");
        assertEquals("maven-local", libA.getRepoKey(), "The repository key should match");
        assertEquals("org/example/lib-a/1.0/lib-a-1.0.jar", libA.getPath(), "The path should match for lib-a");
        // SHA1 values from database may contain trailing spaces, so trim before comparing
        assertEquals("aaaaaa1111111122222222333333334444aaaa", libA.getSha1().trim(), "The SHA1 should match");

        // Verify the second artifact has the correct path
        ModuleArtifact libB = artifactsByName.get("lib-b-2.0.jar");
        assertNotNull(libB, "Should have found the lib-b-2.0.jar artifact");
        assertEquals("maven-local", libB.getRepoKey(), "The repository key should match");
        assertEquals("org/example/lib-b/2.0/lib-b-2.0.jar", libB.getPath(), "The path should match for lib-b");
        assertEquals("aaaaaa1111111122222222333333334444aaaa", libB.getSha1().trim(), "The SHA1 should match");
    }

    /**
     * RTDEV-71186 customer repro: a single module publishes two artifacts with the same name (test.txt),
     * same type, same SHA1, but at different repo paths. The Published Modules tab must show both rows
     * (today's pre-fix code returns only one because getPaginatedArtifact's SELECT DISTINCT collapses them
     * and the matching loop's findFirst then drops the second matching node).
     */
    @Test
    void getModuleArtifact_WithSameSha1SameNameDifferentPaths() throws SQLException {
        List<ModuleArtifact> moduleArtifacts = buildsDao.getModuleArtifact(
                "test-same-sha1-same-name-build", "1", String.valueOf(1349200000000L),
                "default-module", BUILDINFO_REPO_KEY, false);

        assertEquals(2, moduleArtifacts.size(),
                "Should have fanned out to two ModuleArtifacts for the same-name+SHA artifacts at different paths");

        Set<String> paths = moduleArtifacts.stream().map(ModuleArtifact::getPath).collect(Collectors.toSet());
        assertEquals(Set.of("build6/moduleA/test.txt", "build6/moduleB/test.txt"), paths,
                "Both deployment paths should be returned, preserving the customer's distinct upload locations");

        for (ModuleArtifact artifact : moduleArtifacts) {
            assertEquals("test.txt", artifact.getName(), "All artifacts should have the same name");
            assertEquals("generic", artifact.getType(), "All artifacts should have the same type");
            assertEquals("generic-local", artifact.getRepoKey(), "All artifacts should be in generic-local");
            assertEquals("cccccc3333333344444444555555556666cccc", artifact.getSha1().trim(),
                    "All artifacts should share the same SHA1");
        }
    }

    /**
     * RTDEV-71186 multi-module non-regression: two distinct modules each publish a single artifact with
     * identical name+SHA. The cardinality cap (occurrences from build_artifacts) prevents fan-out from
     * leaking the sibling-module path into either module's tab. Each tab must return exactly one row.
     * (Note: the schema cannot disambiguate which node belongs to which module when SHA+name collide
     * across modules; the assertion is on cardinality and absence of phantom cross-module rows, not on
     * which specific path each tab picks.)
     */
    @Test
    void getModuleArtifact_MultiModule_SameNameSameSha_NoPhantoms() throws SQLException {
        List<ModuleArtifact> modAArtifacts = buildsDao.getModuleArtifact(
                "test-multi-module-symmetric", "1", String.valueOf(1349210000000L),
                "mod-a", BUILDINFO_REPO_KEY, false);
        List<ModuleArtifact> modBArtifacts = buildsDao.getModuleArtifact(
                "test-multi-module-symmetric", "1", String.valueOf(1349210000000L),
                "mod-b", BUILDINFO_REPO_KEY, false);

        assertEquals(1, modAArtifacts.size(),
                "mod-a has exactly one build_artifacts row -- the cap must keep the result at 1");
        assertEquals(1, modBArtifacts.size(),
                "mod-b has exactly one build_artifacts row -- the cap must keep the result at 1");

        Set<String> validPaths = Set.of(
                "org/example/moduleA/1.0/lib.jar",
                "org/example/moduleB/1.0/lib.jar");
        assertTrue(validPaths.contains(modAArtifacts.getFirst().getPath()),
                "mod-a path must be one of the two real deployment paths, not a phantom");
        assertTrue(validPaths.contains(modBArtifacts.getFirst().getPath()),
                "mod-b path must be one of the two real deployment paths, not a phantom");
    }

    /**
     * RTDEV-71186 multi-module asymmetric: module A has two build_artifacts rows for (x.jar, jar, sha=E),
     * module B has one. Three matching nodes exist (two under A's hierarchy, one under B's). The cap must
     * yield 2 rows for A and 1 row for B -- never 3 for either tab.
     */
    @Test
    void getModuleArtifact_MultiModule_Asymmetric() throws SQLException {
        List<ModuleArtifact> modAArtifacts = buildsDao.getModuleArtifact(
                "test-multi-module-asymmetric", "1", String.valueOf(1349220000000L),
                "mod-a", BUILDINFO_REPO_KEY, false);
        List<ModuleArtifact> modBArtifacts = buildsDao.getModuleArtifact(
                "test-multi-module-asymmetric", "1", String.valueOf(1349220000000L),
                "mod-b", BUILDINFO_REPO_KEY, false);

        assertEquals(2, modAArtifacts.size(),
                "mod-a has two build_artifacts rows -- the cap yields 2 emissions");
        assertEquals(1, modBArtifacts.size(),
                "mod-b has one build_artifacts row -- the cap keeps the result at 1");

        for (ModuleArtifact artifact : modAArtifacts) {
            assertEquals("x.jar", artifact.getName());
            assertEquals("jar", artifact.getType());
            assertEquals("libs-release-local", artifact.getRepoKey());
            assertEquals("eeeeee5555555566666666777777778888eeee", artifact.getSha1().trim());
        }
        for (ModuleArtifact artifact : modBArtifacts) {
            assertEquals("x.jar", artifact.getName());
            assertEquals("jar", artifact.getType());
            assertEquals("libs-release-local", artifact.getRepoKey());
            assertEquals("eeeeee5555555566666666777777778888eeee", artifact.getSha1().trim());
        }
    }

    @Test
    void selectBuildArtifacts() throws SQLException {
        // Build "ba" number "1" has two artifacts:
        // - Artifact with SHA1 "acab..." matches 5 nodes in npm-local (matched results)
        // - Artifact with SHA1 "bcab..." has no matching node (unmatched result with null node fields)
        // LEFT OUTER JOIN returns rows even when nodes don't match, producing null node fields
        Build build = new Build();
        build.setName("ba");
        build.setNumber("1");
        BuildWrapper buildWrapper = new BuildWrapper(build, BUILDINFO_REPO_KEY);
        List<BuildFileInfo> results = buildArtifactsDao.selectBuildArtifacts(
                buildWrapper, List.of("npm-local", "maven-local"), null);

        if (getDatabaseType() == DbType.POSTGRESQL) {
            assertEquals(1, results.size(), "Should find 1 matched nodes from npm-local");
        } else {
            // Should return 5 matched nodes (the sourceRepos filter excludes unmatched artifacts with null repos)
            assertEquals(5, results.size(), "Should find 5 matched nodes from npm-local");

            // Verify all results are from npm-local repo with correct SHA1
            for (BuildFileInfo nodeInfo : results) {
                assertEquals("npm-local", nodeInfo.getRepoKey(), "All nodes should be from npm-local repo");
                assertEquals("acab88fc2a043c2479a6de676a2f8179e9ea2167", nodeInfo.getActualSha1().trim(),
                        "All nodes should have the correct SHA1");
                assertNotNull(nodeInfo.getRelativePath(), "Nodes should have non-null path");
                assertNotNull(nodeInfo.getName(), "Nodes should have non-null name");
            }
        }
    }

    @Test
    void selectDependencyNodesBySha1() throws SQLException {
        String sha1 = "acab88fc2a043c2479a6de676a2f8179e9ea2167";
        List<String> sha1s = List.of(sha1);
        Map<String, List<BuildNode>> nodesBySha1 = buildsDao.selectDependencyNodesBySha1(sha1s);
        assertTrue(nodesBySha1.containsKey(sha1), "Should find nodes for SHA1");
        assertFalse(nodesBySha1.get(sha1).isEmpty());
    }

    private void assertAnyBuild(@NonNull BuildEntity b) {
        assertNotNull(b);
        switch ((int) b.getBuildId()) {
            case 1:
                assertBuild1(b);
                break;
            case 2:
                assertBuild2(b);
                break;
            case 3:
                assertBuild3(b);
                break;
            case 4:
                assertBuild4(b);
                break;
            case 5:
                assertBuild5(b);
                break;
            case 6:
                assertBuild6(b);
                break;
            default:
                fail("Build with id " + b.getBuildId() + " is not known!");
        }
    }

    private void assertBuild1(@NonNull BuildEntity b) {
        assertEquals(1L, b.getBuildId());
        assertEquals("ba", b.getBuildName());
        assertEquals("1", b.getBuildNumber());
        assertEquals(1349000000000L, b.getBuildDate());
        assertNull(b.getCiUrl());
        assertEquals(1350000000000L, b.getCreated());
        assertEquals("me", b.getCreatedBy());
        assertEquals(1350000000001L, b.getModified());
        assertEquals("not-me", b.getModifiedBy());
        ImmutableSet<BuildProperty> props = b.getProperties();
        assertEquals(2, props.size());
        BuildProperty bp1 = new BuildProperty(1L, 1L, "start", "0");
        BuildProperty bp2 = new BuildProperty(2L, 1L, "status", "bad");
        assertTrue(props.contains(bp1));
        assertTrue(props.contains(bp2));
        for (BuildProperty prop : props) {
            if (prop.getPropId() == 1L) {
                assertTrue(prop.isIdentical(bp1));
            }
            if (prop.getPropId() == 2L) {
                assertTrue(prop.isIdentical(bp2));
            }
        }
        assertTrue(b.getPromotions().isEmpty());
    }

    private void assertBuild2(@NonNull BuildEntity b) {
        assertEquals(2L, b.getBuildId());
        assertEquals("bb", b.getBuildName());
        assertEquals("1", b.getBuildNumber());
        assertEquals(1349001000000L, b.getBuildDate());
        assertEquals("http://myserver/jenkins/bb/1", b.getCiUrl());
        assertEquals(1350001000000L, b.getCreated());
        assertEquals("me", b.getCreatedBy());
        assertEquals(0L, b.getModified());
        assertNull(b.getModifiedBy());
        ImmutableSet<BuildProperty> props = b.getProperties();
        assertEquals(2, props.size());
        BuildProperty bp1 = new BuildProperty(3L, 2L, "start", "1");
        BuildProperty bp2 = new BuildProperty(4L, 2L, "status", "not-too-bad");
        assertTrue(props.contains(bp1));
        assertTrue(props.contains(bp2));
        for (BuildProperty prop : props) {
            if (prop.getPropId() == 1L) {
                assertTrue(prop.isIdentical(bp1));
            }
            if (prop.getPropId() == 2L) {
                assertTrue(prop.isIdentical(bp2));
            }
        }
        assertTrue(b.getPromotions().isEmpty());
    }

    private void assertBuild3(@NonNull BuildEntity b) {
        assertEquals(3L, b.getBuildId());
        assertEquals("ba", b.getBuildName());
        assertEquals("2", b.getBuildNumber());
        assertEquals(1349002000000L, b.getBuildDate());
        assertNull(b.getCiUrl());
        assertEquals(1350002000000L, b.getCreated());
        assertEquals("me", b.getCreatedBy());
        assertEquals(0L, b.getModified());
        assertNull(b.getModifiedBy());
        assertTrue(b.getProperties().isEmpty());
        assertEquals(1, b.getPromotions().size());
        BuildPromotionStatus promotionStatus = new BuildPromotionStatus(
                3L, 1350012000000L, "me", "dead", null, "bad stuff", null);
        BuildPromotionStatus found = b.getPromotions().first();
        assertTrue(found.isIdentical(promotionStatus));
    }

    private void assertBuild4(@NonNull BuildEntity b) {
        assertEquals(4L, b.getBuildId());
        assertEquals("bb", b.getBuildName());
        assertEquals("2", b.getBuildNumber());
        assertEquals(1349003000000L, b.getBuildDate());
        assertEquals("http://myserver/jenkins/bb/2", b.getCiUrl());
        assertEquals(1350003000000L, b.getCreated());
        assertEquals("me", b.getCreatedBy());
        assertEquals(0L, b.getModified());
        assertNull(b.getModifiedBy());
        assertTrue(b.getProperties().isEmpty());
        ImmutableSortedSet<BuildPromotionStatus> promotions = b.getPromotions();
        assertEquals(3, promotions.size());
        ImmutableSortedSet<BuildPromotionStatus> expectedStatus = ImmutableSortedSet.of(
                new BuildPromotionStatus(
                        4L, 1350003000000L, null, "staging", null, null, null),
                new BuildPromotionStatus(
                        4L, 1350013000000L, "promoter", "promoted", "qa-local", "sending to QA", "me"),
                new BuildPromotionStatus(
                        4L, 1350023000000L, "tester", "rollback", "lost-local", "Refused by QA", null)
        );
        assertEquals(expectedStatus, promotions);
        UnmodifiableIterator<BuildPromotionStatus> it1 = expectedStatus.iterator();
        UnmodifiableIterator<BuildPromotionStatus> promosIt = promotions.iterator();
        for (int i = 0; i < 3; i++) {
            assertTrue(promosIt.next().isIdentical(it1.next()));
        }
    }

    private void assertBuild5(@NonNull BuildEntity b) {
        assertEquals(5L, b.getBuildId());
        assertEquals("ba", b.getBuildName());
        assertEquals("3", b.getBuildNumber());
        assertEquals(1349004000000L, b.getBuildDate());
        assertNull(b.getCiUrl());
        assertEquals(1350004000000L, b.getCreated());
        assertEquals("me", b.getCreatedBy());
        assertEquals(0L, b.getModified());
        assertNull(b.getModifiedBy());
        ImmutableSet<BuildProperty> props = b.getProperties();
        assertEquals(2, props.size());
        BuildProperty bp1 = new BuildProperty(5L, 5L, "start", "4");
        BuildProperty bp2 = new BuildProperty(6L, 5L, "status", "good");
        assertTrue(props.contains(bp1));
        assertTrue(props.contains(bp2));
        for (BuildProperty prop : props) {
            if (prop.getPropId() == 1L) {
                assertTrue(prop.isIdentical(bp1));
            }
            if (prop.getPropId() == 2L) {
                assertTrue(prop.isIdentical(bp2));
            }
        }
        ImmutableSortedSet<BuildPromotionStatus> promotions = b.getPromotions();
        assertEquals(2, promotions.size());
        BuildPromotionStatus first = new BuildPromotionStatus(
                5L, 1350024000000L, null, "staging", null, null, "jenkins");
        BuildPromotionStatus second = new BuildPromotionStatus(
                5L, 1350034000000L, "promoter", RELEASED, "public", "Full release", "rel");
        assertTrue(promotions.first().isIdentical(first));
        assertTrue(promotions.last().isIdentical(second));
    }

    private void assertBuild6(@NonNull BuildEntity b) {
        assertEquals(6L, b.getBuildId());
        assertEquals("bb", b.getBuildName());
        assertEquals("3", b.getBuildNumber());
        assertEquals(1349004000000L, b.getBuildDate());
        assertEquals("http://myserver/jenkins/bb/3", b.getCiUrl());
        assertEquals(1350004000000L, b.getCreated());
        assertEquals("me", b.getCreatedBy());
        assertEquals(0L, b.getModified());
        assertNull(b.getModifiedBy());
        assertTrue(b.getProperties().isEmpty());
    }

    @NonNull
    private List<BuildNumberBundleRecord> getExpectedBundlesOfBa1() {
        return List.of(
                new BuildNumberBundleRecord("proj-release-bundles-v2", "rb1", "1.0.0"),
                new BuildNumberBundleRecord(DEFAULT_RBV2_REPO, "rb1", "1.0.0"),
                new BuildNumberBundleRecord(DEFAULT_RBV2_REPO, "rb1", "2.0.0"),
                new BuildNumberBundleRecord(DEFAULT_RBV2_REPO, "rb1", "3.0.0"),
                new BuildNumberBundleRecord(DEFAULT_RBV2_REPO, "rb2", "1.0.0"));
    }

    @NonNull
    public Stream<Arguments> selectLatestBuildIdsByNameCaseInsensitive_FilterDirectionAndOrderBy_Provider() {
        return Stream.of(
                Arguments.of("bb", "date", "desc", "3", "2"),
                Arguments.of("bb", "date", "asc", "2", "3"),
                Arguments.of("bb", "number", "asc", "2", "3"),
                Arguments.of("bb", "number", "desc", "3", "2"),
                Arguments.of("bb", "bad!", "left", "3", "2"),
                Arguments.of("bb", "(select*from(select(sleep(8)))a)", "left", "3", "2")
        );
    }

    @NonNull
    Stream<Arguments> getBuildNames_Provider() {
        return Stream.of(
                Arguments.of(
                        List.of(), null,
                        0, 3,
                        List.of(
                                new BuildNameAndRepo("ba", BUILDINFO_REPO_KEY),
                                new BuildNameAndRepo("bb", BUILDINFO_REPO_KEY),
                                new BuildNameAndRepo("c2", BUILDINFO_REPO_KEY)
                        )
                ),
                Arguments.of(
                        List.of(), null,
                        3, 2,
                        List.of(
                                new BuildNameAndRepo("c5", BUILDINFO_REPO_KEY),
                                new BuildNameAndRepo("cc", BUILDINFO_REPO_KEY)
                        )
                ),
                Arguments.of(
                        List.of(BUILDINFO_REPO_KEY), null,
                        0, 3,
                        List.of(
                                new BuildNameAndRepo("ba", BUILDINFO_REPO_KEY),
                                new BuildNameAndRepo("bb", BUILDINFO_REPO_KEY),
                                new BuildNameAndRepo("c2", BUILDINFO_REPO_KEY)
                        )
                ),
                Arguments.of(
                        List.of(BUILDINFO_REPO_KEY), null,
                        3, 2,
                        List.of(
                                new BuildNameAndRepo("c5", BUILDINFO_REPO_KEY),
                                new BuildNameAndRepo("cc", BUILDINFO_REPO_KEY)
                        )
                ),
                Arguments.of(
                        List.of(), "c2",
                        0, 3,
                        List.of(
                                new BuildNameAndRepo("c2", BUILDINFO_REPO_KEY)
                        )
                ),
                Arguments.of(
                        List.of(BUILDINFO_REPO_KEY), "c2",
                        0, 3,
                        List.of(
                                new BuildNameAndRepo("c2", BUILDINFO_REPO_KEY)
                        )
                ),
                Arguments.of(
                        List.of(), "c*",
                        0, 3,
                        List.of(
                                new BuildNameAndRepo("c2", BUILDINFO_REPO_KEY),
                                new BuildNameAndRepo("c5", BUILDINFO_REPO_KEY),
                                new BuildNameAndRepo("cc", BUILDINFO_REPO_KEY)
                        )
                ),
                Arguments.of(
                        List.of(BUILDINFO_REPO_KEY), "c*",
                        0, 3,
                        List.of(
                                new BuildNameAndRepo("c2", BUILDINFO_REPO_KEY),
                                new BuildNameAndRepo("c5", BUILDINFO_REPO_KEY),
                                new BuildNameAndRepo("cc", BUILDINFO_REPO_KEY)
                        )
                ),
                Arguments.of(
                        List.of("proj2-build-info"), "d_d",
                        0, 10,
                        List.of(
                                new BuildNameAndRepo("d_d", "proj2-build-info")
                        )
                ),
                Arguments.of(
                        List.of(BUILDINFO_REPO_KEY, "proj2-build-info"), "d_*",
                        0, 10,
                        List.of(
                                new BuildNameAndRepo("d_d", "proj2-build-info"),
                                new BuildNameAndRepo("d_e", "proj2-build-info")
                        )
                ),
                Arguments.of(
                        List.of(BUILDINFO_REPO_KEY, "proj2-build-info"), "d%",
                        0, 10,
                        List.of(
                                new BuildNameAndRepo("d%", "proj2-build-info")
                        )
                )
        );
    }

    @NonNull
    Stream<Arguments> getBuildNumbers_ExcludeInReleaseBundle_Provider() {
        return Stream.of(
                Arguments.of(
                        1349005000000L, 0, 3,
                        List.of(new BuildNumberRecord(BUILDINFO_REPO_KEY, "ba", "2", 1349002000000L, "me", List.of()))
                ),
                Arguments.of(
                        1349005000000L, 1349000000000L, 3,
                        List.of(new BuildNumberRecord(BUILDINFO_REPO_KEY, "ba", "2", 1349002000000L, "me", List.of()))
                ),
                Arguments.of(
                        1349005000000L, 1349000000000L, 1,
                        List.of(new BuildNumberRecord(BUILDINFO_REPO_KEY, "ba", "2", 1349002000000L, "me", List.of()))
                ),
                Arguments.of(
                        1349005000000L, 1349002000000L, 3, List.of()
                )
        );
    }

    @NonNull
    Stream<Arguments> getBuildNumbers_ExcludeInReleaseBundleWIthProjects_Provider() {
        return Stream.of(
                Arguments.of(
                        BUILDINFO_REPO_KEY, 1349005000000L, 0, false, 3,
                        List.of(new BuildNumberRecord(BUILDINFO_REPO_KEY, "bb", "3", 1349004000000L, "me", List.of()))
                ),
                Arguments.of(
                        "project-build-info", 1349005000000L, 0, false, 3,
                        List.of(
                                new BuildNumberRecord("project-build-info", "bb", "1", 1349001000000L, "me", List.of()),
                                new BuildNumberRecord("project-build-info", "bb", "2", 1349003000000L, "me", List.of())
                        )
                )
        );
    }

    @NonNull
    Stream<Arguments> getBuildNumbers_Provider() {
        return Stream.of(
                Arguments.of(
                        1349005000000L, 0, 3, 10,
                        List.of(new BuildNumberRecord(BUILDINFO_REPO_KEY, "ba", "1", 1349000000000L, "me",
                                        getExpectedBundlesOfBa1()),
                                new BuildNumberRecord(BUILDINFO_REPO_KEY, "ba", "2", 1349002000000L, "me", List.of()),
                                new BuildNumberRecord(BUILDINFO_REPO_KEY, "ba", "3", 1349004000000L, "me",
                                        List.of(new BuildNumberBundleRecord(DEFAULT_RBV2_REPO, "rb1", "2.0.0"))))
                ),
                Arguments.of(
                        1349005000000L, 0, 5, 10,
                        List.of(new BuildNumberRecord(BUILDINFO_REPO_KEY, "ba", "1", 1349000000000L, "me",
                                        getExpectedBundlesOfBa1()),
                                new BuildNumberRecord(BUILDINFO_REPO_KEY, "ba", "2", 1349002000000L, "me", List.of()),
                                new BuildNumberRecord(BUILDINFO_REPO_KEY, "ba", "3", 1349004000000L, "me",
                                        List.of(new BuildNumberBundleRecord(DEFAULT_RBV2_REPO, "rb1", "2.0.0"))))
                ),
                Arguments.of(
                        1349005000000L, 0, 2, 10,
                        List.of(new BuildNumberRecord(BUILDINFO_REPO_KEY, "ba", "1", 1349000000000L, "me",
                                        getExpectedBundlesOfBa1()),
                                new BuildNumberRecord(BUILDINFO_REPO_KEY, "ba", "2", 1349002000000L, "me", List.of()))
                ),
                Arguments.of(
                        1349005000000L, 1349002000000L, 2, 10,
                        List.of(new BuildNumberRecord(BUILDINFO_REPO_KEY, "ba", "3", 1349004000000L, "me",
                                List.of(new BuildNumberBundleRecord(DEFAULT_RBV2_REPO, "rb1", "2.0.0"))))
                ),
                Arguments.of(
                        1349005000000L, 0, 3, 3,
                        List.of(new BuildNumberRecord(BUILDINFO_REPO_KEY, "ba", "1", 1349000000000L, "me",
                                        List.of(
                                                new BuildNumberBundleRecord("proj-release-bundles-v2", "rb1", "1.0.0"),
                                                new BuildNumberBundleRecord(DEFAULT_RBV2_REPO, "rb1", "1.0.0"),
                                                new BuildNumberBundleRecord(DEFAULT_RBV2_REPO, "rb1", "2.0.0"))),
                                new BuildNumberRecord(BUILDINFO_REPO_KEY, "ba", "2", 1349002000000L, "me", List.of()),
                                new BuildNumberRecord(BUILDINFO_REPO_KEY, "ba", "3", 1349004000000L, "me",
                                        List.of(new BuildNumberBundleRecord(DEFAULT_RBV2_REPO, "rb1", "2.0.0"))))
                ),
                Arguments.of(
                        1349005000000L, 0, 3, 2,
                        List.of(new BuildNumberRecord(BUILDINFO_REPO_KEY, "ba", "1", 1349000000000L, "me",
                                        List.of(
                                                new BuildNumberBundleRecord("proj-release-bundles-v2", "rb1", "1.0.0"),
                                                new BuildNumberBundleRecord(DEFAULT_RBV2_REPO, "rb1", "1.0.0"))),
                                new BuildNumberRecord(BUILDINFO_REPO_KEY, "ba", "2", 1349002000000L, "me", List.of()),
                                new BuildNumberRecord(BUILDINFO_REPO_KEY, "ba", "3", 1349004000000L, "me",
                                        List.of(new BuildNumberBundleRecord(DEFAULT_RBV2_REPO, "rb1", "2.0.0"))))
                ),
                Arguments.of(
                        1349005000000L, 0, 3, 1,
                        List.of(
                                new BuildNumberRecord(BUILDINFO_REPO_KEY, "ba", "1", 1349000000000L, "me",
                                        List.of(
                                                new BuildNumberBundleRecord("proj-release-bundles-v2", "rb1",
                                                        "1.0.0"))),
                                new BuildNumberRecord(BUILDINFO_REPO_KEY, "ba", "2", 1349002000000L, "me", List.of()),
                                new BuildNumberRecord(BUILDINFO_REPO_KEY, "ba", "3", 1349004000000L, "me",
                                        List.of(new BuildNumberBundleRecord(DEFAULT_RBV2_REPO, "rb1", "2.0.0"))))
                ),
                Arguments.of(
                        1349005000000L, 0, 2, 10,
                        List.of(new BuildNumberRecord(BUILDINFO_REPO_KEY, "ba", "1", 1349000000000L, "me",
                                        getExpectedBundlesOfBa1()),
                                new BuildNumberRecord(BUILDINFO_REPO_KEY, "ba", "2", 1349002000000L, "me", List.of()))
                ),
                Arguments.of(
                        1349005000000L, 0, 2, 2,
                        List.of(new BuildNumberRecord(BUILDINFO_REPO_KEY, "ba", "1", 1349000000000L, "me",
                                        List.of(
                                                new BuildNumberBundleRecord("proj-release-bundles-v2", "rb1", "1.0.0"),
                                                new BuildNumberBundleRecord(DEFAULT_RBV2_REPO, "rb1", "1.0.0"))),
                                new BuildNumberRecord(BUILDINFO_REPO_KEY, "ba", "2", 1349002000000L, "me", List.of()))
                ),
                Arguments.of(
                        1349005000000L, 1349002000000L, 2, 2,
                        List.of(new BuildNumberRecord(BUILDINFO_REPO_KEY, "ba", "3", 1349004000000L, "me",
                                List.of(new BuildNumberBundleRecord(DEFAULT_RBV2_REPO, "rb1", "2.0.0"))))
                ),
                Arguments.of(
                        1349005000000L, 0, 1, 10,
                        List.of(new BuildNumberRecord(BUILDINFO_REPO_KEY, "ba", "1", 1349000000000L, "me",
                                getExpectedBundlesOfBa1()))
                ),
                Arguments.of(
                        1349004000000L, 0, 3, 10,
                        List.of(new BuildNumberRecord(BUILDINFO_REPO_KEY, "ba", "1", 1349000000000L, "me",
                                        getExpectedBundlesOfBa1()),
                                new BuildNumberRecord(BUILDINFO_REPO_KEY, "ba", "2", 1349002000000L, "me", List.of()))
                ),
                Arguments.of(
                        1349004000000L, 1349000000000L, 3, 10,
                        List.of(new BuildNumberRecord(BUILDINFO_REPO_KEY, "ba", "2", 1349002000000L, "me", List.of()))
                ),
                Arguments.of(
                        1349002000000L, 1349000000000L, 3, 10, List.of()
                )
        );
    }

    @NonNull
    Stream<Arguments> selectBuildNumbersNotInRepo_Provider() {
        return Stream.of(
                Arguments.of("ba", "1", BUILDINFO_REPO_KEY, List.of()),
                Arguments.of("cc", "1", BUILDINFO_REPO_KEY, List.of("project-build-info")),
                Arguments.of("cc", "1", "project-build-info", List.of(BUILDINFO_REPO_KEY)),
                Arguments.of("dd", "3", "proj1-build-info", List.of(BUILDINFO_REPO_KEY, "proj2-build-info")),
                Arguments.of("not-exists", "3", "proj1-build-info", List.of()),
                Arguments.of("dd", "333", "proj1-build-info", List.of()),
                Arguments.of("dd", "3", "not-exists-build-info", // returns all the other 3 repos (which do exist)
                        List.of(BUILDINFO_REPO_KEY, "proj1-build-info", "proj2-build-info"))
        );
    }

    /**
     * RTDEV-94069: the env/system build-props lookup ({@link BuildsDao#getBuildPropsList(BuildParams)} ->
     * {@link BuildsDao#getBuildId(BuildParams)}) must resolve the build within the SAME repo the caller was
     * authorized against. (name, number, date) are not unique across repos, so without the repo filter a reader
     * of an ordinary repo could read another repo's build props (e.g. a CI secret in artifactory-build-info) by
     * passing a readable {@code buildRepo} while name/number/date point at the protected build.
     */
    @Test
    void getBuildPropsList_isScopedToRequestedRepo() throws SQLException {
        long sharedDate = 1349500000000L;
        // Victim build in artifactory-build-info holding a secret env var.
        BuildEntity victim = new BuildEntity(
                901L, "shared-name", "1", sharedDate, null, sharedDate, "me", 0L, null, BUILDINFO_REPO_KEY);
        victim.setProperties(Set.of(new BuildProperty(9011L, 901L, "buildInfo.env.SECRET", "victim-ci-token-7f4b")));
        victim.setPromotions(new HashSet<>());
        buildsDao.createBuild(victim);

        // A build with the same name/number/date in a repo the low-priv user can read.
        BuildEntity readable = new BuildEntity(
                902L, "shared-name", "1", sharedDate, null, sharedDate, "me", 0L, null, ALTERNATIVE_BUILD_INFO_REPO);
        readable.setProperties(Set.of(new BuildProperty(9021L, 902L, "buildInfo.env.HARMLESS", "nothing-secret")));
        readable.setPromotions(new HashSet<>());
        buildsDao.createBuild(readable);

        // Reading via the readable repo must NOT leak the victim repo's props (the PoC in the ticket).
        List<BuildProps> readableProps = buildsDao.getBuildPropsList(
                envPropsParams("shared-name", "1", sharedDate, ALTERNATIVE_BUILD_INFO_REPO));
        assertEquals(1, readableProps.size());
        assertEquals("buildInfo.env.HARMLESS", readableProps.getFirst().getKey());
        assertEquals("nothing-secret", readableProps.getFirst().getValue());

        // Reading via the actual storing repo returns that repo's props, as expected for an authorized caller.
        List<BuildProps> victimProps =
                buildsDao.getBuildPropsList(envPropsParams("shared-name", "1", sharedDate, BUILDINFO_REPO_KEY));
        assertEquals(1, victimProps.size());
        assertEquals("buildInfo.env.SECRET", victimProps.getFirst().getKey());
        assertEquals("victim-ci-token-7f4b", victimProps.getFirst().getValue());

        // A repo with no such build resolves to no build id -> no props.
        assertTrue(buildsDao.getBuildPropsList(
                envPropsParams("shared-name", "1", sharedDate, NOT_EXISTING_BUILD_REPO)).isEmpty());
    }

    @NonNull
    private static BuildParams envPropsParams(
            @NonNull String buildName,
            @NonNull String buildNumber,
            long buildDate,
            @NonNull String buildRepo) {
        BuildParams buildParams = new BuildParams(
                null, buildNumber, null, null, String.valueOf(buildDate), buildName, buildRepo);
        buildParams.setIsEnvProps(true);
        return buildParams;
    }

    private void createBuilds() throws SQLException {
        buildsDao.createBuild(createBuild11());
        buildsDao.createBuild(createBuild12());
        buildsDao.createBuild(createBuild13());
        buildsDao.createBuild(createBuild15());
        buildsDao.createBuild(createBuild103());
    }

    private void doDelete() throws SQLException {
        assertNotNull(buildsDao.getBuild(11L));
        assertEquals(1, buildsDao.deleteBuild(11L));
        assertNull(buildsDao.getBuild(11L));
    }
}
