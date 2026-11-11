package org.artifactory.storage.db.locks.service.condition;

import org.artifactory.common.ConstantValues;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.descriptor.addon.ha.lock.DbLockingMechanism;
import org.artifactory.storage.db.locks.service.annotation.NativeDbLocksType;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.storage.DbType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.type.AnnotatedTypeMetadata;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class NativeDbLocksBeanConditionTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private ArtifactoryHome artifactoryHome;
    @Mock
    private AnnotatedTypeMetadata metadata;
    @Mock
    private ConditionContext context;
    private NativeDbLocksBeanCondition condition;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        ArtifactoryHome.bind(artifactoryHome);
        condition = new NativeDbLocksBeanCondition();
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
        ArtifactoryHome.unbind();
    }

    @Test
    void testMatchesNoNativeDbLocksTypeAnnotationReturnsFalse() {
        MergedAnnotations mergedAnnotations = mock(MergedAnnotations.class);
        when(mergedAnnotations.get(NativeDbLocksType.class)).thenReturn(MergedAnnotation.missing());
        when(mergedAnnotations.get(Conditional.class)).thenReturn(MergedAnnotation.missing());
        when(metadata.getAnnotations()).thenReturn(mergedAnnotations);

        assertFalse(condition.matches(context, metadata));
    }

    @ParameterizedTest
    @EnumSource(DbType.class)
    void testMatchesDbTypeNoMatchToAnnotationDbTypeReturnsFalse(DbType annotationDbType) {
        DbType dbType = DbType.values()[(annotationDbType.ordinal() + 1) % DbType.values().length];

        MergedAnnotations mergedAnnotations = mock(MergedAnnotations.class);
        MergedAnnotation<NativeDbLocksType> mergedAnnotation =
                (MergedAnnotation<NativeDbLocksType>) mock(MergedAnnotation.class);
        when(mergedAnnotation.getEnum("value", DbType.class)).thenReturn(annotationDbType);
        when(mergedAnnotations.get(NativeDbLocksType.class)).thenReturn(mergedAnnotation);
        when(metadata.getAnnotations()).thenReturn(mergedAnnotations);

        ArtifactoryDbProperties dbProperties = mock(ArtifactoryDbProperties.class);
        when(dbProperties.getDbType()).thenReturn(dbType);
        when(artifactoryHome.getDBProperties()).thenReturn(dbProperties);

        assertFalse(condition.matches(context, metadata));
    }

    @ParameterizedTest
    @EnumSource(DbType.class)
    void testMatchesUnsupportedDbLockingMechanismReturnsFalse(DbType dbType) {
        MergedAnnotations mergedAnnotations = mock(MergedAnnotations.class);
        MergedAnnotation<NativeDbLocksType> mergedAnnotation =
                (MergedAnnotation<NativeDbLocksType>) mock(MergedAnnotation.class);
        when(mergedAnnotation.getEnum("value", DbType.class)).thenReturn(dbType);
        when(mergedAnnotations.get(NativeDbLocksType.class)).thenReturn(mergedAnnotation);
        when(metadata.getAnnotations()).thenReturn(mergedAnnotations);

        ArtifactoryDbProperties dbProperties = mock(ArtifactoryDbProperties.class);
        when(dbProperties.getDbType()).thenReturn(dbType);
        when(artifactoryHome.getDBProperties()).thenReturn(dbProperties);

        ArtifactorySystemProperties artifactorySystemProperties = mock(ArtifactorySystemProperties.class);
        when(artifactorySystemProperties.getProperty(ConstantValues.dbLockingMechanism)).thenReturn(
                DbLockingMechanism.JFROG_TABLE_BASED.getName());
        when(artifactoryHome.getArtifactoryProperties()).thenReturn(artifactorySystemProperties);

        assertFalse(condition.matches(context, metadata));
    }

    @ParameterizedTest
    @EnumSource(DbType.class)
    void testMatchesReturnsTrue(DbType dbType) {
        MergedAnnotations mergedAnnotations = mock(MergedAnnotations.class);
        MergedAnnotation<NativeDbLocksType> mergedAnnotation =
                (MergedAnnotation<NativeDbLocksType>) mock(MergedAnnotation.class);
        when(mergedAnnotation.getEnum("value", DbType.class)).thenReturn(dbType);
        when(mergedAnnotations.get(NativeDbLocksType.class)).thenReturn(mergedAnnotation);
        when(metadata.getAnnotations()).thenReturn(mergedAnnotations);

        ArtifactoryDbProperties dbProperties = mock(ArtifactoryDbProperties.class);
        when(dbProperties.getDbType()).thenReturn(dbType);
        when(artifactoryHome.getDBProperties()).thenReturn(dbProperties);

        ArtifactorySystemProperties artifactorySystemProperties = mock(ArtifactorySystemProperties.class);
        when(artifactorySystemProperties.getProperty(ConstantValues.dbLockingMechanism)).thenReturn(
                DbLockingMechanism.AUTO.getName());
        when(artifactoryHome.getArtifactoryProperties()).thenReturn(artifactorySystemProperties);

        assertTrue(condition.matches(context, metadata));
        when(artifactorySystemProperties.getProperty(ConstantValues.dbLockingMechanism)).thenReturn(
                DbLockingMechanism.NATIVE.getName());
        assertTrue(condition.matches(context, metadata));
    }
}