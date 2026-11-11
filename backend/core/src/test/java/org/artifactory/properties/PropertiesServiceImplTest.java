package org.artifactory.properties;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.artifactory.api.properties.PropertiesDiff;
import org.artifactory.descriptor.property.PredefinedValue;
import org.artifactory.descriptor.property.Property;
import org.artifactory.md.Properties;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.StoringRepo;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.storage.fs.MutableVfsItem;
import org.artifactory.storage.fs.service.FileService;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Arrays;

import static org.artifactory.common.ConstantValues.maxArtifactsSetPropertiesRecursive;
import static org.artifactory.properties.PropertiesUtil.calcPropertiesDiff;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

/**
 * @author dudim
 */
public class PropertiesServiceImplTest extends ArtifactoryHomeBoundTest {

    @DataProvider
    public static Object[][] data() {
        return new Object[][] {
                {true, 0},
                {false, 1},
        };
    }

    @Test(dataProvider = "data")
    public void testAddPropertiesWithLimitEnabled(boolean hasMoreThanLimit, int expectedInvocations) {
        getOrCreateArtifactoryHomeStub().setProperty(maxArtifactsSetPropertiesRecursive.getPropertyName(), "1");
        PropertiesServiceImpl propertiesServiceSpy = spy(new PropertiesServiceImpl());
        InternalRepositoryService repoService = mock(InternalRepositoryService.class);
        FileService fileService = mock(FileService.class);
        ReflectionTestUtils.setField(propertiesServiceSpy, "repoService", repoService);
        ReflectionTestUtils.setField(propertiesServiceSpy, "fileService", fileService);
        StoringRepo repo = mock(StoringRepo.class);
        MutableVfsItem item = mock(MutableVfsItem.class);
        RepoPath repoPath = RepoPathFactory.create("cool-repo", "");
        when(repoService.storingRepositoryByKey("cool-repo")).thenReturn(repo);
        when(repo.getMutableFsItem(repoPath)).thenReturn(item);
        when(item.getRepoPath()).thenReturn(repoPath);
        Property prop = new Property();
        prop.setName("prop1");
        PredefinedValue value1 = new PredefinedValue();
        value1.setValue("value1");
        prop.addPredefinedValue(value1);
        when(fileService.hasMoreThanLimitItems(any(), anyLong())).thenReturn(hasMoreThanLimit);
        try {
            propertiesServiceSpy.addPropertyRecursively(repoPath, null, prop, true);
        } catch (Exception e) {
            // Ignore
        }
        verify(propertiesServiceSpy, times(expectedInvocations)).addProperty(repoPath, null, prop, true);
    }

    @Test
    public void testSetPropertiesWithInternalPropertiesNotInvokeInterceptors() {
        PropertiesServiceImpl propertiesServiceSpy = spy(new PropertiesServiceImpl());

        Mockito.doReturn(true).when(propertiesServiceSpy).setMetadataProperties(any(), any(), eq(true));
        RepoPath repoPath = RepoPathFactory.create("repo-example", "");
        Properties properties = new PropertiesImpl();
        propertiesServiceSpy.setProperties(repoPath, properties, true);

        verify(propertiesServiceSpy, times(1)).setMetadataProperties(any(), any(), eq(true));
        verify(propertiesServiceSpy, times(0)).setArtifactProperties(any(), any());
        verify(propertiesServiceSpy, times(0)).goThroughBeforeCreateProperties(any(), any(), any(), any());
        verify(propertiesServiceSpy, times(0)).goThroughAfterCreateProperties(any(), any(), any());
        verify(propertiesServiceSpy, times(0)).goThroughBeforeDeleteProperties(any(), any(), any(), any());
        verify(propertiesServiceSpy, times(0)).goThroughAfterDeleteProperties(any(), any(), any());
    }

    @Test
    public void testCalcPropertiesDiff() {
        PropertiesServiceImpl propertiesService = spy(new PropertiesServiceImpl());

        PropertiesImpl updatedProperties = new PropertiesImpl();
        updatedProperties.put("key2", "value2-updated");
        updatedProperties.put("key3", "value3-created");
        RepoPath repoPath = RepoPathFactory.create("repoKey", "path");

        // Create mock properties for currentProperties
        PropertiesImpl currentProperties = new PropertiesImpl();
        currentProperties.put("key1", "value1");
        currentProperties.put("key2", "value2");
        // Mock the behavior of getProperties method
        doReturn(currentProperties).when(propertiesService).getProperties(repoPath);

        // Create a mock diff result
        PropertiesImpl expectedModified = new PropertiesImpl();
        expectedModified.put("key2", "value2-updated");
        expectedModified.put("key3", "value3-created");
        PropertiesImpl expectedDeleted = new PropertiesImpl();
        expectedDeleted.put("key1", "");
        PropertiesDiff expectedDiff = new PropertiesDiff(expectedModified, expectedDeleted);

        // Call the method under test
        PropertiesDiff actualDiff = propertiesService.calcPropertiesDiff(updatedProperties, repoPath);

        // Verify that the method called getProperties and calcPropertiesDiff with the expected arguments
        verify(propertiesService).getProperties(repoPath);

        // Assert that the actual diff matches the expected diff
        assertEquals(actualDiff, expectedDiff);
    }

    @DataProvider
    public static Object[][] properties() {
        return new Object[][] {

                {
                        a(p("disttag", "latest")), // current
                        a(p("disttag", "")), // modified
                        a(p("disttag", "")), // expected modified
                        a() // expected deleted
                },
                {
                        a(p("disttag", "latest")), // current
                        a(p("disttag", "latest")), // modified
                        a(), // expected modified
                        a() // expected deleted
                },

                {
                        a(p("disttag", "latest")), // current
                        a(p("disttag", null)), // modified
                        a(p("disttag", null)), // expected modified
                        a() // expected deleted
                },
                {
                        a(p("disttag", null)), // current
                        a(p("disttag", "latest")), // modified
                        a(p("disttag", "latest")), // expected modified
                        a() // expected deleted
                },
                {
                        a(), // current
                        a(p("disttag", "latest")), // modified
                        a(p("disttag", "latest")), // expected modified
                        a() // expected deleted
                },
                {
                        a(p("disttag", "latest")), // current
                        a(), // modified
                        a(), // expected modified
                        a(p("disttag", "")) // expected deleted
                },

                {
                        a(p("disttag", "latest")), // current
                        a(p("disttag", "latest"), p("disttag", "release")), // modified
                        a(p("disttag", "latest"), p("disttag", "release")), // expected modified
                        a() // expected deleted
                },
                {
                        a(p("disttag", "latest"), p("disttag", "release")), // current
                        a(p("disttag", "latest")), // modified
                        a(p("disttag", "latest")), // expected modified
                        a() // expected deleted
                },
                {
                        a(p("disttag", "latest"), p("disttag", "release")),  // current
                        a(), // modified
                        a(), // expected modified
                        a(p("disttag", "")) // expected deleted
                }
        };
    }

    @Test(dataProvider = "properties")
    public void testFilterPreExistingProperties(
            Pair<String, String>[] currentPairs,
            Pair<String, String>[] modifiedPairs,
            Pair<String, String>[] expectedModifiedPairs,
            Pair<String, String>[] expectedDeletedPairs
    ) {
        Multimap<String, String> current = toMultimap(currentPairs);
        Properties currentProperties = new PropertiesImpl();
        currentProperties.putAll(current);

        Multimap<String, String> modified = toMultimap(modifiedPairs);
        Properties modifiedProperties = new PropertiesImpl();
        modifiedProperties.putAll(modified);

        Multimap<String, String> expectedModified = toMultimap(expectedModifiedPairs);
        Properties expectedModifiedProperties = new PropertiesImpl();
        expectedModifiedProperties.putAll(expectedModified);

        Multimap<String, String> expectedDeleted = toMultimap(expectedDeletedPairs);
        Properties expectedDeletedProperties = new PropertiesImpl();
        expectedDeletedProperties.putAll(expectedDeleted);

        PropertiesDiff properties = calcPropertiesDiff(modifiedProperties, currentProperties);

        assertEquals(properties.getModified(), expectedModifiedProperties);
        assertEquals(properties.getDeleted(), expectedDeletedProperties);
    }

    private static Pair<String, String> p(String first, String second) {
        return new ImmutablePair<>(first, second);
    }

    private static Pair[] a(Pair... pairs) {
        return pairs;
    }

    private static Multimap<String, String> toMultimap(Pair<String, String>[] pairs) {
        return Arrays.stream(pairs).
                collect(
                        ArrayListMultimap::create,
                        (m, p) -> m.put(p.getKey(), p.getValue()),
                        Multimap::putAll
                );
    }


}