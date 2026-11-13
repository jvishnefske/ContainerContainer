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
import org.jfrog.access.client.model.CrowdSetting;
import org.jfrog.access.client.model.HttpSsoSetting;
import org.jfrog.access.client.model.LdapGroupSetting;
import org.jfrog.access.client.model.LdapSetting;
import org.jfrog.access.client.model.OAuthGeneralSettings;
import org.jfrog.access.client.model.OAuthSetting;
import org.jfrog.access.client.model.ProxySetting;
import org.jfrog.access.client.model.SamlEncryptedAssertion;
import org.jfrog.access.client.model.SamlSetting;
import org.jfrog.access.rest.system.LdapConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * The {@code cachedAuthSettingClient(..)} surface: external-provider settings, kept as JSON documents in
 * {@code ea_kv} under {@value #PREFIX}{@code *} and encrypted with the encryption wrapper the caller passes (they
 * hold LDAP manager and Crowd application passwords).
 * <p>
 * LDAP and Crowd settings are used by Artifactory's own LDAP and Crowd providers, so they take effect. SAML, OAuth
 * and HTTP SSO settings are stored and returned so the UI round-trips them, but embedded access runs no SAML or
 * OAuth flow: they are <em>inert</em>, and their realms are never reported enabled.
 * <p>
 * {@code subscribeTo*Changes} callbacks run on the writing thread after a write through any settings surface of the
 * same client; callbacks are kept in a {@link Listeners} registry shared by those surfaces.
 */
final class EaAuthSettingsClient {

    private static final Logger log = LoggerFactory.getLogger(EaAuthSettingsClient.class);

    static final String PREFIX = "auth.";
    static final String LDAP = PREFIX + "ldap.settings";
    static final String LDAP_GROUPS = PREFIX + "ldap.group_settings";
    static final String LDAP_CONFIG = PREFIX + "ldap.config";
    static final String CROWD = PREFIX + "crowd";
    static final String PROXY = PREFIX + "proxy";
    static final String HTTP_SSO = PREFIX + "http_sso";
    static final String SAML = PREFIX + "saml";
    static final String SAML_ENCRYPTED_ASSERTION = PREFIX + "saml.encrypted_assertion";
    static final String OAUTH = PREFIX + "oauth.settings";
    static final String OAUTH_GENERAL = PREFIX + "oauth.general";

    private final EaKvDocs docs;
    private final Listeners listeners;

    EaAuthSettingsClient(EaKvDocs docs, Listeners listeners) {
        this.docs = docs;
        this.listeners = listeners;
    }

    /** Change callbacks by topic. The one mutable registry of the settings surfaces; thread-safe. */
    static final class Listeners {
        private final Map<String, List<Runnable>> byTopic = new ConcurrentHashMap<>();

        void add(String topic, Runnable callback) {
            byTopic.computeIfAbsent(topic, t -> new CopyOnWriteArrayList<>()).add(Objects.requireNonNull(callback));
        }

        void fire(String topic) {
            for (Runnable r : byTopic.getOrDefault(topic, List.of())) {
                try {
                    r.run();
                } catch (RuntimeException e) {
                    log.warn("Settings change callback for {} failed", topic, e);
                }
            }
        }
    }

    // ---- LDAP ----

    /**
     * Inserts or replaces an LDAP setting by key.
     *
     * @param setting the setting
     */
    public void upsertLdapSetting(LdapSetting setting) {
        upsertLdapSetting(List.of(setting));
    }

    /**
     * Inserts or replaces LDAP settings by key.
     *
     * @param settings the settings
     */
    public void upsertLdapSetting(Collection<LdapSetting> settings) {
        upsertAll(LDAP, LdapSetting.class, LdapSetting::getKey, settings);
        listeners.fire(LDAP);
    }

    /**
     * Reads an LDAP setting.
     *
     * @param key the key
     * @return the setting, or empty
     */
    public Optional<LdapSetting> getLdapSetting(String key) {
        return find(LDAP, LdapSetting.class, LdapSetting::getKey, key);
    }

    /**
     * Lists LDAP settings.
     *
     * @return the settings
     */
    public List<LdapSetting> getAllLdapSettings() {
        return list(LDAP, LdapSetting.class);
    }

    /**
     * Deletes an LDAP setting.
     *
     * @param key the key
     */
    public void deleteLdapSetting(String key) {
        remove(LDAP, LdapSetting.class, LdapSetting::getKey, key);
        listeners.fire(LDAP);
    }

    /**
     * Registers an LDAP change callback.
     *
     * @param callback the callback
     */
    public void subscribeToLdapChanges(Runnable callback) {
        listeners.add(LDAP, callback);
    }

    /**
     * Inserts or replaces an LDAP group setting by name.
     *
     * @param setting the setting
     */
    public void upsertLdapGroupSetting(LdapGroupSetting setting) {
        upsertLdapGroupSettings(List.of(setting));
    }

    /**
     * Inserts or replaces LDAP group settings by name.
     *
     * @param settings the settings
     */
    public void upsertLdapGroupSettings(Collection<LdapGroupSetting> settings) {
        upsertAll(LDAP_GROUPS, LdapGroupSetting.class, LdapGroupSetting::getName, settings);
        listeners.fire(LDAP_GROUPS);
    }

    /**
     * Reads an LDAP group setting.
     *
     * @param name the name
     * @return the setting, or empty
     */
    public Optional<LdapGroupSetting> getLdapGroupSetting(String name) {
        return find(LDAP_GROUPS, LdapGroupSetting.class, LdapGroupSetting::getName, name);
    }

    /**
     * Lists LDAP group settings.
     *
     * @return the settings
     */
    public List<LdapGroupSetting> getAllLdapGroupSettings() {
        return list(LDAP_GROUPS, LdapGroupSetting.class);
    }

    /**
     * Deletes an LDAP group setting.
     *
     * @param name the name
     */
    public void deleteLdapGroupSetting(String name) {
        remove(LDAP_GROUPS, LdapGroupSetting.class, LdapGroupSetting::getName, name);
        listeners.fire(LDAP_GROUPS);
    }

    /**
     * Registers an LDAP group change callback.
     *
     * @param callback the callback
     */
    public void subscribeToLdapGroupChanges(Runnable callback) {
        listeners.add(LDAP_GROUPS, callback);
    }

    /**
     * Stores the LDAP connection configuration.
     *
     * @param config the configuration
     */
    public void updateLdapConfig(LdapConfig config) {
        docs.write(LDAP_CONFIG, config);
        listeners.fire(LDAP);
    }

    /**
     * Reads the LDAP connection configuration.
     *
     * @return the configuration, or {@code null}
     */
    public LdapConfig getLdapConfig() {
        return docs.<LdapConfig>read(LDAP_CONFIG, EaKvDocs.type(LdapConfig.class)).orElse(null);
    }

    // ---- Crowd, proxy, HTTP SSO ----

    /**
     * Stores the Crowd setting.
     *
     * @param setting the setting
     */
    public void upsertCrowdSetting(CrowdSetting setting) {
        docs.write(CROWD, setting);
        listeners.fire(CROWD);
    }

    /**
     * Reads the Crowd setting.
     *
     * @return the setting, or empty
     */
    public Optional<CrowdSetting> getCrowdSetting() {
        return docs.read(CROWD, EaKvDocs.type(CrowdSetting.class));
    }

    /**
     * Reads the Crowd setting; the flag is ignored.
     *
     * @param flag ignored
     * @return the setting, or empty
     */
    public Optional<CrowdSetting> getCrowdSetting(boolean flag) {
        return getCrowdSetting();
    }

    /**
     * Registers a Crowd change callback.
     *
     * @param callback the callback
     */
    public void subscribeToCrowdChanges(Runnable callback) {
        listeners.add(CROWD, callback);
    }

    /**
     * Stores the default proxy setting.
     *
     * @param setting the setting
     */
    public void upsertProxySetting(ProxySetting setting) {
        docs.write(PROXY, setting);
    }

    /**
     * Stores the HTTP SSO setting.
     *
     * @param setting the setting
     */
    public void upsertHttpSsoSetting(HttpSsoSetting setting) {
        docs.write(HTTP_SSO, setting);
        listeners.fire(HTTP_SSO);
    }

    /**
     * Reads the HTTP SSO setting.
     *
     * @return the setting, or empty
     */
    public Optional<HttpSsoSetting> getHttpSsoSetting() {
        return docs.read(HTTP_SSO, EaKvDocs.type(HttpSsoSetting.class));
    }

    /**
     * Registers an HTTP SSO change callback.
     *
     * @param callback the callback
     */
    public void subscribeToHttpSsoChanges(Runnable callback) {
        listeners.add(HTTP_SSO, callback);
    }

    // ---- SAML and OAuth: stored, inert ----

    /**
     * Stores the SAML setting (inert).
     *
     * @param setting the setting
     */
    public void upsertSamlSetting(SamlSetting setting) {
        docs.write(SAML, setting);
        listeners.fire(SAML);
    }

    /**
     * Reads the SAML setting.
     *
     * @return the setting, or empty
     */
    public Optional<SamlSetting> getSamlSetting() {
        return docs.read(SAML, EaKvDocs.type(SamlSetting.class));
    }

    /**
     * Reads the SAML setting; the flag is ignored.
     *
     * @param flag ignored
     * @return the setting, or empty
     */
    public Optional<SamlSetting> getSamlSetting(boolean flag) {
        return getSamlSetting();
    }

    /**
     * Registers a SAML change callback.
     *
     * @param callback the callback
     */
    public void subscribeToSamlChanges(Runnable callback) {
        listeners.add(SAML, callback);
    }

    /**
     * Stores the SAML encrypted-assertion setting (inert).
     *
     * @param setting the setting
     */
    public void upsertSamlEncryptedAssertion(SamlEncryptedAssertion setting) {
        docs.write(SAML_ENCRYPTED_ASSERTION, setting);
    }

    /**
     * Lists OAuth provider settings (inert).
     *
     * @return the settings
     */
    public List<OAuthSetting> getAllOAuthSettings() {
        return list(OAUTH, OAuthSetting.class);
    }

    /**
     * Reads an OAuth provider setting.
     *
     * @param name the provider name
     * @return the setting, or empty
     */
    public Optional<OAuthSetting> getOAuthSetting(String name) {
        return find(OAUTH, OAuthSetting.class, OAuthSetting::getName, name);
    }

    /**
     * Inserts or replaces an OAuth provider setting by name (inert).
     *
     * @param setting the setting
     */
    public void upsertOAuthSettings(OAuthSetting setting) {
        upsertOAuthSettings(List.of(setting));
    }

    /**
     * Inserts or replaces OAuth provider settings by name (inert).
     *
     * @param settings the settings
     */
    public void upsertOAuthSettings(Collection<OAuthSetting> settings) {
        upsertAll(OAUTH, OAuthSetting.class, OAuthSetting::getName, settings);
        listeners.fire(OAUTH);
    }

    /**
     * Deletes an OAuth provider setting.
     *
     * @param name the provider name
     */
    public void deleteOAuthSettings(String name) {
        remove(OAUTH, OAuthSetting.class, OAuthSetting::getName, name);
        listeners.fire(OAUTH);
    }

    /**
     * Reads the general OAuth setting.
     *
     * @return the setting, or empty
     */
    public Optional<OAuthGeneralSettings> getOAuthGeneralSettings() {
        return docs.read(OAUTH_GENERAL, EaKvDocs.type(OAuthGeneralSettings.class));
    }

    /**
     * Stores the general OAuth setting (inert).
     *
     * @param setting the setting
     */
    public void upsertOAuthGeneralSettings(OAuthGeneralSettings setting) {
        docs.write(OAUTH_GENERAL, setting);
        listeners.fire(OAUTH);
    }

    /**
     * Registers an OAuth change callback.
     *
     * @param callback the callback
     */
    public void subscribeToOAuthChanges(Runnable callback) {
        listeners.add(OAUTH, callback);
    }

    /**
     * Tells whether an external provider that embedded access supports is enabled: an enabled LDAP setting or an
     * enabled Crowd setting. Inert SAML and OAuth settings do not count.
     *
     * @return {@code true} if one is enabled
     */
    public boolean isAnyExternalProviderEnabled() {
        return !enabledRealms().isEmpty();
    }

    /**
     * Returns the external realms the stored settings enable: {@code ldap} and/or {@code crowd}.
     *
     * @return the realm names
     */
    List<String> enabledRealms() {
        List<String> out = new ArrayList<>();
        if (getAllLdapSettings().stream().anyMatch(LdapSetting::isEnabled)) {
            out.add("ldap");
        }
        if (getCrowdSetting().map(CrowdSetting::isEnable).orElse(false)) {
            out.add("crowd");
        }
        return out;
    }

    // ---- internals ----

    private <T> List<T> list(String key, Class<T> type) {
        Optional<List<T>> value = docs.read(key, EaKvDocs.listOf(type));
        return value.map(ArrayList::new).map(l -> (List<T>) l).orElseGet(ArrayList::new);
    }

    private <T> Optional<T> find(String key, Class<T> type, Function<T, String> id, String wanted) {
        return list(key, type).stream().filter(s -> Objects.equals(id.apply(s), wanted)).findFirst();
    }

    private <T> void upsertAll(String key, Class<T> type, Function<T, String> id, Collection<T> settings) {
        JavaType listType = EaKvDocs.listOf(type);
        docs.<List<T>>update(key, listType, current -> {
            List<T> out = new ArrayList<>(current == null ? List.of() : current);
            for (T s : settings) {
                out.removeIf(existing -> Objects.equals(id.apply(existing), id.apply(s)));
                out.add(s);
            }
            return out;
        });
    }

    private <T> void remove(String key, Class<T> type, Function<T, String> id, String wanted) {
        docs.<List<T>>update(key, EaKvDocs.listOf(type), current -> {
            List<T> out = new ArrayList<>(current == null ? List.of() : current);
            out.removeIf(existing -> Objects.equals(id.apply(existing), wanted));
            return out;
        });
    }
}
