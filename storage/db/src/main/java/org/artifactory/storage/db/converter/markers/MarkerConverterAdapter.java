package org.artifactory.storage.db.converter.markers;

import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.converter.ArtifactoryConverterAdapter;
import org.artifactory.storage.db.converter.markers.version.MarkerConverterVersion;
import org.artifactory.storage.db.converter.markers.version.MarkerMigrationConverter;
import org.artifactory.storage.db.util.ArtifactoryDbUtils;
import org.artifactory.version.CompoundVersionDetails;
import org.jfrog.converter.DbDetails;
import org.jfrog.storage.JdbcHelper;

import jakarta.annotation.Nullable;

/**
 * @author Omri Ziv
 */
public class MarkerConverterAdapter implements ArtifactoryConverterAdapter {

    private ArtifactoryHome artifactoryHome;
    private DbDetails dbDetails;

    public MarkerConverterAdapter(ArtifactoryHome artifactoryHome,
            DbDetails dbDetails) {
        this.artifactoryHome = artifactoryHome;
        this.dbDetails = dbDetails;
    }

    @Override
    public void convert(CompoundVersionDetails source, CompoundVersionDetails target) {
        JdbcHelper jdbcHelper = dbDetails.getJdbcHelper();
        for (MarkerConverterVersion markerConverterVersion : MarkerConverterVersion.values()) {
            for (MarkerMigrationConverter markerMigrationConverter : markerConverterVersion.getConverters()) {
                if (markerMigrationConverter.isInterested(source, target)) {
                    markerMigrationConverter.convert(artifactoryHome, jdbcHelper);
                }
            }
        }
    }


    @Override
    public boolean isInterested(@Nullable CompoundVersionDetails source, CompoundVersionDetails target) {
        return source != null && !source.isCurrent();
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
}
