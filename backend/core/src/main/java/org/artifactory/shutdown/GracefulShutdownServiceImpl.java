package org.artifactory.shutdown;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.request.GracefulShutdownAware;
import org.artifactory.common.ConstantValues;
import org.artifactory.concurrent.ArtifactoryExecutorFactory;
import org.artifactory.concurrent.ThreadPoolConfig;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * @author igoru
 */
@Slf4j
@Service
public class GracefulShutdownServiceImpl implements GracefulShutdownService {

    private static final String THREAD_NAME = "graceful-shutdown-thread";
    private static final String LATCH_KEY = "latch";
    private static final long DEFAULT_DELAY = 1000;
    private static final long ADDITIONAL_DELAY_MILLIS = 5000L; // additional time for RT buffers flush

    private final Supplier<Collection<GracefulShutdownAware>> beansSupplier;
    private final long delay;
    private final long timeout;
    private final Map<String, CountDownLatch> latchHolder;
    private final ExecutorService executorService;

    public GracefulShutdownServiceImpl() {
        this(
                () -> ContextHelper.get().beansForType(GracefulShutdownAware.class).values(),
                DEFAULT_DELAY,
                ConstantValues.gracefulShutdownMaxRequestDurationMillis.getLong()
        );
    }

    GracefulShutdownServiceImpl(Supplier<Collection<GracefulShutdownAware>> beansSupplier, long delay, long timeout) {
        this.beansSupplier = beansSupplier;
        this.latchHolder = new ConcurrentHashMap<>();
        this.delay = delay;
        this.timeout = timeout;
        this.executorService = ArtifactoryExecutorFactory.newClassicThreadPool(ThreadPoolConfig.builder()
                .corePoolSize(1)
                .maxThreads(1)
                .keepAliveTimeout(0L)
                .keepAliveTimeoutTimeUnit(TimeUnit.SECONDS)
                .daemon(true)
                .queue(new LinkedBlockingDeque<>(1))
                .threadNameFormat(THREAD_NAME)
                .build(), null, false);
    }

    @Override
    public Result waitProcessesCompletion() {
        try {
            val latch = latchHolder.computeIfAbsent(LATCH_KEY, s -> startWaiting());
            val completed = latch.await(timeout, TimeUnit.MILLISECONDS);
            return completed ? Result.COMPLETED : Result.TIMEOUT;
        } catch (InterruptedException e) {
            logException(e);
            Thread.currentThread().interrupt();
            return Result.ERROR;
        } catch (Exception e) {
            logException(e);
            return Result.ERROR;
        } finally {
            latchHolder.remove(LATCH_KEY);
        }
    }

    private CountDownLatch startWaiting() {
        val latch = new CountDownLatch(1);
        val runnable = createBeanTester(latch);
        executorService.execute(runnable);
        return latch;
    }

    private Runnable createBeanTester(CountDownLatch latch) {
        return () -> {
            log.info("{} started", THREAD_NAME);
            val beans = beansSupplier.get();
            try {
                Thread.sleep(ADDITIONAL_DELAY_MILLIS);
                while (true) {
                    val busyBeans = beans.stream().filter(c -> !c.canBeShutdown()).toList();
                    val busyBeansCount = busyBeans.size();
                    log.info("Busy beans count {}", busyBeansCount);
                    if (busyBeansCount == 0) {
                        Thread.sleep(ADDITIONAL_DELAY_MILLIS);
                        log.info("{} completed", THREAD_NAME);
                        return;
                    } else {
                        Thread.sleep(delay);
                    }
                }
            } catch (InterruptedException e) {
                logException(e);
                Thread.currentThread().interrupt();
            } finally {
                latch.countDown();
            }
        };
    }

    private void logException(Exception e) {
        log.error("{} finished with exception {}", THREAD_NAME, e.getMessage(), e);
    }
}
