package org.artifactory.storage.db.federated.mirrors;

import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Dao for the mirror's metadata table
 *
 * @author Alexei Vainshtein
 */
@Repository
public class FederatedMirrorsMetadataDao {

    protected final ArtifactoryJdbcHelper jdbcHelper;
    private final String TABLE_NAME = "mirrors_metadata";

    @Autowired
    public FederatedMirrorsMetadataDao(ArtifactoryJdbcHelper jdbcHelper) {
        this.jdbcHelper = jdbcHelper;
    }

    /**
     * Returns the representative object of the table {@link MirrorsMetadataModule}
     *
     * @param type - The package type of the config for ex: basic, maven, docker..
     * @param url  - The member url
     * @param time - The cache time to check against the database if the cached entry is still valid
     * @return the {@link MirrorsMetadataModule}. If not found, will return an empty {@link MirrorsMetadataModule}
     * @throws SQLException upon sql error.
     */
    public MirrorsMetadataModule getDbCacheForType(String type, String url, long time) throws SQLException {
        try (ResultSet resultSet = jdbcHelper
                .executeSelect(
                        "SELECT * FROM mirrors_metadata WHERE metadata_type = ? AND member_url = ? AND cache_time >= ?",
                        type, url, time)) {
            MirrorsMetadataModule mirrorsMetadataModule = new MirrorsMetadataModule();
            if (resultSet.next()) {
                mirrorsMetadataModule.setType(resultSet.getString("metadata_type"));
                mirrorsMetadataModule.setUrl(resultSet.getString("member_url"));
                mirrorsMetadataModule.setCacheTime(resultSet.getLong("cache_time"));
                mirrorsMetadataModule.setBlob(resultSet.getBytes("metadata_blob"));
            }
            return mirrorsMetadataModule;
        }
    }

    /**
     * Returns all members
     *
     * @return a list of all members
     * @throws SQLException - if exception occurs on fetching info from the db.
     */
    public List<MirrorsMetadataModule> getAllMembers() throws SQLException {
        List<MirrorsMetadataModule> membersUrls = new ArrayList<>();
        try (ResultSet resultSet = jdbcHelper
                .executeSelect(
                        "SELECT * FROM " + TABLE_NAME)) {
            while (resultSet.next()) {
                MirrorsMetadataModule mirrorsMetadataModule = new MirrorsMetadataModule();
                mirrorsMetadataModule.setType(resultSet.getString("metadata_type"));
                mirrorsMetadataModule.setUrl(resultSet.getString("member_url"));
                mirrorsMetadataModule.setCacheTime(resultSet.getLong("cache_time"));
                mirrorsMetadataModule.setBlob(resultSet.getBytes("metadata_blob"));
                membersUrls.add(mirrorsMetadataModule);
            }
        }
        return membersUrls;
    }

    /**
     * Deletes the entry by package type and member url
     *
     * @param type      - The package type of the config for ex: basic, maven, docker..
     * @param memberUrl - The member url
     * @throws SQLException - if exception happens during the sql execution
     */
    public void deleteCacheByTypeAndMember(String type, String memberUrl) throws SQLException {
        jdbcHelper.executeUpdate("DELETE FROM " + TABLE_NAME + " WHERE metadata_type = ? AND member_url = ?", type,
                memberUrl);
    }

    /**
     * @param type      - The package type of the config for ex: basic, maven, docker..
     * @param memberUrl - The member url
     * @param time      - The cache time
     * @param blob      - The metadata itself
     * @throws SQLException - if exception happens during the sql execution
     */
    public void addMetadataForMember(String type, String memberUrl, long time, byte[] blob) throws SQLException {
        jdbcHelper.executeUpdate(
                "INSERT into " + TABLE_NAME + " (metadata_type, member_url, cache_time, metadata_blob) " +
                        "VALUES (?, ?, ?, ?)",
                type, memberUrl, time, blob);
    }

    public void deleteAllMembers() throws SQLException {
        jdbcHelper.executeUpdate("DELETE FROM " + TABLE_NAME);
    }
}
