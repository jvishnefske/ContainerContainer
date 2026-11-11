package org.artifactory.storage.db.init.migration.service;

import com.google.common.collect.Lists;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.init.migration.MarkerMigrationInfoBlob;
import org.artifactory.storage.db.init.migration.dao.MigrationStatusDao;
import org.artifactory.storage.db.init.migration.entity.DbMigrationStatus;
import org.artifactory.storage.db.init.migration.model.MigrationStatus;
import org.jfrog.common.JsonUtils;
import org.jfrog.commons.continues.util.Direction;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.sql.SQLException;
import java.util.Collection;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * @author Mor Merhav
 */
public class MigrationStatusStorageServiceImplTest {

    private MigrationStatusStorageService migrationStatusStorageService;
    private MockitoSession mockitoSession;

    @Mock
    private MigrationStatusDao migrationStatusDao;

    @BeforeMethod
    public void setUp() {
        mockitoSession = Mockito.mockitoSession()
                .strictness(Strictness.STRICT_STUBS)
                .initMocks(this)
                .startMocking();

        migrationStatusStorageService = new MigrationStatusStorageServiceImpl(migrationStatusDao);
    }

    @AfterMethod
    public void tearDown() {
        mockitoSession.finishMocking();
    }

    @Test
    public void testFindAllUnfinishedByPrefixWithInfoBlob() throws SQLException {
        String prefix = "prefix";
        MarkerMigrationInfoBlob infoBlob = new MarkerMigrationInfoBlob("Serverid");
        byte[] infoBlobBytes = JsonUtils.getInstance().valueToByteArray(infoBlob);
        DbMigrationStatus dbMigrationStatus = new DbMigrationStatus("id", 1L, 2L, infoBlobBytes);
        Collection<DbMigrationStatus> dbMigrationStatuses = Lists.newArrayList(dbMigrationStatus);
        when(migrationStatusDao.findAllUnfinishedByPrefix(prefix)).thenReturn(dbMigrationStatuses);

        List<MigrationStatus> migrationStatuses = migrationStatusStorageService
                .findAllUnfinishedByPrefixWithInfoBlob(prefix, MarkerMigrationInfoBlob.class);

        assertNotNull(migrationStatuses);
        assertEquals(migrationStatuses.size(), 1);

        MigrationStatus migrationStatus = migrationStatuses.get(0);
        assertEquals(migrationStatus.getIdentifier(), "id");
        assertEquals(migrationStatus.getStarted(), 1L);
        assertEquals(migrationStatus.getFinished(), 2L);
        assertTrue(migrationStatus.isFinished());
        assertNotNull(migrationStatus.getMigrationInfoBlob());
        assertEquals(migrationStatus.getMigrationInfoBlob().getModel(), MarkerMigrationInfoBlob.class);
        MarkerMigrationInfoBlob actualInfoBlob = (MarkerMigrationInfoBlob) migrationStatus.getMigrationInfoBlob();
        assertEquals(actualInfoBlob, infoBlob);
    }

    @Test
    public void testFindStartedSinceContainsPrefix() throws SQLException {
        String prefix = "prefix";
        long createTime = 10000;
        migrationStatusStorageService.findStartedSinceContainsPrefix(prefix, createTime);
        verify(migrationStatusDao).findStartedBeforeDateContainsPrefix(prefix, createTime);
    }

    @Test(expectedExceptions = StorageException.class)
    public void testFindStartedSinceContainsPrefixThrowsException() throws SQLException {
        doThrow(new SQLException()).when(migrationStatusDao).findStartedBeforeDateContainsPrefix(any(), anyLong());
        migrationStatusStorageService.findStartedSinceContainsPrefix(any(), anyLong());
    }

    @Test
    public void testFindMigrationByIdPrefixWithInfoBlob() throws SQLException {
        String identifier = "prefix";
        DbMigrationStatus dbMigrationStatus =
                new DbMigrationStatus(identifier, Long.MIN_VALUE, Long.MAX_VALUE, null);
        when(migrationStatusDao.findMigrationsByIdPrefixBetweenDates(any(), any(), any(), any(), any()))
                .thenReturn(List.of(dbMigrationStatus));
        List<MigrationStatus> statuses =
                migrationStatusStorageService.findMigrationByIdPrefixWithInfoBlob(identifier, "_", null);
        verify(migrationStatusDao).findMigrationsByIdPrefixBetweenDates(identifier, "_", Direction.DESC, null, null);
        assertEquals(statuses.size(), 1);
        assertEquals(statuses.get(0).getIdentifier(), identifier);
        assertEquals(statuses.get(0).getStarted(), Long.MIN_VALUE);
        assertEquals(statuses.get(0).getFinished(), Long.MAX_VALUE);
        assertEquals(statuses.get(0).getMigrationInfoBlob(), null);
    }

    @Test
    public void testFindMigrationByIdPrefixWithInfoBlobBetweenDates() throws SQLException {
        migrationStatusStorageService.findMigrationByIdPrefixWithInfoBlobBetweenDates("prefix", Long.MIN_VALUE,
                Long.MAX_VALUE, null);
        verify(migrationStatusDao).findMigrationsByIdPrefixBetweenDates("prefix", null, Direction.DESC, Long.MIN_VALUE,
                Long.MAX_VALUE);
    }

    @Test
    public void testFindMigrationByIdPrefixWithInfoBlobBetweenDatesWithoutPrefix() throws SQLException {
        migrationStatusStorageService.findMigrationByIdPrefixWithInfoBlobBetweenDates(null, Long.MIN_VALUE,
                Long.MAX_VALUE, null);
        verify(migrationStatusDao, times(0)).findMigrationsByIdPrefixBetweenDates(any(), any(), any(), any(), any());
    }

    @Test(expectedExceptions = StorageException.class)
    public void testFindMigrationByIdPrefixWithInfoBlobBetweenDatesThrowsException() throws SQLException {
        when(migrationStatusDao.findMigrationsByIdPrefixBetweenDates("prefix", null, Direction.DESC, Long.MIN_VALUE,
                Long.MAX_VALUE)).thenThrow(SQLException.class);
        migrationStatusStorageService.findMigrationByIdPrefixWithInfoBlobBetweenDates("prefix", Long.MIN_VALUE,
                Long.MAX_VALUE, null);
    }
}