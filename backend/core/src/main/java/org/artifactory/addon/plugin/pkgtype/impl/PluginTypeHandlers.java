/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.addon.plugin.pkgtype.impl;

import org.apache.http.HttpStatus;
import org.artifactory.addon.plugin.ResponseCtx;
import org.artifactory.addon.plugin.pkgtype.PluginDownloadHandler;
import org.artifactory.addon.plugin.pkgtype.PluginPackageType;
import org.artifactory.addon.plugin.pkgtype.UploadDecision;
import org.artifactory.repo.RepoPath;
import org.artifactory.request.Request;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Calls plugin handlers across the plugin boundary and turns every outcome, including a thrown exception, into a
 * value, so that request code never sees an exception from plugin code. The outcome types are closed sets; the
 * request path decides what to do with each one (logging, serving, rejecting) in its own thin shell.
 */
public final class PluginTypeHandlers {

    private PluginTypeHandlers() {
    }

    /** Outcome of offering a GET/HEAD request to a type's download handler. */
    public sealed interface DownloadOutcome {
        /** The type has no download handler, or the handler returned {@code false}. */
        record Declined() implements DownloadOutcome {
        }

        /** The handler filled {@code response} and returned {@code true}. */
        record Handled(ResponseCtx response) implements DownloadOutcome {
            public Handled {
                Objects.requireNonNull(response, "response");
            }
        }

        /** The handler threw; normal handling applies. */
        record Failed(RuntimeException error) implements DownloadOutcome {
            public Failed {
                Objects.requireNonNull(error, "error");
            }
        }
    }

    /**
     * Offers a download to {@code type}'s handler. The handler receives a fresh {@link ResponseCtx} whose status is
     * preset to 200, so a handler that only sets a message or stream serves it with 200.
     *
     * @param newResponse creates the response context handed to the handler (production: {@code ResponseCtx::new})
     */
    public static DownloadOutcome offerDownload(PluginPackageType type, Request request, RepoPath repoPath,
            Supplier<ResponseCtx> newResponse) {
        Optional<PluginDownloadHandler> handler = type.download();
        if (handler.isEmpty()) {
            return new DownloadOutcome.Declined();
        }
        try {
            ResponseCtx response = newResponse.get();
            response.setStatus(HttpStatus.SC_OK);
            return handler.get().handle(request, repoPath, response)
                    ? new DownloadOutcome.Handled(response)
                    : new DownloadOutcome.Declined();
        } catch (RuntimeException e) {
            return new DownloadOutcome.Failed(e);
        }
    }

    /** Outcome of asking a type's upload handler for a decision. */
    public sealed interface UploadOutcome {
        /** The handler decided (a type with no upload handler decides {@link UploadDecision.Accept}). */
        record Decided(UploadDecision decision) implements UploadOutcome {
            public Decided {
                Objects.requireNonNull(decision, "decision");
            }
        }

        /** The handler threw or returned {@code null}; the upload proceeds unchanged. */
        record Failed(RuntimeException error) implements UploadOutcome {
            public Failed {
                Objects.requireNonNull(error, "error");
            }
        }
    }

    /** Asks {@code type}'s upload handler what to do with a PUT to {@code repoPath}. */
    public static UploadOutcome decideUpload(PluginPackageType type, Request request, RepoPath repoPath) {
        if (type.upload().isEmpty()) {
            return new UploadOutcome.Decided(UploadDecision.accept());
        }
        try {
            UploadDecision decision = type.upload().get().decide(request, repoPath);
            return decision == null
                    ? new UploadOutcome.Failed(new IllegalStateException("upload handler returned null"))
                    : new UploadOutcome.Decided(decision);
        } catch (RuntimeException e) {
            return new UploadOutcome.Failed(e);
        }
    }

    /** What the upload path does once a decision is known. */
    public sealed interface UploadRoute {
        /** Store at {@code path}, which equals the requested path unless the handler rewrote it. */
        record Store(RepoPath path) implements UploadRoute {
            public Store {
                Objects.requireNonNull(path, "path");
            }
        }

        /** Store nothing and respond with {@code status} and {@code message}. */
        record Refuse(int status, String message) implements UploadRoute {
            public Refuse {
                Objects.requireNonNull(message, "message");
            }
        }
    }

    /**
     * Maps a decision to a route. Pure. A rewrite into a different repository than {@code requested} is refused
     * with 400, because the target repository's own checks (permissions, type) were made for {@code requested}.
     */
    public static UploadRoute route(UploadDecision decision, RepoPath requested) {
        return switch (decision) {
            case UploadDecision.Accept accept -> new UploadRoute.Store(requested);
            case UploadDecision.Reject reject -> new UploadRoute.Refuse(reject.status(), reject.message());
            case UploadDecision.Rewrite rewrite -> rewrite.target().getRepoKey().equals(requested.getRepoKey())
                    ? new UploadRoute.Store(rewrite.target())
                    : new UploadRoute.Refuse(HttpStatus.SC_BAD_REQUEST, "Plugin package type rewrote upload of " +
                    requested.getId() + " to another repository (" + rewrite.target().getId() + ")");
        };
    }
}
