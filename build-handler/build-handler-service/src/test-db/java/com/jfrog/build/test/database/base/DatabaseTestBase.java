package com.jfrog.build.test.database.base;

import com.jfrog.build.config.BuildServiceBeansConfig;
import com.jfrog.build.test.config.TestBuildServiceBeansConfig;
import com.jfrog.build.test.database.TestDatabaseLifecycle;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.jfrog.common.ResourceUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.InputStream;

import static com.jfrog.build.test.database.TestDatabaseProvider.getDatabaseType;
import static com.jfrog.build.test.database.TestDatabaseProvider.getJdbcHelper;
import static org.jfrog.storage.util.DbStatementUtils.executeSqlStream;
import static org.jfrog.storage.util.DbUtils.doStreamWithConnection;
import static org.junit.jupiter.api.Assertions.fail;

@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(value = {SpringExtension.class, TestDatabaseLifecycle.class})
@ContextConfiguration(classes = {TestBuildServiceBeansConfig.class, BuildServiceBeansConfig.class})
public abstract class DatabaseTestBase {

    @BeforeEach
    public void setUp() {
        String databaseType = getDatabaseType().toString().toLowerCase();
        importSql("/sql/ddl/create-" + databaseType + ".sql");
    }

    @AfterEach
    public void tearDown() {
        importSql("/sql/ddl/drop.sql");
    }

    protected void importSql(@NonNull String resourcePath) {
        try (InputStream resource = ResourceUtils.getResource(resourcePath)) {
            doStreamWithConnection(getJdbcHelper(), connection -> executeSqlStream(connection, resource));
        } catch (Exception e) {
            log.error("Failed to execute SQL", e);
            fail();
        }
    }
}
