package org.artifactory.sh;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.jfrog.storage.JdbcHelper;

@RequiredArgsConstructor
public class ArtifactoryTestBinariesDao {

    private final JdbcHelper jdbcHelper;

    @SneakyThrows
    public TestBinaryEntity create(String sha1, String sha2, String md5, long length) {
        TestBinaryEntity binaryEntity = new TestBinaryEntity(sha1, sha2, md5, length);
        create(binaryEntity);
        return binaryEntity;
    }

    @SneakyThrows
    public void create(TestBinaryEntity be) {
        jdbcHelper.executeUpdate("INSERT INTO binaries (sha1, md5, bin_length, sha256) VALUES(?, ?, ?, ?)",
                be.getSha1(),be.getMd5(),be.getLength(),be.getSha2());
    }

    @SneakyThrows
    public int deleteEntry(String sha1ToDelete)  {
        return jdbcHelper.executeUpdate("DELETE FROM binaries WHERE binaries.sha1 = ?" +
                        " AND NOT EXISTS (SELECT n.node_id FROM nodes n WHERE n.sha1_actual = ?)",
                sha1ToDelete, sha1ToDelete);
    }
}
