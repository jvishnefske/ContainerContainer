package org.artifactory.storage.db.version.converter;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.storage.db.version.converter.validconversion.ValidOracleConversionVersion;
import org.jfrog.storage.DbType;

import java.sql.Connection;
import java.util.function.BiPredicate;

/**
 * @author Tomer Slutsky
 */
@Slf4j
public class SuspendOnConditionOracleConvertor extends SuspendOnConditionDbConvertor {

    private final ValidOracleConversionVersion validOracleConversionVersion;
    private static final String EXCEPTION_MESSAGE =
            "An exception occurred in validity check for converter: Suspendable Converter ";

    @SafeVarargs
    public SuspendOnConditionOracleConvertor(String file, ValidOracleConversionVersion validOracleConversionVersion,
            BiPredicate<Connection, DbType>... conditions) {
        super(file, conditions);
        this.validOracleConversionVersion = validOracleConversionVersion;
    }

    @VisibleForTesting
    @Override
    boolean checkIsValidConversionVersion(Connection con) {
        return validOracleConversionVersion.getValue().getBoolean();
    }

    @Override
    String getExceptionMessage() {
        return EXCEPTION_MESSAGE;
    }

}
