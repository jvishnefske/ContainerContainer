/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.ui.rest.resource.artifacts.browse.treebrowser.actions;

import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.rest.common.model.artifact.BaseArtifact;
import org.artifactory.rest.common.model.migrate.MigrateRepo;
import org.artifactory.rest.common.model.restore.RestoreLayoutRepo;
import org.artifactory.rest.common.model.trash.RestoreArtifact;
import org.artifactory.rest.common.resource.BaseResource;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.action.CopyMoveArtifact;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.action.DeleteArtifact;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.action.DownloadArtifact;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.action.ViewArtifact;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.action.WatchArtifact;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.action.ZapArtifact;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.action.recalculateindex.BaseIndexCalculator;
import org.artifactory.ui.rest.service.artifacts.browse.BrowseServiceFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * @author Chen Keinan
 */
@Path("artifactactions")
@RolesAllowed({AuthorizationService.ROLE_ADMIN, AuthorizationService.ROLE_USER})
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class ArtifactActionsResource extends BaseResource {

    @Autowired
    private BrowseServiceFactory browseFactory;

    @Autowired
    @Qualifier("streamingRestResponse")
    public void setArtifactoryResponse(RestResponse artifactoryResponse) {
        this.artifactoryResponse = artifactoryResponse;
    }

    @POST
    @Path("copy")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response copyArtifact(CopyMoveArtifact copyArtifact) {
        return runService(browseFactory.copyArtifactService(), copyArtifact);
    }

    @POST
    @Path("move")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response moveArtifact(CopyMoveArtifact moveArtifact) {
        return runService(browseFactory.moveArtifactService(), moveArtifact);
    }

    @POST
    @Path("download")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response downloadArtifact(DownloadArtifact downloadArtifact) throws Exception {
        return runService(browseFactory.downloadArtifactService(), downloadArtifact);
    }

    @PUT
    @Path("downloadWithSignedUrl")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response downloadArtifactWithSignedUrl(DownloadArtifact downloadArtifact) {
        return runService(browseFactory.downloadArtifactWithSignedUrlService(), downloadArtifact);
    }

    @GET
    @Path("downloadfolderinfo")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getFolderDownloadInfo() {
        return runService(browseFactory.getDownloadFolderInfo());
    }

    @POST
    @Path("emptytrash")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response emptyTrash() {
        return runServiceAndWait(
                browseFactory.emptyTrashService(),
                "Empty trash operation in progress"
        );
    }

    @POST
    @Path("restore")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response restoreFromTrash(RestoreArtifact artifact)  {
        return runService(browseFactory.restoreArtifactService(), artifact);
    }

    @GET
    @Path("downloadfolder")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response downloadFolder() {
        return runService(browseFactory.downloadFolder());
    }

    @POST
    @Path("watch")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response watchArtifact(WatchArtifact watchArtifact) {
        return runService(browseFactory.watchArtifactService(), watchArtifact);
    }

    @POST
    @Path("delete")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response deleteArtifact(DeleteArtifact deleteArtifact) {
        return runService(browseFactory.deleteArtifactService(), deleteArtifact);
    }

    @POST
    @Path("view")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response viewArtifact(ViewArtifact viewArtifact) {
        return runService(browseFactory.viewArtifactService(), viewArtifact);
    }

    @POST
    @Path("zap")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response zapArtifact(ZapArtifact zapArtifact) {
        return runService(browseFactory.zapArtifactService(), zapArtifact);
    }

    @POST
    @Path("calculateIndex")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response calculateIndex(BaseIndexCalculator baseIndexCalculator) throws Exception {
        return runService(browseFactory.recalculateIndex(), baseIndexCalculator);
    }

    @POST
    @Path("calculateDebianCoordinates")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response calculateDebianCoordinates(BaseArtifact targetRepo) {
        return runService(browseFactory.calculateDebianCoordinates(), targetRepo);
    }

    @POST
    @Path("zapVirtual")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response zapVirtual(ZapArtifact zapArtifact) {
        return runService(browseFactory.zapCachesVirtual(), zapArtifact);
    }

    @POST
    @Path("addSha256")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response addSha256(BaseArtifact baseArtifact) {
        return runService(browseFactory.addSha256ToArtifact(), baseArtifact);
    }

    @POST
    @Path("allowDownload")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response allowDownload(BaseArtifact baseArtifact) {
        return runService(browseFactory.setAllowDownload(), baseArtifact);
    }

    @POST
    @Path("migrate")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response migrate(MigrateRepo migrateRepo) {
        return runService(browseFactory.migrateRepositoryService(), migrateRepo);
    }

    @POST
    @Path("restore_layout")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response restore(RestoreLayoutRepo restoreLayoutRepo) {
        return runService(browseFactory.restoreLayoutService(), restoreLayoutRepo);
    }

    @VisibleForTesting
    void setBrowseFactory(BrowseServiceFactory browseFactory) {
        this.browseFactory = browseFactory;
    }

}
