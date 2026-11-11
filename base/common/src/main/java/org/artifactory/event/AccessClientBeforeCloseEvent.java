package org.artifactory.event;

import org.springframework.context.ApplicationEvent;

/**
 * @author Yevdo Abramov
 * Created on 14/06/2020
 */
public class AccessClientBeforeCloseEvent extends ApplicationEvent {
    public AccessClientBeforeCloseEvent(Object source) {
        super(source);
    }
}
