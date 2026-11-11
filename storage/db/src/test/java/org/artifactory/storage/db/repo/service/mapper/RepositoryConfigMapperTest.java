package org.artifactory.storage.db.repo.service.mapper;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.gson.JsonParser;
import org.apache.commons.io.FileUtils;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.home.TestArtifactoryHome;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.xray.XrayNotAvailableMode;
import org.artifactory.storage.db.repo.entity.DbRepositoryConfig;
import org.jfrog.common.JsonUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static java.lang.reflect.Modifier.isFinal;
import static org.artifactory.storage.db.repo.service.mapper.RepositoryConfigMapperTest.RandomObjectPopulator.createAndFill;
import static org.junit.Assert.assertEquals;

/**
 * @author Tamar Rosen
 */
class RepositoryConfigMapperTest {
    private static RepositoryConfigMapperImpl mapper;

    @BeforeAll
    static void setup() throws Exception {
        createAndBindToArtifactoryHome();
        mapper = new RepositoryConfigMapperImpl();
    }

    @AfterAll
    static void afterClass() {
        unbindAndCleanHome();
    }

    @BeforeEach
    void resetReaderState() {
        // The reader's "log unknown type once per key" set is process-global; clear it so each test starts clean.
        RepoConfigBlobReader.clearLoggedUnknownTypeKeys();
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.repoConfigSkipUnknownPackageType.getPropertyName(), "true");
    }

    @Test
    void testLocalRepositoryConfigToDbRepositoryConfig() throws Exception {
        LocalRepoConfig originalRepoConfig = createAndFill(LocalRepoConfig.class);
        DbRepositoryConfig dbRepositoryConfig = mapper.repositoryConfigToDbRepositoryConfig(originalRepoConfig);
        RepoConfig afterMappingRepoConfig = mapper.dbInfoBlobToRepositoryConfig(dbRepositoryConfig);

        String originalRepoConfigJson = JsonUtils.getInstance().valueToString(originalRepoConfig, true);
        String afterMappingRepoConfigJson = JsonUtils.getInstance().valueToString(afterMappingRepoConfig, true);

        assertEquals(JsonParser.parseString(originalRepoConfigJson),
                JsonParser.parseString(afterMappingRepoConfigJson));
    }

    @Test
    void testFederatedRepositoryConfigToDbRepositoryConfig() throws Exception {
        FederatedRepoConfig originalRepoConfig = createAndFill(FederatedRepoConfig.class);
        originalRepoConfig.getRepoTypeConfig().setCdnRedirect(true);
        originalRepoConfig.getRepoTypeConfig().setDownloadRedirect(true);

        DbRepositoryConfig dbRepositoryConfig = mapper.repositoryConfigToDbRepositoryConfig(originalRepoConfig);
        RepoConfig afterMappingRepoConfig = mapper.dbInfoBlobToRepositoryConfig(dbRepositoryConfig);

        String originalRepoConfigJson = JsonUtils.getInstance().valueToString(originalRepoConfig, true);
        String afterMappingRepoConfigJson = JsonUtils.getInstance().valueToString(afterMappingRepoConfig, true);

        assertEquals(JsonParser.parseString(originalRepoConfigJson),
                JsonParser.parseString(afterMappingRepoConfigJson));
    }

    @Test
    void testRemoteRepositoryConfigToDbRepositoryConfig() throws Exception {
        RemoteRepoConfig originalRepoConfig = createAndFill(RemoteRepoConfig.class);
        DbRepositoryConfig dbRepositoryConfig = mapper.repositoryConfigToDbRepositoryConfig(originalRepoConfig);
        RepoConfig afterMappingRepoConfig = mapper.dbInfoBlobToRepositoryConfig(dbRepositoryConfig);

        String originalRepoConfigJson = JsonUtils.getInstance().valueToString(originalRepoConfig, true);
        String afterMappingRepoConfigJson = JsonUtils.getInstance().valueToString(afterMappingRepoConfig, true);

        assertEquals(JsonParser.parseString(originalRepoConfigJson),
                JsonParser.parseString(afterMappingRepoConfigJson));
    }

    @Test
    void testVirtualRepositoryConfigToDbRepositoryConfig() throws Exception {
        VirtualRepoConfig originalRepoConfig = createAndFill(VirtualRepoConfig.class);
        DbRepositoryConfig dbRepositoryConfig = mapper.repositoryConfigToDbRepositoryConfig(originalRepoConfig);
        RepoConfig afterMappingRepoConfig = mapper.dbInfoBlobToRepositoryConfig(dbRepositoryConfig);

        String originalRepoConfigJson = JsonUtils.getInstance().valueToString(originalRepoConfig, true);
        String afterMappingRepoConfigJson = JsonUtils.getInstance().valueToString(afterMappingRepoConfig, true);

        assertEquals(JsonParser.parseString(originalRepoConfigJson),
                JsonParser.parseString(afterMappingRepoConfigJson));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testDbRepositoryConfigToRepositoryConfig(boolean xrayNotAvailableMode) {
        DbRepositoryConfig dbRepositoryConfig = new DbRepositoryConfig();
        LocalRepoConfig repositoryConfig = LocalRepoConfig.create("my-repo", RepoType.Conan);
        dbRepositoryConfig.setKey("my-repo");
        dbRepositoryConfig.setType("local");
        dbRepositoryConfig.setRepoTypeV2("local");
        dbRepositoryConfig.setPackageType("conan");
        repositoryConfig.getBaseConfig().setModelVersion(1);
        repositoryConfig.getSecurityConfig().setXrayDataTtl(repositoryConfig.getSecurityConfig().getXrayDataTtl());
        repositoryConfig.getSecurityConfig().setXrayIndex(repositoryConfig.getSecurityConfig().isXrayIndex());
        if (xrayNotAvailableMode) {
            repositoryConfig.getSecurityConfig().getXrayConfig()
                    .setXrayNotAvailableMode(XrayNotAvailableMode.FAIL_OPEN);
        } else {
            repositoryConfig.getSecurityConfig().getXrayConfig().setXrayNotAvailableMode(null);
        }
        repositoryConfig.getSecurityConfig()
                .setXrayNotAvailableMode(repositoryConfig.getSecurityConfig().getXrayNotAvailableMode());
        byte[] bytes = JsonUtils.getInstance().valueToByteArray(repositoryConfig);
        dbRepositoryConfig.setRepositoryInfoBlob(bytes);

        RepoConfig repositoryConfig1 = mapper.dbInfoBlobToRepositoryConfig(dbRepositoryConfig);
        DbRepositoryConfig dbRepositoryConfig1 = mapper.repositoryConfigToDbRepositoryConfig(repositoryConfig1);

        String dbRepoConfig1Json = JsonUtils.getInstance().valueToString(dbRepositoryConfig1, true);
        String dbRepoConfigJson = JsonUtils.getInstance().valueToString(dbRepositoryConfig, true);

        assertEquals(JsonParser.parseString(dbRepoConfigJson), JsonParser.parseString(dbRepoConfig1Json));
    }

    @Test
    void testDbInfoBlobToRepositoryConfigReturnsNullForNullInput() {
        Assertions.assertNull(mapper.dbInfoBlobToRepositoryConfig(null));
    }

    @Test
    void testBlobReaderReadsKnownTypeRoundTrip() {
        LocalRepoConfig repositoryConfig = LocalRepoConfig.create("maven-repo", RepoType.Maven);
        repositoryConfig.getBaseConfig().setModelVersion(1);
        byte[] blob = JsonUtils.getInstance().valueToByteArray(repositoryConfig);

        RepoConfig result = RepoConfigBlobReader.read(dbRepoWith("maven-repo", "maven", blob));

        assertEquals("maven-repo", result.getKey());
        assertEquals(RepoType.Maven, result.getPackageType());
    }

    @Test
    void testBlobReaderResolvesSupportBundlesToSupportWithoutAlias() {
        RepoConfig result = RepoConfigBlobReader.read(
                dbRepoWith("support-repo", "supportbundles", blobWithPackageTypeToken("supportbundles")));

        assertEquals(RepoType.Support, result.getPackageType());
    }

    @Test
    void testBlobReaderUnknownTypeSkippedByDefaultReturnsNull() {
        // default: repoConfigSkipUnknownPackageType = true
        RepoConfig result = RepoConfigBlobReader.read(
                dbRepoWith("future-repo", "skillsfuture", blobWithPackageTypeToken("skillsfuture")));

        Assertions.assertNull(result, "an unknown package type must be skipped (null) under the default skip policy");
    }

    @Test
    void testBlobReaderUnknownTypeLoadedAsGenericWhenSkipDisabled() {
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.repoConfigSkipUnknownPackageType.getPropertyName(), "false");

        DbRepositoryConfig dbRepo = dbRepoWith("future-repo", "skillsfuture",
                blobWithPackageTypeToken("skillsfuture"));
        dbRepo.setModified(123L);
        dbRepo.setCreated(456L);

        RepoConfig result = mapper.dbInfoBlobToRepositoryConfig(dbRepo);

        assertEquals("future-repo", result.getKey());
        assertEquals(RepoType.Generic, result.getPackageType());
        assertEquals(123L, result.getModification());
        assertEquals(456L, result.getCreation());
    }

    @Test
    void testBlobReaderLogsUnknownTypeOncePerKey() {
        Logger logger = (Logger) LoggerFactory.getLogger(RepoConfigBlobReader.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            DbRepositoryConfig dbRepo = dbRepoWith("future-repo", "skillsfuture",
                    blobWithPackageTypeToken("skillsfuture"));

            // multiple reads of the same key (e.g. repeated cache reloads)
            RepoConfigBlobReader.read(dbRepo);
            RepoConfigBlobReader.read(dbRepo);
            RepoConfigBlobReader.read(dbRepo);

            long count = appender.list.stream()
                    .filter(e -> e.getLevel() == Level.ERROR)
                    .filter(e -> e.getFormattedMessage().contains("future-repo"))
                    .filter(e -> e.getFormattedMessage().contains("skillsfuture"))
                    .count();
            Assertions.assertEquals(1, count, "the unknown package type must be logged exactly once per key");
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void testBlobReaderLogsErrorWithLoadAsGenericActionWhenSkipDisabled() {
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.repoConfigSkipUnknownPackageType.getPropertyName(), "false");

        Logger logger = (Logger) LoggerFactory.getLogger(RepoConfigBlobReader.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            RepoConfigBlobReader.read(
                    dbRepoWith("future-repo", "skillsfuture", blobWithPackageTypeToken("skillsfuture")));

            List<ILoggingEvent> unknownTypeEvents = appender.list.stream()
                    .filter(e -> e.getFormattedMessage().contains("future-repo"))
                    .filter(e -> e.getFormattedMessage().contains("skillsfuture"))
                    .toList();

            Assertions.assertEquals(1, unknownTypeEvents.size(),
                    "load-as-Generic must log the unknown package type exactly once");
            ILoggingEvent event = unknownTypeEvents.get(0);
            Assertions.assertEquals(Level.ERROR, event.getLevel(),
                    "load-as-Generic must log at ERROR, matching the skip path");
            Assertions.assertTrue(event.getFormattedMessage().contains("loading it as Generic in memory"),
                    "the action text must state the load-as-Generic outcome, got: " + event.getFormattedMessage());
            Assertions.assertTrue(event.getFormattedMessage()
                            .contains(ConstantValues.repoConfigSkipUnknownPackageType.getPropertyName() + "=true"),
                    "the action text must name the flag and the value to flip, got: " + event.getFormattedMessage());
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void testBlobReaderThrowsIllegalStateOnCorruptBlob() {
        DbRepositoryConfig corrupt = dbRepoWith("corrupt-repo", "generic",
                "{ this is not valid json".getBytes(StandardCharsets.UTF_8));
        Assertions.assertThrows(IllegalStateException.class, () -> RepoConfigBlobReader.read(corrupt));
    }

    @Test
    void testBlobReaderThrowsIllegalStateOnNullBlob() {
        DbRepositoryConfig nullBlob = dbRepoWith("null-blob-repo", "generic", null);
        Assertions.assertThrows(IllegalStateException.class, () -> RepoConfigBlobReader.read(nullBlob));
    }

    @Test
    void testBlobReaderThrowsIllegalStateOnEmptyBlob() {
        DbRepositoryConfig emptyBlob = dbRepoWith("empty-blob-repo", "generic", new byte[0]);
        Assertions.assertThrows(IllegalStateException.class, () -> RepoConfigBlobReader.read(emptyBlob));
    }

    @Test
    void testBlobReaderWithNullPackageTypeColumnLoadsGenericWithoutCoercion() {
        // Scope: this locks the contract of the null-column guard in isCoercedToGeneric and covers that branch; it is
        // not a real production scenario. The package_type column is NOT NULL in every dialect, so a null column only
        // arises for an in-memory POJO (a fixture like this), never for a row read from the DB. Kept as a cheap branch
        // tripwire: a genuinely Generic blob with a null column must load as-is and never be treated as a coerced
        // unknown type. If the guard's policy changes (e.g. treat null as coerced), update this assertion rather than
        // deleting the test, so the branch stays locked to the new behavior.
        LocalRepoConfig repositoryConfig = LocalRepoConfig.create("generic-repo", RepoType.Generic);
        repositoryConfig.getBaseConfig().setModelVersion(1);
        byte[] genericBlob = JsonUtils.getInstance().valueToByteArray(repositoryConfig);

        RepoConfig result = RepoConfigBlobReader.read(dbRepoWith("generic-repo", null, genericBlob));

        Assertions.assertNotNull(result, "a Generic blob with a null package-type column must still load");
        assertEquals(RepoType.Generic, result.getPackageType());
    }

    private static DbRepositoryConfig dbRepoWith(String key, String packageTypeColumn, byte[] blob) {
        DbRepositoryConfig dbRepo = new DbRepositoryConfig();
        dbRepo.setKey(key);
        dbRepo.setType("local");
        dbRepo.setPackageType(packageTypeColumn);
        dbRepo.setRepositoryInfoBlob(blob);
        return dbRepo;
    }

    /**
     * Serializes a valid local repository config and rewrites only its {@code packageType} token, so the blob is well
     * formed but carries a package type that may be unknown to the running Artifactory version (the post-rollback
     * scenario).
     */
    private static byte[] blobWithPackageTypeToken(String packageTypeToken) {
        LocalRepoConfig repositoryConfig = LocalRepoConfig.create("future-repo", RepoType.Generic);
        repositoryConfig.getBaseConfig().setModelVersion(1);
        String json = new String(JsonUtils.getInstance().valueToByteArray(repositoryConfig), StandardCharsets.UTF_8);
        String patched = json.replaceFirst("(\"packageType\"\\s*:\\s*)\"generic\"", "$1\"" + packageTypeToken + "\"");
        Assertions.assertNotEquals(json, patched,
                "test setup: expected a packageType=generic token to patch, but blob was: " + json);
        return patched.getBytes(StandardCharsets.UTF_8);
    }

    @ParameterizedTest
    @EnumSource(RepoDetailsType.class)
    void testRepoDetailsTypeRoundTripThroughAttributesMapper(RepoDetailsType original) {
        RepositoryConfigAttributesMapper attrMapper = new RepositoryConfigAttributesMapperImpl();
        String persisted = attrMapper.repoDetailTypeToLowercaseRepoTypeName(original);
        RepoDetailsType restored = attrMapper.repoTypeNameToRepoDetailsType(persisted);
        assertEquals(original, restored);
    }

    /**
     * repo_type_v2 must mirror the v284 backfill SQL: federated -> local, every other type identical to
     * the persisted lowercase type name.
     */
    @ParameterizedTest
    @EnumSource(RepoDetailsType.class)
    void repoTypeV2MirrorsTypeExceptFederatedMapsToLocal(RepoDetailsType type) {
        RepositoryConfigAttributesMapper attrMapper = new RepositoryConfigAttributesMapperImpl();
        String repoTypeV2 = attrMapper.repoDetailTypeToRepoTypeV2(type);
        if (type == RepoDetailsType.FEDERATED) {
            assertEquals("local", repoTypeV2);
        } else {
            assertEquals(attrMapper.repoDetailTypeToLowercaseRepoTypeName(type), repoTypeV2);
        }
    }

    /**
     * The string-level helper is the single source of truth reused when reconstructing repo_type_v2 for repos
     * that round-trip through transparent_repo_config (which drops the shadow column). It must apply the same
     * federated -> local rule as the enum-based mapping, on the raw lowercase type string.
     */
    @ParameterizedTest
    @EnumSource(RepoDetailsType.class)
    void typeStringToRepoTypeV2_mirrorsEnumMapping(RepoDetailsType type) {
        RepositoryConfigAttributesMapper attrMapper = new RepositoryConfigAttributesMapperImpl();
        String fromString = attrMapper.typeStringToRepoTypeV2(type.typeNameLowercase());
        assertEquals(attrMapper.repoDetailTypeToRepoTypeV2(type), fromString);
    }

    @Test
    void typeStringToRepoTypeV2_federatedMapsToLocal() {
        RepositoryConfigAttributesMapper attrMapper = new RepositoryConfigAttributesMapperImpl();
        assertEquals("local", attrMapper.typeStringToRepoTypeV2("federated"));
    }

    @Test
    void federatedRepositoryConfigForwardFillsRepoTypeV2AsLocal() {
        FederatedRepoConfig federated = FederatedRepoConfig.create("fed-repo", RepoType.Generic);
        DbRepositoryConfig db = mapper.repositoryConfigToDbRepositoryConfig(federated);
        assertEquals("federated", db.getType());
        assertEquals("local", db.getRepoTypeV2());
    }

    @Test
    void localRepositoryConfigForwardFillsRepoTypeV2AsType() {
        LocalRepoConfig local = LocalRepoConfig.create("local-repo", RepoType.Maven);
        DbRepositoryConfig db = mapper.repositoryConfigToDbRepositoryConfig(local);
        assertEquals("local", db.getType());
        assertEquals("local", db.getRepoTypeV2());
    }

    private static void createAndBindToArtifactoryHome() throws IOException {
        File homeDir = Files.createTempDirectory(RepositoryConfigMapperTest.class.getSimpleName()
                + "_" + new Random().nextLong()).toFile();
        ArtifactoryHome artifactoryHome = new TestArtifactoryHome(homeDir);
        artifactoryHome.initArtifactorySystemProperties();
        ArtifactoryHome.bind(artifactoryHome);
    }

    private static void unbindAndCleanHome() {
        if (ArtifactoryHome.isBound()) {
            FileUtils.deleteQuietly(ArtifactoryHome.get().getHomeDir());
            ArtifactoryHome.unbind();
        }
    }

    static class RandomObjectPopulator {
        private static final Random random = new Random();

        static <T> T createAndFill(Class<T> clazz) throws Exception {
            T instance;
            if (RepoConfig.class.isAssignableFrom(clazz)) {
                Constructor<T> declaredConstructor = clazz.getDeclaredConstructor(String.class, RepoType.class);
                declaredConstructor.setAccessible(true);
                instance = declaredConstructor
                        .newInstance(getRandomValueForType(String.class), getRandomValueForType(RepoType.class));
            } else {
                instance = clazz.newInstance();
            }
            for (Field field : clazz.getDeclaredFields()) {
                if (isFinal(field.getModifiers())) {
                    continue;
                }
                field.setAccessible(true);
                Object value = getRandomValueForType(field.getType());
                field.set(instance, value);
            }
            return instance;
        }

        private static Object getRandomValueForType(Class<?> type) throws Exception {
            if (type.isEnum()) {
                Object[] enumValues = type.getEnumConstants();
                return enumValues[random.nextInt(enumValues.length)];
            } else if (type.equals(Integer.TYPE) || type.equals(Integer.class)) {
                return random.nextInt();
            } else if (type.equals(Long.TYPE) || type.equals(Long.class)) {
                return random.nextLong();
            } else if (type.equals(Double.TYPE) || type.equals(Double.class)) {
                return random.nextDouble();
            } else if (type.equals(Float.TYPE) || type.equals(Float.class)) {
                return random.nextFloat();
            } else if (type.equals(String.class)) {
                return UUID.randomUUID().toString();
            } else if (type.equals(BigInteger.class)) {
                return BigInteger.valueOf(random.nextInt());
            } else if (type.equals(Boolean.TYPE) || type.equals(Boolean.class)) {
                return random.nextBoolean();
            } else if (type.equals(List.class)) {
                return List.of();
            } else if (type.equals(Map.class)) {
                return Map.of();
            }
            return createAndFill(type);
        }
    }
}