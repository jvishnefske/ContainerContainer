package org.artifactory.addon.conan;

import lombok.experimental.UtilityClass;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.descriptor.repo.RepoDescriptor;

/**
 * Shared authentication util for the Conan Resource and the Conan anonymous auth interceptor.
 *
 * @author Volodymyr Shulga
 */
@UtilityClass
public class ConanAuthUtil {

    public static boolean forceAuthIsNeededForRepo(String repoKey) {
        boolean forceAuth = false;
        RepositoryService repoService = ContextHelper.get().beanForType(RepositoryService.class);
        RepoDescriptor descriptor = repoService.repoDescriptorByKey(repoKey);

        if (descriptor != null) {
            forceAuth = descriptor.isForceConanAuthentication();
        } else {
            RepoDescriptor virtual = repoService.virtualRepoDescriptorByKey(repoKey);
            if (virtual != null) {
                forceAuth = virtual.isForceConanAuthentication();
            }
        }
        return forceAuth;
    }
}
