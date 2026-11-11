package org.artifactory.storage.db.converter.markers.version;

import org.artifactory.storage.db.converter.markers.version.v1.V560AddAccessEmigrateMarkerMigration;
import org.artifactory.storage.db.converter.markers.version.v2.V5100AddAccessResourceTypeMarkerMigration;
import org.artifactory.storage.db.converter.markers.version.v3.V600AddAccessDecryptAllUsersMarkerMigration;
import org.artifactory.storage.db.converter.markers.version.v4.V658AnonAccessToBuildsMarkerMigration;

/**
 * @author Omri Ziv
 */
public enum MarkerConverterVersion {
    //v5.6.0
    v1(new V560AddAccessEmigrateMarkerMigration()),
    //v5.10.0
    v2(new V5100AddAccessResourceTypeMarkerMigration()),
    //v6.0.0
    v3(new V600AddAccessDecryptAllUsersMarkerMigration()),
    //v6.5.8
    v4(new V658AnonAccessToBuildsMarkerMigration());

    private final MarkerMigrationConverter[] converters;

    MarkerConverterVersion(MarkerMigrationConverter... markerMigrationConverters) {
        this.converters = markerMigrationConverters;
    }

    public MarkerMigrationConverter[] getConverters() {
        return converters;
    }
}
