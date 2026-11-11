/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2026 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.ui.rest.service.setmeup;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.descriptor.repo.KeyPairDescriptor;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;

/**
 * Utility for converting a configured Ed25519 key pair into the
 * {@code trusted-public-keys} line value that a Nix client pastes into its
 * {@code nix.conf}.
 * <p>
 * Nix expects {@code <alias>:<base64(raw 32-byte Ed25519 public key)>}. The
 * {@link KeyPairDescriptor} stores the public key as a PEM-wrapped SPKI
 * (X.509 SubjectPublicKeyInfo) blob, which is 12 algorithm-identifier bytes
 * followed by the 32 raw key bytes; this class extracts and re-encodes those
 * 32 bytes.
 *
 * @author Natalie Berni
 */
@Slf4j
@UtilityClass
public class NixSetMeUpUtils {

    private static final int ED25519_SPKI_HEADER_LEN = 12;
    private static final int ED25519_RAW_KEY_LEN = 32;
    private static final int ED25519_SPKI_TOTAL_LEN = ED25519_SPKI_HEADER_LEN + ED25519_RAW_KEY_LEN;

    /**
     * Returns the {@code <alias>:<base64-raw-32-bytes>} payload for the given
     * Ed25519 key pair, or {@link Optional#empty()} when the descriptor is
     * null, is not an Ed25519 key, or has an unparseable public key.
     */
    public static Optional<String> toNixTrustedPublicKey(KeyPairDescriptor keyPair) {
        if (keyPair == null
                || !KeyPairDescriptor.ED25519_TYPE.equals(keyPair.getKeyType())
                || StringUtils.isBlank(keyPair.getPublicKey())
                || StringUtils.isBlank(keyPair.getAlias())) {
            return Optional.empty();
        }
        try (PemReader reader = new PemReader(new StringReader(keyPair.getPublicKey()))) {
            PemObject pem = reader.readPemObject();
            if (pem == null) {
                log.debug("Failed to read public key PEM for key pair '{}'", keyPair.getKey());
                return Optional.empty();
            }
            byte[] spki = pem.getContent();
            if (spki.length != ED25519_SPKI_TOTAL_LEN) {
                log.debug("Unexpected SPKI length {} for key pair '{}', expected {}",
                        spki.length, keyPair.getKey(), ED25519_SPKI_TOTAL_LEN);
                return Optional.empty();
            }
            byte[] raw = Arrays.copyOfRange(spki, ED25519_SPKI_HEADER_LEN, spki.length);
            return Optional.of(keyPair.getAlias() + ":" + Base64.getEncoder().encodeToString(raw));
        } catch (IOException e) {
            log.debug("Failed to parse public key PEM for key pair '{}': {}",
                    keyPair.getKey(), e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Extracts the last path segment from a URL as a candidate repo key.
     * Returns {@code null} for blank, unparseable, or path-less URLs.
     */
    public static String extractRepoKeyFromUrl(String url) {
        if (StringUtils.isBlank(url)) {
            return null;
        }
        try {
            String path = new URI(url).getPath();
            if (StringUtils.isBlank(path) || path.equals("/")) {
                return null;
            }
            String trimmed = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
            int lastSlash = trimmed.lastIndexOf('/');
            return lastSlash >= 0 ? trimmed.substring(lastSlash + 1) : trimmed;
        } catch (URISyntaxException e) {
            log.debug("Could not parse remote URL '{}': {}", url, e.getMessage());
            return null;
        }
    }
}
