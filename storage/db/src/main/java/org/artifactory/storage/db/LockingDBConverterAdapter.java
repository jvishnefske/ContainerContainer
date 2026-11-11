package org.artifactory.storage.db;

import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.storage.db.version.converter.LockingDBSqlConverter;
import org.jfrog.converter.DbDetails;
import org.jfrog.converter.DbVersion;

import java.util.Arrays;

/**
 * Database Converter Adapter for distributed_locks table.
 * <p>Works only on DBConverters that supports distributed_locks</p>
 */
public class LockingDBConverterAdapter extends DatabaseConverterAdapter {

    public LockingDBConverterAdapter(ArtifactoryHome artifactoryHome, DbDetails dbDetails) {
        super(artifactoryHome, dbDetails);
    }

    @Override
    protected DbVersion[] getDbVersions() {
        return Arrays.stream(super.getDbVersions())
                .filter(dbVersion -> dbVersion.converter() instanceof LockingDBSqlConverter)
                .toArray(DbVersion[]::new);
    }
}
