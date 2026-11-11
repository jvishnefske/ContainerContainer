package com.jfrog.build.test.util;

import com.jfrog.build.entity.BuildEntity;
import com.jfrog.build.entity.BuildPromotionStatus;
import com.jfrog.build.entity.BuildProperty;
import lombok.NonNull;
import lombok.experimental.UtilityClass;

import java.util.HashSet;
import java.util.List;

import static com.jfrog.build.acl.util.BuildConstants.BUILDINFO_REPO_KEY;
import static java.lang.System.nanoTime;
import static org.apache.commons.lang3.RandomStringUtils.secure;

@UtilityClass
public class TestBuildsUtils {

    @NonNull
    public static BuildEntity createBuild11() {
        BuildEntity build = createBuildEntity(11L, "c1", "1", nanoTime() - 20000L);
        build.setProperties(new HashSet<>());
        build.setPromotions(new HashSet<>());
        return build;
    }

    @NonNull
    public static BuildEntity createBuild12() {
        BuildEntity build = createBuildEntity(12L, "c2", "1", nanoTime() - 20000L);
        setBuildProperties(build);
        build.setPromotions(new HashSet<>());
        return build;
    }

    @NonNull
    public static BuildEntity createBuild13() {
        BuildEntity build = createBuildEntity(13L, "c2", "13", nanoTime() - 20000L);
        build.setProperties(new HashSet<>());
        addBuildPromotions(build);
        return build;
    }

    @NonNull
    public static BuildEntity createBuild14() {
        BuildEntity build = createBuildEntity(14L, "c2", "14", nanoTime() - 20000L);
        setBuildProperties(build);
        addBuildPromotions(build);
        return build;
    }

    @NonNull
    public static BuildEntity createBuild15() {
        BuildEntity build = createBuildEntity(15L, "c5", "1", nanoTime() - 20000L);
        setLongBuildProperty(build);
        build.setPromotions(new HashSet<>());
        return build;
    }

    @NonNull
    public static BuildEntity createBuild16() {
        BuildEntity build = createBuildEntity(16L, "c2", "13", nanoTime() - 20000L);
        build.setProperties(new HashSet<>());
        addBuildPromotions(build);
        return build;
    }

    @NonNull
    public static BuildEntity createBuild103() {
        BuildEntity build = createBuildEntity(103L, "ba", "2", 1349111111111L);
        build.setProperties(new HashSet<>());
        build.setPromotions(new HashSet<>());
        return build;
    }

    private static void setBuildProperties(@NonNull BuildEntity build) {
        build.setProperties(List.of(
                new BuildProperty(
                        21L + build.getBuildId(),
                        build.getBuildId(),
                        "start",
                        "123"
                ),
                new BuildProperty(
                        22L + build.getBuildId(),
                        build.getBuildId(),
                        "status",
                        "perfect"
                )
        ));
    }

    private static void setLongBuildProperty(@NonNull BuildEntity build) {
        build.setProperties(List.of(
                new BuildProperty(
                        23L + build.getBuildId(),
                        build.getBuildId(),
                        "long-property",
                        secure().nextAscii(4020)
                )
        ));
    }

    private static void addBuildPromotions(@NonNull BuildEntity build) {
        build.setPromotions(List.of(
                new BuildPromotionStatus(
                        build.getBuildId(),
                        build.getCreated() + 6000L,
                        "promoter",
                        "promoted",
                        "public",
                        "Promoted",
                        null
                ),
                new BuildPromotionStatus(
                        build.getBuildId(),
                        build.getCreated() + 3000L,
                        build.getCreatedBy(),
                        "staged",
                        null,
                        null,
                        null
                )
        ));
    }

    @NonNull
    public static BuildEntity createBuildEntity(
            @NonNull Long buildId,
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull Long buildDate) {
        return new BuildEntity(
                buildId,
                buildName,
                buildNumber,
                buildDate,
                "ci-url",
                buildDate,
                "created-by",
                buildDate,
                "modified-by",
                BUILDINFO_REPO_KEY
        );
    }
}
