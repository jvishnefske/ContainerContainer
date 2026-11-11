package org.artifactory.storage.fs;

import lombok.Getter;
import org.artifactory.sapi.fs.VfsItem;
import org.springframework.context.ApplicationEvent;

/**
 * delegator event class for updating storage-handler gc module
 * @author Avishay Halpren
 */
public class VfsItemEvent extends ApplicationEvent {

    @Getter
    private final Type type;

    public VfsItemEvent(VfsItem<?> vfsItem,Type type) {
        super(vfsItem);
        this.type = type;
    }

    public enum Type {
        delete,
        update
    }
}
