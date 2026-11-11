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

package org.artifactory.api.rest.replication;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;
import org.artifactory.api.rest.restmodel.IModel;

import java.io.Serializable;

/**
 * @author mamo
 */
@Data
public class ReplicationConfigRequest implements Serializable, IModel {

    //local/remote
    private Boolean enabled;
    private String cronExp;
    private Boolean syncDeletes;
    private Boolean syncProperties;
    @JsonAlias("pathPrefix")
    private String includePathPrefixPattern;
    private String excludePathPrefixPattern;
    private Boolean checkBinaryExistenceInFilestore;

    //local only
    private String url;
    private Boolean enableEventReplication;
    private String username;
    private String password;
    private String proxy;
    private Boolean disableProxy;
    private Integer socketTimeoutMillis;
    private Boolean syncStatistics;
}
