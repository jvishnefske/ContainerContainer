package org.artifactory.storage.db.fs.dao;

import com.google.common.collect.Lists;
import org.apache.commons.lang3.tuple.Pair;
import org.artifactory.addon.jobs.JobsQuery;
import org.artifactory.addon.replication.ConditionStrategy;
import org.artifactory.storage.db.util.BaseDao;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.artifactory.storage.db.util.querybuilder.ArtifactoryQueryWriter;
import org.jfrog.storage.util.DbUtils;
import org.jfrog.storage.util.querybuilder.QueryWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static org.artifactory.common.ConstantValues.jobsTableFetchLimit;


/**
 * @author Yoaz Menda
 */
@Repository
public class JobsDao extends BaseDao {

    public static final String JOBS_TABLE = "jobs";

    @Autowired
    public JobsDao(ArtifactoryJdbcHelper jdbcHelper) {
        super(jdbcHelper);
    }

    public List<JobRecord> getJobs(JobsQuery jobsQuery) throws SQLException {
        return getJobs(jobsQuery, ConditionStrategy.AND);
    }

    public List<JobRecord> getJobs(JobsQuery jobsQuery, ConditionStrategy timeStrategy) throws SQLException {
        ResultSet resultSet = null;
        List<JobRecord> entries = Lists.newArrayList();
        try {
            resultSet = executeQuery(jobsQuery, timeStrategy);
            while (resultSet.next()) {
                entries.add(jobFromResultSet(resultSet));
            }
            return entries;
        } finally {
            DbUtils.close(resultSet);
        }
    }

    public void create(long jobId, String jobType, String jobStatus, Long started, Long finished,
            String additionalDetails) throws SQLException {
        jdbcHelper.executeUpdate("INSERT INTO " + JOBS_TABLE + " " +
                "(job_id, job_type, job_status, started, finished, additional_details) " +
                "VALUES(?, ?, ?, ?, ?, ?)", jobId, jobType, jobStatus, started, finished, additionalDetails);
    }

    public int deleteJobsStartedBefore(long startedBefore) throws SQLException {
        return jdbcHelper.executeUpdate("DELETE FROM " + JOBS_TABLE + " WHERE started < ?", startedBefore);
    }

    private JobRecord jobFromResultSet(ResultSet resultSet) throws SQLException {
        return new JobRecord(
                resultSet.getLong("job_id"),
                resultSet.getString("job_type"),
                resultSet.getString("job_status"),
                resultSet.getLong("started"),
                resultSet.getLong("finished"),
                resultSet.getString("additional_details"));
    }

    public JobRecord getJob(long jobId) throws SQLException {
        ResultSet resultSet = null;
        JobRecord job = null;
        try {
            resultSet = jdbcHelper.executeSelect("SELECT * from " + JOBS_TABLE + " WHERE job_id = ?", jobId);
            if (resultSet.next()) {
                job = jobFromResultSet(resultSet);
            }
            return job;
        } finally {
            DbUtils.close(resultSet);
        }
    }

    public void updateJob(long jobId, String newStatus, String newAdditionalDetails) throws SQLException {
        jdbcHelper.executeUpdate("UPDATE " + JOBS_TABLE + " SET" +
                        " finished = ?, additional_details = ?, job_status = ?" +
                        " WHERE job_id = ?",
                Instant.now().toEpochMilli(), newAdditionalDetails, newStatus, jobId);
    }

    private ResultSet executeQuery(JobsQuery jobsQuery, ConditionStrategy timeStrategy) throws SQLException {
        QueryWriter sql = new ArtifactoryQueryWriter()
                .from(" " + JOBS_TABLE + " ")
                .orderBy(" started Desc ")
                .limit(jobsTableFetchLimit.getLong());
        List<Pair<String, List<Object>>> conditions = extractConditions(jobsQuery, timeStrategy);
        if (conditions.isEmpty()) {
            return jdbcHelper.executeSelect(sql.build());
        }
        StringBuilder where = new StringBuilder(" ");
        List<Object> whereParams = new ArrayList<>();

        boolean isFirst = true;
        Iterator<Pair<String, List<Object>>> iterator = conditions.iterator();
        while (iterator.hasNext()) {
            if (!isFirst) {
                where.append(" AND ");
            }
            Pair<String, List<Object>> next = iterator.next();
            where.append(next.getLeft());
            whereParams.addAll(next.getRight());
            isFirst = false;
        }
        sql.where(where.toString());
        return jdbcHelper.executeSelect(sql.build(), whereParams);
    }

    private List<Pair<String, List<Object>>> extractConditions(JobsQuery jobsQuery, ConditionStrategy timeStrategy) {
        List<Pair<String, List<Object>>> params = new ArrayList<>();
        if (jobsQuery.getFinishedBefore() != null && jobsQuery.getStartedAfter() != null) {
            String query;
            final List<Object> whereParams;
            if (jobsQuery.getStartedAfter().isBefore(jobsQuery.getFinishedBefore())) {
                // in case of time overlapping, get everything in range
                query = " (finished is NOT NULL) AND ((finished <= ? AND finished > ?) " + timeStrategy.toString() +
                        " (started < ? AND started >= ?)) ";
                whereParams = List.of(
                        jobsQuery.getFinishedBefore().toEpochMilli(),
                        jobsQuery.getStartedAfter().toEpochMilli(),
                        jobsQuery.getFinishedBefore().toEpochMilli(),
                        jobsQuery.getStartedAfter().toEpochMilli()
                );
            } else {
                query = " ((finished is NOT NULL) AND (finished < ?)) " + timeStrategy.toString() + " (started >= ?) ";
                whereParams = List.of(
                        jobsQuery.getFinishedBefore().toEpochMilli(),
                        jobsQuery.getStartedAfter().toEpochMilli()
                );
            }
            params.add(Pair.of(query, whereParams));
        } else {
            if (jobsQuery.getFinishedBefore() != null) {
                params.add(Pair.of(" ((finished is NOT NULL) AND (finished < ?)) ",
                        Collections.singletonList(jobsQuery.getFinishedBefore().toEpochMilli())));
            }
            if (jobsQuery.getStartedAfter() != null) {
                params.add(Pair.of(" started >= ? ",
                        Collections.singletonList(jobsQuery.getStartedAfter().toEpochMilli())));
            }
        }
        if (jobsQuery.getJobStatus() != null) {
            params.add(Pair.of(" job_status = ? ", Collections.singletonList(jobsQuery.getJobStatus().toString())));
        }
        if (jobsQuery.getJobType() != null) {
            params.add(Pair.of(" job_type = ? ", Collections.singletonList(jobsQuery.getJobType().toString())));
        }
        return params;
    }

}
