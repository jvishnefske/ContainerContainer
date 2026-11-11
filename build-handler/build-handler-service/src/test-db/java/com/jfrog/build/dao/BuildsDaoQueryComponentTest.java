package com.jfrog.build.dao;

import com.jfrog.build.model.api.diff.BuildParams;
import com.jfrog.build.entity.BuildEntity;
import com.jfrog.build.entity.BuildEntityRecord;
import com.jfrog.build.entity.BuildProperty;
import com.jfrog.build.model.BuildProps;
import com.jfrog.build.service.BuildDatabaseService;
import com.jfrog.build.test.database.base.DatabaseTestBase;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.TreeSet;

import static com.jfrog.build.test.database.TestDatabaseProvider.getJdbcHelper;
import static com.jfrog.build.acl.util.BuildConstants.BUILDINFO_REPO_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
public class BuildsDaoQueryComponentTest extends DatabaseTestBase {

    @Autowired
    private BuildsDao buildsDao;

    private BuildEntity build;

    @BeforeEach
    void setup() throws SQLException {
        importSql("/sql/dml/builds.sql");
        Instant start = Instant.now();
        int lots = 10000 - 100;
        build = createBuildLots(lots);
        buildsDao.createBuild(build);
        log.info("Duplicate {}", lots);
        getJdbcHelper().executeUpdate(
                "insert into build_props select prop_id + 10000, build_id, prop_key, prop_value from build_props");
        Duration duration = Duration.between(start, Instant.now());
        log.info("Setup took {}", duration);
    }

    @NonNull
    private BuildEntity createBuildLots(int lots) {
        long now = System.currentTimeMillis();
        BuildEntityRecord build = new BuildEntityRecord(
                15,
                "15",
                "" + (long) 15,
                now - 20000L,
                null,
                now,
                "this-is-me",
                15,
                null,
                BUILDINFO_REPO_KEY
        );
        HashSet<BuildProperty> props = new HashSet<>();
        long buildId = build.getBuildId();
        for (int i = 0; i < lots; i++) {
            String key = "buildInfo.env.";
            String value = "" + i;
            if (i % 10 == 0) {
                key = key + "is % 10 ";
            } else if (i % 10 == 1) {
                key = "1  % 10 ";
            } else if (i % 10 == 2) {
                key = "2  % 10 " + i / 10;
            } else {
                key = "" + i;
                value = "" + i;
            }
            props.add(new BuildProperty(1 + 100 + i, buildId, key, value));
        }
        return new BuildEntity(build, props, new TreeSet<>());
    }

    @Test
    void getDistinctBuildPropsResultSetOnQuery() throws Exception {
        BuildParams buildParams = new BuildParams(
                null,
                build.getBuildNumber(),
                null,
                null,
                "" + build.getBuildDate(),
                build.getBuildName(),
                BUILDINFO_REPO_KEY
        );
        Long buildId = buildsDao.getBuildId(buildParams);
        int distinct_rows = 8910;
        log.info("Measure select distinct for comparison");

        Instant start = Instant.now();
        int count = 0;
        try (ResultSet rs = getJdbcHelper().executeSelect("""
                SELECT distinct prop_key, prop_value
                FROM build_props
                WHERE build_id  = ?
                  AND prop_key NOT LIKE 'buildInfo.env.%'
                """, buildId)) {
            while (rs.next()) {
                count++;
            }
        }
        Duration duration = Duration.between(start, Instant.now());
        log.info("The query took {} -- #count {}", duration, count);
        assertEquals(distinct_rows, count);

        start = Instant.now();
        List<BuildProps> buildProps = buildsDao.getBuildPropsList("""
                SELECT prop_key, prop_value
                FROM build_props
                WHERE build_id  = ?
                  AND prop_key NOT LIKE 'buildInfo.env.%'
                """, buildId);
        count = BuildDatabaseService.distinctBuildProps(buildProps).size();
        log.info("Distinct - read #count {}", count);
        assertEquals(distinct_rows, count);
        duration = Duration.between(start, Instant.now());
        log.info("The query #2 took {} -- #count {}", duration, count);
    }
}
