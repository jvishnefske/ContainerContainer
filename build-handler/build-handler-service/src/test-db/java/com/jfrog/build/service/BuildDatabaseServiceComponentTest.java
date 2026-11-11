package com.jfrog.build.service;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jfrog.build.acl.model.BuildWrapper;
import com.jfrog.build.model.api.PublishedModule;
import com.jfrog.build.acl.model.BuildNameAndRepo;
import com.jfrog.build.model.BuildNodeRecord;
import com.jfrog.build.acl.model.BuildRun;
import com.jfrog.build.test.database.base.DatabaseTestBase;
import lombok.NonNull;
import org.jfrog.build.api.Agent;
import org.jfrog.build.api.Artifact;
import org.jfrog.build.api.Build;
import org.jfrog.build.api.BuildAgent;
import org.jfrog.build.api.Dependency;
import org.jfrog.build.api.Module;
import org.jfrog.build.api.builder.ArtifactBuilder;
import org.jfrog.build.api.builder.BuildInfoBuilder;
import org.jfrog.build.api.builder.DependencyBuilder;
import org.jfrog.build.api.builder.ModuleBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Properties;

import static com.jfrog.build.acl.util.BuildConstants.BUILDINFO_REPO_KEY;
import static com.jfrog.build.acl.util.BuildInfoUtils.formatBuildTime;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class BuildDatabaseServiceComponentTest extends DatabaseTestBase {

    @Autowired
    private BuildDatabaseService buildDatabaseService;

    @BeforeEach
    void setup() {
        importSql("/sql/dml/builds.sql");
    }

    @Test
    void getBuildRun() {
        BuildRun buildRun = buildDatabaseService.getBuildRun("bb", "2", formatBuildTime(1349003000000L));
        assertNotNull(buildRun);
        assertEquals("rollback", buildRun.getReleaseStatus());
        assertEquals("project-build-info", buildRun.getBuildRepo());
    }

    @Test
    void getPublishedModules_WithArtifactsAndDependencies() {
        String buildName = "bb";
        String buildNumber = "1";
        String buildStarted = "1349001000000";
        String buildRepo = "project-build-info";

        List<PublishedModule> publishedModules = buildDatabaseService.getPublishedModules(
                buildName, buildNumber, buildStarted, buildRepo);

        assertNotNull(publishedModules);
        assertEquals(2, publishedModules.size());

        PublishedModule module1 = publishedModules.getFirst();
        assertEquals("bb:modb1", module1.getId());
        assertEquals("1", module1.getNumOfArtifact());
        assertEquals("1", module1.getNumOfDependencies());

        PublishedModule module2 = publishedModules.get(1);
        assertEquals("bb:modb2", module2.getId());
        assertEquals("1", module2.getNumOfArtifact());
        assertEquals("2", module2.getNumOfDependencies());
    }

    @Test
    void getAllBuildsInfo() {
        buildDatabaseService.addBuild(getBuildObject(formatBuildTime(System.currentTimeMillis())));
        List<BuildNameAndRepo> allBuildNames = buildDatabaseService.getAllBuildsInfo(null);
        assertEquals(3, allBuildNames.size());
    }

    @Test
    void deleteBuild() {
        buildDatabaseService.deleteBuild("ba", "2", formatBuildTime(1349002000000L), null);
        assertEquals(2, buildDatabaseService.findBuildsByName("ba", BUILDINFO_REPO_KEY).size());
        assertEquals(2, buildDatabaseService.findBuildsByName("ba").size());
    }

    @Test
    void getBuildArtifacts() {
        List<BuildNodeRecord> buildArtifacts = buildDatabaseService.getBuildArtifacts("ba", "1", BUILDINFO_REPO_KEY);
        assertEquals(1, buildArtifacts.size());
        BuildNodeRecord buildNode = buildArtifacts.getFirst();
        assertEquals("node_test_name", buildNode.name());
        assertEquals("path", buildNode.path());
        assertEquals("repo1", buildNode.repoKey());
    }

    @NonNull
    private BuildWrapper getBuildObject(@NonNull String startedBuild) {
        Dependency dependency = new DependencyBuilder()
                .id("moo")
                .type("bob")
                .scopes(Sets.newHashSet("mitzi"))
                .sha1("pop")
                .md5("shmop")
                .requestedBy(new String[][] {})
                .build();

        Artifact artifact = new ArtifactBuilder("blob")
                .type("glob")
                .sha1("shlob")
                .md5("mob")
                .properties(new Properties())
                .build();

        Module module = new ModuleBuilder()
                .id("moo")
                .artifacts(Lists.newArrayList(artifact))
                .dependencies(Lists.newArrayList(dependency))
                .build();

        Properties properties = new Properties();
        properties.put("goo", "koo");

        Build build = new BuildInfoBuilder("test")
                .version("1.7.0")
                .number("23")
                .agent(new Agent("pop", "1.6"))
                .started(startedBuild)
                .durationMillis(6L)
                .principal("bob")
                .artifactoryPrincipal("too")
                .url("mitz")
                .modules(Lists.newArrayList(module))
                .properties(properties)
                .buildAgent(new BuildAgent("agentName", "agentVersion"))
                .agent(new Agent("agentName", "agentVersion"))
                .build();

        return new BuildWrapper(build, BUILDINFO_REPO_KEY);
    }
}
