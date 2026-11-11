package org.artifactory.ph;

import com.jfrog.ph.acl.exception.PackageException;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.security.AuthenticationHelper;
import org.artifactory.security.signature.SignedUrlAuthenticationToken;
import org.artifactory.signature.SignedUrlService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.storage.binstore.exceptions.SignedUrlException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Volodymyr Shulga
 */
class PackageSignedUrlServiceImplTest extends ArtifactoryHomeBoundJUnitTest {
    private static final String SERVER_URL = "serverUrl";
    private static final String PATH = "path";
    private static final String INTERNAL_REPO_KEY = "internalRepoKey";
    private static final long VALID_FOR_SECONDS = 1L;
    private static final boolean UI_REQUEST = true;

    @Mock
    private SignedUrlService signedUrlService;
    private AutoCloseable autoCloseable;
    @InjectMocks
    private PackageSignedUrlServiceImpl packageSignedUrlServiceImpl;
    @Mock
    private ArtifactoryContext artifactoryContext;
    private MockedStatic<SecurityContextHolder> securityContextHolderMockedStatic;
    @Mock
    private SecurityContext securityContext;

    @BeforeEach
    void setUp() {
        autoCloseable = org.mockito.MockitoAnnotations.openMocks(this);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        securityContextHolderMockedStatic = mockStatic(SecurityContextHolder.class);
        securityContextHolderMockedStatic.when(SecurityContextHolder::getContext).thenReturn(securityContext);
    }

    @AfterEach
    void tearDown() throws Exception {
        autoCloseable.close();
        securityContextHolderMockedStatic.close();
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void createSignedUrlWithInternalRepoKey() {
        Assertions.assertDoesNotThrow(
                () -> packageSignedUrlServiceImpl.createSignedUrl(SERVER_URL, PATH, INTERNAL_REPO_KEY,
                        VALID_FOR_SECONDS, UI_REQUEST));
        verify(signedUrlService, Mockito.times(1))
                .createSignedUrl(SERVER_URL, PATH, INTERNAL_REPO_KEY, VALID_FOR_SECONDS, UI_REQUEST);
        verify(signedUrlService, Mockito.never())
                .createSignedUrl(SERVER_URL, PATH, VALID_FOR_SECONDS, UI_REQUEST);
    }

    @Test
    void createSignedUrlWithoutInternalRepoKey() {
        String internalRepoKey = null;
        Assertions.assertDoesNotThrow(
                () -> packageSignedUrlServiceImpl.createSignedUrl(SERVER_URL, PATH, internalRepoKey, VALID_FOR_SECONDS,
                        UI_REQUEST));
        verify(signedUrlService, Mockito.times(1)).createSignedUrl(SERVER_URL, PATH, VALID_FOR_SECONDS, UI_REQUEST);
        verify(signedUrlService, Mockito.never())
                .createSignedUrl(SERVER_URL, PATH, internalRepoKey, VALID_FOR_SECONDS, UI_REQUEST);
    }

    @Test
    void createSignedUrlWithBlankInternalRepoKey() {
        String internalRepoKey = "  ";
        Assertions.assertDoesNotThrow(
                () -> packageSignedUrlServiceImpl.createSignedUrl(SERVER_URL, PATH, internalRepoKey, VALID_FOR_SECONDS,
                        UI_REQUEST));
        verify(signedUrlService, Mockito.times(1)).createSignedUrl(SERVER_URL, PATH, VALID_FOR_SECONDS, UI_REQUEST);
        verify(signedUrlService, Mockito.never())
                .createSignedUrl(SERVER_URL, PATH, internalRepoKey, VALID_FOR_SECONDS, UI_REQUEST);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"internalRepoKey"})
    void createSignedUrl(String internalRepoKey) {
        Assertions.assertDoesNotThrow(
                () -> packageSignedUrlServiceImpl.createSignedUrl(SERVER_URL, PATH, internalRepoKey,
                        VALID_FOR_SECONDS));
        verify(signedUrlService, Mockito.times(1)).createSignedUrl(SERVER_URL, PATH, internalRepoKey, VALID_FOR_SECONDS,
                false);
    }

    @Test
    void checkExceptionHandledProperly() {
        Mockito.when(signedUrlService.createSignedUrl(SERVER_URL, PATH, INTERNAL_REPO_KEY, VALID_FOR_SECONDS,
                UI_REQUEST)).thenThrow(new SignedUrlException("test exception"));
        Assertions.assertThrows(PackageException.class,
                () -> packageSignedUrlServiceImpl.createSignedUrl(SERVER_URL, PATH, INTERNAL_REPO_KEY,
                        VALID_FOR_SECONDS, UI_REQUEST));
    }

    @Test
    void getInternalRepoFromSignedUrlAuthenticationTest() {
        String repoKey = "remote-repo";
        SignedUrlAuthenticationToken signedUrlAuthentication = Mockito.mock(SignedUrlAuthenticationToken.class);
        when(signedUrlAuthentication.getInternalRepo()).thenReturn(repoKey);
        when(AuthenticationHelper.getAuthentication()).thenReturn(signedUrlAuthentication);
        Assertions.assertEquals(repoKey, packageSignedUrlServiceImpl.getInternalRepoFromSignedUrlAuthentication());
    }

    @Test
    void getInternalRepoFromSignedUrlAuthenticationNullTest() {
        Assertions.assertNull(packageSignedUrlServiceImpl.getInternalRepoFromSignedUrlAuthentication());
    }

}