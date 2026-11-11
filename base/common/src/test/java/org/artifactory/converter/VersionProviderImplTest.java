package org.artifactory.converter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.artifactory.common.storage.db.properties.DbVersionInfo;
import org.artifactory.test.ArtifactoryHomeStub;
import org.artifactory.version.ArtifactoryServiceVersionImpl;
import org.artifactory.version.CompoundVersionDetails;
import org.artifactory.version.InstallationType;
import org.artifactory.version.VersionProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Stream;


public class VersionProviderImplTest {

    static Level oldLevel;
    static ListAppender<ILoggingEvent> listAppender = new ListAppender<>();

    @BeforeAll
    public static void setUp() {
        listAppender.start();
        Logger logger = (Logger) LoggerFactory.getLogger(VersionProviderImpl.class);
        logger.addAppender(listAppender);
        oldLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
    }

    @AfterAll
    public static void preDestroy() {
        Logger logger = (Logger) LoggerFactory.getLogger(VersionProviderImpl.class);
        logger.setLevel(oldLevel);
    }

    @ParameterizedTest
    @MethodSource("versions")
    void getInstallationType(CompoundVersionDetails from, CompoundVersionDetails to, InstallationType type,
            String fromVersion, String toVersion,
            String fromRevision, String toRevision) {
        ArtifactoryHomeStub home = new ArtifactoryHomeStub("");

        VersionProvider versionProvider = Mockito.spy(new VersionProviderImpl(home));
        Mockito.when(versionProvider.getOriginalDbVersion()).thenReturn(from);
        Mockito.when(versionProvider.getRunningServiceVersion()).thenReturn(to);

        InstallationType actual = versionProvider.getInstallationType();
        List<ILoggingEvent> logsList = listAppender.list;
        logsList.forEach(e -> System.out.println(e.getFormattedMessage()));
        if (!type.equals(InstallationType.RESTART)) {
            Assertions.assertTrue(logsList.stream()
                    .anyMatch(logEvent -> logEvent.getFormattedMessage()
                            .contains("From Version: '" + fromVersion + "' To Version: '" + toVersion + "'")));
            Assertions.assertTrue(logsList.stream()
                    .anyMatch(logEvent -> logEvent.getFormattedMessage()
                            .contains("From Revision: '" + fromRevision + "' To Revision: '" + toRevision + "'")));
            Assertions.assertEquals(actual, type);
            logsList.clear();
        }
    }

    @Test
    void getDbCompoundVersionDetails() {
        ArtifactoryHomeStub home = new ArtifactoryHomeStub("");
        DbVersionInfo dbVersionInfo = new DbVersionInfo(1L, "7.71.13", Integer.MAX_VALUE,
                3L, "7.71.10");
        VersionProviderImpl versionProvider = Mockito.spy(new VersionProviderImpl(home));
        CompoundVersionDetails versionDetails =
                versionProvider.getDbCompoundVersionDetails(dbVersionInfo);
        Assertions.assertNotNull(versionDetails);
        Assertions.assertEquals("7.71.13", versionDetails.getVersion().getVersion());
        Assertions.assertEquals(77113900, versionDetails.getVersion().getRevision());

        dbVersionInfo = new DbVersionInfo(1L, "7.71.13", 77113900,
                3L, "7.71.10");
        CompoundVersionDetails versionDetails2 =
                versionProvider.getDbCompoundVersionDetails(dbVersionInfo);
        Assertions.assertNotNull(versionDetails2);
        Assertions.assertEquals("7.71.13", versionDetails2.getVersion().getVersion());
        Assertions.assertEquals(77113900, versionDetails2.getVersion().getRevision());

    }

    static Stream<Arguments> versions() {
        return Stream.of(
                Arguments.of(new CompoundVersionDetails(new ArtifactoryServiceVersionImpl("1.1.1", 111, null),
                                null, 0L),
                        new CompoundVersionDetails(new ArtifactoryServiceVersionImpl("1.1.2", 112, null),
                                null, 0L),
                        InstallationType.UPGRADE, "1.1.1", "1.1.2", "111", "112"),
                Arguments.of(new CompoundVersionDetails(new ArtifactoryServiceVersionImpl("1.1.3", 113, null),
                                null, 0L),
                        new CompoundVersionDetails(new ArtifactoryServiceVersionImpl("1.1.2", 112, null),
                                null, 0L),
                        InstallationType.ROLLBACK, "1.1.3", "1.1.2", "113", "112"),
                Arguments.of(new CompoundVersionDetails(new ArtifactoryServiceVersionImpl("1.1.1", 111, null),
                                null, 0L),
                        new CompoundVersionDetails(new ArtifactoryServiceVersionImpl("1.1.1", 111, null),
                                null, 0L),
                        InstallationType.RESTART, "1.1.1", "1.1.1", "111", "111"),
                Arguments.of(new CompoundVersionDetails(new ArtifactoryServiceVersionImpl("1.1.1", 111, null),
                        null, 0L), null, InstallationType.UNKNOWN, "1.1.1", "N/A", "111", "N/A"),
                Arguments.of(null, null, InstallationType.UNKNOWN, "N/A", "N/A", "N/A", "N/A"),
                Arguments.of(null, new CompoundVersionDetails(new ArtifactoryServiceVersionImpl("1.1.1", 111, null),
                        null, 0L), InstallationType.UNKNOWN, "N/A", "1.1.1", "N/A", "111")
        );
    }
}
