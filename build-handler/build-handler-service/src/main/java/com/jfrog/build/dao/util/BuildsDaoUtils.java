package com.jfrog.build.dao.util;

import com.google.common.collect.Lists;
import com.jfrog.build.acl.model.BuildId;
import com.jfrog.build.acl.model.BuildNode;
import com.jfrog.build.acl.model.BuildRepoPath;
import com.jfrog.build.model.api.ContinueBuildFilter;
import com.jfrog.build.entity.BuildIdEntity;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jfrog.storage.DbType;
import org.jfrog.storage.JdbcHelper;
import org.jfrog.storage.util.querybuilder.BaseQueryBuilder;
import org.jfrog.storage.util.querybuilder.DerbyQueryBuilder;
import org.jfrog.storage.util.querybuilder.MysqlQueryBuilder;
import org.jfrog.storage.util.querybuilder.OracleQueryBuilder;
import org.jfrog.storage.util.querybuilder.PostgresqlQueryBuilder;
import org.jfrog.storage.util.querybuilder.SqlServerQueryBuilder;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;

import static com.jfrog.build.model.api.ContinueBuildFilter.OrderBy.BUILD_NAME;
import static java.util.Objects.isNull;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.jfrog.commons.continues.util.Direction.ASC;
import static org.jfrog.storage.DbType.MSSQL;

@Slf4j
@UtilityClass
public class BuildsDaoUtils {

    @NonNull
    public static Optional<BuildRepoPath> getBuildRepoPath(@NonNull ResultSet rs) throws SQLException {
        if (isBlank(rs.getString("repo"))) {
            return empty();
        }
        return of(new BuildRepoPath(
                rs.getString("repo"),
                rs.getString("node_path"),
                rs.getString("node_name")
        ));
    }

    @NonNull
    public static String createWhereClauseFromContinueBuildFilter(
            @NonNull ContinueBuildFilter continueBuildFilter,
            @NonNull List<Object> sqlParametersList,
            @Nullable String alias) {
        BuildId continueBuild = continueBuildFilter.getContinueBuildId();
        StringJoiner whereClauses = new StringJoiner(" AND ");
        String aliasPrefix = StringUtils.isNotBlank(alias) ? alias + "." : "";
        if (continueBuild != null) {
            if (StringUtils.isNotBlank(continueBuild.getBuildRepo())) {
                whereClauses.add(String.format("(%srepo = ?)", aliasPrefix));
                sqlParametersList.add(continueBuild.getBuildRepo());
            }
            Object continueValue = getContinueValue(continueBuildFilter);
            char directionChar = continueBuildFilter.getDirection() == ASC ? '>' : '<';
            if (continueBuildFilter.getOrderBy() == BUILD_NAME) {
                whereClauses.add(String.format("%sbuild_name %s ?", aliasPrefix, directionChar));
                sqlParametersList.add(continueBuild.getName());
            } else {
                whereClauses.add(String.format("(%s%s %s ? OR (%s%s = ? AND %sbuild_name %s ?))",
                        aliasPrefix, continueBuildFilter.getOrderByStr(), directionChar, aliasPrefix,
                        continueBuildFilter.getOrderByStr(), aliasPrefix, directionChar));
                sqlParametersList.add(continueValue);
                sqlParametersList.add(continueValue);
                sqlParametersList.add(continueBuild.getName());
            }
        }
        if (Objects.isNull(continueBuild) || StringUtils.isBlank(continueBuild.getBuildRepo())) {
            String buildRepo = continueBuildFilter.getBuildRepo();
            if (StringUtils.isNotBlank(buildRepo)) {
                whereClauses.add(String.format("(%srepo = ?)", aliasPrefix));
                sqlParametersList.add(buildRepo);
            }
        }
        if (StringUtils.isNotBlank(continueBuildFilter.getSearchStr())) {
            whereClauses.add(String.format("(%sbuild_name like ?)", aliasPrefix));
            sqlParametersList.add("%" + continueBuildFilter.getSearchStr() + "%");
        }
        return whereClauses.toString();
    }

    public static List<BuildIdEntity> populateBuildIdEntryList(ResultSet rs) throws SQLException {
        List<BuildIdEntity> buildIdEntities = new ArrayList<>();
        while (rs.next()) {
            BuildIdEntity buildId =
                    new BuildIdEntity(rs.getLong("build_id"), rs.getString("build_name"), rs.getString("build_number"),
                            rs.getLong("build_date"), rs.getString("repo"));
            buildIdEntities.add(buildId);
        }
        return buildIdEntities;
    }

    public static String createOrderByStr(ContinueBuildFilter continueBuildFilter) {
        String orderByStr;
        if (continueBuildFilter.getOrderBy() == BUILD_NAME) {
            orderByStr = String.format("build_name %s", continueBuildFilter.getDirection());
        } else {
            orderByStr = String.format("%s %s, build_name %s", continueBuildFilter.getOrderByStr(),
                    continueBuildFilter.getDirection(), continueBuildFilter.getDirection());
        }
        return orderByStr;
    }

    private static Object getContinueValue(ContinueBuildFilter continueBuildFilter) {
        BuildId buildId = continueBuildFilter.getContinueBuildId();
        return switch (continueBuildFilter.getOrderBy()) {
            case BUILD_NAME -> buildId.getName();
            case BUILD_NUMBER -> buildId.getNumber();
            default -> buildId.getStartedDate().getTime();
        };
    }

    public static <T> int executeLimitedSizedQuery(JdbcHelper jdbcHelper, String query, List<T> params, int size)
            throws SQLException {
        List<List<T>> partitions = Lists.partition(params, size);
        int rowsAffected = 0;
        for (List<T> partition: partitions) {
            rowsAffected += jdbcHelper.executeUpdate(query, partition);
        }
        return rowsAffected;
    }

    @Nullable
    public static String getBuildNodePath(@NonNull BuildNode node) {
        String path = node.getPath();
        if (isNull(path)) {
            return null;
        }
        String name = node.getName();
        return path.equals(".") ? name : path + "/" + name;
    }

    @Nullable
    public static String preparePropValue(int maxPropValue, String propKey, String propValue) {
        if (propValue != null && propValue.length() > maxPropValue) {
            log.info("Trimming property value to {} characters {}", maxPropValue, propKey);
            log.debug("Trimming property value to {} characters {}: {}", maxPropValue, propKey, propValue);
            propValue = StringUtils.substring(propValue, 0, maxPropValue);
        }
        return propValue;
    }

    @Nullable
    public static Long safeGetLongOrNull(@NonNull ResultSet rs, @NonNull String columnName) throws SQLException {
        long value = rs.getLong(columnName);
        return rs.wasNull() ? null : value;
    }

    @Nullable
    public static Date safeGetDate(@NonNull ResultSet rs, @NonNull String columnName) throws SQLException {
        long value = rs.getLong(columnName);
        return rs.wasNull() ? null : new Date(value);
    }

    public static int getDbIndexedValueMaxSize(@NonNull DbType databaseType) {
        return databaseType == MSSQL ? 900 : 2048;
    }

    public static Long nullIfZero(long id) {
        return (id == 0L) ? null : id;
    }

    @NonNull
    public static BaseQueryBuilder createSqlBuilder(@NonNull DbType databaseType) {
        return switch (databaseType) {
            case ORACLE -> new OracleQueryBuilder();
            case MSSQL -> new SqlServerQueryBuilder();
            case POSTGRESQL -> new PostgresqlQueryBuilder();
            case MYSQL, MARIADB -> new MysqlQueryBuilder();
            default -> new DerbyQueryBuilder();
        };
    }
}
