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

import org.artifactory.addon.plugin.PluginAction;
import org.artifactory.addon.plugin.download.AfterDownloadAction;
import org.artifactory.addon.plugin.download.AfterDownloadErrorAction;
import org.artifactory.addon.plugin.download.AfterRemoteDownloadAction;
import org.artifactory.addon.plugin.download.AltAllResponsesAction;
import org.artifactory.addon.plugin.download.AltRemoteContentAction;
import org.artifactory.addon.plugin.download.AltRemotePathAction;
import org.artifactory.addon.plugin.download.AltResponseAction;
import org.artifactory.addon.plugin.download.BeforeDownloadAction;
import org.artifactory.addon.plugin.download.BeforeDownloadRequestAction;
import org.artifactory.addon.plugin.download.BeforeRemoteDownloadAction;
import org.artifactory.addon.plugin.download.BeforeRemoteInfoRequestAction;
import org.artifactory.addon.plugin.storage.AfterCopyAction;
import org.artifactory.addon.plugin.storage.AfterCreateAction;
import org.artifactory.addon.plugin.storage.AfterDeleteAction;
import org.artifactory.addon.plugin.storage.AfterMoveAction;
import org.artifactory.addon.plugin.storage.AfterPropertyCreateAction;
import org.artifactory.addon.plugin.storage.AfterPropertyDeleteAction;
import org.artifactory.addon.plugin.storage.BeforeCopyAction;
import org.artifactory.addon.plugin.storage.BeforeCreateAction;
import org.artifactory.addon.plugin.storage.BeforeDeleteAction;
import org.artifactory.addon.plugin.storage.BeforeMoveAction;
import org.artifactory.addon.plugin.storage.BeforePropertyCreateAction;
import org.artifactory.addon.plugin.storage.BeforePropertyDeleteAction;
import org.artifactory.addon.plugin.upload.BeforeUploadRequestAction;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The fixed mapping from DSL block and hook name to the {@link PluginAction} interface the hook binds to.
 *
 * <p>The blocks are {@code download}, {@code storage} and {@code upload}. Hook names follow the method
 * names of the {@code PluginAction} interfaces. The property hooks of the storage block are named
 * {@code beforePropertyCreate}, {@code afterPropertyCreate}, {@code beforePropertyDelete} and
 * {@code afterPropertyDelete}, because their interface methods reuse the names {@code beforeCreate} and
 * {@code afterDelete}. The catalog is pure data: the DSL uses it to reject unknown hook names at load
 * time, and the dispatcher selects hooks by the resulting action type.
 */
public final class HookCatalog {

    private static final Map<String, Map<String, Class<? extends PluginAction>>> BLOCKS = Map.of(
            "download", Map.ofEntries(
                    Map.entry("beforeDownloadRequest", BeforeDownloadRequestAction.class),
                    Map.entry("beforeDownload", BeforeDownloadAction.class),
                    Map.entry("altResponse", AltResponseAction.class),
                    Map.entry("altAllResponses", AltAllResponsesAction.class),
                    Map.entry("afterDownload", AfterDownloadAction.class),
                    Map.entry("afterDownloadError", AfterDownloadErrorAction.class),
                    Map.entry("beforeRemoteDownload", BeforeRemoteDownloadAction.class),
                    Map.entry("afterRemoteDownload", AfterRemoteDownloadAction.class),
                    Map.entry("beforeRemoteInfoRequest", BeforeRemoteInfoRequestAction.class),
                    Map.entry("altRemotePath", AltRemotePathAction.class),
                    Map.entry("altRemoteContent", AltRemoteContentAction.class)),
            "storage", Map.ofEntries(
                    Map.entry("beforeCreate", BeforeCreateAction.class),
                    Map.entry("afterCreate", AfterCreateAction.class),
                    Map.entry("beforeDelete", BeforeDeleteAction.class),
                    Map.entry("afterDelete", AfterDeleteAction.class),
                    Map.entry("beforeMove", BeforeMoveAction.class),
                    Map.entry("afterMove", AfterMoveAction.class),
                    Map.entry("beforeCopy", BeforeCopyAction.class),
                    Map.entry("afterCopy", AfterCopyAction.class),
                    Map.entry("beforePropertyCreate", BeforePropertyCreateAction.class),
                    Map.entry("afterPropertyCreate", AfterPropertyCreateAction.class),
                    Map.entry("beforePropertyDelete", BeforePropertyDeleteAction.class),
                    Map.entry("afterPropertyDelete", AfterPropertyDeleteAction.class)),
            "upload", Map.of(
                    "beforeUploadRequest", BeforeUploadRequestAction.class));

    private HookCatalog() {
    }

    /** @return the DSL block names that hold hooks */
    public static Set<String> blocks() {
        return BLOCKS.keySet();
    }

    /**
     * Looks up the action type a hook binds to.
     *
     * @param block    DSL block name, for example {@code download}
     * @param hookName hook name inside the block, for example {@code altResponse}
     * @return the action type, or empty when the block has no such hook
     */
    public static Optional<Class<? extends PluginAction>> actionType(String block, String hookName) {
        Map<String, Class<? extends PluginAction>> hooks = BLOCKS.get(block);
        return hooks == null ? Optional.empty() : Optional.ofNullable(hooks.get(hookName));
    }

    /** @return the hook names a block accepts, empty for an unknown block */
    public static Set<String> hookNames(String block) {
        Map<String, Class<? extends PluginAction>> hooks = BLOCKS.get(block);
        return hooks == null ? Set.of() : hooks.keySet();
    }
}
