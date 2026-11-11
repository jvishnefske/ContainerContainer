package org.artifactory.storage.db.federated.gridtopology;

import org.artifactory.model.GridTopology;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for GridTopologyDao.
 *
 * @author Tomer Ben Kereth
 */
class GridTopologyDaoTest {

    private static final String TOPOLOGY_ID_1 = "topology-1";
    private static final String TOPOLOGY_ID_2 = "topology-2";
    private static final String NON_EXISTENT_TOPOLOGY_ID = "non-existent";
    private static final String TOPOLOGY_TYPE_SELF_DEFINED = "SELF_DEFINED";
    private static final String TOPOLOGY_TYPE_MIRROR = "MIRROR";
    private static final String SITE_1 = "site1";
    private static final String SITE_2 = "site2";
    private static final String SITE_3 = "site3";
    private static final String EMPTY_JSON_ARRAY = "[]";
    private static final String SITES_JSON_1_2 = "[\"site1\",\"site2\"]";
    private static final String SITES_JSON_1_2_3 = "[\"site1\",\"site2\",\"site3\"]";
    private static final String DATABASE_ERROR = "Database error";
    private static final String SELECT_ALL_TOPOLOGIES = """
            SELECT * FROM grid_topology
            """;
    private static final String SELECT_TOPOLOGY_BY_ID = """
            SELECT * FROM grid_topology WHERE grid_topology_id = ?
            """;
    private static final String INSERT_TOPOLOGY = """
            INSERT INTO grid_topology (grid_topology_id, topology_type,
                additional_sites, creation_date, modification_date) VALUES (?, ?, ?, ?, ?)
            """;
    private static final String UPDATE_TOPOLOGY = """
            UPDATE grid_topology SET additional_sites = ?, modification_date = ?
                WHERE grid_topology_id = ?
            """;
    private static final String DELETE_TOPOLOGY = """
            DELETE FROM grid_topology WHERE grid_topology_id = ?
            """;

    @Mock
    private ArtifactoryJdbcHelper jdbcHelper;

    private GridTopologyDao gridTopologyDao;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        gridTopologyDao = new GridTopologyDao(jdbcHelper);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
    }

    @ParameterizedTest
    @MethodSource("getAllTopologiesProvider")
    void getAllTopologies_shouldReturnTopologies_whenTopologiesExist(int expectedCount, ResultSet resultSet)
            throws SQLException {
        doReturn(resultSet).when(jdbcHelper).executeSelect(anyString());

        List<GridTopology> result = gridTopologyDao.getAllTopologies();

        assertEquals(expectedCount, result.size());
        if (expectedCount > 0) {
            assertEquals(TOPOLOGY_ID_1, result.get(0).getGridTopologyId());
            if (expectedCount > 1) {
                assertEquals(TOPOLOGY_ID_2, result.get(1).getGridTopologyId());
            }
        }
        verify(jdbcHelper).executeSelect(SELECT_ALL_TOPOLOGIES);
    }

    private static Stream<Arguments> getAllTopologiesProvider() throws SQLException {
        ResultSet emptyResultSet = mock(ResultSet.class);
        when(emptyResultSet.next()).thenReturn(false);

        ResultSet singleResultSet = createMockResultSet(TOPOLOGY_TYPE_SELF_DEFINED, EMPTY_JSON_ARRAY);
        when(singleResultSet.next()).thenReturn(true).thenReturn(false);

        ResultSet multipleResultSet = mock(ResultSet.class);
        when(multipleResultSet.next()).thenReturn(true).thenReturn(true).thenReturn(false);
        when(multipleResultSet.getString("grid_topology_id")).thenReturn(TOPOLOGY_ID_1)
                .thenReturn(TOPOLOGY_ID_2);
        when(multipleResultSet.getString("topology_type")).thenReturn(TOPOLOGY_TYPE_SELF_DEFINED)
                .thenReturn(TOPOLOGY_TYPE_MIRROR);
        when(multipleResultSet.getBinaryStream("additional_sites"))
                .thenReturn(new ByteArrayInputStream(EMPTY_JSON_ARRAY.getBytes(StandardCharsets.UTF_8)))
                .thenReturn(new ByteArrayInputStream(SITES_JSON_1_2.getBytes(StandardCharsets.UTF_8)));
        when(multipleResultSet.getLong("creation_date")).thenReturn(1000L)
                .thenReturn(2000L);
        when(multipleResultSet.getLong("modification_date")).thenReturn(3000L)
                .thenReturn(4000L);

        return Stream.of(
                Arguments.of(0, emptyResultSet),
                Arguments.of(1, singleResultSet),
                Arguments.of(2, multipleResultSet)
        );
    }

    @Test
    void getAllTopologies_shouldThrowSQLException_whenDatabaseErrorOccurs() throws SQLException {
        doThrow(new SQLException(DATABASE_ERROR)).when(jdbcHelper).executeSelect(anyString());

        assertThrows(SQLException.class, () -> gridTopologyDao.getAllTopologies());
    }

    @ParameterizedTest
    @MethodSource("getTopologyByIdProvider")
    void getTopologyById_shouldReturnTopology_whenTopologyExists(String topologyId, ResultSet resultSet,
            boolean shouldExist) throws SQLException {
        doReturn(resultSet).when(jdbcHelper).executeSelect(anyString(), eq(topologyId));

        GridTopology result = gridTopologyDao.getTopologyById(topologyId);

        if (shouldExist) {
            assertEquals(TOPOLOGY_ID_1, result.getGridTopologyId());
            assertEquals(GridTopology.TopologyType.MIRROR, result.getTopologyType());
            assertEquals(2, result.getAdditionalSites().size());
            assertEquals(SITE_1, result.getAdditionalSites().get(0));
            assertEquals(SITE_2, result.getAdditionalSites().get(1));
        } else {
            assertNull(result);
        }
        verify(jdbcHelper).executeSelect(SELECT_TOPOLOGY_BY_ID, topologyId);
    }

    private static Stream<Arguments> getTopologyByIdProvider() throws SQLException {
        ResultSet existingResultSet = createMockResultSet(TOPOLOGY_TYPE_MIRROR, SITES_JSON_1_2);
        when(existingResultSet.next()).thenReturn(true);

        ResultSet nonExistingResultSet = mock(ResultSet.class);
        when(nonExistingResultSet.next()).thenReturn(false);

        return Stream.of(
                Arguments.of(TOPOLOGY_ID_1, existingResultSet, true),
                Arguments.of(NON_EXISTENT_TOPOLOGY_ID, nonExistingResultSet, false)
        );
    }

    @Test
    void getTopologyById_shouldThrowSQLException_whenDatabaseErrorOccurs() throws SQLException {
        doThrow(new SQLException(DATABASE_ERROR)).when(jdbcHelper).executeSelect(anyString(), anyString());

        assertThrows(SQLException.class, () -> gridTopologyDao.getTopologyById(TOPOLOGY_ID_1));
    }

    @ParameterizedTest
    @MethodSource("createTopologyProvider")
    void createTopology_shouldInsertTopology_whenValidTopologyProvided(List<String> additionalSites,
            byte[] expectedBytes) throws SQLException {
        GridTopology topology = createGridTopology(GridTopology.TopologyType.SELF_DEFINED,
                additionalSites, 2000L);

        gridTopologyDao.createTopology(topology);

        verify(jdbcHelper).executeUpdate(
                eq(INSERT_TOPOLOGY),
                eq(TOPOLOGY_ID_1),
                eq(TOPOLOGY_TYPE_SELF_DEFINED),
                eq(expectedBytes),
                eq(1000L),
                eq(2000L));
    }

    private static Stream<Arguments> createTopologyProvider() {
        return Stream.of(
                Arguments.of(List.of(SITE_1, SITE_2), SITES_JSON_1_2.getBytes(StandardCharsets.UTF_8)),
                Arguments.of(new ArrayList<>(), EMPTY_JSON_ARRAY.getBytes(StandardCharsets.UTF_8))
        );
    }

    @Test
    void createTopology_shouldThrowSQLException_whenDatabaseErrorOccurs() throws SQLException {
        GridTopology topology = createGridTopology(GridTopology.TopologyType.SELF_DEFINED,
                List.of(SITE_1), 2000L);
        doThrow(new SQLException(DATABASE_ERROR)).when(jdbcHelper).executeUpdate(anyString(), any(), any(), any(),
                any(), any());

        assertThrows(SQLException.class, () -> gridTopologyDao.createTopology(topology));
    }

    @ParameterizedTest
    @MethodSource("updateTopologyProvider")
    void updateTopology_shouldUpdateTopology_whenValidTopologyProvided(List<String> additionalSites,
            byte[] expectedBytes) throws SQLException {
        GridTopology topology = createGridTopology(GridTopology.TopologyType.MIRROR,
                additionalSites, 3000L);

        gridTopologyDao.updateTopology(topology);

        verify(jdbcHelper).executeUpdate(
                eq(UPDATE_TOPOLOGY),
                eq(expectedBytes),
                eq(3000L),
                eq(TOPOLOGY_ID_1));
    }

    private static Stream<Arguments> updateTopologyProvider() {
        return Stream.of(
                Arguments.of(List.of(SITE_1, SITE_2, SITE_3), SITES_JSON_1_2_3.getBytes(StandardCharsets.UTF_8)),
                Arguments.of(new ArrayList<>(), EMPTY_JSON_ARRAY.getBytes(StandardCharsets.UTF_8))
        );
    }

    @Test
    void updateTopology_shouldThrowSQLException_whenDatabaseErrorOccurs() throws SQLException {
        GridTopology topology = createGridTopology(GridTopology.TopologyType.SELF_DEFINED,
                List.of(SITE_1), 2000L);
        doThrow(new SQLException(DATABASE_ERROR)).when(jdbcHelper).executeUpdate(anyString(), any(), any(), any());

        assertThrows(SQLException.class, () -> gridTopologyDao.updateTopology(topology));
    }

    @Test
    void deleteTopology_shouldDeleteTopology_whenValidIdProvided() throws SQLException {
        gridTopologyDao.deleteTopology(TOPOLOGY_ID_1);

        verify(jdbcHelper).executeUpdate(
                eq(DELETE_TOPOLOGY),
                eq(TOPOLOGY_ID_1));
    }

    @Test
    void deleteTopology_shouldThrowSQLException_whenDatabaseErrorOccurs() throws SQLException {
        doThrow(new SQLException(DATABASE_ERROR)).when(jdbcHelper).executeUpdate(anyString(), anyString());

        assertThrows(SQLException.class, () -> gridTopologyDao.deleteTopology(TOPOLOGY_ID_1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"SELF_DEFINED", "MIRROR"})
    void mapRowToTopology_shouldMapTopologyTypeCorrectly_whenTopologyTypeIsProvided(String topologyTypeName)
            throws SQLException {
        GridTopology.TopologyType expectedType = GridTopology.TopologyType.valueOf(topologyTypeName);
        ResultSet resultSet = createMockResultSet(topologyTypeName, EMPTY_JSON_ARRAY);
        when(resultSet.next()).thenReturn(true);
        doReturn(resultSet).when(jdbcHelper).executeSelect(anyString(), anyString());

        GridTopology result = gridTopologyDao.getTopologyById(TOPOLOGY_ID_1);

        assertEquals(expectedType, result.getTopologyType());
    }

    @ParameterizedTest
    @MethodSource("invalidAdditionalSitesProvider")
    void mapRowToTopology_shouldReturnEmptyList_whenAdditionalSitesIsInvalid(String additionalSites)
            throws SQLException {
        ResultSet resultSet = createMockResultSet(TOPOLOGY_TYPE_SELF_DEFINED, additionalSites);
        when(resultSet.next()).thenReturn(true);
        doReturn(resultSet).when(jdbcHelper).executeSelect(anyString(), anyString());

        GridTopology result = gridTopologyDao.getTopologyById(TOPOLOGY_ID_1);

        assertTrue(result.getAdditionalSites().isEmpty());
    }

    private static Stream<Arguments> invalidAdditionalSitesProvider() {
        return Stream.of(
                Arguments.of((String) null),
                Arguments.of(""),
                Arguments.of("   "),
                Arguments.of("invalid json")
        );
    }

    private static ResultSet createMockResultSet(String topologyType,
            String additionalSitesJson) throws SQLException {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getString("grid_topology_id")).thenReturn(TOPOLOGY_ID_1);
        when(resultSet.getString("topology_type")).thenReturn(topologyType);

        if (additionalSitesJson != null) {
            InputStream inputStream = new ByteArrayInputStream(
                    additionalSitesJson.getBytes(StandardCharsets.UTF_8));
            when(resultSet.getBinaryStream("additional_sites")).thenReturn(inputStream);
        } else {
            when(resultSet.getBinaryStream("additional_sites")).thenReturn(null);
        }

        when(resultSet.getLong("creation_date")).thenReturn(1000L);
        when(resultSet.getLong("modification_date")).thenReturn(2000L);
        return resultSet;
    }

    private GridTopology createGridTopology(GridTopology.TopologyType topologyType,
            List<String> additionalSites, long modificationDate) {
        return GridTopology.builder()
                .gridTopologyId(TOPOLOGY_ID_1)
                .topologyType(topologyType)
                .additionalSites(additionalSites)
                .creationDate(1000L)
                .modificationDate(modificationDate)
                .build();
    }
}