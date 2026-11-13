/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security.access.embedded.client;

import com.fasterxml.jackson.databind.JavaType;
import org.jfrog.access.model.Realm;
import org.jfrog.access.rest.system.AnonymousConfig;
import org.jfrog.access.rest.system.BasicAuthenticationEnabledConfig;
import org.jfrog.access.rest.system.EnabledRealmsConfig;
import org.jfrog.access.rest.system.LdapConfig;
import org.jfrog.access.rest.system.MTlsConfigurationModel;
import org.jfrog.access.rest.system.PasswordEncryptionPolicyConfig;
import org.jfrog.access.rest.system.PasswordPolicyEnabledConfig;
import org.jfrog.access.rest.system.UserLockPolicyConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The {@code config()} surface: Access's security configuration, one JSON document in {@code ea_kv}
 * ({@value #KEY}) shaped like the map Artifactory sends ({@code security.anonymous-access-enabled},
 * {@code security.authentication.*}, {@code security.user-lock-policy.*}, {@code licenses}).
 * <p>
 * {@link #updateConfigurationModel} deep-merges into the document; a {@code null} value deletes the entry. Getters
 * read it with these defaults: anonymous access off, basic authentication on, password encryption
 * {@value #DEFAULT_PASSWORD_ENCRYPTION}, the IA6 password policy on, lock policy {@value #DEFAULT_LOCK_ATTEMPTS}
 * attempts and no password expiry, mTLS off. Enabled realms are the stored list if Artifactory ever set one;
 * otherwise {@code internal}, plus {@code ldap} and {@code crowd} when an enabled LDAP or Crowd setting exists
 * (SAML and OAuth settings are stored but inert, so their realms are never enabled).
 */
final class EaConfigClient {

    /** The {@code ea_kv} key of the security configuration document. */
    static final String KEY = "access.config";

    static final String DEFAULT_PASSWORD_ENCRYPTION = "supported";
    static final int DEFAULT_LOCK_ATTEMPTS = 5;
    static final long DEFAULT_MAX_LOGIN_DELAY_MILLIS = 5000L;
    static final long DEFAULT_BASE_LOGIN_DELAY_MILLIS = 500L;
    static final int DEFAULT_MAX_LOGIN_DELAY_INCORRECT_ATTEMPTS = 10;

    private static final JavaType MAP = EaKvDocs.type(Map.class);

    private final EaKvDocs docs;
    private final Supplier<List<String>> derivedRealms;
    private final Supplier<LdapConfig> ldapConfig;

    EaConfigClient(EaKvDocs docs, Supplier<List<String>> derivedRealms, Supplier<LdapConfig> ldapConfig) {
        this.docs = docs;
        this.derivedRealms = derivedRealms;
        this.ldapConfig = ldapConfig;
    }

    /**
     * Deep-merges a partial configuration; {@code null} values delete.
     *
     * @param update the partial configuration
     */
    public void updateConfigurationModel(Map<String, Object> update) {
        docs.<Map<String, Object>>update(KEY, MAP, current -> merge(current == null ? Map.of() : current, update));
    }

    /**
     * Returns whether anonymous access is enabled.
     *
     * @return the setting
     */
    public AnonymousConfig getAnonymousAccessEnabled() {
        return new AnonymousConfig(bool(security(), "anonymous-access-enabled", false));
    }

    /**
     * Returns the enabled realms.
     *
     * @return the realms
     */
    public EnabledRealmsConfig getEnabledRealms() {
        Object stored = authentication().get("enabled-realms");
        List<String> realms = new ArrayList<>();
        if (stored instanceof List<?> list) {
            list.forEach(r -> realms.add(String.valueOf(r)));
        } else {
            realms.addAll(derivedRealms.get());
        }
        return EnabledRealmsConfig.builder().enabledRealms(realms).build();
    }

    /**
     * Tells whether a realm is enabled.
     *
     * @param realm the realm
     * @return {@code true} if listed by {@link #getEnabledRealms()}
     */
    public boolean isRealmEnabled(Realm realm) {
        return getEnabledRealms().getEnabledRealms().contains(realm.getName());
    }

    /**
     * Returns whether basic authentication is enabled.
     *
     * @return the setting
     */
    public BasicAuthenticationEnabledConfig getBasicAuthenticationEnabled() {
        return BasicAuthenticationEnabledConfig.builder()
                .enabled(bool(authentication(), "basic-authentication-enabled", true)).build();
    }

    /**
     * Returns the password encryption policy.
     *
     * @return the setting
     */
    public PasswordEncryptionPolicyConfig getPasswordEncryptionPolicy() {
        Object value = authentication().get("password-encryption");
        return PasswordEncryptionPolicyConfig.builder()
                .passwordEncryption(value == null ? DEFAULT_PASSWORD_ENCRYPTION : String.valueOf(value)).build();
    }

    /**
     * Returns whether the password policy is enforced.
     *
     * @return the setting, on by default
     */
    public PasswordPolicyEnabledConfig getPasswordPolicyEnabled() {
        return new PasswordPolicyEnabledConfig(bool(section(security(), "password-policy"), "enabled", true));
    }

    /**
     * Returns the user lock policy.
     *
     * @return the policy
     */
    public UserLockPolicyConfig getUserLockPolicy() {
        Map<String, Object> p = section(security(), "user-lock-policy");
        return UserLockPolicyConfig.builder()
                .attempts((int) number(p, "attempts", DEFAULT_LOCK_ATTEMPTS))
                .secondsToUnlock((int) number(p, "seconds-to-unlock", 0))
                .passwordExpiryDays((int) number(p, "password-expiry-days", 0))
                .adminPasswordExpirable(bool(p, "admin-password-expirable", false))
                .maxLoginDelayMillis(number(p, "max-login-delay-millis", DEFAULT_MAX_LOGIN_DELAY_MILLIS))
                .baseLoginDelayMillis(number(p, "base-login-delay-millis", DEFAULT_BASE_LOGIN_DELAY_MILLIS))
                .maxLoginDelayIncorrectAttempts((int) number(p, "max-login-delay-incorrect-attempts",
                        DEFAULT_MAX_LOGIN_DELAY_INCORRECT_ATTEMPTS))
                .clientLoginSuspensionEnabled(bool(p, "client-login-suspension-enabled", false))
                .build();
    }

    /**
     * Returns the mTLS configuration: always disabled.
     *
     * @return a disabled configuration
     */
    public MTlsConfigurationModel getMTlsConfiguration() {
        return new MTlsConfigurationModel();
    }

    /**
     * Returns the LDAP connection configuration kept by the auth-settings surface.
     *
     * @return the configuration, or {@code null} if none was stored
     */
    public LdapConfig getLdapConfig() {
        return ldapConfig.get();
    }

    /**
     * Answers that cache prefetch is enabled; there is no remote cache to prefetch.
     *
     * @return {@code false}
     */
    public boolean getCachePrefetchDisabled() {
        return false;
    }

    // ---- internals ----

    private Map<String, Object> document() {
        Map<String, Object> doc = docs.<Map<String, Object>>read(KEY, MAP).orElse(null);
        return doc == null ? Map.of() : doc;
    }

    private Map<String, Object> security() {
        return section(document(), "security");
    }

    private Map<String, Object> authentication() {
        return section(security(), "authentication");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Map<String, Object> parent, String key) {
        Object value = parent.get(key);
        return value instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    private static boolean bool(Map<String, Object> map, String key, boolean defaultValue) {
        Object value = map.get(key);
        if (value instanceof Boolean b) {
            return b;
        }
        return value == null ? defaultValue : Boolean.parseBoolean(String.valueOf(value));
    }

    private static long number(Map<String, Object> map, String key, long defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return value == null ? defaultValue : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Deep-merges {@code update} into {@code base}: nested maps merge, {@code null} deletes, anything else
     * replaces.
     *
     * @param base   the current document
     * @param update the partial update
     * @return a new merged map
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> merge(Map<String, Object> base, Map<String, Object> update) {
        Map<String, Object> out = new LinkedHashMap<>(base);
        update.forEach((key, value) -> {
            if (value == null) {
                out.remove(key);
            } else if (value instanceof Map<?, ?> m && out.get(key) instanceof Map<?, ?> existing) {
                out.put(key, merge((Map<String, Object>) existing, (Map<String, Object>) m));
            } else {
                out.put(key, value);
            }
        });
        return out;
    }
}
