/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security.access.embedded.client;

import org.jfrog.common.platform.subscription.SubscriptionType;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The {@code cachedLicense()} surface. Artifactory OSS has no license, and Artifactory only pushes one to Access
 * when a license is installed; embedded access enforces nothing, so the calls only record what was sent in
 * {@code ea_kv} ({@value #LICENSE_KEY}, {@value #SUBSCRIPTION_KEY}) for diagnostics. The license key is stored as
 * given; it is not a secret Artifactory OSS has.
 */
final class EaLicenseClient {

    static final String LICENSE_KEY = "license";
    static final String SUBSCRIPTION_KEY = "license.subscription_type";

    private final EaKvDocs docs;

    EaLicenseClient(EaKvDocs docs) {
        this.docs = docs;
    }

    /**
     * Records a license sent by Artifactory.
     *
     * @param serverName the server name
     * @param licenseKey the license key
     */
    public void add(String serverName, String licenseKey) {
        Map<String, String> doc = new LinkedHashMap<>();
        doc.put("server-name", serverName);
        doc.put("key", licenseKey);
        docs.write(LICENSE_KEY, doc);
    }

    /**
     * Records the subscription type.
     *
     * @param type the subscription type
     */
    public void setSubscriptionType(SubscriptionType type) {
        docs.write(SUBSCRIPTION_KEY, String.valueOf(type));
    }
}
