package org.artifactory.build;

import lombok.NonNull;
import org.jfrog.build.api.builder.PromotionStatusBuilder;
import org.jfrog.build.api.release.PromotionStatus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReleaseStatusListTest {

    @Test
    void size_shouldReturnZero_whenListEmpty() {
        ReleaseStatusList list = new ReleaseStatusList(new ArrayList<>());
        assertEquals(0, list.size());
    }

    @Test
    void isEmpty_shouldReturnTrue_whenListEmpty() {
        ReleaseStatusList list = new ReleaseStatusList(new ArrayList<>());
        assertTrue(list.isEmpty());
    }

    @Test
    void size_shouldReturnElementCount_whenListHasElements() {
        List<PromotionStatus> inner = new ArrayList<>(List.of(
                createPromotionStatus("staged", "repo1"),
                createPromotionStatus("released", "repo2")
        ));
        ReleaseStatusList list = new ReleaseStatusList(inner);

        assertEquals(2, list.size());
        assertFalse(list.isEmpty());
    }

    @Test
    void get_shouldReturnReleaseStatusAtIndex() {
        PromotionStatus innerStatus = createPromotionStatus("released", "my-repo");
        ReleaseStatusList list = new ReleaseStatusList(new ArrayList<>(List.of(innerStatus)));

        ReleaseStatus releaseStatus = list.getFirst();

        assertNotNull(releaseStatus);
        assertEquals("released", releaseStatus.getStatus());
        assertEquals("my-repo", releaseStatus.getRepository());
    }

    @Test
    void contains_shouldReturnTrue_whenReleaseStatusInList() {
        PromotionStatus innerStatus = createPromotionStatus("staged", "repo");
        ReleaseStatusList list = new ReleaseStatusList(new ArrayList<>(List.of(innerStatus)));
        ReleaseStatus wrapper = new ReleaseStatus(innerStatus);

        assertTrue(list.contains(wrapper));
    }

    @Test
    void add_shouldAppendAndReturnTrue() {
        ReleaseStatusList list = new ReleaseStatusList(new ArrayList<>());
        ReleaseStatus toAdd = new ReleaseStatus("released", null, "repo", null, null);

        assertTrue(list.add(toAdd));
        assertEquals(1, list.size());
        assertEquals("released", list.getFirst().getStatus());
    }

    @Test
    void remove_shouldRemoveReleaseStatusAndReturnTrue() {
        PromotionStatus inner = createPromotionStatus("staged", "repo");
        ReleaseStatusList list = new ReleaseStatusList(new ArrayList<>(List.of(inner)));
        ReleaseStatus wrapper = new ReleaseStatus(inner);

        assertTrue(list.remove(wrapper));
        assertEquals(0, list.size());
    }

    @Test
    void clear_shouldRemoveAllElements() {
        List<PromotionStatus> inner = new ArrayList<>(List.of(createPromotionStatus("staged", "r")));
        ReleaseStatusList list = new ReleaseStatusList(inner);
        list.clear();

        assertEquals(0, list.size());
        assertTrue(list.isEmpty());
    }

    @Test
    void subList_shouldReturnSublistAsReleaseStatusList() {
        List<PromotionStatus> inner = new ArrayList<>(List.of(
                createPromotionStatus("a", "r1"),
                createPromotionStatus("b", "r2"),
                createPromotionStatus("c", "r3")
        ));
        ReleaseStatusList list = new ReleaseStatusList(inner);

        List<ReleaseStatus> sub = list.subList(1, 3);

        assertEquals(2, sub.size());
        assertEquals("b", sub.get(0).getStatus());
        assertEquals("c", sub.get(1).getStatus());
    }

    @Test
    void iterator_shouldIterateOverReleaseStatusElements() {
        PromotionStatus p1 = createPromotionStatus("first", "r1");
        PromotionStatus p2 = createPromotionStatus("second", "r2");
        ReleaseStatusList list = new ReleaseStatusList(new ArrayList<>(List.of(p1, p2)));

        var iterator = list.iterator();
        assertTrue(iterator.hasNext());
        assertEquals("first", iterator.next().getStatus());
        assertTrue(iterator.hasNext());
        assertEquals("second", iterator.next().getStatus());
        assertFalse(iterator.hasNext());
    }

    @Test
    void set_shouldReplaceElementAndReturnPrevious() {
        PromotionStatus p1 = createPromotionStatus("old", "r1");
        PromotionStatus p2 = createPromotionStatus("new", "r2");
        ReleaseStatusList list = new ReleaseStatusList(new ArrayList<>(List.of(p1)));
        ReleaseStatus newStatus = new ReleaseStatus(p2);

        ReleaseStatus previous = list.set(0, newStatus);

        assertNotNull(previous);
        assertEquals("old", previous.getStatus());
        assertEquals("new", list.getFirst().getStatus());
    }

    @Test
    void removeByIndex_shouldRemoveAndReturnReleaseStatus() {
        PromotionStatus p = createPromotionStatus("x", "r");
        ReleaseStatusList list = new ReleaseStatusList(new ArrayList<>(List.of(p)));

        ReleaseStatus removed = list.removeFirst();

        assertNotNull(removed);
        assertEquals("x", removed.getStatus());
        assertEquals(0, list.size());
    }

    @Test
    void indexOf_shouldReturnIndex_whenElementPresent() {
        PromotionStatus p1 = createPromotionStatus("a", "r1");
        PromotionStatus p2 = createPromotionStatus("b", "r2");
        ReleaseStatusList list = new ReleaseStatusList(new ArrayList<>(List.of(p1, p2)));
        ReleaseStatus wrapper = new ReleaseStatus(p2);

        assertEquals(1, list.indexOf(wrapper));
    }

    @NonNull
    private static PromotionStatus createPromotionStatus(@NonNull String status, @NonNull String repository) {
        return new PromotionStatusBuilder(status).timestampDate(new Date()).repository(repository).build();
    }
}
