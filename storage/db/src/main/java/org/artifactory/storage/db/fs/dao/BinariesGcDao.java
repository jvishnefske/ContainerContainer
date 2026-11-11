package org.artifactory.storage.db.fs.dao;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.artifactory.storage.db.util.BaseDao;
import org.jfrog.storage.DbType;
import org.jfrog.storage.util.DbUtils;
import org.jfrog.storage.util.querybuilder.QueryWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import jakarta.annotation.Nullable;
import java.io.Serializable;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;

/**
 * @author TamirH
 */
@Repository
@Slf4j
public class BinariesGcDao extends BaseDao {

    public static final String TEMP_SHA1_PREFIX = "##";
    private final DbType dbType;

    @Autowired
    public BinariesGcDao(ArtifactoryJdbcHelper jdbcHelper, ArtifactoryDbProperties dbProperties) {
        super(jdbcHelper);
        this.dbType = dbProperties.getDbType();
    }

    @NonNull
    public Collection<BinaryEntity> findPotentialDeletion(@Nullable String startAfterSha1) throws SQLException {
        return findPotentialDeletion(startAfterSha1,
                ConstantValues.gcSkipOrderBySizeFullGc.getBoolean(),
                ConstantValues.gcSkipOptimizationFullGc.getBoolean());
    }

    @NonNull
    @VisibleForTesting
    public Collection<BinaryEntity> findPotentialDeletion(@Nullable String startAfterSha1,
            boolean gcSkipOrderBySizeFullGcBoolean, boolean gcSkipOptimizationFullGcBoolean) throws SQLException {
        boolean optimizeSearchBySha1 =
                StringUtils.isNotBlank(startAfterSha1) && gcSkipOrderBySizeFullGcBoolean
                        && !gcSkipOptimizationFullGcBoolean;

        if (optimizeSearchBySha1) {
            log.trace("Searching for potential deletion candidates with sha1 greater than: {}", startAfterSha1);
        }
        QueryWriter query = new QueryWriter(dbType);
        final long gcBinariesToDeleteBatchSizeLong = ConstantValues.gcBinariesToDeleteBatchSize.getLong();
        query.select("b.sha1, b.md5, b.bin_length as bin_length, b.sha256")
                .from("binaries b")
                .where((optimizeSearchBySha1 ? "b.sha1 > '" + startAfterSha1 + "' AND " : "") +
                        "b.sha1 NOT LIKE '" + TEMP_SHA1_PREFIX + "%' " +
                        "AND NOT EXISTS (SELECT n.node_id FROM nodes n WHERE n.sha1_actual = b.sha1)")
                .limit(Math.max(1, gcBinariesToDeleteBatchSizeLong));
        if (!gcSkipOptimizationFullGcBoolean) {
            query.orderBy(gcSkipOrderBySizeFullGcBoolean ? "sha1" : "bin_length DESC");
        } else {
            log.trace("No optimization and order by are applied to the potential deletion search query");
        }

        List<BinaryEntity> results = Lists.newArrayList();
        ResultSet resultSet = null;
        try {
            resultSet = jdbcHelper.executeSelect(query.build());
            while (resultSet.next()) {
                results.add(binaryFromResultSet(resultSet));
            }
        } finally {
            DbUtils.close(resultSet);
        }
        return results;
    }

    private BinaryEntity binaryFromResultSet(ResultSet rs) throws SQLException {
        //sha1 sha2 md5 length, order in table is sha1 md5 length sha2
        return new BinaryEntity(rs.getString("sha1"), rs.getString("sha256"), rs.getString("md5"),
                rs.getLong("bin_length"));
    }

    public record BinaryEntity(String sha1, String sha2, String md5, long length) implements Serializable {
        @Override
        public String toString() {
            return "{" + sha1 + ',' + sha2 + ',' + md5 + ',' + length + '}';
        }

    }
}
