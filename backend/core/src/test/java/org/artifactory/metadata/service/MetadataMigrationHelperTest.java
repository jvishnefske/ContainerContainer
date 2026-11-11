package org.artifactory.metadata.service;

import com.google.common.collect.ImmutableList;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.aql.AqlService;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.event.EventOperatorId;
import org.artifactory.event.work.NodeEventsErrorService;
import org.artifactory.metadata.migration.MetadataMigrationInfoBlob;
import org.artifactory.model.LocalConfig;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.storage.db.event.entity.enums.NodeEventCursorType;
import org.artifactory.storage.db.event.model.NodeEventCursor;
import org.artifactory.storage.db.event.service.NodeEventCursorService;
import org.artifactory.storage.db.fs.dao.NodesDao;
import org.artifactory.storage.db.init.migration.model.MigrationStatus;
import org.artifactory.storage.db.init.migration.service.MigrationStatusStorageService;
import org.artifactory.storage.event.EventsService;
import org.jfrog.common.ClockUtils;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.sql.SQLException;
import java.util.Collections;

import static org.artifactory.metadata.service.MetadataMigrationHelper.MIGRATION_IDENTIFIER;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.AssertJUnit.assertFalse;

/**
 * @author Uriah Levy
 */
@Test
public class MetadataMigrationHelperTest {

    @Mock
    private MigrationStatusStorageService migrationStatusStorageService;
    @Mock
    private NodeEventCursorService cursorService;
    @Mock
    private NodesDao nodesDao;
    @Mock
    private AqlService aqlService;
    @Mock
    private RepositoryService repoService;
    @Mock
    private MetadataEventService metadataEventService;
    @Mock
    private EventsService eventsService;
    @Mock
    private MetadataEntityFacade metadataEntityFacade;
    @Mock
    private NodeEventsErrorService nodeEventsErrorService;
    private AutoCloseable mocks;

    @BeforeMethod
    private void beforeMethod() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        mocks.close();
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void shouldMigrateThrowsRuntimeAfterSqlException() throws SQLException {
        MetadataMigrationHelper metadataMigrationHelper = new MetadataMigrationHelper(migrationStatusStorageService,
                cursorService, nodesDao, aqlService, eventsService, repoService, metadataEntityFacade,
                metadataEventService, nodeEventsErrorService);
        when(nodesDao.findLastNodeId()).thenThrow(SQLException.class);
        metadataMigrationHelper.shouldMigrate();
    }

    public void shouldMigrateFalseOnNoNodes() throws SQLException {
        MetadataMigrationHelper metadataMigrationHelper = new MetadataMigrationHelper(migrationStatusStorageService,
                cursorService, nodesDao, aqlService, eventsService, repoService, metadataEntityFacade,
                metadataEventService, nodeEventsErrorService);

        when(nodesDao.findLastNodeId()).thenReturn(0L);

        assertFalse(metadataMigrationHelper.shouldMigrate());
        verify(migrationStatusStorageService).insertMigration(any());
    }

    public void testHandleMissingMigration() throws SQLException {
        MetadataMigrationHelper metadataMigrationHelper = new MetadataMigrationHelper(migrationStatusStorageService,
                cursorService, nodesDao, aqlService, eventsService, repoService, metadataEntityFacade,
                metadataEventService, nodeEventsErrorService);

        long targetNodeId = 11245;
        when(nodesDao.findLastNodeId()).thenReturn(targetNodeId);
        long lastEventLogTimestamp = ClockUtils.epochMillis();
        when(eventsService.getLastEventLogTimestamp()).thenReturn(lastEventLogTimestamp);

        metadataMigrationHelper.initMigrationState();
        verify(migrationStatusStorageService)
                .insertMigrationWithInfoBlob(argThat(migrationStatus ->
                        ((MetadataMigrationInfoBlob) migrationStatus.getMigrationInfoBlob())
                                .getMigrationTargetNodeId() == targetNodeId &&
                                ((MetadataMigrationInfoBlob) migrationStatus.getMigrationInfoBlob())
                                        .getCurrentEventLogTimestamp() == lastEventLogTimestamp));

    }

    public void testShouldMigrateFalseOnNoReposToMigrate() throws SQLException {
        MetadataMigrationHelper metadataMigrationHelper = new MetadataMigrationHelper(migrationStatusStorageService,
                cursorService, nodesDao, aqlService, eventsService, repoService, metadataEntityFacade,
                metadataEventService, nodeEventsErrorService);

        when(nodesDao.findLastNodeId()).thenReturn(1L);

        when(repoService.getLocalFederatedAndCachedRepoConfigs()).thenReturn(Collections.emptyList());

        boolean shouldMigrate = metadataMigrationHelper.shouldMigrate();
        assertFalse(shouldMigrate);
        verify(migrationStatusStorageService)
                .insertMigration(argThat(migrationStatus -> migrationStatus.getMigrationInfoBlob()
                        .equals(new MetadataMigrationInfoBlob())));
        verify(cursorService).updateCursor(any(), anyLong());

        LocalConfig pypiRepo = LocalRepoConfig.create("repoKey", RepoType.Pypi);

        when(repoService.getLocalFederatedAndCachedRepoConfigs()).thenReturn(ImmutableList.of(pypiRepo));
        // Not a supported repo type, still false
        shouldMigrate = metadataMigrationHelper.shouldMigrate();
        assertFalse(shouldMigrate);
        verify(migrationStatusStorageService, times(2))
                .insertMigration(argThat(migrationStatus -> migrationStatus.getMigrationInfoBlob()
                        .equals(new MetadataMigrationInfoBlob())));
    }

    public void testShouldMigrateTrueWithReposToMigrate() throws SQLException {
        MetadataMigrationHelper metadataMigrationHelper = new MetadataMigrationHelper(migrationStatusStorageService,
                cursorService, nodesDao, aqlService, eventsService, repoService, metadataEntityFacade,
                metadataEventService, nodeEventsErrorService);

        when(nodesDao.findLastNodeId()).thenReturn(1L);
        LocalConfig dockerRepo = LocalRepoConfig.create("repoKey", RepoType.Docker);

        when(repoService.getLocalFederatedAndCachedRepoConfigs()).thenReturn(ImmutableList.of(dockerRepo));
        when(metadataEntityFacade.getSupportedRepoTypes()).thenReturn(ImmutableList.of(RepoType.Docker));

        boolean shouldMigrate = metadataMigrationHelper.shouldMigrate();
        assertTrue(shouldMigrate);
        verify(migrationStatusStorageService, times(0)).insertMigration(any());
    }

    public void testShouldMigrateByCursorTrue() throws SQLException {
        MetadataMigrationHelper metadataMigrationHelper = new MetadataMigrationHelper(migrationStatusStorageService,
                cursorService, nodesDao, aqlService, eventsService, repoService, metadataEntityFacade,
                metadataEventService, nodeEventsErrorService);

        when(nodesDao.findLastNodeId()).thenReturn(ClockUtils.epochMillis());
        NodeEventCursor currentCursor = new NodeEventCursor(EventOperatorId.METADATA_OPERATOR_ID.getId(),
                ClockUtils.epochMillis() + 100, NodeEventCursorType.METADATA_MIGRATION);

        LocalConfig dockerRepo = LocalRepoConfig.create("repoKey", RepoType.Docker);
        when(metadataEventService.getOperatorId()).thenReturn(EventOperatorId.METADATA_OPERATOR_ID.getId());
        when(metadataEventService.getMigrationCursorId())
                .thenReturn(EventOperatorId.METADATA_OPERATOR_ID.getId() + "-migration");
        when(repoService.getLocalFederatedAndCachedRepoConfigs()).thenReturn(ImmutableList.of(dockerRepo));
        when(cursorService.cursorForOperator(EventOperatorId.METADATA_OPERATOR_ID.getId() + "-migration"))
                .thenReturn(currentCursor);
        when(metadataEntityFacade.getSupportedRepoTypes()).thenReturn(ImmutableList.of(RepoType.Docker));
        when(migrationStatusStorageService.findMigrationByIdWithInfoBlob(MIGRATION_IDENTIFIER,
                MetadataMigrationInfoBlob.class))
                .thenReturn(new MigrationStatus(MIGRATION_IDENTIFIER, ClockUtils.epochMillis()
                        - 10_000, 0, new MetadataMigrationInfoBlob(ClockUtils.epochMillis() + Integer.MAX_VALUE, 0,
                        ImmutableList.of("docker-local", "npm-local"))));
        assertTrue(metadataMigrationHelper.shouldMigrate());
    }

    public void testInitMigrationStatusWithExistingMigration() {
        MetadataMigrationHelper metadataMigrationHelper = spy(new MetadataMigrationHelper(migrationStatusStorageService,
                cursorService, nodesDao, aqlService, eventsService, repoService, metadataEntityFacade,
                metadataEventService, nodeEventsErrorService));

        when(migrationStatusStorageService.findMigrationById(MIGRATION_IDENTIFIER))
                .thenReturn(new MigrationStatus(MIGRATION_IDENTIFIER, ClockUtils.epochMillis()
                        - 10_000, 0,
                        new MetadataMigrationInfoBlob(ClockUtils.epochMillis() + Integer.MAX_VALUE, 0, null)));
        when(migrationStatusStorageService
                .findMigrationByIdWithInfoBlob(MIGRATION_IDENTIFIER, MetadataMigrationInfoBlob.class))
                .thenReturn(new MigrationStatus(MIGRATION_IDENTIFIER, ClockUtils.epochMillis()
                        - 10_000, 0,
                        new MetadataMigrationInfoBlob(ClockUtils.epochMillis() + Integer.MAX_VALUE, 0, null)));

        metadataMigrationHelper.initMigrationState();

        verify(eventsService, times(0)).getLastEventLogTimestamp();
        verify(metadataMigrationHelper).getMigrationTargetNodeId();
    }

    public void testInitMigrationStatusWithNonExistingMigration() throws SQLException {
        MetadataMigrationHelper metadataMigrationHelper = spy(new MetadataMigrationHelper(migrationStatusStorageService,
                cursorService, nodesDao, aqlService, eventsService, repoService, metadataEntityFacade,
                metadataEventService, nodeEventsErrorService));

        when(migrationStatusStorageService.findMigrationById(MIGRATION_IDENTIFIER))
                .thenReturn(null);
        when(nodesDao.findLastNodeId()).thenReturn(10_000L);
        doNothing().when(metadataMigrationHelper).insertNewDueMigration();

        metadataMigrationHelper.initMigrationState();

        verify(metadataMigrationHelper, times(0)).insertOrSetCompletedMigrationAndUpdateOperatorCursor();
        verify(metadataMigrationHelper, times(0)).getMigrationTargetNodeId();
        verify(metadataMigrationHelper).insertNewDueMigration();
        verify(eventsService).getLastEventLogTimestamp();
    }

    public void assertMigrationIdIsSame() {
        assertEquals(MetadataMigrationHelper.MIGRATION_IDENTIFIER, "metadata-service-migration");
    }
}