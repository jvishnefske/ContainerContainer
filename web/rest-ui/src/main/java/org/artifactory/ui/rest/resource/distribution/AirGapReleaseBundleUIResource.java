package org.artifactory.ui.rest.resource.distribution;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.ha.HaCommonAddon;
import org.artifactory.addon.release.bundle.ReleaseBundleAddon;
import org.artifactory.addon.release.bundle.airgap.AirGapReleaseBundleException;
import org.artifactory.addon.release.bundle.airgap.AirGapReleaseBundleValidationException;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.rest.constant.HaRestConstants;
import org.artifactory.api.rest.constant.SystemRestConstants;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.rest.common.resource.BaseResource;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.ui.rest.model.admin.services.filesystem.FileSystemBrowser;
import org.artifactory.ui.rest.model.admin.services.filesystem.FileSystemItem;
import org.artifactory.ui.rest.service.admin.services.filesystem.BrowseFileSystemService;
import org.artifactory.ui.utils.MultiPartUtils;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.jfrog.common.FileUtils;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.File;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.artifactory.ui.rest.resource.distribution.AirGapReleaseBundleUIResource.PATH_ROOT;

/**
 * @author Yevdo Abramov
 * Created on 23/08/2020
 */
@Path(PATH_ROOT)
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@Slf4j
public class AirGapReleaseBundleUIResource extends BaseResource {
    public static final String PATH_ROOT = SystemRestConstants.JFROG_RELEASE;
    private final BrowseFileSystemService browseFileSystemService;
    private final CentralConfigService centralConfigService;
    private final AddonsManager addonsManager;

    @Inject
    public AirGapReleaseBundleUIResource(@NonNull AddonsManager addonsManager,
            @NonNull BrowseFileSystemService browseFileSystemService,
            @NonNull CentralConfigService centralConfigService) {
        this.addonsManager = addonsManager;
        this.browseFileSystemService = browseFileSystemService;
        this.centralConfigService = centralConfigService;
    }

    @Path("/import")
    @RolesAllowed(AuthorizationService.ROLE_ADMIN)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @POST
    public Response importReleaseBundleFromStream(@QueryParam("dry_run") boolean dryRun,
            @FormDataParam("file") InputStream inputStream) throws AirGapReleaseBundleException {
        ReleaseBundleAddon releaseBundleAddon = addonsManager.addonByType(ReleaseBundleAddon.class);
        long exportFileSizeInMb =
                FileUtils.bytesToMB(getArtifactoryRestRequest().getServletRequest().getContentLengthLong());
        MultiPartUtils.validateFileSizeLimit(centralConfigService, exportFileSizeInMb);

        try {
            releaseBundleAddon.airGapReleaseBundleImport(inputStream, dryRun);
            return Response.accepted().build();
        } catch (AirGapReleaseBundleValidationException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    @Path("internal/import/browse")
    @RolesAllowed({HaRestConstants.ROLE_HA})
    @Produces(MediaType.APPLICATION_JSON)
    @GET
    public Response propagatedBrowseImportDirectory() {
        return browseImportDirectoryInner(false);
    }

    @Path("import/browse")
    @RolesAllowed({AuthorizationService.ROLE_ADMIN})
    @Produces(MediaType.APPLICATION_JSON)
    @GET
    public Response browseImportDirectory() {
        return browseImportDirectoryInner(true);
    }

    @NonNull
    private Response browseImportDirectoryInner(boolean propagate) {
        File baseImportDir = ContextHelper.get().getArtifactoryHome().getImportDir();

        FileSystemBrowser browser = browseFileSystemService.getFileOrFoldersList(baseImportDir.getPath(), true);
        Set<FileSystemItem> allFiles = new HashSet<>();

        if (propagate) {
            List<FileSystemBrowser> browsersFromOtherServers = addonsManager.addonByType(HaCommonAddon.class)
                    .propagateBrowseImportDirectory(FileSystemBrowser.class);

            browsersFromOtherServers
                    .forEach(serverResponse -> allFiles.addAll(serverResponse.getFileSystemItems()));
        }

        allFiles.addAll(browser.getFileSystemItems());

        List<FileSystemItem> sorted = allFiles.stream()
                .sorted(FileSystemItem::compareByTypeAndName)
                .collect(Collectors.toList());

        browser.setFileSystemItems(sorted);
        return Response.ok(browser).build();
    }
}
