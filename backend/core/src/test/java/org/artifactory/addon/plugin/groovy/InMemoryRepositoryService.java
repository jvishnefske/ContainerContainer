/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.addon.plugin.groovy;

import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.common.MoveMultiStatusHolder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.repo.exception.FileExpectedException;
import org.artifactory.api.repo.exception.ItemNotFoundRuntimeException;
import org.artifactory.checksum.ChecksumInfo;
import org.artifactory.checksum.ChecksumType;
import org.artifactory.checksum.ChecksumsInfo;
import org.artifactory.fs.FileInfo;
import org.artifactory.fs.ItemInfo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.resource.ResourceStreamHandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test fixture: a {@link RepositoryService} mock backed by an in-memory tree of files, enough for the
 * {@code repositories} binding of a plugin ({@code exists}, {@code getFileInfo}, {@code getResourceStreamHandle},
 * {@code getStringContent}, {@code deploy}, {@code undeploy}, {@code getChildren}, {@code copy}). Every stored file
 * gets its real SHA-256, as Artifactory computes on deploy. It records every repository key the plugin touched and
 * counts open content handles, and can refuse overwrites (a caller without delete permission) and copies.
 */
final class InMemoryRepositoryService {

    private static final Logger log = LoggerFactory.getLogger(InMemoryRepositoryService.class);

    /** Files by {@code repoKey:path}. Folders are implied by the files under them. */
    private final TreeMap<String, byte[]> files = new TreeMap<>();
    private final Set<String> touchedRepoKeys = new LinkedHashSet<>();
    private final AtomicInteger openHandles = new AtomicInteger();
    private final RepositoryService service = mock(RepositoryService.class);

    /** When set, deploying over an existing file and deleting fail with 403, as for a deploy-only user. */
    boolean denyOverwriteAndDelete;
    /** When set, copies fail with 403 (a caller who may not read the source). */
    boolean denyCopy;
    /** Runs once, just before the next copy (to simulate a concurrent push). */
    Runnable beforeNextCopy;
    int deploys;

    InMemoryRepositoryService() {
        when(service.exists(any())).thenAnswer(a -> exists(touch(a.getArgument(0))));
        when(service.getFileInfo(any())).thenAnswer(a -> fileInfo(touch(a.getArgument(0))));
        when(service.getResourceStreamHandle(any())).thenAnswer(a -> handle(touch(a.getArgument(0))));
        when(service.getStringContent(any(RepoPath.class))).thenAnswer(a -> {
            byte[] content = files.get(key(touch(a.getArgument(0))));
            return content == null ? null : new String(content, StandardCharsets.UTF_8);
        });
        when(service.deploy(any(RepoPath.class), any(InputStream.class)))
                .thenAnswer(a -> deploy(touch(a.getArgument(0)), a.getArgument(1)));
        when(service.undeploy(any(RepoPath.class))).thenAnswer(a -> undeploy(touch(a.getArgument(0))));
        when(service.getChildren(any(RepoPath.class))).thenAnswer(a -> children(touch(a.getArgument(0))));
        when(service.copy(any(RepoPath.class), any(RepoPath.class)))
                .thenAnswer(a -> copy(touch(a.getArgument(0)), touch(a.getArgument(1))));
    }

    RepositoryService service() {
        return service;
    }

    /** Stores {@code content} directly, bypassing the binding (and not counted as touched by the plugin). */
    void put(String repoKey, String path, byte[] content) {
        files.put(repoKey + ":" + path, content.clone());
    }

    byte[] get(String repoKey, String path) {
        byte[] content = files.get(repoKey + ":" + path);
        return content == null ? null : content.clone();
    }

    boolean has(String repoKey, String path) {
        return exists(RepoPathFactory.create(repoKey, path));
    }

    /** @return the stored paths of {@code repoKey} starting with {@code prefix} */
    List<String> paths(String repoKey, String prefix) {
        List<String> result = new ArrayList<>();
        String start = repoKey + ":" + prefix;
        for (String key : files.tailMap(start, true).keySet()) {
            if (!key.startsWith(start)) {
                break;
            }
            result.add(key.substring(repoKey.length() + 1));
        }
        return result;
    }

    Set<String> touchedRepoKeys() {
        return touchedRepoKeys;
    }

    int openHandles() {
        return openHandles.get();
    }

    static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private RepoPath touch(RepoPath path) {
        touchedRepoKeys.add(path.getRepoKey());
        return path;
    }

    private static String key(RepoPath path) {
        return path.getRepoKey() + ":" + path.getPath();
    }

    private boolean isFolder(RepoPath path) {
        String prefix = path.getPath().isEmpty() ? path.getRepoKey() + ":" : key(path) + "/";
        String next = files.ceilingKey(prefix);
        return next != null && next.startsWith(prefix);
    }

    private boolean exists(RepoPath path) {
        return path.getPath().isEmpty() || files.containsKey(key(path)) || isFolder(path);
    }

    private FileInfo fileInfo(RepoPath path) {
        byte[] content = files.get(key(path));
        if (content == null) {
            if (isFolder(path)) {
                throw new FileExpectedException(path);
            }
            throw new ItemNotFoundRuntimeException(path);
        }
        FileInfo info = mock(FileInfo.class);
        ChecksumsInfo checksums = new ChecksumsInfo();
        String sha256 = sha256(content);
        checksums.addChecksumInfo(new ChecksumInfo(ChecksumType.sha256, sha256, sha256));
        when(info.getChecksumsInfo()).thenReturn(checksums);
        when(info.getSize()).thenReturn((long) content.length);
        when(info.getRepoPath()).thenReturn(path);
        when(info.getMimeType()).thenReturn("application/octet-stream");
        return info;
    }

    private ResourceStreamHandle handle(RepoPath path) {
        byte[] content = files.get(key(path));
        if (content == null) {
            throw new ItemNotFoundRuntimeException(path);
        }
        openHandles.incrementAndGet();
        ResourceStreamHandle handle = mock(ResourceStreamHandle.class);
        InputStream input = new ByteArrayInputStream(content);
        when(handle.getInputStream()).thenReturn(input);
        when(handle.getSize()).thenReturn((long) content.length);
        org.mockito.Mockito.doAnswer(a -> {
            openHandles.decrementAndGet();
            return null;
        }).when(handle).close();
        return handle;
    }

    private BasicStatusHolder deploy(RepoPath path, InputStream input) {
        BasicStatusHolder status = new BasicStatusHolder();
        if (denyOverwriteAndDelete && files.containsKey(key(path))) {
            status.error("Not enough permissions to overwrite artifact '" + path + "'", 403, log);
            return status;
        }
        try {
            files.put(key(path), input.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        deploys++;
        return status;
    }

    private BasicStatusHolder undeploy(RepoPath path) {
        BasicStatusHolder status = new BasicStatusHolder();
        if (denyOverwriteAndDelete) {
            status.error("Not enough permissions to delete '" + path + "'", 403, log);
            return status;
        }
        files.remove(key(path));
        String prefix = key(path) + "/";
        files.keySet().removeIf(k -> k.startsWith(prefix));
        return status;
    }

    private List<ItemInfo> children(RepoPath folder) {
        String prefix = folder.getPath().isEmpty() ? folder.getRepoKey() + ":" : key(folder) + "/";
        Map<String, Boolean> names = new TreeMap<>();
        for (String k : files.tailMap(prefix, true).keySet()) {
            if (!k.startsWith(prefix)) {
                break;
            }
            String rest = k.substring(prefix.length());
            int slash = rest.indexOf('/');
            names.merge(slash < 0 ? rest : rest.substring(0, slash), slash >= 0, (a, b) -> a || b);
        }
        List<ItemInfo> result = new ArrayList<>();
        names.forEach((name, isFolder) -> {
            ItemInfo item = mock(ItemInfo.class);
            RepoPath child = RepoPathFactory.create(folder.getRepoKey(),
                    folder.getPath().isEmpty() ? name : folder.getPath() + "/" + name);
            when(item.isFolder()).thenReturn(isFolder);
            when(item.getRepoPath()).thenReturn(child);
            when(item.getName()).thenReturn(name);
            result.add(item);
        });
        return result;
    }

    private MoveMultiStatusHolder copy(RepoPath from, RepoPath to) {
        if (beforeNextCopy != null) {
            Runnable hook = beforeNextCopy;
            beforeNextCopy = null;
            hook.run();
        }
        MoveMultiStatusHolder status = new MoveMultiStatusHolder();
        byte[] content = files.get(key(from));
        if (denyCopy || content == null) {
            status.error("copy of " + from + " refused", 403, log);
            return status;
        }
        if (denyOverwriteAndDelete && files.containsKey(key(to))) {
            status.error("Not enough permissions to overwrite artifact '" + to + "'", 403, log);
            return status;
        }
        files.put(key(to), content.clone());
        return status;
    }
}
