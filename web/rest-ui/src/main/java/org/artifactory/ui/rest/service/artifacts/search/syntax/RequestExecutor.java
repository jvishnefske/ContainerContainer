package org.artifactory.ui.rest.service.artifacts.search.syntax;

import org.jfrog.storage.JdbcHelper;

import java.sql.SQLException;
import java.util.List;

/**
 * @author Igor Usenko
 */
@FunctionalInterface
interface RequestExecutor<T> {
    List<T> execute(JdbcHelper jdbcHelper, PreparedQuery preparedQuery, Deadline deadline) throws SQLException;
}
