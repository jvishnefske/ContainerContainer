package org.artifactory.storage;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.output.NullPrintStream;
import org.artifactory.api.repo.storage.RepoStorageSummaryInfo;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@Slf4j
class StorageSummaryInfoTest {

    /**
     * Tests StorageSummaryInfo concurrent access/modification.
     *
     * @throws InterruptedException .
     */
    @Test
    void testConcurrency() throws InterruptedException {
        int from = 0, to = 1_000;
        Map<String, RepoStorageSummaryInfo> repoStorageSummaries = new HashMap<>();
        StorageSummaryInfo test = new StorageSummaryInfo(repoStorageSummaries, null, null, null);

        ExecutorService executor = Executors.newFixedThreadPool(5);
        try {
            Random rnd = new Random();
            // Add and delete data randomly to/from the map to-from times.
            executor.execute(runRecalculate(from, to, test, rnd, "writer1"));
            // Add and delete data randomly to/from the map to-from times.
            executor.execute(runRecalculate(from, to, test, rnd, "writer2"));

            // Read data from map to-from times.
            assertDoesNotThrow(() -> executor.execute(() -> {
                log.debug("\treader IN");
                IntStream.range(from, to)
                        .forEach(x -> test.getRepoStorageSummaries().values()
                                // I call 'println' to make sure that the values are used and
                                // not bypassed by compiler optimizer.
                                // I call it on NullPrintStream to not to garbage logs.
                                .forEach(NullPrintStream.NULL_PRINT_STREAM::println));
                log.debug("\treader OUT");
            }
            ),
                    "No exception should have been thrown");
        } finally {
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    private Runnable runRecalculate(int from, int to, StorageSummaryInfo test, Random rnd, String taskName) {
        return () -> {
            log.debug(taskName + " IN");
            IntStream.range(from, to)
                    .mapToObj(String::valueOf)
                    .forEach(repoKey -> test.recalculateRepoStorageSummary(shallowCopy -> {
                        String rndRepoKey = String.valueOf(rnd.nextInt(to));
                        shallowCopy.put(rndRepoKey, new RepoStorageSummaryInfo());

                        rndRepoKey = String.valueOf(rnd.nextInt(to));
                        shallowCopy.remove(rndRepoKey);
                    }));
            log.debug(taskName + " OUT");
        };
    }
}