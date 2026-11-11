package com.jfrog.build.model.api;

import lombok.NonNull;

public record BuildIdReleaseBundle(@NonNull String repoKey, @NonNull String name) {
}
