package org.artifactory.storage.db.init.migration;

import com.google.common.collect.Lists;
import org.artifactory.common.ha.HaNodeProperties;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.storage.db.init.migration.dao.MigrationStatusDao;
import org.artifactory.storage.db.init.migration.entity.DbMigrationStatus;
import org.artifactory.storage.db.init.migration.model.MigrationStatus;
import org.artifactory.storage.db.init.migration.service.MigrationStatusStorageService;
import org.artifactory.storage.db.init.migration.service.MigrationStatusStorageServiceImpl;
import org.artifactory.storage.db.init.migration.service.mapper.MigrationStatusMapperImpl;
import org.jfrog.common.ClockUtils;
import org.jfrog.common.JsonUtils;
import org.jfrog.common.StreamSupportUtils;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * @author Omri Ziv
 */
public class HaMarkerMigrationHelperTest {

    private final String MIGRATION_ID = "migration_id_x12";
    @Mock
    MigrationStatusDao migrationStatusDao;
    private final String MOCK_SERVER_ID = "testServerId";
    @Mock
    private ArtifactoryHome home;
    @Mock
    private HaNodeProperties haNodeProperties;

    private HaMarkerMigrationHelper markerMigrationHelper;
    private AutoCloseable autoCloseable;

    @BeforeClass
    public void setup() {
        autoCloseable = MockitoAnnotations.openMocks(this);
        MigrationStatusStorageService migrationStatusStorageService =
                new MigrationStatusStorageServiceImpl(migrationStatusDao);
        markerMigrationHelper = new HaMarkerMigrationHelper(migrationStatusStorageService,
                home);
        when(home.getHaNodeProperties()).thenReturn(haNodeProperties);
        when(haNodeProperties.getServerId()).thenReturn(MOCK_SERVER_ID);
        ArtifactoryHome.bind(home);
    }

    @AfterClass
    public void tearDown() throws Exception {
        ArtifactoryHome.unbind();
        autoCloseable.close();
    }

    @Test
    public void testMigrationNotExists() throws SQLException {
        when(migrationStatusDao.findMigrationById(MIGRATION_ID))
                .thenReturn(Optional.empty());
        assertNull(markerMigrationHelper.findByUniqueIdentifier(MIGRATION_ID));
    }

    @Test
    public void testFindMigration() throws SQLException {
        MigrationStatus migrationStatus = createMigrationStatus(MIGRATION_ID, MOCK_SERVER_ID);
        when(migrationStatusDao.findMigrationById(MIGRATION_ID))
                .thenReturn(Optional.of(convert(migrationStatus)));
        MigrationStatus ans = markerMigrationHelper.findByUniqueIdentifier(MIGRATION_ID);
        assertEquals(MIGRATION_ID, ans.getIdentifier());
        assertEquals(((MarkerMigrationInfoBlob) ans.getMigrationInfoBlob()).getServerId(), MOCK_SERVER_ID);
    }

    @Test
    public void testFinish() throws Exception {
        MigrationStatus migrationStatus = createMigrationStatus(MIGRATION_ID, MOCK_SERVER_ID);
        AtomicLong finishTime = new AtomicLong();
        doAnswer(invocationOnMock -> {
            finishTime.set(invocationOnMock.getArgument(1));
            migrationStatus.setFinished(finishTime.get());
            return null;
        }).when(migrationStatusDao)
                .updateFinishTimeById(any(), anyLong());

        markerMigrationHelper.finish(MIGRATION_ID);
        assertTrue(finishTime.get() > 0);
        assertEquals(finishTime.get(), migrationStatus.getFinished());
    }

    @Test
    public void insertMigration() throws SQLException {
        AtomicReference<DbMigrationStatus> dbMigrationStatus = new AtomicReference<>();
        doAnswer(invocationOnMock -> {
            dbMigrationStatus.set(invocationOnMock.getArgument(0));
            return null;
        })
                .when(migrationStatusDao)
                .insertMigration(argThat(arg -> arg.getIdentifier().equals(MIGRATION_ID)));
        markerMigrationHelper.insertMigrationOfIdentifier(MIGRATION_ID);
        MigrationStatus migrationStatus = convert(dbMigrationStatus.get());
        assertEquals(migrationStatus.getIdentifier(), MIGRATION_ID);
        assertEquals(migrationStatus.getFinished(), 0);
        assertTrue(migrationStatus.getStarted() > 0);
        assertEquals(((MarkerMigrationInfoBlob) migrationStatus.getMigrationInfoBlob()).getServerId(), MOCK_SERVER_ID);
    }

    @Test
    public void getUnfinishedNull() {
        List<MigrationStatus> migrationStatusList = markerMigrationHelper.getUnfinished(null);
        assertTrue(migrationStatusList.isEmpty());
    }

    @Test
    public void getUnfinishedEmpty() {
        List<MigrationStatus> migrationStatusList = markerMigrationHelper.getUnfinished(Lists.newArrayList());
        assertTrue(migrationStatusList.isEmpty());
    }

    @Test
    public void getUnfinished() throws SQLException {
        doAnswer(invocationOnMock -> {
            List<String> migrationIds = invocationOnMock.getArgument(0);
            return StreamSupportUtils.stream(migrationIds)
                    .map(migrationId -> createMigrationStatus(migrationId, MOCK_SERVER_ID))
                    .map(this::convert)
                    .collect(Collectors.toList());
        }).when(migrationStatusDao)
                .findAllUnfinishedIn(anyList());
        List<MigrationStatus> migrationStatuses =
                markerMigrationHelper.getUnfinished(Lists.newArrayList("id-1", "id-2", "id-3"));
        assertEquals(migrationStatuses.size(), 3);
        assertEquals(migrationStatuses.get(0).getIdentifier(), "id-1");
        assertEquals(migrationStatuses.get(0).getFinished(), 0);
        assertNull(migrationStatuses.get(0).getMigrationInfoBlob());
        assertEquals(migrationStatuses.get(1).getIdentifier(), "id-2");
        assertEquals(migrationStatuses.get(1).getFinished(), 0);
        assertEquals(migrationStatuses.get(2).getIdentifier(), "id-3");
        assertEquals(migrationStatuses.get(2).getFinished(), 0);
    }

    @Test
    public void shouldConvertWhileNotExists() throws SQLException {
        when(migrationStatusDao.findMigrationById(MIGRATION_ID))
                .thenReturn(Optional.empty());
        assertFalse(markerMigrationHelper.shouldConvert(MIGRATION_ID));
    }

    @Test
    public void shouldConvertWhileFinished() throws SQLException {
        MigrationStatus migrationStatus = createMigrationStatus(MIGRATION_ID, MOCK_SERVER_ID);
        migrationStatus.setFinished(ClockUtils.epochMillis());
        when(migrationStatusDao.findMigrationById(MIGRATION_ID))
                .thenReturn(Optional.of(convert(migrationStatus)));
        assertFalse(markerMigrationHelper.shouldConvert(MIGRATION_ID));
    }

    @Test
    public void shouldConvertDifferentServerId() throws SQLException {
        MigrationStatus migrationStatus = createMigrationStatus(MIGRATION_ID, "differentServerId");
        when(migrationStatusDao.findMigrationById(MIGRATION_ID))
                .thenReturn(Optional.of(convert(migrationStatus)));
        assertFalse(markerMigrationHelper.shouldConvert(MIGRATION_ID));
    }

    @Test
    public void shouldConvertSuccessful() throws SQLException {
        MigrationStatus migrationStatus = createMigrationStatus(MIGRATION_ID, MOCK_SERVER_ID);
        when(migrationStatusDao.findMigrationById(MIGRATION_ID))
                .thenReturn(Optional.of(convert(migrationStatus)));
        assertTrue(markerMigrationHelper.shouldConvert(MIGRATION_ID));
    }

    @Test
    public void reset() throws SQLException {
        List<DbMigrationStatus> dbMigrationStatuses = new ArrayList<>();
        MigrationStatus migrationStatus = createMigrationStatus(MIGRATION_ID, MOCK_SERVER_ID);
        migrationStatus.setFinished(ClockUtils.epochMillis());
        dbMigrationStatuses.add(convert(migrationStatus));
        doAnswer(invocationOnMock -> {
            dbMigrationStatuses.add(invocationOnMock.getArgument(0));
            return null;
        }).when(migrationStatusDao)
                .insertMigration(argThat(arg -> arg.getIdentifier().equals(MIGRATION_ID)));

        doAnswer(invocationOnMock -> {
            String identifier = invocationOnMock.getArgument(0);
            dbMigrationStatuses.removeIf(dbMigrationStatus -> dbMigrationStatus.getIdentifier().equals(identifier));
            return null;
        }).when(migrationStatusDao)
                .delete(MIGRATION_ID);

        markerMigrationHelper.reset(MIGRATION_ID);
        assertEquals(dbMigrationStatuses.size(), 1);
        assertEquals(dbMigrationStatuses.get(0).getFinished(), 0);
    }

    @Test
    public void resetNonExisted() throws SQLException {
        List<DbMigrationStatus> dbMigrationStatuses = new ArrayList<>();
        MigrationStatus migrationStatus = createMigrationStatus("anotherId", MOCK_SERVER_ID);
        migrationStatus.setFinished(ClockUtils.epochMillis());
        dbMigrationStatuses.add(convert(migrationStatus));
        doAnswer(invocationOnMock -> {
            dbMigrationStatuses.add(invocationOnMock.getArgument(0));
            return null;
        }).when(migrationStatusDao)
                .insertMigration(argThat(arg -> arg.getIdentifier().equals(MIGRATION_ID)));

        doAnswer(invocationOnMock -> {
            String identifier = invocationOnMock.getArgument(0);
            dbMigrationStatuses.removeIf(dbMigrationStatus -> dbMigrationStatus.getIdentifier().equals(identifier));
            return null;
        }).when(migrationStatusDao)
                .delete(MIGRATION_ID);

        markerMigrationHelper.reset(MIGRATION_ID);
        assertEquals(dbMigrationStatuses.size(), 2);
        assertEquals(dbMigrationStatuses.get(0).getIdentifier(), "anotherId");
        assertNotEquals(dbMigrationStatuses.get(0).getFinished(), 0);
        assertEquals(dbMigrationStatuses.get(1).getIdentifier(), MIGRATION_ID);
        assertEquals(dbMigrationStatuses.get(1).getFinished(), 0);
    }

    private MigrationStatus createMigrationStatus(String identifier, String serviceId) {
        final long started = ClockUtils.epochMillis();

        MigrationStatus migrationStatus = new MigrationStatus();
        migrationStatus.setIdentifier(identifier);
        migrationStatus.setStarted(started);
        migrationStatus.setFinished(0);
        migrationStatus.setMigrationInfoBlob(new MarkerMigrationInfoBlob(serviceId));
        return migrationStatus;
    }

    private MigrationStatus convert(DbMigrationStatus dbMigrationStatus) {
        MigrationStatus migrationStatus = new MigrationStatusMapperImpl()
                .dbMigrationStatusToMigrationStatus(dbMigrationStatus);
        migrationStatus.setMigrationInfoBlob(
                JsonUtils
                        .getInstance()
                        .readValue(dbMigrationStatus.getMigrationInfoBlob(), MarkerMigrationInfoBlob.class));
        return migrationStatus;
    }

    private DbMigrationStatus convert(MigrationStatus migrationStatus) {
        MigrationStatusMapperImpl migrationStatusMapper = new MigrationStatusMapperImpl();
        return migrationStatusMapper.migrationStatusToDbMigrationStatus(migrationStatus);
    }

}
