package org.artifactory.queue.services;

import com.google.common.base.Splitter;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.event.queue.QueueCursorType;
import org.artifactory.event.queue.QueueCursorUtils;
import org.artifactory.queue.QueueCursorTypeMapper;
import org.artifactory.storage.db.event.model.NodeEventCursor;

import jakarta.annotation.Nonnull;
import java.util.Iterator;
import java.util.List;

import static org.artifactory.event.queue.QueueCursorUtils.CURSOR;

@Data
@Slf4j
public class PersistentQueueCursorInfo {

    private String repoKey;
    private QueueCursorType type;
    private String variant;
    private String cursorName;

    public PersistentQueueCursorInfo(@Nonnull String repoKey,
            @Nonnull QueueCursorType type,
            @Nonnull String variant) {
        this.repoKey = repoKey;
        this.type = type;
        this.variant = variant;
        this.cursorName = QueueCursorUtils.getCursorName(repoKey, type, variant);
    }

    public PersistentQueueCursorInfo(@Nonnull String cursorName, @Nonnull QueueCursorType type) {
        this.cursorName = cursorName;
        this.type = type;

        splitCursorName();
    }

    public PersistentQueueCursorInfo(@Nonnull NodeEventCursor nodeEventCursor) {
        this(nodeEventCursor.getOperatorId(), QueueCursorTypeMapper.get(nodeEventCursor.getType()));
    }

    private void splitCursorName() {
        List<String> cursorNameSplited = Splitter.on("-" + type.name().toLowerCase()).splitToList(cursorName);
        if (cursorNameSplited.size() == 2) {
            Iterator<String> cursorNameIterator = cursorNameSplited.iterator();
            this.repoKey = cursorNameIterator.next();
            this.variant = cursorNameIterator.next().replace("-" + CURSOR, "");
        } else {
            log.error("Failed parse cursor name: {}", cursorName);
        }
    }

}
