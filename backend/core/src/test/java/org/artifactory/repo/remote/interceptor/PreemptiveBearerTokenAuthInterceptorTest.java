package org.artifactory.repo.remote.interceptor;

import lombok.SneakyThrows;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.ProtocolVersion;
import org.apache.http.auth.AuthState;
import org.apache.http.auth.Credentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.message.BasicRequestLine;
import org.artifactory.addon.common.DefaultRemoteTokenProvider;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.spring.InternalArtifactoryContext;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.util.bearer.ArtifactoryBearerScheme;
import org.jfrog.client.http.auth.PreemptiveBearerTokenAuthInterceptor;
import org.mockito.Mockito;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.hibernate.validator.internal.util.Contracts.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertNull;

/**
 * @author Einas Ashkar
 */
@Test
public class PreemptiveBearerTokenAuthInterceptorTest extends ArtifactoryHomeBoundTest {
    @BeforeMethod
    public void setUp() throws Exception {
        bindArtifactoryHome();
        ArtifactoryContext contextMock = mock(InternalArtifactoryContext.class);
        ArtifactoryContextThreadBinder.bind(contextMock);
        DefaultRemoteTokenProvider defaultRemoteTokenProvider = mock(DefaultRemoteTokenProvider.class);
        when(ContextHelper.get().beanForType(DefaultRemoteTokenProvider.class))
                .thenReturn(defaultRemoteTokenProvider);
    }

    @AfterMethod
    public void tearDown() {
        ArtifactoryContextThreadBinder.unbind();
    }

    @SneakyThrows
    public void injectPreemptiveBearerTokenTest() {
        String repoKey = "remoteRepo";
        RepositoryService repositoryService = Mockito.mock(RepositoryService.class);
        when(ContextHelper.get().beanForType(RepositoryService.class))
                .thenReturn(repositoryService);
        HttpRequest request = mock(HttpRequest.class);
        when(request.getRequestLine())
                .thenReturn(new BasicRequestLine("GET", "foo/bar", new ProtocolVersion("HTTP", 1, 1)));
        AuthState authState = new AuthState();
        HttpClientContext context = mock(HttpClientContext.class);
        CredentialsProvider credentialsProvider = mock(CredentialsProvider.class);

        HttpHost httpHost = mock(HttpHost.class);
        Credentials credentials = mock(Credentials.class);
        when(context.getCredentialsProvider()).thenReturn(credentialsProvider);
        when(context.getTargetAuthState()).thenReturn(authState);
        when(context.getCredentialsProvider()).thenReturn(credentialsProvider);


        when(context.getTargetHost()).thenReturn(httpHost);
        when(credentialsProvider.getCredentials(any())).thenReturn(credentials);
        createRepo(repoKey, repositoryService);
        PreemptiveBearerTokenAuthInterceptor preemptiveBearerTokenAuthInterceptor =
                new PreemptiveBearerTokenAuthInterceptor(new ArtifactoryBearerScheme(repoKey));

        //assertions before process method
        assertNull(context.getTargetAuthState().getCredentials());
        assertNull(context.getTargetAuthState().getAuthScheme());

        preemptiveBearerTokenAuthInterceptor.process(request, context);

        //assertions after process methods
        assertNotNull(context.getTargetAuthState().getCredentials());
        assertNotNull(context.getTargetAuthState().getAuthScheme());
    }

    @SneakyThrows
    public void injectPreemptiveBearerTokenWhenThereIsNoCredentialsTest() {
        String repoKey = "remoteRepo";
        RepositoryService repositoryService = Mockito.mock(RepositoryService.class);
        when(ContextHelper.get().beanForType(RepositoryService.class))
                .thenReturn(repositoryService);
        HttpRequest request = mock(HttpRequest.class);
        when(request.getRequestLine())
                .thenReturn(new BasicRequestLine("GET", "foo/bar", new ProtocolVersion("HTTP", 1, 1)));
        AuthState authState = new AuthState();
        HttpClientContext context = mock(HttpClientContext.class);
        CredentialsProvider credentialsProvider = mock(CredentialsProvider.class);

        HttpHost httpHost = mock(HttpHost.class);
        when(context.getCredentialsProvider()).thenReturn(credentialsProvider);
        when(context.getTargetAuthState()).thenReturn(authState);
        when(context.getCredentialsProvider()).thenReturn(credentialsProvider);


        when(context.getTargetHost()).thenReturn(httpHost);
        // No Credentials
        when(credentialsProvider.getCredentials(any())).thenReturn(null);
        createRepo(repoKey, repositoryService);
        PreemptiveBearerTokenAuthInterceptor preemptiveBearerTokenAuthInterceptor =
                new PreemptiveBearerTokenAuthInterceptor(new ArtifactoryBearerScheme(repoKey));

        //assertions before process method
        assertNull(context.getTargetAuthState().getCredentials());
        assertNull(context.getTargetAuthState().getAuthScheme());

        preemptiveBearerTokenAuthInterceptor.process(request, context);

        //assertions after process methods
        assertNull(context.getTargetAuthState().getCredentials());
        assertNull(context.getTargetAuthState().getAuthScheme());
    }

    void createRepo(String repoKey, RepositoryService repositoryService) {
        RepoDescriptor repoDescriptor = mock(RepoDescriptor.class);
        when(repositoryService.repoDescriptorByKey(repoKey)).thenReturn(repoDescriptor);
        when(repoDescriptor.getType()).thenReturn(RepoType.NimModel);
    }

}
