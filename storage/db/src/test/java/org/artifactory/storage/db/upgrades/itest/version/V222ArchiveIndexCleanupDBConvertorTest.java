package org.artifactory.storage.db.upgrades.itest.version;

import lombok.SneakyThrows;
import org.artifactory.storage.db.servers.model.ArtifactoryServer;
import org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService;
import org.artifactory.storage.db.upgrades.common.AsyncUpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.converter.model.ChangeLogContext;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.sql.SQLException;
import java.util.List;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v222;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertTrue;
import static org.testng.AssertJUnit.assertEquals;

/**
 * @author Evgeny Kurtser
 */
public class V222ArchiveIndexCleanupDBConvertorTest extends AsyncUpgradeBaseTest {
    @Mock
    private ArtifactoryServersCommonService serversService;

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v222;
    }

    @Test(dataProvider = "rtOnOtherPods")
    @SneakyThrows
    void tablesTruncated(List<ArtifactoryServer> list, boolean shouldConvert) {
        assertTablesNotEmpty();

        when(context.beanForType(ArtifactoryServersCommonService.class)).thenReturn(serversService);
        when(serversService.getAllArtifactoryServers()).thenReturn(list);
        try (ChangeLogContext context = new ChangeLogContext(jdbcHelper.getDataSource().getConnection(),
                dbProperties.getDbType(), v222)) {
            v222.converter().convert(context);
        }
        if (shouldConvert) {
            assertTablesEmpty();
            restoreArchiveTables();
        } else {
            assertTablesNotEmpty();
        }
    }

    private void assertTablesNotEmpty() {
        assertTable("indexed_archives_entries", false);
        assertTable("indexed_archives", false);
        assertTable("archive_paths", false);
        assertTable("archive_names", false);
    }

    private void assertTablesEmpty() {
        assertTable("indexed_archives_entries", true);
        assertTable("indexed_archives", true);
        assertTable("archive_paths", true);
        assertTable("archive_names", true);
    }

    @SneakyThrows
    private void assertTable(String tableName, boolean empty) {
        assertTrue(tableExists(tableName));
        assertEquals(empty, tableEmpty(tableName));
    }

    @DataProvider
    private static Object[][] rtOnOtherPods() {
        // Older than v.7.88.0
        ArtifactoryServer rtServerOlder = Mockito.mock(ArtifactoryServer.class);
        when(rtServerOlder.getArtifactoryRevision()).thenReturn(75_602_900L);
        // v.7.88.0
        ArtifactoryServer rtServerSame = Mockito.mock(ArtifactoryServer.class);
        when(rtServerSame.getArtifactoryRevision()).thenReturn(78_000_900L);
        // Newer than v.7.88.0
        ArtifactoryServer rtServerNewer = Mockito.mock(ArtifactoryServer.class);
        when(rtServerNewer.getArtifactoryRevision()).thenReturn(79_602_900L);

        return new Object[][] {
                {List.of(rtServerOlder), false},
                {List.of(rtServerSame, rtServerNewer), true},
                {List.of(rtServerNewer), true},
                {List.of(), true}
        };
    }

    private void restoreArchiveTables() throws SQLException {
        jdbcHelper.executeBulkUpdate("INSERT INTO indexed_archives VALUES (?, ?)",
                List.of(
                        new Object[]{"dcab88fc2a043c2479a6de676a2f8179e9ea2167", 6000},
                        new Object[]{"bbbb88fc2a043c2479a6de676a2f8179e9eabbbb", 6001}
                ));

        jdbcHelper.executeBulkUpdate("INSERT INTO archive_paths VALUES (?, ?)",
                List.of(
                        new Object[]{8001, "META-INF"},
                        new Object[]{8002, "org/apache/tools/ant/filters"},
                        new Object[]{8003, "org/apache/tools/mail"},
                        new Object[]{8004, "."},
                        new Object[]{8005, "another"}
                ));

        jdbcHelper.executeBulkUpdate("INSERT INTO archive_names VALUES (?, ?)",
                List.of(
                        new Object[]{9001, "LICENSE.txt"},
                        new Object[]{9002, "MANIFEST.MF"},
                        new Object[]{9003, "BaseFilterReader.class"},
                        new Object[]{9004, "BaseParamFilterReader.class"},
                        new Object[]{9005, "MailMessage.class"},
                        new Object[]{9006, "Test"},
                        new Object[]{9007, "test.me"}
                ));

        jdbcHelper.executeBulkUpdate("INSERT INTO indexed_archives_entries VALUES (?, ?, ?)",
                List.of(
                        new Object[]{6000, 8001, 9001},
                        new Object[]{6000, 8001, 9002},
                        new Object[]{6000, 8002, 9003},
                        new Object[]{6000, 8002, 9004},
                        new Object[]{6000, 8003, 9005},
                        new Object[]{6001, 8004, 9006},
                        new Object[]{6001, 8005, 9007}
                ));
    }
}