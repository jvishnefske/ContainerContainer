package org.artifactory.metrics.cache;

import lombok.experimental.UtilityClass;

import java.util.Set;

/**
 * @author Oleksandr Legenkiy
 */
@UtilityClass
public class CacheMonitorUtils {

    /**
     * This method suggests if cache monitoring should be ignored
     */
    public static boolean shouldIgnore(String cacheName, Class<?> ownerClass,
            Set<String> packagesToIgnore, Set<String> classesToIgnore, Set<String> cachesToIgnore) {
        if (cachesToIgnore.contains(cacheName)
                || classesToIgnore.contains(ownerClass.getName())
                || classesToIgnore.contains(ownerClass.getSimpleName())) {
            return true;
        }
        // Checking package names is more expensive (because of recursion), so we will skip it
        // if ignored package list is empty
        if (!packagesToIgnore.isEmpty()) {
            String packageName = ownerClass.getPackage().getName();
            while (!packageName.isEmpty()) {
                if (packagesToIgnore.contains(packageName)) {
                    return true;
                }
                int offset = packageName.lastIndexOf('.');
                packageName = offset < 0 ? "" : packageName.substring(0, offset);
            }
        }
        return false;
    }
}
