package org.artifactory.spring;

import java.util.List;

public class ValidContextNames {
    public static List<String> getCommonContextNames() {
        return List.of("addons.xml", "interceptors.xml", "scheduling.xml");
    }

    public static List<String> getContextNames() {
        return List.of("applicationContext.xml", "security.xml", "interceptors.xml",
                "addons.xml", "scheduling.xml");
    }
}
