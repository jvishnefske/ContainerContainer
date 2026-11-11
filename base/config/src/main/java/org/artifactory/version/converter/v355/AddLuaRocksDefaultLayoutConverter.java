/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2026 JFrog Ltd.
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

package org.artifactory.version.converter.v355;

import org.artifactory.version.converter.BasicAddDefaultLayoutConverter;
import org.artifactory.version.converter.RepoLayoutTypeMigrationHelper;
import org.jdom2.Document;
import org.jdom2.Element;

import static org.artifactory.util.RepoLayoutUtils.LUAROCKS_DEFAULT_NAME;

/**
 * Adds the dedicated {@code luarocks-default} repository layout and migrates existing
 * LuaRocks repositories away from {@code simple-default} so that they use the new layout
 * that matches the actual LuaRocks storage convention: {@code package-name/package-name-version-revision.ext}.
 *
 * @author Muskan Gautam
 */
public class AddLuaRocksDefaultLayoutConverter extends BasicAddDefaultLayoutConverter {

    private static final String LUAROCKS_TYPE = "luarocks";

    public AddLuaRocksDefaultLayoutConverter() {
        super(LUAROCKS_DEFAULT_NAME,
                "[module]/[module]-[baseRev](-[fileItegRev]).[ext]",
                "false", null, ".*", ".*");
    }

    @Override
    public void convert(Document doc) {
        super.convert(doc);
        Element rootElement = doc.getRootElement();
        RepoLayoutTypeMigrationHelper.migrateExistingRepos(rootElement, rootElement.getNamespace(), LUAROCKS_TYPE,
                LUAROCKS_DEFAULT_NAME);
    }
}
