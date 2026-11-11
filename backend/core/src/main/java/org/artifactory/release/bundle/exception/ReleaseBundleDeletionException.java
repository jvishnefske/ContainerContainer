package org.artifactory.release.bundle.exception;

import lombok.NonNull;
import org.artifactory.exception.http.BadRequestException;

/**
 * @author Eli Skoran
 * Created on 19/10/2022
 */
public class ReleaseBundleDeletionException extends BadRequestException {
    public static final String ERROR_MESSAGE =
            "Release Bundle %s/%s cannot be deleted because it was marked as 'Keep'. " +
                    "To delete the Release Bundle, clear the 'Keep' selection.";

    private ReleaseBundleDeletionException(@NonNull String message) {
        super(message);
    }

    public static ReleaseBundleDeletionException cannotDeleteBundlesMarkedAsKeep(
            @NonNull String bundleName, @NonNull String bundleVersion) {
        String message = String.format(ERROR_MESSAGE, bundleName, bundleVersion);
        return new ReleaseBundleDeletionException(message);
    }
}
