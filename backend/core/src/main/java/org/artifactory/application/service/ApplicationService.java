package org.artifactory.application.service;

import com.jfrog.apptrust.client.ApptrustClient;
import lombok.NonNull;
import org.artifactory.application.exception.ApplicationServiceDisableException;
import org.artifactory.application.exception.ApplicationServiceException;
import org.artifactory.application.exception.NotifyExistsPackageException;
import org.artifactory.application.exception.NotifyPackageCreatedException;
import org.artifactory.application.model.ApplicationServiceSBOM;
import org.artifactory.spring.ReloadableBean;
import org.artifactory.storage.db.event.service.metadata.model.MetadataEvent;
import org.jfrog.metadata.client.model.MetadataPackage;

import java.util.Optional;

/**
 * @author Natalie Berni
 */
public interface ApplicationService extends ReloadableBean {
    /**
     * Determines whether the application service is currently enabled.
     *
     * @return {@code true} if the application service is enabled,
     * {@code false} otherwise.
     */
    boolean isApplicationServiceEnabled();


    /**
     * Creates an Application Service SBOM based on the provided metadata event and package data.
     *
     * @param metadataEvent   the metadata event containing relevant information
     *                        about the application service.
     * @param metadataPackage the metadata package containing information
     *                        about the package.
     * @return an {@link Optional} containing the generated {@link ApplicationServiceSBOM},
     * or an empty Optional if the SBOM could not be created.
     */

    Optional<ApplicationServiceSBOM> createApplicationServiceSBOM(MetadataEvent metadataEvent,
            MetadataPackage metadataPackage);

    /**
     * Notifies the Application Service that a new Package has been created.
     *
     * @param applicationServiceSBOM the SBOM of the package that has been created.
     * @throws ApplicationServiceDisableException if the application service is disabled.
     * @throws NotifyExistsPackageException       if the Application Service has already been notified about the
     * package created.
     * @throws NotifyPackageCreatedException      if there is an issue while notifying that the
     *                                            package has been created.
     */

    void notifyPackageCreated(ApplicationServiceSBOM applicationServiceSBOM) throws ApplicationServiceException;

    @NonNull ApptrustClient getApptrustClient();
}
