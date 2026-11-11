/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.repo.cache.expirable;

import com.google.common.collect.Maps;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.spring.ArtifactoryApplicationContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.artifactory.util.application.ApplicationServiceConfigurationUtil.APPLICATION_SERVICE_ID_PROPERTY_NAME;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * @author Noam Y. Tenne
 */
public class CacheExpiryImplTest {

    @Test(expectedExceptions = NullPointerException.class)
    public void testInitWithNullContext() {
        new CacheExpiryImpl().init();
    }

    @Test
    public void testInitWithNullBeanName() {
        ArtifactoryApplicationContext context = mock(ArtifactoryApplicationContext.class);
        when(context.beansForType(CacheExpiryChecker.class))
                .thenReturn(Maps.newHashMap());

        CacheExpiryImpl cacheExpiry = new CacheExpiryImpl();
        cacheExpiry.setApplicationContext(context);
        cacheExpiry.init();
        Mockito.verify(context, Mockito.times(1)).beansForType(CacheExpiryChecker.class);
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    public void testIsExpirableWithNullPath() {
        assertFalse(new CacheExpiryImpl().isExpirable(null, null, null), "Null path should never be expirable.");
        assertFalse(new CacheExpiryImpl().isLocalGenerated(null, null, null), "Null path should never be allowed.");
        assertFalse(new CacheExpiryImpl().isMetadata(null, null, null), "Null path should never be allowed.");
        assertFalse(new CacheExpiryImpl().shouldExcludePropertyFromMirroring((RepoType) null, null),
                "Null path should never be allowed.");
    }

    @Test
    public void testShouldExcludePropertyFromMirroring() {
        CacheExpiryChecker rootExpirable = mockChecker(new RepoType[] {RepoType.Maven}, false);
        CacheExpiryChecker acceptingExpirable = mockChecker(new RepoType[] {RepoType.Generic, RepoType.Maven}, true);

        Map<String, CacheExpiryChecker> expirableHashMap = Map.of(
                "root", rootExpirable,
                "accepting", acceptingExpirable
        );

        ArtifactoryApplicationContext context = mock(ArtifactoryApplicationContext.class);
        when(context.beansForType(CacheExpiryChecker.class)).thenReturn(expirableHashMap);

        CacheExpiryImpl cacheExpiry = new CacheExpiryImpl();
        cacheExpiry.setApplicationContext(context);

        cacheExpiry.init();
        Mockito.verify(context, Mockito.times(1)).beansForType(CacheExpiryChecker.class);

        assertTrue(cacheExpiry.shouldExcludePropertyFromMirroring(RepoType.Maven, APPLICATION_SERVICE_ID_PROPERTY_NAME),
                "application.id property should exclude from mirroring");
    }

    @Test
    public void testIsExpirableWithValidPathAndNoExpirables() {
        assertFalse(new CacheExpiryImpl().isExpirable(null, null, "afdafasdf"),
                "Nothing should be expirable when no expiry criteria is set.");
        assertFalse(new CacheExpiryImpl().isLocalGenerated(RepoType.Alpine, "alpine", "afdafasdf"),
                "Nothing should be expirable when no expiry criteria is set.");
        assertFalse(new CacheExpiryImpl().isMetadata(RepoType.Alpine, "alpine", "afdafasdf"),
                "Nothing should be expirable when no expiry criteria is set.");
    }

    @Test
    public void testIsExpirable() {
        CacheExpiryChecker rootExpirable = mockChecker(new RepoType[] {RepoType.Maven}, false);
        CacheExpiryChecker acceptingExpirable = mockChecker(new RepoType[] {RepoType.Generic, RepoType.Maven}, true);


        Map<String, CacheExpiryChecker> expirableHashMap = Map.of(
                "root", rootExpirable,
                "accepting", acceptingExpirable
        );
        String path = "asdfasdf";
        String repoKey = "root";
        boolean expirable = testExpirable(expirableHashMap, repoKey, path);
        assertTrue(expirable, "Path should be expirable.");
        Mockito.verify(acceptingExpirable, times(1)).isExpirable(RepoType.Maven, repoKey, path);
    }

    @Test
    public void testIsNotExpirable() {
        CacheExpiryChecker rootExpirable = mockChecker(new RepoType[] {RepoType.Maven}, false);
        CacheExpiryChecker differentType = mockChecker(new RepoType[] {RepoType.Generic, RepoType.Gems}, true);

        Map<String, CacheExpiryChecker> expirableHashMap = Map.of(
                "root", rootExpirable,
                "accepting", differentType
        );
        String path = "asdfasdf";
        String repoKey = "root";
        boolean expirable = testExpirable(expirableHashMap, repoKey, path);
        assertFalse(expirable, "Path should not be expirable.");
        Mockito.verify(differentType, times(0)).isExpirable(RepoType.Maven, repoKey, path);
        Mockito.verify(rootExpirable, times(1)).isExpirable(RepoType.Maven, repoKey, path);
    }

    private static boolean testExpirable(Map<String, CacheExpiryChecker> expirableHashMap, String repoKey,
            String path) {
        ArtifactoryApplicationContext context = mock(ArtifactoryApplicationContext.class);
        when(context.beansForType(CacheExpiryChecker.class)).thenReturn(expirableHashMap);

        CacheExpiryImpl cacheExpiry = new CacheExpiryImpl();
        cacheExpiry.setApplicationContext(context);

        cacheExpiry.init();
        Mockito.verify(context, Mockito.times(1)).beansForType(CacheExpiryChecker.class);
        return cacheExpiry.isExpirable(RepoType.Maven, repoKey, path);
    }

    private static CacheExpiryChecker mockChecker(RepoType[] types, boolean expirable) {
        CacheExpiryChecker rootExpirable = mock(CacheExpiryChecker.class);
        when(rootExpirable.isExpirable(any(), any(), any())).thenReturn(expirable);
        when(rootExpirable.getTypes()).thenReturn(types);
        return rootExpirable;
    }

    private static CacheExpiryChecker mockGeneratedChecker(RepoType[] types, boolean localGenerated) {
        CacheExpiryChecker rootExpirable = mock(CacheExpiryChecker.class);
        when(rootExpirable.isLocalGenerated(any(), any(), any())).thenReturn(localGenerated);
        when(rootExpirable.getTypes()).thenReturn(types);
        return rootExpirable;
    }

    private static CacheExpiryChecker mockMetadataChecker(RepoType[] types) {
        CacheExpiryChecker rootExpirable = mock(CacheExpiryChecker.class);
        when(rootExpirable.isMetadata(any(), any(), any())).thenReturn(true);
        when(rootExpirable.getTypes()).thenReturn(types);
        return rootExpirable;
    }

    @Test
    public void testIsLocalGenerated() {
        CacheExpiryChecker rootExpirable = mockGeneratedChecker(new RepoType[] {RepoType.Maven}, false);
        CacheExpiryChecker acceptingExpirable =
                mockGeneratedChecker(new RepoType[] {RepoType.Generic, RepoType.Maven}, true);

        Map<String, CacheExpiryChecker> expirableHashMap = Map.of(
                "root", rootExpirable,
                "accepting", acceptingExpirable
        );
        String path = "asdfasdf";
        String repoKey = "root";
        boolean expirable = testIsLocalGenerated(expirableHashMap, repoKey, path);
        assertTrue(expirable, "Path should be local generated.");
        Mockito.verify(acceptingExpirable, times(1)).isLocalGenerated(RepoType.Maven, repoKey, path);
    }

    @Test
    public void testIsMetadata() {
        CacheExpiryChecker rootExpirable = mockGeneratedChecker(new RepoType[] {RepoType.Maven}, false);
        CacheExpiryChecker acceptingExpirable =
                mockMetadataChecker(new RepoType[] {RepoType.Generic, RepoType.Maven});

        Map<String, CacheExpiryChecker> expirableHashMap = Map.of(
                "root", rootExpirable,
                "accepting", acceptingExpirable
        );
        String path = "asdfasdf";
        String repoKey = "root";
        boolean expirable = testIsMetadata(expirableHashMap, repoKey, path);
        assertTrue(expirable, "Path should be local generated.");
        Mockito.verify(acceptingExpirable, times(1)).isMetadata(RepoType.Maven, repoKey, path);
    }

    @Test
    public void testIsInternalMetadata() {
        CacheExpiryChecker rootExpirable = mockGeneratedChecker(new RepoType[] {RepoType.Maven}, false);
        CacheExpiryChecker internalMetadataChecker = mock(InternalMetadataChecker.class);
        when(internalMetadataChecker.getTypes()).thenReturn(new RepoType[] {RepoType.Maven});
        when(internalMetadataChecker.isMetadata(any(), any(), any())).thenCallRealMethod();

        Map<String, CacheExpiryChecker> expirableHashMap = Map.of(
                "root", rootExpirable,
                "internal", internalMetadataChecker
        );
        String path = ".jfrog/artifactory.metadata";
        String repoKey = "root";
        boolean isInternalMetadata = testIsMetadata(expirableHashMap, repoKey, path);
        assertTrue(isInternalMetadata, "Path should be internal metadata.");
        Mockito.verify(internalMetadataChecker, times(1)).isMetadata(RepoType.Maven, repoKey, path);
    }

    @Test
    public void testInternalMetadataTypes() {
        CacheExpiryChecker internalMetadataChecker = mock(InternalMetadataChecker.class);
        when(internalMetadataChecker.getTypes()).thenCallRealMethod();
        RepoType[] types = internalMetadataChecker.getTypes();
        assertEquals(types.length, 55, "Internal metadata checker should have all types.");
        for (RepoType type : types) {
            assertEquals(type, RepoType.values()[type.ordinal()], "Internal metadata checker should have all types.");
        }
    }

    @Test
    public void testInternalMetadataShouldBeLocalGenerated() {
        CacheExpiryChecker rootExpirable = mockGeneratedChecker(new RepoType[] {RepoType.Maven}, false);
        CacheExpiryChecker internalMetadataChecker = mock(InternalMetadataChecker.class);
        when(internalMetadataChecker.getTypes()).thenReturn(new RepoType[] {RepoType.Maven});
        when(internalMetadataChecker.isLocalGenerated(any(), any(), any())).thenCallRealMethod();

        Map<String, CacheExpiryChecker> expirableHashMap = Map.of(
                "root", rootExpirable,
                "internal", internalMetadataChecker
        );
        String path = ".jfrog/artifactory.metadata";
        String repoKey = "root";
        boolean isLocalGenerated = testIsLocalGenerated(expirableHashMap, repoKey, path);
        assertTrue(isLocalGenerated, "Path should be local generated.");
        Mockito.verify(internalMetadataChecker, times(1)).isLocalGenerated(RepoType.Maven, repoKey, path);
    }

    @Test
    public void testInternalMetadataIsNotExpirable() {
        CacheExpiryChecker rootExpirable = mockGeneratedChecker(new RepoType[] {RepoType.Maven}, false);
        CacheExpiryChecker internalMetadataChecker = mock(InternalMetadataChecker.class);
        when(internalMetadataChecker.getTypes()).thenReturn(new RepoType[] {RepoType.Maven});
        when(internalMetadataChecker.isExpirable(any(), any(), any())).thenCallRealMethod();

        Map<String, CacheExpiryChecker> expirableHashMap = Map.of(
                "root", rootExpirable,
                "internal", internalMetadataChecker
        );
        String path = ".jfrog/artifactory.metadata";
        String repoKey = "root";
        boolean isExpirable = testExpirable(expirableHashMap, repoKey, path);
        assertFalse(isExpirable, "Path should not be expirable.");
        Mockito.verify(internalMetadataChecker, times(1)).isExpirable(RepoType.Maven, repoKey, path);
    }


    @Test
    public void testIsNotLocalGenerated() {
        CacheExpiryChecker rootExpirable = mockGeneratedChecker(new RepoType[] {RepoType.Maven}, false);
        CacheExpiryChecker differentChecker =
                mockGeneratedChecker(new RepoType[] {RepoType.Generic, RepoType.Gems}, true);

        Map<String, CacheExpiryChecker> expirableHashMap = Map.of(
                "root", rootExpirable,
                "accepting", differentChecker
        );
        String path = "asdfasdf";
        String repoKey = "root";
        boolean expirable = testIsLocalGenerated(expirableHashMap, repoKey, path);
        assertFalse(expirable, "Path should not be local generated.");
        Mockito.verify(differentChecker, times(0)).isLocalGenerated(RepoType.Maven, repoKey, path);
        Mockito.verify(rootExpirable, times(1)).isLocalGenerated(RepoType.Maven, repoKey, path);
    }

    @ParameterizedTest
    @MethodSource("excludePropertyByValueFromMirroringData")
    void filterExcludePropertyByValueFromMirroring(String propertyKey, Set<String> tags, Set<String> expected) {
        CacheExpiryImpl cacheExpiry = getCacheExpiryImpl(propertyKey, tags, expected);
        assertEquals(expected, cacheExpiry.excludePropertyValueFromMirroring(RepoType.Npm, propertyKey, tags));
    }

    private CacheExpiryImpl getCacheExpiryImpl(String propertyKey, Set<String> tags, Set<String> expected) {
        CacheExpiryChecker expiryChecker = mock(CacheExpiryChecker.class);
        when(expiryChecker.getTypes()).thenReturn(new RepoType[] {RepoType.Npm});
        when(expiryChecker.excludePropertyValueFromMirroring(propertyKey, tags, RepoType.Npm)).thenReturn(
                expected);
        ArtifactoryApplicationContext context = mock(ArtifactoryApplicationContext.class);
        when(context.beansForType(CacheExpiryChecker.class)).thenReturn(Map.of("npm", expiryChecker));
        CacheExpiryImpl cacheExpiry = new CacheExpiryImpl();
        cacheExpiry.setApplicationContext(context);
        cacheExpiry.init();
        return cacheExpiry;
    }

    private static Stream<Arguments> excludePropertyByValueFromMirroringData() {
        return Stream.of(Arguments.of("dist-tag", Set.of("latest", "tag"), Set.of("tag")),
                Arguments.of("name", Set.of("latest", "tag"), Set.of("latest", "tag")));
    }

    private static boolean testIsLocalGenerated(Map<String, CacheExpiryChecker> expirableHashMap, String repoKey,
            String path) {
        ArtifactoryApplicationContext context = mock(ArtifactoryApplicationContext.class);
        when(context.beansForType(CacheExpiryChecker.class)).thenReturn(expirableHashMap);

        CacheExpiryImpl cacheExpiry = new CacheExpiryImpl();
        cacheExpiry.setApplicationContext(context);

        cacheExpiry.init();
        Mockito.verify(context, Mockito.times(1)).beansForType(CacheExpiryChecker.class);
        return cacheExpiry.isLocalGenerated(RepoType.Maven, repoKey, path);
    }

    private static boolean testIsMetadata(Map<String, CacheExpiryChecker> expirableHashMap, String repoKey,
            String path) {
        ArtifactoryApplicationContext context = mock(ArtifactoryApplicationContext.class);
        when(context.beansForType(CacheExpiryChecker.class)).thenReturn(expirableHashMap);

        CacheExpiryImpl cacheExpiry = new CacheExpiryImpl();
        cacheExpiry.setApplicationContext(context);

        cacheExpiry.init();
        Mockito.verify(context, Mockito.times(1)).beansForType(CacheExpiryChecker.class);
        return cacheExpiry.isMetadata(RepoType.Maven, repoKey, path);
    }
}