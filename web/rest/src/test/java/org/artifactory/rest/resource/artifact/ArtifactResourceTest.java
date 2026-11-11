package org.artifactory.rest.resource.artifact;

import lombok.Cleanup;
import lombok.SneakyThrows;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.rest.RestAddon;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.repo.exception.BlackedOutException;
import org.artifactory.api.repo.exception.FolderExpectedException;
import org.artifactory.api.rest.artifact.ItemStatsInfo;
import org.artifactory.api.security.SecurityService;
import org.artifactory.fs.StatsInfo;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.Repo;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.rest.exception.MissingRestAddonException;
import org.artifactory.exception.http.NotFoundException;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.test.ArtifactoryHomeStub;
import org.artifactory.util.DoesNotExistException;
import org.artifactory.util.RepoDoesNotExistException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.stream.Stream;

import static org.artifactory.api.context.ArtifactoryContextThreadBinder.bind;
import static org.artifactory.api.context.ArtifactoryContextThreadBinder.unbind;
import static org.artifactory.common.ConstantValues.securityPersonalInfoNonAdminExposure;
import static org.artifactory.utils.ObfuscationUtils.UNKNOWN_STRING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Andrey Tashlitsky
 */
@ExtendWith({MockitoExtension.class})
class ArtifactResourceTest extends ArtifactoryHomeBoundJUnitTest {

    private static final String COOL_EMAIL = "cool@cool.com";
    private final ArtifactoryHomeStub homeStub = getOrCreateArtifactoryHomeStub();

    @Mock
    AddonsManager addonsManager;
    @Mock
    private ArtifactoryContext artifactoryContext;
    @Mock
    private SecurityService securityService;
    @Mock
    private StatsInfo statsInfo;
    @Mock
    private HttpServletResponse mockResponse;

    private static Repo repo;

    @Spy
    @InjectMocks
    private ArtifactResource artifactResource;

    @BeforeAll
    public static void init() {
        repo = mock(Repo.class);
        lenient().when(repo.getRepoConfig()).thenReturn(mock(RepoConfig.class));
        lenient().when(repo.getRepoPath(any())).thenReturn(RepoPathFactory.create("cool"));

    }

    @BeforeEach
    public void beforeEach() {
        lenient().doReturn(securityService).when(artifactoryContext).beanForType(SecurityService.class);
        bind(artifactoryContext);
    }

    @AfterEach
    public void afterEach() {
        homeStub.setProperty(securityPersonalInfoNonAdminExposure.getPropertyName(),
                securityPersonalInfoNonAdminExposure.getDefValue());
        unbind();
    }

    @ParameterizedTest
    @MethodSource("testHideUserRelatedInfoIfNeededDataProvider")
    void testHideUserRelatedInfoIfNeeded(boolean enabled, boolean admin, String data, String expected) {
        homeStub.setProperty(securityPersonalInfoNonAdminExposure.getPropertyName(), String.valueOf(enabled));
        if(admin) {
            lenient().doReturn(true).when(securityService).isAdmin();
        }
        doReturn(data).when(statsInfo).getLastDownloadedBy();
        doReturn(data).when(statsInfo).getRemoteLastDownloadedBy();
        ItemStatsInfo entity = new ItemStatsInfo("cool-uri", statsInfo);
        artifactResource.hideUserRelatedInfoIfNeeded(entity);
        assertEquals(expected, entity.getLastDownloadedBy());
        assertEquals(expected, entity.getRemoteLastDownloadedBy());
    }

    private static Stream<Arguments> testHideUserRelatedInfoIfNeededDataProvider() {
        return Stream.of(
                Arguments.of(true, true, COOL_EMAIL, COOL_EMAIL),
                Arguments.of(true, true, null, null),
                Arguments.of(true, false, COOL_EMAIL, COOL_EMAIL),
                Arguments.of(true, false, null, null),
                Arguments.of(false, true, COOL_EMAIL, COOL_EMAIL),
                Arguments.of(false, true, null, null),
                Arguments.of(false, false, COOL_EMAIL, UNKNOWN_STRING),
                Arguments.of(false, false, null, null)
        );
    }

    private static Stream<Arguments> writeStreamingFileListExpectedExceptions() {
        return Stream.of(
                Arguments.of(BadRequestException.class, new IllegalArgumentException()),
                Arguments.of(NotFoundException.class, new RepoDoesNotExistException("")),
                Arguments.of(NotFoundException.class, new DoesNotExistException("")),
                Arguments.of(NotFoundException.class,
                        new BlackedOutException(repo.getRepoConfig(), repo.getRepoPath("a path") )),
                Arguments.of(BadRequestException.class, new FolderExpectedException(RepoPathFactory.create("cool"))),
                Arguments.of(MissingRestAddonException.class, new MissingRestAddonException())
        );
    }

    @ParameterizedTest
    @SneakyThrows({IOException.class, BlackedOutException.class})
    @MethodSource("writeStreamingFileListExpectedExceptions")
    void negativeTestWriteStreamingFileList(Class<? extends Exception> expectedType, Exception internalExceptionType) {
        RestAddon restAddon = mock(RestAddon.class);
        when(addonsManager.addonByType(RestAddon.class)).thenReturn(restAddon);
        doReturn("path").when(artifactResource).getPathParam();
        doReturn("/storage").when(artifactResource).getRequestStorageUri();
        doReturn(mock( HttpServletResponse.class)).when(artifactResource).getResponse();
        doReturn(1).when(artifactResource).getQueryParameterAsInt(any());
        doThrow(internalExceptionType)
                .when(restAddon)
                .writeStreamingFileList(any(HttpServletResponse.class),anyString(),anyString(),
                        anyInt(), anyInt(), anyInt(),
                        anyInt(), anyInt(),anyInt(), anyInt());

        assertThrows(expectedType, ()-> {
                    @Cleanup var ignored = artifactResource.writeStreamingFileList();
                }
        );
    }

    @Test
    void testGetPathParam() {
        String expectedPath = "/test/path";
        artifactResource.path = expectedPath;

        String actualPath = artifactResource.getPathParam();

        assertEquals(expectedPath, actualPath);
    }

    @Test
    void testGetResponse() {
        when(mockResponse.getStatus()).thenReturn(200);

        HttpServletResponse actualResponse = artifactResource.getResponse();

        assertEquals(mockResponse.getStatus(), actualResponse.getStatus());
    }

}
