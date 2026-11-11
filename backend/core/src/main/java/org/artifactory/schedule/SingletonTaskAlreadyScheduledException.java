package org.artifactory.schedule;

public class SingletonTaskAlreadyScheduledException extends IllegalStateException {

    public SingletonTaskAlreadyScheduledException(InternalTask internalTask) {
        super("Cannot start a singleton task more than once (" + internalTask + ").");
    }
}