package org.artifactory.mpu;

import org.jfrog.storage.mpu.entity.MultipartUploadTask;

import javax.annotation.Nullable;

public record MultipartStatusResult(MultipartUploadTask task, String message, @Nullable String token) {
}
