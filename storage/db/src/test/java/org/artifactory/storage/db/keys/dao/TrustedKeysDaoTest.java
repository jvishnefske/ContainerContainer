package org.artifactory.storage.db.keys.dao;

import lombok.NonNull;
import org.artifactory.storage.db.keys.model.DbTrustedKey;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrustedKeysDaoTest {

    private static final String KID = "{kid}";
    private static final String KEY = "{key}";
    private static final String TYPE = "{type}";
    private static final String ALIAS = "{alias}";
    private static final String FINGERPRINT = "{fingerprint}";
    private static final String ISSUED_BY = "{issuedBy}";
    private static final long ISSUED = 1L;
    private static final long EXPIRY = 0L;

    private TrustedKeysDao trustedKeysDao;

    private ArtifactoryJdbcHelper jdbcHelper;

    @BeforeEach
    public void setUp() {
        jdbcHelper = mock(ArtifactoryJdbcHelper.class);
        trustedKeysDao = new TrustedKeysDao(jdbcHelper);
    }

    @Test
    void create() throws SQLException {
        trustedKeysDao.create(buildTestDbTrustedKey());
        verify(jdbcHelper).executeUpdate(anyString(), eq(KID), eq(KEY), eq(FINGERPRINT),
                eq(TYPE), eq(ALIAS), eq(ISSUED), eq(ISSUED_BY), eq(EXPIRY));
    }

    @Test
    void delete() throws SQLException {
        trustedKeysDao.delete(KID);
        verify(jdbcHelper).executeUpdate(anyString(), eq(KID));
    }

    @Test
    void deleteAll() throws SQLException {
        trustedKeysDao.deleteAll();
        verify(jdbcHelper).executeUpdate(anyString());
    }

    @Test
    void findAll() throws SQLException {
        ResultSet resultSet = buildTestResultSet();
        when(jdbcHelper.executeSelect(anyString())).thenReturn(resultSet);
        List<DbTrustedKey> result = trustedKeysDao.findAll();
        assertEquals(List.of(buildTestDbTrustedKey()), result);
    }

    @Test
    void findById() throws SQLException {
        ResultSet resultSet = buildTestResultSet();
        when(jdbcHelper.executeSelect(anyString(), any())).thenReturn(resultSet);
        Optional<DbTrustedKey> result = trustedKeysDao.findById(KID);
        assertTrue(result.isPresent());
        assertEquals(buildTestDbTrustedKey(), result.get());
    }

    @Test
    void findByAlias() throws SQLException {
        ResultSet resultSet = buildTestResultSet();
        when(jdbcHelper.executeSelect(anyString(), any())).thenReturn(resultSet);
        Optional<DbTrustedKey> result = trustedKeysDao.findByAlias(ALIAS);
        assertTrue(result.isPresent());
        assertEquals(buildTestDbTrustedKey(), result.get());
    }

    @NonNull
    private static DbTrustedKey buildTestDbTrustedKey() {
        return DbTrustedKey.builder()
                .kid(KID)
                .trustedKey(KEY)
                .fingerprint(FINGERPRINT)
                .type(TYPE)
                .alias(ALIAS)
                .issued(ISSUED)
                .issuedBy(ISSUED_BY)
                .expiry(EXPIRY)
                .build();
    }

    @NonNull
    private static ResultSet buildTestResultSet() throws SQLException {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(true).thenReturn(false);
        when(resultSet.getString("kid")).thenReturn(KID);
        when(resultSet.getString("trusted_key")).thenReturn(KEY);
        when(resultSet.getString("fingerprint")).thenReturn(FINGERPRINT);
        when(resultSet.getString("key_type")).thenReturn(TYPE);
        when(resultSet.getString("alias")).thenReturn(ALIAS);
        when(resultSet.getLong("issued")).thenReturn(ISSUED);
        when(resultSet.getString("issued_by")).thenReturn(ISSUED_BY);
        when(resultSet.getLong("expiry")).thenReturn(EXPIRY);
        return resultSet;
    }
}
