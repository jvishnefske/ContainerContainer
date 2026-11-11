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

package org.artifactory.storage.db.fs.util;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.storage.db.fs.entity.Node;
import org.jfrog.client.util.PathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * @author Yossi Shaul
 */
public abstract class NodeUtils {
    private static final Logger log = LoggerFactory.getLogger(NodeUtils.class);

    /**
     * Returns the number of path elements of this path.
     * Empty path returns 0, root path returns 1.
     *
     * @param path The path to check
     * @return Number of path elements in this path
     */
    public static int getDepth(String path) {
        if (StringUtils.isBlank(path)) {
            return 0;
        } else {
            return StringUtils.countMatches(PathUtils.trimSlashes(path), "/") + 1;
        }
    }

    public static void print(Node node) {
        print(node, 0);
    }

    private static void print(Node node, int depth) {
        String info = indent(depth, node) + nodeToString(node);
        log.info(info);
    }

    public static String nodeToString(Node node) {
        return node.getName() + "[" + node.getPath() + "]";
    }

    private static String indent(int amount, Node node) {
        StringBuilder sb = new StringBuilder();
        sb.append("|  ".repeat(Math.max(0, amount)));
        if (!node.isFile()) {
            sb.append("+-");
        } else {
            sb.append("\\-");
        }
        return sb.toString();
    }
}
