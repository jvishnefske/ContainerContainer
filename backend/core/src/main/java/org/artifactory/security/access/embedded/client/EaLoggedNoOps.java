/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security.access.embedded.client;

import io.grpc.ManagedChannel;
import org.jfrog.access.common.ResourceType;
import org.jfrog.access.common.ServiceId;
import org.jfrog.access.router.registration.RouterRegistrationData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * The surfaces that talk to other platform services and have nothing to do in a standalone server:
 * {@code registry()} (service registration), {@code notifications()} (resource deletion notices) and
 * {@code router()} (router registration). Their calls succeed and are logged at DEBUG; methods not listed here
 * (for example joining a registry) fail closed.
 */
final class EaLoggedNoOps {

    private static final Logger log = LoggerFactory.getLogger(EaLoggedNoOps.class);

    private EaLoggedNoOps() {
    }

    /** The {@code registry()} delegate. */
    static final class Registry {
        /**
         * Ignores a registration.
         *
         * @param serviceId the service
         */
        public void registerServiceId(ServiceId serviceId) {
            log.debug("Embedded access: ignoring service registration of {}", serviceId);
        }

        /**
         * Ignores registrations.
         *
         * @param serviceIds the services
         */
        public void registerAndReplaceServiceIds(List<ServiceId> serviceIds) {
            log.debug("Embedded access: ignoring service registration of {}", serviceIds);
        }

        /**
         * Ignores an unregistration.
         *
         * @param serviceId the service
         */
        public void unregisterServiceId(ServiceId serviceId) {
            log.debug("Embedded access: ignoring service unregistration of {}", serviceId);
        }
    }

    /** The {@code notifications()} delegate. */
    static final class Notifications {
        /**
         * Ignores a deletion notice.
         *
         * @param type the resource type
         * @param name the resource
         */
        public void deleteResource(ResourceType type, String name) {
            log.debug("Embedded access: ignoring deletion notice for {} {}", type, name);
        }
    }

    /** The {@code router()} delegate. */
    static final class Router {
        /**
         * Ignores a router registration.
         *
         * @param data the registration
         */
        public void registerService(RouterRegistrationData data) {
            log.debug("Embedded access: no router to register with");
        }

        /**
         * Ignores a router unregistration.
         *
         * @param data the registration
         */
        public void unregisterService(RouterRegistrationData data) {
            log.debug("Embedded access: no router to unregister from");
        }

        /**
         * Ignores a channel.
         *
         * @param channel the channel
         */
        public void setChannel(ManagedChannel channel) {
            log.debug("Embedded access: no router channel needed");
        }
    }
}
