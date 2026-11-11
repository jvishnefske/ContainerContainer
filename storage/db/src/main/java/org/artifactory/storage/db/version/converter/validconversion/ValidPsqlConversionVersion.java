package org.artifactory.storage.db.version.converter.validconversion;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jfrog.storage.DbType;

/**
 * @author Tomer Slutsky
 */
@AllArgsConstructor
@Getter
public enum ValidPsqlConversionVersion {
    PSQL_10(DbType.POSTGRESQL, 100000L),
    VALID_VERSION(DbType.POSTGRESQL, 0L);

    private final DbType dbType;
    private final long dbVersion;
}
