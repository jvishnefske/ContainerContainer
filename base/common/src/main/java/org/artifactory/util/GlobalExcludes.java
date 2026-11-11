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

package org.artifactory.util;

import com.google.common.collect.Lists;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.common.ConstantValues;

import jakarta.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * An adapter from file to  Ant-based path matcher util class.
 *
 * @author Gidi Shabat
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public abstract class GlobalExcludes {

    /**
     * The global excludes that applies to all repositories. Configurable by the user with {@link
     * org.artifactory.common.ConstantValues#globalExcludes}.
     */

    public static boolean isInGlobalExcludes(File file) {
        // global excludes are used as includes to test if path is in the default excludes
        // Always behave as file to force full match.
        return PathMatcher.matches(cleanPath(file), initOrGetGlobalExcludes(true), Collections.emptyList(), false);
    }

    public static boolean matches(File file, @Nullable Collection<String> includes,
            @Nullable Collection<String> excludes) {
        return PathMatcher.matches(cleanPath(file), includes, excludes, false);
    }

    private static List<String> initOrGetGlobalExcludes(boolean isIncludeAsExclude) {
        try {
            if (!isIncludeAsExclude && ConstantValues.useEmptyListForDefaultGlobalExcludes.getBoolean()) {
                return new ArrayList<>();
            } else {
                if (StringUtils.isBlank(ConstantValues.globalExcludes.getString())) {
                    return new ArrayList<>();
                }
                return Arrays.asList(ConstantValues.globalExcludes.getString().split(","));
            }
        } catch (Exception e) {
            log.error("Failed to parse global default excludes. Using empty list : {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private static String cleanPath(File file) {
        String path = file.getAbsolutePath();
        path = path.replace('\\', '/');
        if (path.startsWith("/") && path.length() > 1) {
            return path.substring(1);
        }
        return path;
    }

    public static List<String> getGlobalExcludes() {
        return Lists.newArrayList(initOrGetGlobalExcludes(false));
    }
}
