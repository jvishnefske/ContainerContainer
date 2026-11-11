/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2019 JFrog Ltd.
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

package org.artifactory.mapper;

import org.jfrog.common.mapper.Validatable;
import org.joda.time.format.ISODateTimeFormat;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;

/**
 * @author Uriah Levy
 */
@Mapper(componentModel = "spring")
@Named("ArtifactoryBaseMapper")
public interface ArtifactoryBaseMapper {
    @AfterMapping // TODO doesn't work
    default void validate(@MappingTarget Validatable object) {
        object.validate();
    }


    @Named("EpochToIso8601")
    default String epochToIso8601(long epoch) {
        return ISODateTimeFormat.dateTime().print(epoch);
    }
}
