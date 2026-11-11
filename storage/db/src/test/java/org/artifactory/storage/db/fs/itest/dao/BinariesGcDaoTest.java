package org.artifactory.storage.db.fs.itest.dao;

import org.artifactory.storage.db.fs.dao.BinariesGcDao;
import org.artifactory.storage.db.itest.DbBaseTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * @author TamirH
 */
@Test
public class BinariesGcDaoTest extends DbBaseTest {
    @Autowired
    BinariesGcDao binariesDao;

    @BeforeClass
    public void importBinaries() {
        importSql("/sql/binaries.sql");
    }

    @Test
    void findPotentialDeletion() throws SQLException {
        Collection<BinariesGcDao.BinaryEntity> potentialDeletion = binariesDao.findPotentialDeletion(null);
        assertEquals(potentialDeletion.size(), 3);
        verifyAllPotentialForDeletionReturned(potentialDeletion);
    }

    private void verifyAllPotentialForDeletionReturned(Collection<BinariesGcDao.BinaryEntity> potentialDeletion) {
        Set<String> nodesSha1 = potentialDeletion.stream()
                .map(binaryEntity -> binaryEntity == null ? "" : binaryEntity.sha1())
                .collect(Collectors.toSet());
        assertTrue(nodesSha1.contains("356a192b7913b04c54574d18c28d46e6395428ab"));
        assertTrue(nodesSha1.contains("74239116da1def240fe1d366eb535513efc1c40b"));
        assertTrue(nodesSha1.contains("da39a3ee5e6b4b0d3255bfef95601890afd80709"));

        Set<String> nodesSha2 = potentialDeletion.stream()
                .map(binaryEntity -> binaryEntity == null ? "" : binaryEntity.sha2())
                .collect(Collectors.toSet());
        assertTrue(nodesSha2.contains("dddd23029162f3b2dc51f512cb64bce8cb6913ed6e540f23ec567d898f60ddac"));
        assertTrue(nodesSha2.contains("yyyy23029162f3b2dc51f512cb64bce8cb6913ed6e540f23ec567d898f60yyyy"));
        assertTrue(nodesSha2.contains("dcabf055bc6d5477c35f82da16323efb884fc21a87fbf7ebda9d5848eee3e280"));
    }

    @Test
    void assertPotentialDeletionOrderBySize() throws SQLException {
        Collection<BinariesGcDao.BinaryEntity> potentialDeletion = binariesDao.findPotentialDeletion(null, false,
                false);
        assertEquals(potentialDeletion.size(), 3);
        List<String> nodes = potentialDeletion.stream().map(BinariesGcDao.BinaryEntity::sha1).toList();
        // verify candidates are ordered by size when gcSkipOrderBySizeFullGcBoolean is false
        assertEquals(nodes.get(0), "74239116da1def240fe1d366eb535513efc1c40b");
        assertEquals(nodes.get(1), "356a192b7913b04c54574d18c28d46e6395428ab");
        assertEquals(nodes.get(2), "da39a3ee5e6b4b0d3255bfef95601890afd80709");
    }

    @Test
    void assertPotentialDeletionOrderBySha1() throws SQLException {
        Collection<BinariesGcDao.BinaryEntity> potentialDeletion = binariesDao.findPotentialDeletion(null, true, false);
        assertEquals(potentialDeletion.size(), 3);
        List<String> nodes = potentialDeletion.stream().map(BinariesGcDao.BinaryEntity::sha1).toList();
        // verify candidates are ordered by sha1 when gcSkipOrderBySizeFullGcBoolean is true
        assertEquals(nodes.get(0), "356a192b7913b04c54574d18c28d46e6395428ab");
        assertEquals(nodes.get(1), "74239116da1def240fe1d366eb535513efc1c40b");
        assertEquals(nodes.get(2), "da39a3ee5e6b4b0d3255bfef95601890afd80709");
    }

    @Test
    void assertPotentialDeletionSkipOrderBy() throws SQLException {
        Collection<BinariesGcDao.BinaryEntity> potentialDeletion = binariesDao.findPotentialDeletion(null, true, true);
        assertEquals(potentialDeletion.size(), 3);
    }

    @Test
    void assertPotentialDeletionStartAfterSha1() throws SQLException {
        Collection<BinariesGcDao.BinaryEntity> potentialDeletion =
                binariesDao.findPotentialDeletion("74239116da1def240fe1d366eb535513efc1c40b", true, false);
        assertEquals(potentialDeletion.size(), 1);
        List<String> nodes = potentialDeletion.stream().map(BinariesGcDao.BinaryEntity::sha1).toList();
        // verify search continues after entered sha1
        assertEquals(nodes.get(0), "da39a3ee5e6b4b0d3255bfef95601890afd80709");
    }

}