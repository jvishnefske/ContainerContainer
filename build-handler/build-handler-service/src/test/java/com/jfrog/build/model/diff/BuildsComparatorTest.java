package com.jfrog.build.model.diff;

import com.google.common.collect.Lists;
import com.jfrog.build.model.api.diff.BuildsDiffBaseFileModel;
import com.jfrog.build.model.api.diff.BuildsDiffDependencyModel;
import com.jfrog.build.model.api.diff.BuildsDiffStatus;
import com.jfrog.build.model.api.diff.BuildsDiffValue;
import com.jfrog.build.service.BuildArtifactService;
import org.jfrog.build.api.Build;
import org.jfrog.build.api.Dependency;
import org.jfrog.build.api.Module;
import org.jfrog.build.api.builder.BuildInfoBuilder;
import org.jfrog.build.api.builder.DependencyBuilder;
import org.jfrog.build.api.builder.ModuleBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.jfrog.build.model.api.diff.BuildsDiffStatus.UPDATED;
import static com.jfrog.build.model.api.diff.BuildsDiffValue.CHECKSUM;
import static com.jfrog.build.model.api.diff.BuildsDiffValue.SCOPE;
import static com.jfrog.build.model.api.diff.BuildsDiffValue.UNCHANGED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BuildsComparatorTest {

    private BuildArtifactService artifactService;

    @BeforeEach
    void beforeEach() {
        artifactService = mock(BuildArtifactService.class);
        when(artifactService.mapSha1ToRepoPathOfFileNodes(anyList())).thenReturn(Map.of());
    }

    @Test
    public void testCompareArtifacts_nullModules_returnsEmptyList() {
        Build firstBuild = new BuildInfoBuilder("b1").number("1").started("1").build();
        Build secondBuild = new BuildInfoBuilder("b2").number("2").started("2").build();
        firstBuild.setModules(null);
        secondBuild.setModules(null);

        List<BuildsDiffBaseFileModel> diffs = new BuildsComparator(artifactService)
                .compareArtifacts(firstBuild, secondBuild);

        assertTrue(diffs.isEmpty());
    }

    @Test
    public void testCompareDependencies_nullModules_returnsEmptyList() {
        Build firstBuild = new BuildInfoBuilder("b1").number("1").started("1").build();
        Build secondBuild = new BuildInfoBuilder("b2").number("2").started("2").build();
        firstBuild.setModules(null);
        secondBuild.setModules(null);

        List<BuildsDiffBaseFileModel> diffs = new BuildsComparator(artifactService)
                .compareDependencies(firstBuild, secondBuild);

        assertTrue(diffs.isEmpty());
    }

    @Test
    public void testDependenciesDifferentChecksumDifferentScope() {
        List<Dependency> dep1 = Lists.newArrayList();
        dep1.add(new DependencyBuilder().id("org.cool:cool:1.0").type("jar").sha1("1").md5("1")
                .scopes(Set.of("test"))
                .build());
        dep1.add(new DependencyBuilder().id("org.cool:cool2:1.0").type("jar").sha1("5").md5("5")
                .scopes(Set.of("test"))
                .build());
        dep1.add(new DependencyBuilder().id("org.cool:cooler:1.0").type("jar").sha1("2").md5("2").build());
        dep1.add(new DependencyBuilder().id("org.cool:coolest:1.0").type("jar").sha1("3").md5("3").build());
        dep1.add(new DependencyBuilder().id("org.cool:coolest2:1.0").type("jar").sha1("7").md5("7").build());
        Module module1 = new ModuleBuilder().id("cool-module").dependencies(dep1).build();
        Build currentBuild = new BuildInfoBuilder("b1").number("4").started("1").build();
        currentBuild.setModules(Lists.newArrayList(module1));

        List<Dependency> dep2 = Lists.newArrayList();
        dep2.add(new DependencyBuilder().id("org.cool:cool:1.0").type("jar").sha1("1").md5("1")
                .scopes(Set.of("compile"))
                .build());
        dep2.add(new DependencyBuilder().id("org.cool:cool2:1.0").type("jar").sha1("6").md5("6")
                .scopes(Set.of("compile"))
                .build());
        dep2.add(new DependencyBuilder().id("org.cool:cooler:1.0").type("jar").sha1("2").md5("2").build());
        dep2.add(new DependencyBuilder().id("org.cool:coolest:2.0").type("jar").sha1("3").md5("3").build());
        dep2.add(new DependencyBuilder().id("org.cool:coolest2:1.0").type("jar").sha1("8").md5("8").build());
        Module module2 = new ModuleBuilder().id("cool-module").dependencies(dep2).build();
        Build prevBuild = new BuildInfoBuilder("b2").number("5").started("2").build();
        prevBuild.setModules(Lists.newArrayList(module2));

        List<BuildsDiffBaseFileModel> diffs =
                new BuildsComparator(artifactService).compareDependencies(currentBuild, prevBuild);

        assertEquals(5, diffs.size());
        assertDependencyDiff(diffs, "org.cool:cool:1.0", "org.cool:cool:1.0", UPDATED, Set.of(SCOPE));
        assertDependencyDiff(diffs, "org.cool:cool2:1.0", "org.cool:cool2:1.0", UPDATED, Set.of(SCOPE, CHECKSUM));
        assertDependencyDiff(diffs, "org.cool:cooler:1.0", "org.cool:cooler:1.0", BuildsDiffStatus.UNCHANGED,
                Set.of(UNCHANGED));
        assertDependencyDiff(diffs, "org.cool:coolest:1.0", "org.cool:coolest:2.0", BuildsDiffStatus.UNCHANGED,
                Set.of(UNCHANGED)); // Currently, changes in the ID are not recognized as a change.
        assertDependencyDiff(diffs, "org.cool:coolest2:1.0", "org.cool:coolest2:1.0", UPDATED, Set.of(CHECKSUM));
    }

    private void assertDependencyDiff(
            List<BuildsDiffBaseFileModel> diffs,
            String depName1,
            String depName2,
            BuildsDiffStatus expectedStatus,
            Set<BuildsDiffValue> expectedValue) {
        BuildsDiffDependencyModel fileModel = diffs.stream()
                .filter(input -> Objects.equals(input.getName(), depName1) &&
                        Objects.equals(input.getDiffName(), depName2))
                .map(BuildsDiffDependencyModel.class::cast)
                .findFirst()
                .orElse(null);
        assertNotNull(fileModel);
        assertEquals(expectedStatus, fileModel.getStatus());
        assertEquals(expectedValue, fileModel.getBuildsDiffValue());
    }
}
