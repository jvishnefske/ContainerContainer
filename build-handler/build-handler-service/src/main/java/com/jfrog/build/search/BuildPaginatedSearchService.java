package com.jfrog.build.search;

import com.google.common.collect.Lists;
import com.jfrog.build.acl.model.BuildsSearchFilter;
import com.jfrog.build.model.BuildPaginatedResult;
import jakarta.annotation.Nullable;
import lombok.NonNull;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A generic pagination service that fetches data in batches and applies optional filtering.
 * <p>
 * This service handles pagination by repeatedly fetching data using a fixed batch size equal to the requested limit.
 * It continues fetching batches until either the requested limit is reached or no more data is available.
 * <p>
 * The service uses a search function to fetch data batches based on the provided {@link BuildsSearchFilter}.
 * It supports optional filtering of fetched data (e.g., filtering by permissions) using a {@link Predicate}.
 * The itemFilter is applied item-by-item as data is processed, ensuring that only items that pass the itemFilter are
 * added
 * to the final result. The offset tracks the position in the raw data source (all fetched items), not just the
 * filtered results, ensuring correct pagination continuation.
 * <p>
 * The service guarantees that the returned result will never exceed the requested limit, even when filtering
 * is applied.
 *
 * @param <T> the type of data being paginated
 */
public class BuildPaginatedSearchService<T> {

    private final Function<BuildsSearchFilter, List<T>> searchFunction;
    private final Predicate<T> itemFilter;

    public BuildPaginatedSearchService(@NonNull Function<BuildsSearchFilter, List<T>> searchFunction) {
        this(searchFunction, null);
    }

    public BuildPaginatedSearchService(
            @NonNull Function<BuildsSearchFilter, List<T>> searchFunction,
            @Nullable Predicate<T> itemFilter) {
        this.searchFunction = searchFunction;
        this.itemFilter = itemFilter;
    }

    @NonNull
    public BuildPaginatedResult<T> search(@NonNull BuildsSearchFilter searchFilter) {
        long recordsLimit = searchFilter.getLimit();
        long recordsOffset = searchFilter.getOffset();

        List<T> dataToReturn = Lists.newArrayList();
        while (dataToReturn.size() < recordsLimit) {
            searchFilter.setOffset(recordsOffset);
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
        }
        return BuildPaginatedResult.<T>builder()
                .data(dataToReturn)
                .offset(recordsOffset)
                .build();
    }
}
