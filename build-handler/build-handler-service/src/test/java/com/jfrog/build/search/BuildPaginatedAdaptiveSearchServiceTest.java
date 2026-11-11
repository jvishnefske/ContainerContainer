package com.jfrog.build.search;

import com.jfrog.build.acl.model.BuildsSearchFilter;
import com.jfrog.build.model.BuildPaginatedResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BuildPaginatedAdaptiveSearchServiceTest {

    @Mock
    private BuildsSearchFilter searchFilter;
    @Mock
    private Function<BuildsSearchFilter, List<String>> searchFunction;

    @Test
    void search_NoPostProcessor_SingleBatch_ReturnsAllData() {
        List<String> data = List.of("item1", "item2", "item3");
        when(searchFunction.apply(searchFilter)).thenReturn(data).thenReturn(List.of());
        BuildPaginatedAdaptiveSearchService<String> service = new BuildPaginatedAdaptiveSearchService<>(searchFunction, 100L);

        when(searchFilter.getOffset()).thenReturn(0L);
        when(searchFilter.getLimit()).thenReturn(10L);

        BuildPaginatedResult<String> result = service.search(searchFilter);

        BuildPaginatedResult<String> expected = BuildPaginatedResult.<String>builder()
                .data(data)
                .offset(3)
                .build();
        assertEquals(expected, result);
        verify(searchFunction, times(2)).apply(searchFilter);
        verify(searchFilter).setLimit(10L);
    }

    @Test
    void search_NoPostProcessor_SingleBatch_NonZeroOffset_ReturnsAllData() {
        List<String> data = List.of("item1", "item2", "item3");
        when(searchFunction.apply(searchFilter)).thenReturn(data).thenReturn(List.of());
        BuildPaginatedAdaptiveSearchService<String> service = new BuildPaginatedAdaptiveSearchService<>(searchFunction, 100L);

        when(searchFilter.getOffset()).thenReturn(3L);
        when(searchFilter.getLimit()).thenReturn(10L);

        BuildPaginatedResult<String> result = service.search(searchFilter);

        BuildPaginatedResult<String> expected = BuildPaginatedResult.<String>builder()
                .data(data)
                .offset(6)
                .build();
        assertEquals(expected, result);
        verify(searchFunction, times(2)).apply(searchFilter);
        verify(searchFilter).setLimit(10L);
    }

    @Test
    void search_NoPostProcessor_MultipleBatches_ReturnsUpToNumberOfRecords() {
        List<String> batch1 = List.of("item1", "item2");
        List<String> batch2 = List.of("item3", "item4");
        List<String> batch3 = List.of("item5");
        when(searchFunction.apply(searchFilter)).thenReturn(batch1).thenReturn(batch2).thenReturn(batch3);

        BuildPaginatedAdaptiveSearchService<String> service = new BuildPaginatedAdaptiveSearchService<>(searchFunction, 100L);
        when(searchFilter.getOffset()).thenReturn(0L);
        when(searchFilter.getLimit()).thenReturn(5L);

        BuildPaginatedResult<String> result = service.search(searchFilter);

        BuildPaginatedResult<String> expected = BuildPaginatedResult.<String>builder()
                .data(List.of("item1", "item2", "item3", "item4", "item5"))
                .offset(5)
                .build();
        assertEquals(expected, result);
        verify(searchFunction, times(3)).apply(searchFilter);
        verify(searchFilter).setLimit(5L);
        verify(searchFilter).setLimit(10L);
        verify(searchFilter).setLimit(20L);
    }

    @Test
    void search_NoPostProcessor_EmptyResults_ReturnsEmpty() {
        when(searchFunction.apply(searchFilter)).thenReturn(List.of());
        BuildPaginatedAdaptiveSearchService<String> service = new BuildPaginatedAdaptiveSearchService<>(searchFunction, 100L);

        when(searchFilter.getOffset()).thenReturn(0L);
        when(searchFilter.getLimit()).thenReturn(10L);

        BuildPaginatedResult<String> result = service.search(searchFilter);

        BuildPaginatedResult<String> expected = BuildPaginatedResult.<String>builder()
                .data(List.of())
                .offset(0)
                .build();
        assertEquals(expected, result);
        verify(searchFilter).setLimit(10L);
    }

    @Test
    void search_NoPostProcessor_LessDataThanLimit_ReturnsAllAvailable() {
        List<String> data = List.of("item1", "item2");
        when(searchFunction.apply(searchFilter)).thenReturn(data).thenReturn(List.of());
        BuildPaginatedAdaptiveSearchService<String> service = new BuildPaginatedAdaptiveSearchService<>(searchFunction, 100L);

        when(searchFilter.getOffset()).thenReturn(0L);
        when(searchFilter.getLimit()).thenReturn(10L);

        BuildPaginatedResult<String> result = service.search(searchFilter);

        BuildPaginatedResult<String> expected = BuildPaginatedResult.<String>builder()
                .data(data)
                .offset(2)
                .build();
        assertEquals(expected, result);
    }

    @Test
    void search_WithFilter_SingleBatch_FiltersData() {
        List<String> data = List.of("item1", "item2", "item3", "item4");
        when(searchFunction.apply(searchFilter)).thenReturn(data).thenReturn(List.of());
        Predicate<String> itemFilter = item -> !item.equals("item2");

        BuildPaginatedAdaptiveSearchService<String> service =
                new BuildPaginatedAdaptiveSearchService<>(searchFunction, 100L, itemFilter);
        when(searchFilter.getLimit()).thenReturn(10L);
        when(searchFilter.getOffset()).thenReturn(0L);

        BuildPaginatedResult<String> result = service.search(searchFilter);

        BuildPaginatedResult<String> expected = BuildPaginatedResult.<String>builder()
                .data(List.of("item1", "item3", "item4"))
                .offset(4) // Offset tracks all fetched items, filtered included
                .build();
        assertEquals(expected, result);
        verify(searchFunction, times(2)).apply(searchFilter);
        verify(searchFilter).setLimit(10L);
    }

    @Test
    void search_WithFilter_MultipleBatches_FiltersEachBatch() {
        List<String> batch1 = List.of("item1", "item2");
        List<String> batch2 = List.of("item3", "item4");
        List<String> batch3 = List.of("item5");
        when(searchFunction.apply(searchFilter)).thenReturn(batch1).thenReturn(batch2).thenReturn(batch3)
                .thenReturn(List.of());

        Predicate<String> itemFilter = item -> !item.contains("2") && !item.contains("4");

        BuildPaginatedAdaptiveSearchService<String> service =
                new BuildPaginatedAdaptiveSearchService<>(searchFunction, 100L, itemFilter);
        when(searchFilter.getLimit()).thenReturn(10L);
        when(searchFilter.getOffset()).thenReturn(0L);

        BuildPaginatedResult<String> result = service.search(searchFilter);

        BuildPaginatedResult<String> expected = BuildPaginatedResult.<String>builder()
                .data(List.of("item1", "item3", "item5"))
                .offset(5)
                .build();
        assertEquals(expected, result);
        verify(searchFunction, times(4)).apply(searchFilter);
        verify(searchFilter).setLimit(10L);
        verify(searchFilter).setLimit(20L);
        verify(searchFilter).setLimit(40L);
    }

    @Test
    void search_WithFilter_ContinuesUntilLimitReached() {
        List<String> batch1 = List.of("item1", "item2");
        List<String> batch2 = List.of("item3", "item4");
        List<String> batch3 = List.of("item5", "item6");
        when(searchFunction.apply(searchFilter)).thenReturn(batch1).thenReturn(batch2).thenReturn(batch3)
                .thenReturn(List.of());

        // Filter filters out even-numbered items
        Predicate<String> itemFilter = item -> {
            String num = item.replace("item", "");
            return Integer.parseInt(num) % 2 != 0;
        };

        BuildPaginatedAdaptiveSearchService<String> service =
                new BuildPaginatedAdaptiveSearchService<>(searchFunction, 100L, itemFilter);
        when(searchFilter.getLimit()).thenReturn(3L);
        when(searchFilter.getOffset()).thenReturn(0L);

        BuildPaginatedResult<String> result = service.search(searchFilter);

        BuildPaginatedResult<String> expected = BuildPaginatedResult.<String>builder()
                .data(List.of("item1", "item3", "item5"))
                .offset(5) // Processed 5 items (item1, item2, item3, item4, item5) to get 3 after filtering, stopped
                // before item6
                .build();
        assertEquals(expected, result);
        verify(searchFilter).setLimit(3L);
        verify(searchFilter).setLimit(6L);
        verify(searchFilter).setLimit(12L);
    }

    @Test
    void search_WithFilter_EmptyAfterFiltering_ContinuesFetching() {
        List<String> batch1 = List.of("item2", "item4"); // All filtered out
        List<String> batch2 = List.of("item1", "item3"); // All pass filter
        when(searchFunction.apply(searchFilter)).thenReturn(batch1).thenReturn(batch2);

        Predicate<String> itemFilter = item -> {
            String num = item.replace("item", "");
            return Integer.parseInt(num) % 2 != 0;
        };

        BuildPaginatedAdaptiveSearchService<String> service =
                new BuildPaginatedAdaptiveSearchService<>(searchFunction, 100L, itemFilter);
        when(searchFilter.getLimit()).thenReturn(2L);
        when(searchFilter.getOffset()).thenReturn(0L);

        BuildPaginatedResult<String> result = service.search(searchFilter);

        BuildPaginatedResult<String> expected = BuildPaginatedResult.<String>builder()
                .data(List.of("item1", "item3"))
                .offset(4)
                .build();
        assertEquals(expected, result);
        verify(searchFilter).setLimit(2L);
        verify(searchFilter).setLimit(4L);
    }

    @Test
    void search_ZeroLimit_ReturnsEmpty() {
        BuildPaginatedAdaptiveSearchService<String> service = new BuildPaginatedAdaptiveSearchService<>(searchFunction, 100L);

        when(searchFilter.getOffset()).thenReturn(0L);
        when(searchFilter.getLimit()).thenReturn(0L);

        BuildPaginatedResult<String> result = service.search(searchFilter);

        BuildPaginatedResult<String> expected = BuildPaginatedResult.<String>builder()
                .data(List.of())
                .offset(0)
                .build();
        assertEquals(expected, result);
    }

    @Test
    void search_AdaptiveDoubling_RespectsMaxFetchLimit() {
        List<String> batch1 = List.of("item1");
        List<String> batch2 = List.of("item2");
        List<String> batch3 = List.of("item3");
        when(searchFunction.apply(searchFilter)).thenReturn(batch1).thenReturn(batch2).thenReturn(batch3)
                .thenReturn(List.of());

        // Filter filters out all items
        Predicate<String> itemFilter = item -> false;

        BuildPaginatedAdaptiveSearchService<String> service =
                new BuildPaginatedAdaptiveSearchService<>(searchFunction, 20L, itemFilter);
        when(searchFilter.getLimit()).thenReturn(5L);
        when(searchFilter.getOffset()).thenReturn(0L);

        BuildPaginatedResult<String> result = service.search(searchFilter);

        BuildPaginatedResult<String> expected = BuildPaginatedResult.<String>builder()
                .data(List.of())
                .offset(3)
                .build();
        assertEquals(expected, result);
        // Verify that fetchLimit doubles: 5 -> 10 -> 20 (max), then stays at 20
        verify(searchFilter).setLimit(5L);
        verify(searchFilter).setLimit(10L);
        // Should cap at maxFetchLimit (called twice: once when reaching 20, once more when still capped)
        verify(searchFilter, times(2)).setLimit(20L);
    }

    @Test
    void search_AdaptiveDoubling_StopsAtMaxFetchLimit() {
        List<String> batch1 = List.of("item1");
        List<String> batch2 = List.of("item2");
        when(searchFunction.apply(searchFilter)).thenReturn(batch1).thenReturn(batch2)
                .thenReturn(List.of());

        Predicate<String> itemFilter = item -> false;

        BuildPaginatedAdaptiveSearchService<String> service =
                new BuildPaginatedAdaptiveSearchService<>(searchFunction, 10L, itemFilter);
        when(searchFilter.getLimit()).thenReturn(5L);
        when(searchFilter.getOffset()).thenReturn(0L);

        BuildPaginatedResult<String> result = service.search(searchFilter);

        BuildPaginatedResult<String> expected = BuildPaginatedResult.<String>builder()
                .data(List.of())
                .offset(2)
                .build();
        assertEquals(expected, result);
        // Verify that fetchLimit doubles: 5 -> 10 (max), then stays at 10
        verify(searchFilter).setLimit(5L);
        verify(searchFilter, times(2)).setLimit(10L);
    }

    @Test
    void search_WithFilter_AdaptiveDoubling_OptimizesBatchSize() {
        // First batch: 2 items, 1 filtered out -> need more (have 1, need 4 more)
        // Second batch: 4 items, 2 filtered out -> need more (have 3, need 2 more)
        // Third batch: 4 items, process until limit reached -> exactly reaches limit (have 5)
        List<String> batch1 = List.of("item1", "item2");
        List<String> batch2 = List.of("item3", "item4", "item5", "item6");
        List<String> batch3 = List.of("item7", "item8", "item9", "item10");
        when(searchFunction.apply(searchFilter)).thenReturn(batch1).thenReturn(batch2).thenReturn(batch3)
                .thenReturn(List.of());

        // Filter filters out even-numbered items
        Predicate<String> itemFilter = item -> {
            String num = item.replace("item", "");
            return Integer.parseInt(num) % 2 != 0;
        };

        BuildPaginatedAdaptiveSearchService<String> service =
                new BuildPaginatedAdaptiveSearchService<>(searchFunction, 100L, itemFilter);
        when(searchFilter.getLimit()).thenReturn(5L);
        when(searchFilter.getOffset()).thenReturn(0L);

        BuildPaginatedResult<String> result = service.search(searchFilter);

        // Should get: item1, item3, item5, item7, item9 (5 items)
        // Processed: batch1 (2 items), batch2 (4 items), batch3 up to item9 (3 items) = 9 items
        BuildPaginatedResult<String> expected = BuildPaginatedResult.<String>builder()
                .data(List.of("item1", "item3", "item5", "item7", "item9"))
                .offset(9) // Processed 2 + 4 + 3 = 9 items (stopped after item9, before item10)
                .build();
        assertEquals(expected, result);
        // Verify adaptive doubling: 5 -> 10 -> 20
        verify(searchFilter).setLimit(5L);
        verify(searchFilter).setLimit(10L);
        verify(searchFilter).setLimit(20L);
    }
}
