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
import org.artifactory.addon.plugin.pkgtype.PluginApiHandler;
import org.artifactory.addon.plugin.pkgtype.PluginApiResponse;
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
 * {@code download { request, repoPath, response -> boolean }}, {@code upload { request, repoPath -> UploadDecision }},
 * {@code index { repoPath -> }} and {@code api { request -> PluginApiResponse or Map }}. Name validation is left to
 * the registry, which reports rejected names in the plugin's status.
 *
 * <p>The closures are adapted to the handler interfaces so that the plugin boundary never throws:
 * <ul>
 *     <li>download: the closure's delegate is the {@code ResponseCtx}, so {@code status = 200} works. Groovy
 *         truth of the return value decides whether the request was handled. A failure is reported and
 *         declines (normal Generic handling); a {@link CancelException} sets its status and message on the
 *         response and counts as handled.</li>
 *     <li>upload: an {@link UploadDecision} is used as is; {@code null} accepts. Any other value or a failure is
 *         reported and fails closed, rejecting the upload with 500, because a handler that enforces an upload
 *         policy must not be bypassed by its own bug. A {@link CancelException} rejects with its status (403 when
 *         it is not 4xx/5xx).</li>
 *     <li>index: failures, including cancellation, are reported; there is no request left to fail.</li>
 *     <li>api: the closure gets the {@link org.artifactory.addon.plugin.pkgtype.PluginApiRequest} and returns a
 *         response, converted by {@link ApiResponseConversion}. It fails closed: a failure or a return value that
 *         is not a valid response is reported and answered with a generic 500. A {@link CancelException} answers
 *         with its status (500 when it is not 4xx/5xx) and message as plain text.</li>
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

    /** Sets the HTTP API handler. */
    public void api(Closure<?> closure) {
        builder.api(apiHandler(closure));
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

    private PluginApiHandler apiHandler(Closure<?> closure) {
        String what = "packageType(" + name + ").api";
        return request -> apiResponse(HookInvoker.invoke(closure, null, request), what);
    }

    private PluginApiResponse apiResponse(HookOutcome outcome, String what) {
        return switch (outcome) {
            case HookOutcome.Completed completed -> converted(ApiResponseConversion.convert(completed.value()), what);
            case HookOutcome.Failed failed -> {
                reporter.report(pluginName, what, failed.error());
                yield apiFailure();
            }
            case HookOutcome.Cancelled cancelled -> PluginApiResponse.text(
                    apiCancelStatus(cancelled.cancel().getErrorCode()), "text/plain",
                    String.valueOf(cancelled.cancel().getMessage()));
        };
    }

    private PluginApiResponse converted(ApiResponseConversion.Result result, String what) {
        if (result instanceof ApiResponseConversion.Result.Valid valid) {
            return valid.response();
        }
        reporter.report(pluginName, what,
                new IllegalStateException(((ApiResponseConversion.Result.Invalid) result).reason()));
        return apiFailure();
    }

    /** The generic 500 a failed or invalid {@code api} closure is answered with; carries no failure details. */
    static PluginApiResponse apiFailure() {
        return PluginApiResponse.text(500, "text/plain", "The repository's package type failed to handle the request");
    }

    /** @return {@code status} when it is a 4xx or 5xx, otherwise 500 */
    static int apiCancelStatus(int status) {
        return status >= 400 && status <= 599 ? status : 500;
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
                yield UploadDecision.reject(500, "Upload refused: upload handler of package type failed");
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
                "upload handler returned " + value.getClass().getName()
                        + ", expected UploadDecision; refusing the upload"));
        return UploadDecision.reject(500, "Upload refused: upload handler returned an invalid decision");
    }

    /** @return {@code status} when it is a valid reject status (4xx or 5xx), otherwise 403 */
    static int rejectStatus(int status) {
        return status >= 400 && status <= 599 ? status : 403;
    }
}
