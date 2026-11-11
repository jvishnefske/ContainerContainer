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

package org.artifactory.ui.rest.resource.admin.security.signingkeys;


import org.artifactory.api.security.AuthorizationService;
import org.artifactory.rest.common.resource.BaseResource;
import org.artifactory.ui.rest.model.admin.security.signingkey.SignKey;
import org.artifactory.ui.rest.model.utils.FileUpload;
import org.artifactory.ui.rest.service.admin.security.SecurityServiceFactory;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * This Resource stays as backward compatible
 * @author Chen Keinan
 * @author Omri Ziv
 */
@RolesAllowed({AuthorizationService.ROLE_ADMIN})
@Component
@Path("signingkeys")
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class SigningKeysResource extends BaseResource {

    private final SecurityServiceFactory securityFactory;

    @Autowired
    public SigningKeysResource(SecurityServiceFactory securityFactory) {
        this.securityFactory = securityFactory;
    }

    @POST
    @Path("install")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response uploadSigningKey(FormDataMultiPart formParams) {
        FileUpload fileUpload = new FileUpload(formParams);
        return runService(securityFactory.uploadSigningKey(), fileUpload);
    }

    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    public Response removeSigningKey() {
        return runService(securityFactory.removeSigningKeyService());
    }


    @POST
    @Path("verify")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response verifySigningKey(SignKey signKey) {
        return runService(securityFactory.verifySigningKey(), signKey);
    }

    @PUT
    @Path("update")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updatePassPhrase(SignKey signKey) {
        return runService(securityFactory.updateSigningKey(), signKey);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getSigningKey() {
        return runService(securityFactory.getSigningKey());
    }
}
