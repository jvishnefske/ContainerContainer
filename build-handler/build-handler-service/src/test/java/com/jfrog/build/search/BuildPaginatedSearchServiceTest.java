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
class BuildPaginatedSearchServiceTest {

    @Mock
    private BuildsSearchFilter searchFilter;
    @Mock
    private Function<BuildsSearchFilter, List<String>> searchFunction;

    @Test
    void search_NoPostProcessor_SingleBatch_ReturnsAllData() {
        List<String> data = List.of("item1", "item2", "item3");
        when(searchFunction.apply(searchFilter)).thenReturn(data).thenReturn(List.of());
        BuildPaginatedSearchService<String> service = new BuildPaginatedSearchService<>(searchFunction);

        when(searchFilter.getOffset()).thenReturn(0L);
        when(searchFilter.getLimit()).thenReturn(10L);

        BuildPaginatedResult<String> result = service.search(searchFilter);

        BuildPaginatedResult<String> expected = BuildPaginatedResult.<String>builder()
                .data(data)
                .offset(3)
                .build();
        assertEquals(expected, result);
        verify(searchFunction, times(2)).apply(searchFilter);
    }

    @Test
    void search_NoPostProcessor_SingleBatch_NonZeroOffset_ReturnsAllData() {
        List<String> data = List.of("item1", "item2", "item3");
        when(searchFunction.apply(searchFilter)).thenReturn(data).thenReturn(List.of());
        BuildPaginatedSearchService<String> service = new BuildPaginatedSearchService<>(searchFunction);

        when(searchFilter.getOffset()).thenReturn(3L);
        when(searchFilter.getLimit()).thenReturn(10L);

        BuildPaginatedResult<String> result = service.search(searchFilter);

        BuildPaginatedResult<String> expected = BuildPaginatedResult.<String>builder()
                .data(data)
                .offset(6)
                .build();
        assertEquals(expected, result);
        verify(searchFunction, times(2)).apply(searchFilter);
    }

    @Test
    void search_NoPostProcessor_MultipleBatches_ReturnsUpToNumberOfRecords() {
        List<String> batch1 = List.of("item1", "item2");
        List<String> batch2 = List.of("item3", "item4");
        List<String> batch3 = List.of("item5");
        when(searchFunction.apply(searchFilter)).thenReturn(batch1).thenReturn(batch2).thenReturn(batch3);

        BuildPaginatedSearchService<String> service = new BuildPaginatedSearchService<>(searchFunction);
        when(searchFilter.getOffset()).thenReturn(0L);
        when(searchFilter.getLimit()).thenReturn(5L);

        BuildPaginatedResult<String> result = service.search(searchFilter);

        BuildPaginatedResult<String> expected = BuildPaginatedResult.<String>builder()
                .data(List.of("item1", "item2", "item3", "item4", "item5"))
                .offset(5)
                .build();
        assertEquals(expected, result);
        verify(searchFunction, times(3)).apply(searchFilter);
    }

    @Test
    void search_NoPostProcessor_EmptyResults_ReturnsEmpty() {
        when(searchFunction.apply(searchFilter)).thenReturn(List.of());
        BuildPaginatedSearchService<String> service = new BuildPaginatedSearchService<>(searchFunction);

        when(searchFilter.getOffset()).thenReturn(0L);
        when(searchFilter.getLimit()).thenReturn(10L);

        BuildPaginatedResult<String> result = service.search(searchFilter);

        BuildPaginatedResult<String> expected = BuildPaginatedResult.<String>builder()
                .data(List.of())
                .offset(0)
                .build();
        assertEquals(expected, result);
    }

    @Test
    void search_NoPostProcessor_LessDataThanLimit_ReturnsAllAvailable() {
        List<String> data = List.of("item1", "item2");
        when(searchFunction.apply(searchFilter)).thenReturn(data).thenReturn(List.of());
        BuildPaginatedSearchService<String> service = new BuildPaginatedSearchService<>(searchFunction);

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
        Predicate<String> filter = item -> !item.equals("item2");

        BuildPaginatedSearchService<String> service = new BuildPaginatedSearchService<>(searchFunction, filter);
        when(searchFilter.getLimit()).thenReturn(10L);
        when(searchFilter.getOffset()).thenReturn(0L);

        BuildPaginatedResult<String> result = service.search(searchFilter);

        BuildPaginatedResult<String> expected = BuildPaginatedResult.<String>builder()
                .data(List.of("item1", "item3", "item4"))
                .offset(4) // Offset tracks all fetched items, filtered included
                .build();
        assertEquals(expected, result);
        verify(searchFunction, times(2)).apply(searchFilter);
    }

    @Test
    void search_WithFilter_MultipleBatches_FiltersEachBatch() {
        List<String> batch1 = List.of("item1", "item2");
        List<String> batch2 = List.of("item3", "item4");
        List<String> batch3 = List.of("item5");
        when(searchFunction.apply(searchFilter)).thenReturn(batch1).thenReturn(batch2).thenReturn(batch3)
                .thenReturn(List.of());

        Predicate<String> filter = item -> !item.contains("2") && !item.contains("4");

        BuildPaginatedSearchService<String> service = new BuildPaginatedSearchService<>(searchFunction, filter);
        when(searchFilter.getLimit()).thenReturn(10L);
        when(searchFilter.getOffset()).thenReturn(0L);

        BuildPaginatedResult<String> result = service.search(searchFilter);

        BuildPaginatedResult<String> expected = BuildPaginatedResult.<String>builder()
                .data(List.of("item1", "item3", "item5"))
                .offset(5)
                .build();
        assertEquals(expected, result);
        verify(searchFunction, times(4)).apply(searchFilter);
    }

    @Test
    void search_WithFilter_ContinuesUntilLimitReached() {
        List<String> batch1 = List.of("item1", "item2");
        List<String> batch2 = List.of("item3", "item4");
        List<String> batch3 = List.of("item5", "item6");
        when(searchFunction.apply(searchFilter)).thenReturn(batch1).thenReturn(batch2).thenReturn(batch3)
                .thenReturn(List.of());

        // Filter filters out even-numbered items
        Predicate<String> filter = item -> {
            String num = item.replace("item", "");
            return Integer.parseInt(num) % 2 != 0;
        };

        BuildPaginatedSearchService<String> service = new BuildPaginatedSearchService<>(searchFunction, filter);
        when(searchFilter.getLimit()).thenReturn(3L);
        when(searchFilter.getOffset()).thenReturn(0L);

        BuildPaginatedResult<String> result = service.search(searchFilter);

        BuildPaginatedResult<String> expected = BuildPaginatedResult.<String>builder()
                .data(List.of("item1", "item3", "item5"))
                .offset(5) // Processed 5 items (item1, item2, item3, item4, item5) to get 3 after filtering, stopped
                // before item6
                .build();
        assertEquals(expected, result);
    }

    @Test
    void search_WithFilter_EmptyAfterFiltering_ContinuesFetching() {
        List<String> batch1 = List.of("item2", "item4"); // All filtered out
        List<String> batch2 = List.of("item1", "item3"); // All pass filter
        when(searchFunction.apply(searchFilter)).thenReturn(batch1).thenReturn(batch2);

        Predicate<String> filter = item -> {
            String num = item.replace("item", "");
            return Integer.parseInt(num) % 2 != 0;
        };

        BuildPaginatedSearchService<String> service = new BuildPaginatedSearchService<>(searchFunction, filter);
        when(searchFilter.getLimit()).thenReturn(2L);
        when(searchFilter.getOffset()).thenReturn(0L);

        BuildPaginatedResult<String> result = service.search(searchFilter);

        BuildPaginatedResult<String> expected = BuildPaginatedResult.<String>builder()
                .data(List.of("item1", "item3"))
                .offset(4)
                .build();
        assertEquals(expected, result);
    }

    @Test
    void search_WithFilter_LimitNeverExceeded() {
        // This test verifies that when filtering is applied, the limit is never exceeded
        // Scenario: limit is 10, first batch has 8 items that pass filter, second batch has 5 items that pass filter
        // Result should be exactly 10 items, not 13
        List<String> batch1 = List.of("item1", "item2", "item3", "item4", "item5", "item6", "item7", "item8");
        List<String> batch2 = List.of("item9", "item10", "item11", "item12", "item13");
        when(searchFunction.apply(searchFilter)).thenReturn(batch1).thenReturn(batch2);

        // Filter that allows all items (no filtering)
        Predicate<String> filter = item -> true;

        BuildPaginatedSearchService<String> service = new BuildPaginatedSearchService<>(searchFunction, filter);
        when(searchFilter.getLimit()).thenReturn(10L);
        when(searchFilter.getOffset()).thenReturn(0L);

        BuildPaginatedResult<String> result = service.search(searchFilter);

        // Should return exactly 10 items, not 13
        assertEquals(10, result.data().size());
        assertEquals(10, result.offset()); // Offset should be 10 (stopped after adding 10th item)
    }

    @Test
    void search_ZeroLimit_ReturnsEmpty() {
        BuildPaginatedSearchService<String> service = new BuildPaginatedSearchService<>(searchFunction);

        when(searchFilter.getOffset()).thenReturn(0L);
        when(searchFilter.getLimit()).thenReturn(0L);

        BuildPaginatedResult<String> result = service.search(searchFilter);

        BuildPaginatedResult<String> expected = BuildPaginatedResult.<String>builder()
                .data(List.of())
                .offset(0)
                .build();
        assertEquals(expected, result);
    }
}
