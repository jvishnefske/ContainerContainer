package org.artifactory.api.rest.pagination;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class Pagination {

    private long pageSize;
    private long currentPage;
    private long lastPage;
    private long total;

    public static Pagination createPagination(long page, long pageSize, long total) {
        int last = (int) (Math.ceil((double) total / pageSize));
        return Pagination.builder()
                .pageSize(pageSize)
                .currentPage(page)
                .lastPage(last)
                .total(total)
                .build();
    }
}
