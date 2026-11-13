/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/**
 * The client side of embedded access: {@link EmbeddedAccessClient}, an implementation of JFrog Access's public
 * {@code AccessClient} interface over the embedded store, so every Artifactory call site keeps working without an
 * Access server.
 * <p>
 * Each Access sub-client is a {@link FailClosed} proxy over a package-private adapter ({@code EaTokenClient},
 * {@code EaAuthClient}, {@code EaGrpcAuthClient}, {@code EaUsersClient}, {@code EaGroupsClient},
 * {@code EaPermissionsClient}, {@code EaGrpcPermissionsClient}, {@code EaPlatformConfigClient},
 * {@code EaConfigClient}, {@code EaAuthSettingsClient}, {@code EaSystemClient}, {@code EaLicenseClient},
 * {@code EaProjectClient}, and the logged no-ops in {@code EaLoggedNoOps}). Methods an adapter does not implement,
 * and whole surfaces embedded access does not offer, throw {@link EmbeddedAccessUnsupportedException}.
 * Conversions between the store's records and the Access models are pure functions in {@code EaModels};
 * failures map to the HTTP-shaped exceptions Artifactory already handles in {@code EaErrors}.
 * <p>
 * Inputs the caller supplies: the store and change bus, Artifactory's service id, the cleartext
 * {@link org.artifactory.security.access.embedded.core.SigningKeys} (see {@link EaKeyMaterial} to create them), an
 * {@link EaPasswordVerifier}, and a clock.
 */
package org.artifactory.security.access.embedded.client;
