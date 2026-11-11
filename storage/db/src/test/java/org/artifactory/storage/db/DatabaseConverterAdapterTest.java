package org.artifactory.storage.db;

import org.artifactory.common.home.ArtifactoryHome;
import org.jfrog.converter.DbDetails;
import org.jfrog.converter.DbVersion;
import org.jfrog.converter.model.ConverterExecutionType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * Verify that DatabaseConverterAdapter processes the right DBConverters
 */
class DatabaseConverterAdapterTest {

    @Test
    void testGetDbVersions() {
        DatabaseConverterAdapter adapter =
                new DatabaseConverterAdapter(mock(ArtifactoryHome.class), mock(DbDetails.class));
        DbVersion[] dbVersions = adapter.getDbVersions();
        for (DbVersion dbVersion : dbVersions) {
            assertEquals(ConverterExecutionType.SEQ_SYNC, dbVersion.type());
        }
    }
}
