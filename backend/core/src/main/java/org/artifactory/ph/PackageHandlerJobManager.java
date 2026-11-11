package org.artifactory.ph;

import org.artifactory.spring.ContextCreationListener;

/**
 * An Artifactory side service to initialize all the Jobs that should run in Package handler side.
 */
public interface PackageHandlerJobManager extends ContextCreationListener {
}
