package org.artifactory.security.ssh;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.home.ArtifactoryHome;

/**
 * Runs an action with {@link ArtifactoryHome} and {@link ArtifactoryContext} bound to the current thread, restoring
 * the previous binding state on return.
 * <p>
 * MINA SSHD serves SSH callbacks on nio2 worker threads owned by the JDK's async-channel group. Those threads do not
 * inherit Artifactory's per-thread state, so any callback that reaches {@code ArtifactoryHome.get()} or
 * {@code ContextHelper.get()} would otherwise fail with {@code IllegalStateException}.
 *
 * @author Andrey Tashlitsky
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SshThreadBinder {

    @FunctionalInterface
    public interface ThrowingRunnable<E extends Throwable> {
        void run() throws E;
    }

    @FunctionalInterface
    public interface ThrowingSupplier<T, E extends Throwable> {
        T get() throws E;
    }

    public static <T, E extends Throwable> T callBound(ArtifactoryHome home, ArtifactoryContext context,
            ThrowingSupplier<T, E> action) throws E {
        boolean bindHome = !ArtifactoryHome.isBound();
        boolean bindContext = ContextHelper.get() == null;
        if (bindHome) {
            ArtifactoryHome.bind(home);
        }
        if (bindContext) {
            ArtifactoryContextThreadBinder.bind(context);
        }
        try {
            return action.get();
        } finally {
            if (bindContext) {
                ArtifactoryContextThreadBinder.unbind();
            }
            if (bindHome) {
                ArtifactoryHome.unbind();
            }
        }
    }

    public static <E extends Throwable> void runBound(ArtifactoryHome home, ArtifactoryContext context,
            ThrowingRunnable<E> action) throws E {
        callBound(home, context, () -> {
            action.run();
            return null;
        });
    }
}
