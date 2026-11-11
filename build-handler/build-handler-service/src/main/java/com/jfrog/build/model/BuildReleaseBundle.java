package com.jfrog.build.model;

import lombok.NonNull;

import java.io.Serializable;

public record BuildReleaseBundle(@NonNull String repoKey, @NonNull String name) implements Serializable {
}
