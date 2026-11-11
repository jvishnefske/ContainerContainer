package org.artifactory.storage.db.init;


import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.jfrog.storage.DbType;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertThrows;

/**
 * @author William Arustamyan
 */
class DbConnectionUtilsTest {

    private final ArtifactoryDbProperties dbProperties = mock(ArtifactoryDbProperties.class);

    @Test
    void testDbConnectionUtilsWhenDbTypeIsPostgres() {
        when(this.dbProperties.getDbType()).thenReturn(DbType.POSTGRESQL);
        assertDoesNotThrow(() -> DbConnectionUtils.checkDefaultDbProperties(this.dbProperties));
    }

    @Test
    void testDbConnectionUtilsWhenDbTypeNotPostgresAndFlagIsPresent() {
        when(this.dbProperties.getDbType()).thenReturn(DbType.DERBY);
        when(this.dbProperties.allowNonPgFlag()).thenReturn(Optional.of(Boolean.TRUE));
        assertDoesNotThrow(() -> DbConnectionUtils.checkDefaultDbProperties(this.dbProperties));
    }

    @Test
    void testDbConnectionUtilsWhenDbTypeNotPostgresAndFlagIsNotPresent() {
        when(this.dbProperties.getDbType()).thenReturn(DbType.DERBY);
        when(this.dbProperties.allowNonPgFlag()).thenReturn(Optional.empty());
        assertThrows(() -> DbConnectionUtils.checkDefaultDbProperties(this.dbProperties));
    }

    @Test
    void testDbConnectionUtilsWhenDbTypeNotPostgresAndFlagIsPresentAndIsFalse() {
        when(this.dbProperties.getDbType()).thenReturn(DbType.DERBY);
        when(this.dbProperties.allowNonPgFlag()).thenReturn(Optional.of(Boolean.FALSE));
        assertThrows(() -> DbConnectionUtils.checkDefaultDbProperties(this.dbProperties));
    }
}
