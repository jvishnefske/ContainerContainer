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

package org.artifactory.security;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * P2: the plugin realm provider sits after the external directories and before Access user/password authentication
 * and the internal database in the {@code authenticationManager} chain; the other providers keep their stock order.
 */
class PluginRealmProviderOrderTest {

    @Test
    void pluginRealmProviderComesBeforeStoredPasswordProviders() throws Exception {
        List<String> providers = new ArrayList<>();
        try (InputStream in = getClass().getResourceAsStream("/META-INF/spring/security.xml")) {
            assertNotNull(in);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document doc = factory.newDocumentBuilder().parse(in);
            NodeList beans = doc.getElementsByTagName("bean");
            for (int i = 0; i < beans.getLength(); i++) {
                Element bean = (Element) beans.item(i);
                if ("authenticationManager".equals(bean.getAttribute("id"))) {
                    NodeList refs = bean.getElementsByTagName("ref");
                    for (int j = 0; j < refs.getLength(); j++) {
                        providers.add(((Element) refs.item(j)).getAttribute("bean"));
                    }
                }
            }
        }
        assertEquals(List.of("ldapAuthenticationProvider", "crowdAuthenticationProviderAdapter",
                "pluginRealmAuthenticationProvider", "accessAuthenticationProvider",
                "gitHubEnterpriseAuthenticationProvider", "dbAuthenticationProvider",
                "rememberMeAuthenticationProvider", "accessInternalAuthenticationProvider"), providers);
    }
}
