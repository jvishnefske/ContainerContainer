package org.artifactory.aql.result;

import java.io.Closeable;

public interface AqlJsonStreamer extends Closeable {
    default Row readNext() {
        return null;
    }
}