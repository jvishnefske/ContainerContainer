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

package org.artifactory.api.rest.keys;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.artifactory.api.util.LongFromDateDeserializer;
import org.artifactory.api.util.LongToDateSerializer;

@Getter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class TrustedKeyResponse {

    private String kid;

    @JsonProperty("key")
    private String trustedKey;
    private String fingerprint;
    private String alias;
    private String type;

    @JsonProperty("issued_on")
    @JsonSerialize(using = LongToDateSerializer.class)
    @JsonDeserialize(using = LongFromDateDeserializer.class)
    private Long issued;

    @JsonProperty("issued_by")
    private String issuedBy;

    @JsonProperty("valid_until")
    @JsonSerialize(using = LongToDateSerializer.class)
    @JsonDeserialize(using = LongFromDateDeserializer.class)
    private Long expiry;
}
