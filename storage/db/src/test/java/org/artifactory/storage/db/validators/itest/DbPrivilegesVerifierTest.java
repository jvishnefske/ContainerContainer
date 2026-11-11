package org.artifactory.storage.db.validators.itest;

import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.storage.db.itest.DbBaseTest;
import org.jfrog.storage.priviledges.DBPrivilegesVerifier;
import org.jfrog.storage.priviledges.DBPrivilegesVerifierFactory;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * @author Uriah Levy
 */
@Test
public class DbPrivilegesVerifierTest extends DbBaseTest {

    @BeforeClass
    public void setUp() {
        importSql("/sql/create_privileges_tables.sql");
    }

    @Test
    public void testVerifierOnExistingTable() throws SQLException {
        DBPrivilegesVerifier verifier =
                DBPrivilegesVerifierFactory.createDBPrivilegesVerifier(ArtifactoryHome.get().getDBProperties()
                        .getDbType());
        try (Connection connection = jdbcHelper.getDataSource().getConnection()) {
            verifier.isSufficientPrivileges(connection, "artifactory");
        }
    }
}
