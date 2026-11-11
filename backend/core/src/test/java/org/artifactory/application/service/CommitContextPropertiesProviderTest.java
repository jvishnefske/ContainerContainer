package org.artifactory.application.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.access.token.JwtAccessToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.LoggerFactory;

import static org.artifactory.common.ConstantValues.appTrustCommitContextDataEnabled;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * @author Oleksandr Legenkiy
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CommitContextPropertiesProviderTest extends ArtifactoryHomeBoundJUnitTest {

    private static final String DEFAULT_EXT = """
            {
                "vcsCommit": {
                    "revision": "someRevision",
                    "vcsUrl": "someVcsUrl",
                    "branch": "someBranch"
                }
            }
            """;
    private static final String REVISION_PROP_KEY = "jf.revision";
    private static final String VCS_URL_PROP_KEY = "jf.vcsUrl";
    private static final String BRANCH_PROP_KEY = "jf.branch";

    @Mock
    private JwtAccessToken jwtAccessTokenMock;
    private ListAppender<ILoggingEvent> listAppender;
    private CommitContextPropertiesProvider provider;

    @BeforeEach
    void setUp() {
        provider = new CommitContextPropertiesProvider();
        homeStub.setProperty(appTrustCommitContextDataEnabled, "true");
        initLoggerAppender();
    }

    @Test
    void testFeatureDisabled() {
        homeStub.setProperty(appTrustCommitContextDataEnabled, "false");
        var result = provider.retrieveCommitContextProperties(jwtAccessTokenMock);
        assertTrue(result.isEmpty());
        assertEquals("AppTrust commit context feature is disabled. Returning empty map",
                listAppender.list.getFirst().getFormattedMessage());
    }

    @Test
    void testExtIsMissing() {
        when(jwtAccessTokenMock.getExtension()).thenReturn(null);
        var result = provider.retrieveCommitContextProperties(jwtAccessTokenMock);
        assertTrue(result.isEmpty());
        assertEquals("The authentication token's 'ext' is missing. Returning empty map",
                listAppender.list.getFirst().getFormattedMessage());
    }

    @Test
    void testEmptyVcsInfo() {
        when(jwtAccessTokenMock.getExtension()).thenReturn("{}");
        var result = provider.retrieveCommitContextProperties(jwtAccessTokenMock);
        assertTrue(result.isEmpty());

        when(jwtAccessTokenMock.getExtension()).thenReturn("{\"vcsCommit\": {}}");
        result = provider.retrieveCommitContextProperties(jwtAccessTokenMock);
        assertTrue(result.isEmpty());

        assertEquals("The 'vcsCommit' is empty or missing. Returning empty map",
                listAppender.list.getFirst().getFormattedMessage());
        assertEquals("The 'vcsCommit' is empty or missing. Returning empty map",
                listAppender.list.get(1).getFormattedMessage());
    }

    @Test
    void testMissingFieldsChecks() {
        when(jwtAccessTokenMock.getExtension()).thenReturn("""
                {
                    "vcsCommit": {
                        "someNonRelevantField": "value"
                    }
                }
                """);
        var result = provider.retrieveCommitContextProperties(jwtAccessTokenMock);
        assertTrue(result.isEmpty());
        assertEquals("'vcsCommit' is missing 'revision' field",
                listAppender.list.getFirst().getFormattedMessage());
        assertEquals("'vcsCommit' is missing 'vcsUrl' field",
                listAppender.list.get(1).getFormattedMessage());
        assertEquals("'vcsCommit' is missing 'branch' field",
                listAppender.list.get(2).getFormattedMessage());
    }

    @Test
    void testExceptionHandled() {
        var exc = new RuntimeException("TEST");
        when(jwtAccessTokenMock.getExtension()).thenThrow(exc);
        var result = provider.retrieveCommitContextProperties(jwtAccessTokenMock);
        assertTrue(result.isEmpty());
        assertEquals("Failed to retrieve commit info for AppTrust integration",
                listAppender.list.getFirst().getMessage());
    }

    @Test
    void testCommitPropsRetrievedSuccessfully() {
        when(jwtAccessTokenMock.getExtension()).thenReturn(DEFAULT_EXT);
        var result = provider.retrieveCommitContextProperties(jwtAccessTokenMock);
        assertEquals(3, result.size());
        assertEquals("someRevision", result.get(REVISION_PROP_KEY));
        assertEquals("someVcsUrl", result.get(VCS_URL_PROP_KEY));
        assertEquals("someBranch", result.get(BRANCH_PROP_KEY));
    }

    private void initLoggerAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger(CommitContextPropertiesProvider.class);
        logger.setLevel(Level.DEBUG);
        listAppender = new ListAppender<>();
        logger.addAppender(listAppender);
        listAppender.start();
    }
}