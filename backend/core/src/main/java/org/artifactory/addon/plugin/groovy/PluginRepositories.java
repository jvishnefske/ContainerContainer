/*
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2019 JFrog Ltd.
 * Modifications copyright (C) 2026 the fork authors.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.addon.plugin.groovy;

import org.artifactory.addon.plugin.pkgtype.PluginApiResponse;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.properties.PropertiesService;
import org.artifactory.api.repo.exception.FileExpectedException;
import org.artifactory.api.repo.exception.ItemNotFoundRuntimeException;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.common.status.StatusHolder;
import org.artifactory.descriptor.property.Property;
import org.artifactory.fs.FileInfo;
import org.artifactory.fs.ItemInfo;
import org.artifactory.md.Properties;
import org.artifactory.repo.RepoPath;
import org.artifactory.resource.ResourceStreamHandle;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The {@code repositories} binding of plugin scripts: a small subset of the papi {@code Repositories}
 * interface implemented over the internal repository and properties services.
 *
 * <p><b>Permissions.</b> Calls run as the thread that runs the hook, never as the system: for request hooks and
 * for a package type's {@code api} handler that is the requesting user. Writes enforce that user's permissions the
 * way the services always do: {@link #deploy} goes through the normal upload path (deploy permission, and delete
 * permission to overwrite an existing file), {@link #delete} needs delete permission, and {@link #move} and
 * {@link #copy} move or copy only what the user may read and write (a move also needs delete on the source).
 * Reads do not check read permission: {@link #getContent}, {@link #openContent}, {@link #getFileInfo},
 * {@link #findFile}, {@link #exists} and {@link #getChildren} see the repository as stored, because plugins are
 * installed by administrators. For the HTTP API the gate is the request shell, which checks the method's
 * permission on the repository before any plugin code runs; these calls add the per-path write checks above.
 *
 * <p><b>Memory.</b> {@link #deploy} streams its input into the binary store; {@link #getContent} and
 * {@link #openContent} stream; only {@link #getStringContent} reads a whole file into memory and is meant for
 * small files.
 *
 * <p><b>Failures.</b> Failures surface as the services report them (a {@link StatusHolder} for deploy, delete,
 * move and copy, an empty {@code Optional} for {@link #findFile} and {@link #openContent} on a missing file,
 * exceptions otherwise) and are handled by the hook error policy of the engine.
 */
public final class PluginRepositories {

    private final RepositoryService repositoryService;
    private final PropertiesService propertiesService;

    public PluginRepositories(RepositoryService repositoryService, PropertiesService propertiesService) {
        this.repositoryService = Objects.requireNonNull(repositoryService, "repositoryService");
        this.propertiesService = Objects.requireNonNull(propertiesService, "propertiesService");
    }

    /** @return the file's info; throws when the path is missing or is a folder */
    public FileInfo getFileInfo(RepoPath repoPath) {
        return repositoryService.getFileInfo(repoPath);
    }

    /**
     * The facts of a stored file that a package type usually needs.
     *
     * @param repoPath     the file
     * @param size         its size in bytes
     * @param sha256       its SHA-256 as lower-case hex, which Artifactory computes on every deploy; {@code null}
     *                     only for files stored before Artifactory kept SHA-256 and never re-calculated
     * @param sha1         its SHA-1 as lower-case hex
     * @param md5          its MD5 as lower-case hex
     * @param lastModified last modification time, epoch milliseconds
     * @param mimeType     its MIME type as Artifactory stores it
     */
    public record StoredFile(RepoPath repoPath, long size, String sha256, String sha1, String md5, long lastModified,
            String mimeType) {
        public StoredFile {
            Objects.requireNonNull(repoPath, "repoPath");
        }
    }

    /** @return the file at {@code repoPath}, or empty when nothing is there or it is a folder */
    public Optional<StoredFile> findFile(RepoPath repoPath) {
        if (!repositoryService.exists(repoPath)) {
            return Optional.empty();
        }
        FileInfo info;
        try {
            info = repositoryService.getFileInfo(repoPath);
        } catch (ItemNotFoundRuntimeException | FileExpectedException e) {
            return Optional.empty();
        }
        String sha256 = info.getChecksumsInfo() == null ? null : info.getChecksumsInfo().getSha256();
        String sha1 = info.getChecksumsInfo() == null ? null : info.getChecksumsInfo().getSha1();
        String md5 = info.getChecksumsInfo() == null ? null : info.getChecksumsInfo().getMd5();
        return Optional.of(new StoredFile(repoPath, info.getSize(), sha256, sha1, md5, info.getLastModified(),
                info.getMimeType()));
    }

    /** @return a stream handle over the file's content; the caller closes it */
    public ResourceStreamHandle getContent(RepoPath repoPath) {
        return repositoryService.getResourceStreamHandle(repoPath);
    }

    /**
     * Opens the file's content as an API response body: a stream of the file's size that releases the underlying
     * handle when closed, which the HTTP API shell always does. Use it as {@code [status: 200, body: content]} or
     * {@code PluginApiResponse.status(200).withBody(content)}.
     *
     * @return the body, or empty when there is no file at {@code repoPath}
     */
    public Optional<PluginApiResponse.Body.Stream> openContent(RepoPath repoPath) {
        if (findFile(repoPath).isEmpty()) {
            return Optional.empty();
        }
        ResourceStreamHandle handle = repositoryService.getResourceStreamHandle(repoPath);
        return Optional.of(new PluginApiResponse.Body.Stream(new ApiResponseConversion.HandleClosingStream(handle),
                handle.getSize()));
    }

    /** @return the file's content decoded as a string */
    public String getStringContent(RepoPath repoPath) {
        return repositoryService.getStringContent(repoPath);
    }

    /**
     * Stores {@code content} at {@code repoPath}, replacing an existing file. The stream is read once, straight
     * into the binary store, so its size is bounded only by the storage. The deploy goes through the normal upload
     * path, so the repository type's own {@code upload} handler and, after commit, its {@code index} handler run as
     * for any other deploy. The caller keeps ownership of {@code content}.
     */
    public StatusHolder deploy(RepoPath repoPath, InputStream content) {
        return repositoryService.deploy(repoPath, content);
    }

    /**
     * Moves the file or folder at {@code from} to {@code to}, which must be in the same repository.
     *
     * @return the move status; an error with status 400 and nothing moved when {@code to} is in another repository
     */
    public StatusHolder move(RepoPath from, RepoPath to) {
        Optional<StatusHolder> refused = refuseCrossRepo("move", from, to);
        return refused.isPresent() ? refused.get() : repositoryService.move(from, to, false, true, true);
    }

    /**
     * Copies the file or folder at {@code from} to {@code to}, which must be in the same repository.
     *
     * @return the copy status; an error with status 400 and nothing copied when {@code to} is in another repository
     */
    public StatusHolder copy(RepoPath from, RepoPath to) {
        Optional<StatusHolder> refused = refuseCrossRepo("copy", from, to);
        return refused.isPresent() ? refused.get() : repositoryService.copy(from, to);
    }

    private static Optional<StatusHolder> refuseCrossRepo(String operation, RepoPath from, RepoPath to) {
        if (from.getRepoKey().equals(to.getRepoKey())) {
            return Optional.empty();
        }
        BasicStatusHolder status = new BasicStatusHolder();
        status.error("Plugin " + operation + " from " + from.getId() + " to another repository (" + to.getId() +
                ") is not allowed", 400, LoggerFactory.getLogger(PluginRepositories.class));
        return Optional.of(status);
    }

    /** Deletes the file or folder (recursively) at {@code repoPath}. */
    public StatusHolder delete(RepoPath repoPath) {
        return repositoryService.undeploy(repoPath);
    }

    /**
     * @return the direct children (files and folders) of the folder at {@code repoPath}; empty for a file or a
     * missing path
     */
    public List<ItemInfo> getChildren(RepoPath repoPath) {
        return repositoryService.getChildren(repoPath);
    }

    /** @return whether an item exists at {@code repoPath} */
    public boolean exists(RepoPath repoPath) {
        return repositoryService.exists(repoPath);
    }

    /** @return the item's properties */
    public Properties getProperties(RepoPath repoPath) {
        return propertiesService.getProperties(repoPath);
    }

    /**
     * Sets property {@code key} to {@code values} on the item, replacing previous values of that key.
     *
     * @return the item's properties after the change
     */
    public Properties setProperty(RepoPath repoPath, String key, String... values) {
        propertiesService.addProperty(repoPath, null, new Property(key), values);
        return propertiesService.getProperties(repoPath);
    }
}
