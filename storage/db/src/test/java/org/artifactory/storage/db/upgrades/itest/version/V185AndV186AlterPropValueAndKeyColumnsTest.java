package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.converter.DbConverterResult;
import org.jfrog.converter.model.ChangeLogContext;
import org.jfrog.converter.model.ConverterApplyStatus;
import org.testng.annotations.Test;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v184;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v185;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v186;
import static org.testng.Assert.assertEquals;

/**
 * @author Hussein Alshqerat
 */
public class V185AndV186AlterPropValueAndKeyColumnsTest extends UpgradeBaseTest {

    private static final int DEFAULT_INITIAL_VALUE = -1;
    private static final Pattern PATTERN = Pattern.compile("(\\d+\\.\\d+)");
    private static final Map<Double, Optional<Integer>> STATICS_INITIAL_VALUE = Map.of(
            17.5, Optional.empty(),
            13.9, Optional.of(DEFAULT_INITIAL_VALUE));

    @Test
    public void testConvertorsCompleted() throws SQLException {
        testOnlyWithPostgresql();
        final Double dbVersion = getDbVersion();
        final Integer initialExpectedValue =
                STATICS_INITIAL_VALUE.getOrDefault(dbVersion, Optional.of(DEFAULT_INITIAL_VALUE))
                                     .orElse(null);
        assertStatics(initialExpectedValue);
        runV185AndV186Convertors(ConverterApplyStatus.COMPLETED);
        assertStatics(1000);
    }

    @Test
    public void testConvertorsSkipped() throws SQLException {
        testOnlyWithPostgresql();
        setStatics();
        runV185AndV186Convertors(ConverterApplyStatus.SKIPPED);
        assertStatics(1000);
    }

    private void setStatics() throws SQLException {
        jdbcHelper.executeUpdate("ALTER TABLE node_props ALTER COLUMN prop_key SET STATISTICS 1000");
        jdbcHelper.executeUpdate("ALTER TABLE node_props ALTER COLUMN prop_value SET STATISTICS 1000");
    }

    private void assertStatics(Integer expectedValue) throws SQLException {
        ResultSet result = jdbcHelper.executeSelect(
                """
                        SELECT attname, attstattarget FROM pg_attribute
                        WHERE attrelid = 'node_props'::regclass
                        AND (attname = 'prop_key' or attname = 'prop_value');
                        """
        );
        assertResultSetValues(result,  expectedValue);
        result.close();
    }

    private void assertResultSetValues(ResultSet result, Integer expectedValue) throws SQLException {
        if (result.next()) {
            assertEquals(expectedValue, result.getObject(2));
        }
    }

    private void runV185AndV186Convertors(ConverterApplyStatus expectedResult) throws SQLException {
        try (ChangeLogContext context = new ChangeLogContext(jdbcHelper.getDataSource().getConnection(),
                dbProperties.getDbType(), v185)) {
            DbConverterResult result = v185.converter().convert(context);
            assertEquals(result.getStatus(), expectedResult);
        }
        try (ChangeLogContext context = new ChangeLogContext(jdbcHelper.getDataSource().getConnection(),
                dbProperties.getDbType(), v186)) {
            DbConverterResult result = v186.converter().convert(context);
            assertEquals(result.getStatus(), expectedResult);
        }
    }

    private Double getDbVersion() throws SQLException {
        try (ResultSet result = jdbcHelper.executeSelect("SELECT current_setting('server_version')")) {
            if (result.next()) {
                final String fullDbVersion = result.getString("current_setting");
                final Matcher matcher = PATTERN.matcher(fullDbVersion);
                if (matcher.find()) {
                    return Double.parseDouble(matcher.group(1));
                }
            }
        }
        return Double.NaN;
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v184;
    }
}