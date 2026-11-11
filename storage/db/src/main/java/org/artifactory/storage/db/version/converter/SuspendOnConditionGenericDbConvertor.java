package org.artifactory.storage.db.version.converter;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;
import org.jfrog.storage.DbType;

import java.sql.Connection;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * Generic suspend-on-condition converter that works for all database types.
 * Suspends execution based on a provided condition (e.g., feature flag).
 *
 * @author Tomer Ben Kereth
 */
@Slf4j
public class SuspendOnConditionGenericDbConvertor extends SuspendOnConditionDbConvertor {

    private final Predicate<Connection> condition;
    private static final String EXCEPTION_MESSAGE =
            "An exception occurred in validity check for converter: Suspendable Converter ";

    /**
     * Creates a suspend-on-condition converter that works for all database types.
     *
     * @param file The SQL script file name (without extension)
     * @param condition A predicate that returns true if the converter should run, false to suspend
     * @param conditions Additional database conditions (e.g., tableNotExists, etc.)
     */
    @SafeVarargs
    public SuspendOnConditionGenericDbConvertor(String file, Predicate<Connection> condition,
            BiPredicate<Connection, DbType>... conditions) {
        super(file, conditions);
        this.condition = condition;
    }

    @VisibleForTesting
    @Override
    boolean checkIsValidConversionVersion(Connection con) {
        return condition.test(con);
    }

    @Override
    String getExceptionMessage() {
        return EXCEPTION_MESSAGE;
    }
}

