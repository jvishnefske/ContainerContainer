package org.artifactory.storage.db.version.converter;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.storage.db.version.converter.validconversion.ValidPsqlConversionVersion;
import org.jfrog.storage.DbType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.BiPredicate;

/**
 * @author Tomer Slutsky
 */
@Slf4j
public class SuspendOnConditionPsqlDbConvertor extends SuspendOnConditionDbConvertor {

    private final ValidPsqlConversionVersion validPsqlConversionVersion;
    private static final String SHOW_VERSION = "SHOW server_version_num";
    private static final String EXCEPTION_MESSAGE =
            "An exception occurred in version check for converter: Suspendable Converter ";

    @SafeVarargs
    public SuspendOnConditionPsqlDbConvertor(String file, ValidPsqlConversionVersion validPsqlConversionVersion,
            BiPredicate<Connection, DbType>... conditions) {
        super(file, conditions);
        this.validPsqlConversionVersion = validPsqlConversionVersion;
    }

    @VisibleForTesting
    @Override
    boolean checkIsValidConversionVersion(Connection con) {
        try (PreparedStatement stmt = con.prepareStatement(SHOW_VERSION)) {
            ResultSet resultSet = stmt.executeQuery();
            if (resultSet.next()) {
                long version = resultSet.getLong(1);
                if (version < validPsqlConversionVersion.getDbVersion()) {
                    log.info(
                            "Suspending converter {} since the db version {} is not valid for this converter, the db " +
                                    "version must be at least: {}",
                            file,
                            version, validPsqlConversionVersion);
                    return false;
                }

                return true;
            }

            log.warn("Version query has not returned a version, suspending converter");
            return false;
        } catch (SQLException e) {
            log.debug(getExceptionMessage() + file, e);
            log.error(getExceptionMessage() + file,
                    e.getMessage());
            return false;
        }
    }

    @Override
    String getExceptionMessage() {
        return EXCEPTION_MESSAGE;
    }
}
