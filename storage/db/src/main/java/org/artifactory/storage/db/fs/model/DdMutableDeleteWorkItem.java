package org.artifactory.storage.db.fs.model;

import jakarta.annotation.Nonnull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.artifactory.workqueue.WorkItem;

/**
 * @author Eli Skoran 02/03/2021
 */

@EqualsAndHashCode(callSuper = false)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DdMutableDeleteWorkItem extends WorkItem {

    private long id;

    @Nonnull
    @Override
    public String getUniqueKey() {
        return String.valueOf(id);
    }
}
