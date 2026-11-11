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

import groovy.lang.Closure;
import org.artifactory.addon.plugin.ResponseCtx;
import org.artifactory.addon.plugin.pkgtype.PluginDownloadHandler;
import org.artifactory.addon.plugin.pkgtype.PluginIndexHandler;
import org.artifactory.addon.plugin.pkgtype.PluginPackageType;
import org.artifactory.addon.plugin.pkgtype.PluginUploadHandler;
import org.artifactory.addon.plugin.pkgtype.UploadDecision;
import org.artifactory.exception.CancelException;
import org.codehaus.groovy.runtime.typehandling.DefaultTypeTransformation;

/**
 * Delegate of a {@code packageType('name') { ... }} block; builds one {@link PluginPackageType}.
 *
 * <p>Keywords: {@code displayName 'text'}, {@code layout 'layout-name'},
 * {@code download { request, repoPath, response -> boolean }}, {@code upload { request, repoPath -> UploadDecision }}
 * and {@code index { repoPath -> }}. Name validation is left to the registry, which reports rejected names
 * in the plugin's status.
 *
 * <p>The closures are adapted to the handler interfaces so that the plugin boundary never throws:
 * <ul>
 *     <li>download: the closure's delegate is the {@code ResponseCtx}, so {@code status = 200} works. Groovy
 *         truth of the return value decides whether the request was handled. A failure is reported and
 *         declines (normal Generic handling); a {@link CancelException} sets its status and message on the
 *         response and counts as handled.</li>
 *     <li>upload: an {@link UploadDecision} is used as is; {@code null} accepts. Any other value or a failure is
 *         reported and accepts. A {@link CancelException} rejects with its status (403 when it is not 4xx/5xx).</li>
 *     <li>index: failures, including cancellation, are reported; there is no request left to fail.</li>
 * </ul>
 */
public final class PackageTypeDelegate {

    private final PluginPackageType.Builder builder;
    private final String name;
    private final String pluginName;
    private final HookFailureReporter reporter;

    PackageTypeDelegate(String name, String pluginName, HookFailureReporter reporter) {
        this.builder = PluginPackageType.builder(name, pluginName);
        this.name = name;
        this.pluginName = pluginName;
        this.reporter = reporter;
    }

    /** Sets the human-readable name. */
    public void displayName(String value) {
        builder.displayName(value);
    }

    /** Sets the repository layout suggested for new repositories of this type. */
    public void layout(String value) {
        builder.layoutName(value);
    }

    /** Sets the GET/HEAD handler. */
    public void download(Closure<?> closure) {
        builder.download(downloadHandler(closure));
    }

    /** Sets the PUT handler. */
    public void upload(Closure<?> closure) {
        builder.upload(uploadHandler(closure));
    }

    /** Sets the handler run after an artifact is stored. */
    public void index(Closure<?> closure) {
        builder.index(indexHandler(closure));
    }

    PluginPackageType build() {
        return builder.build();
    }

    private PluginDownloadHandler downloadHandler(Closure<?> closure) {
        String what = "packageType(" + name + ").download";
        return (request, repoPath, response) ->
                handled(HookInvoker.invoke(closure, response, request, repoPath, response), response, what);
    }

    private PluginUploadHandler uploadHandler(Closure<?> closure) {
        String what = "packageType(" + name + ").upload";
        return (request, repoPath) -> decision(HookInvoker.invoke(closure, null, request, repoPath), what);
    }

    private PluginIndexHandler indexHandler(Closure<?> closure) {
        String what = "packageType(" + name + ").index";
        return stored -> indexed(HookInvoker.invoke(closure, null, stored), what);
    }

    private boolean handled(HookOutcome outcome, ResponseCtx response, String what) {
        return switch (outcome) {
            case HookOutcome.Completed completed -> DefaultTypeTransformation.castToBoolean(completed.value());
            case HookOutcome.Failed failed -> {
                reporter.report(pluginName, what, failed.error());
                yield false;
            }
            case HookOutcome.Cancelled cancelled -> {
                response.setStatus(cancelled.cancel().getErrorCode());
                response.setMessage(cancelled.cancel().getMessage());
                yield true;
            }
        };
    }

    private UploadDecision decision(HookOutcome outcome, String what) {
        return switch (outcome) {
            case HookOutcome.Completed completed -> toDecision(completed.value(), what);
            case HookOutcome.Failed failed -> {
                reporter.report(pluginName, what, failed.error());
                yield UploadDecision.accept();
            }
            case HookOutcome.Cancelled cancelled -> UploadDecision.reject(
                    rejectStatus(cancelled.cancel().getErrorCode()), String.valueOf(cancelled.cancel().getMessage()));
        };
    }

    private void indexed(HookOutcome outcome, String what) {
        switch (outcome) {
            case HookOutcome.Completed completed -> {
            }
            case HookOutcome.Failed failed -> reporter.report(pluginName, what, failed.error());
            case HookOutcome.Cancelled cancelled -> reporter.report(pluginName, what, cancelled.cancel());
        }
    }

    private UploadDecision toDecision(Object value, String what) {
        if (value == null) {
            return UploadDecision.accept();
        }
        if (value instanceof UploadDecision decision) {
            return decision;
        }
        reporter.report(pluginName, what, new IllegalStateException(
                "upload handler returned " + value.getClass().getName() + ", expected UploadDecision; accepting"));
        return UploadDecision.accept();
    }

    /** @return {@code status} when it is a valid reject status (4xx or 5xx), otherwise 403 */
    static int rejectStatus(int status) {
        return status >= 400 && status <= 599 ? status : 403;
    }
}
