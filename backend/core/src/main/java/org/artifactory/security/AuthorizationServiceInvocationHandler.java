package org.artifactory.security;

import com.google.common.annotations.VisibleForTesting;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.util.Idempotent;
import org.artifactory.concurrent.ArtifactoryExecutorFactory;
import org.artifactory.concurrent.ThreadPoolConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.artifactory.permissions.PermissionsCacheType.Profiles.PERMISSIONS_CACHE_SHADOW;
import static org.artifactory.permissions.PermissionsCacheType.SHADOW;

/**
 * Handler for intercepting calls to {@link AuthorizationService} and comparing the results of the legacy and new
 * implementations
 *
 * @author Igor Usenko
 */
@Slf4j
@Component("authorizationServiceInvocationHandler")
@Profile(PERMISSIONS_CACHE_SHADOW)
public class AuthorizationServiceInvocationHandler implements InvocationHandler {

    private static final String THREAD_NAME = "authorization-shadow-thread-%d";

    @Autowired
    @Lazy
    @Qualifier("legacyAuthorizationServiceImpl")
    private AuthorizationService legacyAuthorizationService;
    @Autowired
    @Lazy
    @Qualifier("authorizationServiceImpl")
    private AuthorizationService authorizationService;
    @Value("${artifactory.permissions.cache.shadow.probability}")
    private float probability;

    private final ConcurrentMap<Method, MethodInfo> shadowedMethods;
    private final ExecutorService executorService;
    private final MeterRegistry meterRegistry;

    public AuthorizationServiceInvocationHandler(
            MeterRegistry meterRegistry,
            @Value("${artifactory.permissions.cache.shadow.core.pool.size}") int corePoolSize,
            @Value("${artifactory.permissions.cache.shadow.max.threads}") int maxThreads,
            @Value("${artifactory.permissions.cache.shadow.keep.alive.timeout.seconds}") int keepAliveTimeout,
            @Value("${artifactory.permissions.cache.shadow.queue.size}") int queueSize
    ) {
        this.meterRegistry = meterRegistry;
        this.shadowedMethods = new ConcurrentHashMap<>();
        this.executorService = ArtifactoryExecutorFactory.newClassicThreadPool(ThreadPoolConfig.builder()
                .corePoolSize(corePoolSize)
                .maxThreads(maxThreads)
                .keepAliveTimeout(keepAliveTimeout)
                .keepAliveTimeoutTimeUnit(TimeUnit.SECONDS)
                .daemon(true)
                .queue(new LinkedBlockingDeque<>(queueSize))
                .rejectedExecutionHandler((runnable, executor) ->
                        reportRejectedShadowCall()
                )
                .threadNameFormat(THREAD_NAME)
                .build(), ContextHelper.get(), true);
    }

    @PostConstruct
    public void postConstruct() {
        log.info("Active permissions cache type: {}", SHADOW);
    }

    @SuppressWarnings("java:S2245") //suppress Sonar warning about pseudorandom number generator safeness
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        Object legacyValue = method.invoke(legacyAuthorizationService, args);

        MethodInfo methodInfo =
                shadowedMethods.computeIfAbsent(method, m ->
                        new MethodInfo(method.toGenericString(), method.isAnnotationPresent(Idempotent.class)));
        if (methodInfo.canShadow()) {
            if (ThreadLocalRandom.current().nextFloat() < probability) {
                executorService.submit(() -> callNewService(method, args, legacyValue, methodInfo.signature()));
            } else {
                reportCall(methodInfo.signature(), true, false);
            }
        } else {
            reportCall(methodInfo.signature(), false, false);
        }

        return legacyValue;
    }

    private void callNewService(Method method, Object[] args, Object legacyValue, String methodSignature) {
        try {
            reportCall(methodSignature, true, true);
            Object newValue = method.invoke(authorizationService, args);
            compareValues(methodSignature, legacyValue, newValue);
        } catch (Exception e) {
            reportShadowCall(methodSignature, false, e);
            logFailure(methodSignature, e);
        }
    }

    private void compareValues(String methodSignature, Object legacyValue, Object newValue) {
        boolean matches;
        if (legacyValue instanceof Collection<?> legacyCollection && newValue instanceof Collection<?> newCollection) {
            matches = CollectionUtils.isEqualCollection(legacyCollection, newCollection);
        } else {
            matches = Objects.equals(legacyValue, newValue);
        }
        reportShadowCall(methodSignature, matches, null);
        if (!matches) {
            logDifference(methodSignature, legacyValue, newValue);
        }
    }

    private void reportCall(String methodSignature, boolean canShadow, boolean didShadow) {
        meterRegistry.counter("authorization.service.calls",
                        "signature", methodSignature,
                        "canShadow", String.valueOf(canShadow),
                        "didShadow", String.valueOf(didShadow))
                .increment();
    }

    private void reportShadowCall(String methodSignature, boolean resultMatches, Exception exception) {
        meterRegistry.counter("authorization.service.shadow.calls",
                        "signature", methodSignature,
                        "resultMatches", String.valueOf(resultMatches),
                        "ex", getErrorCause(exception))
                .increment();
    }

    @VisibleForTesting
    void reportRejectedShadowCall() {
        meterRegistry.counter("authorization.service.rejected.shadow.calls")
                .increment();
    }

    @VisibleForTesting
    void logDifference(String methodSignature, Object legacyValue, Object newValue) {
        log.warn("Legacy method {} returned different value [{}] than new method [{}]",
                methodSignature, legacyValue, newValue);
    }

    @VisibleForTesting
    void logFailure(String methodSignature, Exception e) {
        log.warn("Failed to call new method {} due to: {}", methodSignature, getErrorCause(e), e);
    }

    @VisibleForTesting
    void autowire(AuthorizationService legacyAuthorizationService, AuthorizationService authorizationService,
            float probability) {
        this.legacyAuthorizationService = legacyAuthorizationService;
        this.authorizationService = authorizationService;
        this.probability = probability;
    }

    private static String getErrorCause(Exception exception) {
        if (exception == null) {
            return "(none)";
        }
        return exception.getCause() == null ?
                exception.getClass().getSimpleName() :
                exception.getCause().getClass().getName();
    }

    private record MethodInfo(
            String signature,
            boolean canShadow
    ) {
    }

}
