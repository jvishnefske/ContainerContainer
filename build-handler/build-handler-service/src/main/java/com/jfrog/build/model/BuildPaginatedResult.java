package com.jfrog.build.model;

import lombok.Builder;
import lombok.NonNull;

import java.util.List;

@Builder
public record BuildPaginatedResult<T>(@NonNull List<T> data, long offset) {
}
