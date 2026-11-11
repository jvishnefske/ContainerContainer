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

package com.jfrog.build.model.api;

import com.fasterxml.jackson.annotation.JsonAlias;

import jakarta.xml.bind.annotation.XmlElement;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Eli Givoni
 */
public class BuildsByName implements Serializable {

    @JsonAlias("slf")
    @XmlElement(name = "uri")
    public String slf;
    public List<Build> buildsNumbers = new ArrayList<>();

    public static class Build {
        public String uri;
        public String started;

        public Build(String uri, String started) {
            this.started = started;
            this.uri = uri;
        }

        private Build() {
        }
    }
}
