package org.artifactory.ui.rest.service.jfevent;

import org.springframework.beans.factory.annotation.Lookup;
import org.springframework.stereotype.Component;

/**
 * @author Yevdo Abramov
 * Created on 24/05/2020
 */
@Component
public abstract class JfEventServiceFactory {

    @Lookup
    public abstract GetDomainsListJfEventService getDomainsList();
}
