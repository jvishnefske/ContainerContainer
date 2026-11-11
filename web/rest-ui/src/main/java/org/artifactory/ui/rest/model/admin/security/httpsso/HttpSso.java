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

package org.artifactory.ui.rest.model.admin.security.httpsso;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.artifactory.rest.common.model.BaseModel;
import org.artifactory.rest.common.util.JsonUtil;
import org.jfrog.common.config.validation.NotUnicode;


/**
 * @author Chen Keinan
 */
@Data
@EqualsAndHashCode(callSuper=false)
public class HttpSso extends BaseModel {

    private boolean httpSsoProxied = false;

    private boolean noAutoUserCreation = false;

    private boolean allowUserToAccessProfile = false;

    @NotUnicode
    private String remoteUserRequestVariable = "REMOTE_USER";

    private boolean syncLdapGroups = false;

    @Override
    public String toString() {
        return JsonUtil.jsonToString(this);
    }
}