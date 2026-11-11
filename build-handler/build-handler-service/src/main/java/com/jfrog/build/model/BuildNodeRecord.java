package com.jfrog.build.model;

public record BuildNodeRecord(String repoKey, String path, String name,
        String sha1, String md5, long size) {}
