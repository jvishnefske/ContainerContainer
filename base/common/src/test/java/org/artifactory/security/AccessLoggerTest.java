package org.artifactory.security;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.request.RequestThreadLocal;
import org.artifactory.request.RequestWrapper;
import org.artifactory.security.access.AccessTokenAuthentication;
import org.artifactory.security.props.auth.PropsAuthenticationToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.net.URISyntaxException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccessLoggerTest {
    private ListAppender<ILoggingEvent> listAppender;
    private Logger logger;
    private AccessTokenAuthentication tokenAuthentication;

    @BeforeEach
    void setUp() throws URISyntaxException {
        this.logger = (Logger) LoggerFactory.getLogger(AccessLogger.class);
        this.listAppender = new ListAppender<>();
        this.listAppender.start();
        this.logger.addAppender(listAppender);
        this.logger.setLevel(Level.INFO);
        this.tokenAuthentication = createTokenAuthentication();

        AccessLogger.setSkipDownloadLogs(true);

        SecurityContext securityContext = mock(SecurityContext.class);
        Mockito.when(securityContext.getAuthentication()).thenReturn(this.tokenAuthentication);
        SecurityContextHolder.setContext(securityContext);

        RequestWrapper requestMock = mock(RequestWrapper.class);
        when(requestMock.getClientAddress()).thenReturn("127.0.0.2");
        RequestThreadLocal.bind(requestMock);
    }

    @AfterEach
    void tearDown() {
        AccessLogger.setSkipDownloadLogs(false);
        SecurityContextHolder.clearContext();
        RequestThreadLocal.unbind();
        this.logger.detachAppender(listAppender);
    }

    AccessTokenAuthentication createTokenAuthentication() {
        AccessTokenAuthentication authentication = mock(AccessTokenAuthentication.class);
        when(authentication.getName()).thenReturn("user@test.com");
        return authentication;
    }

    ILoggingEvent getCapturedLogEvent() {
        assertEquals(1, this.listAppender.list.size(), "Expected a single event in the artifactory-access.log");
        return this.listAppender.list.get(0);
    }

    @Test
    void annotated_shouldGenerateCorrectLog() {
        RepoPath repoPath = RepoPathFactory.create("repo", "folder/file.txt");
        AccessLogger.annotated(repoPath);

        ILoggingEvent actual = getCapturedLogEvent();
        assertEquals(
                "[ACCEPTED ANNOTATE] repo:folder/file.txt  for client : user@test.com / 127.0.0.2 [token]",
                actual.getFormattedMessage());
        assertArrayEquals(
                new Object[] {"ACCEPTED", "ANNOTATE", repoPath, "", "user@test.com", "127.0.0.2", "token"},
                actual.getArgumentArray()
        );
    }

    @Test
    void annotateDenied_shouldGenerateCorrectLog() {
        RepoPath repoPath = RepoPathFactory.create("repo", "folder/file.txt");
        AccessLogger.annotateDenied(repoPath);

        ILoggingEvent actual = getCapturedLogEvent();
        assertEquals(
                "[DENIED ANNOTATE] repo:folder/file.txt  for client : user@test.com / 127.0.0.2 [token]",
                actual.getFormattedMessage());
        assertArrayEquals(
                new Object[] {"DENIED", "ANNOTATE", repoPath, "", "user@test.com", "127.0.0.2", "token"},
                actual.getArgumentArray()
        );
    }

    @Test
    void downloaded_shouldGenerateCorrectLog() {
        RepoPath repoPath = RepoPathFactory.create("repo", "folder/file.txt");
        AccessLogger.setSkipDownloadLogs(false);
        AccessLogger.downloaded(repoPath);

        ILoggingEvent actual = getCapturedLogEvent();
        assertEquals(
                "[ACCEPTED DOWNLOAD] repo:folder/file.txt  for client : user@test.com / 127.0.0.2 [token]",
                actual.getFormattedMessage());
        assertArrayEquals(
                new Object[] {"ACCEPTED", "DOWNLOAD", repoPath, "", "user@test.com", "127.0.0.2", "token"},
                actual.getArgumentArray()
        );
    }

    @Test
    void downloadDenied_shouldGenerateCorrectLog() {
        RepoPath repoPath = RepoPathFactory.create("repo", "folder/file.txt");
        AccessLogger.setSkipDownloadLogs(false);
        AccessLogger.downloadDenied(repoPath);

        ILoggingEvent actual = getCapturedLogEvent();
        assertEquals(
                "[DENIED DOWNLOAD] repo:folder/file.txt  for client : user@test.com / 127.0.0.2 [token]",
                actual.getFormattedMessage());
        assertArrayEquals(
                new Object[] {"DENIED", "DOWNLOAD", repoPath, "", "user@test.com", "127.0.0.2", "token"},
                actual.getArgumentArray()
        );
    }

    @Test
    void deployed_shouldGenerateCorrectLog() {
        RepoPath repoPath = RepoPathFactory.create("repo", "folder/file.txt");
        AccessLogger.setSkipDownloadLogs(false);
        AccessLogger.deployed(repoPath);

        ILoggingEvent actual = getCapturedLogEvent();
        assertEquals(
                "[ACCEPTED DEPLOY] repo:folder/file.txt  for client : user@test.com / 127.0.0.2 [token]",
                actual.getFormattedMessage());
        assertArrayEquals(
                new Object[] {"ACCEPTED", "DEPLOY", repoPath, "", "user@test.com", "127.0.0.2", "token"},
                actual.getArgumentArray()
        );
    }

    @Test
    void downloaded_shouldNotGenerateLog_whenDisabled() {
        RepoPath repoPath = RepoPathFactory.create("repo", "folder/file.txt");
        AccessLogger.downloaded(repoPath);

        assertTrue(this.listAppender.list.isEmpty(), "Expected no log events in the artifactory-access.log");
    }

    @Test
    void downloadDenied_shouldNotGenerateLog_whenDisabled() {
        RepoPath repoPath = RepoPathFactory.create("repo", "folder/file.txt");
        AccessLogger.downloadDenied(repoPath);

        assertTrue(this.listAppender.list.isEmpty(), "Expected no log events in the artifactory-access.log");
    }

    @Test
    void deployDenied_shouldGenerateCorrectLog() {
        RepoPath repoPath = RepoPathFactory.create("repo", "folder/file.txt");
        AccessLogger.deployDenied(repoPath);

        ILoggingEvent actual = getCapturedLogEvent();
        assertEquals(
                "[DENIED DEPLOY] repo:folder/file.txt  for client : user@test.com / 127.0.0.2 [token]",
                actual.getFormattedMessage());
        assertArrayEquals(
                new Object[] {"DENIED", "DEPLOY", repoPath, "", "user@test.com", "127.0.0.2", "token"},
                actual.getArgumentArray()
        );
    }

    @Test
    void unauthorizedSearch_shouldGenerateCorrectLog() {
        // Act
        AccessLogger.unauthorizedSearch();

        // Assert
        ILoggingEvent actual = getCapturedLogEvent();
        assertEquals(
                "[DENIED SEARCH]   for client : user@test.com / 127.0.0.2 [token]",
                actual.getFormattedMessage());
        assertArrayEquals(
                new Object[] {"DENIED", "SEARCH", "", "", "user@test.com", "127.0.0.2", "token"},
                actual.getArgumentArray()
        );
    }

    @Test
    void loggedIn_token_shouldGenerateCorrectLog() {
        AccessLogger.loggedIn(this.tokenAuthentication);

        ILoggingEvent actual = getCapturedLogEvent();
        assertEquals(
                "[ACCEPTED LOGIN]   for client : user@test.com / 127.0.0.2 [token]",
                actual.getFormattedMessage());
        assertArrayEquals(
                new Object[] {"ACCEPTED", "LOGIN", "", "", "user@test.com", "127.0.0.2", "token"},
                actual.getArgumentArray()
        );
    }

    @Test
    void loggedIn_apiKey_shouldGenerateCorrectLog() {
        PropsAuthenticationToken authentication = mock(PropsAuthenticationToken.class);
        when(authentication.getName()).thenReturn("svc-build");
        when(authentication.getPropsKey()).thenReturn("apiKey");

        AccessLogger.loggedIn(authentication);

        ILoggingEvent actual = getCapturedLogEvent();
        assertEquals(
                "[ACCEPTED LOGIN]   for client : svc-build / 127.0.0.2 [apiKey]",
                actual.getFormattedMessage());
        assertArrayEquals(
                new Object[] {"ACCEPTED", "LOGIN", "", "", "svc-build", "127.0.0.2", "apiKey"},
                actual.getArgumentArray()
        );
    }

    @Test
    void loggedIn_rest_shouldGenerateCorrectLog() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("reader");

        AccessLogger.loggedIn(authentication);

        ILoggingEvent actual = getCapturedLogEvent();
        assertEquals(
                "[ACCEPTED LOGIN]   for client : reader / 127.0.0.2",
                actual.getFormattedMessage());
        assertArrayEquals(
                new Object[] {"ACCEPTED", "LOGIN", "", "", "reader", "127.0.0.2", ""},
                actual.getArgumentArray()
        );
    }

    @Test
    void loginDenied_shouldGenerateCorrectLog() {
        AccessLogger.loginDenied(this.tokenAuthentication);

        ILoggingEvent actual = getCapturedLogEvent();
        assertEquals(
                "[DENIED LOGIN]   for client : user@test.com / 127.0.0.2 [token]",
                actual.getFormattedMessage());
        assertArrayEquals(
                new Object[] {"DENIED", "LOGIN", "", "", "user@test.com", "127.0.0.2", "token"},
                actual.getArgumentArray()
        );
    }

    @Test
    void loginDenied_user_shouldGenerateCorrectLog() {
        PropsAuthenticationToken authentication = mock(PropsAuthenticationToken.class);
        when(authentication.getPropsKey()).thenReturn("apiKey");
        AccessLogger.loginDenied(authentication, "reader");

        ILoggingEvent actual = getCapturedLogEvent();
        assertEquals(
                "[DENIED LOGIN]   for client : reader / 127.0.0.2 [apiKey]",
                actual.getFormattedMessage());
        assertArrayEquals(
                new Object[] {"DENIED", "LOGIN", "", "", "reader", "127.0.0.2", "apiKey"},
                actual.getArgumentArray()
        );
    }

    @Test
    void loginDenied_anonymous_shouldGenerateCorrectLog() {
        Authentication authentication = mock(Authentication.class);
        AccessLogger.loginDenied(authentication, "");

        ILoggingEvent actual = getCapturedLogEvent();
        assertEquals(
                "[DENIED LOGIN]   for client : NA / 127.0.0.2",
                actual.getFormattedMessage());
        assertArrayEquals(
                new Object[] {"DENIED", "LOGIN", "", "", "NA", "127.0.0.2", ""},
                actual.getArgumentArray()
        );
    }

    @Test
    void configurationChanged_shouldGenerateCorrectLog() {
        AccessLogger.configurationChanged();

        ILoggingEvent actual = getCapturedLogEvent();
        assertEquals(
                "[ACCEPTED CONFIGURATION_CHANGE]   for client : user@test.com / 127.0.0.2 [token]",
                actual.getFormattedMessage());
        assertArrayEquals(
                new Object[] {"ACCEPTED", "CONFIGURATION_CHANGE", "", "", "user@test.com", "127.0.0.2", "token"},
                actual.getArgumentArray()
        );
    }

    @Test
    void created_shouldGenerateCorrectLog() {
        AccessLogger.created("custom object <1>");

        ILoggingEvent actual = getCapturedLogEvent();
        assertEquals(
                "[ACCEPTED CREATE]  custom object <1> for client : user@test.com / 127.0.0.2 [token]",
                actual.getFormattedMessage());
        assertArrayEquals(
                new Object[] {"ACCEPTED", "CREATE", "", "custom object <1>", "user@test.com", "127.0.0.2", "token"},
                actual.getArgumentArray()
        );
    }

    @Test
    void updated_shouldGenerateCorrectLog() {
        AccessLogger.updated("object <1>");

        ILoggingEvent actual = getCapturedLogEvent();
        assertEquals(
                "[ACCEPTED UPDATE]  object <1> for client : user@test.com / 127.0.0.2 [token]",
                actual.getFormattedMessage());
        assertArrayEquals(
                new Object[] {"ACCEPTED", "UPDATE", "", "object <1>", "user@test.com", "127.0.0.2", "token"},
                actual.getArgumentArray()
        );
    }

    @Test
    void deleted_shouldGenerateCorrectLog() {
        AccessLogger.deleted("object <1>");

        ILoggingEvent actual = getCapturedLogEvent();
        assertEquals(
                "[ACCEPTED DELETE]  object <1> for client : user@test.com / 127.0.0.2 [token]",
                actual.getFormattedMessage());
        assertArrayEquals(
                new Object[] {"ACCEPTED", "DELETE", "", "object <1>", "user@test.com", "127.0.0.2", "token"},
                actual.getArgumentArray()
        );
    }

    @Test
    void deleted_path_shouldGenerateCorrectLog() {
        RepoPath repoPath = RepoPathFactory.create("repo", "folder/file.txt");
        AccessLogger.deleted(repoPath);

        ILoggingEvent actual = getCapturedLogEvent();
        assertEquals(
                "[ACCEPTED DELETE] repo:folder/file.txt  for client : user@test.com / 127.0.0.2 [token]",
                actual.getFormattedMessage());
        assertArrayEquals(
                new Object[] {"ACCEPTED", "DELETE", repoPath, "", "user@test.com", "127.0.0.2", "token"},
                actual.getArgumentArray()
        );
    }

    @Test
    void deleteDenied_path_shouldGenerateCorrectLog() {
        RepoPath repoPath = RepoPathFactory.create("repo", "folder/file.txt");
        AccessLogger.deleteDenied(repoPath);

        ILoggingEvent actual = getCapturedLogEvent();
        assertEquals(
                "[DENIED DELETE] repo:folder/file.txt  for client : user@test.com / 127.0.0.2 [token]",
                actual.getFormattedMessage());
        assertArrayEquals(
                new Object[] {"DENIED", "DELETE", repoPath, "", "user@test.com", "127.0.0.2", "token"},
                actual.getArgumentArray()
        );
    }

    @Test
    void approved_shouldGenerateCorrectLog() {
        AccessLogger.approved("object <1>");

        ILoggingEvent actual = getCapturedLogEvent();
        assertEquals(
                "[ACCEPTED APPROVE]  object <1> for client : user@test.com / 127.0.0.2 [token]",
                actual.getFormattedMessage());
        assertArrayEquals(
                new Object[] {"ACCEPTED", "APPROVE", "", "object <1>", "user@test.com", "127.0.0.2", "token"},
                actual.getArgumentArray()
        );
    }

    @Test
    void propertyAdded_shouldGenerateCorrectLog() {
        RepoPath repoPath = RepoPathFactory.create("repo", "1.txt");
        AccessLogger.propertyAdded(repoPath, "<name>");

        ILoggingEvent actual = getCapturedLogEvent();
        assertEquals(
                "[ACCEPTED PROPERTY_ADDED] repo:1.txt <name> for client : user@test.com / 127.0.0.2 [token]",
                actual.getFormattedMessage());
        assertArrayEquals(
                new Object[] {"ACCEPTED", "PROPERTY_ADDED", repoPath, "<name>", "user@test.com", "127.0.0.2", "token"},
                actual.getArgumentArray()
        );
    }

    @Test
    void propertyAddedDenied_shouldGenerateCorrectLog() {
        RepoPath repoPath = RepoPathFactory.create("repo", "1.txt");
        AccessLogger.propertyAddedDenied(repoPath, "<name>");

        ILoggingEvent actual = getCapturedLogEvent();
        assertEquals(
                "[DENIED PROPERTY_ADDED] repo:1.txt <name> for client : user@test.com / 127.0.0.2 [token]",
                actual.getFormattedMessage());
        assertArrayEquals(
                new Object[] {"DENIED", "PROPERTY_ADDED", repoPath, "<name>", "user@test.com", "127.0.0.2", "token"},
                actual.getArgumentArray()
        );
    }

    @Test
    void propertyDeleted_shouldGenerateCorrectLog() {
        RepoPath repoPath = RepoPathFactory.create("repo", "1.txt");
        AccessLogger.propertyDeleted(repoPath, "<id>");

        ILoggingEvent actual = getCapturedLogEvent();
        assertEquals(
                "[ACCEPTED PROPERTY_REMOVED] repo:1.txt <id> for client : user@test.com / 127.0.0.2 [token]",
                actual.getFormattedMessage());
        assertArrayEquals(
                new Object[] {"ACCEPTED", "PROPERTY_REMOVED", repoPath, "<id>", "user@test.com", "127.0.0.2", "token"},
                actual.getArgumentArray()
        );
    }

    @Test
    void propertyDeletedDenied_shouldGenerateCorrectLog() {
        RepoPath repoPath = RepoPathFactory.create("repo", "1.txt");
        AccessLogger.propertyDeletedDenied(repoPath, "<id>");

        ILoggingEvent actual = getCapturedLogEvent();
        assertEquals(
                "[DENIED PROPERTY_REMOVED] repo:1.txt <id> for client : user@test.com / 127.0.0.2 [token]",
                actual.getFormattedMessage());
        assertArrayEquals(
                new Object[] {"DENIED", "PROPERTY_REMOVED", repoPath, "<id>", "user@test.com", "127.0.0.2", "token"},
                actual.getArgumentArray()
        );
    }

    @Test
    void propertyUpdated_shouldGenerateCorrectLog() {
        RepoPath repoPath = RepoPathFactory.create("repo", "1.txt");
        AccessLogger.propertyUpdated(repoPath, "<id>");

        ILoggingEvent actual = getCapturedLogEvent();
        assertEquals(
                "[ACCEPTED PROPERTY_UPDATED] repo:1.txt <id> for client : user@test.com / 127.0.0.2 [token]",
                actual.getFormattedMessage());
        assertArrayEquals(
                new Object[] {"ACCEPTED", "PROPERTY_UPDATED", repoPath, "<id>", "user@test.com", "127.0.0.2", "token"},
                actual.getArgumentArray()
        );
    }

    @Test
    void buildCreate_shouldGenerateCorrectLog() {
        AccessLogger.buildCreate("my_build");

        ILoggingEvent actual = getCapturedLogEvent();
        assertEquals(
                "[ACCEPTED BUILD_CREATE]  my_build for client : user@test.com / 127.0.0.2 [token]",
                actual.getFormattedMessage());
        assertArrayEquals(
                new Object[] {"ACCEPTED", "BUILD_CREATE", "", "my_build", "user@test.com", "127.0.0.2", "token"},
                actual.getArgumentArray()
        );
    }

    @Test
    void buildCreateDenied_shouldGenerateCorrectLog() {
        AccessLogger.buildCreateDenied("my_build");

        ILoggingEvent actual = getCapturedLogEvent();
        assertEquals(
                "[DENIED BUILD_CREATE]  my_build for client : user@test.com / 127.0.0.2 [token]",
                actual.getFormattedMessage());
        assertArrayEquals(
                new Object[] {"DENIED", "BUILD_CREATE", "", "my_build", "user@test.com", "127.0.0.2", "token"},
                actual.getArgumentArray()
        );
    }

    @Test
    void buildPromote_shouldGenerateCorrectLog() {
        AccessLogger.buildPromote("my_build");

        ILoggingEvent actual = getCapturedLogEvent();
        assertEquals(
                "[ACCEPTED BUILD_PROMOTE]  my_build for client : user@test.com / 127.0.0.2 [token]",
                actual.getFormattedMessage());
        assertArrayEquals(
                new Object[] {"ACCEPTED", "BUILD_PROMOTE", "", "my_build", "user@test.com", "127.0.0.2", "token"},
                actual.getArgumentArray()
        );
    }

    @Test
    void buildPromoteDenied_shouldGenerateCorrectLog() {
        AccessLogger.buildPromoteDenied("my_build");

        ILoggingEvent actual = getCapturedLogEvent();
        assertEquals(
                "[DENIED BUILD_PROMOTE]  my_build for client : user@test.com / 127.0.0.2 [token]",
                actual.getFormattedMessage());
        assertArrayEquals(
                new Object[] {"DENIED", "BUILD_PROMOTE", "", "my_build", "user@test.com", "127.0.0.2", "token"},
                actual.getArgumentArray()
        );
    }

    @Test
    void buildDelete_shouldGenerateCorrectLog() {
        AccessLogger.buildDelete("my_build");

        ILoggingEvent actual = getCapturedLogEvent();
        assertEquals(
                "[ACCEPTED BUILD_DELETE]  my_build for client : user@test.com / 127.0.0.2 [token]",
                actual.getFormattedMessage());
        assertArrayEquals(
                new Object[] {"ACCEPTED", "BUILD_DELETE", "", "my_build", "user@test.com", "127.0.0.2", "token"},
                actual.getArgumentArray()
        );
    }

    @Test
    void buildDeleteDenied_shouldGenerateCorrectLog() {
        AccessLogger.buildDeleteDenied("my_build");

        ILoggingEvent actual = getCapturedLogEvent();
        assertEquals(
                "[DENIED BUILD_DELETE]  my_build for client : user@test.com / 127.0.0.2 [token]",
                actual.getFormattedMessage());
        assertArrayEquals(
                new Object[] {"DENIED", "BUILD_DELETE", "", "my_build", "user@test.com", "127.0.0.2", "token"},
                actual.getArgumentArray()
        );
    }
}