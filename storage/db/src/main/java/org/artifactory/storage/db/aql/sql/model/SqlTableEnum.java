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

package org.artifactory.storage.db.aql.sql.model;


/**
 * @author Gidi Shabat
 */
public enum SqlTableEnum {
    nodes("n"),
    node_props("np"),
    node_meta_infos("nmi"),
    stats("s"),
    stats_remote("sr"),
    unknown("u"),
    build_modules("bm"),
    module_props("bmp"),
    build_dependencies("bd"),
    build_artifacts("ba"),
    build_props("bp"),
    build_promotions("bpr"),
    builds("b"),
    artifact_bundles("ab"),
    bundle_files("bf"),
    ;

    public final String alias;

    SqlTableEnum(String alias) {
        this.alias = alias;
    }

    public AqlFieldExtensionEnum[] getFields() {
        return AqlFieldExtensionEnum.getFieldsByTable(this);
    }
}
