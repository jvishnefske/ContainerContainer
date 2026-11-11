package org.artifactory.api.rest.pagination;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;

public class PaginationTest {

    @ParameterizedTest
    @MethodSource("values")
    void createPagination(int page, int size, int total, int lastPage) {
        Pagination pagination = Pagination.createPagination(page, size, total);
        assertEquals(page, pagination.getCurrentPage());
        assertEquals(size, pagination.getPageSize());
        assertEquals(total, pagination.getTotal());
        assertEquals(lastPage, pagination.getLastPage());
    }

    private static Stream<Arguments> values() {
        return Stream.of(
                Arguments.of(1, 10, 20, 2),
                Arguments.of(1, 1, 10, 10),
                Arguments.of(1, 20, 5, 1)
        );
    }
}
