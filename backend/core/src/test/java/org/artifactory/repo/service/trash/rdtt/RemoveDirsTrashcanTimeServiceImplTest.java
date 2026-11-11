package org.artifactory.repo.service.trash.rdtt;

import ch.qos.logback.classic.Level;
import com.jfrog.test.log.MemoryAppender;
import com.jfrog.test.log.MemoryAppenderUtil;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.spring.ArtifactoryApplicationContext;
import org.artifactory.storage.db.fs.dao.PropertiesDao;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;

import java.sql.SQLException;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class RemoveDirsTrashcanTimeServiceImplTest {
    private MemoryAppender memoryAppender;

    @BeforeEach
    void setUp() {
        memoryAppender = MemoryAppenderUtil.createMemoryAppender(RemoveDirsTrashcanTimeServiceImpl.class, Level.INFO);
    }

    @AfterEach
    void tearDown() {
        memoryAppender.reset();
    }

    @Test
    void runNormalMinusOne() throws SQLException {
        int batchSize = 42;
        int batchCount = -1;
        PropertiesDao propertiesDao = mock(PropertiesDao.class);
        when(propertiesDao.removeDirsTrashcanTime(anyInt())).thenReturn(batchSize-1);
        spyRemoveDirsTrashcanTimeService(propertiesDao, task -> {
            task.execute(batchSize, 0, batchCount);
            assertEquals("Batch #0 finished. %d records were removed".formatted(batchSize-1),
                    memoryAppender.getLoggedEvents().get(0).getFormattedMessage());
            assertEquals("Last batch reached. Remove Directories Trashcan Time task finishes",
                    memoryAppender.getLoggedEvents()
                            .get(memoryAppender.getLoggedEvents().size()-1).getFormattedMessage());
        });
    }

    @Test
    void runNormalZero() throws SQLException {
        int batchSize = 42;
        int batchCount = 0;
        PropertiesDao propertiesDao = mock(PropertiesDao.class);
        when(propertiesDao.removeDirsTrashcanTime(anyInt())).thenReturn(batchSize-1);
        spyRemoveDirsTrashcanTimeService(propertiesDao, task -> {
            task.execute(batchSize, 0, batchCount);
            assertEquals("Max number (%d) of batch executions reached. Remove Directories Trashcan Time task finishes"
                            .formatted(batchCount), memoryAppender.getLoggedEvents().get(0).getFormattedMessage());
        });
    }

    @Test
    void runNormalOne() throws SQLException {
        int batchSize = 42;
        int batchCount = 1;
        PropertiesDao propertiesDao = mock(PropertiesDao.class);
        when(propertiesDao.removeDirsTrashcanTime(anyInt())).thenReturn(batchSize);
        spyRemoveDirsTrashcanTimeService(propertiesDao, task -> {
            task.execute(batchSize, 0, batchCount);
            assertEquals("Batch #0 finished. %d records were removed".formatted(batchSize),
                    memoryAppender.getLoggedEvents().get(0).getFormattedMessage());
            assertEquals("Max number (1) of batch executions reached. Remove Directories Trashcan Time task finishes",
                    memoryAppender.getLoggedEvents()
                            .get(memoryAppender.getLoggedEvents().size()-1).getFormattedMessage());
        });
    }
    @Test
    void runNormalFive() throws SQLException {
        int batchSize = 42;
        int batchCount = 5;
        PropertiesDao propertiesDao = mock(PropertiesDao.class);
        when(propertiesDao.removeDirsTrashcanTime(anyInt())).thenReturn(batchSize);
        spyRemoveDirsTrashcanTimeService(propertiesDao, task -> {
            task.execute(batchSize, 0, batchCount);
            assertEquals("Batch #%d finished. %d records were removed".formatted(batchCount-1, batchSize),
                    memoryAppender.getLoggedEvents().get(batchCount-1).getFormattedMessage());
            assertEquals("Max number (%d) of batch executions reached. Remove Directories Trashcan Time task finishes"
                            .formatted(batchCount),
                    memoryAppender.getLoggedEvents()
                            .get(memoryAppender.getLoggedEvents().size()-1).getFormattedMessage());
        });
    }

    @ParameterizedTest
    @MethodSource
    void runNormal(RunNormalArgs args) throws SQLException {
        int batchSize = 42;
        PropertiesDao propertiesDao = mock(PropertiesDao.class);

        int firstReturn = args.firstReturn();
        Integer[] restReturn = args.restReturns();

        when(propertiesDao.removeDirsTrashcanTime(anyInt())).thenReturn(firstReturn, restReturn);
        spyRemoveDirsTrashcanTimeService(propertiesDao, task -> {
            task.execute(batchSize, 0, -1);
            assertEquals("Batch #0 finished. %d records were removed".formatted(firstReturn),
                    memoryAppender.getLoggedEvents().get(0).getFormattedMessage());
            assertEquals("Last batch reached. Remove Directories Trashcan Time task finishes",
                    memoryAppender.getLoggedEvents()
                            .get(memoryAppender.getLoggedEvents().size()-1).getFormattedMessage());
        });
    }

    private record RunNormalArgs(int firstReturn, Integer[] restReturns){}
    private static Stream<Arguments> runNormal() {
        return Stream.of(
                arguments(new RunNormalArgs(0, new Integer[0])),
                arguments(new RunNormalArgs( 1, new Integer[0])),
                arguments(new RunNormalArgs( 42, new Integer[]{0})),
                arguments(new RunNormalArgs(100, new Integer[]{58, 16, 0}))
        );
    }

    @ParameterizedTest
    @MethodSource
    void runWithException(Exception expectedError) throws SQLException {
        int batchSize = 42;
        PropertiesDao propertiesDao = mock(PropertiesDao.class);
        when(propertiesDao.removeDirsTrashcanTime(anyInt())).thenThrow(expectedError);
        spyRemoveDirsTrashcanTimeService(propertiesDao, task -> {
            task.execute(batchSize - 1, 0, -1);
            String errorMessage = memoryAppender.getLoggedEvents().get(0).getMessage();
            assertTrue(errorMessage.startsWith("Error while Remove Directories Trashcan Time execution"));
            assertTrue(errorMessage.endsWith(expectedError.getMessage()));
        });
    }

    @Test
    void runWithInterruptedException() throws SQLException {
        int batchSize = 42;
        PropertiesDao propertiesDao = mock(PropertiesDao.class);
        when(propertiesDao.removeDirsTrashcanTime(anyInt())).thenReturn(batchSize + 1);

        spyRemoveDirsTrashcanTimeService(propertiesDao, task -> {
            RemoveDirsTrashcanTimeServiceImpl x = spy(task);
            try {
                doThrow(new InterruptedException()).when(x).delayNextInvocation(0);
                x.execute(batchSize, 0, -1);
                assertTrue(Thread.currentThread().isInterrupted());
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static Stream<Arguments> runWithException() {
        return Stream.of(
                Arguments.of(new SQLException("SQL-TEST")),
                Arguments.of(new RuntimeException("COMMON-TEST")));
    }


    private void spyRemoveDirsTrashcanTimeService(PropertiesDao propertiesDao,
            Consumer<RemoveDirsTrashcanTimeServiceImpl> executionBody) {
        // Mock this code: this.propertiesDao = ContextHelper.get().beanForType(PropertiesDao.class);
        try (MockedStatic<ContextHelper> contextHelper = mockStatic(ContextHelper.class)) {
            ArtifactoryApplicationContext artifactoryApplicationContext = mock(ArtifactoryApplicationContext.class);
            when(artifactoryApplicationContext.beanForType(PropertiesDao.class)).thenReturn(propertiesDao);
            contextHelper.when(ContextHelper::get).thenReturn(artifactoryApplicationContext);
            RemoveDirsTrashcanTimeServiceImpl task = new RemoveDirsTrashcanTimeServiceImpl(propertiesDao);
            executionBody.accept(task);
        }
    }
}