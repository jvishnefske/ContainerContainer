package org.artifactory.model.fs;

import org.artifactory.repo.RepoPathFactory;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;


/**
 * @author Oleksandr Legenkiy
 */
class ItemInfoImplTest {

    @Test
    void testIsIdentical() {
        ItemInfoImpl item1 = createItemInfoMock();
        ItemInfoImpl item2 = createItemInfoMock();
        ItemInfoImpl item3 = createItemInfoMock();
        when(item3.getCreated()).thenReturn(3L); // item3 has a different "created" timestamp
        try (MockedStatic<ItemInfoUtils> itemInfoUtilsMockedStatic = Mockito.mockStatic(ItemInfoUtils.class)) {
            itemInfoUtilsMockedStatic
                    .when(ItemInfoUtils::isUpdateCreatedTimestampOnCopyOrMoveEnabled).thenReturn(false);
            assertTrue(item1.isIdentical(item2));
            assertFalse(item1.isIdentical(item3)); // should check the "created" timestamp
            itemInfoUtilsMockedStatic
                    .when(ItemInfoUtils::isUpdateCreatedTimestampOnCopyOrMoveEnabled).thenReturn(true);
            assertTrue(item1.isIdentical(item2));
            assertTrue(item1.isIdentical(item3)); // should ignore the "created" timestamp
        }
    }

    private ItemInfoImpl createItemInfoMock() {
        ItemInfoImpl itemMock = Mockito.mock(ItemInfoImpl.class);
        when(itemMock.isIdentical(any())).thenCallRealMethod();
        itemMock.lastModified = 1L;
        when(itemMock.getLastModified()).thenReturn(1L);
        ReflectionTestUtils.setField(itemMock, "created", 2L);
        when(itemMock.getCreated()).thenReturn(2L);
        var dummyRepoPath = RepoPathFactory.create("someRepoPath");
        ReflectionTestUtils.setField(itemMock, "repoPath", dummyRepoPath);
        when(itemMock.getRepoPath()).thenReturn(dummyRepoPath);
        ReflectionTestUtils.setField(itemMock, "name", "someName");
        when(itemMock.getName()).thenReturn("someName");
        ItemAdditionalInfo itemAdditionalInfoMock = Mockito.mock(ItemAdditionalInfo.class);
        when(itemAdditionalInfoMock.isIdentical(any())).thenReturn(true);
        when(itemMock.getAdditionalInfo()).thenReturn(itemAdditionalInfoMock);
        return itemMock;
    }
}