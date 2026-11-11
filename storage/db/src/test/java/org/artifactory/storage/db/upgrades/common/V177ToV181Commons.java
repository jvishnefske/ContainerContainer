package org.artifactory.storage.db.upgrades.common;

import lombok.NonNull;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.converter.model.ChangeLogContext;
import org.jfrog.converter.model.ConverterApplyStatus;
import org.testng.annotations.BeforeMethod;

import java.sql.SQLException;
import java.util.List;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v176;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v177;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v178;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v179;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v180;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v181;
import static org.testng.Assert.assertEquals;

public abstract class V177ToV181Commons extends AsyncUpgradeBaseTest {
    protected final static String TABLE_NAME = "nodes";
    protected List<String> COLUMN_NAMES = null;
    protected final static String OLD_INDEX = "nodes_node_name_idx";
    protected final static String NEW_INDEX = "nodes_name_path_idx";

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v176;
    }

    @BeforeMethod
    public void setup() {
        if (COLUMN_NAMES == null) {
            //dbProperites ready only here
            COLUMN_NAMES = List.of(getColName("node_name"), getColName("node_path"));
        }
    }

    protected void runUpgrade(@NonNull ArtifactoryDBVersion version, ConverterApplyStatus expectedResult)
            throws SQLException {
        try (ChangeLogContext context = new ChangeLogContext(jdbcHelper.getDataSource().getConnection(),
                dbProperties.getDbType(), version)) {
            assertEquals(version.converter().convert(context).getStatus(), expectedResult);
        }
    }

    protected ConverterApplyStatus getExpectedStatus(ArtifactoryDBVersion artifactoryDBVersion) {
        if (dbProperties.isPostgres() && (artifactoryDBVersion.equals(v177) || artifactoryDBVersion.equals(v178) ||
                artifactoryDBVersion.equals(v181))) {
            return ConverterApplyStatus.COMPLETED;
        } else if (!dbProperties.isPostgres() &&
                (artifactoryDBVersion.equals(v179) || artifactoryDBVersion.equals(v180))) {
            return ConverterApplyStatus.COMPLETED;
        }
        return ConverterApplyStatus.SKIPPED;
    }

    protected String getColName(String colName) {
        if (dbProperties.isPostgres()) {
            colName = colName + " varchar_pattern_ops";
        }
        return colName;
    }
}
