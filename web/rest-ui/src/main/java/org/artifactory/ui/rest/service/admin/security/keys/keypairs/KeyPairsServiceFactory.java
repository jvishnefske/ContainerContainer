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

package org.artifactory.ui.rest.service.admin.security.keys.keypairs;

import org.springframework.beans.factory.annotation.Lookup;

/**
 * @author Yuval Reches
 */
public abstract class KeyPairsServiceFactory {

    @Lookup
    public abstract GetAllKeyPairsService getAllKeyPairsService();

    @Lookup
    public abstract DeleteKeyPairsService deleteKeyPairsService();

    @Lookup
    public abstract GetKeyPairService getKeyPairService();

    @Lookup
    public abstract CreateKeyPairService createKeyPairService();

    @Lookup
    public abstract VerifyKeyPairService verifyKeyPairService();

    @Lookup
    public abstract GetPublicKeyService getPublicKey();

    @Lookup
    public abstract UpdatePublicKeyService updateKeyPairService();

}