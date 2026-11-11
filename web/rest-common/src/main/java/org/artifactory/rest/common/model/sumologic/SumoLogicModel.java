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

package org.artifactory.rest.common.model.sumologic;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.artifactory.common.ConstantValues;
import org.artifactory.rest.common.model.BaseModel;
import org.artifactory.rest.common.util.JsonUtil;

/**
 * @author Shay Yaakov
 */
@Data
@EqualsAndHashCode(callSuper=false)
@AllArgsConstructor
public class SumoLogicModel extends BaseModel {

    private Boolean enabled;
    private String proxy;
    private String clientId;
    private String secret;
    private String email;
    private String redirectUrl;
    private String sumoBaseUrl;
    private String dashboardUrl;
    private String collectorUrl;
    private int licenseType;

    public SumoLogicModel() {
        this.sumoBaseUrl = ConstantValues.sumoLogicApiUrl.getString();
    }

    @Override
    public String toString() {
        return JsonUtil.jsonToString(this);
    }
}
