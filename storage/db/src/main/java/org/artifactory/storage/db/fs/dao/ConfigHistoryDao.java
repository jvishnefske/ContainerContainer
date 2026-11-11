package org.artifactory.storage.db.fs.dao;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.storage.db.fs.entity.ConfigHistoryRecord;
import org.jfrog.storage.JdbcHelper;
import org.jfrog.storage.util.DbUtils;
import org.jfrog.storage.wrapper.BlobWrapper;

import java.io.IOException;
import java.io.InputStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.artifactory.common.home.ArtifactoryHome.ARTIFACTORY_CONFIG_FILE;

@RequiredArgsConstructor
@Slf4j
public class ConfigHistoryDao {

    private final JdbcHelper jdbcHelper;
    private static final String CONFIG_HISTORY_TABLE = "config_history";
    private static final String ID_COLUMN = "id";
    private static final String CONFIG_DATA_COLUMN = "config_data";
    private static final String REPO_CONFIG_DATA_COLUMN = "repo_config_data";
    private static final String LOG_CONFIG_DATA_COLUMN = "log_config_data";
    private static final String COLUMNS = String.join(",",
            CONFIG_DATA_COLUMN, REPO_CONFIG_DATA_COLUMN, LOG_CONFIG_DATA_COLUMN);
    public static final String ARTIFACTORY_REPO_CONFIG_FILE_NAME =
            "artifactory/config/artifactory.repository.config.latest.json";
    public static final String MISSING_REPO_CONFIG_PLACEHOLDER = "missing_repo_config";
    private static final String INSERT_QUERY = "INSERT INTO " +
            CONFIG_HISTORY_TABLE + " (" + COLUMNS + ") VALUES(?, ?, ?)";
    private static final String SELECT_QUERY = "SELECT * FROM " + CONFIG_HISTORY_TABLE + " where " + ID_COLUMN + " =?";
    private static final String SELECT_ID_QUERY = "SELECT " + ID_COLUMN + " from " +
            CONFIG_HISTORY_TABLE + " order by " + ID_COLUMN + " DESC";

    public boolean saveConfigHistory(ArtifactoryHome home) throws SQLException, IOException {
        List<Config> configs = loadConfig(List.of(ARTIFACTORY_CONFIG_FILE, ARTIFACTORY_REPO_CONFIG_FILE_NAME));
        Map<String, String> congigMap = configs.stream().collect(Collectors.toMap(Config::name, Config::data));
        String configData = congigMap.get(ARTIFACTORY_CONFIG_FILE);
        String repoConfigData = congigMap.get(ARTIFACTORY_REPO_CONFIG_FILE_NAME);
        String logConfig = home.getLogbackFile();

        return insert(configData, repoConfigData, logConfig);
    }

    public boolean insert(String configData, String repoConfigData, String logConfigData)
            throws SQLException {
        BlobWrapper config = new BlobWrapper(configData);
        if (repoConfigData == null) {
            log.warn("repoConfigData is null, continuing without repo config snapshot");
            repoConfigData = MISSING_REPO_CONFIG_PLACEHOLDER;
        }
        if (logConfigData == null) {
            log.warn("logConfigData is null");
            return false;
        }
        BlobWrapper repo = new BlobWrapper(repoConfigData);
        BlobWrapper log = new BlobWrapper(logConfigData);

        return jdbcHelper.executeUpdate(INSERT_QUERY, config, repo, log) == 1;
    }

    public int getLatestId() throws SQLException {
        SortedSet<Integer> ids = new TreeSet<>();
        try (ResultSet resultSet = jdbcHelper.executeSelect(SELECT_ID_QUERY)) {
            while (resultSet.next()) {
                ids.add(resultSet.getInt("id"));
            }
            if (ids.isEmpty()) {
                throw new SQLException("Can't get config id for version ");
            }
            return ids.last();
        }
    }

    public ConfigHistoryRecord get(int id) throws SQLException, IOException {
        try (ResultSet resultSet = jdbcHelper.executeSelect(SELECT_QUERY, id)) {
            if (resultSet.next()) {
                return toConfigHistory(resultSet);
            }
            throw new SQLException("Can't get config for id " + id);
        }
    }

    public List<Config> loadConfig(List<String> names) throws SQLException {
        ResultSet resultSet = null;
        List<Config> configs = new ArrayList<>();
        try {
            resultSet = jdbcHelper.executeSelect("SELECT config_name, data FROM configs WHERE config_name IN (#)",
                    names);
            while (resultSet.next()) {
                String name = resultSet.getString(1);
                InputStream binaryStream = resultSet.getBinaryStream(2);
                configs.add(new Config(name, IOUtils.toString(binaryStream, UTF_8)));
            }
            return configs;
        } catch (IOException e) {
            throw new SQLException("Failed to read config '" + names + "' due to: " + e.getMessage(), e);
        } finally {
            DbUtils.close(resultSet);
        }
    }

    private ConfigHistoryRecord toConfigHistory(ResultSet resultSet) throws SQLException, IOException {
        return ConfigHistoryRecord.builder()
                .id(resultSet.getInt("id"))
                .configData(IOUtils.toString(resultSet.getBinaryStream(CONFIG_DATA_COLUMN), UTF_8))
                .repoConfigData(IOUtils.toString(resultSet.getBinaryStream(REPO_CONFIG_DATA_COLUMN), UTF_8))
                .logConfigData(IOUtils.toString(resultSet.getBinaryStream(LOG_CONFIG_DATA_COLUMN), UTF_8))
                .build();
    }

    public record Config(
            String name,
            String data
    ) {
    }
}
