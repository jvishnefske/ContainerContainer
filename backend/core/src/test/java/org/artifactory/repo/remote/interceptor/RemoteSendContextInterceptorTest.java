package org.artifactory.repo.remote.interceptor;

import org.apache.http.client.methods.HttpGet;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.UserGroupService;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RemoteRepositoryRepoTypeConfig;
import org.artifactory.repo.RepoPath;
import org.artifactory.request.HeadersMultiMap;
import org.artifactory.request.Request;
import org.artifactory.security.UserInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

import java.util.Enumeration;
import java.util.List;
import java.util.stream.Stream;

import static java.util.Collections.enumeration;
import static org.artifactory.api.request.ArtifactoryHeaders.EMAIL;
import static org.artifactory.api.request.ArtifactoryHeaders.USER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * @author Andrey Tashlitsky
 */
class RemoteSendContextInterceptorTest {

    private static final String COOL_REPO = "cool-repo";
    private static final String COOL_USER = "cool-user";
    private static final String COOL_EMAIL = "cool-email";
    @Mock
    private RepositoryService repositoryService;
    @Mock
    private UserGroupService userGroupService;
    @InjectMocks
    private RemoteSendContextInterceptor interceptor;
    @Mock
    private HttpGet outgoingRequest;
    @Mock
    private Request incomingRequest;
    @Mock
    private RemoteRepoConfig repoConfig;
    @Mock
    private RemoteRepositoryRepoTypeConfig typeConfig;
    @Mock
    private RepoPath repoPath;
    @Mock
    private UserInfo userInfo;
    @Spy
    private HeadersMultiMap headers;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = openMocks(this);
        doReturn(COOL_REPO).when(repoPath).getRepoKey();
        doReturn(userInfo).when(userGroupService).currentUser();
        doReturn(COOL_USER).when(userInfo).getUsername();
        doReturn(COOL_EMAIL).when(userInfo).getEmail();
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void testBeforeRemoteHttpMethodExecution_IncomingRequestNull_DoNothing() {
        interceptor.beforeRemoteHttpMethodExecution(outgoingRequest, null, headers, repoPath);
        assertTrue(headers.getMultiHeaders().isEmpty());
        verify(headers, times(0)).addHeader(anyString(), anyString());
    }

    @Test
    void testBeforeRemoteHttpMethodExecution_RemoteRpoConfigNull_DoNothing() {
        doReturn(null).when(repositoryService).remoteRepoConfigByKey(COOL_REPO);
        interceptor.beforeRemoteHttpMethodExecution(outgoingRequest, incomingRequest, headers, repoPath);
        assertTrue(headers.getMultiHeaders().isEmpty());
        verify(headers, times(0)).addHeader(anyString(), anyString());
    }

    @Test
    void testBeforeRemoteHttpMethodExecution_SendContextFalse_DoNothing() {
        doReturn(false).when(typeConfig).isSendContext();
        doReturn(typeConfig).when(repoConfig).getRepoTypeConfig();
        doReturn(repoConfig).when(repositoryService).remoteRepoConfigByKey(COOL_REPO);
        interceptor.beforeRemoteHttpMethodExecution(outgoingRequest, incomingRequest, headers, repoPath);
        assertTrue(headers.getMultiHeaders().isEmpty());
        verify(headers, times(0)).addHeader(anyString(), anyString());
    }

    @ParameterizedTest
    @MethodSource("noHeaders")
    void testBeforeRemoteHttpMethodExecution_NoHeaders_ShouldAdd(Enumeration<String> e1, Enumeration<String> e2,
            int expectedUser, int expectedEmail) {
        doReturn(true).when(typeConfig).isSendContext();
        doReturn(typeConfig).when(repoConfig).getRepoTypeConfig();
        doReturn(repoConfig).when(repositoryService).remoteRepoConfigByKey(COOL_REPO);
        doReturn(e1).when(incomingRequest).getHeaderValues(USER);
        doReturn(e2).when(incomingRequest).getHeaderValues(EMAIL);
        interceptor.beforeRemoteHttpMethodExecution(outgoingRequest, incomingRequest, headers, repoPath);
        assertEquals(2, headers.getMultiHeaders().keySet().size());
        verify(headers, times(expectedUser)).addAll(eq(USER), eq(e1));
        verify(headers, times(expectedEmail)).addAll(eq(EMAIL), eq(e2));
    }

    private static Stream<Arguments> noHeaders() {
        return Stream.of(
                Arguments.of(enumeration(List.of()), enumeration(List.of()), 0, 0),
                Arguments.of(enumeration(List.of("cool-user1", "cool-user2")), enumeration(List.of()), 1, 0),
                Arguments.of(enumeration(List.of()), enumeration(List.of("cool-email1")), 0, 1),
                Arguments.of(enumeration(List.of("cool-user1")), enumeration(List.of("cool-email1")), 1, 1)
        );
    }
}
