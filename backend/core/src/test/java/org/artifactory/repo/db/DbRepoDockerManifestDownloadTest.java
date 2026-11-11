package org.artifactory.repo.db;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.xray.XrayAddon;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.status.StatusHolder;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.snapshot.MavenSnapshotVersionAdapter;
import org.artifactory.request.Request;
import org.artifactory.request.RequestContext;
import org.artifactory.spring.InternalArtifactoryContext;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

public class DbRepoDockerManifestDownloadTest {

    private AddonsManager addonsManager;

    @Test
    public void downloadUnscannedManifestAllowedOnlyForDockerRepoWhenHeaderSetToTrue() {
        Arrays.stream(RepoType.values()).forEach(repoType -> verifyDownloadAllowed(
                repoType,
                true,
                false,
                "X-JFrog-Bypass-Unscanned-Blocking",
                "true",
                "manifest.json",
                repoType.isDockerGroup()));
    }

    @Test
    public void downloadWhenHeaderSetToFalse() {
        Arrays.stream(RepoType.values()).forEach(repoType -> verifyDownloadAllowed(
                repoType,
                true,
                false,
                "X-JFrog-Bypass-Unscanned-Blocking",
                "false",
                "manifest.json",
                false));

        Arrays.stream(RepoType.values()).forEach(repoType -> verifyDownloadAllowed(
                repoType,
                false,
                false,
                "X-JFrog-Bypass-Unscanned-Blocking",
                "false",
                "manifest.json",
                true));

    }

    @Test
    public void downloadUnscannedManifestWithWrongHeader() {
        Arrays.stream(RepoType.values()).forEach(repoType -> verifyDownloadAllowed(
                repoType,
                true,
                false,
                "X-JFrog-Bypass-Download-Wrong",
                "false",
                "manifest.json",
                false));

        Arrays.stream(RepoType.values()).forEach(repoType -> verifyDownloadAllowed(
                repoType,
                false,
                false,
                "X-JFrog-Bypass-Download-Wrong",
                "false",
                "manifest.json",
                true
        ));

    }

    @Test
    public void downloadUnscannedFileWhenHeaderSetToTrue() {
        Arrays.stream(RepoType.values()).forEach(repoType -> verifyDownloadAllowed(
                repoType,
                true,
                false,
                "X-JFrog-Bypass-Unscanned-Blocking",
                "true",
                "other.file",
                false));


        Arrays.stream(RepoType.values()).forEach(repoType -> verifyDownloadAllowed(
                repoType,
                false,
                false,
                "X-JFrog-Bypass-Unscanned-Blocking",
                "true",
                "other.file",
                true));

    }

    @Test
    public void downloadManifestWhenRequestIsNotSet() {
        Arrays.stream(RepoType.values()).forEach(repoType -> verifyDownloadAllowed(
                repoType,
                true,
                true,
                "",
                "",
                "manifest.json",
                false));


        Arrays.stream(RepoType.values()).forEach(repoType -> verifyDownloadAllowed(
                repoType,
                false,
                true,
                "",
                "",
                "manifest.json",
                true));

    }

    public void verifyDownloadAllowed(
            RepoType repoType,
            boolean downloadBlockedByXRay,
            boolean requestIsNotSet,
            String headerName,
            String headerValue,
            String fileName,
            boolean downloadAllowed
    ) {
        setup();

        XrayAddon xrayAddon = mock(XrayAddon.class);
        when(addonsManager.addonByType(XrayAddon.class)).thenReturn(xrayAddon);
        when(xrayAddon.isDownloadBlocked(any(RepoPath.class), eq(true))).thenReturn(false);
        when(xrayAddon.isDownloadBlocked(any(RepoPath.class), eq(false))).thenReturn(downloadBlockedByXRay);
        when(xrayAddon.isXrayAliveByHeartbeat()).thenReturn(true);
        RequestContext requestContext = mock(RequestContext.class);
        if (requestIsNotSet) {
            when(requestContext.getRequest()).thenReturn(null);
        } else {
            Request request = mock(Request.class);
            Enumeration<String> enumeration = mock(Enumeration.class);
            if (headerName.isEmpty()) {
                when(enumeration.hasMoreElements()).thenReturn(false);
            } else {
                when(enumeration.hasMoreElements()).thenReturn(true);
                when(enumeration.nextElement()).thenReturn(headerValue);
                when(request.getHeaderValues(headerName)).thenReturn(enumeration);
                when(requestContext.getRequest()).thenReturn(request);
            }
        }
        RepoPath path = InternalRepoPathFactory.create("target", fileName);
        DbLocalRepo<LocalRepoDescriptor, LocalRepoConfig> repo = createDbRepo(repoType);
        StatusHolder status = repo.checkDownloadIsNotBlocked(requestContext, path);

        assertEquals(!status.isError(), downloadAllowed);

        cleanUp();
    }

    private void setup() {
        ArtifactoryContext context = mock(InternalArtifactoryContext.class);
        AuthorizationService authService = mock(AuthorizationService.class);
        addonsManager = mock(AddonsManager.class);
        when(context.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        when(context.getAuthorizationService()).thenReturn(authService);
        ArtifactoryContextThreadBinder.bind(context);
        ArtifactoryHome home = mock(ArtifactoryHome.class);
        ArtifactorySystemProperties properties = mock(ArtifactorySystemProperties.class);
        when(properties.getListProperty(ConstantValues.caseInsensitivePackages))
                .thenReturn(new ArrayList<>(List.of("nuget")));
        when(home.getArtifactoryProperties()).thenReturn(properties);
        ArtifactoryHome.bind(home);
    }

    private void cleanUp() {
        ArtifactoryContextThreadBinder.unbind();
        ArtifactoryHome.unbind();
    }

    private DbLocalRepo<LocalRepoDescriptor, LocalRepoConfig> createDbRepo(RepoType repoType) {
        LocalRepoConfig repoConfig = LocalRepoConfig.create("repoKey", repoType);
        LocalRepoDescriptor repoDescriptor = new LocalRepoDescriptor(repoConfig, List.of(), List.of(), List.of());
        return new DbLocalRepo<>(repoDescriptor, repoConfig, null, null, null) {

            @Override
            public MavenSnapshotVersionAdapter getMavenSnapshotVersionAdapter() {
                return null;
            }
        };
    }

}
