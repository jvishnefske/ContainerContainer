package org.artifactory.rest.services.cleanup;

import org.springframework.beans.factory.annotation.Lookup;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * @author Oleksandr Legenkiy
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public interface CleanupPackagePolicyServiceFactory {

    @Lookup
    GetAllCleanupPackagePolicyService getAllCleanupPackagePolicyService();

    @Lookup
    GetCleanupPackagePolicyService getCleanupPackagePolicyService();

    @Lookup
    CreateCleanupPackagePolicyService createCleanupPackagePolicyService();

    @Lookup
    UpdateCleanupPackagePolicyService updateCleanupPackagePolicyService();

    @Lookup
    DeleteCleanupPackagePolicyService deleteCleanupPackagePolicyService();
}
