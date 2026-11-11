package org.artifactory.repo.system;

import lombok.experimental.UtilityClass;
import org.artifactory.util.ThreadMarker;

/**
 * Per-thread marker set by the internal system-folder upload endpoint while it writes a file.
 * Folder-owning storage interceptors check {@link #isMarked()} to detect the trusted internal
 * upload flow and skip their own permission checks — authentication and authorization are
 * already enforced at the API boundary.
 *
 * <p>The marker is generic on purpose: it is not tied to any particular {@link SystemFolders}
 * folder or addon, so any interceptor guarding a system folder can opt out of the internal flow
 * the same way.
 *
 * @author Fawaz Atwa
 */
@UtilityClass
public class SystemFolderUploadMarker {

    public final String THREAD_MARKER = "system-folder-upload-thread-marker";

    public void mark() {
        ThreadMarker.mark(THREAD_MARKER);
    }

    public void unmark() {
        ThreadMarker.unmark(THREAD_MARKER);
    }

    public boolean isMarked() {
        return ThreadMarker.isMarkedBy(THREAD_MARKER);
    }
}
