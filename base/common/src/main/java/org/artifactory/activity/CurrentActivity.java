package org.artifactory.activity;

import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

/**
 * A set of context properties, describing the currently running activity - HTTP request, a scheduled job,
 * an async operations, etc.
 *
 * <p>
 *     The primary goal of having this data is to provide extra context information for logging and metrics.
 *     Moreover, the main idea is to use this class in middlewares / proxies, not in the application code.
 *     Application's business logic should NEVER rely on these properties because nobody guarantees their
 *     existence / correctness.
 * </p>
 *
 * <p>
 *     Under the hood, activity properties persist in {@link org.slf4j.MDC}, e.g. it is local to the
 *     current thread (but many frameworks auto-propagate it to child threads when needed). The maintenance
 *     of MDC properties is not performed by the enum itself, but goes through a global {@link ActivityContext}
 *     adapter, which we plan to extend in the future to not just maintain MDC but also update the current trace span.
 * </p>
 *
 * @see ActivityContext
 * @author Sergey Steinvil
 */
public enum CurrentActivity {
    KIND(ActivityConstants.ACTIVITY_PROP_KIND),
    GROUP(ActivityConstants.ACTIVITY_PROP_GROUP),
    PACKAGE(ActivityConstants.ACTIVITY_PROP_PACKAGE),
    NAME(ActivityConstants.ACTIVITY_PROP_NAME),
    REPO(ActivityConstants.ACTIVITY_PROP_REPO_KEY),
    REPO_TYPE(ActivityConstants.ACTIVITY_PROP_REPO_TYPE),
    ACTOR(ActivityConstants.ACTIVITY_PROP_ACTOR);

    private final String key;

    CurrentActivity(String key) {
        this.key = key;
    }

    //#region ---------- API ----------
    public String get() {
        return CurrentActivity.context().get(this.key);
    }

    public String get(String defaultValue) {
        return CurrentActivity.context().get(this.key, defaultValue);
    }

    public void put(String value) {
        CurrentActivity.context().put(this.key, value);
    }

    public void putIfEmpty(String value) {
        if (CurrentActivity.context().get(this.key, "").isEmpty()) {
            CurrentActivity.context().put(this.key, value);
        }
    }

    public <E extends Enum<E>>void put(E value) {
        put(value.name().toLowerCase());
    }

    public void remove() {
        CurrentActivity.context().remove(this.key);
    }
    //#endregion ---------- API ----------

    private static final AtomicReference<ActivityContext> GLOBAL = new AtomicReference<>(new DefaultActivityContext());

    /**
     * Retrieves the global {@link ActivityContext} object that allows getting / updating details
     * of the current runtime activity (HTTP request / async task / scheduled job). Practically,
     * all the information is stored in the {@link org.slf4j.MDC} and hence it is per-thread.
     * Note: in the future, the ActivityContext may also update the active span (OpenTelemetry).
     *
     * @return The {@link ActivityContext} object, providing access to properties of the current activity.
     */
    public static ActivityContext context() {
        return GLOBAL.get();
    }

    /**
     * This function allows replacing / wrapping the global {@link ActivityContext} object.
     * Not in use (yet).
     *
     * @param supplier The callback function, receiving the old {@link ActivityContext} object
     *                 and returning the new one to assign.  Note: this callback should have no
     *                 side effects, because it may be called multiple times (retries in case of
     *                 thread collisions).
     * @return The previous {@link ActivityContext} object.
     */
    public static ActivityContext updateContext(UnaryOperator<ActivityContext> supplier) {
        return GLOBAL.getAndUpdate(supplier);
    }

    /**
     * Creates a new auto-closable object that will save a snapshot of MDC values
     * on construction and release it when closed. Normally, new activity scopes
     * are used in the infrastructure code, running async operations in a background thread.
     *
     * <p>
     * This API was created for try-with-resources blocks, like this:
     * <pre>{@code
     *     try (ActivityScope scope = CurrentActivity.newScope()) {
     *          CurrentActivity.PACKAGE("npm");
     *          // ...
     *     }
     *     // Note: All changes that we made to MDC within the try block will be erased here
     * }</pre>
     * </p>
     *
     * @return The new {@link ActivityScope} object
     */
    public static ActivityScope newScope() {
        return new ActivityScope();
    }

    /**
     * A convenience wrapper, creating a new MDC scope for the activity of specific type.
     * Technically it simply calls {@link #newScope()} and then {@link ActivityContext#putKind(String)}.
     *
     * @param kind  - Kind to assign to the current activity
     * @return The new {@link ActivityScope} object
     */
    public static ActivityScope newScope(String kind) {
        ActivityScope scope = newScope();
        context().putKind(kind);
        return scope;
    }

    /**
     * A convenience wrapper, creating a new activity scope (see {@link #newScope()} and then
     * assigning kind and optional group to it.
     *
     * @param kind  - Kind to assign to the current activity
     * @param group - Optional group name to assign to the current activity (null values will be skipped)
     * @return The new {@link ActivityScope} object
     */
    public static ActivityScope newScope(String kind, String group) {
        ActivityScope scope = newScope();
        ActivityContext context = context();
        if (kind != null) {
            context.putKind(kind);
        }
        if (group != null) {
            context.putGroup(group);
        }
        return scope;
    }

    /**
     * Retrieves {@link ActivityDetails} annotations for the given object and method and
     * uses them to update details of the current activity - set package type, assign activity group, etc.
     * <p>
     * This helper was designed for proxies of all kinds (aspects, async executors, etc.) that need to
     * invoke a specific method of a given object. It is important to note that aspects often pass
     * a reference to the method from the interface / base class, while developers may annotate
     * the implementation. Using the {@link AopUtils#getMostSpecificMethod(Method, Class)} helper
     * resolves the real implementation and then
     * {@link AnnotatedElementUtils#findAllMergedAnnotations(AnnotatedElement, Class)} collects
     * annotations from the implementation and all its ancestors.
     * </p>
     *
     * @param target - (optional) the target object, whose method will be invoked
     * @param method - (optional) the method that will be invoked
     */
    public static void updateFromAnnotations(Object target, Method method) {
        if (target == null && method == null) {
            return;
        }

        Class<?> targetClass;
        Method targetMethod = null;

        if (target == null) {
            targetClass = method.getDeclaringClass();
            targetMethod = method;
        } else {
            targetClass = target.getClass();
            if (method != null) {
                // AOP often passes us a reference to the method from interface, while developers may annotate
                // the implementation. Using the AopUtils.getMostSpecificMethod() helps us to get annotations
                // from both.
                targetMethod = AopUtils.getMostSpecificMethod(method, targetClass);
            }
        }

        // First apply details from annotations, applied on the class (or any of its ancestors)
        updateFromAnnotations(targetClass);
        // Then apply details from method annotations (or annotations from the interface - if any)
        updateFromAnnotations(targetMethod);
    }

    /**
     * Retrieves {@link ActivityDetails} annotations for the given class or method and - if found -
     * updates the current activity with properties, provided by these annotations.
     * <p>
     * For example:
     * <pre>{@code
     *     @JobCommand(jobName = JobName.DOCKER_V2_TAG_CLEANUP, description = "Docker V2 Tags Cleanup Job")
     *     @ActivityDetails(PackageType.DOCKER)
     *     public class DockerV2TagCleanupJob extends QuartzCommand {
     *          // ...
     *     }
     *
     *     // Later on, scheduled jobs framework will run an equivalent of
     *     //
     *     // CurrentActivity.applyActivityDetailsFromAnnotations(DockerV2TagCleanupJob.class)
     *     //
     *     // which will update the current current activity with a correct package type,
     *     // like this: CurrentActivity.get().putPackageType(PackageType.DOCKER)
     *     }</pre>
     * </p>
     *
     * @param element The class or method to get {@link ActivityDetails} annotations for.
     */
    public static void updateFromAnnotations(AnnotatedElement element) {
        if (element == null) {
            return;
        }
        findActivityDetails(element).forEach(CurrentActivity::applyOnCurrentActivity);
    }

    /**
     * Updates properties of the current activity from non-empty attributes of the given
     * {@link ActivityDetails} annotation.
     *
     * @param annotation The {@link ActivityDetails} to read activity properties from.
     */
    public static void applyOnCurrentActivity(ActivityDetails annotation) {
        ActivityContext activity = context();
        if (!annotation.kind().isEmpty()) {
            activity.putKind(annotation.kind());
        }
        if (!annotation.group().isEmpty()) {
            activity.putGroup(annotation.group());
        }
        if (!annotation.customPackageType().isEmpty()) {
            activity.putPackageType(annotation.customPackageType());
        } else if (annotation.packageType().length > 0) {
            activity.putPackageType(annotation.packageType()[0]);
        }
    }

    public static Set<ActivityDetails> findActivityDetails(AnnotatedElement element) {
        return AnnotatedElementUtils.findAllMergedAnnotations(element, ActivityDetails.class);
    }

    /**
     * Searches annotation of specified type on the given method and then - if not found - on its declaring class.
     * @param method - The method to search annotation for
     * @param annotationType - The class of the annotation to search
     * @return The annotation if found, otherwise null.
     */
    public static <T extends Annotation> T findAnnotation(Method method, Class<T> annotationType) {
        T annotation = AnnotatedElementUtils.findMergedAnnotation(method, annotationType);
        if (annotation != null) {
            return annotation;
        }
        return AnnotatedElementUtils.findMergedAnnotation(method.getDeclaringClass(), annotationType);
    }
}
