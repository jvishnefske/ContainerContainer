/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.storage.db.keys.dao;

import com.google.common.collect.Lists;
import lombok.NonNull;
import org.artifactory.storage.db.keys.model.DbTrustedKey;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.artifactory.storage.db.util.BaseDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static java.util.Optional.ofNullable;

/**
 * @author Rotem Kfir
 */
@Repository
public class TrustedKeysDao extends BaseDao {

    public static final String TRUSTED_KEYS_TABLE = "trusted_keys";

    @Autowired
    public TrustedKeysDao(@NonNull ArtifactoryJdbcHelper jdbcHelper) {
        super(jdbcHelper);
    }

    public int create(@NonNull DbTrustedKey key) throws SQLException {
        return jdbcHelper.executeUpdate("INSERT INTO trusted_keys "
                        + "(kid, trusted_key, fingerprint, key_type, alias, issued, issued_by, expiry) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                key.getKid(), key.getTrustedKey(), key.getFingerprint(), key.getType(),
                key.getAlias(), key.getIssued(), key.getIssuedBy(), key.getExpiry());
    }

    public int delete(@NonNull String kid) throws SQLException {
        return jdbcHelper.executeUpdate("DELETE FROM trusted_keys WHERE kid = ?", kid);
    }

    public int deleteAll() throws SQLException {
        return jdbcHelper.executeUpdate("DELETE FROM trusted_keys");
    }

    @NonNull
    public Optional<DbTrustedKey> findById(@NonNull String kid) throws SQLException {
        return findByProperty("kid", kid);
    }

    @NonNull
    public Optional<DbTrustedKey> findByAlias(@NonNull String alias) throws SQLException {
        return findByProperty("alias", alias);
    }

    @NonNull
    private Optional<DbTrustedKey> findByProperty(
            @NonNull String propertyName,
            @NonNull String value) throws SQLException {
        DbTrustedKey key = null;
        String query = "SELECT * FROM trusted_keys WHERE " + propertyName + " = ?";
        try (ResultSet resultSet = jdbcHelper.executeSelect(query, value)) {
            if (resultSet.next()) {
                key = readFrom(resultSet);
            }
        }
        return ofNullable(key);
    }

    @NonNull
    public List<DbTrustedKey> findAll() throws SQLException {
        List<DbTrustedKey> results = Lists.newArrayList();
        try (ResultSet resultSet = jdbcHelper.executeSelect("SELECT * FROM trusted_keys")) {
            while (resultSet.next()) {
                results.add(readFrom(resultSet));
            }
            return results;
        }
    }

    @NonNull
    private DbTrustedKey readFrom(@NonNull ResultSet resultSet) throws SQLException {
        return DbTrustedKey.builder()
                .kid(resultSet.getString("kid"))
                .trustedKey(resultSet.getString("trusted_key"))
                .fingerprint(resultSet.getString("fingerprint"))
                .type(resultSet.getString("key_type"))
                .alias(resultSet.getString("alias"))
                .issued(resultSet.getLong("issued"))
                .issuedBy(resultSet.getString("issued_by"))
                .expiry(resultSet.getLong("expiry"))
                .build();
    }
}
