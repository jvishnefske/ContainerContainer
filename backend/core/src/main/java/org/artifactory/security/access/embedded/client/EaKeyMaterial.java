/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security.access.embedded.client;

import org.artifactory.security.access.embedded.core.EaOutcome;
import org.artifactory.security.access.embedded.core.SigningKeys;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.jfrog.access.common.ServiceId;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/**
 * Creates and reads the RSA signing material of embedded access.
 * <p>
 * {@link #generate} makes an RSA-2048 key and a self-signed certificate whose common name is
 * {@link ServiceId#ROOT_CRT_CN_PREFIX} followed by the Access service id, which is how Access names its root
 * certificate, so {@link ServiceId#fromCnName} reads it back. It returns cleartext {@link SigningKeys}: the caller
 * encrypts them before storing. {@link #parse} turns stored keys back into JCA objects for signing and verifying.
 */
public final class EaKeyMaterial {

    /** The RSA modulus size. */
    public static final int KEY_BITS = 2048;

    /** How long a generated certificate is valid. */
    public static final Duration VALIDITY = Duration.ofDays(3650);

    /** How far before "now" a generated certificate's validity starts, to absorb clock skew. */
    public static final Duration BACKDATE = Duration.ofDays(1);

    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";
    private static final int PEM_LINE = 64;

    private EaKeyMaterial() {
    }

    /** A parsed private key and certificate. */
    public record Parsed(PrivateKey privateKey, X509Certificate certificate) {
    }

    /**
     * Generates a key pair and a self-signed certificate.
     *
     * @param accessServiceId the Access service id ({@code jfac@<id>}) to name in the certificate
     * @param clock           the clock that sets the validity window
     * @return the new keys, or {@code Refused} if the JCA providers fail
     */
    public static EaOutcome<SigningKeys> generate(ServiceId accessServiceId, Clock clock) {
        try {
            SecureRandom random = new SecureRandom();
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(KEY_BITS, random);
            KeyPair pair = generator.generateKeyPair();
            Instant now = clock.instant();
            X500Name name = new X500NameBuilder(BCStyle.INSTANCE)
                    .addRDN(BCStyle.CN, ServiceId.ROOT_CRT_CN_PREFIX + accessServiceId.getFormattedName())
                    .build();
            X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(name,
                    new BigInteger(127, random).add(BigInteger.ONE), Date.from(now.minus(BACKDATE)),
                    Date.from(now.plus(VALIDITY)), name, pair.getPublic());
            builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
            builder.addExtension(Extension.keyUsage, true,
                    new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyCertSign));
            ContentSigner signer = new JcaContentSignerBuilder(SIGNATURE_ALGORITHM).build(pair.getPrivate());
            X509Certificate certificate = new JcaX509CertificateConverter().getCertificate(builder.build(signer));
            certificate.verify(pair.getPublic());
            return SigningKeys.of(pair.getPrivate().getEncoded(), toPem(certificate.getEncoded()));
        } catch (Exception e) {
            return EaOutcome.refused("cannot generate signing keys: " + e);
        }
    }

    /**
     * Parses stored keys.
     *
     * @param keys the keys
     * @return the JCA objects, or {@code Refused} if the key is not an RSA PKCS#8 key or the PEM is not an X.509
     *         certificate for it
     */
    public static EaOutcome<Parsed> parse(SigningKeys keys) {
        try {
            PrivateKey privateKey = KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(keys.privateKeyPkcs8()));
            X509Certificate certificate = (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(
                            keys.certificatePem().getBytes(StandardCharsets.US_ASCII)));
            if (!(privateKey instanceof RSAPrivateKey rsa) || !(certificate.getPublicKey()
                    instanceof java.security.interfaces.RSAPublicKey pub)
                    || !rsa.getModulus().equals(pub.getModulus())) {
                return EaOutcome.refused("signing key and certificate do not match");
            }
            return EaOutcome.ok(new Parsed(privateKey, certificate));
        } catch (GeneralSecurityException | ClassCastException e) {
            return EaOutcome.refused("cannot parse signing keys: " + e.getMessage());
        }
    }

    private static String toPem(byte[] der) {
        String body = Base64.getMimeEncoder(PEM_LINE, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(der);
        return "-----BEGIN CERTIFICATE-----\n" + body + "\n-----END CERTIFICATE-----\n";
    }
}
