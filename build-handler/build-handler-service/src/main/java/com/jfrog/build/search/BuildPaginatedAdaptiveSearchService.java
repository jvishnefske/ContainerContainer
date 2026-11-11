package com.jfrog.build.search;

import com.google.common.collect.Lists;
import com.jfrog.build.acl.model.BuildsSearchFilter;
import com.jfrog.build.model.BuildPaginatedResult;
import jakarta.annotation.Nullable;
import lombok.NonNull;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import static java.lang.Math.min;

/**
 * An adaptive pagination service that dynamically adjusts batch sizes during pagination.
 * <p>
 * This service starts with a batch size equal to the requested limit and doubles it after each iteration
 * (up to the maximum fetch limit). This adaptive approach is useful when the filter filters out
 * a significant portion of fetched data, as it allows fetching larger batches to compensate for filtered results.
 * <p>
 * The service uses a search function to fetch data batches based on the provided {@link BuildsSearchFilter}.
 * It supports optional filtering of fetched data (e.g., filtering by permissions) using a {@link Predicate}.
 * The itemFilter is applied item-by-item as data is processed, ensuring that only items that pass the itemFilter are
 * added
 * to the final result. The offset tracks the position in the raw data source (all fetched items), not just the
 * filtered results, ensuring correct pagination continuation.
 * <p>
 * The dynamic batch sizing helps optimize performance by reducing the number of database queries needed
 * when many results are filtered out during filtering (e.g., permission checks).
 * <p>
 * The service guarantees that the returned result will never exceed the requested limit, even when filtering
 * is applied.
 *
 * @param <T> the type of data being paginated
 */
public class BuildPaginatedAdaptiveSearchService<T> {

    private final Function<BuildsSearchFilter, List<T>> searchFunction;
    private final Predicate<T> itemFilter;
    private final long maxFetchLimit;

    public BuildPaginatedAdaptiveSearchService(
            @NonNull Function<BuildsSearchFilter, List<T>> searchFunction,
            long maxFetchLimit) {
        this(searchFunction, maxFetchLimit, null);
    }

    public BuildPaginatedAdaptiveSearchService(
            @NonNull Function<BuildsSearchFilter, List<T>> searchFunction,
            long maxFetchLimit,
            @Nullable Predicate<T> itemFilter) {
        this.searchFunction = searchFunction;
        this.itemFilter = itemFilter;
        this.maxFetchLimit = maxFetchLimit;
    }

    @NonNull
    public BuildPaginatedResult<T> search(@NonNull BuildsSearchFilter searchFilter) {
        long recordsLimit = searchFilter.getLimit();
        long recordsOffset = searchFilter.getOffset();
        long fetchLimit = recordsLimit;

        List<T> dataToReturn = Lists.newArrayList();
        while (dataToReturn.size() < recordsLimit) {
            searchFilter.setOffset(recordsOffset);
            searchFilter.setLimit(fetchLimit);
            List<T> fetchedData = searchFunction.apply(searchFilter);
            if (fetchedData.isEmpty()) {
                break;
            }

            for (T fetchedItem : fetchedData) {
                recordsOffset++;
                if (itemFilter == null || itemFilter.test(fetchedItem)) {
                    dataToReturn.add(fetchedItem);
                }

                if (dataToReturn.size() >= recordsLimit) {
                    break;
                }
            }

            fetchLimit = min(fetchLimit * 2, maxFetchLimit);
        }
        return BuildPaginatedResult.<T>builder()
                .data(dataToReturn)
                .offset(recordsOffset)
                .build();
    }
}

