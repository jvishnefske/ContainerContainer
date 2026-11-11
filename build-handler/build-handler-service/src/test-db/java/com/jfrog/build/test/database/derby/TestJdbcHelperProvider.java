package com.jfrog.build.test.database.derby;

import lombok.NonNull;
import org.apache.commons.io.output.NullOutputStream;
import org.apache.derby.jdbc.EmbeddedDriver;
import org.jfrog.storage.JFrogDataSource;
import org.jfrog.storage.JdbcHelper;

import java.io.OutputStream;

public class TestJdbcHelperProvider {

    @SuppressWarnings("unused")
    public static final OutputStream DEV_NULL = NullOutputStream.INSTANCE;

    @NonNull
    public static JdbcHelper buildTestJdbcHelper() {
        suppressDerbyLogFile();
        return new JdbcHelper(initInMemoryDataSource());
    }

    private static void suppressDerbyLogFile() {
        String thisClassName = TestJdbcHelperProvider.class.getName();
        System.setProperty("derby.stream.error.field", thisClassName + ".DEV_NULL");
    }

    @NonNull
    private static JFrogDataSource initInMemoryDataSource() {
        EmbeddedDriver derbyEmbeddedDriver = new EmbeddedDriver();
        return new TestJFrogDataSource(derbyEmbeddedDriver);
    }
}
