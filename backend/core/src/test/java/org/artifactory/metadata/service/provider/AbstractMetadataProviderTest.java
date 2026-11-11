package org.artifactory.metadata.service.provider;

import com.google.common.collect.ImmutableList;
import lombok.NonNull;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.xray.XrayAddon;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.module.ModuleInfo;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.RepoLayout;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.md.Properties;
import org.artifactory.model.BaseConfig;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.model.fs.FileInfoImpl;
import org.artifactory.properties.PropertiesImpl;
import org.artifactory.repo.Repo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.cache.expirable.CacheExpiry;
import org.artifactory.repo.cache.expirable.CacheExpiryImpl;
import org.artifactory.repo.db.DbLocalRepo;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.spring.InternalArtifactoryContext;
import org.artifactory.storage.db.event.service.metadata.model.MutableMetadataEntityBOM;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.util.RepoLayoutUtils;
import org.jfrog.common.ClockUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static java.util.Objects.isNull;
import static org.artifactory.metadata.service.provider.DockerMetadataProvider.MANIFEST_TYPE_PROP;
import static org.artifactory.util.RepoLayoutUtils.GRADLE_DEFAULT_DEPRECATED;
import static org.artifactory.util.RepoLayoutUtils.SBT_IVY_DEFAULT_NAME;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * @author Uriah Levy
 */
@Test
public class AbstractMetadataProviderTest extends ArtifactoryHomeBoundTest {

    @AfterMethod
    public void teardown() {
        ArtifactoryContextThreadBinder.unbind();
    }

    public void testExcludedUserProperties() {
        NpmMetadataProvider npmMetadataProvider = new NpmMetadataProvider();
        MutableMetadataEntityBOM bom = new MutableMetadataEntityBOM();
        PropertiesImpl props = new PropertiesImpl();
        props.put("npm.description", "the gift is a cool movie");
        props.put("npm.keywords", "Gordo, Weirdo");
        props.put("npm.name", "Bojangles");

        npmMetadataProvider.userPropsResolver().apply(bom, props, null, null);

        assertEquals(bom.getUserProperties().size(), 0);

        RpmMetadataProvider rpmMetadataProvider = new RpmMetadataProvider();
        bom = new MutableMetadataEntityBOM();
        props = new PropertiesImpl();
        props.put("rpm.metadata.name", "Robyn");
        props.put("rpm.metadata.summary", "the gift is a strange movie");

        rpmMetadataProvider.userPropsResolver().apply(bom, props, null, null);

        NugetMetadataProvider nugetMetadataProvider = new NugetMetadataProvider();
        bom = new MutableMetadataEntityBOM();
        props = new PropertiesImpl();
        props.put("nuget.dependency", "long, comma, separated, list, of, dependencies, to, exclude");

        nugetMetadataProvider.userPropsResolver().apply(bom, props, null, null);

        assertEquals(bom.getUserProperties().size(), 0);
    }

    public void testNullUserProperty() {
        NpmMetadataProvider npmMetadataProvider = new NpmMetadataProvider();
        MutableMetadataEntityBOM bom = new MutableMetadataEntityBOM();
        PropertiesImpl props = new PropertiesImpl();
        props.put("foo", null);
        props.put("foo", "bar");
        props.put("baz", "");
        props.put("boom", "box");

        npmMetadataProvider.userPropsResolver().apply(bom, props, null, null);

        assertEquals(bom.getUserProperties().size(), 2);
        assertEquals(bom.getUserProperties().get("foo").size(), 1);
        assertEquals(bom.getUserProperties().get("boom").size(), 1);
    }

    public void testQualifierResolver() {
        NpmMetadataProvider npmMetadataProvider = new NpmMetadataProvider();
        MutableMetadataEntityBOM bom = new MutableMetadataEntityBOM();
        PropertiesImpl props = new PropertiesImpl();
        props.put("npm.name", "@foo/bar");

        npmMetadataProvider.qualifiersResolver().apply(bom, props, null, null);

        assertEquals(1, bom.getQualifiers().size());
        assertEquals(bom.getQualifiers().get("npm_scope"), "foo");
    }

    public void testNpmTagsResolver() {
        NpmMetadataProvider npmMetadataProvider = new NpmMetadataProvider();
        MutableMetadataEntityBOM bom = new MutableMetadataEntityBOM();
        PropertiesImpl props = new PropertiesImpl();
        props.put("npm.keywords", "foo, bar");

        npmMetadataProvider.tagsResolver().apply(bom, props, null, null);

        assertEquals(2, bom.getTags().size());
        assertTrue(bom.getTags().containsAll(ImmutableList.of("foo", "bar")));

        props.put("npm.keywords", "\f\\oo//\\, /ba/r//");
        bom.getTags().clear();
        npmMetadataProvider.tagsResolver().apply(bom, props, null, null);

        assertEquals(2, bom.getTags().size());
        assertTrue(bom.getTags().containsAll(ImmutableList.of("foo", "bar")));

        props.clear();
        props.put("npm.keywords", "[]");
        bom.getTags().clear();
        npmMetadataProvider.tagsResolver().apply(bom, props, null, null);
        assertTrue(bom.getTags().isEmpty());

        props.clear();
        props.put("npm.keywords", "no_content");
        bom.getTags().clear();
        npmMetadataProvider.tagsResolver().apply(bom, props, null, null);
        assertTrue(bom.getTags().isEmpty());
    }

    public void testNugetTagsResolver() {
        NugetMetadataProvider nugetMetadataProvider = new NugetMetadataProvider();
        MutableMetadataEntityBOM bom = new MutableMetadataEntityBOM();
        PropertiesImpl props = new PropertiesImpl();
        props.put("nuget.tags", "foo bar");

        nugetMetadataProvider.tagsResolver().apply(bom, props, null, null);

        assertEquals(2, bom.getTags().size());
        assertTrue(bom.getTags().containsAll(ImmutableList.of("foo", "bar")));
    }

    public void descriptionResolver() {
        NpmMetadataProvider npmMetadataProvider = new NpmMetadataProvider();
        MutableMetadataEntityBOM bom = new MutableMetadataEntityBOM();
        PropertiesImpl props = new PropertiesImpl();
        props.put("npm.description", "darn cool package");

        npmMetadataProvider.packageDescriptionResolver().apply(bom, props, null, null);

        assertEquals(bom.getDescription(), "darn cool package");

        RpmMetadataProvider rpmMetadataProvider = new RpmMetadataProvider();
        bom = new MutableMetadataEntityBOM();
        props = new PropertiesImpl();
        props.put("rpm.metadata.name", "Robyn");
        props.put("rpm.metadata.summary", "the gift is a strange movie");

        rpmMetadataProvider.packageDescriptionResolver().apply(bom, props, null, null);

        assertEquals(bom.getDescription(), "the gift is a strange movie");

        props.clear();
        bom.setDescription(null);
        npmMetadataProvider.packageDescriptionResolver().apply(bom, props, null, null);

        assertNull(bom.getDescription());

        //new:
        props.clear();
        props.put("npm.description", "no_content");
        npmMetadataProvider.packageDescriptionResolver().apply(bom, props, null, null);
        assertNull(bom.getDescription());
    }

    public void testNpmPkgIdResolver() {
        NpmMetadataProvider npmMetadataProvider = new NpmMetadataProvider();
        MutableMetadataEntityBOM bom = new MutableMetadataEntityBOM();
        FileInfoImpl fileInfo = new FileInfoImpl(new RepoPathImpl("npm-local", "express/-/express-1.0.0.tgz"));
        PropertiesImpl props = new PropertiesImpl();
        props.put("npm.name", "express");

        npmMetadataProvider.pkgIdResolver().apply(bom, props, fileInfo, null);

        assertEquals(bom.getPkgid(), "npm://express");

        props.replaceValues("npm.name", ImmutableList.of(new String(new byte[500])));
        npmMetadataProvider.pkgIdResolver().apply(bom, props, fileInfo, null);

        assertTrue(bom.getPkgid().startsWith("npm://"));
        assertEquals(bom.getPkgid().length(), 255);
    }

    public void testDebianFileInfoResolver() {
        setupMocks();

        DebianMetadataProvider debianMetadataProvider = new DebianMetadataProvider();
        MutableMetadataEntityBOM bom = new MutableMetadataEntityBOM();
        FileInfoImpl fileInfo = new FileInfoImpl(new RepoPathImpl("debian-local", "lucid/i386/acl-1.0.0.deb"));
        PropertiesImpl props = new PropertiesImpl();
        props.put("deb.distribution", "lucid");
        props.put("deb.architecture", "i386");

        debianMetadataProvider.fileInfoResolver().apply(bom, props, fileInfo, null);
        debianMetadataProvider.architectureResolver().apply(bom, props, fileInfo, null);
        debianMetadataProvider.distributionResolver().apply(bom, props, fileInfo, null);

        assertEquals(bom.getArtifactMetadata().get(0).getDist(), "lucid");
        assertEquals(bom.getArtifactMetadata().get(0).getArch(), "i386");
    }

    public void testDebianPkgIdResolver() {
        DebianMetadataProvider debianMetadataProvider = new DebianMetadataProvider();
        MutableMetadataEntityBOM bom = new MutableMetadataEntityBOM();
        FileInfoImpl fileInfo = new FileInfoImpl(new RepoPathImpl("debian-local", "lucid/i386/acl-1.0.0.deb"));
        PropertiesImpl props = new PropertiesImpl();
        props.put("deb.name", "acl");

        debianMetadataProvider.pkgIdResolver().apply(bom, props, fileInfo, null);

        assertEquals(bom.getPkgid(), "deb://acl");
    }

    public void testAlpineFileInfoResolver() {
        setupMocks();

        AlpineMetadataProvider alpineMetadataProvider = new AlpineMetadataProvider();
        MutableMetadataEntityBOM bom = new MutableMetadataEntityBOM();
        FileInfoImpl fileInfo =
                new FileInfoImpl(new RepoPathImpl("alpine-local", "v3.11/main/x86_64/unzip-6.0-r6.apk"));
        PropertiesImpl props = new PropertiesImpl();
        props.put("alpine.branch", "v3.11");
        props.put("alpine.architecture", "x86_64");

        alpineMetadataProvider.fileInfoResolver().apply(bom, props, fileInfo, null);
        alpineMetadataProvider.distributionResolver().apply(bom, props, fileInfo, null);
        alpineMetadataProvider.architectureResolver().apply(bom, props, fileInfo, null);

        assertEquals(bom.getArtifactMetadata().get(0).getDist(), "v3.11");
        assertEquals(bom.getArtifactMetadata().get(0).getArch(), "x86_64");
    }

    public void testAlpinePkgIdResolver() {
        AlpineMetadataProvider alpineMetadataProvider = new AlpineMetadataProvider();
        MutableMetadataEntityBOM bom = new MutableMetadataEntityBOM();
        FileInfoImpl fileInfo =
                new FileInfoImpl(new RepoPathImpl("alpine-local", "v3.11/main/x86_64/unzip-6.0-r6.apk"));
        PropertiesImpl props = new PropertiesImpl();
        props.put("alpine.name", "unzip");

        alpineMetadataProvider.pkgIdResolver().apply(bom, props, fileInfo, null);

        assertEquals(bom.getPkgid(), "alpine://unzip");
    }

    public void testHelmPkgIdResolver() {
        HelmMetadataProvider helmMetadataProvider = new HelmMetadataProvider();
        MutableMetadataEntityBOM bom = new MutableMetadataEntityBOM();
        FileInfoImpl fileInfo = new FileInfoImpl(
                new RepoPathImpl("helm-local", "artifactory-helm/artifactory-7.0.0.tgz"));
        PropertiesImpl props = new PropertiesImpl();
        props.put("chart.name", "artifactory");

        helmMetadataProvider.pkgIdResolver().apply(bom, props, fileInfo, null);

        assertEquals(bom.getPkgid(), "helm://artifactory");
    }

    public void testPubPkgIdResolver() {
        PubMetadataProvider pubMetadataProvider = new PubMetadataProvider();
        MutableMetadataEntityBOM bom = new MutableMetadataEntityBOM();
        FileInfoImpl fileInfo = new FileInfoImpl(
                new RepoPathImpl("pub-local", "artifactory-pub/pedantic-1.9.0.tgz"));
        PropertiesImpl props = new PropertiesImpl();
        props.put("pub.name", "pedantic");
        props.put("pub.version", "1.9.0");
        props.put("pub.description", "The Dart analyzer settings and best practices used internally at Google.");

        pubMetadataProvider.pkgIdResolver().apply(bom, props, fileInfo, null);
        pubMetadataProvider.versionResolver().apply(bom, props, fileInfo, null);
        pubMetadataProvider.packageDescriptionResolver().apply(bom, props, fileInfo, null);

        assertEquals(bom.getPkgid(), "pub://pedantic");
        assertEquals(bom.getVersion(), "1.9.0");
        assertEquals(bom.getDescription(), "The Dart analyzer settings and best practices used internally at Google.");
    }

    public void testComposerPkgIdResolver() {
        ComposerMetadataProvider composerMetadataProvider = new ComposerMetadataProvider();
        MutableMetadataEntityBOM bom = new MutableMetadataEntityBOM();
        FileInfoImpl fileInfo = new FileInfoImpl(
                new RepoPathImpl("composer-local", "jfi/jfi-core/jfi-code-2.1.250.tar.gz"));
        PropertiesImpl props = new PropertiesImpl();
        props.put("composer.name", "jfrog/insight-core");

        composerMetadataProvider.pkgIdResolver().apply(bom, props, fileInfo, null);

        assertEquals(bom.getPkgid(), "composer://jfrog/insight-core");
    }

    public void testOpkgPkgIdResolver() {
        OpkgMetadataProvider opkgMetadataProvider = new OpkgMetadataProvider();
        MutableMetadataEntityBOM bom = new MutableMetadataEntityBOM();
        FileInfoImpl fileInfo = new FileInfoImpl(new RepoPathImpl("opkg-local", "test-package1_0.1_x86.ipk"));
        PropertiesImpl props = new PropertiesImpl();
        props.put("opkg.name", "test-package1");

        opkgMetadataProvider.pkgIdResolver().apply(bom, props, fileInfo, null);

        assertEquals(bom.getPkgid(), "opkg://test-package1");
    }

    public void testPuppetPkgIdResolver() {
        PuppetMetadataProvider puppetMetadataProvider = new PuppetMetadataProvider();
        MutableMetadataEntityBOM bom = new MutableMetadataEntityBOM();
        FileInfoImpl fileInfo = new FileInfoImpl(new RepoPathImpl("puppet-local", "puppetlabs-mysql-3.9.0.tar.gz"));
        PropertiesImpl props = new PropertiesImpl();
        props.put("puppet.name", "puppetlabs-mysql");

        puppetMetadataProvider.pkgIdResolver().apply(bom, props, fileInfo, null);

        assertEquals(bom.getPkgid(), "puppet://puppetlabs-mysql");
    }

    public void testVagrantPkgIdResolver() {
        VagrantMetadataProvider vagrantMetadataProvider = new VagrantMetadataProvider();
        MutableMetadataEntityBOM bom = new MutableMetadataEntityBOM();
        FileInfoImpl fileInfo = new FileInfoImpl(new RepoPathImpl("vagrant-local", "trusty64_1.0.0_virtualbox.box"));
        PropertiesImpl props = new PropertiesImpl();
        props.put("box_name", "trusty");

        vagrantMetadataProvider.pkgIdResolver().apply(bom, props, fileInfo, null);

        assertEquals(bom.getPkgid(), "vagrant://trusty");
    }

    public void testIvyPkgIdResolver() {
        InternalRepositoryService internalRepositoryService = mock(InternalRepositoryService.class);
        IvyMetadataProvider ivyMetadataProvider = new IvyMetadataProvider(internalRepositoryService);
        MutableMetadataEntityBOM bom = new MutableMetadataEntityBOM();
        FileInfoImpl fileInfo = new FileInfoImpl(
                new RepoPathImpl("ivy-local", "org.apache.ivy.example/version/1.0-dev-b1/ivys/ivy-0.1-deb-b1.xml"));

        Repo repo = mock(Repo.class);
        when(internalRepositoryService.repositoryByKey("ivy-local")).thenReturn(repo);
        when(repo.getDescriptor()).thenReturn(new LocalRepoDescriptor());
        when(repo.getItemModuleInfo("org.apache.ivy.example/version/1.0-dev-b1/ivys/ivy-0.1-deb-b1.xml"))
                .thenReturn(new ModuleInfo("org.apache.ivy.example", "version", "1.0-dev-b1",
                                           null, null, null, "xml", "xml", Collections.emptyMap()));

        ivyMetadataProvider.nameResolver().apply(bom, null, fileInfo, null);
        ivyMetadataProvider.versionResolver().apply(bom, null, fileInfo, null);
        ivyMetadataProvider.pkgIdResolver().apply(bom, null, fileInfo, null);

        assertEquals(bom.getName(), "org.apache.ivy.example:version");
        assertEquals(bom.getVersion(), "1.0-dev-b1");
        assertEquals(bom.getPkgid(), "ivy://org.apache.ivy.example:version");
    }

    public void testSbtPkgIdResolver() {
        InternalRepositoryService internalRepositoryService = mock(InternalRepositoryService.class);
        SbtMetadataProvider sbtMetadataProvider = new SbtMetadataProvider(internalRepositoryService);
        MutableMetadataEntityBOM bom = new MutableMetadataEntityBOM();
        FileInfoImpl fileInfo = new FileInfoImpl(
                new RepoPathImpl("sbt-local",
                                 "sbt_example/sbt_example_2.11/1.1-SNAPSHOT/sbt_example_2.11-1.1-SNAPSHOT.pom"));

        Repo repo = mock(Repo.class);
        when(internalRepositoryService.repositoryByKey("sbt-local")).thenReturn(repo);
        when(repo.getDescriptor()).thenReturn(new LocalRepoDescriptor());
        when(repo.getItemModuleInfo("sbt_example/sbt_example_2.11/1.1-SNAPSHOT/sbt_example_2.11-1.1-SNAPSHOT.pom"))
                .thenReturn(new ModuleInfo("sbt_example", "sbt_example_2.11", "1.1-SNAPSHOT", null, null, null, "pom",
                                           "pom",
                                           Collections.emptyMap()));

        sbtMetadataProvider.nameResolver().apply(bom, null, fileInfo, null);
        sbtMetadataProvider.versionResolver().apply(bom, null, fileInfo, null);
        sbtMetadataProvider.pkgIdResolver().apply(bom, null, fileInfo, null);

        assertEquals(bom.getName(), "sbt_example:sbt_example_2.11");
        assertEquals(bom.getVersion(), "1.1-SNAPSHOT");
        assertEquals(bom.getPkgid(), "gav://sbt_example:sbt_example_2.11");
    }

    public void testGradlePkgIdResolver() {
        InternalRepositoryService internalRepositoryService = mock(InternalRepositoryService.class);
        GradleMetadataProvider gradleMetadataProvider = new GradleMetadataProvider(internalRepositoryService);
        MutableMetadataEntityBOM bom = new MutableMetadataEntityBOM();
        FileInfoImpl fileInfo = new FileInfoImpl(
                new RepoPathImpl(
                        "gradle-local",
                        "gradle_example/gradle_example_2.11/1.1-SNAPSHOT/gradle_example_2.11-1.1-SNAPSHOT.pom"));

        Repo repo = mock(Repo.class);
        when(internalRepositoryService.repositoryByKey("gradle-local")).thenReturn(repo);
        when(repo.getDescriptor()).thenReturn(new LocalRepoDescriptor());
        when(repo.getItemModuleInfo(
                "gradle_example/gradle_example_2.11/1.1-SNAPSHOT/gradle_example_2.11-1.1-SNAPSHOT.pom"))
                .thenReturn(
                        new ModuleInfo("gradle_example", "gradle_example_2.11", "1.1-SNAPSHOT", null, null, null, "pom",
                                       "pom",
                                       Collections.emptyMap()));

        gradleMetadataProvider.nameResolver().apply(bom, null, fileInfo, null);
        gradleMetadataProvider.versionResolver().apply(bom, null, fileInfo, null);
        gradleMetadataProvider.pkgIdResolver().apply(bom, null, fileInfo, null);

        assertEquals(bom.getName(), "gradle_example:gradle_example_2.11");
        assertEquals(bom.getVersion(), "1.1-SNAPSHOT");
        assertEquals(bom.getPkgid(), "gav://gradle_example:gradle_example_2.11");
    }

    public void testConanPkgIdResolver() {
        ConanMetadataProvider conanMetadataProvider = new ConanMetadataProvider();
        MutableMetadataEntityBOM bom = new MutableMetadataEntityBOM();
        FileInfoImpl fileInfo = new FileInfoImpl(
                new RepoPathImpl("conan-local", "conan/zlib/1.2.9/stable/0/export/conanfile.py"));
        PropertiesImpl props = new PropertiesImpl();
        props.put("conan.package.name", "zlib");

        conanMetadataProvider.pkgIdResolver().apply(bom, props, fileInfo, null);

        assertEquals(bom.getPkgid(), "conan://zlib");
    }

    public void testConanPkgIdWithVendorResolver() {
        ConanMetadataProvider conanMetadataProvider = new ConanMetadataProvider();
        MutableMetadataEntityBOM bom = new MutableMetadataEntityBOM();
        FileInfoImpl fileInfo = new FileInfoImpl(
                new RepoPathImpl("conan-local", "conan/zlib/1.2.9/stable/0/export/conanfile.py"));
        PropertiesImpl props = new PropertiesImpl();
        props.put("conan.package.name", "zlib");
        props.put("conan.package.vendor", "jfrog");

        conanMetadataProvider.pkgIdResolver().apply(bom, props, fileInfo, null);

        assertEquals(bom.getPkgid(), "conan://jfrog:zlib");
    }

    public void testGoPkgIdResolver() {
        InternalRepositoryService internalRepositoryService = mock(InternalRepositoryService.class);
        GoMetadataProvider goMetadataProvider = new GoMetadataProvider(internalRepositoryService);
        MutableMetadataEntityBOM bom = new MutableMetadataEntityBOM();
        FileInfoImpl fileInfo = new FileInfoImpl(
                new RepoPathImpl("go-local", "jfrog.com/jfrog-go-commons/@v/v0.0.1-20190617090946-29.mod"));
        PropertiesImpl props = new PropertiesImpl();
        props.put("go.name", "jfrog.com/jfrog-go-commons");

        goMetadataProvider.pkgIdResolver().apply(bom, props, fileInfo, null);

        assertEquals(bom.getPkgid(), "go://jfrog.com/jfrog-go-commons");
    }

    public void testTrimValueIfNeeded() {
        RpmMetadataProvider rpmMetadataProvider = new RpmMetadataProvider();
        String normalizedValue = rpmMetadataProvider
                                         .trimValueIfNeeded("rpm.metadata.summary", new String(new byte[5000]));
        assertEquals(normalizedValue.length(), 5000);

        normalizedValue = rpmMetadataProvider
                                  .trimValueIfNeeded("rpm.metadata.tooLong", new String(new byte[5000]));
        assertEquals(normalizedValue.length(), 4000);

        normalizedValue = rpmMetadataProvider
                                  .trimValueIfNeeded("rpm.metadata.notTooLong", new String(new byte[5]));
        assertEquals(normalizedValue.length(), 5);

        DockerMetadataProvider dockerMetadataProvider = new DockerMetadataProvider();
        normalizedValue = dockerMetadataProvider
                                  .trimValueIfNeeded("docker.hasNoDescriptionKey", new String(new byte[5000]));
        assertEquals(normalizedValue.length(), 4000);

        normalizedValue = dockerMetadataProvider
                                  .trimValueIfNeeded("docker.hasNoDescriptionKey", new String(new byte[4]));
        assertEquals(normalizedValue.length(), 4);

        NpmMetadataProvider npmMetadataProvider = new NpmMetadataProvider();
        normalizedValue = npmMetadataProvider.trimValueIfNeeded("npm.keywords", new String(new byte[5000]));
        assertEquals(normalizedValue.length(), 5000);

        normalizedValue = npmMetadataProvider.trimValueIfNeeded("npm.description", new String(new byte[5000]));
        assertEquals(normalizedValue.length(), 5000);
    }

    public void testRpmPkgIdAndNameResolver() {
        RpmMetadataProvider rpmMetadataProvider = new RpmMetadataProvider();
        MutableMetadataEntityBOM bom = new MutableMetadataEntityBOM();
        FileInfoImpl fileInfo = new FileInfoImpl(new RepoPathImpl("rpm-local", "ImageMagick.rpm"));
        PropertiesImpl props = new PropertiesImpl();
        props.put("rpm.metadata.name", "ImageMagick");
        props.put("rpm.metadata.release", "2.el7");

        rpmMetadataProvider.pkgIdResolver().apply(bom, props, fileInfo, null);
        assertEquals(bom.getPkgid(), "rpm://7:ImageMagick");

        rpmMetadataProvider.nameResolver().apply(bom, props, fileInfo, null);
        assertEquals(bom.getName(), "7:ImageMagick");

        props = new PropertiesImpl();
        props.put("rpm.metadata.name", "ImageMagick");
        props.put("rpm.metadata.release", "2.el7.AnotherValidElVersion");

        rpmMetadataProvider.pkgIdResolver().apply(bom, props, fileInfo, null);
        assertEquals(bom.getPkgid(), "rpm://7:ImageMagick");

        props = new PropertiesImpl();
        props.put("rpm.metadata.name", "ImageMagick");

        rpmMetadataProvider.pkgIdResolver().apply(bom, props, fileInfo, null);
        rpmMetadataProvider.nameResolver().apply(bom, props, fileInfo, null);
        assertEquals(bom.getPkgid(), "rpm://ImageMagick");
        assertEquals(bom.getName(), "ImageMagick");

        props = new PropertiesImpl();
        props.put("rpm.metadata.name", "dejavu-font");
        props.put("rpm.metadata.release", "9.el7.centos");

        rpmMetadataProvider.pkgIdResolver().apply(bom, props, fileInfo, null);
        assertEquals(bom.getPkgid(), "rpm://7:dejavu-font");
    }

    public void testGradleSupportsAnyLayout() {
        InternalRepositoryService internalRepositoryService = mock(InternalRepositoryService.class);
        GradleMetadataProvider gradleMetadataProvider = new GradleMetadataProvider(internalRepositoryService);

        LocalRepoDescriptor localRepoDescriptor = new LocalRepoDescriptor();
        localRepoDescriptor.setType(RepoType.Gradle);
        localRepoDescriptor.setRepoLayout(RepoLayoutUtils.MAVEN_2_DEFAULT);
        when(internalRepositoryService.repoDescriptorByKey("gradle-local")).thenReturn(localRepoDescriptor);

        assertTrue(gradleMetadataProvider.isLeadArtifact(new RepoPathImpl("gradle-local", "package.pom")));
        assertFalse(gradleMetadataProvider.isLeadArtifact(new RepoPathImpl("gradle-local", "package.not_a_pom")));

        localRepoDescriptor.setRepoLayout(RepoLayoutUtils.GRADLE_DEFAULT_DEPRECATED);
        assertTrue(gradleMetadataProvider.isLeadArtifact(new RepoPathImpl("gradle-local", "package.pom")));
    }

    public void testPackageConsumptionLeadFileMavenGradle() {
        InternalRepositoryService internalRepositoryService = mock(InternalRepositoryService.class);
        GradleMetadataProvider gradleMetadataProvider = new GradleMetadataProvider(internalRepositoryService);

        LocalRepoDescriptor localRepoDescriptor = new LocalRepoDescriptor();
        localRepoDescriptor.setType(RepoType.Gradle);
        localRepoDescriptor.setRepoLayout(RepoLayoutUtils.MAVEN_2_DEFAULT);
        when(internalRepositoryService.repoDescriptorByKey("gradle-local")).thenReturn(localRepoDescriptor);

        RepoConfig repoConfig = mock(RepoConfig.class);
        when(internalRepositoryService.repoConfigByKey("gradle-local")).thenReturn(repoConfig);
        BaseConfig baseConfig = mock(BaseConfig.class);
        when(repoConfig.getBaseConfig()).thenReturn(baseConfig);

        assertFalse(
                gradleMetadataProvider.isPackageConsumptionLead(new RepoPathImpl("gradle-local", "package.not_a_pom")));

        localRepoDescriptor.setRepoLayout(GRADLE_DEFAULT_DEPRECATED);
        assertTrue(gradleMetadataProvider.isPackageConsumptionLead(new RepoPathImpl("gradle-local", "package.pom")));
    }

    @SuppressWarnings("unchecked")
    public void testLayoutBasedProvidersLayoutRestricted() {
        RepoPathImpl leadArtifactRepoPath;
        InternalRepositoryService repoService = mock(InternalRepositoryService.class);
        XrayAddon xrayAddonMock = mock(XrayAddon.class);
        AddonsManager addonsManagerMock = mock(AddonsManager.class);
        when(addonsManagerMock.addonByType(XrayAddon.class)).thenReturn(xrayAddonMock);
        RepoConfig repoConfig = mock(RepoConfig.class);
        BaseConfig baseConfig = mock(BaseConfig.class);
        when(repoConfig.getBaseConfig()).thenReturn(baseConfig);
        DbLocalRepo<LocalRepoDescriptor, LocalRepoConfig> repo = mock(DbLocalRepo.class);

        RepoLayout unsupportedLayout = new RepoLayout();
        unsupportedLayout.setName("unsupported-layout");

        // Ivy: maven layout
        String ivyLocalRepo = "ivy-local";
        String pathToPom = "org/jfrog/package/1.0/package-1.0.pom";
        ModuleInfo pomModuleInfo = new ModuleInfo("com/jfrog", "artifactory", "1.0",
                null, null, null, "pom", "pom", Collections.emptyMap());

        when(repoConfig.getBaseConfig().getRepoLayoutRef()).thenReturn(RepoLayoutUtils.IVY_DEFAULT_NAME);
        IvyMetadataProvider ivyMetadataProvider = new IvyMetadataProvider(repoService);
        when(repoService.repoConfigByKey(ivyLocalRepo)).thenReturn(repoConfig);

        leadArtifactRepoPath = new RepoPathImpl(ivyLocalRepo, pathToPom);
        when(repoService.repositoryByKey(leadArtifactRepoPath.getRepoKey())).thenReturn(repo);
        assertTrue(ivyMetadataProvider.buildVersionFilesQuery(leadArtifactRepoPath).isPresent());

        // Ivy: some other layout
        when(repoConfig.getBaseConfig().getRepoLayoutRef()).thenReturn(unsupportedLayout.getName());
        assertFalse(ivyMetadataProvider.buildVersionFilesQuery(leadArtifactRepoPath).isPresent());

        // Ivy: only depth > 2 is supported for the lead artifact
        assertFalse(ivyMetadataProvider.buildVersionFilesQuery(
                new RepoPathImpl("ivy-local", "flat-path.pom")).isPresent());

        // Maven: maven layout
        String mavenLocalRepo = "maven-local";
        when(repoConfig.getBaseConfig().getRepoLayoutRef()).thenReturn(RepoLayoutUtils.MAVEN_2_DEFAULT_NAME);
        MavenMetadataProvider mavenMetadataProvider = new MavenMetadataProvider(repoService, addonsManagerMock);
        when(repoService.repoConfigByKey(mavenLocalRepo)).thenReturn(repoConfig);

        leadArtifactRepoPath = new RepoPathImpl(mavenLocalRepo, pathToPom);
        when(repo.getItemModuleInfo(pathToPom)).thenReturn(pomModuleInfo);
        when(repoService.repositoryByKey(leadArtifactRepoPath.getRepoKey())).thenReturn(repo);
        assertTrue(mavenMetadataProvider.buildVersionFilesQuery(leadArtifactRepoPath).isPresent());

        // Maven: some other layout
        when(repoConfig.getBaseConfig().getRepoLayoutRef()).thenReturn(unsupportedLayout.getName());
        assertFalse(mavenMetadataProvider.buildVersionFilesQuery(leadArtifactRepoPath).isPresent());

        // Gradle: maven layout
        String gradleLocalRepo = "gradle-local";
        when(repoConfig.getBaseConfig().getRepoLayoutRef()).thenReturn(RepoLayoutUtils.MAVEN_2_DEFAULT_NAME);
        GradleMetadataProvider gradleMetadataProvider = new GradleMetadataProvider(repoService);
        when(repoService.repoConfigByKey(gradleLocalRepo)).thenReturn(repoConfig);

        leadArtifactRepoPath = new RepoPathImpl(gradleLocalRepo, pathToPom);
        when(repo.getItemModuleInfo(pathToPom)).thenReturn(pomModuleInfo);
        when(repoService.repositoryByKey(leadArtifactRepoPath.getRepoKey())).thenReturn(repo);
        assertTrue(gradleMetadataProvider.buildVersionFilesQuery(leadArtifactRepoPath).isPresent());

        // Gradle: some other layout
        when(repoConfig.getBaseConfig().getRepoLayoutRef()).thenReturn(unsupportedLayout.getName());
        assertFalse(gradleMetadataProvider.buildVersionFilesQuery(leadArtifactRepoPath).isPresent());

        // SBT: SBT layout
        String sbtLocalRepo = "sbt-local";
        String sbtPomPath = "sbt_example/sbt_example_2.11/1.1-SNAPSHOT/sbt_example_2.11-1.1-SNAPSHOT.pom";
        ModuleInfo sbtModuleInfo = new ModuleInfo("sbt_example", "sbt_example_2.11", "1.1",
                "SNAPSHOT", "SNAPSHOT", null, "pom",
                "pom", Collections.emptyMap());

        when(repoConfig.getBaseConfig().getRepoLayoutRef()).thenReturn(RepoLayoutUtils.SBT_DEFAULT_NAME);
        SbtMetadataProvider sbtMetadataProvider = new SbtMetadataProvider(repoService);
        when(repoService.repoConfigByKey(sbtLocalRepo)).thenReturn(repoConfig);

        leadArtifactRepoPath = new RepoPathImpl(sbtLocalRepo, sbtPomPath);
        when(repo.getItemModuleInfo(sbtPomPath)).thenReturn(sbtModuleInfo);
        when(repoService.repositoryByKey(leadArtifactRepoPath.getRepoKey())).thenReturn(repo);
        assertTrue(sbtMetadataProvider.buildVersionFilesQuery(leadArtifactRepoPath).isPresent());

        // SBT: Maven layout
        when(repoConfig.getBaseConfig().getRepoLayoutRef()).thenReturn(RepoLayoutUtils.MAVEN_2_DEFAULT_NAME);
        assertTrue(sbtMetadataProvider.buildVersionFilesQuery(leadArtifactRepoPath).isPresent());

        // SBT: Ivy layout
        when(repoConfig.getBaseConfig().getRepoLayoutRef()).thenReturn(RepoLayoutUtils.IVY_DEFAULT_NAME);
        assertTrue(sbtMetadataProvider.buildVersionFilesQuery(leadArtifactRepoPath).isPresent());

        // SBT: SBT-Ivy layout
        when(repoConfig.getBaseConfig().getRepoLayoutRef()).thenReturn(SBT_IVY_DEFAULT_NAME);
        assertTrue(sbtMetadataProvider.buildVersionFilesQuery(leadArtifactRepoPath).isPresent());

        // SBT: some other layout
        when(repoConfig.getBaseConfig().getRepoLayoutRef()).thenReturn(unsupportedLayout.getName());
        assertFalse(sbtMetadataProvider.buildVersionFilesQuery(leadArtifactRepoPath).isPresent());

        // Go: Go layout
        String goLocalRepo = "go-local";
        String goModPath = "jfrog.com/jfrog-go-commons/@v/v0.0.1-20190617090946-29.mod";
        ModuleInfo moduleInfo = new ModuleInfo(
                "jfrog.com", "jfrog-go-commons", "0.0.1", null,
                "20190617090946-29", null, "mod", "mod", Collections.emptyMap());

        when(repoConfig.getBaseConfig().getRepoLayoutRef()).thenReturn(RepoLayoutUtils.GO_DEFAULT_NAME);
        GoMetadataProvider goMetadataProvider = new GoMetadataProvider(repoService);
        when(repoService.repoConfigByKey(goLocalRepo)).thenReturn(repoConfig);

        leadArtifactRepoPath = new RepoPathImpl(goLocalRepo, goModPath);
        when(repo.getItemModuleInfo(goModPath)).thenReturn(moduleInfo);
        when(repoService.repositoryByKey(leadArtifactRepoPath.getRepoKey())).thenReturn(repo);
        assertTrue(goMetadataProvider.buildVersionFilesQuery(leadArtifactRepoPath).isPresent());

        // Go: some other layout
        when(repoConfig.getBaseConfig().getRepoLayoutRef()).thenReturn(unsupportedLayout.getName());
        assertFalse(goMetadataProvider.buildVersionFilesQuery(leadArtifactRepoPath).isPresent());
    }

    public void testRpmPkgIdWithComplexResolver() {
        RpmMetadataProvider rpmMetadataProvider = new RpmMetadataProvider();
        MutableMetadataEntityBOM bom = new MutableMetadataEntityBOM();
        FileInfoImpl fileInfo = new FileInfoImpl(new RepoPathImpl("rpm-local", "ImageMagick.rpm"));
        PropertiesImpl props = new PropertiesImpl();
        props.put("rpm.metadata.name", "ImageMagick");
        props.put("rpm.metadata.release", "2.cmplx1234.el6");
        rpmMetadataProvider.pkgIdResolver().apply(bom, props, fileInfo, null);
        assertEquals(bom.getPkgid(), "rpm://6:ImageMagick");
    }

    public void testRpmVersionResolver() {
        RpmMetadataProvider rpmMetadataProvider = new RpmMetadataProvider();
        MutableMetadataEntityBOM bom = new MutableMetadataEntityBOM();
        FileInfoImpl fileInfo = new FileInfoImpl(new RepoPathImpl("rpm-local", "ImageMagick.rpm"));
        PropertiesImpl props = new PropertiesImpl();
        props.put("rpm.metadata.name", "ImageMagick");
        props.put("rpm.metadata.version", "1.5.3");
        props.put("rpm.metadata.epoch", "1");
        props.put("rpm.metadata.release", "2.el7");

        rpmMetadataProvider.versionResolver().apply(bom, props, fileInfo, null);
        assertEquals(bom.getVersion(), "1:1.5.3-2.el7");

        props = new PropertiesImpl();
        bom = new MutableMetadataEntityBOM();
        rpmMetadataProvider.versionResolver().apply(new MutableMetadataEntityBOM(), props, fileInfo, null);
        assertNull(bom.getVersion());

        props = new PropertiesImpl();
        bom = new MutableMetadataEntityBOM();
        props.put("rpm.metadata.version", "1.5.3");
        props.put("rpm.metadata.epoch", "0");
        rpmMetadataProvider.versionResolver().apply(bom, props, fileInfo, null);
        assertEquals(bom.getVersion(), "0:1.5.3");

        props = new PropertiesImpl();
        bom = new MutableMetadataEntityBOM();
        props.put("rpm.metadata.version", "1.5.3");
        rpmMetadataProvider.versionResolver().apply(bom, props, fileInfo, null);
        assertEquals(bom.getVersion(), "1.5.3");
    }

    public void testMavenVersionPopulationUniqueSnapshotSupported() {
        Repo repo = mock(Repo.class);
        ModuleInfo moduleInfo = new ModuleInfo(
                "org.jfrog.test",
                "multi3",
                "2.17",
                "SNAPSHOT",
                "20200402.123134-8", null, "pom", null, Collections.emptyMap());
        InternalRepositoryService internalRepositoryService = mock(InternalRepositoryService.class);
        XrayAddon xrayAddonMock = mock(XrayAddon.class);
        AddonsManager addonsManagerMock = mock(AddonsManager.class);

        when(addonsManagerMock.addonByType(XrayAddon.class)).thenReturn(xrayAddonMock);
        when(internalRepositoryService.repositoryByKey(any())).thenReturn(repo);
        when(xrayAddonMock.isXrayEnabled()).thenReturn(true);
        when(xrayAddonMock.isXrayOfMinimalRequiredVersion(any())).thenReturn(true);
        when(repo.getItemModuleInfo(any())).thenReturn(moduleInfo);

        MavenMetadataProvider provider = new MavenMetadataProvider(internalRepositoryService, addonsManagerMock);

        MutableMetadataEntityBOM bom = new MutableMetadataEntityBOM();
        FileInfoImpl fileInfo = new FileInfoImpl(new RepoPathImpl(
                "maven-local",
                "org/jfrog/test/multi3/2.17-SNAPSHOT/multi3-2.17-20200402.123134-8.pom"
        ));
        provider.versionResolver().apply(bom, new PropertiesImpl(), fileInfo, null);
        assertEquals(bom.getVersion(), "2.17-20200402.123134-8");
    }

    public void testMavenVersionPopulationUniqueSnapshotSupportedNoXrayConfigured() {
        Repo repo = mock(Repo.class);
        ModuleInfo moduleInfo = new ModuleInfo(
                "org.jfrog.test",
                "multi3",
                "2.17",
                "SNAPSHOT",
                "20200402.123134-8", null, "pom", null, Collections.emptyMap());
        InternalRepositoryService internalRepositoryService = mock(InternalRepositoryService.class);
        XrayAddon xrayAddonMock = mock(XrayAddon.class);
        AddonsManager addonsManagerMock = mock(AddonsManager.class);

        when(addonsManagerMock.addonByType(XrayAddon.class)).thenReturn(xrayAddonMock);
        when(internalRepositoryService.repositoryByKey(any())).thenReturn(repo);
        when(xrayAddonMock.isDefault()).thenReturn(false);
        when(xrayAddonMock.isXrayConfigMissing()).thenReturn(true);
        when(repo.getItemModuleInfo(any())).thenReturn(moduleInfo);

        MavenMetadataProvider provider = new MavenMetadataProvider(internalRepositoryService, addonsManagerMock);

        MutableMetadataEntityBOM bom = new MutableMetadataEntityBOM();
        FileInfoImpl fileInfo = new FileInfoImpl(new RepoPathImpl(
                "maven-local",
                "org/jfrog/test/multi3/2.17-SNAPSHOT/multi3-2.17-20200402.123134-8.pom"
        ));
        provider.versionResolver().apply(bom, new PropertiesImpl(), fileInfo, null);
        assertEquals(bom.getVersion(), "2.17-20200402.123134-8");
    }

    public void testMavenVersionPopulationUniqueSnapshotSupportedOSS() {
        Repo repo = mock(Repo.class);
        ModuleInfo moduleInfo = new ModuleInfo(
                "org.jfrog.test",
                "multi3",
                "2.17",
                "SNAPSHOT",
                "20200402.123134-8", null, "pom", null, Collections.emptyMap());
        InternalRepositoryService internalRepositoryService = mock(InternalRepositoryService.class);
        XrayAddon xrayAddonMock = mock(XrayAddon.class);
        AddonsManager addonsManagerMock = mock(AddonsManager.class);

        when(addonsManagerMock.addonByType(XrayAddon.class)).thenReturn(xrayAddonMock);
        when(internalRepositoryService.repositoryByKey(any())).thenReturn(repo);
        when(xrayAddonMock.isDefault()).thenReturn(true);
        when(repo.getItemModuleInfo(any())).thenReturn(moduleInfo);

        MavenMetadataProvider provider = new MavenMetadataProvider(internalRepositoryService, addonsManagerMock);

        MutableMetadataEntityBOM bom = new MutableMetadataEntityBOM();
        FileInfoImpl fileInfo = new FileInfoImpl(new RepoPathImpl(
                "maven-local",
                "org/jfrog/test/multi3/2.17-SNAPSHOT/multi3-2.17-20200402.123134-8.pom"
        ));
        provider.versionResolver().apply(bom, new PropertiesImpl(), fileInfo, null);
        assertEquals(bom.getVersion(), "2.17-20200402.123134-8");
    }

    public void testMavenVersionPopulationUniqueSnapshotSupportedXrayDisabled() {
        Repo repo = mock(Repo.class);
        ModuleInfo moduleInfo = new ModuleInfo(
                "org.jfrog.test",
                "multi3",
                "2.17",
                "SNAPSHOT",
                "20200402.123134-8", null, "pom", null, Collections.emptyMap());
        InternalRepositoryService internalRepositoryService = mock(InternalRepositoryService.class);
        XrayAddon xrayAddonMock = mock(XrayAddon.class);
        AddonsManager addonsManagerMock = mock(AddonsManager.class);

        when(addonsManagerMock.addonByType(XrayAddon.class)).thenReturn(xrayAddonMock);
        when(internalRepositoryService.repositoryByKey(any())).thenReturn(repo);
        when(xrayAddonMock.isDefault()).thenReturn(false);
        when(xrayAddonMock.isXrayConfigMissing()).thenReturn(false);
        when(xrayAddonMock.isXrayEnabled()).thenReturn(false);
        when(repo.getItemModuleInfo(any())).thenReturn(moduleInfo);

        MavenMetadataProvider provider = new MavenMetadataProvider(internalRepositoryService, addonsManagerMock);

        MutableMetadataEntityBOM bom = new MutableMetadataEntityBOM();
        FileInfoImpl fileInfo = new FileInfoImpl(new RepoPathImpl(
                "maven-local",
                "org/jfrog/test/multi3/2.17-SNAPSHOT/multi3-2.17-20200402.123134-8.pom"
        ));
        provider.versionResolver().apply(bom, new PropertiesImpl(), fileInfo, null);
        assertEquals(bom.getVersion(), "2.17-20200402.123134-8");
    }

    public void testMavenVersionPopulationUniqueSnapshotUnsupportedXrayLowerThanMinVersion() {
        Repo repo = mock(Repo.class);
        ModuleInfo moduleInfo = new ModuleInfo(
                "org.jfrog.test",
                "multi3",
                "2.17",
                "SNAPSHOT",
                "20200402.123134-8", null, "pom", null, Collections.emptyMap());
        InternalRepositoryService internalRepositoryService = mock(InternalRepositoryService.class);
        XrayAddon xrayAddonMock = mock(XrayAddon.class);
        AddonsManager addonsManagerMock = mock(AddonsManager.class);

        when(addonsManagerMock.addonByType(XrayAddon.class)).thenReturn(xrayAddonMock);
        when(internalRepositoryService.repositoryByKey(any())).thenReturn(repo);
        when(xrayAddonMock.isXrayEnabled()).thenReturn(true);
        when(xrayAddonMock.isXrayOfMinimalRequiredVersion(any())).thenReturn(false);
        when(repo.getItemModuleInfo(any())).thenReturn(moduleInfo);

        MavenMetadataProvider provider = new MavenMetadataProvider(internalRepositoryService, addonsManagerMock);

        MutableMetadataEntityBOM bom = new MutableMetadataEntityBOM();
        FileInfoImpl fileInfo = new FileInfoImpl(new RepoPathImpl(
                "maven-local",
                "org/jfrog/test/multi3/2.17-SNAPSHOT/multi3-2.17-20200402.123134-8.pom"
        ));
        provider.versionResolver().apply(bom, new PropertiesImpl(), fileInfo, null);
        assertEquals(bom.getVersion(), "2.17-SNAPSHOT");
    }

    public void testLayoutBasedResolvers() {
        Repo repo = mock(Repo.class);
        InternalRepositoryService internalRepositoryService = mock(InternalRepositoryService.class);
        XrayAddon xrayAddonMock = mock(XrayAddon.class);
        AddonsManager addonsManagerMock = mock(AddonsManager.class);

        when(addonsManagerMock.addonByType(XrayAddon.class)).thenReturn(xrayAddonMock);
        when(internalRepositoryService.repositoryByKey("libs-release-local")).thenReturn(repo);
        when(repo.getDescriptor()).thenReturn(new LocalRepoDescriptor());
        when(repo.getItemModuleInfo("org/jfrog/artifactory/1.0.0/artifactory-1.0.0.pom"))
                .thenReturn(new ModuleInfo(
                        "org/jfrog",
                        "artifactory",
                        "1.0.0",
                        null,
                        null,
                        null,
                        "pom",
                        "pom",
                        Collections.emptyMap()));

        MavenMetadataProvider provider = new MavenMetadataProvider(internalRepositoryService, addonsManagerMock);

        MutableMetadataEntityBOM bom = new MutableMetadataEntityBOM();
        FileInfoImpl fileInfo = new FileInfoImpl(
                new RepoPathImpl("libs-release-local", "org/jfrog/artifactory/1.0.0/artifactory-1.0.0.pom"));

        provider.nameResolver().apply(bom, null, fileInfo, null);
        provider.versionResolver().apply(bom, null, fileInfo, null);
        provider.pkgIdResolver().apply(bom, null, fileInfo, null);

        assertEquals(bom.getName(), "org.jfrog:artifactory");
        assertEquals(bom.getVersion(), "1.0.0");
        assertEquals(bom.getPkgid(), "gav://org.jfrog:artifactory");
    }

    public void testLastModifiedMappedToVersionModified() {
        MutableMetadataEntityBOM bom = new MutableMetadataEntityBOM();
        FileInfoImpl fileInfo = new FileInfoImpl(
                new RepoPathImpl("libs-release-local", "org/jfrog/artifactory/1.0.0/artifactory-1.0.0.pom"));
        long lastModified = ClockUtils.epochMillis();
        fileInfo.setLastModified(lastModified);
        DockerMetadataProvider dockerMetadataProvider = spy(new DockerMetadataProvider());

        doReturn(new ArrayList<>()).when(dockerMetadataProvider).findVersionFiles(any());
        doNothing().when(dockerMetadataProvider).setVersionRepo(any(), any(), any());

        dockerMetadataProvider.fileInfoResolver().apply(bom, new PropertiesImpl(), fileInfo, null);
        assertEquals(bom.getModified(), lastModified);
    }

    public void testColonReplacedWithDoubleUnderscoreForFatManifest() {
        MutableMetadataEntityBOM bom = new MutableMetadataEntityBOM();
        FileInfoImpl fileInfo = new FileInfoImpl(
                new RepoPathImpl(
                        "docker-local",
                        "ubuntu/sha256__2f6fb811f836424acf6120ee35e3698ee65861f290332cb009c709c3d5088a82/manifest" +
                                         ".json"));
        DockerMetadataProvider dockerMetadataProvider = spy(new DockerMetadataProvider());

        doReturn(new ArrayList<>()).when(dockerMetadataProvider).findVersionFiles(any());
        doNothing().when(dockerMetadataProvider).setVersionRepo(any(), any(), any());

        PropertiesImpl properties = new PropertiesImpl();
        properties.put(dockerMetadataProvider.getType(properties) +
                               DockerMetadataProvider.DockerTypeSpecificMetadataTemplate.VERSION_SUFFIX,
                       "sha256:2f6fb811f836424acf6120ee35e3698ee65861f290332cb009c709c3d5088a82");
        dockerMetadataProvider.versionResolver().apply(bom, properties, fileInfo, null);
        assertEquals(bom.getVersion(), "sha256__2f6fb811f836424acf6120ee35e3698ee65861f290332cb009c709c3d5088a82");
    }

    public void testVersionUntouchedForNonFatManifest() {
        MutableMetadataEntityBOM bom = new MutableMetadataEntityBOM();
        FileInfoImpl fileInfo = new FileInfoImpl(
                new RepoPathImpl("docker-local",
                                 "ubuntu/latest/manifest.json"));
        DockerMetadataProvider dockerMetadataProvider = spy(new DockerMetadataProvider());

        doReturn(new ArrayList<>()).when(dockerMetadataProvider).findVersionFiles(any());
        doNothing().when(dockerMetadataProvider).setVersionRepo(any(), any(), any());

        PropertiesImpl properties = new PropertiesImpl();
        properties.put(dockerMetadataProvider.getType(properties) +
                               DockerMetadataProvider.DockerTypeSpecificMetadataTemplate.VERSION_SUFFIX,
                       "latest");
        dockerMetadataProvider.versionResolver().apply(bom, properties, fileInfo, null);
        assertEquals(bom.getVersion(), "latest");
    }

    public void testDockerGetType() {
        DockerMetadataProvider dockerMetadataProvider = new DockerMetadataProvider();
        PropertiesImpl properties = new PropertiesImpl();

        properties.put(MANIFEST_TYPE_PROP, "application/vnd.oci.image.manifest.v1+json");
        assertEquals(dockerMetadataProvider.getType(properties), "oci");

        properties.replaceValues(MANIFEST_TYPE_PROP, ImmutableList.of("application/vnd.oci.image.index.v1+json"));
        assertEquals(dockerMetadataProvider.getType(properties), "oci");

        properties.replaceValues(MANIFEST_TYPE_PROP,
                                 ImmutableList.of("application/vnd.docker.distribution.manifest.v2+json"));
        assertEquals(dockerMetadataProvider.getType(properties), "docker");

        properties.replaceValues(MANIFEST_TYPE_PROP,
                                 ImmutableList.of("application/vnd.docker.distribution.manifest.list.v2+json"));
        assertEquals(dockerMetadataProvider.getType(properties), "docker");

        properties.replaceValues(MANIFEST_TYPE_PROP, ImmutableList.of("some value"));
        assertEquals(dockerMetadataProvider.getType(properties), "docker");

        properties.removeAll(MANIFEST_TYPE_PROP);
        assertEquals(dockerMetadataProvider.getType(properties), "docker");
    }

    public void testIsLeadArtifact() {
        PypiMetadataProvider pypiMetadataProvider = new PypiMetadataProvider();
        assertTrue(pypiMetadataProvider.isLeadArtifact(new RepoPathImpl("pypi-local", "package.whl")));
        assertTrue(pypiMetadataProvider.isLeadArtifact(new RepoPathImpl("pypi-local", "package.tgz")));
        assertTrue(pypiMetadataProvider.isLeadArtifact(new RepoPathImpl("pypi-local", "package.tar.gz")));
        assertTrue(pypiMetadataProvider.isLeadArtifact(new RepoPathImpl("pypi-local", "package.egg")));
        assertTrue(pypiMetadataProvider.isLeadArtifact(new RepoPathImpl("pypi-local", "package.bz2")));
        assertTrue(pypiMetadataProvider.isLeadArtifact(new RepoPathImpl("pypi-local", "package.gz")));

        ChefMetadataProvider chefMetadataProvider = new ChefMetadataProvider();
        assertTrue(chefMetadataProvider.isLeadArtifact(new RepoPathImpl("chef-local", "package.tgz")));
        assertTrue(chefMetadataProvider.isLeadArtifact(new RepoPathImpl("chef-local", "package.tar.gz")));
        assertFalse(chefMetadataProvider.isLeadArtifact(new RepoPathImpl("chef-local", "package.pom")));

        BowerMetadataProvider bowerMetadataProvider = new BowerMetadataProvider();
        assertTrue(bowerMetadataProvider.isLeadArtifact(new RepoPathImpl("bower-local", "package.tgz")));
        assertTrue(bowerMetadataProvider.isLeadArtifact(new RepoPathImpl("bower-local", "package.tar.gz")));

        CocoaPodsMetadataProvider cocoaPodsMetadataProvider = new CocoaPodsMetadataProvider();
        assertTrue(cocoaPodsMetadataProvider.isLeadArtifact(new RepoPathImpl("cocoa-local", "package.tgz")));
        assertTrue(cocoaPodsMetadataProvider.isLeadArtifact(new RepoPathImpl("cocoa-local", "package.tar.gz")));

        DockerMetadataProvider dockerMetadataProvider = new DockerMetadataProvider();
        assertTrue(dockerMetadataProvider.isLeadArtifact(new RepoPathImpl("docker-local", "manifest.json")));
        assertFalse(dockerMetadataProvider.isLeadArtifact(new RepoPathImpl("docker-local", "no_manifest.json")));

        IvyMetadataProvider ivyMetadataProvider = new IvyMetadataProvider(null);
        assertTrue(ivyMetadataProvider.isLeadArtifact(new RepoPathImpl("ivy-local", "ivy-something.xml")));
        assertTrue(ivyMetadataProvider.isLeadArtifact(new RepoPathImpl("ivy-local", "ivy.xml")));
    }

    public void testIsPackageConsumptionLeadFile() {
        PypiMetadataProvider pypiMetadataProvider = new PypiMetadataProvider();
        assertTrue(pypiMetadataProvider.isPackageConsumptionLead(new RepoPathImpl("pypi-local", "package.whl")));
        assertTrue(pypiMetadataProvider.isPackageConsumptionLead(new RepoPathImpl("pypi-local", "package.tgz")));
        assertTrue(pypiMetadataProvider.isPackageConsumptionLead(new RepoPathImpl("pypi-local", "package.tar.gz")));
        assertTrue(pypiMetadataProvider.isPackageConsumptionLead(new RepoPathImpl("pypi-local", "package.egg")));
        assertTrue(pypiMetadataProvider.isPackageConsumptionLead(new RepoPathImpl("pypi-local", "package.bz2")));
        assertTrue(pypiMetadataProvider.isPackageConsumptionLead(new RepoPathImpl("pypi-local", "package.gz")));

        ChefMetadataProvider chefMetadataProvider = new ChefMetadataProvider();
        assertTrue(chefMetadataProvider.isPackageConsumptionLead(new RepoPathImpl("chef-local", "package.tgz")));
        assertTrue(chefMetadataProvider.isPackageConsumptionLead(new RepoPathImpl("chef-local", "package.tar.gz")));
        assertFalse(chefMetadataProvider.isPackageConsumptionLead(new RepoPathImpl("chef-local", "package.pom")));

        BowerMetadataProvider bowerMetadataProvider = new BowerMetadataProvider();
        assertTrue(bowerMetadataProvider.isPackageConsumptionLead(new RepoPathImpl("bower-local", "package.tgz")));
        assertTrue(bowerMetadataProvider.isPackageConsumptionLead(new RepoPathImpl("bower-local", "package.tar.gz")));

        CocoaPodsMetadataProvider cocoaPodsMetadataProvider = new CocoaPodsMetadataProvider();
        assertTrue(cocoaPodsMetadataProvider.isPackageConsumptionLead(new RepoPathImpl("cocoa-local", "package.tgz")));
        assertTrue(
                cocoaPodsMetadataProvider.isPackageConsumptionLead(new RepoPathImpl("cocoa-local", "package.tar.gz")));

        IvyMetadataProvider ivyMetadataProvider = new IvyMetadataProvider(null);
        assertTrue(ivyMetadataProvider.isPackageConsumptionLead(new RepoPathImpl("ivy-local", "ivy-something.xml")));
        assertTrue(ivyMetadataProvider.isPackageConsumptionLead(new RepoPathImpl("ivy-local", "ivy.xml")));

        DockerMetadataProvider dockerMetadataProvider = new DockerMetadataProvider();
        assertTrue(dockerMetadataProvider.isPackageConsumptionLead(new RepoPathImpl("docker-local", "manifest.json")));
        assertFalse(dockerMetadataProvider.isPackageConsumptionLead(
                new RepoPathImpl("docker-local", "list.manifest.json")));

        GoMetadataProvider goMetadataProvider = new GoMetadataProvider(null);
        assertTrue(goMetadataProvider.isPackageConsumptionLead(new RepoPathImpl("ivy-local", "go.mod")));
        assertFalse(goMetadataProvider.isPackageConsumptionLead(new RepoPathImpl("ivy-local", "go.zip")));

        ConanMetadataProvider conanMetadataProvider = new ConanMetadataProvider();
        assertTrue(conanMetadataProvider.isPackageConsumptionLead(new RepoPathImpl("conan-local",
                                                                                   "conan_package.tgz")));
        assertFalse(conanMetadataProvider.isPackageConsumptionLead(new RepoPathImpl("conan-local", "conanfile.py")));

        HuggingFaceMetadataProvider huggingFaceMetadataProvider = new HuggingFaceMetadataProvider();
        assertTrue(huggingFaceMetadataProvider.isPackageConsumptionLead(new RepoPathImpl("hf-local", "anyfilename")));
        assertFalse(huggingFaceMetadataProvider.isPackageConsumptionLead(
                new RepoPathImpl("hf-local", ".directory/.jfrog_huggingface_model_info.json")));
    }

    public void testIsLeadNoExtension() {
        BowerMetadataProvider bowerMetadataProvider = new BowerMetadataProvider();
        assertFalse(bowerMetadataProvider.isLeadArtifact(new RepoPathImpl("bower-local", "package")));
    }

    public void testTerraformModule() {
        setupMocks();
        TerraformMetadataProvider metadataProvider = new TerraformMetadataProvider();
        MutableMetadataEntityBOM bom = new MutableMetadataEntityBOM();
        FileInfoImpl fileInfo =
                new FileInfoImpl(new RepoPathImpl("terraform-local", "hashicorp/google-modules/google/1.0.0.zip"));
        PropertiesImpl props = new PropertiesImpl();
        props.put("terraform.type", "module");
        props.put("terraform.namespace", "hashicorp");
        props.put("terraform.name", "google-modules");
        props.put("terraform.provider", "google");
        props.put("terraform.version", "1.0.0");

        metadataProvider.nameResolver().apply(bom, props, fileInfo, null);
        metadataProvider.fileInfoResolver().apply(bom, props, fileInfo, null);
        metadataProvider.distributionResolver().apply(bom, props, fileInfo, null);
        metadataProvider.architectureResolver().apply(bom, props, fileInfo, null);
        metadataProvider.pkgIdResolver().apply(bom, props, fileInfo, null);
        metadataProvider.versionResolver().apply(bom, props, fileInfo, null);

        assertNull(bom.getArtifactMetadata().get(0).getArch());
        assertEquals(bom.getName(), "hashicorp/google-modules/google");
        assertEquals(bom.getPkgid(), "terraform://hashicorp/google-modules/google");
        assertEquals(bom.getVersion(), "1.0.0");
        assertEquals(metadataProvider.getType(props), "terraformmodule");
    }

    public void testTerraformProvider() {
        setupMocks();

        TerraformMetadataProvider metadataProvider = new TerraformMetadataProvider();
        MutableMetadataEntityBOM bom = new MutableMetadataEntityBOM();
        FileInfoImpl fileInfo = new FileInfoImpl(new RepoPathImpl(
                "terraform-local", "hashicorp/google/terraform-provider-google_1.0.0_darwin_amd64.zip"));
        PropertiesImpl props = new PropertiesImpl();
        props.put("terraform.type", "provider");
        props.put("terraform.namespace", "hashicorp");
        props.put("terraform.name", "google");
        props.put("terraform.version", "1.0.0");
        props.put("terraform.flavor", "darwin_amd64");

        metadataProvider.nameResolver().apply(bom, props, fileInfo, null);
        metadataProvider.fileInfoResolver().apply(bom, props, fileInfo, null);
        metadataProvider.distributionResolver().apply(bom, props, fileInfo, null);
        metadataProvider.architectureResolver().apply(bom, props, fileInfo, null);
        metadataProvider.pkgIdResolver().apply(bom, props, fileInfo, null);
        metadataProvider.versionResolver().apply(bom, props, fileInfo, null);

        assertEquals(bom.getArtifactMetadata().get(0).getArch(), "darwin_amd64");
        assertEquals(bom.getName(), "hashicorp/google");
        assertEquals(bom.getPkgid(), "terraform://hashicorp/google");
        assertEquals(bom.getVersion(), "1.0.0");
        assertEquals(metadataProvider.getType(props), "terraformprovider");
    }

    public void testDockerpkgIdResolver() {
        DockerMetadataProvider dockerMetadataProvider = new DockerMetadataProvider();
        MutableMetadataEntityBOM bom = new MutableMetadataEntityBOM();
        FileInfoImpl fileInfo = new FileInfoImpl(
                new RepoPathImpl("docker-local", "alpine/v1/manifest.json"));
        PropertiesImpl props = new PropertiesImpl();
        props.put("artifactory.content-type", "application/vnd.docker.distribution.manifest.v2+json");
        props.put("docker.manifest", "v1");
        props.put("docker.manifest.digest", "sha256:123");
        props.put("docker.manifest.type", "application/vnd.docker.distribution.manifest.v2+json");
        props.put("docker.repoName", "alpine");
        props.put("sha256", "123");

        dockerMetadataProvider.pkgIdResolver().apply(bom, props, fileInfo, null);

        assertEquals(bom.getPkgid(), "docker://alpine");
    }

    public void testOciPkgIdResolver() {
        OciMetadataProvider ociMetadataProvider = new OciMetadataProvider();
        MutableMetadataEntityBOM bom = new MutableMetadataEntityBOM();
        FileInfoImpl fileInfo = new FileInfoImpl(
                new RepoPathImpl("oci-local", "wasm/v1/manifest.json"));
        PropertiesImpl props = new PropertiesImpl();
        props.put("artifactory.content-type", "application/vnd.oci.image.manifest.v1+json");
        props.put("docker.manifest", "v1");
        props.put("docker.manifest.digest", "sha256:123");
        props.put("docker.manifest.type", "application/vnd.oci.image.manifest.v1+json");
        props.put("docker.repoName", "wasm");
        props.put("sha256", "123");

        ociMetadataProvider.pkgIdResolver().apply(bom, props, fileInfo, null);

        assertEquals(bom.getPkgid(), "docker://wasm");
    }

    public void testHexMetadataProvider() {
        setupMocks();
        HexMetadataProvider metadataProvider = new HexMetadataProvider();
        MutableMetadataEntityBOM bom = new MutableMetadataEntityBOM();
        FileInfoImpl fileInfo = new FileInfoImpl(new RepoPathImpl(
                "hex-local", "hex-local/jfrog-test-2.5.1.tar"));
        PropertiesImpl props = new PropertiesImpl();
        props.put("hex.name", "test");
        props.put("hex.version", "2.5.1");

        metadataProvider.nameResolver().apply(bom, props, fileInfo, null);
        metadataProvider.fileInfoResolver().apply(bom, props, fileInfo, null);
        metadataProvider.pkgIdResolver().apply(bom, props, fileInfo, null);
        metadataProvider.versionResolver().apply(bom, props, fileInfo, null);
        metadataProvider.tagsResolver().apply(bom, props, fileInfo, null);
        metadataProvider.userPropsResolver().apply(bom, props, fileInfo, null);

        assertEquals(metadataProvider.getLeadArtifactExtensions(), Collections.singletonList("tar"));
        assertEquals(metadataProvider.typeSpecificMetadataTemplate.getVersionKey(), Optional.of("hex.version"));
        assertEquals(metadataProvider.getType(props), "hex");
        assertEquals(bom.getName(), "test");
        assertEquals(bom.getPkgid(), "hex://test");
        assertEquals(bom.getVersion(), "2.5.1");
    }


    public void testAnsibleMetadataProvider() {
        setupMocks();
        AnsibleMetadataProvider metadataProvider = new AnsibleMetadataProvider();
        MutableMetadataEntityBOM bom = new MutableMetadataEntityBOM();
        FileInfoImpl fileInfo = new FileInfoImpl(new RepoPathImpl(
                "ansible-local", "ansible-local/collections/jfrog/test/2.5.1/jfrog-test-2.5.1.tar.gz"));
        PropertiesImpl props = new PropertiesImpl();
        props.put("ansible.tags", "[\"tag1\",\"tag2\",\"tag3\"]");
        props.put("ansible.namespace", "jfrog");
        props.put("ansible.name", "test");
        props.put("ansible.version", "2.5.1");
        props.put("ansible.requires", ">=2.9.10");
        props.put("ansible.id", "98c4d46d-4ed0-3390-9383-88b286df580a");
        props.put("ansible.fullname", "jfrog.test");

        metadataProvider.nameResolver().apply(bom, props, fileInfo, null);
        metadataProvider.fileInfoResolver().apply(bom, props, fileInfo, null);
        metadataProvider.pkgIdResolver().apply(bom, props, fileInfo, null);
        metadataProvider.versionResolver().apply(bom, props, fileInfo, null);
        metadataProvider.tagsResolver().apply(bom, props, fileInfo, null);
        metadataProvider.userPropsResolver().apply(bom, props, fileInfo, null);

        assertEquals(metadataProvider.getLeadArtifactExtensions(), Collections.singletonList("tar.gz"));
        assertEquals(metadataProvider.typeSpecificMetadataTemplate.getVersionKey(), Optional.of("ansible.version"));
        assertEquals(metadataProvider.getType(props), "ansible");
        assertEquals(bom.getName(), "jfrog.test");
        assertEquals(bom.getPkgid(), "ansible://jfrog.test");
        assertEquals(bom.getVersion(), "2.5.1");
        assertTrue(bom.getTags().contains("tag1"));
        assertTrue(bom.getTags().contains("tag2"));
        assertTrue(bom.getTags().contains("tag3"));

        Map<String, Set<String>> userProperties = bom.getUserProperties();
        assertEquals(userProperties.get("ansible.namespace").iterator().next(), "jfrog");
        assertEquals(userProperties.get("ansible.name").iterator().next(), "test");
        assertEquals(userProperties.get("ansible.requires").iterator().next(), ">=2.9.10");
        assertEquals(userProperties.get("ansible.id").iterator().next(), "98c4d46d-4ed0-3390-9383-88b286df580a");
    }

    public void testP2MetadataProvider() {
        setupMocks();
        CacheExpiry cacheExpiry = mock(CacheExpiryImpl.class);
        P2MetadataProvider metadataProvider = new P2MetadataProvider();
        metadataProvider.setCacheExpiry(cacheExpiry);
        when(cacheExpiry.isExpirable(RepoType.P2, "p2-remote-cache",
                                     "org.eclipse.dltk.debug.ui_4.0.0.201206120848.jar")).thenReturn(true);
        MutableMetadataEntityBOM bom = new MutableMetadataEntityBOM();
        FileInfoImpl fileInfo = new FileInfoImpl(new RepoPathImpl(
                "p2-remote-cache", "p2-remote-cache/org.eclipse.dltk.debug.ui_4.0.0.201206120848.jar"));
        PropertiesImpl props = new PropertiesImpl();

        metadataProvider.nameResolver().apply(bom, props, fileInfo, null);
        metadataProvider.fileInfoResolver().apply(bom, props, fileInfo, null);
        metadataProvider.pkgIdResolver().apply(bom, props, fileInfo, null);
        metadataProvider.versionResolver().apply(bom, props, fileInfo, null);

        assertEquals(metadataProvider.getLeadArtifactExtensions(), Set.of("jar", "jar.pack.gz"));
        assertEquals(metadataProvider.getType(props), "p2");
        assertEquals(bom.getName(), "org.eclipse.dltk.debug.ui");
        assertEquals(bom.getPkgid(), "p2://org.eclipse.dltk.debug.ui");
        assertEquals(bom.getVersion(), "4.0.0.201206120848");
        assertTrue(metadataProvider.isPackageConsumptionLead(fileInfo.getRepoPath()));
    }


    public void testP2MetadataProviderWithJarPack() {
        setupMocks();
        CacheExpiry cacheExpiry = mock(CacheExpiryImpl.class);
        P2MetadataProvider metadataProvider = new P2MetadataProvider();
        metadataProvider.setCacheExpiry(cacheExpiry);
        when(cacheExpiry.isExpirable(RepoType.P2, "p2-remote-cache",
                                     "org.eclipse.cdt.launch_7.1.0.201302132326.jar.pack.gz")).thenReturn(true);
        MutableMetadataEntityBOM bom = new MutableMetadataEntityBOM();
        FileInfoImpl fileInfo = new FileInfoImpl(new RepoPathImpl(
                "p2-remote-cache", "p2-remote-cache/org.eclipse.cdt.launch_7.1.0.201302132326.jar.pack.gz"));
        PropertiesImpl props = new PropertiesImpl();

        metadataProvider.nameResolver().apply(bom, props, fileInfo, null);
        metadataProvider.fileInfoResolver().apply(bom, props, fileInfo, null);
        metadataProvider.pkgIdResolver().apply(bom, props, fileInfo, null);
        metadataProvider.versionResolver().apply(bom, props, fileInfo, null);

        assertEquals(metadataProvider.getLeadArtifactExtensions(), Set.of("jar", "jar.pack.gz"));
        assertEquals(metadataProvider.getType(props), "p2");
        assertEquals(bom.getName(), "org.eclipse.cdt.launch");
        assertEquals(bom.getPkgid(), "p2://org.eclipse.cdt.launch");
        assertEquals(bom.getVersion(), "7.1.0.201302132326");
        assertTrue(metadataProvider.isPackageConsumptionLead(fileInfo.getRepoPath()));
        assertTrue(bom.getUserProperties().isEmpty());
    }

    public void testP2IsPackageConsumptionLead() {
        setupMocks();
        CacheExpiry cacheExpiry = mock(CacheExpiryImpl.class);
        P2MetadataProvider metadataProvider = new P2MetadataProvider();
        metadataProvider.setCacheExpiry(cacheExpiry);
        when(cacheExpiry.isExpirable(RepoType.P2, "p2-local", "artifacts.jar")).thenReturn(true);
        when(cacheExpiry.isExpirable(RepoType.P2, "p2-local", "p2.index")).thenReturn(true);
        assertTrue(metadataProvider.isPackageConsumptionLead(
                new RepoPathImpl("p2-local", "org.eclipse.dltk.debug.ui_4.0.0.201206120848.jar")));
        assertTrue(metadataProvider.isPackageConsumptionLead(
                new RepoPathImpl("p2-local", "org.eclipse.dltk.debug.ui_4.0.0.201206120848.jar.pack.gz")));
        assertFalse(metadataProvider.isPackageConsumptionLead(
                new RepoPathImpl("p2-local", "org.eclipse.dltk.debug.ui_4.0.0.201206120848")));
        assertFalse(metadataProvider.isPackageConsumptionLead(new RepoPathImpl("p2-local", "artifacts.jar")));
        assertFalse(metadataProvider.isPackageConsumptionLead(new RepoPathImpl("p2-local", "p2.index")));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "suffix",
            "null"
    }, nullValues = "null")
    void getPkgid(String suffix) {
        RepoPath repoPath = new RepoPathImpl("repoKey", "path/to/artifact");
        Properties properties = new PropertiesImpl();
        AbstractMetadataProvider metadataProvider = getAbstractMetadataProvider(repoPath, properties, suffix);

        String actualPkid = metadataProvider.getPkgid(repoPath, properties);
        String expectedPkid = isNull(suffix) ? null : "gav://" + suffix;
        assertEquals(expectedPkid, actualPkid);
    }

    private static void setupMocks() {
        ArtifactoryContext artifactoryContext = Mockito.mock(InternalArtifactoryContext.class);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        RepositoryService repositoryService = mock(RepositoryService.class);
        LocalRepoDescriptor localRepoDescriptor = mock(LocalRepoDescriptor.class);
        when(localRepoDescriptor.isLocal()).thenReturn(true);
        when(localRepoDescriptor.isCache()).thenReturn(true);
        when(artifactoryContext.beanForType(RepositoryService.class)).thenReturn(repositoryService);
        when(repositoryService.localFederatedOrCachedRepoDescriptorByKey(any())).thenReturn(localRepoDescriptor);
    }

    @NonNull
    private static AbstractMetadataProvider getAbstractMetadataProvider(
            RepoPath repoPath, Properties properties, String suffix) {
        AbstractMetadataProvider metadataProvider = new AbstractMetadataProvider(RepoType.Maven) {
            @Override
            protected String getType(@Nonnull Properties properties) {
                return "maven";
            }
        };
        TypeSpecificMetadataTemplate typeSpecificMetadataTemplate = mock(TypeSpecificMetadataTemplate.class);
        when(typeSpecificMetadataTemplate.getPkgidSuffix(repoPath, properties)).thenReturn(suffix);
        metadataProvider.setTypeSpecificMetadataTemplate(typeSpecificMetadataTemplate);
        return metadataProvider;
    }

    public void testIsLeadArtifactMatchRepoLayout() {
        InternalRepositoryService repoService = mock(InternalRepositoryService.class);
        RepoConfig repoConfig = mock(RepoConfig.class);
        BaseConfig baseConfig = mock(BaseConfig.class);
        when(repoConfig.getBaseConfig()).thenReturn(baseConfig);

        // SBT: check list of layouts
        String sbtLocalRepo = "sbt-local";
        String sbtPomPath = "sbt_example/sbt_example_2.11/1.1-SNAPSHOT/sbt_example_2.11-1.1-SNAPSHOT.pom";
        Set<String> layouts = Set.of(
                RepoLayoutUtils.MAVEN_2_DEFAULT_NAME,
                RepoLayoutUtils.SBT_DEFAULT_NAME,
                RepoLayoutUtils.IVY_DEFAULT_NAME,
                RepoLayoutUtils.SBT_IVY_DEFAULT_NAME
        );
        RepoPath leadArtifactRepoPath = new RepoPathImpl(sbtLocalRepo, sbtPomPath);
        SbtMetadataProvider sbtMetadataProvider = new SbtMetadataProvider(repoService);
        when(repoService.repoConfigByKey(sbtLocalRepo)).thenReturn(repoConfig);

        assertFalse(sbtMetadataProvider.isLeadArtifactMatchRepoLayouts(repoService, leadArtifactRepoPath, layouts));
        when(repoConfig.getBaseConfig().getRepoLayoutRef()).thenReturn(RepoLayoutUtils.SBT_DEFAULT_NAME);
        assertTrue(sbtMetadataProvider.isLeadArtifactMatchRepoLayouts(repoService, leadArtifactRepoPath, layouts));

        // GO: Check single layout
        String goLocalRepo = "go-local";
        String goModPath = "jfrog.com/jfrog-go-commons/@v/v0.0.1-20190617090946-29.mod";

        leadArtifactRepoPath = new RepoPathImpl(goLocalRepo, goModPath);
        GoMetadataProvider goMetadataProvider = new GoMetadataProvider(repoService);
        when(repoService.repoConfigByKey(goLocalRepo)).thenReturn(repoConfig);

        assertFalse(goMetadataProvider.isLeadArtifactMatchRepoLayout(
                repoService, leadArtifactRepoPath, RepoLayoutUtils.GO_DEFAULT_NAME));
        when(repoConfig.getBaseConfig().getRepoLayoutRef()).thenReturn(RepoLayoutUtils.GO_DEFAULT_NAME);
        assertTrue(goMetadataProvider.isLeadArtifactMatchRepoLayout(
                repoService, leadArtifactRepoPath, RepoLayoutUtils.GO_DEFAULT_NAME));

    }
}
