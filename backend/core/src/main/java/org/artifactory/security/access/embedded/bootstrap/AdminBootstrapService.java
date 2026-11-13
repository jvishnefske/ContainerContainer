/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security.access.embedded.bootstrap;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.security.AccessLogger;
import org.artifactory.security.access.embedded.core.BootstrapRules;
import org.artifactory.security.access.embedded.core.BootstrapRules.BootstrapState;
import org.artifactory.security.access.embedded.core.BootstrapRules.ClaimVerdict;
import org.artifactory.security.access.embedded.core.BootstrapRules.Claimed;
import org.artifactory.security.access.embedded.core.BootstrapRules.Generate;
import org.artifactory.security.access.embedded.core.BootstrapRules.GenerateReason;
import org.artifactory.security.access.embedded.core.BootstrapRules.Keep;
import org.artifactory.security.access.embedded.core.BootstrapRules.Source;
import org.artifactory.security.access.embedded.core.BootstrapRules.StartDecision;
import org.artifactory.security.access.embedded.core.BootstrapRules.Unclaimed;
import org.artifactory.security.access.embedded.core.BootstrapRules.UseSupplied;
import org.artifactory.security.access.embedded.core.ClaimRateLimit;
import org.artifactory.security.access.embedded.core.EaOutcome;
import org.artifactory.security.access.embedded.core.EaOutcome.Done;
import org.artifactory.security.access.embedded.core.EaOutcome.Ok;
import org.artifactory.security.access.embedded.core.PasswordPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * The imperative shell of the initial-administrator bootstrap (IA1 to IA4). Decisions are made by
 * {@link BootstrapRules}, {@link ClaimRateLimit} and {@link PasswordPolicy}; this class only reads the environment
 * and files, writes the token file, and talks to the {@link BootstrapStatePort}.
 * <p>
 * <b>On start</b> ({@link #start}): unless the server is already claimed, the file named by
 * {@value #TOKEN_FILE_ENV} is read (at most {@value #MAX_SUPPLIED_FILE_BYTES} bytes, surrounding white space
 * stripped). {@link BootstrapRules#decideOnStart} then chooses:
 * <ul>
 *     <li>{@code Generate}: 32 {@link SecureRandom} bytes, base64url without padding, are written to
 *     {@value #TOKEN_FILE_NAME} in the security directory ({@code $JFROG_HOME/artifactory/var/etc/security/}) as a
 *     temporary file created with POSIX mode 0600, the state holding only the token's hash is stored, and the file
 *     is then moved into place atomically. Only the path is logged.</li>
 *     <li>{@code UseSupplied}: the hash of the operator's token is stored; a stale generated file is deleted.</li>
 *     <li>{@code Keep}: nothing changes, except that a stored generated token whose file has gone missing is
 *     replaced, since nobody could claim with it.</li>
 * </ul>
 * A {@code Refused} decision (for example an expired operator-supplied token that is still supplied) is returned
 * to the caller; the server then stays unclaimed and the setup filter keeps every endpoint closed.
 * <p>
 * <b>Claim</b> ({@link #claim}): rate limit, password policy (username {@value #ADMIN_USERNAME}), token check,
 * hash, then {@link BootstrapStatePort#claim} in one transaction. A generated token file is then deleted; an
 * operator-supplied file is left alone (its token no longer works). The claim is audited through
 * {@link AccessLogger} with the client address. Callers see only the closed {@link ClaimResult}; the precise
 * {@link ClaimVerdict} is logged server-side only.
 * <p>
 * <b>Shared state.</b> The service is a singleton used by concurrent request threads, so two fields are shared:
 * {@link #isClaimed()} reads a {@code volatile} flag (the setup filter's one read per request), and the rate-limit
 * state is an {@link AtomicReference} replaced by compare-and-swap with the result of the pure
 * {@link ClaimRateLimit#check}. That reference is the minimal shared handle: the limit must count attempts across
 * threads, the state it holds is immutable, and nothing else is mutated in place.
 * <p>
 * The service is not a Spring bean by itself: it is wired by the embedded access factory, which builds it with a
 * port over the embedded store ({@link #forHome}), calls {@link #start}, and publishes it in the application
 * context, where the web layer looks it up by type.
 */
public final class AdminBootstrapService {

    /** The environment variable naming an operator-supplied setup-token file. */
    public static final String TOKEN_FILE_ENV = "INITIAL_ADMIN_TOKEN_FILE";
    /** The name of the generated setup-token file in the security directory. */
    public static final String TOKEN_FILE_NAME = "initial-admin.token";
    /** The largest accepted supplied token file, in bytes. */
    public static final int MAX_SUPPLIED_FILE_BYTES = 1024;
    /** The built-in administrator claimed by the setup token. */
    public static final String ADMIN_USERNAME = "admin";

    /** The closed set of answers to a claim request. */
    public enum ClaimResult {
        /** The server is now claimed and the administrator password is set. */
        CLAIMED,
        /** Too many attempts from this client or in total; try later. */
        RATE_LIMITED,
        /** Refused, for any reason: the caller learns nothing more. */
        REFUSED
    }

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapService.class);
    private static final int TOKEN_BYTES = 32;
    private static final int MAX_RATE_LIMIT_CAS_ATTEMPTS = 64;
    private static final String UNKNOWN_CLIENT = "unknown";

    private final BootstrapStatePort port;
    private final PasswordHasher hasher;
    private final PasswordPolicy policy;
    private final ClaimRateLimit rateLimit;
    private final Path securityDir;
    private final Duration tokenTtl;
    private final UnaryOperator<String> env;
    private final SecureRandom random;
    private final Consumer<String> audit;
    private final AtomicReference<ClaimRateLimit.State> rateState =
            new AtomicReference<>(ClaimRateLimit.State.empty());
    private volatile boolean claimed;

    private AdminBootstrapService(Builder b) {
        this.port = Objects.requireNonNull(b.port, "port");
        this.securityDir = Objects.requireNonNull(b.securityDir, "securityDir");
        this.hasher = b.hasher != null ? b.hasher : PasswordHasher.standard();
        this.policy = b.policy != null ? b.policy : PasswordPolicy.withBundledList().toOptional()
                .orElseThrow(() -> new IllegalStateException("the bundled common-password list is unavailable"));
        this.rateLimit = b.rateLimit != null ? b.rateLimit : ClaimRateLimit.defaults();
        this.tokenTtl = b.tokenTtl != null ? b.tokenTtl : BootstrapRules.DEFAULT_TOKEN_TTL;
        if (tokenTtl.isNegative() || tokenTtl.isZero()) {
            throw new IllegalArgumentException("tokenTtl must be positive");
        }
        this.env = b.env != null ? b.env : System::getenv;
        this.random = b.random != null ? b.random : new SecureRandom();
        this.audit = b.audit != null ? b.audit : AccessLogger::initialAdminClaimed;
    }

    /**
     * Starts a builder.
     *
     * @param port        the state port
     * @param securityDir the directory of the generated token file
     * @return the builder
     */
    public static Builder builder(BootstrapStatePort port, Path securityDir) {
        return new Builder(port, securityDir);
    }

    /**
     * Builds the production service for an Artifactory home: the token file goes to {@link
     * ArtifactoryHome#getSecurityDir()}, and the token lifetime is {@link
     * ConstantValues#securityInitialAdminTokenTtlHours} (the default 24 hours when not positive).
     *
     * @param home the Artifactory home
     * @param port the state port over the embedded store
     * @return the service, not yet started
     */
    public static AdminBootstrapService forHome(ArtifactoryHome home, BootstrapStatePort port) {
        long hours = ConstantValues.securityInitialAdminTokenTtlHours.getLong(home);
        Duration ttl = hours > 0 ? Duration.ofHours(hours) : BootstrapRules.DEFAULT_TOKEN_TTL;
        if (hours <= 0) {
            log.warn("{} must be positive; using {} hours",
                    ConstantValues.securityInitialAdminTokenTtlHours.getPropertyName(), ttl.toHours());
        }
        return builder(port, home.getSecurityDir().toPath()).tokenTtl(ttl).build();
    }

    /**
     * Finds the service published in an application context. Used by the web layer, which must fail closed when
     * this returns empty (the context is not ready, or embedded access is not wired).
     *
     * @param context the application context, or {@code null}
     * @return the service, or empty if the context is absent or holds none
     */
    public static Optional<AdminBootstrapService> lookup(ArtifactoryContext context) {
        if (context == null) {
            return Optional.empty();
        }
        try {
            Map<String, AdminBootstrapService> beans = context.beansForType(AdminBootstrapService.class);
            return beans == null ? Optional.empty() : beans.values().stream().filter(Objects::nonNull).findFirst();
        } catch (RuntimeException e) {
            log.debug("Initial admin bootstrap service is not available", e);
            return Optional.empty();
        }
    }

    /**
     * Tells whether the server has been claimed. This is one volatile read, cheap enough for every request.
     *
     * @return {@code true} once a claim succeeded or a claimed state was found on start
     */
    public boolean isClaimed() {
        return claimed;
    }

    /**
     * Returns the path of the generated token file.
     *
     * @return the path, whether or not the file exists
     */
    public Path tokenFile() {
        return securityDir.resolve(TOKEN_FILE_NAME);
    }

    /**
     * Applies the start-up decision, see the class description.
     *
     * @param now the current time
     * @return {@code Done}, or {@code Refused} with a reason for the operator
     */
    public EaOutcome<Done> start(Instant now) {
        EaOutcome<Optional<BootstrapState>> loaded = port.load();
        if (!(loaded instanceof Ok<Optional<BootstrapState>> ok)) {
            return loaded.flatMap(ignored -> EaOutcome.done());
        }
        Optional<BootstrapState> stored = ok.value();
        if (BootstrapRules.isClaimed(stored)) {
            claimed = true;
            return EaOutcome.done();
        }
        EaOutcome<Optional<String>> supplied = readSupplied();
        EaOutcome<Done> result = supplied
                .flatMap(token -> BootstrapRules.decideOnStart(stored, token, now, tokenTtl))
                .flatMap(decision -> apply(decision, stored, now));
        if (!result.isOk()) {
            log.error("Initial admin setup could not start: {}", describe(result));
        }
        return result;
    }

    /**
     * Attempts to claim the server.
     *
     * @param token    the presented setup token, or {@code null}
     * @param password the chosen administrator password, or {@code null}
     * @param clientIp the client address, used for rate limiting and audit; {@code null} counts as unknown
     * @param now      the current time
     * @return the closed result
     */
    public ClaimResult claim(String token, String password, String clientIp, Instant now) {
        String client = clientIp == null || clientIp.isBlank() ? UNKNOWN_CLIENT : clientIp;
        ClaimRateLimit.Decision decision = countAttempt(client, now);
        if (decision != ClaimRateLimit.Decision.ALLOWED) {
            log.warn("Initial admin claim from {} rate limited: {}", client, decision);
            return ClaimResult.RATE_LIMITED;
        }
        if (claimed) {
            log.warn("Initial admin claim from {} refused: {}", client, ClaimVerdict.ALREADY_CLAIMED);
            return ClaimResult.REFUSED;
        }
        EaOutcome<Done> policyVerdict = policy.validate(ADMIN_USERNAME, password);
        if (!policyVerdict.isOk()) {
            log.warn("Initial admin claim from {} refused: password does not meet the policy", client);
            return ClaimResult.REFUSED;
        }
        Optional<BootstrapState> loaded = port.load().toOptional().flatMap(s -> s);
        if (loaded.isEmpty()) {
            log.warn("Initial admin claim from {} refused: no readable bootstrap state", client);
            return ClaimResult.REFUSED;
        }
        BootstrapState state = loaded.get();
        ClaimVerdict verdict = BootstrapRules.classifyClaim(state, token, now);
        Optional<Claimed> newState = BootstrapRules.attemptClaim(state, token, now, client).toOptional();
        if (newState.isEmpty()) {
            log.warn("Initial admin claim from {} refused: {}", client, verdict);
            return ClaimResult.REFUSED;
        }
        Optional<String> hash = hasher.encode(password).toOptional();
        if (hash.isEmpty()) {
            log.warn("Initial admin claim from {} refused: password cannot be hashed", client);
            return ClaimResult.REFUSED;
        }
        EaOutcome<Done> stored = port.claim(newState.get(), BootstrapRules.serialise(state), hash.get());
        if (!stored.isOk()) {
            log.warn("Initial admin claim from {} refused: the claim could not be stored ({})", client,
                    describe(stored));
            return ClaimResult.REFUSED;
        }
        claimed = true;
        if (state instanceof Unclaimed unclaimed && unclaimed.source() == Source.GENERATED) {
            deleteTokenFile();
        }
        audit.accept(client);
        log.info("Initial admin claimed from {}", client);
        return ClaimResult.CLAIMED;
    }

    /**
     * Tells whether a {@code Keep} of a generated token must be replaced because its file is missing. Pure.
     *
     * @param decision   the start decision
     * @param fileExists whether the generated token file exists
     * @return {@code true} if a new token must be generated
     */
    static boolean mustReplaceMissingFile(StartDecision decision, boolean fileExists) {
        return decision instanceof Keep keep && keep.state() instanceof Unclaimed u
                && u.source() == Source.GENERATED && !fileExists;
    }

    private EaOutcome<Done> apply(StartDecision decision, Optional<BootstrapState> stored, Instant now) {
        Optional<String> prior = stored.map(BootstrapRules::serialise);
        if (mustReplaceMissingFile(decision, Files.exists(tokenFile()))) {
            log.warn("Initial admin setup token file {} is missing; generating a new token", tokenFile());
            return generate(prior, now, null);
        }
        return switch (decision) {
            case Keep keep -> {
                if (keep.state() instanceof Claimed) {
                    claimed = true;
                } else {
                    logSetupRequired(keep.state());
                }
                yield EaOutcome.done();
            }
            case Generate generate -> generate(prior, now, generate.reason());
            case UseSupplied use -> port.save(use.state(), prior).map(done -> {
                deleteStaleGeneratedFile(stored);
                log.warn("Initial admin setup required: using the setup token supplied through {} (expires {})",
                        TOKEN_FILE_ENV, use.state().expiresAt());
                return done;
            });
        };
    }

    private EaOutcome<Done> generate(Optional<String> prior, Instant now, GenerateReason reason) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        EaOutcome<Unclaimed> state = BootstrapRules.unclaimed(token, now, tokenTtl, Source.GENERATED);
        if (!(state instanceof Ok<Unclaimed> okState)) {
            return state.flatMap(ignored -> EaOutcome.done());
        }
        Path temp;
        try {
            Files.createDirectories(securityDir);
            temp = Files.createTempFile(securityDir, TOKEN_FILE_NAME + ".", ".tmp",
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            Files.writeString(temp, token + "\n", StandardCharsets.US_ASCII);
        } catch (IOException | UnsupportedOperationException e) {
            return EaOutcome.refused("cannot write the setup token file in " + securityDir + ": " + e);
        }
        EaOutcome<Done> saved = port.save(okState.value(), prior);
        if (!saved.isOk()) {
            deleteQuietly(temp);
            return saved;
        }
        try {
            Files.move(temp, tokenFile(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            deleteQuietly(temp);
            return EaOutcome.refused("cannot move the setup token file to " + tokenFile() + ": " + e);
        }
        log.warn("Initial admin setup required: a new setup token was written to {} (reason {}, expires {})",
                tokenFile(), reason == null ? "TOKEN_FILE_MISSING" : reason, okState.value().expiresAt());
        return EaOutcome.done();
    }

    private EaOutcome<Optional<String>> readSupplied() {
        String location = env.apply(TOKEN_FILE_ENV);
        if (location == null || location.isBlank()) {
            return EaOutcome.ok(Optional.empty());
        }
        Path file = Path.of(location);
        byte[] content;
        try (InputStream in = Files.newInputStream(file)) {
            content = in.readNBytes(MAX_SUPPLIED_FILE_BYTES + 1);
        } catch (IOException | RuntimeException e) {
            return EaOutcome.refused("cannot read " + TOKEN_FILE_ENV + " file " + file + ": " + e);
        }
        if (content.length > MAX_SUPPLIED_FILE_BYTES) {
            return EaOutcome.refused(TOKEN_FILE_ENV + " file " + file + " is larger than "
                    + MAX_SUPPLIED_FILE_BYTES + " bytes");
        }
        Optional<String> token = BootstrapRules.normaliseSupplied(new String(content, StandardCharsets.UTF_8));
        if (token.isEmpty()) {
            return EaOutcome.refused(TOKEN_FILE_ENV + " file " + file + " is empty");
        }
        return EaOutcome.ok(token);
    }

    private ClaimRateLimit.Decision countAttempt(String client, Instant now) {
        for (int i = 0; i < MAX_RATE_LIMIT_CAS_ATTEMPTS; i++) {
            ClaimRateLimit.State prior = rateState.get();
            ClaimRateLimit.Result result = rateLimit.check(prior, client, now);
            if (rateState.compareAndSet(prior, result.state())) {
                return result.decision();
            }
        }
        // Extreme contention: refuse rather than spin.
        return ClaimRateLimit.Decision.GLOBAL_LIMITED;
    }

    private void logSetupRequired(BootstrapState state) {
        if (state instanceof Unclaimed u) {
            log.warn("Initial admin setup required: the setup token {} (expires {})",
                    u.source() == Source.GENERATED ? "is in " + tokenFile() : "was supplied through "
                            + TOKEN_FILE_ENV, u.expiresAt());
        }
    }

    private void deleteStaleGeneratedFile(Optional<BootstrapState> stored) {
        if (stored.filter(s -> s instanceof Unclaimed u && u.source() == Source.GENERATED).isPresent()) {
            deleteTokenFile();
        }
    }

    private void deleteTokenFile() {
        try {
            Files.deleteIfExists(tokenFile());
        } catch (IOException e) {
            log.error("Could not delete the used setup token file {}; delete it by hand", tokenFile(), e);
        }
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Could not delete temporary file {}", file, e);
        }
    }

    private static String describe(EaOutcome<?> outcome) {
        return outcome.fold(value -> "ok", reason -> reason, op -> "unsupported: " + op);
    }

    /** Builds an {@link AdminBootstrapService}; everything except the port and directory has a default. */
    public static final class Builder {
        private final BootstrapStatePort port;
        private final Path securityDir;
        private PasswordHasher hasher;
        private PasswordPolicy policy;
        private ClaimRateLimit rateLimit;
        private Duration tokenTtl;
        private UnaryOperator<String> env;
        private SecureRandom random;
        private Consumer<String> audit;

        private Builder(BootstrapStatePort port, Path securityDir) {
            this.port = port;
            this.securityDir = securityDir;
        }

        /**
         * Sets the password hasher (default {@link PasswordHasher#standard()}).
         *
         * @param value the hasher
         * @return this builder
         */
        public Builder hasher(PasswordHasher value) {
            this.hasher = value;
            return this;
        }

        /**
         * Sets the password policy (default {@link PasswordPolicy#withBundledList()}).
         *
         * @param value the policy
         * @return this builder
         */
        public Builder policy(PasswordPolicy value) {
            this.policy = value;
            return this;
        }

        /**
         * Sets the claim rate limit (default {@link ClaimRateLimit#defaults()}).
         *
         * @param value the limiter
         * @return this builder
         */
        public Builder rateLimit(ClaimRateLimit value) {
            this.rateLimit = value;
            return this;
        }

        /**
         * Sets the setup-token lifetime (default {@link BootstrapRules#DEFAULT_TOKEN_TTL}).
         *
         * @param value the lifetime, positive
         * @return this builder
         */
        public Builder tokenTtl(Duration value) {
            this.tokenTtl = value;
            return this;
        }

        /**
         * Sets the environment lookup (default {@link System#getenv(String)}).
         *
         * @param value maps a variable name to its value or {@code null}
         * @return this builder
         */
        public Builder env(UnaryOperator<String> value) {
            this.env = value;
            return this;
        }

        /**
         * Sets the random source of generated tokens (default a new {@link SecureRandom}).
         *
         * @param value the random source
         * @return this builder
         */
        public Builder random(SecureRandom value) {
            this.random = value;
            return this;
        }

        /**
         * Sets the audit sink of successful claims (default {@link AccessLogger#initialAdminClaimed}).
         *
         * @param value receives the client address of each successful claim
         * @return this builder
         */
        public Builder audit(Consumer<String> value) {
            this.audit = value;
            return this;
        }

        /**
         * Builds the service.
         *
         * @return the service, not yet started
         * @throws IllegalArgumentException if the token lifetime is not positive
         * @throws IllegalStateException    if the default policy's bundled list cannot be read
         */
        public AdminBootstrapService build() {
            return new AdminBootstrapService(this);
        }
    }
}
