package org.artifactory.api.context;

import jakarta.annotation.Nonnull;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.home.ArtifactoryHome;

import java.util.function.Supplier;

/**
 * A utility class that allows to safely bind {@link ArtifactoryContext} instance to current thread and execute a code
 * within that context.
 *
 * @author Oleksandr Legenkiy
 */
@Slf4j
@UtilityClass
public class ArtifactoryContextUtils {

    /**
     * Executes a supplier within an Artifactory context by binding {@link ArtifactoryContext} and
     * {@link ArtifactoryHome} to the current thread.
     * <p>
     * This method is particularly useful when executing code from external threads (such as JMX/RMI threads,
     * scheduler threads, or async tasks) that don't have the Artifactory context automatically bound.
     * The context and home are bound before execution and automatically unbound in a finally block,
     * ensuring proper cleanup even if an exception occurs.
     * </p>
     *
     * <p><b>Usage Example:</b></p>
     * <pre>{@code
     * ArtifactoryContext context = ContextHelper.get();
     * Long result = ArtifactoryContextUtils.supplyWithContext(
     *     () -> fileService.getFilesCount(repoPath),
     *     context,
     *     "Failed to get files count"
     * );
     * }</pre>
     *
     * @param <T> the type of result returned by the supplier
     * @param supplier the supplier to execute within the Artifactory context. The supplier's operation
     *                 will have access to {@link ArtifactoryHome} and {@link ArtifactoryContext} via
     *                 their respective thread-local getters
     * @param context the Artifactory context to bind to the current thread. This context and its
     *                associated {@link ArtifactoryHome} will be available during supplier execution
     * @param errorMessage a descriptive error message to log if the supplier execution fails.
     *                     The actual exception will be appended to this message in the log
     * @return the result produced by the supplier
     * @throws RuntimeException if the supplier throws any exception. The original exception is logged
     *                          with the provided error message and then rethrown
     */
    public <T> T supplyWithContext(@Nonnull Supplier<T> supplier, @Nonnull ArtifactoryContext context,
            @Nonnull String errorMessage) {
        bindArtifactoryContext(context);
        try {
            return supplier.get();
        } catch (Exception e) {
            log.warn(errorMessage, e);
            throw e;
        } finally {
            ArtifactoryContextThreadBinder.unbind();
            ArtifactoryHome.unbind();
        }
    }

    private void bindArtifactoryContext(ArtifactoryContext context) {
        try {
            ArtifactoryContextThreadBinder.bind(context);
            ArtifactoryHome.bind(context.getArtifactoryHome());
        } catch (Exception e) {
            log.warn("Failed to bind artifactory context to current thread", e);
            throw e;
        }
    }
}
