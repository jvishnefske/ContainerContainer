package org.artifactory.sh;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.jfrog.storage.JdbcHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ArtifactoryTestBinariesTaskDao {
    private final JdbcHelper jdbcHelper;
    @SneakyThrows
    public void createFailedTask(int id, String sha1, String repo) {
        jdbcHelper.executeUpdate("INSERT INTO binaries_tasks(" +
                        "id, sha1, time, local_repo_key, remote_url_hash, start_time, count, server_id) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)", id, sha1, System.currentTimeMillis(),
                repo, 1, null, 10, null);
    }

}
