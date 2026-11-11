/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2019 JFrog Ltd.
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

package org.artifactory.signed.url;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.KeyLengthException;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.config.CentralConfigKey;
import org.artifactory.io.checksum.ChecksumUtils;
import org.artifactory.sapi.common.BaseSettings;
import org.artifactory.sapi.common.ExportSettings;
import org.artifactory.sapi.common.ImportSettings;
import org.artifactory.security.InternalSecurityService;
import org.artifactory.signature.IssuerModel;
import org.artifactory.signature.SignedUrlService;
import org.artifactory.spring.Reloadable;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.fs.service.ConfigsService;
import org.jfrog.common.ClockUtils;
import org.jfrog.config.Home;
import org.jfrog.security.crypto.EncryptionWrapper;
import org.jfrog.storage.binstore.exceptions.SignedUrlException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import jakarta.ws.rs.core.UriBuilder;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.regex.Pattern;

import static com.jfrog.sysconf.SysLayout.ETC_DIR;
import static com.jfrog.sysconf.SysLayout.SECURITY_DIR;
import static org.artifactory.api.rest.constant.SystemRestConstants.PATH_PARAM_SIGNATURE;
import static org.artifactory.common.config.adapter.ArtifactoryConfigurationAdapter.SIGNING_KEY_DB_CONFIG_NAME;
import static org.jfrog.common.ArgUtils.requireMatches;
import static org.jfrog.common.ArgUtils.requireNonBlank;
import static org.jfrog.common.ArgUtils.requirePositive;
import static org.jfrog.common.ArgUtils.requireSatisfies;

/**
 * @author Rotem Kfir
 */
@Service
@Reloadable(beanClass = SignedUrlService.class,
        initAfter = {InternalSecurityService.class},
        listenOn = CentralConfigKey.none)
public class SignedUrlServiceImpl implements SignedUrlService {
    private static final Logger log = LoggerFactory.getLogger(SignedUrlServiceImpl.class);
    private static final Pattern PATTERN = Pattern.compile("[^/]+(/[^/]+)+/?");
    private static final String URL_SIGNING_KEY_FILE = "url.signing.key";
    private static final String PATH_CHECKSUM_CLAIM = "sum";
    public static final String INTERNAL_REPO = "internal_repo";
    private static final String UI_REQUEST_CLAIM = "ui.request";

    @Autowired
    private InternalSecurityService securityService;
    @Autowired
    private ConfigsService configsService;
    @Autowired
    CentralConfigService centralConfigService;

    @Override
    public void init() {
        try {
            if (getSigningKey() == null) {
                createAndStoreSigningKey();
            }
        } catch (Exception e) {
            log.error("Failed to initialize URL signing key", e);
            throw e;
        }
    }

    @Override
    public void createAndStoreSigningKey() {
        byte[] signingKey = generateRandomBytes();
        setSigningKey(signingKey);
    }

    @Override
    public void setSigningKey(byte[] signingKey) {
        try {
            String encryptedConfig =
                    encryptConfig(Hex.encodeHexString(signingKey), ContextHelper.get().getArtifactoryHome());
            configsService.addOrUpdateConfig(SIGNING_KEY_DB_CONFIG_NAME, encryptedConfig, System.currentTimeMillis());
        } catch (Exception e) {
            log.warn("Unable to set a URL signing key, reason: " + e.getMessage());
            log.debug("Unable to set a URL signing key", e);
            throw e;
        }
    }

    @Override
    @Nullable
    public byte[] getSigningKey() {
        String config = configsService.getConfig(SIGNING_KEY_DB_CONFIG_NAME);
        if (StringUtils.isBlank(config)) {
            return null;
        }
        try {
            String dec = decryptConfig(config, ContextHelper.get().getArtifactoryHome());
            return Hex.decodeHex(dec.toCharArray());
        } catch (DecoderException e) {
            log.warn("Unable to get URL signing key, reason: " + e.getMessage());
            log.debug("Unable to get URL signing key", e);
            throw new RuntimeException(e);
        } catch (Exception e) {
            log.warn("Unable to get URL signing key, reason: " + e.getMessage());
            log.debug("Unable to get URL signing key", e);
            throw e;
        }
    }

    @Nonnull
    @Override
    public String createSignedUrl(String serverUrl, @Nonnull String path, Long validForSeconds, boolean isUiRequest) {
        return createSignedUrl(serverUrl, path, null, validForSeconds, isUiRequest);
    }

    @Nonnull
    @Override
    public String createSignedUrl(@Nonnull String serverUrl, @Nonnull String path,
            @Nullable String internalRepoKey, @Nullable Long validForSeconds, boolean useOriginPrincipal) {
        // Assert validity
        requireValidPath(path);
        requireValidExpiry(validForSeconds);
        long validFor = validForSeconds != null ? validForSeconds : ConstantValues.signedUrlValidForSeconds.getLong();

        return UriBuilder.fromPath(serverUrl)
                .path(path)
                .queryParam(PATH_PARAM_SIGNATURE, generateToken(path, internalRepoKey, validFor, useOriginPrincipal))
                .toString();
    }

    private String generateToken(@Nonnull String path, @Nullable String internalRepoKey, long validFor,
            boolean isUiRequest) {
        JWSHeader header = new JWSHeader(JWSAlgorithm.HS256);

        JWTClaimsSet.Builder claimsSetBuilder = new JWTClaimsSet.Builder()
                .issueTime(new Date()) // Now in UTC
                .expirationTime(new Date(ClockUtils.epochMillis() + validFor * 1000))
                .issuer(securityService.currentUsername())
                .claim(PATH_CHECKSUM_CLAIM, calculateSha2(path))
                .claim(UI_REQUEST_CLAIM, String.valueOf(isUiRequest));

        if (StringUtils.isNotBlank(internalRepoKey)) {
            claimsSetBuilder.claim(INTERNAL_REPO, internalRepoKey);
        }

        SignedJWT signedJWT = new SignedJWT(header, claimsSetBuilder.build());

        byte[] signingKey = getSigningKey();
        if (signingKey == null) {
            throw new SignedUrlException("Unable to find the URL signing key");
        }

        try {
            JWSSigner signer = new MACSigner(signingKey);
            signedJWT.sign(signer);
            return signedJWT.serialize();
        } catch (KeyLengthException ke) {
            throw new SignedUrlException("The secret key length is shorter than the minimum 256-bit requirement", ke);
        } catch (JOSEException je) {
            throw new SignedUrlException("The signed token couldn't be signed", je);
        }
    }

    private void requireValidExpiry(Long validForSeconds) {
        if (validForSeconds == null) {
            return;
        }
        requirePositive(validForSeconds, "The URL should be valid for a positive number of seconds");

        Long maxValidForSeconds = centralConfigService.getDescriptor().getSignedUrlConfig().getMaxValidForSeconds();
        requireSatisfies(validForSeconds, v -> v <= maxValidForSeconds,
                "URL maximum validity period is " + maxValidForSeconds + " seconds");
    }

    @Override
    public IssuerModel verifySignedToken(@Nonnull String repoPath, @Nonnull String signedToken) {
        log.debug("Verify Signed URL for repoPath: '{}'", repoPath);

        requireValidPath(repoPath);
        requireNonBlank(signedToken, "Signature should not be blank");

        byte[] signingKey = getSigningKey();
        if (signingKey == null) {
            throw new SignedUrlException("Unable to find the URL signing key");
        }

        try {
            SignedJWT signedJWT = SignedJWT.parse(signedToken);
            // verify signature
            JWSVerifier verifier = new MACVerifier(signingKey);
            if (!signedJWT.verify(verifier)) {
                throw new SignedUrlException("Verification of the signature failed");
            }
            // verify expiration
            JWTClaimsSet claimsSet = signedJWT.getJWTClaimsSet();
            if (claimsSet.getExpirationTime() != null &&
                    claimsSet.getExpirationTime().before(new Date(ClockUtils.epochMillis()))) {
                throw new SignedUrlException("The signed token has been expired");
            }
            // verify path checksum
            if (!calculateSha2(repoPath).equals(claimsSet.getClaim(PATH_CHECKSUM_CLAIM))) {
                throw new SignedUrlException(
                        "The signed token refers to a different path than " + repoPath + " (Checksum mismatch)");
            }
            // verify internal path
            boolean isUiRequest = Boolean.valueOf((String) claimsSet.getClaim(UI_REQUEST_CLAIM));
            String tokenIssuer = claimsSet.getIssuer();
            return new IssuerModel(isUiRequest ? tokenIssuer : "signed:" + tokenIssuer,
                    claimsSet.getStringClaim(INTERNAL_REPO));
        } catch (SignedUrlException e) {
            throw e;
        } catch (Exception e) {
            throw new SignedUrlException("Couldn't parse into a valid signed token", e);
        }
    }

    private void requireValidPath(String path) {
        requireNonBlank(path, "invalid input, path cannot be empty");
        requireMatches(path, PATTERN,
                "Invalid input, path should be in the format of repoKey/path");
    }

    private String calculateSha2(String path) {
        return ChecksumUtils.calculateSha2(new ByteArrayInputStream(path.getBytes(StandardCharsets.UTF_8)));
    }

    private byte[] generateRandomBytes() {
        try {
            KeyGenerator generator = KeyGenerator.getInstance("HmacSHA256");
            generator.init(288); // 288 > 256 bits, which is the minimal requirement
            SecretKey secretKey = generator.generateKey();
            return secretKey.getEncoded();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Encrypt config using master.key
     */
    private String encryptConfig(String config, Home home) {
        EncryptionWrapper masterEncryptionWrapper = home.getMasterEncryptionWrapper();
        if (masterEncryptionWrapper == null) {
            return config;
        }
        return masterEncryptionWrapper.encryptIfNeeded(config);
    }

    /**
     * Decrypt config using master.key
     */
    private String decryptConfig(@Nullable String config, Home home) {
        EncryptionWrapper masterEncryptionWrapper = home.getMasterEncryptionWrapper();
        if (config == null || masterEncryptionWrapper == null) {
            return config;
        }
        return masterEncryptionWrapper.decryptIfNeeded(config).getDecryptedData();
    }

    @Override
    public void exportTo(ExportSettings settings) {
        MutableStatusHolder statusHolder = settings.getStatusHolder();
        statusHolder.status("Exporting URL signing key...", log);
        try {
            byte[] signingKey = getSigningKey();
            if (signingKey == null) {
                statusHolder.debug("No URL signing key found. Nothing to export.", log);
                return;
            }
            try {
                FileUtils.writeByteArrayToFile(getSigningKeyFile(settings), signingKey);
            } catch (IOException e) {
                statusHolder.error("Failed to create/write to file " + URL_SIGNING_KEY_FILE, log);
            }
        } catch (StorageException e) {
            statusHolder.error("Failed to read URL signing key from table", e, log);
        }
    }

    @Override
    public void importFrom(ImportSettings settings) {
        MutableStatusHolder statusHolder = settings.getStatusHolder();
        statusHolder.status("Importing URL signing key...", log);
        try {
            File signingKeyFile = getSigningKeyFile(settings);
            if (!signingKeyFile.exists() || !signingKeyFile.isFile()) {
                statusHolder
                        .status("Can't find " + URL_SIGNING_KEY_FILE + ". skipping URL signing key import", log);
                return;
            }
            byte[] signingKey = FileUtils.readFileToByteArray(signingKeyFile);
            setSigningKey(signingKey);
        } catch (StorageException e) {
            statusHolder.error("Failed to set URL signing key", e, log);
        } catch (IOException e) {
            statusHolder.error("Failed to read URL signing key from: " + URL_SIGNING_KEY_FILE, e, log);
        }
    }

    private File getSigningKeyFile(BaseSettings settings) {
        return Paths.get(settings.getBaseDir().getAbsolutePath(), ETC_DIR, SECURITY_DIR, URL_SIGNING_KEY_FILE).toFile();
    }
}
