/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security.access.embedded.core;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

/**
 * The token-signing material of embedded access: a PKCS#8-encoded private key and the PEM of the self-signed
 * certificate that Artifactory publishes as {@code root.crt}. This type performs no cryptography; it only holds and
 * serialises the bytes. Arrays are copied in and out, and {@link #toString()} never shows the key.
 * <p>
 * {@link #serialise()} produces {@code v1:<base64 key>:<base64 PEM>}, which contains the private key in clear, so
 * the caller must encrypt it (for example with the master encryption wrapper) before storing it.
 */
public final class SigningKeys {

    private static final String VERSION = "v1";
    private static final String SEPARATOR = ":";

    private final byte[] privateKeyPkcs8;
    private final String certificatePem;

    private SigningKeys(byte[] privateKeyPkcs8, String certificatePem) {
        this.privateKeyPkcs8 = privateKeyPkcs8;
        this.certificatePem = certificatePem;
    }

    /**
     * Creates the holder after checking that both parts are present.
     *
     * @param privateKeyPkcs8 the PKCS#8 DER encoding of the private key, copied
     * @param certificatePem  the PEM encoding of the certificate
     * @return the keys, or {@code Refused} if a part is missing or empty
     */
    public static EaOutcome<SigningKeys> of(byte[] privateKeyPkcs8, String certificatePem) {
        if (privateKeyPkcs8 == null || privateKeyPkcs8.length == 0) {
            return EaOutcome.refused("signing key is empty");
        }
        if (certificatePem == null || !certificatePem.contains("-----BEGIN CERTIFICATE-----")) {
            return EaOutcome.refused("signing certificate is not PEM");
        }
        return EaOutcome.ok(new SigningKeys(privateKeyPkcs8.clone(), certificatePem));
    }

    /**
     * Parses the output of {@link #serialise()}.
     *
     * @param serialised the serialised form
     * @return the keys, or {@code Refused} if the text is malformed
     */
    public static EaOutcome<SigningKeys> parse(String serialised) {
        if (serialised == null) {
            return EaOutcome.refused("signing keys are missing");
        }
        String[] parts = serialised.split(SEPARATOR, -1);
        if (parts.length != 3 || !VERSION.equals(parts[0])) {
            return EaOutcome.refused("signing keys have an unknown format");
        }
        try {
            Base64.Decoder decoder = Base64.getDecoder();
            return of(decoder.decode(parts[1]), new String(decoder.decode(parts[2]), StandardCharsets.US_ASCII));
        } catch (IllegalArgumentException e) {
            return EaOutcome.refused("signing keys are not valid base64");
        }
    }

    /**
     * Serialises the keys; the result holds the private key in clear and must be encrypted before storage.
     *
     * @return {@code v1:<base64 key>:<base64 PEM>}
     */
    public String serialise() {
        Base64.Encoder encoder = Base64.getEncoder();
        return VERSION + SEPARATOR + encoder.encodeToString(privateKeyPkcs8) + SEPARATOR
                + encoder.encodeToString(certificatePem.getBytes(StandardCharsets.US_ASCII));
    }

    /**
     * Returns a copy of the PKCS#8 DER encoding of the private key.
     *
     * @return the key bytes
     */
    public byte[] privateKeyPkcs8() {
        return privateKeyPkcs8.clone();
    }

    /**
     * Returns the PEM encoding of the certificate.
     *
     * @return the certificate PEM
     */
    public String certificatePem() {
        return certificatePem;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof SigningKeys other && Arrays.equals(privateKeyPkcs8, other.privateKeyPkcs8)
                && certificatePem.equals(other.certificatePem);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(privateKeyPkcs8) + Objects.hashCode(certificatePem);
    }

    @Override
    public String toString() {
        return "SigningKeys[privateKey=<redacted>, certificatePem=" + certificatePem.length() + " chars]";
    }
}
