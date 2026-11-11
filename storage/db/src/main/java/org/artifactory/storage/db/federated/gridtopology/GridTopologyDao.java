package org.artifactory.storage.db.federated.gridtopology;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.model.GridTopology;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.artifactory.storage.db.util.BaseDao;
import org.jfrog.common.JsonUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO for the grid_topology table.
 *
 * @author Tomer Ben Kereth
 */
@Repository
@Slf4j
public class GridTopologyDao extends BaseDao {

    @Autowired
    public GridTopologyDao(ArtifactoryJdbcHelper jdbcHelper) {
        super(jdbcHelper);
    }

    /**
     * Retrieves all grid topologies from the database.
     *
     * @return List of all grid topologies
     * @throws SQLException if an error occurs while fetching topologies
     */
    public List<GridTopology> getAllTopologies() throws SQLException {
        List<GridTopology> topologies = new ArrayList<>();
        try (ResultSet resultSet = jdbcHelper.executeSelect("""
                SELECT * FROM grid_topology
                """)) {
            while (resultSet.next()) {
                topologies.add(mapRowToTopology(resultSet));
            }
        }
        return topologies;
    }

    /**
     * Retrieves a grid topology by its topology ID.
     *
     * @param gridTopologyId The topology ID to search for
     * @return The grid topology if found, null otherwise
     * @throws SQLException if an error occurs while fetching the topology
     */
    public GridTopology getTopologyById(String gridTopologyId) throws SQLException {
        try (ResultSet resultSet = jdbcHelper.executeSelect("""
                SELECT * FROM grid_topology WHERE grid_topology_id = ?
                """, gridTopologyId)) {
            if (resultSet.next()) {
                return mapRowToTopology(resultSet);
            }
        }
        return null;
    }

    /**
     * Creates a new grid topology in the database.
     *
     * @param topology The topology to create
     * @throws SQLException if an error occurs while creating the topology
     */
    public void createTopology(GridTopology topology) throws SQLException {
        String additionalSitesJson = JsonUtils.getInstance().valueToString(topology.getAdditionalSites());
        byte[] additionalSitesBytes = StringUtils.isEmpty(additionalSitesJson) ? null :
                additionalSitesJson.getBytes(StandardCharsets.UTF_8);
        jdbcHelper.executeUpdate("""
                INSERT INTO grid_topology (grid_topology_id, topology_type,
                    additional_sites, creation_date, modification_date) VALUES (?, ?, ?, ?, ?)
                """,
                topology.getGridTopologyId(),
                topology.getTopologyType().name(),
                additionalSitesBytes,
                topology.getCreationDate(),
                topology.getModificationDate());
    }

    /**
     * Updates a grid topology in the database.
     * Note: topology_type is immutable and cannot be changed after creation.
     *
     * @param topology The topology to update
     * @throws SQLException if an error occurs while updating the topology
     */
    public void updateTopology(GridTopology topology) throws SQLException {
        String additionalSitesJson = JsonUtils.getInstance().valueToString(topology.getAdditionalSites());
        byte[] additionalSitesBytes = StringUtils.isEmpty(additionalSitesJson) ? null :
                additionalSitesJson.getBytes(StandardCharsets.UTF_8);
        jdbcHelper.executeUpdate("""
                UPDATE grid_topology SET additional_sites = ?, modification_date = ?
                    WHERE grid_topology_id = ?
                """,
                additionalSitesBytes,
                topology.getModificationDate(),
                topology.getGridTopologyId());
    }

    /**
     * Maps a database row to a GridTopology object.
     *
     * @param resultSet The result set containing the topology data
     * @return A GridTopology object
     * @throws SQLException if an error occurs while reading from the result set
     */
    private GridTopology mapRowToTopology(ResultSet resultSet) throws SQLException {
        List<String> additionalSites = new ArrayList<>();
        InputStream binaryStream = resultSet.getBinaryStream("additional_sites");
        if (binaryStream != null) {
            try {
                String additionalSitesJson = IOUtils.toString(binaryStream, StandardCharsets.UTF_8);
                if (!additionalSitesJson.trim().isEmpty()) {
                    TypeReference<List<String>> typeRef = new TypeReference<>() {};
                    additionalSites = JsonUtils.getInstance().readValue(additionalSitesJson, typeRef);
                }
            } catch (Exception e) {
                log.error("Failed to parse additional_sites JSON for topology {}",
                        resultSet.getString("grid_topology_id"), e);
            }
        }

        return GridTopology.builder()
                .gridTopologyId(resultSet.getString("grid_topology_id"))
                .topologyType(GridTopology.TopologyType.valueOf(resultSet.getString("topology_type")))
                .additionalSites(additionalSites)
                .creationDate(resultSet.getLong("creation_date"))
                .modificationDate(resultSet.getLong("modification_date"))
                .build();
    }

    public void deleteTopology(String gridTopologyId) throws SQLException {
        jdbcHelper.executeUpdate("""
                DELETE FROM grid_topology WHERE grid_topology_id = ?
                """, gridTopologyId);
    }
}