package com.jfrog.build.model.api;

import lombok.Data;

import java.util.List;

@Data
public class PagingModel<T> {

    private final String totalItems;
    private final List<T> pagingData;

    public PagingModel(long numOfPages, List<T> pagingData) {
        this.totalItems = Long.toString(numOfPages);
        this.pagingData = pagingData;
    }
}
