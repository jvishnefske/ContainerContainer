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

package org.artifactory.addon.plugin.wasm;

import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * An effect a module asked for in a reply, as decoded by {@link AbiCodec}. Nothing is performed until
 * {@link EffectPolicy} turned it into an {@link Authorized} effect.
 */
public sealed interface Effect {

    /** {@code http_get}: fetch {@code url} with the given request headers. */
    record HttpGet(String url, Map<String, String> headers) implements Effect {
        public HttpGet {
            Objects.requireNonNull(url, "url");
            headers = Map.copyOf(headers);
        }
    }

    /** {@code mint_access_token}: an Access token for the caller, living at most {@code expiresIn} seconds. */
    record MintAccessToken(long expiresIn, OptionalLong notAfter) implements Effect {
        public MintAccessToken {
            Objects.requireNonNull(notAfter, "notAfter");
        }
    }

    /** An effect {@link EffectPolicy} allowed, ready for the host to perform. */
    sealed interface Authorized {
    }

    /** An allowed {@code http_get}: an {@code https} URI to an allowlisted host and port, without user info. */
    record AllowedGet(URI uri, Map<String, String> headers) implements Authorized {
        public AllowedGet {
            Objects.requireNonNull(uri, "uri");
            headers = Map.copyOf(headers);
        }
    }

    /** An allowed {@code mint_access_token}, in an execution call. */
    record AllowedMint(long expiresIn, OptionalLong notAfter) implements Authorized {
        public AllowedMint {
            Objects.requireNonNull(notAfter, "notAfter");
        }
    }
}
