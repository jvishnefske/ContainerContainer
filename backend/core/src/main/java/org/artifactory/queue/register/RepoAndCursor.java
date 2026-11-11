package org.artifactory.queue.register;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.artifactory.event.queue.QueueCursorType;

@Getter
@EqualsAndHashCode
@AllArgsConstructor
public class RepoAndCursor {
    final String repoKey;
    final QueueCursorType cursorType;
}
