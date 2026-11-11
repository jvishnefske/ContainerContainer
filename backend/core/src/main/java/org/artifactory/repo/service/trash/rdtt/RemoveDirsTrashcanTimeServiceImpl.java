package org.artifactory.repo.service.trash.rdtt;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.storage.db.fs.dao.PropertiesDao;
import org.jfrog.common.ExceptionLogUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * This service fixes DB performance issue that leads to statement execution timeout
 * on some trashcan/GC related DB queries.
 *
 * @author Evgeny Kurtser
 */
@Slf4j
@Service
public class RemoveDirsTrashcanTimeServiceImpl implements RemoveDirsTrashcanTimeService {
    private final PropertiesDao propertiesDao;
    public RemoveDirsTrashcanTimeServiceImpl(@Autowired PropertiesDao propertiesDao) {
        this.propertiesDao = propertiesDao;
    }

    @Override
    public void execute(int batchSize, long delay, int batchCount) {
        int batchCounter = 0;
        try {
            while(true) {
                if(batchCount >= 0 && batchCounter >= batchCount) {
                    log.info("Max number ({}) of batch executions reached. " +
                            "Remove Directories Trashcan Time task finishes", batchCount);
                    break;
                }
                int deletedRecords = propertiesDao.removeDirsTrashcanTime(batchSize);
                log.info("Batch #{} finished. {} records were removed", batchCounter, deletedRecords);
                if (deletedRecords < batchSize) {
                    log.info("Last batch reached. Remove Directories Trashcan Time task finishes");
                    break;
                } else {
                    delayNextInvocation(delay);
                }
                batchCounter++;
            }
        } catch (InterruptedException e) {
            log.warn("Remove Directories Trashcan Time task finishes since it was interrupted");
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            ExceptionLogUtils.debugOrError(log, "Error while Remove Directories Trashcan Time execution", e);
        }
    }

    void delayNextInvocation(long delay) throws InterruptedException {
        TimeUnit.MILLISECONDS.sleep(delay);
    }
}
