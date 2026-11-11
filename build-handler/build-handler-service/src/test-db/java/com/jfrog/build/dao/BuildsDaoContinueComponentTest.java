package com.jfrog.build.dao;

import com.jfrog.build.acl.model.GeneralBuild;
import com.jfrog.build.model.api.ContinueBuildFilter;
import com.jfrog.build.entity.BuildIdEntity;
import com.jfrog.build.acl.model.BuildId;
import com.jfrog.build.model.BuildIdImpl;
import com.jfrog.build.test.database.base.DatabaseTestBase;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.jfrog.commons.continues.util.Direction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.jfrog.build.model.api.ContinueBuildFilter.OrderBy.BUILD_NAME;
import static com.jfrog.build.acl.util.BuildConstants.BUILDINFO_REPO_KEY;
import static org.jfrog.commons.continues.util.Direction.ASC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BuildsDaoContinueComponentTest extends DatabaseTestBase {

    @Autowired
    private BuildsDao buildsDao;

    @BeforeEach
    void setup() {
        importSql("/sql/dml/builds.sql");
    }

    @Test
    void getLatestBuildIds_WithContinue() {
        ContinueBuildFilter continueBuildFilter = new ContinueBuildFilter();
        BuildId buildId = new BuildIdImpl(5, "ba", "3", 1349004000000L, "project-build-info");
        continueBuildFilter.setContinueBuildId(buildId);
        continueBuildFilter.setDirection(Direction.DESC);
        List<BuildIdEntity> latestBuildIds = buildsDao.getLatestBuildIds(continueBuildFilter);
        assertEquals(1, latestBuildIds.size());
        Map<String, BuildIdEntity> builds = latestBuildIds.stream()
                .collect(Collectors.toMap(BuildIdEntity::getBuildName, build -> build));
        assertEquals(4, builds.get("bb").getBuildId());
        assertEquals("2", builds.get("bb").getBuildNumber());
        assertEquals(1349003000000L, builds.get("bb").getBuildDate());
        assertEquals("project-build-info", builds.get("bb").getBuildRepo());
        assertNull(builds.get("ba"));
    }

    @Test
    void getLatestBuildIds_OrderByNameWithContinue() {
        ContinueBuildFilter continueBuildFilter = new ContinueBuildFilter();
        BuildId buildId = new BuildIdImpl(5, "ba", "3", 1349004000000L, "project-build-info");
        continueBuildFilter.setContinueBuildId(buildId);
        continueBuildFilter.setDirection(ASC);
        continueBuildFilter.setOrderBy(BUILD_NAME);
        List<BuildIdEntity> latestBuildIds = buildsDao.getLatestBuildIds(continueBuildFilter);
        assertEquals(1, latestBuildIds.size());
        Map<String, BuildIdEntity> builds = latestBuildIds.stream()
                .collect(Collectors.toMap(BuildIdEntity::getBuildName, build -> build));
        assertEquals(4, builds.get("bb").getBuildId());
        assertEquals("2", builds.get("bb").getBuildNumber());
        assertEquals(1349003000000L, builds.get("bb").getBuildDate());
        assertEquals("project-build-info", builds.get("bb").getBuildRepo());
        assertNull(builds.get("ba"));
    }

    @Test
    void getLatestBuildIds_OrderByNameWithContinueEnd() {
        ContinueBuildFilter continueBuildFilter = new ContinueBuildFilter();
        BuildId buildId = new BuildIdImpl(4, "bb", "2", 1349003000000L, BUILDINFO_REPO_KEY);
        continueBuildFilter.setContinueBuildId(buildId);
        continueBuildFilter.setDirection(ASC);
        continueBuildFilter.setOrderBy(BUILD_NAME);
        List<BuildIdEntity> latestBuildIds = buildsDao.getLatestBuildIds(continueBuildFilter);
        assertEquals(0, latestBuildIds.size());
    }

    @Test
    void getLatestBuildIds_FilteredBySearchText() {
        ContinueBuildFilter continueBuildFilter = new ContinueBuildFilter();
        continueBuildFilter.setSearchStr("ba");
        List<BuildIdEntity> latestBuildIds = buildsDao.getLatestBuildIds(continueBuildFilter);
        assertEquals(1, latestBuildIds.size());
        Map<String, BuildIdEntity> builds = latestBuildIds.stream()
                .collect(Collectors.toMap(BuildIdEntity::getBuildName, build -> build));
        assertNull(builds.get("bb"));
        assertEquals(5, builds.get("ba").getBuildId());
        assertEquals("3", builds.get("ba").getBuildNumber());
        assertEquals(1349004000000L, builds.get("ba").getBuildDate());
        assertEquals(BUILDINFO_REPO_KEY, builds.get("ba").getBuildRepo());
    }

    @Test
    void getLatestBuildIds_FilteredByLimit() {
        ContinueBuildFilter continueBuildFilter = new ContinueBuildFilter();
        continueBuildFilter.setLimit(1L);
        continueBuildFilter.setDirection(Direction.DESC);
        List<BuildIdEntity> latestBuildIds = buildsDao.getLatestBuildIds(continueBuildFilter);
        assertEquals(1, latestBuildIds.size());
        Map<String, BuildIdEntity> builds = latestBuildIds.stream()
                .collect(Collectors.toMap(BuildIdEntity::getBuildName, build -> build));
        assertNull(builds.get("bb"));
        assertEquals(5, builds.get("ba").getBuildId());
        assertEquals("3", builds.get("ba").getBuildNumber());
        assertEquals(1349004000000L, builds.get("ba").getBuildDate());
        assertEquals(BUILDINFO_REPO_KEY, builds.get("ba").getBuildRepo());
    }

    @Test
    void getLatestBuildIds_FilteredByZeroLimit() {
        ContinueBuildFilter continueBuildFilter = new ContinueBuildFilter();
        continueBuildFilter.setLimit(0L);
        continueBuildFilter.setDirection(Direction.DESC);
        List<BuildIdEntity> latestBuildIds = buildsDao.getLatestBuildIds(continueBuildFilter);
        assertEquals(0, latestBuildIds.size());
    }

    @Test
    void getLatestBuildIds_OrderedByNameAsc() {
        ContinueBuildFilter continueBuildFilter = new ContinueBuildFilter();
        continueBuildFilter.setOrderBy(BUILD_NAME);
        List<BuildIdEntity> latestBuildIds = buildsDao.getLatestBuildIds(continueBuildFilter);
        assertEquals(2, latestBuildIds.size());
        Map<String, BuildIdEntity> builds = latestBuildIds.stream()
                .collect(Collectors.toMap(BuildIdEntity::getBuildName, build -> build));
        assertEquals("ba", latestBuildIds.get(0).getBuildName());
        assertEquals("bb", latestBuildIds.get(1).getBuildName());
        assertEquals(4, builds.get("bb").getBuildId());
        assertEquals("2", builds.get("bb").getBuildNumber());
        assertEquals(1349003000000L, builds.get("bb").getBuildDate());
        assertEquals(5, builds.get("ba").getBuildId());
        assertEquals("3", builds.get("ba").getBuildNumber());
        assertEquals(1349004000000L, builds.get("ba").getBuildDate());
        assertEquals(BUILDINFO_REPO_KEY, builds.get("ba").getBuildRepo());
    }

    @Test
    void getLatestBuildIds_OrderedByNameDesc() {
        ContinueBuildFilter continueBuildFilter = new ContinueBuildFilter();
        continueBuildFilter.setOrderBy(BUILD_NAME);
        continueBuildFilter.setDirection(Direction.DESC);
        List<BuildIdEntity> latestBuildIds = buildsDao.getLatestBuildIds(continueBuildFilter);
        assertEquals(2, latestBuildIds.size());
        assertEquals("bb", latestBuildIds.get(0).getBuildName());
        assertEquals("ba", latestBuildIds.get(1).getBuildName());
        assertEquals("project-build-info", latestBuildIds.get(0).getBuildRepo());
    }

    @Test
    void getLatestBuildIds_OrderedByNumberDesc() {
        ContinueBuildFilter continueBuildFilter = new ContinueBuildFilter();
        continueBuildFilter.setOrderBy(ContinueBuildFilter.OrderBy.BUILD_NUMBER);
        continueBuildFilter.setDirection(Direction.DESC);
        List<BuildIdEntity> latestBuildIds = buildsDao.getLatestBuildIds(continueBuildFilter);
        assertEquals(2, latestBuildIds.size());
        assertEquals("3", latestBuildIds.get(0).getBuildNumber());
        assertEquals("2", latestBuildIds.get(1).getBuildNumber());
        assertEquals(BUILDINFO_REPO_KEY, latestBuildIds.get(0).getBuildRepo());
    }

    @ParameterizedTest
    @MethodSource("getLatestBuildIds_WithContinueByRepo_Provider")
    void getLatestBuildIds_WithContinueByRepo(String buildName, String buildRepo, int occurrences) {
        ContinueBuildFilter continueBuildFilter = new ContinueBuildFilter();
        BuildId buildId = new BuildIdImpl(5, buildName, "3", 1349004000000L, buildRepo);
        continueBuildFilter.setContinueBuildId(buildId);
        continueBuildFilter.setDirection(Direction.DESC);
        List<BuildIdEntity> latestBuildIds = buildsDao.getLatestBuildIds(continueBuildFilter);
        assertEquals(latestBuildIds.size(), occurrences);
        if (occurrences > 0) {
            Map<String, BuildIdEntity> builds = latestBuildIds.stream()
                    .collect(Collectors.toMap(BuildIdEntity::getBuildName, build -> build));
            assertEquals(4, builds.get("bb").getBuildId());
            assertEquals("2", builds.get("bb").getBuildNumber());
            assertEquals(1349003000000L, builds.get("bb").getBuildDate());
            assertEquals("project-build-info", builds.get("bb").getBuildRepo());
            assertNull(builds.get("ba"));
        }
    }

    @NonNull
    static Stream<Arguments> getLatestBuildIds_WithContinueByRepo_Provider() {
        return Stream.of(
                Arguments.of("ba", null, 1),
                Arguments.of("ba", StringUtils.EMPTY, 1),
                Arguments.of("ba", "project-build-info", 1),
                Arguments.of("ba", BUILDINFO_REPO_KEY, 0)
        );
    }

    @Test
    void getBuildForName() throws SQLException {
        ContinueBuildFilter continueBuildFilter = new ContinueBuildFilter();
        List<GeneralBuild> generalBuilds = buildsDao.getBuildForName("ba", BUILDINFO_REPO_KEY, continueBuildFilter);
        assertEquals(3, generalBuilds.size());
        assertEquals(BUILDINFO_REPO_KEY, generalBuilds.getFirst().getBuildRepo());
    }

    @Test
    void getBuildForName_WithLimit() throws SQLException {
        ContinueBuildFilter continueBuildFilter = new ContinueBuildFilter();
        continueBuildFilter.setLimit(1L);
        List<GeneralBuild> generalBuilds = buildsDao.getBuildForName("ba", BUILDINFO_REPO_KEY, continueBuildFilter);
        assertEquals(1, generalBuilds.size());
        assertEquals(BUILDINFO_REPO_KEY, generalBuilds.getFirst().getBuildRepo());
    }

    @Test
    void getBuildForName_WithZeroLimit() throws SQLException {
        ContinueBuildFilter continueBuildFilter = new ContinueBuildFilter();
        continueBuildFilter.setLimit(0L);
        List<GeneralBuild> generalBuilds = buildsDao.getBuildForName("ba", BUILDINFO_REPO_KEY, continueBuildFilter);
        assertEquals(0, generalBuilds.size());
    }

    @Test
    void getBuildForName_WithWithContinue() throws SQLException {
        ContinueBuildFilter continueBuildFilter = new ContinueBuildFilter();
        BuildId buildId = new BuildIdImpl(0L, "ba", "3", 1349004000000L, BUILDINFO_REPO_KEY);
        continueBuildFilter.setContinueBuildId(buildId);
        List<GeneralBuild> generalBuilds = buildsDao.getBuildForName("ba", BUILDINFO_REPO_KEY, continueBuildFilter);
        assertEquals(2, generalBuilds.size());
        assertEquals(BUILDINFO_REPO_KEY, generalBuilds.getFirst().getBuildRepo());
    }

    @Test
    void getDistinctBuildNames() {
        ContinueBuildFilter continueBuildFilter = new ContinueBuildFilter();
        continueBuildFilter.setOrderBy(BUILD_NAME);
        continueBuildFilter.setDirection(ASC);

        List<String> buildNames = buildsDao.getDistinctBuildNames(continueBuildFilter);
        assertEquals(2, buildNames.size());
        assertTrue(buildNames.containsAll(List.of("ba", "bb")));
    }

    @Test
    void getDistinctBuildNames_WithContinue() {
        ContinueBuildFilter continueBuildFilter = new ContinueBuildFilter();
        continueBuildFilter.setOrderBy(BUILD_NAME);
        continueBuildFilter.setDirection(ASC);
        BuildId buildId = new BuildIdImpl(-1L, "ba", null, -1L, null);
        continueBuildFilter.setContinueBuildId(buildId);

        List<String> buildNames = buildsDao.getDistinctBuildNames(continueBuildFilter);
        assertEquals(1, buildNames.size());
        assertTrue(buildNames.contains("bb"));
    }

    @Test
    void getDistinctBuildNames_BySearchStr() {
        ContinueBuildFilter continueBuildFilter = new ContinueBuildFilter();
        continueBuildFilter.setOrderBy(BUILD_NAME);
        continueBuildFilter.setDirection(ASC);
        continueBuildFilter.setSearchStr("a");

        List<String> buildNames = buildsDao.getDistinctBuildNames(continueBuildFilter);
        assertEquals(1, buildNames.size());
        assertTrue(buildNames.contains("ba"));
    }

    @Test
    void getDistinctBuildNames_WithLimit() {
        ContinueBuildFilter continueBuildFilter = new ContinueBuildFilter();
        continueBuildFilter.setOrderBy(BUILD_NAME);
        continueBuildFilter.setDirection(ASC);

        continueBuildFilter.setLimit(0L);
        List<String> buildNames = buildsDao.getDistinctBuildNames(continueBuildFilter);
        assertEquals(0, buildNames.size());

        continueBuildFilter.setLimit(1L);
        buildNames = buildsDao.getDistinctBuildNames(continueBuildFilter);
        assertEquals(1, buildNames.size());
    }

    @Test
    void getDistinctBuildNamesCount() {
        ContinueBuildFilter continueBuildFilter = new ContinueBuildFilter();
        continueBuildFilter.setOrderBy(BUILD_NAME);
        continueBuildFilter.setDirection(ASC);
        assertEquals(2, buildsDao.getDistinctBuildNamesCount(continueBuildFilter));
    }

    @Test
    void getDistinctBuildNamesCount_BySearchString() {
        ContinueBuildFilter continueBuildFilter = new ContinueBuildFilter();
        continueBuildFilter.setOrderBy(BUILD_NAME);
        continueBuildFilter.setDirection(ASC);
        continueBuildFilter.setSearchStr("b");
        assertEquals(2, buildsDao.getDistinctBuildNamesCount(continueBuildFilter));
    }
}
