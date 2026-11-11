package org.artifactory.repo.cache;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.function.IntSupplier;

/**
 * A Utility class that handles cache loader optimization logic
 *
 * @author Oleksandr Legenkiy
 */
@Slf4j
@UtilityClass
public class RepoConfigCacheLoaderOptimizationUtils {

    /**
     * Defines if should getAll optimization be applied. Since some times, it is faster to do getAll instead of loading
     * repo configs by keys via IN. For example:
     * ---
     * keys to fetch = 10, total keys = 100, batch = 5
     * 2 batches - useful, 18 batches - unuseful
     * ratio = 2/18 = 0.11, optimization ratio = 1, getAll is not effective
     * ---
     * keys to fetch = 60, total keys = 100, batch = 5
     * 12 batches - useful, 8 batches - unuseful
     * ratio = 12/8 = 1.5, optimization ratio = 1, getAll is effective
     */
    public static boolean shouldGetAllOptimizationBeApplied(int numOfKeysToLoad,
            IntSupplier allReposCountFunction, int batchOperationSize, double optimizationRatio) {
        int allRepoKeysCount = allReposCountFunction.getAsInt();
        log.trace("Keys to load : {}, all keys count: {}, batch size: {}, optimization ratio: {}", numOfKeysToLoad,
                allRepoKeysCount, batchOperationSize, optimizationRatio);
        if (optimizationRatio < 0) {
            log.trace("Skip optimization because optimization ratio less than 0");
            return false;
        }
        if (optimizationRatio == 0) {
            log.trace("Always do optimization because optimization ratio is 0");
            return true;
        }
        if (numOfKeysToLoad <= batchOperationSize) {
            log.trace("Skip optimization because keys to load less than batch size");
            return false;
        }
        if (numOfKeysToLoad >= allRepoKeysCount) {
            log.trace("Do optimization because keys to load more than all repo keys");
            return true;
        }
        double usefulRepoConfigBatchQueries = Math.ceil((double) numOfKeysToLoad / batchOperationSize);
        double allRepoConfigBatchQueries = Math.ceil((double) allRepoKeysCount / batchOperationSize);
        if (usefulRepoConfigBatchQueries >= allRepoConfigBatchQueries) {
            log.trace("Do optimization because useful batch queries {} more than all batch queries {}",
                    usefulRepoConfigBatchQueries, allRepoConfigBatchQueries);
            return true;
        }
        double unusefulRepoConfigBatchQueries = allRepoConfigBatchQueries - usefulRepoConfigBatchQueries;
        log.trace("Unuseful batch queries: {}", unusefulRepoConfigBatchQueries);
        double ration = usefulRepoConfigBatchQueries / unusefulRepoConfigBatchQueries;
        boolean isOptimizationHelpful = ration >= optimizationRatio;
        log.trace("Calculated ratio: {}, is optimization helpful: {}", ration, isOptimizationHelpful);
        return isOptimizationHelpful;
    }
}
