package org.artifactory.repo.service.trash.rdtt;

import org.artifactory.async.Async;

/**
 * This service fixes DB performance issue that leads to statement execution timeout
 * on some trashcan/GC related DB queries.
 *
 * @author Evgeny Kurtser
 */
public interface RemoveDirsTrashcanTimeService {
    /**
     * removes some records from {@code node_props} table performs DB calls of Remove Directories Trashcan Time task.<p>
     * This asynchronous task executes DB query that deletes directory records with {@code prop_key = 'trash.time'}
     *
     * @param batchSize max number of records that will be removed from the node_props table in a single iteration
     * @param delay     guaranteed delay(in seconds) between each iteration. For example, if {@code delay = 10} and last
     *                  iteration started at <i>12:00:00</i> and finished at <i>12:05:40</i> then next iteration
     *                  will start at <i>12:05:50</i>
     */
    @Async
    void execute(int batchSize, long delay, int batchCount);
}
