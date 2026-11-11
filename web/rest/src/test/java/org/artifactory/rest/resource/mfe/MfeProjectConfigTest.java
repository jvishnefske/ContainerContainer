package org.artifactory.rest.resource.mfe;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MfeProjectConfigTest {

    @Test
    void getJsPath_shouldReturnWebappJs_whenBasePathEmpty() {
        MfeProjectConfig config = MfeProjectConfig.builder()
                .basePath("")
                .build();

        String result = config.getJsPath("/webapp");

        assertEquals("/webapp/js", result);
    }

    @Test
    void getJsPath_shouldReturnBasePathJs_whenBasePathSet() {
        MfeProjectConfig config = MfeProjectConfig.builder()
                .basePath("/rtui")
                .build();

        String result = config.getJsPath("/webapp");

        assertEquals("/webapp/rtui/js", result);
    }

    @Test
    void getImgPath_shouldReturnWebappImg_whenBasePathEmpty() {
        MfeProjectConfig config = MfeProjectConfig.builder()
                .basePath("")
                .build();

        String result = config.getImgPath("/webapp");

        assertEquals("/webapp/img", result);
    }

    @Test
    void getImgPath_shouldReturnBasePathImg_whenBasePathSet() {
        MfeProjectConfig config = MfeProjectConfig.builder()
                .basePath("/rtui")
                .build();

        String result = config.getImgPath("/webapp");

        assertEquals("/webapp/rtui/img", result);
    }

    @Test
    void getMigratedRoutesPath_shouldReturnNull_whenFlagIsFalse() {
        MfeProjectConfig config = MfeProjectConfig.builder()
                .basePath("/rtui")
                .hasMigratedRoutes(false)
                .build();

        String result = config.getMigratedRoutesPath();

        assertNull(result);
    }

    @Test
    void getMigratedRoutesPath_shouldReturnPath_whenFlagIsTrue() {
        MfeProjectConfig config = MfeProjectConfig.builder()
                .basePath("/rtui")
                .hasMigratedRoutes(true)
                .build();

        String result = config.getMigratedRoutesPath();

        assertEquals("/rtui/migrated-routes.json", result);
    }

    @Test
    void getAssetsPath_shouldReturnWebappAssets_whenBasePathEmpty() {
        MfeProjectConfig config = MfeProjectConfig.builder()
                .basePath("")
                .build();

        String result = config.getAssetsPath("/webapp");

        assertEquals("/webapp/assets", result);
    }

    @Test
    void getAssetsPath_shouldReturnBasePathAssets_whenBasePathSet() {
        MfeProjectConfig config = MfeProjectConfig.builder()
                .basePath("/rtui")
                .build();

        String result = config.getAssetsPath("/webapp");

        assertEquals("/webapp/rtui/assets", result);
    }

    @Test
    void getJsPath_shouldNormalizeBasePath_whenBasePathHasNoLeadingSlash() {
        MfeProjectConfig config = MfeProjectConfig.builder()
                .basePath("rtui")
                .build();

        String result = config.getJsPath("/webapp");

        assertEquals("/webapp/rtui/js", result);
    }
}
