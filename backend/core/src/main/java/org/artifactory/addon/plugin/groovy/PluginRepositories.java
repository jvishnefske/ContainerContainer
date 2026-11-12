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

import org.artifactory.api.properties.PropertiesService;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.common.status.StatusHolder;
import org.artifactory.descriptor.property.Property;
import org.artifactory.fs.FileInfo;
import org.artifactory.fs.ItemInfo;
import org.artifactory.md.Properties;
import org.artifactory.repo.RepoPath;
import org.artifactory.resource.ResourceStreamHandle;

import java.io.InputStream;
import java.util.List;
import java.util.Objects;

/**
 * The {@code repositories} binding of plugin scripts: a small subset of the papi {@code Repositories}
 * interface implemented over the internal repository and properties services.
 *
 * <p>Calls run with the permissions of the thread that runs the hook (the requesting user for request
 * hooks). {@link #getContent(RepoPath)} reads raw content and skips read-permission checks, as plugins are
 * installed by administrators. Failures surface as the services report them (a {@link StatusHolder} for
 * deploy and delete, exceptions otherwise) and are handled by the hook error policy of the engine.
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

    /** @return a stream handle over the file's content; the caller closes it */
    public ResourceStreamHandle getContent(RepoPath repoPath) {
        return repositoryService.getResourceStreamHandle(repoPath);
    }

    /** @return the file's content decoded as a string */
    public String getStringContent(RepoPath repoPath) {
        return repositoryService.getStringContent(repoPath);
    }

    /** Stores {@code content} at {@code repoPath}, replacing an existing file. */
    public StatusHolder deploy(RepoPath repoPath, InputStream content) {
        return repositoryService.deploy(repoPath, content);
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
