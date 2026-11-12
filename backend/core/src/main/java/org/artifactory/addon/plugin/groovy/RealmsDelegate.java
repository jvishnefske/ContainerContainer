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

package org.artifactory.addon.plugin.groovy;

import groovy.lang.Closure;
import groovy.lang.MissingMethodException;

import java.util.Optional;

/**
 * Delegate of a {@code realms} block. Each call {@code name { ... }} or
 * {@code name(autoCreateUsers: true, realmPolicy: 'additive') { ... }} declares a {@link PluginRealm}; its body is
 * evaluated against a {@link Body} that collects the {@code authenticate}, {@code bearer} and {@code userExists}
 * closures. Declaring a closure twice, an unknown option, an invalid name or a realm without the closures its
 * policy needs fails the load of the plugin.
 */
public final class RealmsDelegate {

    private final PluginDefinitionBuilder definition;

    RealmsDelegate(PluginDefinitionBuilder definition) {
        this.definition = definition;
    }

    /** Groovy dispatch entry point for realm declarations. */
    public Object methodMissing(String name, Object args) {
        Optional<Closure<?>> block = HookBlockDelegate.closureArgument(args);
        if (block.isEmpty()) {
            throw new MissingMethodException(name, RealmsDelegate.class, HookBlockDelegate.asArray(args));
        }
        Body body = new Body(name);
        PluginScript.runDelegated(block.get(), body);
        definition.addRealm(PluginRealm.fromOptions(definition.pluginName(), name,
                HookBlockDelegate.optionsArgument(args), body.authenticate, body.bearer, body.userExists));
        return null;
    }

    /** Delegate of one realm's body; lives only while the body is evaluated at load time. */
    public static final class Body {

        private final String declaredRealmName;
        private Optional<Closure<?>> authenticate = Optional.empty();
        private Optional<Closure<?>> bearer = Optional.empty();
        private Optional<Closure<?>> userExists = Optional.empty();

        Body(String declaredRealmName) {
            this.declaredRealmName = declaredRealmName;
        }

        /** Declares the closure offered Basic credentials, {@code (username, credentials) -> RealmResult}. */
        public void authenticate(Closure<?> closure) {
            authenticate = once("authenticate", authenticate, closure);
        }

        /** Declares the closure offered Bearer tokens, {@code (token) -> RealmResult}. */
        public void bearer(Closure<?> closure) {
            bearer = once("bearer", bearer, closure);
        }

        /** Declares the closure answering whether a user exists in the realm, {@code (username) -> boolean}. */
        public void userExists(Closure<?> closure) {
            userExists = once("userExists", userExists, closure);
        }

        private Optional<Closure<?>> once(String what, Optional<Closure<?>> current, Closure<?> closure) {
            if (current.isPresent()) {
                throw new IllegalArgumentException("Realm '" + declaredRealmName + "' declares " + what + " twice");
            }
            return Optional.of(closure);
        }
    }
}
