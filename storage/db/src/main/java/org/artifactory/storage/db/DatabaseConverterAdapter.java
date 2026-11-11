package org.artifactory.storage.db;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.converter.ArtifactoryConverterAdapter;
import org.artifactory.storage.db.util.ArtifactoryDbUtils;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.artifactory.version.CompoundVersionDetails;
import org.jfrog.converter.DbConverterExecutionException;
import org.jfrog.converter.DbConverterRuntimeException;
import org.jfrog.converter.DbDetails;
import org.jfrog.converter.DbVersion;
import org.jfrog.converter.model.ConverterExecutionType;
import org.jfrog.converter.util.DbConverterUtil;

import jakarta.annotation.Nullable;
import java.sql.SQLException;

/**
 * @author Omri Ziv
 */
@Slf4j
public class DatabaseConverterAdapter implements ArtifactoryConverterAdapter {
    private final ArtifactoryHome artifactoryHome;
    private final DbDetails dbDetails;

    public DatabaseConverterAdapter(ArtifactoryHome artifactoryHome, DbDetails dbDetails) {
        this.artifactoryHome = artifactoryHome;
        this.dbDetails = dbDetails;
    }

    @Override
    public void convert(CompoundVersionDetails source, CompoundVersionDetails target) {
        try {
            DbConverterUtil.calculateAndExecute(
                    new DbDetails(dbDetails.getDbType(), dbDetails.getJdbcHelper()),
                    ArtifactoryDBVersion.getAlreadyApplied(source, ConverterExecutionType.SEQ_SYNC),
                    getDbVersions(), target.getVersionName(),
                    artifactoryHome.getServerId());
        } catch (DbConverterExecutionException | SQLException e) {
            throw new DbConverterRuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public boolean isInterested(@Nullable CompoundVersionDetails source, CompoundVersionDetails target) {
        return source != null && !source.isCurrent() && source.before(target);
    }

    @Override
    public void revert() {

    }

    @Override
    public void backup() {

    }

    @Override
    public void clean() {

    }

    @Override
    public void assertConversionPrecondition(ArtifactoryHome home, CompoundVersionDetails fromVersion,
            CompoundVersionDetails toVersion) {
        ArtifactoryDbUtils.verifyDbDetails(dbDetails, artifactoryHome);
    }

    protected DbVersion[] getDbVersions() {
        return ArtifactoryDBVersion.get(ConverterExecutionType.SEQ_SYNC);
    }
}
