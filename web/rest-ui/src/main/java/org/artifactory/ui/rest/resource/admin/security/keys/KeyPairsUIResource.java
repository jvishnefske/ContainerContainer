/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2020 JFrog Ltd.
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

package org.artifactory.ui.rest.resource.admin.security.keys;

import org.artifactory.api.rest.keys.KeyPairModel;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.rest.common.resource.BaseResource;
import org.artifactory.ui.rest.model.admin.security.keys.KeyPairIModel;
import org.artifactory.ui.rest.service.admin.security.keys.keypairs.KeyPairsServiceFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

/**
 * @author Yuval Reches
 */
@Path("security/keyPairs")
@Component
@RolesAllowed({AuthorizationService.ROLE_ADMIN})
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class KeyPairsUIResource extends BaseResource {

    private final KeyPairsServiceFactory keyPairsServiceFactory;

    @Autowired
    public KeyPairsUIResource(KeyPairsServiceFactory keyPairsServiceFactory) {
        this.keyPairsServiceFactory = keyPairsServiceFactory;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllKeys() {
        return runService(keyPairsServiceFactory.getAllKeyPairsService());
    }

    @GET
    @Path("{keyPair: .+}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getKeyInfoById(@PathParam("keyPair") String keyPair) {
        return runService(keyPairsServiceFactory.getKeyPairService(), keyPair);
    }

    @POST
    @Path("delete")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response deleteKeysById(List<KeyPairIModel> keyPairs) {
        return runService(keyPairsServiceFactory.deleteKeyPairsService(), keyPairs);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createKey(KeyPairModel keyPairModel) {
        return runService(keyPairsServiceFactory.createKeyPairService(), keyPairModel);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("verify")
    public Response verify(KeyPairModel keyPairModel) {
        return runService(keyPairsServiceFactory.verifyKeyPairService(), keyPairModel);
    }

    @GET
    @Path("{keyPair}/public")
    @Produces(APPLICATION_JSON)
    @RolesAllowed({AuthorizationService.ROLE_USER, AuthorizationService.ROLE_ADMIN})
    public Response getPublicKey(@PathParam("keyPair") String keyPair) {
        return runService(keyPairsServiceFactory.getPublicKey(), keyPair);
    }

    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updateKey(KeyPairModel keyPairModel) {
        return runService(keyPairsServiceFactory.updateKeyPairService(), keyPairModel);
    }

}